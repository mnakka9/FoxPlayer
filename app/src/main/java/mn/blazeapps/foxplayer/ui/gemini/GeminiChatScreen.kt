package mn.blazeapps.foxplayer.ui.gemini

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mn.blazeapps.foxplayer.R
import mn.blazeapps.foxplayer.data.auth.AuthState
import mn.blazeapps.foxplayer.data.gemini.GeminiChatMessage
import mn.blazeapps.foxplayer.data.gemini.MessageSender
import mn.blazeapps.foxplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatScreen(
    viewModel: GeminiViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isContextEnabled by viewModel.isContextEnabled.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAuthSheet by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isDark = MaterialTheme.colorScheme.background == BgDeep

    // Scroll to bottom as messages stream in
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(ColorOrange, ColorBlueViolet))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_gemini),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column {
                            Text(
                                "Gemini AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showModelSelector = true }
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    selectedModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Model",
                                    tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    // Profile/Auth chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .clickable { showAuthSheet = true }
                            .padding(end = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                when (authState) {
                                    is AuthState.GmailUser -> Icons.Default.Email
                                    is AuthState.ApiKeyUser -> Icons.Default.Key
                                    else -> Icons.Default.AccountCircle
                                },
                                contentDescription = null,
                                tint = when (authState) {
                                    is AuthState.GmailUser -> ColorOrangeLight
                                    is AuthState.ApiKeyUser -> Color(0xFF22C55E)
                                    else -> if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                when (authState) {
                                    is AuthState.GmailUser -> (authState as AuthState.GmailUser).displayName ?: (authState as AuthState.GmailUser).email.substringBefore('@')
                                    is AuthState.ApiKeyUser -> "API Key"
                                    else -> "Sign In"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Restart Chat",
                            tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Audiobook Context Banner
            viewModel.bookTitle?.let { title ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0x331E293B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { viewModel.toggleContext() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = if (isContextEnabled) ColorOrangeLight else TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Context: $title",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isContextEnabled) ColorOrangeDim else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (isContextEnabled) "ACTIVE" else "OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isContextEnabled) ColorOrangeLight else TextMuted,
                            )
                        }
                    }
                }
            }

            // Error Banner
            errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Chat Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        isDark = isDark,
                        onCopyText = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Message", message.text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onSaveToNotes = {
                            viewModel.saveNoteForBook("Gemini note: ${message.text}") { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "Saved excerpt to Book Notes!" else "Could not save note",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }

            // Suggested Prompts
            if (messages.size <= 1 && !isStreaming) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.suggestedPrompts) { prompt ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable {
                                inputText = prompt
                            },
                        ) {
                            Text(
                                prompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = if (isDark) Color(0xF00A0F26) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Ask $selectedModel about plot, lore...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorOrange,
                            unfocusedBorderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )

                    IconButton(
                        onClick = {
                            if (!viewModel.isAuthenticated) {
                                showAuthSheet = true
                            } else {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendMessage(textToSend)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isStreaming,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isStreaming) {
                                    Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight))
                                } else {
                                    Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                                }
                            ),
                    ) {
                        if (isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAuthSheet) {
        AuthBottomSheet(
            authState = authState,
            customApiKey = customApiKey,
            onLoginWithApiKey = viewModel::loginWithApiKey,
            onSignInGmail = viewModel::signInWithGmail,
            onSignUpGmail = viewModel::signUpWithGmail,
            onSignOut = viewModel::signOut,
            onDismiss = { showAuthSheet = false },
        )
    }

    if (showModelSelector) {
        ModelSelectorBottomSheet(
            availableModels = availableModels,
            selectedModelId = selectedModel,
            isRefreshing = isFetchingModels,
            onSelectModel = viewModel::selectModel,
            onRefreshModels = viewModel::refreshAvailableModels,
            onDismiss = { showModelSelector = false },
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: GeminiChatMessage,
    isDark: Boolean,
    onCopyText: () -> Unit,
    onSaveToNotes: () -> Unit,
) {
    val isUser = message.sender == MessageSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ColorOrange, ColorBlueViolet)))
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_gemini),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) {
                ColorOrange
            } else {
                if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant
            },
            border = if (!isUser) {
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant)
            } else null,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectionContainer {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = if (isUser) Color.White else (if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface),
                    )
                }

                if (!isUser && !message.isStreaming && message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onCopyText,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = onSaveToNotes,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = "Save excerpt to note",
                                tint = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
