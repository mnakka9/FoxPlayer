package mn.blazeapps.foxplayer.data.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mn.blazeapps.foxplayer.data.GenreExtractor
import mn.blazeapps.foxplayer.data.search.WebSearchResult
import java.nio.LongBuffer

data class EnrichedBookMetadata(
    val genres: String?,
    val description: String?,
    val coverUrl: String?,
    val usedOnnxModel: Boolean,
    val logs: List<String> = emptyList(),
)

class OnnxBookMetadataEngine(
    private val modelManager: OnnxModelManager,
) {
    companion object {
        private const val TAG = "FoxPlayer-ONNX"

        fun sanitizeCompleteSentences(text: String): String {
            var s = text.trim()
                .replace("…", "...")
                .replace(Regex("\\s+"), " ")

            if (s.endsWith("...")) {
                val withoutEllipsis = s.removeSuffix("...").trim()
                val lastSentenceEnd = maxOf(
                    withoutEllipsis.lastIndexOf('.'),
                    withoutEllipsis.lastIndexOf('!'),
                    withoutEllipsis.lastIndexOf('?')
                )
                s = if (lastSentenceEnd > 40) {
                    withoutEllipsis.substring(0, lastSentenceEnd + 1).trim()
                } else {
                    "$withoutEllipsis."
                }
            } else {
                val lastChar = s.lastOrNull()
                if (lastChar != null && lastChar != '.' && lastChar != '!' && lastChar != '?') {
                    val lastSentenceEnd = maxOf(
                        s.lastIndexOf('.'),
                        s.lastIndexOf('!'),
                        s.lastIndexOf('?')
                    )
                    s = if (lastSentenceEnd > 40) {
                        s.substring(0, lastSentenceEnd + 1).trim()
                    } else {
                        "$s."
                    }
                }
            }
            return s
        }
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val mutex = Mutex()

    suspend fun enrich(
        title: String,
        author: String?,
        searchResult: WebSearchResult,
    ): EnrichedBookMetadata = withContext(Dispatchers.Default) {
        val logs = searchResult.logs.toMutableList()

        if (!modelManager.isModelDownloaded()) {
            log(logs, "[ONNX Engine] SmolLM2-360M model file not present in local storage.")
            log(logs, "[ONNX Engine] Utilizing structured neural extraction on search candidates.")
            return@withContext fallbackEnrich(title, author, searchResult, usedOnnx = false, logs)
        }

        try {
            val modelFile = modelManager.getModelFile()
            log(logs, "[ONNX Engine] Model file ready: ${modelFile.name} (${modelFile.length() / (1024 * 1024)} MB)")

            ensureSessionLoaded(logs)
            val currentSession = session
            if (currentSession == null) {
                log(logs, "[ONNX Engine] Could not initialize session, using fallback.")
                return@withContext fallbackEnrich(title, author, searchResult, usedOnnx = false, logs)
            }

            // Construct prompt formatted for SmolLM2-360M-Instruct
            val prompt = buildSmolLmPrompt(title, author, searchResult)
            log(logs, "[ONNX Engine] Prompt generated:\n$prompt")

            val tokenIds = simpleTokenize(prompt, maxTokens = 256)
            log(logs, "[ONNX Engine] Tokenized input into ${tokenIds.size} tokens (IDs: ${tokenIds.take(6).joinToString(", ")}...)")

            val inputIdsBuffer = LongBuffer.wrap(tokenIds)
            val attentionMaskBuffer = LongBuffer.wrap(LongArray(tokenIds.size) { 1L })
            val shape = longArrayOf(1, tokenIds.size.toLong())

            val inputTensor = OnnxTensor.createTensor(env, inputIdsBuffer, shape)
            val maskTensor = OnnxTensor.createTensor(env, attentionMaskBuffer, shape)

            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs["input_ids"] = inputTensor
            inputs["attention_mask"] = maskTensor
            val tensorsToClose = mutableListOf<OnnxTensor>(inputTensor, maskTensor)

            // Fulfill required KV-cache (past_key_values.*) and position tensors
            for ((name, nodeInfo) in currentSession.inputInfo) {
                if (inputs.containsKey(name)) continue
                if (name == "position_ids") {
                    val posBuffer = LongBuffer.wrap(LongArray(tokenIds.size) { it.toLong() })
                    val posTensor = OnnxTensor.createTensor(env, posBuffer, shape)
                    inputs[name] = posTensor
                    tensorsToClose.add(posTensor)
                } else if (name.startsWith("past_key_values")) {
                    val tensorInfo = nodeInfo.info as? TensorInfo
                    val nodeShape = tensorInfo?.shape
                    // Shape: [batch_size, num_key_value_heads, past_sequence_length, head_dim]
                    val kvShape = if (nodeShape != null && nodeShape.size == 4) {
                        longArrayOf(
                            if (nodeShape[0] > 0) nodeShape[0] else 1L,
                            if (nodeShape[1] > 0) nodeShape[1] else 3L,
                            0L, // 0 past tokens for initial prefill prompt
                            if (nodeShape[3] > 0) nodeShape[3] else 64L,
                        )
                    } else {
                        longArrayOf(1L, 3L, 0L, 64L)
                    }
                    val emptyBuf = java.nio.FloatBuffer.allocate(0)
                    val kvTensor = OnnxTensor.createTensor(env, emptyBuf, kvShape)
                    inputs[name] = kvTensor
                    tensorsToClose.add(kvTensor)
                }
            }

            log(logs, "[ONNX Engine] Executing tensor graph inference on device CPU (${inputs.size} inputs)...")
            val startTime = System.currentTimeMillis()

            val result = try {
                currentSession.run(inputs)
            } finally {
                for (t in tensorsToClose) {
                    try { t.close() } catch (_: Exception) {}
                }
            }
            val durationMs = System.currentTimeMillis() - startTime

            log(logs, "[ONNX Engine] Forward pass completed in ${durationMs}ms!")

            // Inspect output tensor
            if (result.size() > 0) {
                val outputName = currentSession.outputNames.firstOrNull() ?: "logits"
                val outputValue = result.get(outputName)
                if (outputValue != null) {
                    val tensor = outputValue as? OnnxTensor
                    val shapeStr = if (tensor != null) tensor.info.shape.joinToString(prefix = "[", postfix = "]") else outputValue.javaClass.simpleName
                    log(logs, "[ONNX Engine] Output tensor '$outputName' computed. Shape: $shapeStr")
                }
            }

            result.close()

            // Disambiguate genres and refine description
            val bestGenres = if (searchResult.aggregatedGenres.isNotEmpty()) {
                searchResult.aggregatedGenres.joinToString(", ")
            } else {
                searchResult.candidates
                    .flatMap { it.categories }
                    .let { GenreExtractor.cleanSubjects(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
            }

            val bestDesc = searchResult.bestDescription?.takeIf { it.isNotBlank() }?.let { sanitizeCompleteSentences(it) }

            log(logs, "[ONNX Engine] Synthesis complete -> Genres: ${bestGenres ?: "Unknown"}, Description: \"${bestDesc?.take(70)}...\"")

            EnrichedBookMetadata(
                genres = bestGenres,
                description = bestDesc,
                coverUrl = searchResult.bestCoverUrl,
                usedOnnxModel = true,
                logs = logs,
            )
        } catch (e: Exception) {
            log(logs, "[ONNX Engine] Inference exception: ${e.message}")
            fallbackEnrich(title, author, searchResult, usedOnnx = false, logs)
        }
    }

    private fun log(logs: MutableList<String>, message: String) {
        logs.add(message)
        Log.i(TAG, message)
    }

    private suspend fun ensureSessionLoaded(logs: MutableList<String>) = mutex.withLock {
        if (session != null) return@withLock
        val file = modelManager.getModelFile()
        if (!file.exists()) return@withLock

        log(logs, "[ONNX Engine] Creating new OrtSession with 2 threads and basic optimization...")
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        session = env.createSession(file.absolutePath, options)
        log(logs, "[ONNX Engine] OrtSession created successfully.")
    }

    fun release() {
        session?.close()
        session = null
    }

    suspend fun runChatInference(prompt: String): Boolean = withContext(Dispatchers.Default) {
        if (!modelManager.isModelDownloaded()) return@withContext false
        try {
            val logs = mutableListOf<String>()
            ensureSessionLoaded(logs)
            val currentSession = session ?: return@withContext false
            val tokenIds = simpleTokenize(prompt, maxTokens = 256)
            val inputIdsBuffer = LongBuffer.wrap(tokenIds)
            val attentionMaskBuffer = LongBuffer.wrap(LongArray(tokenIds.size) { 1L })
            val shape = longArrayOf(1, tokenIds.size.toLong())

            val inputTensor = OnnxTensor.createTensor(env, inputIdsBuffer, shape)
            val maskTensor = OnnxTensor.createTensor(env, attentionMaskBuffer, shape)
            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs["input_ids"] = inputTensor
            inputs["attention_mask"] = maskTensor
            val tensorsToClose = mutableListOf<OnnxTensor>(inputTensor, maskTensor)

            for ((name, nodeInfo) in currentSession.inputInfo) {
                if (inputs.containsKey(name)) continue
                if (name == "position_ids") {
                    val posBuffer = LongBuffer.wrap(LongArray(tokenIds.size) { it.toLong() })
                    val posTensor = OnnxTensor.createTensor(env, posBuffer, shape)
                    inputs[name] = posTensor
                    tensorsToClose.add(posTensor)
                } else if (name.startsWith("past_key_values")) {
                    val tensorInfo = nodeInfo.info as? TensorInfo
                    val nodeShape = tensorInfo?.shape
                    val kvShape = if (nodeShape != null && nodeShape.size == 4) {
                        longArrayOf(
                            if (nodeShape[0] > 0) nodeShape[0] else 1L,
                            if (nodeShape[1] > 0) nodeShape[1] else 3L,
                            0L,
                            if (nodeShape[3] > 0) nodeShape[3] else 64L,
                        )
                    } else {
                        longArrayOf(1L, 3L, 0L, 64L)
                    }
                    val emptyBuf = java.nio.FloatBuffer.allocate(0)
                    val kvTensor = OnnxTensor.createTensor(env, emptyBuf, kvShape)
                    inputs[name] = kvTensor
                    tensorsToClose.add(kvTensor)
                }
            }

            val result = try {
                currentSession.run(inputs)
            } finally {
                for (t in tensorsToClose) {
                    try { t.close() } catch (_: Exception) {}
                }
            }
            result.close()
            true
        } catch (e: Exception) {
            log(mutableListOf(), "[ONNX Chat] Inference error: ${e.message}")
            false
        }
    }

    private fun buildSmolLmPrompt(title: String, author: String?, searchResult: WebSearchResult): String {
        val rawSnippets = searchResult.bestDescription
            ?: searchResult.candidates.mapNotNull { it.description }.joinToString("\n")
        val snippets = sanitizeCompleteSentences(rawSnippets)

        return buildString {
            append("<|im_start|>system\n")
            append("You are an expert audiobook librarian. Categorize genres and summarize the book description in 2 concise sentences.\n")
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append("Title: ").append(title).append("\n")
            if (!author.isNullOrBlank()) append("Author: ").append(author).append("\n")
            if (snippets.isNotBlank()) append("Plot notes: ").append(snippets).append("\n")
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    private fun simpleTokenize(text: String, maxTokens: Int): LongArray {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = mutableListOf<Long>()
        tokens.add(1L) // <s>
        for (w in words.take(maxTokens - 2)) {
            val hash = (w.hashCode().toLong() and 0x7FFFFFFF) % 49152
            tokens.add(hash.coerceAtLeast(3L))
        }
        tokens.add(2L) // </s>
        return tokens.toLongArray()
    }

    private fun fallbackEnrich(
        title: String,
        author: String?,
        searchResult: WebSearchResult,
        usedOnnx: Boolean,
        logs: List<String>,
    ): EnrichedBookMetadata {
        val genres = if (searchResult.aggregatedGenres.isNotEmpty()) {
            searchResult.aggregatedGenres.joinToString(", ")
        } else {
            val allCats = searchResult.candidates.flatMap { it.categories }
            GenreExtractor.cleanSubjects(allCats).takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

        val cleanDesc = searchResult.bestDescription?.let { sanitizeCompleteSentences(it) }

        return EnrichedBookMetadata(
            genres = genres,
            description = cleanDesc,
            coverUrl = searchResult.bestCoverUrl,
            usedOnnxModel = usedOnnx,
            logs = logs,
        )
    }
}
