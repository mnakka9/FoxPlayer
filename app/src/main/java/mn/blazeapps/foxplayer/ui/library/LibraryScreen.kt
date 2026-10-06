package mn.blazeapps.foxplayer.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import mn.blazeapps.foxplayer.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import mn.blazeapps.foxplayer.data.LibraryBook
import mn.blazeapps.foxplayer.ui.theme.BgDeep
import mn.blazeapps.foxplayer.ui.theme.ColorBlueViolet
import mn.blazeapps.foxplayer.ui.theme.ColorBlueVioletLight
import mn.blazeapps.foxplayer.ui.theme.ColorOrange
import mn.blazeapps.foxplayer.ui.theme.ColorOrangeLight
import mn.blazeapps.foxplayer.ui.theme.GlassBg
import mn.blazeapps.foxplayer.ui.theme.GlassBorder
import mn.blazeapps.foxplayer.ui.theme.InputBg
import mn.blazeapps.foxplayer.ui.theme.SquircleIconBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import mn.blazeapps.foxplayer.ui.theme.ColorRed
import mn.blazeapps.foxplayer.ui.theme.TextMuted
import mn.blazeapps.foxplayer.ui.theme.TextPrimary
import mn.blazeapps.foxplayer.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mn.blazeapps.foxplayer.data.ai.LocalChatEngineMode
import mn.blazeapps.foxplayer.data.ai.LocalLlmClient
import mn.blazeapps.foxplayer.ui.settings.SettingsDialog
import mn.blazeapps.foxplayer.ui.theme.ThemeMode
import mn.blazeapps.foxplayer.FoxPlayerApplication
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenBook: (Long) -> Unit,
    onAddFolder: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as FoxPlayerApplication
    val geminiPreferences = app.container.geminiPreferences
    val isGeminiChatEnabled by geminiPreferences.isChatEnabled.collectAsStateWithLifecycle()
    val customGeminiApiKey by geminiPreferences.customApiKey.collectAsStateWithLifecycle()
    val selectedGeminiModel by geminiPreferences.selectedModel.collectAsStateWithLifecycle()
    val localChatPreferences = app.container.localChatPreferences
    val localChatEngineMode by localChatPreferences.engineMode.collectAsStateWithLifecycle()
    val foundryEndpoint by localChatPreferences.foundryEndpoint.collectAsStateWithLifecycle()
    val foundryModel by localChatPreferences.foundryModel.collectAsStateWithLifecycle()

    val books by viewModel.books.collectAsStateWithLifecycle()
    val visibleBooks by viewModel.visibleBooks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val availableGenres by viewModel.availableGenres.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val modelDownloadState by viewModel.modelDownloadState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<LibraryBook?>(null) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background == BgDeep

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent)) {
                TopAppBar(
                    navigationIcon = {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = "FoxPlayer",
                            modifier = Modifier
                                .padding(start = 12.dp, end = 4.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    },
                    title = {
                        Column {
                            Text(
                                "FoxPlayer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                librarySubtitle(books.size, visibleBooks.size, searchQuery, filter, selectedGenre),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showThemePicker = true }) {
                            Icon(
                                imageVector = if (themeMode == ThemeMode.DARK) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                                contentDescription = "Change theme",
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
                if (books.isNotEmpty()) {
                    LibraryControls(
                        query = searchQuery,
                        filter = filter,
                        selectedGenre = selectedGenre,
                        availableGenres = availableGenres,
                        themeMode = themeMode,
                        onQueryChange = viewModel::setSearchQuery,
                        onFilterChange = viewModel::setFilter,
                        onGenreChange = viewModel::setSelectedGenre,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFolder,
                icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                text = { Text("Add book", fontWeight = FontWeight.Bold) },
                containerColor = ColorOrange,
                contentColor = Color.White,
                modifier = Modifier.shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = Color(0x66F97316),
                    ambientColor = Color(0x33F97316),
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                books.isEmpty() && !importing -> {
                    EmptyLibrary(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    if (visibleBooks.isEmpty()) {
                        EmptySearchResults(
                            query = searchQuery,
                            filter = filter,
                            selectedGenre = selectedGenre,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(168.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(visibleBooks, key = { it.book.id }) { item ->
                                BookCard(
                                    item = item,
                                    onClick = { onOpenBook(item.book.id) },
                                    onLongClick = { pendingDelete = item },
                                )
                            }
                        }
                    }
                }
            }
            if (importing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showThemePicker) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(24.dp),
            ),
            icon = {
                Icon(
                    if (themeMode == ThemeMode.DARK) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = null,
                    tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(
                    "Appearance",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    "Choose light or dark mode for FoxPlayer.",
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onThemeModeChange(ThemeMode.DARK)
                        showThemePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (themeMode == ThemeMode.DARK) ColorOrange else ColorBlueViolet,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Dark", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onThemeModeChange(ThemeMode.LIGHT)
                        showThemePicker = false
                    },
                ) {
                    Text("Light", color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
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
            localChatEngineMode = localChatEngineMode,
            onLocalChatEngineModeChange = localChatPreferences::setEngineMode,
            foundryEndpoint = foundryEndpoint,
            onFoundryEndpointChange = localChatPreferences::setFoundryEndpoint,
            foundryModel = foundryModel,
            onFoundryModelChange = localChatPreferences::setFoundryModel,
            onTestFoundryConnection = {
                withContext(Dispatchers.IO) {
                    val ipcStatus = mn.blazeapps.foxplayer.data.ai.FoundryIpcManager.checkStatus(context)
                    if (ipcStatus.isConnected) {
                        LocalLlmClient.ConnectionStatus(
                            isSuccess = true,
                            latencyMs = ipcStatus.latencyMs,
                            models = ipcStatus.models.ifEmpty { listOf(foundryModel) },
                            message = ipcStatus.message.ifBlank { "IPC Connected (${ipcStatus.latencyMs}ms)" },
                        )
                    } else if (ipcStatus.isAppInstalled) {
                        LocalLlmClient.ConnectionStatus(
                            isSuccess = false,
                            latencyMs = ipcStatus.latencyMs,
                            models = ipcStatus.models,
                            message = ipcStatus.message,
                        )
                    } else {
                        val http = LocalLlmClient(endpointUrl = foundryEndpoint, modelName = foundryModel).testConnection()
                        if (http.isSuccess) {
                            http
                        } else {
                            LocalLlmClient.ConnectionStatus(
                                isSuccess = false,
                                latencyMs = 0,
                                models = emptyList(),
                                message = "Foundry app not installed",
                            )
                        }
                    }
                }
            },
            isGeminiChatEnabled = isGeminiChatEnabled,
            onGeminiChatEnabledChange = geminiPreferences::setChatEnabled,
            geminiApiKey = customGeminiApiKey,
            onGeminiApiKeyChange = geminiPreferences::setCustomApiKey,
            selectedGeminiModel = selectedGeminiModel,
            onSelectedGeminiModelChange = geminiPreferences::setSelectedModel,
            onDownloadModel = viewModel::downloadOnnxModel,
            onDeleteModel = { viewModel.deleteOnnxModel() },
            onDismiss = { showSettingsDialog = false },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(24.dp),
            ),
            title = {
                Text(
                    "Remove from library?",
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    "“${target.book.title}” will be removed from FoxPlayer. Audio files on disk are not deleted.",
                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeBook(target.book.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorRed,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

private fun librarySubtitle(
    total: Int,
    visible: Int,
    query: String,
    filter: LibraryFilter,
    selectedGenre: String? = null,
): String {
    val searching = query.isNotBlank() || filter != LibraryFilter.ALL || !selectedGenre.isNullOrBlank()
    return if (searching) {
        "$visible of $total books"
    } else {
        if (total == 1) "1 book" else "$total books"
    }
}

@Composable
private fun LibraryControls(
    query: String,
    filter: LibraryFilter,
    selectedGenre: String?,
    availableGenres: List<String>,
    themeMode: ThemeMode,
    onQueryChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onGenreChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .then(
                    if (isDark) {
                        Modifier.border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(28.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search titles, authors, genres, chapters",
                        color = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search library",
                        tint = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = if (isDark) TextMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = ColorOrange,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = if (isDark) InputBg else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { option ->
                val selected = filter == option
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label()) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
        if (availableGenres.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedGenre == null,
                    onClick = { onGenreChange(null) },
                    label = { Text("All Genres") },
                    leadingIcon = if (selectedGenre == null) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
                availableGenres.forEach { genre ->
                    val isSelected = selectedGenre.equals(genre, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGenreChange(genre) },
                        label = { Text(genre) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

private fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.ALL -> "All"
    LibraryFilter.NEW -> "New"
    LibraryFilter.IN_PROGRESS -> "In progress"
    LibraryFilter.FINISHED -> "Finished"
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyStateIcon(Icons.AutoMirrored.Filled.MenuBook)
        Text(
            "Welcome to FoxPlayer",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Tap Add book and pick a folder. Each folder becomes one book; audio files inside become chapters. Titles, authors, and covers are read from ID3 tags when available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptySearchResults(
    query: String,
    filter: LibraryFilter,
    selectedGenre: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyStateIcon(Icons.Default.SearchOff)
        Text(
            "No matching books",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        val detail = buildString {
            if (query.isNotBlank()) {
                append("Nothing matches “${query.trim()}”")
            } else if (!selectedGenre.isNullOrBlank()) {
                append("Nothing in genre “$selectedGenre”")
            } else {
                append("Nothing in ${filter.label().lowercase()}")
            }
            append(". Try another title, author, genre, or chapter name.")
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyStateIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(40.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    item: LibraryBook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val percent = item.progressPercent
    val shape = RoundedCornerShape(18.dp)
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box {
                CoverArt(
                    title = item.book.title,
                    coverPath = item.book.coverPath,
                    revoked = item.book.accessRevoked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (percent >= 100) {
                                MaterialTheme.colorScheme.tertiary
                            } else if (percent <= 0) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (percent <= 0) "New" else "$percent%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = item.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.book.author?.takeIf { it.isNotBlank() } ?: "Unknown author",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val genres = item.book.genres
                if (!genres.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = genres,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${item.chapters.size} chapters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            percent <= 0 -> "Not started"
                            percent >= 100 -> "Finished"
                            else -> "$percent% complete"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun CoverArt(
    title: String,
    coverPath: String?,
    revoked: Boolean,
    modifier: Modifier = Modifier,
) {
    val file = coverPath?.let { File(it) }?.takeIf { it.exists() }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("${file.absolutePath}-${file.lastModified()}")
                    .diskCacheKey("${file.absolutePath}-${file.lastModified()}")
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (revoked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Access lost", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
