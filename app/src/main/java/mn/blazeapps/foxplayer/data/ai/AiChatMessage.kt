package mn.blazeapps.foxplayer.data.ai

import java.util.UUID

enum class ChatSender {
    User,
    Assistant,
    System,
}

data class ChatSource(
    val title: String,
    val type: String, // "Wikipedia", "Dictionary", "Bookmarks", "Book Info", "SmolLM-135M ONNX"
    val snippet: String? = null,
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<ChatSource> = emptyList(),
)
