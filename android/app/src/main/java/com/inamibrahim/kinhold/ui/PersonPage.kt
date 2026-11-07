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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Source

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
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    // Re-read when the person or the tree changes, so a saved edit shows at once.
    val details by produceState<PersonDetails?>(null, family, person.id) { value = loadDetails(person.id) }
    val items = buildItems(family, person, details)

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader("Person", person.name, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Back", false, onBack)
                Pill("Edit", false, onEdit)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person, 72.dp, ring = 3.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            val dates = person.lifeDates()
                            if (dates.isNotEmpty()) Text(dates, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text(
                                "${family.parentsOf(person.id).size} parents  ·  ${family.spousesOf(person.id).size} spouses  ·  ${family.childrenOf(person.id).size} children",
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
    if (born.isEmpty() && died.isEmpty()) out += PageItem.Text("No birth or death recorded.")
    if (person.notes.isNotBlank()) {
        out += PageItem.Heading("Notes")
        out += PageItem.Note(person.notes)
    }
    if (details == null) {
        out += PageItem.Text("Loading events…")
        return out
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
