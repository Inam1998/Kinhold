package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.SurfaceHigh)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
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
