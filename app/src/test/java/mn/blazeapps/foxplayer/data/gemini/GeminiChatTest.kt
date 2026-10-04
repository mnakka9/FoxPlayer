package mn.blazeapps.foxplayer.data.gemini

import mn.blazeapps.foxplayer.data.auth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiChatTest {

    @Test
    fun testGeminiChatMessageDefaults() {
        val msg = GeminiChatMessage(
            sender = MessageSender.USER,
            text = "Tell me about this book",
        )
        assertNotNull(msg.id)
        assertEquals(MessageSender.USER, msg.sender)
        assertEquals("Tell me about this book", msg.text)
        assertFalse(msg.isStreaming)
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun testGeminiStreamingMessageState() {
        val streamingMsg = GeminiChatMessage(
            sender = MessageSender.GEMINI,
            text = "Generating response...",
            isStreaming = true,
        )
        assertEquals(MessageSender.GEMINI, streamingMsg.sender)
        assertTrue(streamingMsg.isStreaming)

        val completedMsg = streamingMsg.copy(
            text = "Here is the completed response.",
            isStreaming = false,
        )
        assertEquals("Here is the completed response.", completedMsg.text)
        assertFalse(completedMsg.isStreaming)
    }

    @Test
    fun testAuthStateTransitions() {
        val loggedOut: AuthState = AuthState.LoggedOut
        assertTrue(loggedOut is AuthState.LoggedOut)

        val gmailUser: AuthState = AuthState.GmailUser(
            uid = "user_456",
            email = "listener@gmail.com",
            displayName = "listener",
        )
        assertTrue(gmailUser is AuthState.GmailUser)
        val authed = gmailUser as AuthState.GmailUser
        assertEquals("listener@gmail.com", authed.email)
        assertEquals("listener", authed.displayName)

        val apiKeyUser: AuthState = AuthState.ApiKeyUser(
            maskedKey = "AIzaSy...4x9B",
        )
        assertTrue(apiKeyUser is AuthState.ApiKeyUser)
        val keyAuthed = apiKeyUser as AuthState.ApiKeyUser
        assertEquals("AIzaSy...4x9B", keyAuthed.maskedKey)

        val errorState: AuthState = AuthState.Error("Network failure")
        assertEquals("Network failure", (errorState as AuthState.Error).message)
    }

    @Test
    fun testGeminiModelsManagerDefaults() {
        val models = GeminiModelsManager.DEFAULT_MODELS
        assertTrue(models.isNotEmpty())
        assertTrue(models.any { it.id == "gemini-1.5-flash" && it.isRecommended })
        assertTrue(models.any { it.id == "gemini-2.0-flash" })
        assertTrue(models.any { it.id == "gemini-1.5-pro" })
        assertTrue(models.any { it.id == "gemini-2.0-flash-lite" })
        assertTrue(models.any { it.id == "gemini-1.5-flash-8b" })
    }
}
