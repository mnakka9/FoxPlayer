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
import mn.blazeapps.foxplayer.data.auth.AuthState
import mn.blazeapps.foxplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBottomSheet(
    authState: AuthState,
    customApiKey: String,
    onLoginWithApiKey: (String, (Boolean, String?) -> Unit) -> Unit,
    onSignInGmail: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignUpGmail: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var gmailInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf(customApiKey) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var selectedAuthTab by remember { mutableIntStateOf(0) } // 0: Gmail, 1: API Key

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
                            "Account & Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Sign in with Gmail or enter your Gemini API Key",
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

            // Current Session Card if logged in
            when (authState) {
                is AuthState.GmailUser -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
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
                                        Icons.Default.Email,
                                        contentDescription = null,
                                        tint = ColorOrangeLight,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        "Gmail Session Active",
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
                                "Signed in as: ${authState.email}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is AuthState.ApiKeyUser -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
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
                                        Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        "API Key Session Active",
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
                                "Authenticated with Gemini Key: ${authState.maskedKey}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    // Not logged in: Show Tab Selector for Gmail vs API Key
                    TabRow(
                        selectedTabIndex = selectedAuthTab,
                        containerColor = Color.Transparent,
                        contentColor = ColorOrange,
                        divider = {},
                    ) {
                        Tab(
                            selected = selectedAuthTab == 0,
                            onClick = { selectedAuthTab = 0; authError = null },
                            text = { Text("Gmail Login", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        Tab(
                            selected = selectedAuthTab == 1,
                            onClick = { selectedAuthTab = 1; authError = null },
                            text = { Text("API Key Login", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }

                    authError?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "⚠️ $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    if (selectedAuthTab == 0) {
                        // Gmail Login Form
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Sign in with your Gmail account to manage your AI literary companion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = gmailInput,
                                onValueChange = { gmailInput = it; authError = null },
                                label = { Text("Gmail Address") },
                                placeholder = { Text("yourname@gmail.com") },
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isSubmitting = true
                                        onSignUpGmail(gmailInput, password) { success, err ->
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
                                        onSignInGmail(gmailInput, password) { success, err ->
                                            isSubmitting = false
                                            if (success) onDismiss() else authError = err
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ColorOrange,
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Sign In with Gmail", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // API Key Login Form
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Enter your personal Google Gemini API key to authenticate directly without an account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it; authError = null },
                                label = { Text("Gemini API Key") },
                                placeholder = { Text("AIzaSy...") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    if (keyInput.isNotBlank()) {
                                        IconButton(onClick = { keyInput = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            Button(
                                onClick = {
                                    isSubmitting = true
                                    onLoginWithApiKey(keyInput) { success, err ->
                                        isSubmitting = false
                                        if (success) onDismiss() else authError = err
                                    }
                                },
                                enabled = !isSubmitting && keyInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ColorOrange,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Log In with API Key", fontWeight = FontWeight.Bold)
                            }

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
                                Text("Get Free Key from Google AI Studio")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
