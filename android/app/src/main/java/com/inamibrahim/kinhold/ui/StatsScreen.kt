package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Counted
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.TreeStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Numbers about the whole tree. They are worked out when the screen opens, off the main thread, and nothing is stored. */
@Composable
fun StatsScreen(family: Family, onOpen: (Int) -> Unit, scroll: androidx.compose.foundation.ScrollState, onBack: () -> Unit) {
    val stats by produceState<TreeStats?>(null, family) {
        value = withContext(Dispatchers.Default) { TreeStats.of(family, LocalDate.now().year) }
    }
    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(scroll).padding(horizontal = 20.dp)) {
            ScreenHeader("Your tree", "Statistics", Modifier.padding(top = 12.dp, bottom = 20.dp), onBack = onBack)
            val s = stats
            if (s == null) {
                Text("Counting…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
            } else {
                StatCard("People", Icons.Filled.Person, Tones.Sky) {
                    Line("In the tree", "%,d".format(s.people))
                    Line("Men", "%,d".format(s.men))
                    Line("Women", "%,d".format(s.women))
                    if (s.sexNotStated > 0) Line("Sex not stated", "%,d".format(s.sexNotStated))
                    Line("May be alive", "%,d".format(s.mayBeAlive))
                    Line("Died", "%,d".format(s.deceased))
                    Line("Couples", "%,d".format(s.couples))
                    Line("Generations", s.generations.toString())
                }
                StatCard("Records", Icons.Filled.Info, Tones.Teal) {
                    Line("With a photo", "%,d".format(s.withPhoto))
                    Line("With no dates at all", "%,d".format(s.withNoDates))
                }
                StatCard("Notable people", Icons.Filled.Star, Tones.Amber) {
                    s.oldest?.let { PersonLine("Born earliest", it.name, it.years(), onOpen, it.id) }
                    s.youngest?.let { PersonLine("Born most recently", it.name, it.years(), onOpen, it.id) }
                    s.longestLife?.let { PersonLine("Longest life", it.person.name, "${it.value} years", onOpen, it.person.id) }
                    s.mostChildren?.let { PersonLine("Most children", it.person.name, "${it.value}", onOpen, it.person.id) }
                    s.averageLifespan?.let { Line("Average lifespan", "%.1f years".format(Locale.ROOT, it)) }
                    if (s.oldest == null && s.mostChildren == null) Muted("Add some birth years and children to see these.")
                }
                if (s.topSurnames.isNotEmpty()) {
                    StatCard("Most common surnames", Icons.Filled.Favorite, Tones.Rose) {
                        for ((name, n) in s.topSurnames) Line(name, "%,d".format(n))
                    }
                }
                if (s.topPlaces.isNotEmpty()) {
                    StatCard("Most common places", Icons.Filled.Info, Tones.Coral) {
                        for ((place, n) in s.topPlaces) Line(place, "%,d".format(n))
                        Muted("Birth and death places, counted as typed.")
                    }
                }
                if (s.birthsByDecade.isNotEmpty()) {
                    StatCard("Births by decade", Icons.Filled.DateRange, Tones.Violet) {
                        val most = s.birthsByDecade.maxOf { it.second }
                        for ((decade, n) in s.birthsByDecade) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${decade}s", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.width(64.dp))
                                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / most).clip(RoundedCornerShape(50)).background(Tones.Violet))
                                }
                                Text("%,d".format(n), style = MaterialTheme.typography.labelMedium, color = Palette.Text, modifier = Modifier.width(48.dp).padding(start = 8.dp))
                            }
                        }
                    }
                }
                Text(
                    "Worked out from what is recorded: someone with no birth year is left out of the decades, the lifespan and the youngest and oldest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp)) {}
        }
    }
}

@Composable
private fun StatCard(title: String, icon: ImageVector, tone: Color, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(icon, tone, 36.dp)
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
            }
            Column(Modifier.padding(top = 6.dp)) { content() }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
    }
}

/** A line naming a person; tapping it opens them on the tree. */
@Composable
private fun PersonLine(label: String, name: String, detail: String, onOpen: (Int) -> Unit, id: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(id) }.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Text(listOf(name, detail).filter { it.isNotEmpty() }.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(top = 10.dp))
}
