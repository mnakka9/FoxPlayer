package mn.blazeapps.foxplayer.ui.book

import android.widget.Toast
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mn.blazeapps.foxplayer.data.ai.AiChatMessage
import mn.blazeapps.foxplayer.data.ai.ChatQueryScope
import mn.blazeapps.foxplayer.data.ai.ChatSender
import mn.blazeapps.foxplayer.data.ai.ChatSource
import mn.blazeapps.foxplayer.data.ai.LocalChatEngineMode
import mn.blazeapps.foxplayer.ui.theme.*

@Composable
fun BookAiChatDialog(
    messages: List<AiChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String, ChatQueryScope) -> Unit,
    onClearChat: () -> Unit,
    onAddBookmarkNote: (String) -> Unit = {},
    engineMode: LocalChatEngineMode = LocalChatEngineMode.FAST_LOCAL,
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    var inputText by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf<ChatQueryScope>(ChatQueryScope.AUTO) }
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
                                when (engineMode) {
                                    LocalChatEngineMode.FOUNDRY_LOCAL -> "Microsoft Foundry Local (On-Device LLM)"
                                    LocalChatEngineMode.FAST_LOCAL -> "Fast Local · Web search & Notes"
                                },
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

                // Scope Filter: Auto vs General (Web & AI) vs Bookmark Notes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedScope == ChatQueryScope.AUTO,
                        onClick = { selectedScope = ChatQueryScope.AUTO },
                        label = { Text("⚡ Auto", fontSize = 11.sp, fontWeight = if (selectedScope == ChatQueryScope.AUTO) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorBlueViolet,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedScope == ChatQueryScope.AUTO,
                            borderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                            selectedBorderColor = ColorBlueViolet,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )
                    FilterChip(
                        selected = selectedScope == ChatQueryScope.GENERAL,
                        onClick = { selectedScope = ChatQueryScope.GENERAL },
                        label = { Text("🌐 General (Web & AI)", fontSize = 11.sp, fontWeight = if (selectedScope == ChatQueryScope.GENERAL) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorBlueViolet,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedScope == ChatQueryScope.GENERAL,
                            borderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                            selectedBorderColor = ColorBlueViolet,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )
                    FilterChip(
                        selected = selectedScope == ChatQueryScope.BOOKMARK_NOTES,
                        onClick = { selectedScope = ChatQueryScope.BOOKMARK_NOTES },
                        label = { Text("📝 Bookmark Notes", fontSize = 11.sp, fontWeight = if (selectedScope == ChatQueryScope.BOOKMARK_NOTES) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ColorOrange,
                            selectedLabelColor = Color.White,
                            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedScope == ChatQueryScope.BOOKMARK_NOTES,
                            borderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                            selectedBorderColor = ColorOrange,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )

                    Spacer(Modifier.weight(1f))

                    if (messages.isNotEmpty()) {
                        TextButton(
                            onClick = onClearChat,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear Chat",
                                modifier = Modifier.size(12.dp),
                                tint = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.width(2.dp))
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
                            ChatBubble(
                                msg = msg,
                                isDark = isDark,
                                onAddBookmarkNote = onAddBookmarkNote,
                            )
                        }

                        if (isGenerating) {
                            item {
                                GeneratingBubble(isDark = isDark, engineMode = engineMode)
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
                                when (selectedScope) {
                                    ChatQueryScope.GENERAL -> "Ask anything with live web search & AI..."
                                    ChatQueryScope.BOOKMARK_NOTES -> "Search bookmark notes in this audiobook..."
                                    ChatQueryScope.AUTO -> "Search notes or ask anything with web search & AI..."
                                },
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
                                onSendMessage(query, selectedScope)
                            }
                        }),
                    )

                    IconButton(
                        onClick = {
                            val query = inputText.trim()
                            if (query.isNotBlank()) {
                                inputText = ""
                                onSendMessage(query, selectedScope)
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
fun BookAiChatDialog(
    messages: List<AiChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onAddBookmarkNote: (String) -> Unit = {},
    engineMode: LocalChatEngineMode = LocalChatEngineMode.FAST_LOCAL,
    onDismiss: () -> Unit,
) {
    BookAiChatDialog(
        messages = messages,
        isGenerating = isGenerating,
        onSendMessage = { query, _ -> onSendMessage(query) },
        onClearChat = onClearChat,
        onAddBookmarkNote = onAddBookmarkNote,
        engineMode = engineMode,
        onDismiss = onDismiss,
    )
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
    onAddBookmarkNote: (String) -> Unit,
) {
    val isUser = msg.sender == ChatSender.User
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
            modifier = if (isUser) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth(0.96f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Selectable text & rendered markdown
                SelectionContainer {
                    MarkdownContentView(
                        markdown = msg.text,
                        isDark = isDark,
                        isUser = isUser,
                    )
                }

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

                if (!isUser) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val cleanPlainText = stripMarkdownForPlainText(msg.text)
                                onAddBookmarkNote(cleanPlainText)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = "Add to Chapter Notes",
                                tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Add Note",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                            )
                        }

                        IconButton(
                            onClick = {
                                val cleanPlainText = stripMarkdownForPlainText(msg.text)
                                clipboardManager.setText(AnnotatedString(cleanPlainText))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun stripMarkdownForPlainText(text: String): String {
    return text
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace("**", "")
        .replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
        .replace("`", "")
        .replace(Regex("(?m)^>\\s+"), "")
        .trim()
}

fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val boldContent = text.substring(i + 2, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(boldContent)
                    pop()
                    i = end + 2
                    continue
                }
            }
            if (text[i] == '*' && (i == 0 || text[i - 1] != '*') && (i + 1 == len || text[i + 1] != '*')) {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && (end + 1 == len || text[end + 1] != '*')) {
                    val italicContent = text.substring(i + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor))
                    append(italicContent)
                    pop()
                    i = end + 1
                    continue
                }
            }
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    val codeContent = text.substring(i + 1, end)
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium))
                    append(codeContent)
                    pop()
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}

@Composable
fun MarkdownContentView(
    markdown: String,
    isDark: Boolean,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryTextColor = if (isUser) Color.White else if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface
    val headingColor = if (isUser) Color.White else if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary
    val quoteBorderColor = if (isUser) Color.White.copy(alpha = 0.6f) else ColorOrange

    val lines = remember(markdown) { markdown.lines() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> {
                    Spacer(Modifier.height(4.dp))
                }
                trimmed.startsWith("### ") -> {
                    val headerText = trimmed.removePrefix("### ").trim()
                    Text(
                        text = parseInlineMarkdown(headerText, headingColor),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = headingColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                trimmed.startsWith("## ") -> {
                    val headerText = trimmed.removePrefix("## ").trim()
                    Text(
                        text = parseInlineMarkdown(headerText, headingColor),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = headingColor,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                trimmed.startsWith("# ") -> {
                    val headerText = trimmed.removePrefix("# ").trim()
                    Text(
                        text = parseInlineMarkdown(headerText, headingColor),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = headingColor,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                trimmed.startsWith("> ") -> {
                    val quoteText = trimmed.removePrefix("> ").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0x22F97316) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(quoteBorderColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(quoteText, primaryTextColor),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = primaryTextColor,
                        )
                    }
                }
                trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletText = trimmed.replace(Regex("^[•\\-*]\\s+"), "")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = parseInlineMarkdown(bulletText, primaryTextColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryTextColor,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                    val match = Regex("^(\\d+\\.)\\s+(.*)").find(trimmed)
                    val num = match?.groupValues?.get(1) ?: "•"
                    val itemText = match?.groupValues?.get(2) ?: trimmed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = num,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = parseInlineMarkdown(itemText, primaryTextColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryTextColor,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed, primaryTextColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryTextColor,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun SourceBadge(
    src: ChatSource,
    isDark: Boolean,
) {
    val (badgeBg, badgeText) = when (src.type) {
        "Wikipedia", "Live Web Search" -> Color(0x2E0284C7) to Color(0xFF38BDF8)
        "DuckDuckGo" -> Color(0x2EE25C26) to Color(0xFFFF8B53)
        "Brave Search" -> Color(0x2EFB542B) to Color(0xFFFF7A59)
        "Bookmarks" -> Color(0x2E10B981) to Color(0xFF6EE7B7)
        "On-Device Neural Model" -> ColorPurpleDim to ColorPurple
        "Microsoft Foundry Local", "Foundry Local Fallback", "Foundry Local IPC" -> Color(0x2E10B981) to Color(0xFF34D399)
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
private fun GeneratingBubble(
    isDark: Boolean,
    engineMode: LocalChatEngineMode = LocalChatEngineMode.FAST_LOCAL,
) {
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
                    color = if (engineMode == LocalChatEngineMode.FOUNDRY_LOCAL) ColorBlueVioletLight else ColorOrange,
                )
                Text(
                    when (engineMode) {
                        LocalChatEngineMode.FOUNDRY_LOCAL -> "Foundry Local reasoning & searching..."
                        LocalChatEngineMode.FAST_LOCAL -> "Searching web & summarizing..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
