package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.inamibrahim.kinhold.model.Family
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
fun SettingsScreen(family: Family, scroll: androidx.compose.foundation.ScrollState, onChooseMe: (Int?) -> Unit, onLock: (LockSetupMode) -> Unit, onBack: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
        ) {
            ScreenHeader("Kinhold", "Settings", Modifier.padding(top = 12.dp, bottom = 20.dp))

            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Person, Tones.Mint, 36.dp); Text("Who are you?", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    val me = family.people.firstOrNull { it.isMe }
                    Text(
                        if (me != null) "${me.name} is marked YOU on the tree, and the Me button takes you there." else "Choose yourself so the tree can mark you and the Me button can bring you back to your own card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (me != null) "Change" else "Choose yourself", false, { picking = true }, icon = Icons.Filled.Person)
                        if (me != null) Pill("Clear", false, { onChooseMe(null) })
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Lock, Tones.Teal, 36.dp); Text("App lock", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text(
                        if (AppLock.enabled) "Kinhold asks for your PIN when it opens, and when you come back to it after a while." else "Optional. Ask for a PIN before Kinhold opens, so someone who picks up your phone cannot read the tree.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!AppLock.enabled) {
                            DialogButton("Turn on", { onLock(LockSetupMode.On) }, kind = ButtonKind.Primary)
                        } else {
                            DialogButton("Change PIN", { onLock(LockSetupMode.Change) }, kind = ButtonKind.Tonal)
                            DialogButton("Turn off", { onLock(LockSetupMode.Off) }, kind = ButtonKind.Danger)
                        }
                    }
                    if (AppLock.enabled) {
                        Text("Lock again", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (d in com.inamibrahim.kinhold.model.LockDelay.entries) Pill(d.label, AppLock.delay == d, { AppLock.chooseDelay(d) })
                        }
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Settings, Tones.Sky, 36.dp); Text("Theme", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Favorite, Tones.Rose, 36.dp); Text("Accent colour", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
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
            AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ToneIcon(Icons.Filled.Settings, Tones.Amber, 36.dp); Text("Dates", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp)) }
                    Text("Show the Hijri date beside a full date, for example on the Details page.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Pill(if (AppSettings.showHijri) "Hijri dates: on" else "Hijri dates: off", AppSettings.showHijri, { AppSettings.chooseShowHijri(!AppSettings.showHijri) })
                }
            }
            Pill("Back", false, onBack, Modifier.fillMaxWidth().padding(top = 20.dp))
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
        }
    }
    if (picking) PersonPickerDialog(family, "Which card is you?", { onChooseMe(it); picking = false }, { picking = false })
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

/** A search box and the matching people, to pick which card is you. */
@Composable
fun PersonPickerDialog(family: Family, title: String, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(family, query) { if (query.isBlank()) family.people.sortedBy { it.name }.take(30) else family.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = Palette.Text) },
        text = {
            Column {
                Field("Search by name", query, { query = it }, Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(results, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(p.id) }.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(p, 40.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                                if (p.lifeDates().isNotEmpty()) Text(p.lifeDates(), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
        },
    )
}
