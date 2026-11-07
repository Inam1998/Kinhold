package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Everything that is not the tree itself, in one place: backup and sharing, settings, and the Help guide. */
@Composable
fun MoreScreen(backupStatus: String, onBackup: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit, onBack: () -> Unit) {
    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp)) {
            ScreenHeader("Kinhold", "More", Modifier.padding(top = 12.dp, bottom = 20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MoreRow(Icons.Filled.Lock, Tones.Mint, "Backup and sharing", "$backupStatus. Save, restore, merge and import", onBackup)
                MoreRow(Icons.Filled.Settings, Tones.Sky, "Settings", "Who you are, theme and accent colour", onSettings)
                MoreRow(Icons.Filled.Info, Tones.Amber, "How Kinhold works", "A page for every feature, with its limits", onHelp)
            }
            DialogButton("Back to the tree", onBack, Modifier.fillMaxWidth().padding(top = 20.dp), kind = ButtonKind.Neutral)
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, tone: Color, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ToneIcon(icon, tone, 44.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextMuted)
        }
    }
}
