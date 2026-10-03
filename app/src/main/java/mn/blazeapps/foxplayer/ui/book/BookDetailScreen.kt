package mn.blazeapps.foxplayer.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import coil.compose.AsyncImage
import mn.blazeapps.foxplayer.data.onnx.ModelDownloadState
import mn.blazeapps.foxplayer.data.onnx.EnrichedBookMetadata
import mn.blazeapps.foxplayer.ui.theme.BgDeep
import mn.blazeapps.foxplayer.ui.theme.BgMid
import mn.blazeapps.foxplayer.ui.theme.ColorBlueViolet
import mn.blazeapps.foxplayer.ui.theme.ColorBlueVioletDim
import mn.blazeapps.foxplayer.ui.theme.ColorBlueVioletLight
import mn.blazeapps.foxplayer.ui.theme.ColorBlueVioletSubtle
import mn.blazeapps.foxplayer.ui.theme.ColorOrange
import mn.blazeapps.foxplayer.ui.theme.ColorOrangeDim
import mn.blazeapps.foxplayer.ui.theme.ColorOrangeLight
import mn.blazeapps.foxplayer.ui.theme.ColorPurple
import mn.blazeapps.foxplayer.ui.theme.ColorRedLight
import mn.blazeapps.foxplayer.ui.theme.GlassBg
import mn.blazeapps.foxplayer.ui.theme.GlassBorder
import mn.blazeapps.foxplayer.ui.theme.GlassIconButton
import mn.blazeapps.foxplayer.ui.theme.InputBg
import mn.blazeapps.foxplayer.ui.theme.PillBadge
import mn.blazeapps.foxplayer.ui.theme.SquircleIconBox
import mn.blazeapps.foxplayer.ui.theme.TextMuted
import mn.blazeapps.foxplayer.ui.theme.TextPrimary
import mn.blazeapps.foxplayer.ui.theme.TextSecondary
import mn.blazeapps.foxplayer.ui.settings.SettingsDialog
import mn.blazeapps.foxplayer.ui.theme.ThemeMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mn.blazeapps.foxplayer.data.BookmarkWithChapter
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import mn.blazeapps.foxplayer.ui.formatDuration
import mn.blazeapps.foxplayer.ui.library.CoverArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    viewModel: BookViewModel,
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onBack: () -> Unit,
    onRestoreAccess: () -> Unit,
) {
    LaunchedEffect(bookId) { viewModel.open(bookId) }

    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val pane by viewModel.pane.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatGenerating by viewModel.isChatGenerating.collectAsStateWithLifecycle()
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var showEditGenresDialog by remember { mutableStateOf(false) }
    var editGenresText by remember { mutableStateOf("") }
    var isDetectingGenres by remember { mutableStateOf(false) }
    var showEnrichDialog by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    val modelDownloadState by viewModel.modelDownloadState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    val live = player.bookId == bookId
    val activeChapterId = if (live) player.chapterId else book?.lastChapterId
    val currentChapter = chapters.firstOrNull { it.id == activeChapterId }
        ?: chapters.getOrNull(if (live) player.currentIndex else 0)
    val chapterListState = rememberLazyListState()
    val positionMs = if (live) player.positionMs else book?.lastPositionMs ?: 0L
    val durationMs = when {
        live && player.durationMs > 0 -> player.durationMs
        else -> currentChapter?.durationMs ?: 0L
    }
    val isPlaying = live && player.isPlaying
    val speed = if (live) player.speed else 1f

    LaunchedEffect(pane, activeChapterId, chapters) {
        if (pane != DetailPane.Chapters) return@LaunchedEffect
        val chapterIndex = activeChapterId
            ?.let { targetId -> chapters.indexOfFirst { it.id == targetId } }
            ?.takeIf { it >= 0 }
            ?: return@LaunchedEffect
        chapterListState.animateScrollToItem(chapterIndex)
    }

    val isDark = MaterialTheme.colorScheme.background == BgDeep

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        book?.title ?: "FoxPlayer",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    )
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
                    IconButton(onClick = { showEnrichDialog = true }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Enrich with AI",
                            tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { showChatDialog = true }) {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = "AI Companion Chat",
                            tint = if (showChatDialog) ColorOrange else if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { viewModel.rescanChapters() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Re-scan chapters",
                            tint = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = {
                        editGenresText = book?.genres.orEmpty()
                        showEditGenresDialog = true
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = "Edit genres",
                            tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(
                            title = book?.title.orEmpty(),
                            coverPath = book?.coverPath,
                            revoked = book?.accessRevoked == true,
                            modifier = Modifier
                                .size(108.dp)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.large),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentChapter?.displayName ?: "No chapters",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val author = book?.author
                            if (!author.isNullOrBlank()) {
                                Text(
                                    author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "${chapters.size} chapters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val genres = book?.genres
                            if (!genres.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    genres,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        editGenresText = genres
                                        showEditGenresDialog = true
                                    },
                                )
                            }
                            val description = book?.description
                            if (!description.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) 12 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded },
                                )
                            }
                        }
                    }

                    if (book?.accessRevoked == true) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRestoreAccess, modifier = Modifier.fillMaxWidth()) {
                            Text("Folder access lost — pick folder again")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    PlayerSlider(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = viewModel::seekTo,
                    )
                    Spacer(Modifier.height(4.dp))
                    PlayerControls(
                        isPlaying = isPlaying,
                        speed = speed,
                        onPlayPause = viewModel::playPause,
                        onBack = viewModel::seekBack,
                        onForward = viewModel::seekForward,
                        onSpeed = viewModel::setSpeed,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = pane == DetailPane.Chapters,
                    onClick = { viewModel.setPane(DetailPane.Chapters) },
                    label = {
                        Text(
                            "Chapters",
                            fontWeight = if (pane == DetailPane.Chapters) FontWeight.Bold else FontWeight.Medium,
                            color = if (pane == DetailPane.Chapters) Color.White else if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = if (pane == DetailPane.Chapters) Color.White else if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                        selectedContainerColor = ColorBlueViolet,
                        labelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = Color.White,
                        iconColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        selectedLeadingIconColor = Color.White,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = pane == DetailPane.Chapters,
                        borderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                        selectedBorderColor = ColorBlueViolet,
                    ),
                    shape = RoundedCornerShape(20.dp),
                )
                FilterChip(
                    selected = pane == DetailPane.Bookmarks,
                    onClick = { viewModel.setPane(DetailPane.Bookmarks) },
                    label = {
                        Text(
                            "Bookmarks",
                            fontWeight = if (pane == DetailPane.Bookmarks) FontWeight.Bold else FontWeight.Medium,
                            color = if (pane == DetailPane.Bookmarks) Color.White else if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = if (pane == DetailPane.Bookmarks) Color.White else if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                        selectedContainerColor = ColorBlueViolet,
                        labelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = Color.White,
                        iconColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                        selectedLeadingIconColor = Color.White,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = pane == DetailPane.Bookmarks,
                        borderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                        selectedBorderColor = ColorBlueViolet,
                    ),
                    shape = RoundedCornerShape(20.dp),
                )
                if (pane == DetailPane.Bookmarks) {
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(
                        onClick = { showBookmarkDialog = true },
                        size = 40.dp,
                        contentColor = ColorOrangeLight,
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = "Add bookmark",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (pane) {
                DetailPane.Chapters -> {
                    ChapterList(
                        chapters = chapters,
                        currentChapterId = activeChapterId,
                        onSelect = viewModel::jumpToChapter,
                        listState = chapterListState,
                        modifier = Modifier.weight(1f),
                    )
                }
                DetailPane.Bookmarks -> {
                    BookmarkList(
                        bookmarks = bookmarks,
                        onSelect = { viewModel.jumpToBookmark(it.bookmark) },
                        onDelete = { viewModel.deleteBookmark(it.bookmark) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(24.dp),
            ),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SquircleIconBox(
                        size = 38.dp,
                        brush = Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight)),
                        shadowColor = Color(0x66F97316),
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        "Add Bookmark",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val positionText = formatDuration(positionMs)
                    Text(
                        "Bookmark at ${currentChapter?.displayName ?: "current position"} · $positionText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    OutlinedTextField(
                        value = bookmarkNote,
                        onValueChange = { bookmarkNote = it },
                        label = { Text("Note (optional)") },
                        placeholder = { Text("Enter a note...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                            focusedBorderColor = ColorBlueVioletLight,
                            unfocusedBorderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                            focusedLabelColor = ColorBlueVioletLight,
                            unfocusedLabelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = ColorOrange,
                            focusedPlaceholderColor = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addBookmark(bookmarkNote)
                        bookmarkNote = ""
                        showBookmarkDialog = false
                        viewModel.setPane(DetailPane.Bookmarks)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorOrange,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Save Bookmark", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text("Cancel", color = if (isDark) ColorBlueVioletSubtle else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showEditGenresDialog) {
        AlertDialog(
            onDismissRequest = { showEditGenresDialog = false },
            containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(24.dp),
            ),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SquircleIconBox(
                        size = 38.dp,
                        brush = Brush.linearGradient(listOf(ColorBlueViolet, ColorPurple)),
                        shadowColor = Color(0x666366F1),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        "Edit Genres",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter comma-separated genres for this audiobook (e.g. Fantasy, Sci-Fi).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editGenresText,
                        onValueChange = { editGenresText = it },
                        label = { Text("Genres") },
                        placeholder = { Text("Sci-Fi, Fantasy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                            focusedBorderColor = ColorBlueVioletLight,
                            unfocusedBorderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                            focusedLabelColor = ColorBlueVioletLight,
                            unfocusedLabelColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            cursorColor = ColorOrange,
                            focusedPlaceholderColor = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    isDetectingGenres = true
                                    val detected = viewModel.autoDetectGenres()
                                    if (!detected.isNullOrBlank()) {
                                        editGenresText = detected
                                    }
                                    isDetectingGenres = false
                                }
                            },
                            enabled = !isDetectingGenres,
                        ) {
                            if (isDetectingGenres) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ColorOrange,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                "Auto-detect from web",
                                color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateGenres(editGenresText)
                        showEditGenresDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorBlueViolet,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGenresDialog = false }) {
                    Text("Cancel", color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showEnrichDialog) {
        EnrichMetadataDialog(
            bookTitle = book?.title.orEmpty(),
            bookAuthor = book?.author,
            currentGenres = book?.genres.orEmpty(),
            currentDescription = book?.description.orEmpty(),
            isModelDownloaded = viewModel.isModelDownloaded(),
            onOpenSettings = {
                showEnrichDialog = false
                showSettingsDialog = true
            },
            onEnrich = { query -> viewModel.enrichBookMetadata(query) },
            onApply = { genres, description, coverUrl ->
                viewModel.applyEnrichedMetadata(genres, description, coverUrl)
                showEnrichDialog = false
            },
            onDismiss = { showEnrichDialog = false },
        )
    }

    if (showChatDialog) {
        BookAiChatDialog(
            messages = chatMessages,
            isGenerating = isChatGenerating,
            onSendMessage = viewModel::sendChatMessage,
            onClearChat = viewModel::clearChat,
            onDismiss = { showChatDialog = false },
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            modelDownloadState = modelDownloadState,
            isModelDownloaded = viewModel.isModelDownloaded(),
            modelSizeBytes = viewModel.getModelSizeBytes(),
            freeSpaceBytes = viewModel.getFreeSpaceBytes(),
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onDownloadModel = viewModel::downloadOnnxModel,
            onDeleteModel = { viewModel.deleteOnnxModel() },
            onDismiss = { showSettingsDialog = false },
        )
    }
}

@Composable
private fun EnrichMetadataDialog(
    bookTitle: String,
    bookAuthor: String?,
    currentGenres: String,
    currentDescription: String,
    isModelDownloaded: Boolean,
    onOpenSettings: () -> Unit,
    onEnrich: suspend (String) -> EnrichedBookMetadata?,
    onApply: (genres: String?, description: String?, coverUrl: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    var searchQuery by remember { mutableStateOf(bookTitle) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var enrichedResult by remember { mutableStateOf<EnrichedBookMetadata?>(null) }
    var editableGenres by remember { mutableStateOf(currentGenres) }
    var editableDesc by remember { mutableStateOf(currentDescription) }
    var updateCover by remember { mutableStateOf(true) }
    var showLogs by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
        titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
        textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.border(
            BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
            RoundedCornerShape(24.dp),
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SquircleIconBox(
                    size = 38.dp,
                    brush = Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight)),
                    shadowColor = Color(0x66F97316),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        "Enrich with AI",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Web search & Local ONNX model",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    if (isModelDownloaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ColorOrangeLight,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "On-Device AI Active (SmolLM2-360M)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "Web search active (SmolLM2-360M in Settings)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = onOpenSettings,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    "Settings",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                item {
                    // Search Query Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Query") },
                        placeholder = { Text("Book title...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surface,
                            focusedBorderColor = ColorBlueVioletLight,
                            unfocusedBorderColor = if (isDark) GlassBorder else MaterialTheme.colorScheme.outline,
                        ),
                    )
                }

                item {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isAnalyzing = true
                                val res = onEnrich(searchQuery)
                                enrichedResult = res
                                if (res != null) {
                                    if (!res.genres.isNullOrBlank()) editableGenres = res.genres
                                    if (!res.description.isNullOrBlank()) editableDesc = res.description
                                }
                                isAnalyzing = false
                            }
                        },
                        enabled = !isAnalyzing && searchQuery.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorOrange,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Searching & Analyzing...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Search & Extract Metadata", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (enrichedResult != null) {
                    item {
                        val result = enrichedResult!!
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (result.usedOnnxModel) {
                                PillBadge(
                                    text = "Analyzed with ONNX Model",
                                    backgroundColor = ColorOrangeDim,
                                    borderColor = ColorOrange,
                                    contentColor = ColorOrangeLight,
                                )
                            }

                            // Cover preview
                            if (!result.coverUrl.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    AsyncImage(
                                        model = result.coverUrl,
                                        contentDescription = "Discovered cover",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "New Cover Discovered",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = updateCover,
                                                onCheckedChange = { updateCover = it },
                                                colors = CheckboxDefaults.colors(checkedColor = ColorOrange),
                                            )
                                            Text(
                                                "Update cover image",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }

                            // Editable Genres
                            OutlinedTextField(
                                value = editableGenres,
                                onValueChange = { editableGenres = it },
                                label = { Text("Genres") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            // Editable Description
                            OutlinedTextField(
                                value = editableDesc,
                                onValueChange = { editableDesc = it },
                                label = { Text("Description") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            // Expandable Activity & Model Log
                            if (result.logs.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0xFF0F1535) else MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showLogs = !showLogs },
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Activity & Model Logs (${result.logs.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = if (showLogs) "Hide" else "Show",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (showLogs) {
                                            Spacer(Modifier.height(8.dp))
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isDark) Color(0x66000000) else Color(0x11000000),
                                                        RoundedCornerShape(8.dp),
                                                    )
                                                    .padding(8.dp),
                                            ) {
                                                result.logs.forEach { logLine ->
                                                    Text(
                                                        text = logLine,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                        ),
                                                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cover = if (updateCover) enrichedResult?.coverUrl else null
                    onApply(editableGenres, editableDesc, cover)
                },
                enabled = enrichedResult != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorBlueViolet,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Apply to Book", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun PlayerSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val duration = durationMs.coerceAtLeast(0)
    var dragging by remember { mutableFloatStateOf(-1f) }
    val value = if (dragging >= 0) dragging else {
        if (duration <= 0) 0f else (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }
    Column {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (duration > 0 && dragging >= 0) {
                    onSeek((dragging * duration).toLong())
                }
                dragging = -1f
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = if (dragging >= 0 && duration > 0) (dragging * duration).toLong() else positionMs
            Text(formatDuration(shown), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    speed: Float,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Replay30,
                    contentDescription = "Back 30 seconds",
                    modifier = Modifier.size(32.dp),
                    tint = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        spotColor = Color(0x66F97316),
                        ambientColor = Color(0x33F97316),
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ColorOrange, ColorOrangeLight),
                        ),
                    )
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = onForward) {
                Icon(
                    Icons.Default.Forward30,
                    contentDescription = "Forward 30 seconds",
                    modifier = Modifier.size(32.dp),
                    tint = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SpeedSlider(
            speed = speed,
            onSpeed = onSpeed,
        )
    }
}

@Composable
private fun SpeedSlider(
    speed: Float,
    onSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    var dragging by remember { mutableFloatStateOf(-1f) }
    val current = if (dragging > 0f) dragging else speed
    val formattedSpeed = String.format(java.util.Locale.US, "%.2fx", current)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    dragging = -1f
                    onSpeed(1.0f)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = formattedSpeed,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (kotlin.math.abs(current - 1.0f) < 0.04f) {
                        if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary
                    } else {
                        if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.tertiary
                    },
                )
            }
        }
        Slider(
            value = current.coerceIn(0.5f, 2.5f),
            onValueChange = { raw ->
                val stepped = (kotlin.math.round(raw * 20f) / 20f).coerceIn(0.5f, 2.5f)
                dragging = stepped
                onSpeed(stepped)
            },
            onValueChangeFinished = {
                if (dragging > 0f) {
                    onSpeed(dragging)
                }
                dragging = -1f
            },
            valueRange = 0.5f..2.5f,
            steps = 39,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0.5x", style = MaterialTheme.typography.labelSmall, color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1.0x", style = MaterialTheme.typography.labelSmall, color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2.5x", style = MaterialTheme.typography.labelSmall, color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<ChapterEntity>,
    currentChapterId: Long?,
    onSelect: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(chapters, key = { _, item -> item.id }) { index, chapter ->
            ChapterCard(
                index = index,
                chapter = chapter,
                selected = chapter.id == currentChapterId,
                onClick = { onSelect(index) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterCard(
    index: Int,
    chapter: ChapterEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    val gradient = Brush.linearGradient(
        colors = listOf(
            ColorBlueViolet,
            ColorPurple,
        ),
    )
    val accentBorder = Brush.linearGradient(
        colors = listOf(
            ColorOrange,
            ColorPurple,
            ColorBlueViolet,
        ),
    )
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                ColorBlueViolet.copy(alpha = 0.22f)
            } else if (isDark) {
                GlassBg
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 6.dp else 0.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, accentBorder, RoundedCornerShape(18.dp))
                } else if (isDark) {
                    Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(18.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight)) else gradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chapter.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        ColorOrangeLight
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    val durationText = if (chapter.startOffsetMs > 0L) {
                        "${formatDuration(chapter.startOffsetMs)} · ${formatDuration(chapter.durationMs)}"
                    } else {
                        formatDuration(chapter.durationMs)
                    }
                    Text(
                        durationText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ColorOrange),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Now playing",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play chapter",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkList(
    bookmarks: List<BookmarkWithChapter>,
    onSelect: (BookmarkWithChapter) -> Unit,
    onDelete: (BookmarkWithChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    if (bookmarks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "No Bookmarks Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Tap the bookmark button above to save your listening spot.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(bookmarks.size, key = { bookmarks[it].bookmark.id }) { index ->
            val item = bookmarks[index]
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onDelete(item)
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete bookmark",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
                enableDismissFromStartToEnd = false,
            ) {
                Card(
                    onClick = { onSelect(item) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isDark) {
                                Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(ColorPurple, ColorBlueViolet))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.chapter?.displayName ?: "Chapter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isDark) ColorBlueVioletDim else MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        formatDuration(item.bookmark.positionMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (item.bookmark.note.isNotBlank()) {
                                    Text(
                                        item.bookmark.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onDelete(item) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete bookmark",
                                tint = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) ColorOrangeDim else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Jump to bookmark",
                                tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
