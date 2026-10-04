package mn.blazeapps.foxplayer.data.gemini

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GeminiModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val inputTokenLimit: Int = 1048576,
    val outputTokenLimit: Int = 8192,
    val isRecommended: Boolean = false,
)

object GeminiModelsManager {
    private const val TAG = "GeminiModelsManager"

    val DEFAULT_MODELS = listOf(
        GeminiModelInfo(
            id = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
            description = "Fast and versatile performance across text and reasoning. Highly recommended for book companions.",
            inputTokenLimit = 1048576,
            outputTokenLimit = 8192,
            isRecommended = true,
        ),
        GeminiModelInfo(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            description = "Next-generation multimodal foundation model with high reasoning speed and fresh world knowledge.",
            inputTokenLimit = 1048576,
            outputTokenLimit = 8192,
        ),
        GeminiModelInfo(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            description = "Deep reasoning model with 2M token context window for complex plot dissection and nuanced literary analysis.",
            inputTokenLimit = 2097152,
            outputTokenLimit = 8192,
        ),
        GeminiModelInfo(
            id = "gemini-2.0-flash-lite",
            displayName = "Gemini 2.0 Flash Lite",
            description = "Ultra-fast low-latency model optimized for high throughput and rapid responses.",
            inputTokenLimit = 1048576,
            outputTokenLimit = 8192,
        ),
        GeminiModelInfo(
            id = "gemini-1.5-flash-8b",
            displayName = "Gemini 1.5 Flash 8B",
            description = "Compact 8B model built for lightweight conversational tasks and speedy summaries.",
            inputTokenLimit = 1048576,
            outputTokenLimit = 8192,
        ),
    )

    suspend fun fetchAvailableModels(apiKey: String): Result<List<GeminiModelInfo>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.success(DEFAULT_MODELS)
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FoxPlayer-Android")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "Failed to fetch models from API (HTTP ${connection.responseCode}): $errorBody")
                return@withContext Result.success(DEFAULT_MODELS)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val modelsArray = json.optJSONArray("models") ?: return@withContext Result.success(DEFAULT_MODELS)

            val parsedList = mutableListOf<GeminiModelInfo>()
            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.optJSONObject(i) ?: continue
                val rawName = modelObj.optString("name", "")
                val modelId = rawName.removePrefix("models/")
                if (modelId.isBlank()) continue

                // Verify model supports generateContent
                val methods = modelObj.optJSONArray("supportedGenerationMethods")
                var supportsGenerate = false
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                }

                // Filter out non-chat models (embeddings, aqa, etc.)
                if (!supportsGenerate || modelId.contains("embedding") || modelId.contains("aqa")) {
                    continue
                }

                val displayName = modelObj.optString("displayName", "").ifBlank { modelId }
                val description = modelObj.optString("description", "")
                val inputTokens = modelObj.optInt("inputTokenLimit", 1048576)
                val outputTokens = modelObj.optInt("outputTokenLimit", 8192)

                parsedList += GeminiModelInfo(
                    id = modelId,
                    displayName = displayName,
                    description = description,
                    inputTokenLimit = inputTokens,
                    outputTokenLimit = outputTokens,
                    isRecommended = modelId == "gemini-1.5-flash",
                )
            }

            if (parsedList.isEmpty()) {
                Result.success(DEFAULT_MODELS)
            } else {
                // Ensure popular defaults like gemini-1.5-flash appear first if present
                val sorted = parsedList.sortedByDescending { it.isRecommended }
                Result.success(sorted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models: ${e.message}", e)
            Result.success(DEFAULT_MODELS)
        } finally {
            connection?.disconnect()
        }
    }
}
