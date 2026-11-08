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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.inamibrahim.kinhold.model.CustomField
import com.inamibrahim.kinhold.model.DateExtra
import com.inamibrahim.kinhold.model.DateQualifier
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
    qualifier: String,
    onChange: (day: String, month: String, year: String, qualifier: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val parsed = PartialDate.parse(day, month, year, DateExtra.qualifier(qualifier), DateExtra.endYear(qualifier))
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
            onDone = { d -> onChange(d.day?.toString() ?: "", d.month?.toString() ?: "", d.year?.toString() ?: "", DateExtra.encode(d.qualifier, d.endYear)); open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * Choosing a date: a calendar (month grid, with a tap on the month heading to jump to a year) for a full date, or the year list and month
 * chips when only the month or only the year is known. A Hijri tab fills the calendar in from a Hijri date. "How sure" marks a date that
 * is about, before, after or between. Nothing is kept until Done.
 */
@Composable
private fun DatePickerDialog(initial: PartialDate, onDone: (PartialDate) -> Unit, onDismiss: () -> Unit) {
    var year by rememberSaveable { mutableStateOf(initial.year?.toString() ?: "") }
    var month by rememberSaveable { mutableStateOf(initial.month ?: 0) }
    var day by rememberSaveable { mutableStateOf(initial.day ?: 0) }
    var precision by rememberSaveable { mutableStateOf(if (initial.day != null) "day" else if (initial.month != null) "month" else if (initial.year != null) "year" else "day") }
    var qualKey by rememberSaveable { mutableStateOf(initial.qualifier.key) }
    var endYear by rememberSaveable { mutableStateOf(initial.endYear?.toString() ?: "") }
    var hijriMode by rememberSaveable { mutableStateOf(false) }
    var hYear by rememberSaveable { mutableStateOf("") }
    var hMonth by rememberSaveable { mutableStateOf(0) }
    var hDay by rememberSaveable { mutableStateOf(0) }
    val y = year.toIntOrNull()
    if (month != 0 && y == null) month = 0
    // A qualifier needs a year; "between" needs an end year that is not before the start.
    val qualifier = if (y == null) DateQualifier.Exact else DateQualifier.fromKey(qualKey) ?: DateQualifier.Exact
    val endY = endYear.toIntOrNull()
    val chosen = PartialDate(y, month.takeIf { it > 0 }, day.takeIf { it > 0 }, qualifier, if (qualifier == DateQualifier.Between) endY else null)
    val canFinish = chosen.isValid && (qualifier != DateQualifier.Between || endY != null)

    // A complete Hijri date sets the Gregorian date and moves the calendar there.
    val hy = hYear.toIntOrNull()
    val hijriConverted = if (hijriMode && hy != null && hMonth > 0 && hDay > 0) com.inamibrahim.kinhold.model.HijriDates.partialFromHijri(hy, hMonth, hDay) else null
    androidx.compose.runtime.LaunchedEffect(hijriConverted) {
        val h = hijriConverted ?: return@LaunchedEffect
        year = h.year.toString(); month = h.month ?: 0; day = h.day ?: 0; qualKey = DateQualifier.Exact.key; precision = "day"
    }

    fun choosePrecision(p: String) {
        precision = p
        when (p) {
            "month" -> day = 0
            "year" -> { month = 0; day = 0 }
            else -> Unit
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.Surface)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(chosen.text().ifEmpty { "No date" }, style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
                com.inamibrahim.kinhold.model.HijriDates.describe(chosen)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted) }
            }
            EqualRow(2) {
                PickChip("Gregorian", !hijriMode, true, modifier = Modifier.weight(1f)) { hijriMode = false }
                PickChip("Hijri", hijriMode, true, modifier = Modifier.weight(1f)) {
                    if (!hijriMode) {
                        // Start from the Gregorian date already chosen, if it is complete.
                        if (chosen.year != null && chosen.month != null && chosen.day != null) {
                            runCatching { java.time.LocalDate.of(chosen.year, chosen.month, chosen.day) }.getOrNull()
                                ?.let { com.inamibrahim.kinhold.model.HijriDates.fromGregorian(it) }
                                ?.let { (a, b, c) -> hYear = a.toString(); hMonth = b; hDay = c }
                        }
                        hijriMode = true
                    }
                }
            }
            if (hijriMode) {
                MonthCalendar(HijriSystem, hy, hMonth, hDay) { yy, mm, dd -> hYear = yy.toString(); hMonth = mm; hDay = dd }
                Text(
                    when {
                        hijriConverted != null -> "That is ${hijriConverted.text()} in the Gregorian calendar (filled in for you)."
                        hy != null && hMonth > 0 && hDay > 0 -> "That Hijri date does not exist (years 1300 to 1600 only)."
                        else -> "Tap a day; the Gregorian date is worked out for you. Tap the month or the year at the top to jump."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hijriConverted != null) Palette.Accent else Palette.TextMuted,
                )
            } else {
                EqualRow(3) {
                    PickChip("Full date", precision == "day", true, modifier = Modifier.weight(1f)) { choosePrecision("day") }
                    PickChip("Month", precision == "month", true, modifier = Modifier.weight(1f)) { choosePrecision("month") }
                    PickChip("Year only", precision == "year", true, modifier = Modifier.weight(1f)) { choosePrecision("year") }
                }
                if (precision == "day") {
                    MonthCalendar(GregorianSystem, y, month, day) { yy, mm, dd -> year = yy.toString(); month = mm; day = dd }
                } else {
                    // Years are chosen from a list that scrolls (newest first) and opens at the chosen year; typing one still works.
                    Field("Year", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), KeyboardType.Number, placeholder = "Type a year, or choose below")
                    YearGrid(y) { year = it.toString() }
                    if (precision == "month") {
                        Label("Month", Modifier.padding(top = 6.dp))
                        EqualRow(3) {
                            PartialDate.MONTH_NAMES.forEachIndexed { i, name ->
                                PickChip(name, month == i + 1, y != null, modifier = Modifier.weight(1f)) { month = if (month == i + 1) 0 else i + 1 }
                            }
                        }
                    }
                }
            }
            Label("How sure", Modifier.padding(top = 4.dp))
            EqualRow(3) {
                for (q in DateQualifier.entries) {
                    PickChip(q.label, qualifier == q, y != null, modifier = Modifier.weight(1f)) { qualKey = q.key }
                }
            }
            if (qualifier == DateQualifier.Between) {
                Field("Until year", endYear, { endYear = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), KeyboardType.Number, placeholder = "e.g. 1955")
                if (!canFinish) Text("The end year must be the same as or later than the first year.", style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
            }
            Text(
                "Tap a day in the calendar. To go further, tap the year at the top, then a month, then the day. Use Month or Year only when you do not know the rest. About, before, after and between are for dates you are not sure of.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            DialogButtons(centered = true) {
                DialogButton("Clear", { onDone(PartialDate.None) }, kind = ButtonKind.Danger)
                DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
                DialogButton("Done", { onDone(chosen) }, kind = ButtonKind.Primary, enabled = canFinish)
            }
        }
    }
}

@Composable
internal fun PickChip(label: String, selected: Boolean, enabled: Boolean, round: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .then(if (round) Modifier.size(40.dp) else Modifier)
            .clip(if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(50))
            .background(if (selected) Palette.Accent else Palette.SurfaceHigh)
            .semantics(mergeDescendants = true) { if (selected) stateDescription = "Selected" }
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = if (round) 0.dp else 8.dp, vertical = if (round) 0.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
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
                    Field("Source", s.title, { onChange(sources.replaced(i, s.copy(title = it))) }, placeholder = "A book, certificate or website")
                    Field("Where in it", s.citation, { onChange(sources.replaced(i, s.copy(citation = it))) }, placeholder = "Page, entry number or link")
                    Field("Note", s.note, { onChange(sources.replaced(i, s.copy(note = it))) }, singleLine = false, placeholder = "Optional")
DialogButton("Remove this source", { onChange(sources.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.Danger)
                }
            }
        }
        if (sources.size < Limits.SOURCES) Pill("Add a source", false, { onChange(sources + Source()) })
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
    var dateQual by rememberSaveable { mutableStateOf(DateExtra.encode(initial.date.qualifier, initial.date.endYear)) }
    var place by rememberSaveable { mutableStateOf(initial.place) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var sourcesJson by rememberSaveable { mutableStateOf(EventJson.sources(initial.sources).toString()) }
    val sources = remember(sourcesJson) { runCatching { EventJson.readSources(JSONArray(sourcesJson)) }.getOrDefault(emptyList()) }
    // The sources list keeps blank entries while typing, so it is read here without dropping them.
    val editable = remember(sourcesJson) { rawSources(sourcesJson) }

    val kind = EventKind.fromKey(kindKey) ?: EventKind.Custom
    val kinds = (if (forCouple) EventKind.forCouple else EventKind.forPerson).distinct().let {
        if (initial.kind in it) it else it + initial.kind
    }
    val date = PartialDate.parse(day, month, year, DateExtra.qualifier(dateQual), DateExtra.endYear(dateQual))
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
            Field(detailLabel(kind), detail, { detail = it }, Modifier.fillMaxWidth().padding(top = 14.dp), placeholder = "Optional")
            DateFields("Date (any part can be left empty)", day, month, year, dateQual, { d, m, y, q -> day = d; month = m; year = y; dateQual = q }, Modifier.padding(top = 14.dp))
            PlaceField("Place", place, { place = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            Field("Notes", note, { note = it }, Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = false, placeholder = "What else is known about it")

            Label("Sources for this event", Modifier.padding(top = 20.dp, bottom = 8.dp))
            SourcesEditor(editable, { sourcesJson = EventJson.sources(it).toString() })

            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
            }
            FormActions(
                "Save event",
                { onSave(Event(kind, detail, date ?: PartialDate.None, place, note, sources)) },
                problem == null,
                onCancel,
                "Remove this event",
                onRemove,
                Modifier.padding(top = 24.dp),
            )
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
                    Field("Label", f.label, { onChange(fields.replaced(i, f.copy(label = it))) }, placeholder = "e.g. Blood group")
                    Field("Value", f.value, { onChange(fields.replaced(i, f.copy(value = it))) }, placeholder = "e.g. O positive")
DialogButton("Remove this field", { onChange(fields.filterIndexed { j, _ -> j != i }) }, kind = ButtonKind.Danger)
                }
            }
        }
        if (fields.size < Limits.FIELDS) Pill("Add a field", false, { onChange(fields + CustomField()) })
    }
}

/** A grid of years, newest first, that scrolls and opens near the chosen year (or 1980), so a year is a flick and a tap instead of four digits. */
@Composable
private fun YearGrid(selected: Int?, onPick: (Int) -> Unit) {
    val newest = java.time.LocalDate.now().year
    val oldest = 1700
    val count = newest - oldest + 1
    val start = ((newest - (selected ?: 1980)).coerceIn(0, count - 1) / 4 * 4 - 4).coerceAtLeast(0)
    val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState(initialFirstVisibleItemIndex = start)
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
        state = state,
        modifier = Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(18.dp)).background(Palette.SurfaceHigh.copy(alpha = 0.5f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(count, key = { it }) { i ->
            val year = newest - i
            PickChip("$year", selected == year, true, modifier = Modifier.fillMaxWidth()) { onPick(year) }
        }
    }
}
