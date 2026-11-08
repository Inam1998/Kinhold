package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.model.HijriDates
import java.time.LocalDate
import java.time.YearMonth
import java.time.chrono.HijrahChronology

/** What a month calendar needs to know about a calendar: its years, its months, how long a month is and which weekday it starts on. */
interface CalendarSystem {
    val firstYear: Int
    val lastYear: Int
    fun monthName(month: Int): String
    fun monthLength(year: Int, month: Int): Int

    /** The weekday the month starts on, 0 for Sunday to 6 for Saturday. */
    fun firstWeekday(year: Int, month: Int): Int

    /** Today as (year, month, day) in this calendar, or null. */
    fun today(): Triple<Int, Int, Int>?
}

/** The ordinary (Gregorian) calendar, from 1600. Earlier years are entered under "Year only". */
object GregorianSystem : CalendarSystem {
    override val firstYear = 1600
    override val lastYear: Int get() = LocalDate.now().year + 1
    override fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    override fun monthLength(year: Int, month: Int): Int = YearMonth.of(year, month).lengthOfMonth()
    override fun firstWeekday(year: Int, month: Int): Int = LocalDate.of(year, month, 1).dayOfWeek.value % 7
    override fun today(): Triple<Int, Int, Int> = LocalDate.now().let { Triple(it.year, it.monthValue, it.dayOfMonth) }
}

/** The Hijri (Umm al-Qura) calendar the phone provides, 1300 to 1600 AH. */
object HijriSystem : CalendarSystem {
    override val firstYear = 1300
    override val lastYear = 1600
    override fun monthName(month: Int): String = HijriDates.MONTH_NAMES[month - 1]
    override fun monthLength(year: Int, month: Int): Int = HijrahChronology.INSTANCE.date(year, month, 1).lengthOfMonth()
    override fun firstWeekday(year: Int, month: Int): Int = LocalDate.from(HijrahChronology.INSTANCE.date(year, month, 1)).dayOfWeek.value % 7
    override fun today(): Triple<Int, Int, Int>? = HijriDates.fromGregorian(LocalDate.now())
}

/**
 * A month calendar for any [CalendarSystem]. Arrows move one month. Tapping the year opens a list of years, and choosing one leads on to
 * the months and then the days, so a far-away date takes three taps. Tapping the month name opens the months directly. A tap on a day
 * chooses it. The grid has only as many week rows as the month needs, so there is no blank space below it.
 */
@Composable
fun MonthCalendar(system: CalendarSystem, selectedYear: Int?, selectedMonth: Int, selectedDay: Int, modifier: Modifier = Modifier,
    onPickYear: (year: Int) -> Unit = {},
    onPickMonth: (year: Int, month: Int) -> Unit = { _, _ -> },
    onPick: (year: Int, month: Int, day: Int) -> Unit,
) {
    val today = remember(system) { system.today() }
    val first = system.firstYear
    val last = system.lastYear
    var viewYear by rememberSaveable { mutableStateOf((selectedYear ?: today?.first ?: first).coerceIn(first, last)) }
    var viewMonth by rememberSaveable { mutableStateOf(if (selectedYear != null && selectedMonth in 1..12) selectedMonth else today?.second ?: 1) }
    var mode by rememberSaveable { mutableStateOf("days") } // "days", "months" or "years"

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderPart(system.monthName(viewMonth)) { mode = if (mode == "months") "days" else "months" }
            HeaderPart("$viewYear") { mode = if (mode == "years") "days" else "years" }
            Box(Modifier.weight(1f))
            if (mode == "days") {
                ArrowButton(Icons.Filled.KeyboardArrowLeft, "Previous month", enabled = !(viewYear == first && viewMonth == 1)) {
                    if (viewMonth == 1) { viewMonth = 12; viewYear -= 1 } else viewMonth -= 1
                }
                ArrowButton(Icons.Filled.KeyboardArrowRight, "Next month", enabled = !(viewYear == last && viewMonth == 12)) {
                    if (viewMonth == 12) { viewMonth = 1; viewYear += 1 } else viewMonth += 1
                }
            }
        }
        when (mode) {
            "months" -> {
                EqualRow(3) {
                    for (m in 1..12) {
                        PickChip(system.monthName(m).take(3), viewMonth == m, true, modifier = Modifier.weight(1f)) { viewMonth = m; mode = "days"; onPickMonth(viewYear, m) }
                    }
                }
            }
            "years" -> {
                // Years, newest last. After a year is chosen the months are shown, then the days.
                val state = rememberLazyGridState(initialFirstVisibleItemIndex = ((viewYear - first) / 4 * 4 - 4).coerceAtLeast(0))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(232.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(last - first + 1, key = { it }) { i ->
                        val y = first + i
                        PickChip("$y", viewYear == y, true, modifier = Modifier.fillMaxWidth()) { viewYear = y; mode = "months"; onPickYear(y) }
                    }
                }
            }
            else -> Days(
                system, viewYear, viewMonth,
                if (selectedYear == viewYear && selectedMonth == viewMonth) selectedDay else 0,
                today?.takeIf { it.first == viewYear && it.second == viewMonth }?.third ?: 0,
            ) { d -> onPick(viewYear, viewMonth, d) }
        }
    }
}

@Composable
private fun Days(system: CalendarSystem, year: Int, month: Int, selectedDay: Int, todayDay: Int, onDay: (Int) -> Unit) {
    val length = runCatching { system.monthLength(year, month) }.getOrNull() ?: return
    val offset = system.firstWeekday(year, month)
    val rows = (offset + length + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (w in listOf("S", "M", "T", "W", "T", "F", "S")) {
                Text(w, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - offset + 1
                    if (day < 1 || day > length) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val selected = day == selectedDay
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(if (selected) Palette.Accent else androidx.compose.ui.graphics.Color.Transparent)
                                .semantics { contentDescription = "$day ${system.monthName(month)} $year" + if (selected) ", selected" else "" }
                                .clickable(role = Role.Button) { onDay(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$day",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) Palette.OnAccent else if (day == todayDay) Palette.Accent else Palette.Text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderPart(text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(18.dp).padding(start = 2.dp))
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).semantics { contentDescription = description }.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Palette.Text else Palette.TextMuted.copy(alpha = 0.35f))
    }
}
