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
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    // Section 1: On-Device AI Model
                    item {
                        SectionHeader(
                            title = "On-Device AI Engine",
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
                                    Text(
                                        "SmolLM2-360M-Instruct",
                                        style = MaterialTheme.typography.titleSmall,
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

                                Text(
                                    "State-of-the-art 360M parameter neural model fine-tuned for summarization and reasoning. Runs 100% locally on your device for offline book metadata enrichment and companion chat without sending data to servers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Specs row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SpecChip(label = "Model Size", value = "~272 MB", isDark = isDark)
                                    SpecChip(label = "Required Space", value = "~350 MB", isDark = isDark)
                                    SpecChip(label = "Privacy", value = "100% On-Device", isDark = isDark)
                                }

                                HorizontalDivider(
                                    color = if (isDark) GlassBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )

                                // Dynamic Model State UI
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
                                        // Idle / Not Downloaded
                                        if (isModelDownloaded) {
                                            // Model file exists on disk
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
                                                            "Status: Not Installed",
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
                                    "FoxPlayer v1.1.0",
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
