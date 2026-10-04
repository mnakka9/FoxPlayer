package mn.blazeapps.foxplayer.ui.gemini

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mn.blazeapps.foxplayer.data.auth.AuthState
import mn.blazeapps.foxplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBottomSheet(
    authState: AuthState,
    customApiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSignInGuest: () -> Unit,
    onSignInEmail: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignUpEmail: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf(customApiKey) }

    val isDark = MaterialTheme.colorScheme.background == BgDeep

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xF50D1230) else MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ColorOrange, ColorBlueViolet))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column {
                        Text(
                            "Account & Gemini Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Firebase Authentication & Google AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Current Session State
            when (authState) {
                is AuthState.LoggedIn -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (authState.isAnonymous) Icons.Default.PersonOutline else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (authState.isAnonymous) ColorOrangeLight else Color(0xFF22C55E),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        if (authState.isAnonymous) "Guest Session" else "Signed In",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Button(
                                    onClick = onSignOut,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) Color(0x33EF4444) else MaterialTheme.colorScheme.errorContainer,
                                        contentColor = if (isDark) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Text("Sign Out", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(
                                if (authState.isAnonymous) "Browsing anonymously without registration." else "Logged in as: ${authState.email ?: authState.displayName ?: authState.uid}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    // Sign-in options
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Firebase Authentication",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                        )

                        // 1-Tap Guest Sign In
                        Button(
                            onClick = {
                                onSignInGuest()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorOrange,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("1-Tap Guest Access (No Password)", fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(
                            color = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )

                        Text(
                            "Or Sign In with Email & Password",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; authError = null },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; authError = null },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )

                        authError?.let { err ->
                            Text(
                                "⚠️ $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isSubmitting = true
                                    onSignUpEmail(email, password) { success, err ->
                                        isSubmitting = false
                                        if (success) onDismiss() else authError = err
                                    }
                                },
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Register")
                            }

                            Button(
                                onClick = {
                                    isSubmitting = true
                                    onSignInEmail(email, password) { success, err ->
                                        isSubmitting = false
                                        if (success) onDismiss() else authError = err
                                    }
                                },
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ColorBlueVioletLight,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Sign In", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // Gemini API Key Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Google Gemini API Key",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Required for conversational generation. Free API keys can be generated instantly from Google AI Studio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        onApiKeyChange(it)
                    },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        if (keyInput.isNotBlank()) {
                            IconButton(onClick = {
                                keyInput = ""
                                onApiKeyChange("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedButton(
                    onClick = {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://aistudio.google.com/app/apikey")
                        )
                        context.startActivity(browserIntent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Get Free API Key from Google AI Studio")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
