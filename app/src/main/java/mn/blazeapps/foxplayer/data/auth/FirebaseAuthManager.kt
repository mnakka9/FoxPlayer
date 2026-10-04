package mn.blazeapps.foxplayer.data.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mn.blazeapps.foxplayer.data.gemini.GeminiPreferences

sealed class AuthState {
    data object LoggedOut : AuthState()
    data object Loading : AuthState()
    data class GmailUser(
        val uid: String,
        val email: String,
        val displayName: String?,
    ) : AuthState()
    data class ApiKeyUser(
        val maskedKey: String,
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

class FirebaseAuthManager(
    private val context: Context,
    private val preferences: GeminiPreferences,
) {
    private val tag = "FirebaseAuthManager"
    private var firebaseAuth: FirebaseAuth? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            firebaseAuth = FirebaseAuth.getInstance().also { auth ->
                updateState(auth.currentUser)
                auth.addAuthStateListener { updatedAuth ->
                    updateState(updatedAuth.currentUser)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Firebase initialization warning: ${e.message}")
            updateState(null)
        }
    }

    private fun updateState(firebaseUser: FirebaseUser?) {
        if (firebaseUser != null && !firebaseUser.email.isNullOrBlank()) {
            _authState.value = AuthState.GmailUser(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore('@'),
            )
        } else if (preferences.isApiKeySession.value && preferences.isApiKeyConfigured()) {
            _authState.value = AuthState.ApiKeyUser(preferences.getMaskedApiKey())
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    fun signInWithGmail(rawEmail: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            val err = "Firebase services not initialized"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        val email = formatGmailAddress(rawEmail)
        if (email.isBlank() || pass.isBlank()) {
            val err = "Please enter your Gmail address and password"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                preferences.setApiKeySessionActive(false)
                updateState(result.user)
                onComplete(true, null)
            }
            .addOnFailureListener { ex ->
                val msg = ex.localizedMessage ?: "Sign-in failed"
                _authState.value = AuthState.Error(msg)
                onComplete(false, msg)
            }
    }

    fun signUpWithGmail(rawEmail: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            val err = "Firebase services not initialized"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        val email = formatGmailAddress(rawEmail)
        if (email.isBlank() || pass.length < 6) {
            val err = "Please enter a valid Gmail address and password (at least 6 characters)"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                preferences.setApiKeySessionActive(false)
                updateState(result.user)
                onComplete(true, null)
            }
            .addOnFailureListener { ex ->
                val msg = ex.localizedMessage ?: "Account creation failed"
                _authState.value = AuthState.Error(msg)
                onComplete(false, msg)
            }
    }

    fun loginWithApiKey(apiKey: String, onComplete: (Boolean, String?) -> Unit) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            val err = "API Key cannot be empty"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        preferences.setCustomApiKey(cleanKey)
        preferences.setApiKeySessionActive(true)
        try {
            firebaseAuth?.signOut()
        } catch (_: Exception) {
        }
        _authState.value = AuthState.ApiKeyUser(preferences.getMaskedApiKey())
        onComplete(true, null)
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (_: Exception) {
        }
        preferences.setApiKeySessionActive(false)
        _authState.value = AuthState.LoggedOut
    }

    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.GmailUser || _authState.value is AuthState.ApiKeyUser
    }

    private fun formatGmailAddress(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.contains("@")) {
            return "$trimmed@gmail.com"
        }
        return trimmed
    }
}
