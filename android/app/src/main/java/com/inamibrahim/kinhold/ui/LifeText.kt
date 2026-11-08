package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A line of life dates. When only one date is known the text starts with "b. " (born) or "d. " (died); here that prefix is shown as a small
 * cake or candle picture instead, with the spoken words "Born" or "Died" for a screen reader. Any other text is shown as it is.
 */
@Composable
fun LifeText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val born = text.startsWith("b. ")
    val died = text.startsWith("d. ")
    if (!born && !died) {
        Text(text, style = style, color = color, modifier = modifier)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (born) AppIcons.Cake else AppIcons.Candle, contentDescription = if (born) "Born" else "Died", tint = color, modifier = Modifier.size(16.dp))
        Text(text.substring(3), style = style, color = color)
    }
}
