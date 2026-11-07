package com.inamibrahim.kinhold.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The screen colour with a soft accent glow fading in from the top, as on every screen of the other apps. */
@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background)
            .drawBehind {
                val strength = if (Palette.dark) 1f else 0.6f
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.Accent.copy(alpha = 0.20f * strength), Color.Transparent),
                        center = Offset(size.width * 0.12f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Palette.AccentDeep.copy(alpha = 0.16f * strength), Color.Transparent),
                        center = Offset(size.width * 0.95f, 0f),
                        radius = size.width * 0.7f,
                    ),
                )
            },
    ) { content() }
}

/** A small accent overline above a large bold title, left-aligned, with no bar or divider. */
@Composable
fun ScreenHeader(overline: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            overline.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = Palette.Accent,
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = Palette.Text,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A fully round chip: raised fill when off, the solid accent with dark text when selected. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberPressScale()
    Box(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.SurfaceHigh)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Palette.OnAccent else Palette.Text,
        )
    }
}

/** The one main action on a screen: a tall, fully round button in the solid accent. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val press = rememberPressScale()
    Box(
        modifier
            .graphicsLayer { scaleX = press.scale.value; scaleY = press.scale.value }
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Palette.Accent else Palette.SurfaceHigh)
            .clickable(enabled = enabled, interactionSource = press.source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Palette.OnAccent else Palette.TextMuted,
        )
    }
}

/** A button that shrinks a little while it is pressed, as in the other apps. */
@Stable
class PressScale(val source: MutableInteractionSource, val scale: State<Float>)

@Composable
fun rememberPressScale(): PressScale {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "press")
    return remember(source, scale) { PressScale(source, scale) }
}

/**
 * The hero of a screen: a bold gradient card that fades from the accent into a deeper shade, with a faint light in
 * the top-left corner and a soft glow under it, as in the shared design.
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val start = Palette.AccentDeep
    val end = lerp(Palette.AccentDeep, Color(0xFF0B1220), 0.45f)
    Box(
        modifier
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = start, spotColor = start)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset(900f, 900f)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.1f, 0f),
                        radius = size.width * 0.8f,
                    ),
                )
                drawHeroGlyph(Color.White.copy(alpha = 0.10f))
            },
    ) { content() }
}

/** The app's mark (one parent above three children) drawn large and faint in the corner of a hero card. */
private fun DrawScope.drawHeroGlyph(color: Color) {
    // Up in the top-right corner, clear of the button, and small enough to stay inside the card.
    val unit = size.height * 0.34f
    val cx = size.width - unit * 0.9f
    val cy = unit * 0.95f
    val r = unit * 0.16f
    val stroke = Stroke(width = unit * 0.045f, cap = StrokeCap.Round)
    val top = Offset(cx, cy - unit * 0.55f)
    val kids = listOf(-0.5f, 0f, 0.5f).map { Offset(cx + unit * it, cy + unit * 0.25f) }
    val busY = cy - unit * 0.12f
    drawLine(color, Offset(top.x, top.y + r), Offset(top.x, busY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(kids.first().x, busY), Offset(kids.last().x, busY), strokeWidth = stroke.width, cap = StrokeCap.Round)
    for (k in kids) drawLine(color, Offset(k.x, busY), Offset(k.x, k.y - r), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawCircle(color, r * 1.15f, top)
    for (k in kids) drawCircle(color, r, k)
}

/** A lifted rounded surface. Everything sits on cards, with no divider lines between rows. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Palette.Surface,
        shadowElevation = 8.dp,
    ) { content() }
}
