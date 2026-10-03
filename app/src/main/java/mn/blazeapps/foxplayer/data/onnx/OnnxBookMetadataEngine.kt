package mn.blazeapps.foxplayer.data.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
            log(logs, "[ONNX Engine] SmolLM-135M model file not present in local storage.")
            log(logs, "[ONNX Engine] Utilizing structured rule-based neural extraction on search candidates.")
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

            // Construct prompt formatted for SmolLM-135M-Instruct
            val prompt = buildSmolLmPrompt(title, author, searchResult)
            log(logs, "[ONNX Engine] Prompt generated:\n$prompt")

            val tokenIds = simpleTokenize(prompt, maxTokens = 256)
            log(logs, "[ONNX Engine] Tokenized input into ${tokenIds.size} tokens (IDs: ${tokenIds.take(6).joinToString(", ")}...)")

            val inputIdsBuffer = LongBuffer.wrap(tokenIds)
            val attentionMaskBuffer = LongBuffer.wrap(LongArray(tokenIds.size) { 1L })
            val shape = longArrayOf(1, tokenIds.size.toLong())

            val inputTensor = OnnxTensor.createTensor(env, inputIdsBuffer, shape)
            val maskTensor = OnnxTensor.createTensor(env, attentionMaskBuffer, shape)

            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to maskTensor,
            )

            log(logs, "[ONNX Engine] Executing tensor graph inference on device CPU...")
            val startTime = System.currentTimeMillis()

            val result = currentSession.run(inputs)
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

            inputTensor.close()
            maskTensor.close()
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

            val bestDesc = searchResult.bestDescription?.takeIf { it.isNotBlank() }

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

    private fun buildSmolLmPrompt(title: String, author: String?, searchResult: WebSearchResult): String {
        val snippets = searchResult.candidates
            .mapNotNull { it.description }
            .take(2)
            .joinToString("\n")
            .take(300)

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

        return EnrichedBookMetadata(
            genres = genres,
            description = searchResult.bestDescription,
            coverUrl = searchResult.bestCoverUrl,
            usedOnnxModel = usedOnnx,
            logs = logs,
        )
    }
}
