package mn.blazeapps.foxplayer.data.gemini

import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class MessageSender {
    USER,
    GEMINI,
    SYSTEM
}

data class GeminiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

class GeminiChatEngine(
    private val preferences: GeminiPreferences,
) {
    private val tag = "GeminiChatEngine"
    private var chat: Chat? = null
    private var lastConfiguredKey: String? = null
    private var lastContextPrompt: String? = null

    fun resetChat() {
        chat = null
        lastConfiguredKey = null
        lastContextPrompt = null
    }

    fun isReady(): Boolean = preferences.isApiKeyConfigured()

    private fun getOrInitChat(bookTitle: String?, bookAuthor: String?, includeContext: Boolean): Chat {
        val apiKey = preferences.getEffectiveApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API Key is not configured. Please enter your API key in Settings or local.properties.")
        }

        val contextPrompt = if (includeContext && !bookTitle.isNullOrBlank()) {
            val authorPart = if (!bookAuthor.isNullOrBlank()) " by $bookAuthor" else ""
            "You are FoxPlayer's intelligent literary companion. The listener is currently enjoying the audiobook \"$bookTitle\"$authorPart. Provide insightful, helpful, and concise answers regarding plot, literary themes, character motivations, author background, and recommendations. Avoid unsolicited plot twists or major spoilers unless explicitly asked."
        } else {
            "You are FoxPlayer's intelligent AI companion. Help the user explore audiobooks, literature, chapter summaries, and literary discussion with thoughtful, well-structured answers."
        }

        val existing = chat
        if (existing != null && lastConfiguredKey == apiKey && lastContextPrompt == contextPrompt) {
            return existing
        }

        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            systemInstruction = content { text(contextPrompt) },
        )

        val newChat = model.startChat()
        chat = newChat
        lastConfiguredKey = apiKey
        lastContextPrompt = contextPrompt
        return newChat
    }

    suspend fun streamMessage(
        userPrompt: String,
        bookTitle: String?,
        bookAuthor: String?,
        includeContext: Boolean,
        onTokenChunk: (incrementalText: String, fullTextSoFar: String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentChat = getOrInitChat(bookTitle, bookAuthor, includeContext)
            val fullResponse = StringBuilder()

            val stream = currentChat.sendMessageStream(userPrompt)
            stream.catch { err ->
                Log.e(tag, "Stream error: ${err.message}", err)
                throw err
            }.collect { chunk ->
                val chunkText = chunk.text ?: ""
                if (chunkText.isNotEmpty()) {
                    fullResponse.append(chunkText)
                    withContext(Dispatchers.Main) {
                        onTokenChunk(chunkText, fullResponse.toString())
                    }
                }
            }

            val finalResult = fullResponse.toString().trim()
            if (finalResult.isEmpty()) {
                Result.failure(IllegalStateException("Empty response received from Gemini"))
            } else {
                Result.success(finalResult)
            }
        } catch (e: Exception) {
            Log.e(tag, "Gemini message generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
