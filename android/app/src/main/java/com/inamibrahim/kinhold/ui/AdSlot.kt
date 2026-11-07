package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.AdPlacement
import com.inamibrahim.kinhold.model.AdPolicy

/**
 * A place for a banner advertisement. There is no ad service yet, so in a debug build this draws a labelled placeholder the
 * size of a standard banner, to tune layouts; in a release build it draws nothing. It is also nothing for a Pro phone, and
 * nothing in any place [AdPlacement.allowed] forbids.
 */
@Composable
fun AdSlot(placement: AdPlacement, modifier: Modifier = Modifier) {
    if (!AdPolicy.shows(placement, AppSettings.proUnlocked, adsOn = AppSettings.debuggable)) return
    AppCard(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text("Ad space (placeholder, debug build only)", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        }
    }
}
