package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.saveable.rememberSaveable
import com.inamibrahim.kinhold.model.LinkRules
import com.inamibrahim.kinhold.model.LinkType
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

    data class TimelineItem(val entry: com.inamibrahim.kinhold.model.TimelineEntry) : PageItem

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
    vm: com.inamibrahim.kinhold.data.KinholdViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    // Re-read when the person or the tree changes, so a saved edit shows at once.
    val details by produceState<PersonDetails?>(null, family, person.id) { value = loadDetails(person.id) }
    val items = buildItems(family, person, details)
    var changingParent by remember { mutableStateOf<Person?>(null) }
    // Linking someone who is already in the tree: first how, then who, then (for parents and children) what kind of link.
    var choosingLinkType by rememberSaveable { mutableStateOf(false) }
    var linkType by rememberSaveable { mutableStateOf<String?>(null) }
    var linkOther by rememberSaveable { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<RemoveLink?>(null) }
    var linkNotice by remember { mutableStateOf<String?>(null) }

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            ScreenHeader("Person", person.name, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), onBack = onBack)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Edit", Icons.Filled.Edit, onEdit, Modifier.weight(1f), tone = Tones.Amber)
                ActionTile("Link person", AppIcons.Link, { linkNotice = null; choosingLinkType = true }, Modifier.weight(1f), tone = Tones.Violet)
                ActionTile("Share", Icons.Filled.Share, onShare, Modifier.weight(1f), tone = Tones.Teal)
            }
            linkNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Accent, modifier = Modifier.padding(start = 24.dp, end = 20.dp, bottom = 4.dp)) }
            val context = LocalContext.current
            val pin = if (person.lat != null && person.lng != null) GeoPin(person.lat, person.lng) else null
            val canCall = person.phone.isNotBlank()
            val canMail = person.email.isNotBlank()
            if (canCall || canMail || pin != null || person.address.isNotBlank()) {
                var notice by remember { mutableStateOf<String?>(null) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canCall) {
                        ActionTile("Call", Icons.Filled.Call, { Actions.call(context, person.phone) }, Modifier.weight(1f), tone = Tones.Mint)
                        ActionTile("Text", AppIcons.Chat, { Actions.text(context, person.phone) }, Modifier.weight(1f), tone = Tones.Sky)
                        ActionTile("WhatsApp", AppIcons.WhatsApp, { if (!Actions.whatsApp(context, person.phone)) notice = "WhatsApp needs the number with its country code, such as +92 300 1234567. Edit the number to add it." }, Modifier.weight(1f), tone = Tones.Teal)
                    }
                    if (canMail) ActionTile("Email", Icons.Filled.Email, { Actions.email(context, person.email) }, Modifier.weight(1f), tone = Tones.Amber)
                    if (pin != null || person.address.isNotBlank()) ActionTile("Map", Icons.Filled.LocationOn, { Actions.map(context, person.name, person.address, pin) }, Modifier.weight(1f), tone = Tones.Coral)
                }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 6.dp)) }
            }
            // Breathing room under the buttons, and a soft fade so scrolled content melts away instead of being cut off.
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person, 72.dp, ring = 3.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            val dates = if (AppSettings.showHijri) com.inamibrahim.kinhold.model.HijriDates.lifeLine(person.birthDate, person.deathDate) else person.lifeDates()
                            if (dates.isNotEmpty()) LifeText(dates, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            val age = person.ageText(java.time.LocalDate.now())
                            if (age.isNotEmpty()) Text(age, style = MaterialTheme.typography.bodyMedium, color = Tones.Amber)
                            val meId = remember(family) { family.people.firstOrNull { it.isMe }?.id }
                            val relation = remember(family, person.id) { if (meId != null && meId != person.id) com.inamibrahim.kinhold.model.Relationships.between(family, meId, person.id) else null }
                            relation?.let { r -> Text("Your ${r.english.replaceFirstChar(Char::lowercase)}" + (AppSettings.kinLanguage?.let { l -> r.wordIn(l)?.let { " · $it" } } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Tones.Rose) }
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
                item { Column(Modifier.padding(top = 12.dp)) { DetailsHeading("More photos", Modifier.padding(bottom = 8.dp)); GallerySection(vm, person.id) } }
                val parentList = family.parentsOf(person.id)
                if (parentList.isNotEmpty()) {
                    item {
                        LinkBlock("Parents", parentList.map { parent ->
                            LinkRowData(parent.name, listOfNotNull(family.kindOf(person.id, parent.id).label, parent.years().ifEmpty { null }).joinToString("  ·  "), { changingParent = parent }) { removing = RemoveLink(person.id, parent.id, isPartner = false) }
                        })
                    }
                }
                val childList = family.childrenOf(person.id)
                if (childList.isNotEmpty()) {
                    item {
                        LinkBlock("Children", childList.map { child ->
                            LinkRowData(child.name, listOfNotNull(childLabel(family.kindOf(child.id, person.id)), child.years().ifEmpty { null }).joinToString("  ·  "), null) { removing = RemoveLink(child.id, person.id, isPartner = false) }
                        })
                    }
                }
                val spouseList = family.spousesInOrder(person.id)
                if (spouseList.isNotEmpty()) {
                    item {
                        LinkBlock("Spouses and partners", spouseList.map { spouse ->
                            LinkRowData(spouse.name, listOfNotNull(if (family.partnershipEnded(person.id, spouse.id)) "Ended" else null, spouse.years().ifEmpty { null }).joinToString("  ·  ").ifEmpty { null }, null) { removing = RemoveLink(person.id, spouse.id, isPartner = true) }
                        })
                    }
                }
                itemsIndexed(items) { index, item ->
                    when (item) {
                        is PageItem.Heading -> DetailsHeading(item.text, Modifier.padding(top = 18.dp))
                        is PageItem.Line -> Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(0.35f))
                            Text(item.value, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(0.65f))
                        }
                        is PageItem.Text -> Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(horizontal = 4.dp))
                        is PageItem.Note -> Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(horizontal = 4.dp))
                        is PageItem.EventItem -> EventRow(item.event, onClick = null)
                        is PageItem.TimelineItem -> TimelineRow(item.entry, first = items.getOrNull(index - 1) !is PageItem.TimelineItem, last = items.getOrNull(index + 1) !is PageItem.TimelineItem)
                        is PageItem.SourceItem -> Column(Modifier.padding(horizontal = 4.dp)) {
                            Text(item.source.title.ifBlank { "Untitled source" }, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            if (item.source.citation.isNotBlank()) Text(item.source.citation, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                            if (item.source.note.isNotBlank()) Text(item.source.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
                item { Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {} }
            }
            Box(
                Modifier.fillMaxWidth().height(16.dp).align(Alignment.TopCenter).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Palette.Background, androidx.compose.ui.graphics.Color.Transparent)),
                ),
            )
            }
        }
    }
    changingParent?.let { parent ->
        LinkKindDialog(person, parent, family.kindOf(person.id, parent.id), { vm.setLinkKind(person.id, parent.id, it); changingParent = null }, { changingParent = null })
    }

    fun finishLink(otherId: Int, type: LinkType, kind: com.inamibrahim.kinhold.model.LinkKind) {
        vm.linkExisting(person.id, otherId, type, kind) { result ->
            val other = if (otherId in family) family.person(otherId).nameWithYears() else "That person"
            linkNotice = result.fold(
                {
                    when (type) {
                        LinkType.Parent -> "$other is now a parent of ${person.name}."
                        LinkType.Child -> "$other is now a child of ${person.name}."
                        LinkType.Spouse -> "$other and ${person.name} are now partners."
                    }
                },
                { it.message ?: "They could not be linked." },
            )
        }
        linkType = null
        linkOther = null
    }

    if (choosingLinkType) {
        LinkTypeDialog(person, { t -> choosingLinkType = false; linkType = t.name }, { choosingLinkType = false })
    }
    val type = linkType?.let { n -> LinkType.entries.firstOrNull { it.name == n } }
    val other = linkOther?.takeIf { it in family }
    if (type != null && other == null) {
        PersonPickerDialog(
            family = family,
            title = "Who is ${when (type) { LinkType.Parent -> "a parent"; LinkType.Child -> "a child"; LinkType.Spouse -> "a spouse or partner" }} of ${person.name}?",
            onPick = { id -> if (type == LinkType.Spouse) finishLink(id, type, com.inamibrahim.kinhold.model.LinkKind.Birth) else linkOther = id },
            onDismiss = { linkType = null },
            exclude = remember(family, person.id, type) { LinkRules.excluded(family, person.id, type) },
        )
    }
    // After choosing the kind of link for a child, a person with partners is asked whether the child is also the partner's.
    var childKindName by rememberSaveable { mutableStateOf<String?>(null) }
    val childKind = childKindName?.let { n -> com.inamibrahim.kinhold.model.LinkKind.entries.firstOrNull { it.name == n } }
    if (type != null && other != null && childKind == null) {
        // Which kind of parent link it is: for a parent the chosen person is the parent, for a child the chosen person is the child.
        val child = if (type == LinkType.Parent) person else family.person(other)
        val parent = if (type == LinkType.Parent) family.person(other) else person
        val spouses = family.spousesInOrder(person.id)
        LinkKindDialog(
            child, parent, com.inamibrahim.kinhold.model.LinkKind.Birth,
            { kind -> if (type == LinkType.Child && spouses.isNotEmpty()) childKindName = kind.name else finishLink(other, type, kind) },
            { linkType = null; linkOther = null },
        )
    }
    if (type == LinkType.Child && other != null && childKind != null) {
        val child = family.person(other)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { childKindName = null; linkType = null; linkOther = null },
            containerColor = Palette.Surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            title = { Text("Who is the other parent of ${child.nameWithYears()}?", color = Palette.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (spouse in family.spousesInOrder(person.id)) {
                        DialogButton(
                            spouse.nameWithYears(),
                            {
                                finishLink(other, LinkType.Child, childKind)
                                // The child becomes the partner's child too. If the rules refuse that (for example four parents already), say so.
                                vm.linkExisting(spouse.id, other, LinkType.Child, childKind) { r -> r.exceptionOrNull()?.let { linkNotice = "${child.name} was linked to ${person.name}. ${it.message}" } }
                                childKindName = null
                            },
                            Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, icon = Icons.Filled.Person,
                        )
                    }
                    DialogButton("Only ${person.name}", { finishLink(other, LinkType.Child, childKind); childKindName = null }, Modifier.fillMaxWidth(), kind = ButtonKind.Neutral)
                }
            },
            confirmButton = {},
            dismissButton = { DialogButton("Cancel", { childKindName = null; linkType = null; linkOther = null }, kind = ButtonKind.Neutral) },
        )
    }

    removing?.let { r ->
        val a = if (r.first in family) family.person(r.first).name else "Someone"
        val b = if (r.second in family) family.person(r.second).name else "someone"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = Palette.Surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            title = { Text("Remove this link?", color = Palette.Text) },
            text = {
                Text(
                    if (r.isPartner) "$a and $b will no longer be partners. Neither of them is deleted. If a marriage or other event is recorded for them, the link cannot be removed until that event is deleted in Edit."
                    else "$b will no longer be recorded as a parent of $a. Neither of them is deleted, and both keep their other links.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                DialogButtons {
                    DialogButton("Cancel", { removing = null }, kind = ButtonKind.Neutral)
                    DialogButton(
                        "Remove link",
                        {
                            removing = null
                            if (r.isPartner) {
                                vm.unlinkPartners(r.first, r.second) { ok -> linkNotice = if (ok) "The link was removed." else "$a and $b have a marriage or other event recorded. Delete it in Edit first, then remove the link." }
                            } else {
                                vm.unlinkParent(r.first, r.second) { linkNotice = "The link was removed." }
                            }
                        },
                        kind = ButtonKind.Danger,
                    )
                }
            },
        )
    }
}

/** A link to remove: for a parent link [first] is the child and [second] the parent; for a partnership they are the two partners. */
/** One line of the timeline: the date on the left, and what happened, where, how old they were and how many sources on the right. */
@Composable
private fun TimelineRow(entry: com.inamibrahim.kinhold.model.TimelineEntry, first: Boolean, last: Boolean) {
    val tone = when (entry.kind) {
        com.inamibrahim.kinhold.model.TimelineKind.Birth -> Tones.Mint
        com.inamibrahim.kinhold.model.TimelineKind.Death -> Tones.Violet
        com.inamibrahim.kinhold.model.TimelineKind.Marriage -> Tones.Rose
        com.inamibrahim.kinhold.model.TimelineKind.Child -> Tones.Sky
        com.inamibrahim.kinhold.model.TimelineKind.Event -> Tones.Amber
    }
    val icon = when (entry.kind) {
        com.inamibrahim.kinhold.model.TimelineKind.Birth -> AppIcons.Cake
        com.inamibrahim.kinhold.model.TimelineKind.Death -> AppIcons.Candle
        com.inamibrahim.kinhold.model.TimelineKind.Marriage -> AppIcons.Rings
        com.inamibrahim.kinhold.model.TimelineKind.Child -> FamilyIcons.Child
        com.inamibrahim.kinhold.model.TimelineKind.Event -> Icons.Filled.Star
    }
    // Birth and death are the two ends of a life, so their markers are solid; everything between is a soft tint.
    val solid = entry.kind == com.inamibrahim.kinhold.model.TimelineKind.Birth || entry.kind == com.inamibrahim.kinhold.model.TimelineKind.Death
    val hijri = if (AppSettings.showHijri) com.inamibrahim.kinhold.model.HijriDates.describe(entry.date)?.let { " ($it)" }.orEmpty() else ""
    val date = entry.date.text().ifEmpty { "No date" } + hijri
    val railColor = Palette.TextMuted.copy(alpha = 0.28f)
    val badge = 40.dp
    // A rail runs down the left through every marker. It reaches over the gap between rows so the line is unbroken.
    Row(
        Modifier
            .fillMaxWidth()
            .androidx_drawRail(first, last, railColor, badge),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(badge)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Palette.Background)
                .background(if (solid) tone else tone.copy(alpha = if (Palette.dark) 0.22f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (solid) Color(0xCC0B1220) else tone, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 20.dp)) {
            Text(date, style = MaterialTheme.typography.labelLarge, color = tone, modifier = Modifier.padding(top = 2.dp))
            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            if (entry.place.isNotBlank()) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(14.dp))
                    Text(entry.place, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            val chips = listOfNotNull(entry.age.ifEmpty { null }, if (entry.sources > 0) (if (entry.sources == 1) "1 source" else "${entry.sources} sources") else null)
            if (chips.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in chips) {
                        Text(
                            c,
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Accent,
                            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Palette.Accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Draws the timeline rail behind a row: from the top (unless first) to the marker, and from the marker down past the row's end (unless last). */
private fun Modifier.androidx_drawRail(first: Boolean, last: Boolean, color: Color, badge: androidx.compose.ui.unit.Dp): Modifier = this
    // The list leaves 8 dp between rows. Every row except the last reports itself 8 dp shorter, so the next row starts exactly where this
    // one's content ends and the rail is one unbroken line.
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val gap = if (last) 0 else 8.dp.roundToPx()
        layout(p.width, (p.height - gap).coerceAtLeast(0)) { p.place(0, 0) }
    }
    .drawBehind {
        val x = badge.toPx() / 2
        val w = 2.dp.toPx()
        if (!last) drawLine(color, Offset(x, badge.toPx()), Offset(x, size.height + 8.dp.toPx()), strokeWidth = w, cap = StrokeCap.Butt)
    }

/** The name with the years in brackets, so two people with one name can be told apart. */
private fun Person.nameWithYears(): String = if (years().isEmpty()) name else "$name (${years()})"

private class RemoveLink(val first: Int, val second: Int, val isPartner: Boolean)

private class LinkRowData(val name: String, val subtitle: String?, val onChange: (() -> Unit)?, val onRemove: () -> Unit)

private fun childLabel(kind: com.inamibrahim.kinhold.model.LinkKind): String = when (kind) {
    com.inamibrahim.kinhold.model.LinkKind.Birth -> "Birth child"
    com.inamibrahim.kinhold.model.LinkKind.Adopted -> "Adopted child"
    com.inamibrahim.kinhold.model.LinkKind.Foster -> "Foster child"
    com.inamibrahim.kinhold.model.LinkKind.Step -> "Stepchild"
}

/** A heading and one card per linked person, each with its buttons. */
@Composable
private fun LinkBlock(heading: String, rows: List<LinkRowData>) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailsHeading(heading)
        for (row in rows) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tones.Rose) }
                    }
                    row.onChange?.let { DialogButton("Change", it, kind = ButtonKind.Neutral, small = true) }
                    DialogButton("Unlink", row.onRemove, kind = ButtonKind.Neutral, small = true)
                }
            }
        }
    }
}

/** How the person to be linked relates to [person]: parent, child or spouse. */
@Composable
private fun LinkTypeDialog(person: Person, onPick: (LinkType) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        title = { Text("Link someone already in the tree", color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose who they are to ${person.name}. You pick the person next.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                DialogButton("A parent of ${person.name}", { onPick(LinkType.Parent) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, icon = FamilyIcons.Parent)
                DialogButton("A child of ${person.name}", { onPick(LinkType.Child) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, icon = FamilyIcons.Child)
                DialogButton("A spouse or partner", { onPick(LinkType.Spouse) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, icon = Icons.Filled.Favorite)
            }
        },
        confirmButton = { DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral) },
    )
}

private fun buildItems(family: Family, person: Person, details: PersonDetails?): List<PageItem> {
    val out = ArrayList<PageItem>()
    // Birth and death are part of the timeline below; this heading is only for how to reach the person.
    if (person.phone.isNotBlank() || person.email.isNotBlank() || person.address.isNotBlank() || (person.lat != null && person.lng != null)) out += PageItem.Heading("Contact")
    if (person.phone.isNotBlank()) out += PageItem.Line("Phone", person.phone)
    if (person.email.isNotBlank()) out += PageItem.Line("Email", person.email)
    if (person.address.isNotBlank()) out += PageItem.Line("Address", person.address)
    if (person.lat != null && person.lng != null) out += PageItem.Line("Map pin", GeoPin(person.lat, person.lng).text())
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
    out += PageItem.Heading("Timeline")
    val timeline = com.inamibrahim.kinhold.model.Timeline.of(family, person, details)
    if (timeline.isEmpty()) out += PageItem.Text("Nothing recorded yet. Choose Edit to add dates, places, baptism, work, moves and more.")
    for (t in timeline) out += PageItem.TimelineItem(t)
    if (details.sources.isNotEmpty()) {
        out += PageItem.Heading("Sources")
        for (s in details.sources) out += PageItem.SourceItem(s)
    }
    return out
}

/** Choose how a child is linked to one parent: birth, adoptive, foster or step. */
@Composable
private fun LinkKindDialog(child: Person, parent: Person, current: com.inamibrahim.kinhold.model.LinkKind, onPick: (com.inamibrahim.kinhold.model.LinkKind) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        title = { Text("${parent.nameWithYears()} is ${child.nameWithYears()}'s…", color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in com.inamibrahim.kinhold.model.LinkKind.entries) {
                    DialogButton(k.label, { onPick(k) }, Modifier.fillMaxWidth(), kind = if (k == current) ButtonKind.Primary else ButtonKind.Tonal)
                }
                Text("Only birth and adoptive parents count as ancestors in How are we related.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        },
        confirmButton = { DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral) },
    )
}

/** A section heading on the Details page (Parents, Children, Timeline and so on): larger and brighter than the rows under it so the page is easy to scan. */
@Composable
private fun DetailsHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Palette.Text, modifier = modifier.padding(start = 4.dp))
}
