package com.brahma.mobile.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Animated "aurora" backdrop: soft glowing blobs drifting behind the glass panels. */
@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BColors.Bg1, BColors.Bg2, BColors.Bg3)))
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(cx, cy), radius = r
                    ),
                    radius = r, center = Offset(cx, cy)
                )
            }
            blob(BColors.Accent, size.width * (0.2f + 0.3f * a), size.height * (0.15f + 0.10f * a), size.width * 0.75f)
            blob(BColors.Accent2, size.width * (0.85f - 0.3f * a), size.height * (0.45f + 0.10f * a), size.width * 0.6f)
            blob(BColors.Pink, size.width * (0.3f + 0.2f * a), size.height * (0.85f - 0.10f * a), size.width * 0.7f)
        }
        content()
    }
}

/** Glassmorphism: translucent gradient fill + bright glass-edge border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.White,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.06f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.28f))),
                shape
            )
            .padding(padding.dp),
        content = content
    )
}

/** Claymorphism: puffy, soft, 3D button with highlight + shade rim and a deep colored shadow. */
@Composable
fun ClayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BColors.Accent,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    Box(
        modifier
            .scale(scale)
            .shadow(if (pressed) 3.dp else 14.dp, shape, ambientColor = color, spotColor = color)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.30f), color, lerp(color, Color.Black, 0.35f))))
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                        start = Offset.Zero, end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

enum class OrbState { Idle, Listening, Thinking, Speaking }

/** The big clay orb: pulses with the microphone level and changes color by state. */
@Composable
fun ClayOrb(state: OrbState, level: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse")
    val lvl by animateFloatAsState(level, label = "lvl")
    val base = when (state) {
        OrbState.Idle -> BColors.Accent
        OrbState.Listening -> BColors.Pink
        OrbState.Thinking -> BColors.Accent2
        OrbState.Speaking -> BColors.Green
    }
    Box(
        modifier
            .size(250.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            for (i in 0..2) {
                val ph = (pulse + i / 3f) % 1f
                drawCircle(
                    color = base.copy(alpha = (1f - ph) * 0.28f),
                    radius = r * (0.60f + ph * 0.40f) + lvl * 16.dp.toPx(),
                    center = center,
                    style = Stroke(2.dp.toPx())
                )
            }
            val body = r * 0.56f * (1f + lvl * 0.10f)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = body, center = center + Offset(8.dp.toPx(), 16.dp.toPx()))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(lerp(base, Color.White, 0.55f), base, lerp(base, Color.Black, 0.50f)),
                    center = center + Offset(-r * 0.18f, -r * 0.22f), radius = body * 1.5f
                ),
                radius = body, center = center
            )
            drawCircle(Color.White.copy(alpha = 0.40f), radius = r * 0.10f, center = center + Offset(-r * 0.24f, -r * 0.28f))
        }
        Icon(
            imageVector = when (state) {
                OrbState.Idle -> Icons.Rounded.Mic
                OrbState.Listening -> Icons.Rounded.GraphicEq
                OrbState.Thinking -> Icons.Rounded.AutoAwesome
                OrbState.Speaking -> Icons.Rounded.VolumeUp
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(54.dp)
        )
    }
}

@Composable
fun glassFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BColors.TextHi,
    unfocusedTextColor = BColors.TextHi,
    focusedBorderColor = BColors.Accent2,
    unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
    focusedLabelColor = BColors.Accent2,
    unfocusedLabelColor = BColors.TextLo,
    cursorColor = BColors.Accent2,
    focusedContainerColor = Color.White.copy(alpha = 0.05f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
)
