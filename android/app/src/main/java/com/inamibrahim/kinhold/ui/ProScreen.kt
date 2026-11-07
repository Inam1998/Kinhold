package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.ProFeatures

/** What Kinhold Pro is. Buying is not built yet: in a release build the screen says so, and the developer switch below is debug only. */
@Composable
fun ProScreen(scroll: androidx.compose.foundation.ScrollState, onBack: () -> Unit) {
    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(scroll).padding(horizontal = 20.dp)) {
            ScreenHeader("Kinhold", "Kinhold Pro", Modifier.padding(top = 12.dp, bottom = 20.dp), onBack = onBack)

            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIcon(Icons.Filled.Star, Tones.Amber, 36.dp)
                        Text("What Pro gives you", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
                    }
                    for (line in ProFeatures.included) Bullet(line)
                    Text(
                        if (AppSettings.proUnlocked) "Pro is unlocked on this phone." else "Buying Pro is not available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (AppSettings.proUnlocked) Palette.Accent else Palette.TextMuted,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToneIcon(Icons.Filled.Check, Tones.Mint, 36.dp)
                        Text("Always free", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text("Nothing that keeps your tree safe is ever locked.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                    for (line in ProFeatures.alwaysFree) Bullet(line)
                }
            }

            if (AppSettings.debuggable) {
                AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Developer switch", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text("Debug builds only. Pretends Pro is bought, to check that the ad slots disappear.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        EqualRow(2) {
                            Pill("Free", !AppSettings.proUnlocked, { AppSettings.chooseProUnlocked(false) }, Modifier.weight(1f))
                            Pill("Pro", AppSettings.proUnlocked, { AppSettings.chooseProUnlocked(true) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.Accent, modifier = Modifier.padding(end = 10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}
