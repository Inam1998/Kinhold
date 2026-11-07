@file:OptIn(ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.ageText
import com.inamibrahim.kinhold.model.describeAge
import com.inamibrahim.kinhold.model.relativesOf
import com.inamibrahim.kinhold.tree.layoutFocus

/**
 * The family around one person. Tap anyone to move the focus to them, or use Find. The focus card below the tree has
 * the buttons to change that person or add a relative.
 */
@Composable
fun TreeScreen(
    family: Family,
    focusId: Int,
    onFocus: (Int) -> Unit,
    onAdd: (Relation) -> Unit,
    onAddChild: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onMe: () -> Unit,
    onDetails: (Int) -> Unit,
    lastBackup: Long?,
    onMore: () -> Unit,
    state: TreeViewState,
    undoText: String?,
    onUndo: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    // Back closes the search first, and only then leaves the tree.
    androidx.activity.compose.BackHandler(enabled = searching) { searching = false }
    val layout = remember(family, focusId) { layoutFocus(family, focusId) }

    val focus = family.person(focusId)

    // Frame the view around the focused person, and again once the canvas has a size.
    LaunchedEffect(layout, state.size) {
        state.adopt(layout)
        if (state.size.width > 0 && state.framedFocus != layout.focusId) {
            state.show(layout)
            state.framedFocus = layout.focusId
        }
    }

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
            ScreenHeader("Kinhold", "Family tree", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            val now = System.currentTimeMillis()
            val backupText = lastBackup?.let { "Backed up ${describeAge(it, now)}" } ?: "Not backed up yet"
            // Worth a nudge once there is real work to lose: amber when never backed up, or not for a month.
            val stale = family.size >= 5 && (lastBackup == null || now - lastBackup > 30L * 86_400_000L)
            Text(
                buildAnnotatedString {
                    append("%,d %s in this tree  ·  ".format(family.size, if (family.size == 1) "person" else "people"))
                    withStyle(SpanStyle(color = if (stale) Tones.Amber else Palette.TextMuted)) { append(backupText) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                TreeCanvas(layout = layout, state = state, onFocus = { onFocus(it.person.id) })
                if (!searching) {
                    Pill("Recenter", false, { state.show(layout) }, Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 4.dp).shadow(4.dp, RoundedCornerShape(50)), icon = Icons.Filled.Refresh)
                }
                if (searching) {
                    SearchPanel(
                        family = family,
                        onOldest = { family.oldest()?.let { onFocus(it.id) }; searching = false },
                        onClose = { searching = false },
                        onPick = {
                            onFocus(it.id)
                            searching = false
                        },
                        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 20.dp),
                    )
                }
            }
            if (undoText != null && !searching) {
                AppCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(undoText, style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.weight(1f))
                        DialogButton("Undo", onUndo, Modifier.padding(vertical = 8.dp), kind = ButtonKind.Tonal)
                    }
                }
            }
            // Below the tree, not over it, so the rows at the bottom are never hidden behind the card.
            if (!searching) {
                FocusCard(
                    family = family,
                    person = focus,
                    onAdd = onAdd,
                    onAddChild = { onAddChild(focus.id) },
                    onEdit = { onEdit(focus.id) },
                    onDetails = { onDetails(focus.id) },
                    onFocus = onFocus,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                        .fillMaxWidth(),
                )
            }
            FloatingNav(
                listOf(
                    NavItem("Me", Icons.Filled.Person, false) { onMe(); state.show(layout) },
                    NavItem("Find", Icons.Filled.Search, searching) { searching = !searching },
                    NavItem("More", Icons.Filled.Menu, false, onMore),
                ),
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
            )
        }
    }
}

/** Who the tree is centred on, how many relatives they have, and the buttons to change them or add a relative. */
@Composable
private fun FocusCard(
    family: Family,
    person: Person,
    onAdd: (Relation) -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
    onFocus: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parents = family.parentsOf(person.id).size
    val spouses = family.spousesOf(person.id).size
    val children = family.childrenOf(person.id).size
    AppCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(person, 64.dp, ring = 3.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (person.isMe) {
                            Text("YOU", style = MaterialTheme.typography.labelMedium, color = Palette.OnAccent, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Palette.Accent).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    val age = person.ageText(java.time.LocalDate.now())
                    if (age.isNotEmpty()) Text(age, style = MaterialTheme.typography.bodyMedium, color = Tones.Amber, modifier = Modifier.padding(top = 2.dp))
                    if (person.lifeDates().isNotEmpty()) {
                        Text(
                            person.lifeDates(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        "${plural(parents, "parent")}  ·  ${plural(spouses, "spouse")}  ·  ${plural(children, "child", "children")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // Siblings, half-siblings and step relatives, worked out from the links. Tapping one moves the focus to them.
            // At most four are shown here (the rest are on the Details page), wrapping instead of scrolling sideways.
            val relatives = remember(family, person.id) { family.relativesOf(person.id) }
            if (relatives.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (r in relatives.take(4)) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.SurfaceHigh)
                                .clickable { onFocus(r.person.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(r.label, style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                            Text(r.person.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1)
                        }
                    }
                    if (relatives.size > 4) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.SurfaceHigh)
                                .clickable(onClick = onDetails)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("${relatives.size - 4} more", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                            Text("See all", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                        }
                    }
                }
            }
            // Every action fits on one row: icons over labels, equal widths, nothing hidden off the edge.
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("Details", Icons.Filled.Person, onDetails, Modifier.weight(1f), tone = Tones.Sky)
                ActionTile("Edit", Icons.Filled.Edit, onEdit, Modifier.weight(1f), tone = Tones.Amber)
                // A person has at most two parents, but can have any number of spouses.
                if (parents < Family.MAX_PARENTS) ActionTile("Parent", Icons.Filled.KeyboardArrowUp, { onAdd(Relation.ParentOf(person.id)) }, Modifier.weight(1f), tone = Tones.Violet)
                ActionTile("Spouse", Icons.Filled.Favorite, { onAdd(Relation.SpouseOf(person.id)) }, Modifier.weight(1f), tone = Tones.Rose)
                ActionTile("Child", Icons.Filled.KeyboardArrowDown, onAddChild, Modifier.weight(1f), tone = Tones.Mint)
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

/** A filled pill to type a name in, with the matches listed under it. */
@Composable
private fun SearchPanel(family: Family, onPick: (Person) -> Unit, onOldest: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(family, query) { family.search(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = AppFont, fontSize = 16.sp, color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Palette.SurfaceHigh)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        if (query.isEmpty()) {
                            Text("Type a name", style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
                        }
                        inner()
                    }
                },
            )
            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (query.isBlank()) Pill("Oldest person", false, onOldest, icon = Icons.Filled.Home) else androidx.compose.foundation.layout.Spacer(Modifier)
                DialogButton("Close", onClose, kind = ButtonKind.Neutral, icon = Icons.Filled.Close)
            }
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    "No one with that name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = if (results.isEmpty()) 0.dp else 8.dp)) {
                items(results, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(p) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p, 40.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            // Several people can share a name, so say whose child each one is.
                            val parents = family.parentsOf(p.id)
                            val details = listOf(p.years(), if (parents.isEmpty()) "" else "child of ${parents.joinToString(" & ") { it.name }}")
                                .filter { it.isNotEmpty() }
                                .joinToString("  ·  ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                }
            }
        }
    }
}
