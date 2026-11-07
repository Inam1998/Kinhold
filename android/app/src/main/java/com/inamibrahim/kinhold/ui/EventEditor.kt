package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventJson
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Limits
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Source
import org.json.JSONArray

/** Three boxes for a date whose day, month and year are each optional. */
@Composable
fun DateFields(
    title: String,
    day: String,
    month: String,
    year: String,
    onChange: (day: String, month: String, year: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Label(title, Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Day", day, { onChange(it.filter(Char::isDigit).take(2), month, year) }, Modifier.weight(1f), KeyboardType.Number)
            Field("Month (1–12)", month, { onChange(day, it.filter(Char::isDigit).take(2), year) }, Modifier.weight(1.3f), KeyboardType.Number)
            Field("Year", year, { onChange(day, month, it.filter(Char::isDigit).take(4)) }, Modifier.weight(1.3f), KeyboardType.Number)
        }
    }
}

/** A list of source citations, each with a title, where in it, and a note; with buttons to add and remove. */
@Composable
fun SourcesEditor(sources: List<Source>, onChange: (List<Source>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sources.forEachIndexed { i, s ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Source (a book, certificate, website…)", s.title, { onChange(sources.replaced(i, s.copy(title = it))) })
                    Field("Where in it (page, entry, link)", s.citation, { onChange(sources.replaced(i, s.copy(citation = it))) })
                    Field("Note", s.note, { onChange(sources.replaced(i, s.copy(note = it))) }, singleLine = false)
                    Text(
                        "Remove this source",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Danger,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onChange(sources.filterIndexed { j, _ -> j != i }) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        }
        if (sources.size < Limits.SOURCES) Pill("+ Add a source", false, { onChange(sources + Source()) })
    }
}

private fun <T> List<T>.replaced(index: Int, item: T): List<T> = mapIndexed { i, old -> if (i == index) item else old }

/** One event in a list: what it is, when and where, and how many sources it has. Tap to open it. */
@Composable
fun EventRow(event: Event, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val where = listOf(event.date.text(), event.place).filter { it.isNotBlank() }.joinToString("  ·  ")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.SurfaceHigh)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(event.title(), style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (event.sources.isNotEmpty()) {
            Text(
                if (event.sources.size == 1) "1 source" else "${event.sources.size} sources",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Accent,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun detailLabel(kind: EventKind): String = when (kind) {
    EventKind.Occupation -> "Job"
    EventKind.Education -> "School or course"
    EventKind.Military -> "Unit, rank or war"
    EventKind.Religion -> "Religion"
    EventKind.Nationality -> "Nationality"
    EventKind.Title -> "Title"
    EventKind.Property -> "Property"
    EventKind.Custom -> "Name of the event"
    else -> "Details (optional)"
}

/**
 * Add or change one event. Nothing is kept until Save is pressed; Back or Cancel leave it as it was. [onRemove] is null for a
 * new event. Everything typed survives a screen rotation.
 */
@Composable
fun EventEditorScreen(
    initial: Event,
    forCouple: Boolean,
    heading: String,
    onSave: (Event) -> Unit,
    onRemove: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var kindKey by rememberSaveable { mutableStateOf(initial.kind.key) }
    var detail by rememberSaveable { mutableStateOf(initial.detail) }
    var day by rememberSaveable { mutableStateOf(initial.date.day?.toString() ?: "") }
    var month by rememberSaveable { mutableStateOf(initial.date.month?.toString() ?: "") }
    var year by rememberSaveable { mutableStateOf(initial.date.year?.toString() ?: "") }
    var place by rememberSaveable { mutableStateOf(initial.place) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var sourcesJson by rememberSaveable { mutableStateOf(EventJson.sources(initial.sources).toString()) }
    val sources = remember(sourcesJson) { runCatching { EventJson.readSources(JSONArray(sourcesJson)) }.getOrDefault(emptyList()) }
    // The sources list keeps blank entries while typing, so it is read here without dropping them.
    val editable = remember(sourcesJson) { rawSources(sourcesJson) }

    val kind = EventKind.fromKey(kindKey) ?: EventKind.Custom
    val kinds = (if (forCouple) EventKind.forCouple else EventKind.forPerson + EventKind.Custom).let {
        if (initial.kind in it) it else it + initial.kind
    }
    val date = PartialDate.parse(day, month, year)
    val problem = when {
        date == null -> "That is not a real date. Check the day, month and year (a month needs a year, and a day needs a month)."
        kind == EventKind.Custom && detail.isBlank() -> "Give this event a name."
        else -> null
    }

    ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            ScreenHeader(if (onRemove == null) "New event" else "Event", heading, Modifier.padding(top = 12.dp, bottom = 20.dp))

            Label("What happened", Modifier.padding(bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in kinds) Pill(k.label, k == kind, { kindKey = k.key })
            }
            Field(detailLabel(kind), detail, { detail = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            DateFields("Date (any part can be left empty)", day, month, year, { d, m, y -> day = d; month = m; year = y }, Modifier.padding(top = 14.dp))
            Field("Place", place, { place = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            Field("Notes", note, { note = it }, Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = false)

            Label("Sources for this event", Modifier.padding(top = 20.dp, bottom = 8.dp))
            SourcesEditor(editable, { sourcesJson = EventJson.sources(it).toString() })

            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
            }
            PrimaryButton(
                "Save event",
                { onSave(Event(kind, detail, date ?: PartialDate.None, place, note, sources)) },
                Modifier.fillMaxWidth().padding(top = 24.dp),
                enabled = problem == null,
            )
            Pill("Cancel", false, onCancel, Modifier.fillMaxWidth().padding(top = 12.dp))
            if (onRemove != null) {
                Text(
                    "Remove this event",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable(onClick = onRemove).padding(vertical = 16.dp),
                )
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
        }
    }
}

/** The sources in the editor's saved text, including blank ones that are still being typed. */
private fun rawSources(json: String): List<Source> = try {
    val array = JSONArray(json)
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Source(o.optString("title", ""), o.optString("citation", ""), o.optString("note", ""))
    }
} catch (e: org.json.JSONException) {
    emptyList()
}
