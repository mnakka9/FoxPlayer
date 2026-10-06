package mn.blazeapps.foxplayer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import mn.blazeapps.foxplayer.data.ai.FoundryIpcManager
import mn.blazeapps.foxplayer.data.ai.LocalChatEngineMode
import mn.blazeapps.foxplayer.data.ai.LocalChatPreferences
import mn.blazeapps.foxplayer.data.ai.LocalLlmClient
import mn.blazeapps.foxplayer.data.onnx.ModelDownloadState
import mn.blazeapps.foxplayer.ui.theme.*
import java.util.Locale

@Composable
fun SettingsDialog(
    modelDownloadState: ModelDownloadState,
    isModelDownloaded: Boolean,
    modelSizeBytes: Long,
    freeSpaceBytes: Long,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    localChatEngineMode: LocalChatEngineMode = LocalChatEngineMode.FAST_LOCAL,
    onLocalChatEngineModeChange: (LocalChatEngineMode) -> Unit = {},
    foundryEndpoint: String = LocalChatPreferences.DEFAULT_ENDPOINT,
    onFoundryEndpointChange: (String) -> Unit = {},
    foundryModel: String = LocalChatPreferences.DEFAULT_MODEL,
    onFoundryModelChange: (String) -> Unit = {},
    onTestFoundryConnection: (suspend () -> LocalLlmClient.ConnectionStatus)? = null,
    isGeminiChatEnabled: Boolean = false,
    onGeminiChatEnabledChange: (Boolean) -> Unit = {},
    geminiApiKey: String = "",
    onGeminiApiKeyChange: (String) -> Unit = {},
    selectedGeminiModel: String = "gemini-1.5-flash",
    onSelectedGeminiModelChange: (String) -> Unit = {},
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onRescanChapters: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isFoundryAppInstalled by remember { mutableStateOf(FoundryIpcManager.isFoundryAppInstalled(context)) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionStatusText by remember { mutableStateOf<String?>(null) }
    var isConnectionSuccess by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFoundryDeleteConfirm by remember { mutableStateOf(false) }

    val foundryDownloadState by FoundryIpcManager.downloadState.collectAsState()
    var isFoundryModelCached by remember { mutableStateOf(false) }
    var foundryModelEstimatedSizeBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(foundryModel, isFoundryAppInstalled) {
        if (isFoundryAppInstalled && FoundryIpcManager.isSupportedOs()) {
            isFoundryModelCached = FoundryIpcManager.isModelCached(context, foundryModel)
            foundryModelEstimatedSizeBytes = FoundryIpcManager.getModelSizeBytes(context, foundryModel)
        } else {
            isFoundryModelCached = false
        }
    }

    LaunchedEffect(foundryDownloadState) {
        if (foundryDownloadState is FoundryIpcManager.FoundryDownloadState.Ready &&
            FoundryIpcManager.isSameModel((foundryDownloadState as FoundryIpcManager.FoundryDownloadState.Ready).modelAlias, foundryModel)
        ) {
            isFoundryModelCached = true
            foundryModelEstimatedSizeBytes = FoundryIpcManager.getModelSizeBytes(context, foundryModel)
        }
    }

    var dynamicCatalogModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(isFoundryAppInstalled) {
        if (isFoundryAppInstalled && FoundryIpcManager.isSupportedOs()) {
            try {
                val status = FoundryIpcManager.checkStatus(context)
                val combined = (status.cachedModels + status.models).distinct()
                if (combined.isNotEmpty()) {
                    dynamicCatalogModels = combined.map { alias ->
                        val label = when {
                            alias.contains("0.5b", ignoreCase = true) -> "Qwen 2.5 0.5B ($alias)"
                            alias.contains("1.5b", ignoreCase = true) -> "Qwen 2.5 1.5B ($alias)"
                            alias.contains("phi", ignoreCase = true) -> "Microsoft Phi-3.5-mini ($alias)"
                            alias.contains("coder", ignoreCase = true) -> "Qwen 2.5 Coder ($alias)"
                            else -> alias
                        }
                        alias to label
                    }
                }
            } catch (_: Exception) {}
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
                .fillMaxHeight(0.90f),
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
                    .padding(16.dp),
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
                        SquircleIconBox(
                            size = 38.dp,
                            brush = Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight)),
                            shadowColor = Color(0x66F97316),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "On-Device AI & Preferences",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Section 1: Local AI Companion Engine
                    item {
                        SectionHeader(
                            title = "Local AI Companion Engine",
                            icon = Icons.Default.AutoAwesome,
                            iconTint = ColorOrangeLight,
                            isDark = isDark,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Select which engine drives on-device local chat. (Google Gemini Cloud AI is configured independently below).",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Engine Option 1: Fast Local Agent
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                border = BorderStroke(
                                    if (localChatEngineMode == LocalChatEngineMode.FAST_LOCAL) 2.dp else 1.dp,
                                    if (localChatEngineMode == LocalChatEngineMode.FAST_LOCAL) ColorOrange else if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLocalChatEngineModeChange(LocalChatEngineMode.FAST_LOCAL) },
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
                                                Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = ColorOrangeLight,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Column {
                                                Text(
                                                    "Fast Local Agent",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    "Default · Instant search & lore",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        RadioButton(
                                            selected = localChatEngineMode == LocalChatEngineMode.FAST_LOCAL,
                                            onClick = { onLocalChatEngineModeChange(LocalChatEngineMode.FAST_LOCAL) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = ColorOrange,
                                                unselectedColor = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                            ),
                                        )
                                    }

                                    Text(
                                        "Instant multi-engine search across Wikipedia, DuckDuckGo, and Brave Search with neural text formatting. Zero background RAM, no setup needed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    if (localChatEngineMode == LocalChatEngineMode.FAST_LOCAL) {
                                        HorizontalDivider(
                                            color = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp),
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                "SmolLM2-360M-Instruct",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(if (isDark) ColorOrangeDim else MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    "ONNX Q4F16",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }

                                        // Specs row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            SpecChip(label = "Model Size", value = "~272 MB", isDark = isDark)
                                            SpecChip(label = "Required Space", value = "~350 MB", isDark = isDark)
                                            SpecChip(label = "Privacy", value = "100% On-Device", isDark = isDark)
                                        }

                                        // Dynamic Model State UI (SmolLM2)
                                        when (modelDownloadState) {
                                            is ModelDownloadState.Downloading -> {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(14.dp),
                                                                strokeWidth = 2.dp,
                                                                color = ColorOrange,
                                                            )
                                                            Text(
                                                                "Downloading SmolLM2-360M ONNX...",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                        Text(
                                                            "${(modelDownloadState.progress * 100).toInt()}%",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ColorOrange,
                                                        )
                                                    }

                                                    LinearProgressIndicator(
                                                        progress = { modelDownloadState.progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(8.dp)
                                                            .clip(RoundedCornerShape(4.dp)),
                                                        color = ColorOrange,
                                                        trackColor = if (isDark) Color(0x33F97316) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    )

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Text(
                                                            "${modelDownloadState.bytesDownloaded / (1024 * 1024)} MB of ${modelDownloadState.totalBytes / (1024 * 1024)} MB",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                        )
                                                        Text(
                                                            "Keep app active",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                        )
                                                    }
                                                }
                                            }

                                            is ModelDownloadState.Error -> {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Default.ErrorOutline,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                        Text(
                                                            "Download Failed",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error,
                                                        )
                                                    }
                                                    Text(
                                                        modelDownloadState.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                                    )
                                                    Button(
                                                        onClick = onDownloadModel,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = ColorOrange,
                                                            contentColor = Color.White,
                                                        ),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.align(Alignment.End),
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Retry Download")
                                                    }
                                                }
                                            }

                                            is ModelDownloadState.Ready -> {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = ColorOrangeLight,
                                                                modifier = Modifier.size(18.dp),
                                                            )
                                                            Column {
                                                                Text(
                                                                    "Installed & Ready",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                                                )
                                                                val sizeMb = if (modelSizeBytes > 0) "${modelSizeBytes / (1024 * 1024)} MB" else "~272 MB"
                                                                Text(
                                                                    "Storage used: $sizeMb",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                                )
                                                            }
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            TextButton(
                                                                onClick = { showDeleteConfirm = true },
                                                                colors = ButtonDefaults.textButtonColors(
                                                                    contentColor = MaterialTheme.colorScheme.error,
                                                                ),
                                                            ) {
                                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Delete")
                                                            }
                                                            TextButton(onClick = onDownloadModel) {
                                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Re-fetch")
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            else -> {
                                                if (isModelDownloaded) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = ColorOrangeLight,
                                                                modifier = Modifier.size(18.dp),
                                                            )
                                                            Text(
                                                                "Model Installed & Active",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                        TextButton(
                                                            onClick = { showDeleteConfirm = true },
                                                            colors = ButtonDefaults.textButtonColors(
                                                                contentColor = MaterialTheme.colorScheme.error,
                                                            ),
                                                        ) {
                                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Delete")
                                                        }
                                                    }
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Column {
                                                                Text(
                                                                    "Status: Not Installed (Optional)",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                )
                                                                val freeGb = String.format(Locale.US, "%.1f", freeSpaceBytes / (1024.0 * 1024 * 1024))
                                                                Text(
                                                                    "Device storage: $freeGb GB free",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                                )
                                                            }

                                                            Button(
                                                                onClick = onDownloadModel,
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = ColorOrange,
                                                                    contentColor = Color.White,
                                                                ),
                                                                shape = RoundedCornerShape(12.dp),
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Download", fontWeight = FontWeight.Bold)
                                                            }
                                                        }

                                                        Text(
                                                            "💡 When not installed, FoxPlayer uses online web search (Wikipedia, DuckDuckGo, Brave Search) to synthesize book details.",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Engine Option 2: Microsoft Foundry Local
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                border = BorderStroke(
                                    if (localChatEngineMode == LocalChatEngineMode.FOUNDRY_LOCAL) 2.dp else 1.dp,
                                    if (localChatEngineMode == LocalChatEngineMode.FOUNDRY_LOCAL) ColorBlueVioletLight else if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLocalChatEngineModeChange(LocalChatEngineMode.FOUNDRY_LOCAL) },
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                                Icons.Default.Psychology,
                                                contentDescription = null,
                                                tint = ColorBlueVioletLight,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Column {
                                                Text(
                                                    "Microsoft Foundry Local",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    "On-Demand On-Device LLM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        RadioButton(
                                            selected = localChatEngineMode == LocalChatEngineMode.FOUNDRY_LOCAL,
                                            onClick = { onLocalChatEngineModeChange(LocalChatEngineMode.FOUNDRY_LOCAL) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = ColorBlueVioletLight,
                                                unselectedColor = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                            ),
                                        )
                                    }

                                    Text(
                                        "Runs an on-device Large Language Model (Qwen 2.5, Phi-3.5) with agentic tool calling through Microsoft Foundry Local / ONNX GenAI. Private, 100% offline, zero cloud API fees.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    if (localChatEngineMode == LocalChatEngineMode.FOUNDRY_LOCAL) {
                                        HorizontalDivider(
                                            color = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        )

                                        // Foundry Local Companion App Status & Action
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    if (isFoundryAppInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = if (isFoundryAppInstalled) Color(0xFF34D399) else ColorOrangeLight,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Column {
                                                    Text(
                                                        if (isFoundryAppInstalled) "Foundry Local Service Ready" else "Foundry Local App Not Installed",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isFoundryAppInstalled) (if (isDark) Color(0xFF34D399) else Color(0xFF059669)) else (if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary),
                                                    )
                                                    Text(
                                                        if (isFoundryAppInstalled) "Runs via IPC (Zero APK bloat)" else "Install from Google Play to run local models",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                    )
                                                }
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    if (isFoundryAppInstalled) {
                                                        val intent = FoundryIpcManager.createLaunchAppIntent(context)
                                                        if (intent != null) context.startActivity(intent)
                                                    } else {
                                                        context.startActivity(FoundryIpcManager.createPlayStoreIntent())
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Icon(
                                                    if (isFoundryAppInstalled) Icons.Default.Launch else Icons.Default.Shop,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (isFoundryAppInstalled) "Open App" else "Install App", fontSize = 11.sp)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            SpecChip(label = "Mode", value = "Android IPC", isDark = isDark)
                                            SpecChip(label = "Service", value = "Separate App", isDark = isDark)
                                            SpecChip(label = "Privacy", value = "100% On-Device", isDark = isDark)
                                        }

                                        // Model Selection Dropdown for Foundry Local
                                        var showFoundryMenu by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { showFoundryMenu = true },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Model: $foundryModel")
                                                Spacer(Modifier.weight(1f))
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }

                                            DropdownMenu(
                                                expanded = showFoundryMenu,
                                                onDismissRequest = { showFoundryMenu = false },
                                            ) {
                                                val defaultModels = listOf(
                                                    "qwen2.5-0.5b" to "Qwen 2.5 0.5B (Recommended · Fast, ~350MB)",
                                                    "phi-3.5-mini" to "Microsoft Phi-3.5-mini (Deep Reasoning, ~1.8GB)",
                                                    "qwen2.5-1.5b" to "Qwen 2.5 1.5B (Balanced, ~900MB)",
                                                    "qwen2.5-coder-0.5b" to "Qwen 2.5 Coder 0.5B (Compact)",
                                                )
                                                val availableModels = if (dynamicCatalogModels.isNotEmpty()) {
                                                    (dynamicCatalogModels + defaultModels).distinctBy { (id, _) ->
                                                        id.lowercase().removeSuffix("-instruct")
                                                    }
                                                } else {
                                                    defaultModels
                                                }

                                                availableModels.forEach { (id, label) ->
                                                    val isSelected = FoundryIpcManager.isSameModel(id, foundryModel)
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                label,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            )
                                                        },
                                                        onClick = {
                                                            onFoundryModelChange(id)
                                                            showFoundryMenu = false
                                                        },
                                                        leadingIcon = {
                                                            if (isSelected) {
                                                                Icon(Icons.Default.Check, contentDescription = null, tint = ColorBlueVioletLight)
                                                            }
                                                        },
                                                    )
                                                }
                                             }
                                        }

                                        // Foundry Model Download & Cache Management UI
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isDark) Color(0x331E1B4B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .border(
                                                    1.dp,
                                                    if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                    RoundedCornerShape(12.dp),
                                                )
                                                .padding(12.dp),
                                        ) {
                                            when {
                                                // State 1: Currently Downloading
                                                foundryDownloadState is FoundryIpcManager.FoundryDownloadState.Downloading &&
                                                    FoundryIpcManager.isSameModel((foundryDownloadState as FoundryIpcManager.FoundryDownloadState.Downloading).modelAlias, foundryModel) -> {
                                                    val dl = foundryDownloadState as FoundryIpcManager.FoundryDownloadState.Downloading
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(14.dp),
                                                                    strokeWidth = 2.dp,
                                                                    color = ColorBlueVioletLight,
                                                                )
                                                                Text(
                                                                    "Downloading $foundryModel...",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                                                                )
                                                            }
                                                            Text(
                                                                "${(dl.progress * 100).toInt()}%",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = ColorBlueVioletLight,
                                                            )
                                                        }

                                                        LinearProgressIndicator(
                                                            progress = { dl.progress },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(8.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                                                            color = ColorBlueVioletLight,
                                                            trackColor = if (isDark) Color(0x338B5CF6) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                "Downloading via Foundry Local background service",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                            )
                                                            TextButton(
                                                                onClick = { FoundryIpcManager.cancelDownload() },
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                            ) {
                                                                Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                            }
                                                        }
                                                    }
                                                }

                                                // State 2: Download Failed / Error
                                                foundryDownloadState is FoundryIpcManager.FoundryDownloadState.Error &&
                                                    FoundryIpcManager.isSameModel((foundryDownloadState as FoundryIpcManager.FoundryDownloadState.Error).modelAlias, foundryModel) -> {
                                                    val err = foundryDownloadState as FoundryIpcManager.FoundryDownloadState.Error
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        ) {
                                                            Icon(
                                                                Icons.Default.ErrorOutline,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp),
                                                            )
                                                            Text(
                                                                "Download Failed",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.error,
                                                            )
                                                        }
                                                        Text(
                                                            err.message,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                                        )
                                                        Button(
                                                            onClick = {
                                                                FoundryIpcManager.startDownload(context, foundryModel, forceRedownload = false)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = ColorBlueViolet,
                                                                contentColor = Color.White,
                                                            ),
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier.align(Alignment.End),
                                                        ) {
                                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Retry Download")
                                                        }
                                                    }
                                                }

                                                // State 3: Model is Cached / Downloaded & Ready
                                                isFoundryModelCached -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.weight(1f),
                                                        ) {
                                                            Icon(
                                                                Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = Color(0xFF34D399),
                                                                modifier = Modifier.size(18.dp),
                                                            )
                                                            Column {
                                                                Text(
                                                                    "Model Cached & Ready",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                                                )
                                                                val sizeStr = if (foundryModelEstimatedSizeBytes > 0) {
                                                                    "${foundryModelEstimatedSizeBytes / (1024 * 1024)} MB"
                                                                } else {
                                                                    "Downloaded"
                                                                }
                                                                Text(
                                                                    "Storage: $sizeStr in Foundry Local service",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                                )
                                                            }
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            TextButton(
                                                                onClick = { showFoundryDeleteConfirm = true },
                                                                colors = ButtonDefaults.textButtonColors(
                                                                    contentColor = MaterialTheme.colorScheme.error,
                                                                ),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            ) {
                                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Delete", fontSize = 11.sp)
                                                            }
                                                            TextButton(
                                                                onClick = {
                                                                    FoundryIpcManager.startDownload(context, foundryModel, forceRedownload = true)
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            ) {
                                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = ColorBlueVioletLight)
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Re-fetch", fontSize = 11.sp, color = ColorBlueVioletLight)
                                                            }
                                                        }
                                                    }
                                                }

                                                // State 4: Not Cached / Not Downloaded yet
                                                else -> {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    "Status: Model Not Downloaded",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.error,
                                                                )
                                                                val estSize = if (foundryModelEstimatedSizeBytes > 0) {
                                                                    "~${foundryModelEstimatedSizeBytes / (1024 * 1024)} MB"
                                                                } else {
                                                                    when {
                                                                        foundryModel.contains("phi", ignoreCase = true) -> "~1.8 GB"
                                                                        foundryModel.contains("1.5b", ignoreCase = true) -> "~900 MB"
                                                                        else -> "~350 MB"
                                                                    }
                                                                }
                                                                Text(
                                                                    "Size: $estSize · Required for on-device inference",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                                )
                                                            }

                                                            Button(
                                                                onClick = {
                                                                    if (isFoundryAppInstalled) {
                                                                        FoundryIpcManager.startDownload(context, foundryModel, forceRedownload = false)
                                                                    } else {
                                                                        context.startActivity(FoundryIpcManager.createPlayStoreIntent())
                                                                    }
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = ColorBlueViolet,
                                                                    contentColor = Color.White,
                                                                ),
                                                                shape = RoundedCornerShape(10.dp),
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Download Model", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            }
                                                        }

                                                        Text(
                                                            "💡 The Foundry Local app acts as an execution service. FoxPlayer triggers the download directly via IPC into the service cache.",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Endpoint URL Editor
                                        var showEndpointField by remember { mutableStateOf(false) }
                                        if (showEndpointField) {
                                            OutlinedTextField(
                                                value = foundryEndpoint,
                                                onValueChange = onFoundryEndpointChange,
                                                label = { Text("Foundry Local Endpoint URL") },
                                                placeholder = { Text("http://127.0.0.1:8080/v1") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    "Endpoint: $foundryEndpoint",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                                )
                                                TextButton(
                                                    onClick = { showEndpointField = true },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Edit", fontSize = 11.sp, color = ColorBlueVioletLight)
                                                }
                                            }
                                        }

                                        // Test Connection & Status Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (onTestFoundryConnection != null) {
                                                        isTestingConnection = true
                                                        connectionStatusText = null
                                                        coroutineScope.launch {
                                                            val status = onTestFoundryConnection()
                                                            isTestingConnection = false
                                                            isConnectionSuccess = status.isSuccess
                                                            if (status.isSuccess) {
                                                                isFoundryModelCached = FoundryIpcManager.isModelCached(context, foundryModel)
                                                                foundryModelEstimatedSizeBytes = FoundryIpcManager.getModelSizeBytes(context, foundryModel)
                                                            }
                                                            connectionStatusText = if (status.isSuccess) {
                                                                status.message
                                                            } else {
                                                                "Offline: ${status.message.take(28)}"
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = !isTestingConnection && onTestFoundryConnection != null,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ColorBlueViolet,
                                                    contentColor = Color.White,
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                            ) {
                                                if (isTestingConnection) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color.White,
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Testing...")
                                                } else {
                                                    Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Test Connection")
                                                }
                                            }

                                            if (connectionStatusText != null) {
                                                Text(
                                                    connectionStatusText!!,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isConnectionSuccess) Color(0xFF34D399) else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(start = 8.dp),
                                                )
                                            }
                                        }

                                        Text(
                                            "💡 Seamless Fallback: If Foundry Local is not detected when you ask a question, FoxPlayer automatically falls back to Fast Local Search so you always receive an answer.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Appearance & Theme
                    item {
                        SectionHeader(
                            title = "Appearance",
                            icon = Icons.Default.Palette,
                            iconTint = ColorBlueVioletLight,
                            isDark = isDark,
                        )
                        Spacer(Modifier.height(8.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ThemeOptionRow(
                                    title = "Dark Theme",
                                    subtitle = "Deep cosmic glassmorphic palette",
                                    icon = Icons.Default.DarkMode,
                                    selected = themeMode == ThemeMode.DARK,
                                    isDark = isDark,
                                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                                )
                                ThemeOptionRow(
                                    title = "Light Theme",
                                    subtitle = "Clean daylight high-contrast palette",
                                    icon = Icons.Default.LightMode,
                                    selected = themeMode == ThemeMode.LIGHT,
                                    isDark = isDark,
                                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                                )
                                ThemeOptionRow(
                                    title = "System Default",
                                    subtitle = "Follow Android device system theme",
                                    icon = Icons.Default.BrightnessAuto,
                                    selected = themeMode == ThemeMode.SYSTEM,
                                    isDark = isDark,
                                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                                )
                            }
                        }
                    }

                    // Section: Google Gemini AI Companion
                    item {
                        SectionHeader(
                            title = "Google Gemini Cloud AI",
                            icon = Icons.Default.AutoAwesome,
                            iconTint = ColorOrangeLight,
                            isDark = isDark,
                        )
                        Spacer(Modifier.height(8.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Enable Gemini Chat Activity",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "Show ✦ Gemini action icon in the book detail bar to chat with Gemini using Gmail login or API key.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Switch(
                                        checked = isGeminiChatEnabled,
                                        onCheckedChange = onGeminiChatEnabledChange,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = ColorOrange,
                                        ),
                                    )
                                }

                                if (isGeminiChatEnabled) {
                                    HorizontalDivider(
                                        color = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        SpecChip(label = "Engine", value = selectedGeminiModel, isDark = isDark)
                                        SpecChip(label = "Streaming", value = "Real-Time Tokens", isDark = isDark)
                                        SpecChip(label = "Auth", value = "Gmail / API-Key", isDark = isDark)
                                    }

                                    // Model Selection Dropdown
                                    var showModelMenu by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { showModelMenu = true },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Model: $selectedGeminiModel")
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }

                                        DropdownMenu(
                                            expanded = showModelMenu,
                                            onDismissRequest = { showModelMenu = false },
                                        ) {
                                            listOf(
                                                "gemini-1.5-flash" to "Gemini 1.5 Flash (Recommended)",
                                                "gemini-2.0-flash" to "Gemini 2.0 Flash (Next-Gen)",
                                                "gemini-1.5-pro" to "Gemini 1.5 Pro (Deep Reasoning)",
                                                "gemini-2.0-flash-lite" to "Gemini 2.0 Flash Lite (Fast)",
                                                "gemini-1.5-flash-8b" to "Gemini 1.5 Flash 8B (Compact)",
                                            ).forEach { (id, label) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            label,
                                                            fontWeight = if (id == selectedGeminiModel) FontWeight.Bold else FontWeight.Normal,
                                                        )
                                                    },
                                                    onClick = {
                                                        onSelectedGeminiModelChange(id)
                                                        showModelMenu = false
                                                    },
                                                    leadingIcon = {
                                                        if (id == selectedGeminiModel) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = ColorOrange)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    var showKeyField by remember { mutableStateOf(false) }
                                    if (showKeyField) {
                                        OutlinedTextField(
                                            value = geminiApiKey,
                                            onValueChange = onGeminiApiKeyChange,
                                            label = { Text("Gemini API Key (optional override)") },
                                            placeholder = { Text("AIzaSy...") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                    } else {
                                        TextButton(
                                            onClick = { showKeyField = true },
                                            contentPadding = PaddingValues(0.dp),
                                        ) {
                                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (geminiApiKey.isNotBlank()) "Gemini API Key configured (tap to edit)" else "Configure custom API key",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ColorOrangeLight,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Web Knowledge Sources
                    item {
                        SectionHeader(
                            title = "Web Knowledge Sources",
                            icon = Icons.Default.Language,
                            iconTint = ColorBlueVioletLight,
                            isDark = isDark,
                        )
                        Spacer(Modifier.height(8.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                KnowledgeSourceRow(
                                    name = "Wikipedia API",
                                    desc = "Extracts book synopses, author history & encyclopedic context",
                                    isDark = isDark,
                                )
                                KnowledgeSourceRow(
                                    name = "DuckDuckGo API",
                                    desc = "Instant literary definitions, character lore & related topics",
                                    isDark = isDark,
                                )
                                KnowledgeSourceRow(
                                    name = "Brave Search API",
                                    desc = "Live web indexing across publisher reviews & series chronology",
                                    isDark = isDark,
                                )
                            }
                        }
                    }

                    // Section: Audiobook Management
                    if (onRescanChapters != null) {
                        item {
                            SectionHeader(
                                title = "Audiobook Management",
                                icon = Icons.Default.FolderOpen,
                                iconTint = ColorOrangeLight,
                                isDark = isDark,
                            )
                            Spacer(Modifier.height(8.dp))

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) GlassBg else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onRescanChapters()
                                            onDismiss()
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Column {
                                            Text(
                                                "Re-scan Audiobook Chapters",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                "Re-index audio files, duration, and chapter tags from device storage",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 4: About
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "FoxPlayer v${mn.blazeapps.foxplayer.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Private Audiobook Player · On-Device ONNX AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(20.dp),
            ),
            title = {
                Text("Delete SmolLM2-360M Model?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("This will remove the ~272 MB model file from your device. FoxPlayer will fall back to online web search. You can re-download the model at any time in Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteModel()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Delete Model", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    // Foundry Model Delete Confirmation Dialog
    if (showFoundryDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showFoundryDeleteConfirm = false },
            containerColor = if (isDark) Color(0xF20D1230) else MaterialTheme.colorScheme.surface,
            titleContentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textContentColor = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(
                BorderStroke(1.dp, if (isDark) GlassBorder else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(20.dp),
            ),
            title = {
                Text("Delete Model from Cache?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("This will remove '$foundryModel' from Microsoft Foundry Local service cache. You can re-download it at any time in Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFoundryDeleteConfirm = false
                        coroutineScope.launch {
                            val success = FoundryIpcManager.deleteModel(context, foundryModel)
                            if (success) {
                                isFoundryModelCached = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Delete Model", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFoundryDeleteConfirm = false }) {
                    Text("Cancel", color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isDark: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SpecChip(label: String, value: String, isDark: Boolean) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color(0x33000000) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    if (isDark) Color(0x33F97316) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) ColorOrange else (if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextMuted else MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = ColorOrange,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun KnowledgeSourceRow(
    name: String,
    desc: String,
    isDark: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(ColorOrange),
        )
        Column {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
