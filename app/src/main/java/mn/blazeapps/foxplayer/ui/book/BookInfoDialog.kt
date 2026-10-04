package mn.blazeapps.foxplayer.ui.book

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mn.blazeapps.foxplayer.data.ai.BookSeriesAndAuthorInfo
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.ui.theme.*

@Composable
fun BookInfoDialog(
    book: BookEntity?,
    seriesInfo: BookSeriesAndAuthorInfo?,
    isFetchingInfo: Boolean,
    onRefreshInfo: () -> Unit,
    onAddBookmarkNote: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background == BgDeep
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(book?.id) {
        if (seriesInfo == null && !isFetchingInfo) {
            onRefreshInfo()
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(ColorOrange, ColorOrangeLight))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                "Audiobook Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                book?.title ?: "Book Details & Series",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Section 1: Book Description / Synopsis
                    item {
                        SectionTitle(
                            title = "Book Synopsis",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            isDark = isDark,
                        )
                        Spacer(Modifier.height(6.dp))

                        val desc = book?.description
                        if (!desc.isNullOrBlank()) {
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
                                Column(modifier = Modifier.padding(14.dp)) {
                                    SelectionContainer {
                                        MarkdownContentView(
                                            markdown = desc,
                                            isDark = isDark,
                                            isUser = false,
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val clean = stripMarkdownForPlainText(desc)
                                                onAddBookmarkNote(clean)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.BookmarkAdd,
                                                contentDescription = null,
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
                                                val clean = stripMarkdownForPlainText(desc)
                                                clipboardManager.setText(AnnotatedString(clean))
                                                Toast.makeText(context, "Synopsis copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy synopsis",
                                                tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0x1AFFFFFF) else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "No synopsis stored for this audiobook. Use 'Enrich with AI' to automatically download online description and genres.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }

                    // Section 2: Series Context & Next Book
                    item {
                        SectionTitle(
                            title = "Series & Reading Order",
                            icon = Icons.Default.AutoStories,
                            isDark = isDark,
                        )
                        Spacer(Modifier.height(6.dp))

                        if (isFetchingInfo && seriesInfo == null) {
                            LoadingCard(
                                message = "Analyzing book series and reading order...",
                                isDark = isDark,
                            )
                        } else if (seriesInfo != null) {
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
                                    if (!seriesInfo.seriesName.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Series",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    seriesInfo.seriesName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }

                                            if (!seriesInfo.seriesOrder.isNullOrBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(if (isDark) ColorOrangeDim else MaterialTheme.colorScheme.primaryContainer)
                                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                                ) {
                                                    Text(
                                                        seriesInfo.seriesOrder,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        }

                                        // Next Book in Series - Highlighted Card!
                                        if (!seriesInfo.nextBook.isNullOrBlank()) {
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isDark) Color(0x33F97316) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                ),
                                                border = BorderStroke(1.dp, ColorOrange),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.ArrowForward,
                                                            contentDescription = null,
                                                            tint = ColorOrange,
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                        Column {
                                                            Text(
                                                                "Next in Series",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = ColorOrange,
                                                            )
                                                            Text(
                                                                seriesInfo.nextBook,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            onAddBookmarkNote("Next book in series: ${seriesInfo.nextBook}")
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(
                                                            Icons.Default.BookmarkAdd,
                                                            contentDescription = "Save next book to notes",
                                                            tint = ColorOrange,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                "• No sequel or next book recorded (may be final book in series or standalone).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        if (!seriesInfo.previousBook.isNullOrBlank()) {
                                            Text(
                                                "Preceded by: ${seriesInfo.previousBook}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        if (!seriesInfo.seriesOverview.isNullOrBlank()) {
                                            Text(
                                                seriesInfo.seriesOverview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    } else if (seriesInfo.isStandalone) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Bookmark,
                                                contentDescription = null,
                                                tint = ColorOrangeLight,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                "Standalone audiobook — not part of an ongoing series.",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    } else {
                                        Text(
                                            "No specific series sequence was identified online for this title.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0x1AFFFFFF) else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Tap 'Refresh' below to search series chronology online.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }

                    // Section 3: Author's Best Sellers
                    val authorName = book?.author
                    if (!authorName.isNullOrBlank()) {
                        item {
                            SectionTitle(
                                title = "More by $authorName",
                                icon = Icons.Default.Star,
                                isDark = isDark,
                            )
                            Spacer(Modifier.height(6.dp))

                            if (isFetchingInfo && seriesInfo == null) {
                                LoadingCard(
                                    message = "Fetching author's best sellers & popular bibliography...",
                                    isDark = isDark,
                                )
                            } else if (seriesInfo?.authorBestSellers?.isNotEmpty() == true) {
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
                                        Text(
                                            "Best-Selling & Highly Rated Works:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
                                        )

                                        seriesInfo.authorBestSellers.forEach { title ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
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
                                                        tint = if (isDark) ColorBlueVioletLight else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(
                                                        title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isDark) TextPrimary else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        onAddBookmarkNote("Recommended by $authorName: $title")
                                                    },
                                                    modifier = Modifier.size(24.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.BookmarkAdd,
                                                        contentDescription = "Save to notes",
                                                        tint = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isDark) Color(0x1AFFFFFF) else MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "No additional bibliography was identified for this author.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Section 4: Sources
                    if (seriesInfo != null && seriesInfo.sources.isNotEmpty()) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                seriesInfo.sources.forEach { src ->
                                    SourceBadge(src, isDark)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onRefreshInfo,
                        enabled = !isFetchingInfo,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isFetchingInfo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = ColorOrange,
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Refresh Details")
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorBlueViolet,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDark) ColorOrangeLight else MaterialTheme.colorScheme.primary,
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
private fun LoadingCard(
    message: String,
    isDark: Boolean,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0x1AFFFFFF) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = ColorOrange,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
