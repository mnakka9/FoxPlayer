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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    onBack: () -> Unit,
    onRestoreAccess: () -> Unit,
) {
    LaunchedEffect(bookId) { viewModel.open(bookId) }

    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val pane by viewModel.pane.collectAsStateWithLifecycle()
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var showEditGenresDialog by remember { mutableStateOf(false) }
    var editGenresText by remember { mutableStateOf("") }
    var isDetectingGenres by remember { mutableStateOf(false) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        book?.title ?: "FoxPlayer",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editGenresText = book?.genres.orEmpty()
                        showEditGenresDialog = true
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Edit genres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                shape = MaterialTheme.shapes.extraLarge,
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
            ) {
                FilterChip(
                    selected = pane == DetailPane.Chapters,
                    onClick = { viewModel.setPane(DetailPane.Chapters) },
                    label = { Text("Chapters") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                )
                FilterChip(
                    selected = pane == DetailPane.Bookmarks,
                    onClick = { viewModel.setPane(DetailPane.Bookmarks) },
                    label = { Text("Bookmarks") },
                    leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showBookmarkDialog = true }) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (pane == DetailPane.Chapters) {
                ChapterList(
                    chapters = chapters,
                    currentChapterId = activeChapterId,
                    onSelect = viewModel::jumpToChapter,
                    listState = chapterListState,
                    modifier = Modifier.weight(1f),
                )
            } else {
                BookmarkList(
                    bookmarks = bookmarks,
                    onSelect = { viewModel.jumpToBookmark(it.bookmark) },
                    onDelete = { viewModel.deleteBookmark(it.bookmark) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("Add bookmark") },
            text = {
                OutlinedTextField(
                    value = bookmarkNote,
                    onValueChange = { bookmarkNote = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addBookmark(bookmarkNote)
                        bookmarkNote = ""
                        showBookmarkDialog = false
                        viewModel.setPane(DetailPane.Bookmarks)
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showEditGenresDialog) {
        AlertDialog(
            onDismissRequest = { showEditGenresDialog = false },
            title = { Text("Edit genres") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter comma-separated genres for this audiobook (e.g. Fantasy, Sci-Fi).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editGenresText,
                        onValueChange = { editGenresText = it },
                        label = { Text("Genres") },
                        placeholder = { Text("Sci-Fi, Fantasy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Auto-detect from web")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGenres(editGenresText)
                        showEditGenresDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditGenresDialog = false }) { Text("Cancel") }
            },
        )
    }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Replay30, contentDescription = "Back 30 seconds", modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = onForward) {
                Icon(Icons.Default.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(32.dp))
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
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
            Text("0.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )
    val accentBorder = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary,
        ),
    )
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 6.dp else 2.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, accentBorder, RoundedCornerShape(18.dp))
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
                    .background(gradient),
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
                        MaterialTheme.colorScheme.primary
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
                    Text(
                        formatDuration(chapter.durationMs),
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
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Now playing",
                        tint = MaterialTheme.colorScheme.onSecondary,
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
    if (bookmarks.isEmpty()) {
        Text(
            "No bookmarks yet. Tap the bookmark button to save your place.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 12.dp),
        )
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
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
                ListItem(
                    headlineContent = {
                        Text(item.chapter?.displayName ?: "Chapter")
                    },
                    supportingContent = {
                        val note = item.bookmark.note
                        val time = formatDuration(item.bookmark.positionMs)
                        Text(if (note.isBlank()) time else "$time · $note")
                    },
                    modifier = Modifier.clickable { onSelect(item) },
                    trailingContent = {
                        TextButton(onClick = { onSelect(item) }) { Text("Go") }
                    },
                )
            }
        }
    }
}
