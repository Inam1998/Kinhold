@file:OptIn(ExperimentalLayoutApi::class)

package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.inamibrahim.kinhold.model.CustomField
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventJson
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Limits
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Source
import org.json.JSONArray

/**
 * A date whose year, month and day are each optional, shown as one tappable box. Tapping opens a picker: type or step the
 * year, then touch a month and a day. The three values are kept as text so forms can store them across a rotation.
 */
@Composable
fun DateFields(
    title: String,
    day: String,
    month: String,
    year: String,
    onChange: (day: String, month: String, year: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val parsed = PartialDate.parse(day, month, year)
    val shown = parsed?.text().orEmpty()
    Column(modifier) {
        Label(title, Modifier.padding(bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.SurfaceHigh)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneIcon(Icons.Filled.DateRange, Tones.Sky, 32.dp)
            Text(
                if (parsed == null) "Not a real date: tap to fix" else shown.ifEmpty { "Tap to set a date" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (parsed == null) Palette.Danger else if (shown.isEmpty()) Palette.TextMuted else Palette.Text,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
    if (open) {
        DatePickerDialog(
            initial = parsed ?: PartialDate.None,
            onDone = { d -> onChange(d.day?.toString() ?: "", d.month?.toString() ?: "", d.year?.toString() ?: ""); open = false },
            onDismiss = { open = false },
        )
    }
}

private val DAYS_IN_MONTH = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

@Composable
private fun DatePickerDialog(initial: PartialDate, onDone: (PartialDate) -> Unit, onDismiss: () -> Unit) {
    var year by rememberSaveable { mutableStateOf(initial.year?.toString() ?: "") }
    var month by rememberSaveable { mutableStateOf(initial.month ?: 0) }
    var day by rememberSaveable { mutableStateOf(initial.day ?: 0) }
    val y = year.toIntOrNull()
    val canMonth = y != null
    if (month != 0 && !canMonth) month = 0
    val maxDay = if (month in 1..12) DAYS_IN_MONTH[month - 1] else 0
    if (day > maxDay) day = 0
    val preview = PartialDate(y, month.takeIf { it > 0 }, day.takeIf { it > 0 }).text().ifEmpty { "No date" }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(preview, color = Palette.Accent) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Year")
                Field("Year (for example 1950)", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), KeyboardType.Number)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (step in listOf(-10, -1, 1, 10)) {
                        PickChip(if (step > 0) "+$step" else "−${-step}", false, true) { year = ((y ?: 1950) + step).coerceIn(0, 9999).toString() }
                    }
                }
                Label("Month", Modifier.padding(top = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PartialDate.MONTH_NAMES.forEachIndexed { i, name ->
                        PickChip(name, month == i + 1, canMonth) { month = if (month == i + 1) 0 else i + 1 }
                    }
                }
                if (month != 0) {
                    Label("Day", Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in 1..maxDay) PickChip("$d", day == d, true, round = true) { day = if (day == d) 0 else d }
                    }
                }
                Text(
                    "Every part is optional. Touch a chosen month or day again to clear it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton("Clear", { onDone(PartialDate.None) }, kind = ButtonKind.Danger)
                DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
                DialogButton("Done", { onDone(PartialDate(y, month.takeIf { it > 0 }, day.takeIf { it > 0 })) }, kind = ButtonKind.Primary)
            }
        },
    )
}

@Composable
private fun PickChip(label: String, selected: Boolean, enabled: Boolean, round: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .then(if (round) Modifier.size(40.dp) else Modifier)
            .clip(if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (round) 0.dp else 14.dp, vertical = if (round) 0.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Palette.OnAccent else if (enabled) Palette.Text else Palette.TextMuted.copy(alpha = 0.4f),
        )
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
DialogButton("Remove this source", { onChange(sources.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.Danger)
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

            var pickingKind by rememberSaveable { mutableStateOf(false) }
            Label("What happened", Modifier.padding(bottom = 6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.SurfaceHigh).clickable { pickingKind = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToneIcon(Icons.Filled.Star, Tones.Amber, 32.dp)
                Text(kind.label, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Text("Change", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
            }
            if (pickingKind) {
                AlertDialog(
                    onDismissRequest = { pickingKind = false },
                    containerColor = Palette.Surface,
                    shape = RoundedCornerShape(28.dp),
                    title = { Text("What happened?", color = Palette.Text) },
                    text = {
                        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            for (k in kinds) {
                                Text(
                                    k.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (k == kind) Palette.Accent else Palette.Text,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { kindKey = k.key; pickingKind = false }.padding(horizontal = 12.dp, vertical = 12.dp),
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { DialogButton("Cancel", { pickingKind = false }, kind = ButtonKind.Neutral) },
                )
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
DialogButton("Remove this event", onRemove, Modifier.fillMaxWidth(), kind = ButtonKind.Danger)
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

/** The person's own facts: each a label and a value, with buttons to add and remove. */
@Composable
fun FieldsEditor(fields: List<CustomField>, onChange: (List<CustomField>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fields.forEachIndexed { i, f ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Label (for example Blood group)", f.label, { onChange(fields.replaced(i, f.copy(label = it))) })
                    Field("Value", f.value, { onChange(fields.replaced(i, f.copy(value = it))) })
DialogButton("Remove this field", { onChange(fields.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.Danger)
                }
            }
        }
        if (fields.size < Limits.FIELDS) Pill("+ Add a field", false, { onChange(fields + CustomField()) })
    }
}
