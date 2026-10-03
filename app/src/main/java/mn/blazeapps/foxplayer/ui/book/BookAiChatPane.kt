package mn.blazeapps.foxplayer.ui.book

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mn.blazeapps.foxplayer.data.ai.AiChatMessage
import mn.blazeapps.foxplayer.data.ai.ChatSender
import mn.blazeapps.foxplayer.data.ai.ChatSource
import mn.blazeapps.foxplayer.ui.theme.*

@Composable
fun BookAiChatDialog(
    messages: List<AiChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(
                1.dp,
                if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            ) {
                // Header: icon, title, subtitle & close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SquircleIconBox(
                            size = 38.dp,
                            brush = Brush.linearGradient(listOf(ColorBlueViolet, ColorOrange)),
                            shadowColor = Color(0x666366F1),
                        ) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                "AI Companion Chat",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Web search & Notes",
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

                Spacer(Modifier.height(8.dp))

                // Action row: single internal notes filter + optional clear chat
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                // Chat history container with interactive vertical scrollbar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
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

                    // Interactive scrollbar
                    ChatScrollbar(
                        listState = listState,
                        coroutineScope = coroutineScope,
                        isDark = isDark,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(8.dp),
                    )
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
                                if (inputText.isNotBlank()) {
                                    Brush.linearGradient(listOf(ColorBlueViolet, ColorOrange))
                                } else {
                                    Brush.linearGradient(listOf(Color(0x336366F1), Color(0x33F97316)))
                                }
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.White else if (isDark) TextMuted else Color.LightGray,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScrollbar(
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val totalItems = listState.layoutInfo.totalItemsCount
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val trackHeightPx = constraints.maxHeight.toFloat()

        if (totalItems > 1 && visibleItems.isNotEmpty() && trackHeightPx > 0) {
            val visibleCount = visibleItems.size
            val thumbHeightFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.12f, 1f)
            val thumbHeightPx = trackHeightPx * thumbHeightFraction

            val firstVisibleIndex = listState.firstVisibleItemIndex
            val maxIndex = (totalItems - visibleCount).coerceAtLeast(1)
            val scrollProgress = (firstVisibleIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
            val thumbOffsetPx = (trackHeightPx - thumbHeightPx) * scrollProgress

            // Interactive track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDark) Color(0x1AFFFFFF) else Color(0x1A000000))
                    .pointerInput(totalItems) {
                        detectTapGestures { offset ->
                            val touchRatio = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val targetIndex = (touchRatio * (totalItems - 1)).toInt()
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    }
                    .pointerInput(totalItems) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                change.consume()
                                val touchRatio = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                                val targetIndex = (touchRatio * (totalItems - 1)).toInt()
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        )
                    }
            ) {
                // Draggable thumb indicator
                val density = LocalDensity.current
                val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
                val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = thumbOffsetDp)
                        .height(thumbHeightDp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isDragging) {
                                Brush.verticalGradient(listOf(ColorOrange, ColorOrangeLight))
                            } else {
                                Brush.verticalGradient(listOf(ColorBlueVioletLight, ColorBlueViolet))
                            }
                        ),
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ColorBlueViolet, ColorOrange))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
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
                BorderStroke(1.dp, if (isDark) GlassBorderSubtle else MaterialTheme.colorScheme.outlineVariant)
            } else null,
            modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                )

                if (msg.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
