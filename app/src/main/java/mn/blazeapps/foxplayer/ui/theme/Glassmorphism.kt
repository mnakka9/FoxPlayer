package mn.blazeapps.foxplayer.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ambient background container reproducing the BlazeIn multi-point radial mesh glow:
 * - Base: deep obsidian mesh (#1A0A3A -> #0F1A3D -> #080B1A -> #2A0E1A)
 * - Top-Left: Indigo glow (rgba(99, 102, 241, 0.45))
 * - Top-Right: Electric Orange glow (rgba(249, 115, 22, 0.35))
 * - Bottom-Right: Violet glow (rgba(139, 92, 246, 0.40))
 * - Bottom-Left: Warm Orange glow (rgba(249, 115, 22, 0.28))
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        BgMeshIndigo,
                        BgMeshAzure,
                        BgDeep,
                        BgMeshOrange,
                    ),
                ),
            ),
    ) {
        // Top-Left Glowing Orb (Indigo / Blue-Violet)
        Box(
            modifier = Modifier
                .size(360.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x606366F1),
                            Color(0x286366F1),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Top-Right Glowing Orb (Electric Orange)
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-40).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x50F97316),
                            Color(0x20F97316),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Bottom-Right Glowing Orb (Violet / Purple)
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x558B5CF6),
                            Color(0x228B5CF6),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Bottom-Left Glowing Orb (Warm Orange)
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x40F97316),
                            Color(0x15F97316),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        content()
    }
}

/**
 * Modifier applying BlazeIn glassmorphic styling:
 * - Translucent glass background with rim reflection
 * - Border highlight gradient
 * - Rounded corner clip
 */
fun Modifier.glassEffect(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = GlassBg,
    borderColor: Color = Color.White,
    borderAlphaTop: Float = 0.22f,
    borderAlphaBottom: Float = 0.08f,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(
        color = backgroundColor,
        shape = shape,
    )
    .border(
        border = BorderStroke(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    borderColor.copy(alpha = borderAlphaTop),
                    borderColor.copy(alpha = borderAlphaBottom),
                ),
            ),
        ),
        shape = shape,
    )

/**
 * Reusable Glass Card with BlazeIn 16dp curvature, soft specular rim, and optional click interaction.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = GlassBg,
    borderAlphaTop: Float = 0.24f,
    borderAlphaBottom: Float = 0.08f,
    borderColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White.copy(alpha = 0.2f)),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                spotColor = Color(0x30000000),
                ambientColor = Color(0x15000000),
            )
            .glassEffect(
                shape = shape,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                borderAlphaTop = borderAlphaTop,
                borderAlphaBottom = borderAlphaBottom,
            )
            .then(clickModifier),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Squircle Frosted Glass Icon Button (for action bars, toolbar, player controls)
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = GlassBg,
    borderAlphaTop: Float = 0.25f,
    contentColor: Color = TextPrimary,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .glassEffect(
                shape = shape,
                backgroundColor = containerColor,
                borderAlphaTop = borderAlphaTop,
                borderAlphaBottom = 0.08f,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.25f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * Pill-shaped badge matching BlazeIn's pill style.
 */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    backgroundColor: Color = ColorBlueVioletDim,
    borderColor: Color = Color(0x596366F1),
    contentColor: Color = ColorBlueVioletSubtle,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

/**
 * Reusable Squircle Icon Box with gradient fill and colored drop glow shadow.
 */
@Composable
fun SquircleIconBox(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    brush: Brush = Brush.linearGradient(listOf(ColorBlueViolet, ColorOrange)),
    shadowColor: Color = Color(0x666366F1),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                spotColor = shadowColor,
                ambientColor = shadowColor.copy(alpha = 0.25f),
            )
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Electric Orange Primary Action Button with signature radiant glow drop shadow.
 */
@Composable
fun ElectricOrangeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = Color(0x66F97316),
                ambientColor = Color(0x33F97316),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(ColorOrange, ColorOrangeLight),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * Frosted Glass Top App Bar for FoxPlayer.
 */
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    leadingBrandIcon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(12.dp))
            } else if (leadingBrandIcon != null) {
                leadingBrandIcon()
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                title()
                if (subtitle != null) {
                    subtitle()
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions,
                )
            }
        }
    }
}
