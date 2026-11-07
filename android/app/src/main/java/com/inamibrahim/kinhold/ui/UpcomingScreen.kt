package com.inamibrahim.kinhold.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.Reminders
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.MarriageDate
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Upcoming
import com.inamibrahim.kinhold.model.UpcomingDate
import com.inamibrahim.kinhold.model.UpcomingKind
import java.time.LocalDate

private sealed interface UpItem {
    data class Header(val text: String) : UpItem
    data class Row(val date: UpcomingDate) : UpItem
}

private fun title(d: UpcomingDate): String = when (d.kind) {
    UpcomingKind.Birthday -> if (d.years != null) "${d.person.name} turns ${d.years}" else "${d.person.name}'s birthday"
    UpcomingKind.Remembrance -> if (d.years != null && d.years > 0) "${d.person.name}: ${d.years} years since they died" else "Remembering ${d.person.name}"
    UpcomingKind.Anniversary -> "${d.person.name} and ${d.other?.name ?: "spouse"}" + if (d.years != null && d.years > 0) ": ${d.years} years married" else ": anniversary"
}

/** The birthdays, remembrance days and anniversaries in the next year, soonest first, with the optional morning reminder. */
@Composable
fun UpcomingScreen(family: Family, marriages: List<MarriageDate>?, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var remind by remember { mutableStateOf(Reminders.enabled(context)) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Reminders.set(context, true); remind = true; denied = false } else denied = true
    }
    val today = remember { LocalDate.now() }
    val dates = remember(family, marriages) { Upcoming.compute(family, marriages.orEmpty(), today) }
    val items = remember(dates) {
        val out = ArrayList<UpItem>()
        var last = ""
        for (d in dates) {
            val group = when { d.daysAway == 0 -> "Today"; d.daysAway <= 7 -> "This week"; d.daysAway <= 31 -> "This month"; else -> "Later this year" }
            if (group != last) { out += UpItem.Header(group); last = group }
            out += UpItem.Row(d)
        }
        out
    }

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader("Family dates", "Upcoming", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), onBack = onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ToneIcon(Icons.Filled.Notifications, Tones.Amber, 36.dp)
                                Text("Morning reminder", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
                            }
                            Text(
                                "Optional. At about 9 in the morning Kinhold can tell you about dates today and tomorrow. It works on this phone only and sends nothing anywhere.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextMuted,
                                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                            )
                            if (remind) {
                                DialogButton("Turn the reminder off", { Reminders.set(context, false); remind = false }, kind = ButtonKind.Neutral)
                            } else {
                                DialogButton(
                                    "Turn the reminder on",
                                    { if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else { Reminders.set(context, true); remind = true } },
                                    kind = ButtonKind.Primary,
                                )
                            }
                            if (denied) Text("Notifications are blocked for Kinhold. Allow them in your phone's settings to get the reminder.", style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                if (items.isEmpty()) {
                    item {
                        Text(
                            "No dates in the next year yet. Add a day and month to someone's birth, death or marriage and it will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                        )
                    }
                }
                items(items) { item ->
                    when (item) {
                        is UpItem.Header -> Text(item.text, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                        is UpItem.Row -> {
                            val d = item.date
                            val (icon, tone) = when (d.kind) {
                                UpcomingKind.Birthday -> Icons.Filled.Star to Tones.Amber
                                UpcomingKind.Remembrance -> Icons.Filled.Info to Tones.Violet
                                UpcomingKind.Anniversary -> Icons.Filled.Favorite to Tones.Rose
                            }
                            AppCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable { onOpen(d.person.id) }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ToneIcon(icon, tone, 44.dp)
                                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                        Text(title(d), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                                        Text(
                                            "${PartialDate(d.on.year, d.on.monthValue, d.on.dayOfMonth).text().substringBeforeLast(' ')}  ·  ${Upcoming.describe(d.daysAway)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Palette.TextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Column(Modifier.padding(bottom = 8.dp)) {} }
            }
        }
    }
}
