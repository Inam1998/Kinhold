package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Theme and accent colour. Changes apply at once and are remembered. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            ScreenHeader("Kinhold", "Settings", Modifier.padding(top = 12.dp, bottom = 20.dp))

            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text(
                        "Dark, light, or follow the phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in ThemeMode.entries) {
                            Pill(if (m == ThemeMode.System) "Phone" else m.name, AppSettings.mode == m, { AppSettings.chooseMode(m) })
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Accent colour", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text(
                        "Used for buttons, highlights and the focus outline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (a in AccentChoice.entries) Swatch(a, a == Palette.accent) { AppSettings.chooseAccent(a) }
                    }
                }
            }

            Text(
                "These choices belong to this phone. They are not part of your tree or its backups.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Pill("Back", false, onBack, Modifier.fillMaxWidth().padding(top = 20.dp))
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Swatch(choice: AccentChoice, selected: Boolean, onClick: () -> Unit) {
    val color = if (Palette.dark) choice.dark[0] else choice.light[0]
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, Palette.Text, CircleShape) else Modifier)
            .semantics { contentDescription = choice.label + if (selected) ", selected" else "" }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}
