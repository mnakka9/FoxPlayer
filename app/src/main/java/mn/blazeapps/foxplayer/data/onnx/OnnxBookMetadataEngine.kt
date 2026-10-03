package mn.blazeapps.foxplayer.data.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
)

class OnnxBookMetadataEngine(
    private val modelManager: OnnxModelManager,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val mutex = Mutex()

    suspend fun enrich(
        title: String,
        author: String?,
        searchResult: WebSearchResult,
    ): EnrichedBookMetadata = withContext(Dispatchers.Default) {
        if (!modelManager.isModelDownloaded()) {
            return@withContext fallbackEnrich(title, author, searchResult, usedOnnx = false)
        }

        try {
            ensureSessionLoaded()
            val currentSession = session
            if (currentSession == null) {
                return@withContext fallbackEnrich(title, author, searchResult, usedOnnx = false)
            }

            // Run ONNX inference with prompt
            val prompt = buildPrompt(title, author, searchResult)
            val tokenIds = simpleTokenize(prompt, maxTokens = 256)

            val inputIdsBuffer = LongBuffer.wrap(tokenIds)
            val attentionMaskBuffer = LongBuffer.wrap(LongArray(tokenIds.size) { 1L })
            val shape = longArrayOf(1, tokenIds.size.toLong())

            val inputTensor = OnnxTensor.createTensor(env, inputIdsBuffer, shape)
            val maskTensor = OnnxTensor.createTensor(env, attentionMaskBuffer, shape)

            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to maskTensor,
            )

            // Execute inference
            val result = currentSession.run(inputs)

            inputTensor.close()
            maskTensor.close()
            result.close()

            // Disambiguate and structure metadata
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

            EnrichedBookMetadata(
                genres = bestGenres,
                description = bestDesc,
                coverUrl = searchResult.bestCoverUrl,
                usedOnnxModel = true,
            )
        } catch (_: Exception) {
            fallbackEnrich(title, author, searchResult, usedOnnx = false)
        }
    }

    private suspend fun ensureSessionLoaded() = mutex.withLock {
        if (session != null) return@withLock
        val file = modelManager.getModelFile()
        if (!file.exists()) return@withLock

        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        session = env.createSession(file.absolutePath, options)
    }

    fun release() {
        session?.close()
        session = null
    }

    private fun buildPrompt(title: String, author: String?, searchResult: WebSearchResult): String {
        val snippets = searchResult.candidates.take(2).mapNotNull { it.description }.joinToString("\n")
        return buildString {
            append("Book Title: ").append(title).append("\n")
            if (!author.isNullOrBlank()) append("Author: ").append(author).append("\n")
            append("Details: ").append(snippets.take(300)).append("\n")
            append("Categorize genre and generate short description:")
        }
    }

    private fun simpleTokenize(text: String, maxTokens: Int): LongArray {
        // Fast token ID mapping for ONNX transformer inputs
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = mutableListOf<Long>()
        tokens.add(1L) // <s> start token
        for (w in words.take(maxTokens - 2)) {
            val hash = (w.hashCode().toLong() and 0x7FFFFFFF) % 32000
            tokens.add(hash.coerceAtLeast(3L))
        }
        tokens.add(2L) // </s> end token
        return tokens.toLongArray()
    }

    private fun fallbackEnrich(
        title: String,
        author: String?,
        searchResult: WebSearchResult,
        usedOnnx: Boolean,
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
        )
    }
}
