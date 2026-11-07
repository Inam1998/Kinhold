package com.inamibrahim.kinhold.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import com.inamibrahim.kinhold.model.GeoPin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Source
import com.inamibrahim.kinhold.model.ageText
import com.inamibrahim.kinhold.model.relativesOf

/** One line of the person page. The page is a lazy list, so a person with hundreds of events costs only what is on screen. */
private sealed interface PageItem {
    data class Heading(val text: String) : PageItem

    data class Line(val label: String, val value: String) : PageItem

    data class Text(val text: String) : PageItem

    data class EventItem(val event: Event) : PageItem

    data class SourceItem(val source: Source) : PageItem

    data class Note(val text: String) : PageItem
}

/**
 * Everything recorded about one person: dates and places, notes, events, marriages and sources. The events are read from the
 * database when the page opens (and again after a change), never kept for the whole tree.
 */
@Composable
fun PersonPageScreen(
    family: Family,
    person: Person,
    loadDetails: suspend (Int) -> PersonDetails,
    onShare: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    // Re-read when the person or the tree changes, so a saved edit shows at once.
    val details by produceState<PersonDetails?>(null, family, person.id) { value = loadDetails(person.id) }
    val items = buildItems(family, person, details)

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader("Person", person.name, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp))
            Pill("Back", false, onBack, Modifier.padding(horizontal = 20.dp), icon = Icons.AutoMirrored.Filled.ArrowBack)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Edit", Icons.Filled.Edit, onEdit, Modifier.weight(1f), tone = Tones.Amber)
                ActionTile("Share branch", Icons.Filled.Share, onShare, Modifier.weight(1f), tone = Tones.Teal)
            }
            val context = LocalContext.current
            val pin = if (person.lat != null && person.lng != null) GeoPin(person.lat, person.lng) else null
            val canCall = person.phone.isNotBlank()
            val canMail = person.email.isNotBlank()
            if (canCall || canMail || pin != null || person.address.isNotBlank()) {
                var notice by remember { mutableStateOf<String?>(null) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canCall) {
                        ActionTile("Call", Icons.Filled.Call, { Actions.call(context, person.phone) }, Modifier.weight(1f), tone = Tones.Mint)
                        ActionTile("Text", Icons.Filled.Send, { Actions.text(context, person.phone) }, Modifier.weight(1f), tone = Tones.Sky)
                        ActionTile("WhatsApp", Icons.Filled.Send, { if (!Actions.whatsApp(context, person.phone)) notice = "WhatsApp needs the number with its country code, such as +92 300 1234567. Edit the number to add it." }, Modifier.weight(1f), tone = Tones.Teal)
                    }
                    if (canMail) ActionTile("Email", Icons.Filled.Email, { Actions.email(context, person.email) }, Modifier.weight(1f), tone = Tones.Amber)
                    if (pin != null || person.address.isNotBlank()) ActionTile("Map", Icons.Filled.LocationOn, { Actions.map(context, person.name, person.address, pin) }, Modifier.weight(1f), tone = Tones.Coral)
                }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 6.dp)) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person, 72.dp, ring = 3.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            val dates = person.lifeDates()
                            if (dates.isNotEmpty()) Text(dates, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            val age = person.ageText(java.time.LocalDate.now())
                            if (age.isNotEmpty()) Text(age, style = MaterialTheme.typography.bodyMedium, color = Tones.Amber)
                            Text(
                                run {
                                    fun n(c: Int, one: String, many: String) = "$c ${if (c == 1) one else many}"
                                    "${n(family.parentsOf(person.id).size, "parent", "parents")}  ·  ${n(family.spousesOf(person.id).size, "spouse", "spouses")}  ·  ${n(family.childrenOf(person.id).size, "child", "children")}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Accent,
                            )
                        }
                    }
                }
                items(items) { item ->
                    when (item) {
                        is PageItem.Heading -> Text(
                            item.text,
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 14.dp),
                        )
                        is PageItem.Line -> Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(0.35f))
                            Text(item.value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(0.65f))
                        }
                        is PageItem.Text -> Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 4.dp))
                        is PageItem.Note -> Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(horizontal = 4.dp))
                        is PageItem.EventItem -> EventRow(item.event, onClick = null)
                        is PageItem.SourceItem -> Column(Modifier.padding(horizontal = 4.dp)) {
                            Text(item.source.title.ifBlank { "Untitled source" }, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            if (item.source.citation.isNotBlank()) Text(item.source.citation, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                            if (item.source.note.isNotBlank()) Text(item.source.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
                item { Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {} }
            }
        }
    }
}

private fun buildItems(family: Family, person: Person, details: PersonDetails?): List<PageItem> {
    val out = ArrayList<PageItem>()
    out += PageItem.Heading("Life")
    val born = listOf(person.birthDate.text(), person.birthPlace).filter { it.isNotBlank() }.joinToString("  ·  ")
    val died = listOf(person.deathDate.text(), person.deathPlace).filter { it.isNotBlank() }.joinToString("  ·  ")
    if (born.isNotEmpty()) out += PageItem.Line("Born", born)
    if (died.isNotEmpty()) out += PageItem.Line("Died", died)
    if (person.phone.isNotBlank()) out += PageItem.Line("Phone", person.phone)
    if (person.email.isNotBlank()) out += PageItem.Line("Email", person.email)
    if (person.address.isNotBlank()) out += PageItem.Line("Address", person.address)
    if (person.lat != null && person.lng != null) out += PageItem.Line("Map pin", GeoPin(person.lat, person.lng).text())
    if (born.isEmpty() && died.isEmpty()) out += PageItem.Text("No birth or death recorded.")
    if (person.notes.isNotBlank()) {
        out += PageItem.Heading("Notes")
        out += PageItem.Note(person.notes)
    }
    val relatives = family.relativesOf(person.id)
    if (relatives.isNotEmpty()) {
        out += PageItem.Heading("Siblings and step relatives")
        for (r in relatives) out += PageItem.Line(r.label, r.person.name)
    }
    if (details == null) {
        out += PageItem.Text("Loading events…")
        return out
    }
    if (details.fields.isNotEmpty()) {
        out += PageItem.Heading("More about ${person.name}")
        for (f in details.fields) out += PageItem.Line(f.label.ifBlank { "Note" }, f.value)
    }
    out += PageItem.Heading("Events")
    if (details.events.isEmpty()) out += PageItem.Text("No events yet. Choose Edit to add baptism, work, moves and more.")
    for (e in details.events) out += PageItem.EventItem(e)
    for (spouse in family.spousesInOrder(person.id)) {
        val list = details.coupleEvents[spouse.id].orEmpty()
        val ended = family.partnershipEnded(person.id, spouse.id)
        out += PageItem.Heading("With ${spouse.name}${if (ended) " (ended)" else ""}")
        if (list.isEmpty()) out += PageItem.Text("No marriage date or place recorded.")
        for (e in list) out += PageItem.EventItem(e)
    }
    if (details.sources.isNotEmpty()) {
        out += PageItem.Heading("Sources")
        for (s in details.sources) out += PageItem.SourceItem(s)
    }
    return out
}
