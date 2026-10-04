package mn.blazeapps.foxplayer.data.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object LoggedOut : AuthState()
    data object Loading : AuthState()
    data class LoggedIn(
        val uid: String,
        val email: String?,
        val isAnonymous: Boolean,
        val displayName: String?,
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

class FirebaseAuthManager(private val context: Context) {
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
                updateFromUser(auth.currentUser)
                auth.addAuthStateListener { updatedAuth ->
                    updateFromUser(updatedAuth.currentUser)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Firebase initialization warning: ${e.message}")
            // Fall back to clean logged-out state
            _authState.value = AuthState.LoggedOut
        }
    }

    private fun updateFromUser(user: FirebaseUser?) {
        if (user != null) {
            _authState.value = AuthState.LoggedIn(
                uid = user.uid,
                email = user.email,
                isAnonymous = user.isAnonymous,
                displayName = user.displayName ?: if (user.isAnonymous) "Guest Listener" else user.email?.substringBefore('@'),
            )
        } else if (_authState.value !is AuthState.LoggedIn) {
            _authState.value = AuthState.LoggedOut
        }
    }

    fun signInAnonymously(onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            // Local guest fallback mode
            _authState.value = AuthState.LoggedIn(
                uid = "guest_local_${System.currentTimeMillis()}",
                email = null,
                isAnonymous = true,
                displayName = "Guest Listener",
            )
            onComplete(true, null)
            return
        }

        _authState.value = AuthState.Loading
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                updateFromUser(user)
                onComplete(true, null)
            }
            .addOnFailureListener { ex ->
                Log.w(tag, "Firebase anonymous sign-in failed, activating local guest: ${ex.message}")
                // Graceful fallback to local guest session so user is never blocked
                _authState.value = AuthState.LoggedIn(
                    uid = "guest_local_${System.currentTimeMillis()}",
                    email = null,
                    isAnonymous = true,
                    displayName = "Guest Listener",
                )
                onComplete(true, null)
            }
    }

    fun signInWithEmail(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            val err = "Firebase services not initialized"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || pass.isBlank()) {
            val err = "Email and password cannot be empty"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(cleanEmail, pass)
            .addOnSuccessListener { result ->
                updateFromUser(result.user)
                onComplete(true, null)
            }
            .addOnFailureListener { ex ->
                val msg = ex.localizedMessage ?: "Sign-in failed"
                _authState.value = AuthState.Error(msg)
                onComplete(false, msg)
            }
    }

    fun signUpWithEmail(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            val err = "Firebase services not initialized"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || pass.length < 6) {
            val err = "Please provide a valid email and password (minimum 6 characters)"
            _authState.value = AuthState.Error(err)
            onComplete(false, err)
            return
        }

        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(cleanEmail, pass)
            .addOnSuccessListener { result ->
                updateFromUser(result.user)
                onComplete(true, null)
            }
            .addOnFailureListener { ex ->
                val msg = ex.localizedMessage ?: "Registration failed"
                _authState.value = AuthState.Error(msg)
                onComplete(false, msg)
            }
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (_: Exception) {
        }
        _authState.value = AuthState.LoggedOut
    }

    val currentUser: AuthState.LoggedIn?
        get() = _authState.value as? AuthState.LoggedIn
}
