package mn.blazeapps.foxplayer.ui.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mn.blazeapps.foxplayer.data.ai.AiChatMessage
import mn.blazeapps.foxplayer.data.ai.ChatSender
import mn.blazeapps.foxplayer.data.ai.ChatSource
import mn.blazeapps.foxplayer.ui.theme.*

@Composable
fun BookAiChatPane(
    messages: List<AiChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) GlassBg else MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(16.dp),
            )
            .padding(10.dp),
    ) {
        // Top action bar: only the internal notes filter + optional clear chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuggestionChip(
                onClick = { onSendMessage("Search my bookmark notes") },
                label = { Text("📝 Search Notes", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                icon = {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = ColorBlueVioletLight,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isDark) GlassBgStrong else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                ),
            )

            if (messages.isNotEmpty()) {
                TextButton(
                    onClick = onClearChat,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear Chat",
                        modifier = Modifier.size(13.dp),
                        tint = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Clear",
                        fontSize = 11.sp,
                        color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Chat message history
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg = msg, isDark = isDark)
            }

            if (isGenerating) {
                item {
                    GeneratingBubble(isDark = isDark)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Search notes or ask anything online...",
                        fontSize = 13.sp,
                        color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                enabled = !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                    focusedBorderColor = ColorBlueVioletLight,
                    unfocusedBorderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val query = inputText.trim()
                    if (query.isNotBlank()) {
                        inputText = ""
                        onSendMessage(query)
                    }
                }),
            )

            IconButton(
                onClick = {
                    val query = inputText.trim()
                    if (query.isNotBlank()) {
                        inputText = ""
                        onSendMessage(query)
                    }
                },
                enabled = !isGenerating && inputText.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (!isGenerating && inputText.isNotBlank()) {
                            Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight))
                        } else {
                            Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                        }
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: AiChatMessage,
    isDark: Boolean,
) {
    val isUser = msg.sender == ChatSender.User

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = "AI Companion",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Surface(
            color = if (isUser) {
                ColorBlueViolet
            } else {
                if (isDark) Color(0x331E293B) else MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            border = if (!isUser) {
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant)
            } else null,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = if (isUser) Color.White else if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                )

                if (msg.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        msg.sources.forEach { src ->
                            SourceBadge(src, isDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(
    src: ChatSource,
    isDark: Boolean,
) {
    val (badgeBg, badgeText) = when (src.type) {
        "Wikipedia" -> Color(0x2E0284C7) to Color(0xFF38BDF8)
        "DuckDuckGo" -> Color(0x2EE25C26) to Color(0xFFFF8B53)
        "Brave Search" -> Color(0x2EFB542B) to Color(0xFFFF7A59)
        "Bookmarks" -> Color(0x2E10B981) to Color(0xFF6EE7B7)
        "On-Device Neural Model" -> ColorPurpleDim to ColorPurple
        else -> ColorOrangeDim to ColorOrangeLight
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "${src.type}: ${src.title}",
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = badgeText,
        )
    }
}

@Composable
private fun GeneratingBubble(isDark: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            color = if (isDark) Color(0x261E293B) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (isDark) GlassBorderSubtle else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = ColorOrange,
                )
                Text(
                    "Searching web & summarizing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
