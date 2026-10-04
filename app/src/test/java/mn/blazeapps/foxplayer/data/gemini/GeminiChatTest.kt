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

        val guestUser: AuthState = AuthState.LoggedIn(
            uid = "guest_123",
            email = null,
            isAnonymous = true,
            displayName = "Guest Listener",
        )
        assertTrue(guestUser is AuthState.LoggedIn)
        val guest = guestUser as AuthState.LoggedIn
        assertTrue(guest.isAnonymous)
        assertEquals("Guest Listener", guest.displayName)

        val emailUser: AuthState = AuthState.LoggedIn(
            uid = "user_456",
            email = "listener@example.com",
            isAnonymous = false,
            displayName = "listener",
        )
        val authed = emailUser as AuthState.LoggedIn
        assertFalse(authed.isAnonymous)
        assertEquals("listener@example.com", authed.email)

        val errorState: AuthState = AuthState.Error("Network failure")
        assertEquals("Network failure", (errorState as AuthState.Error).message)
    }
}
