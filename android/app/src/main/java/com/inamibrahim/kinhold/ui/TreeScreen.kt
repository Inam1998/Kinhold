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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.describeAge
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
    onEdit: (Int) -> Unit,
    lastBackup: Long?,
    onBackup: () -> Unit,
    undoText: String?,
    onUndo: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val layout = remember(family, focusId) { layoutFocus(family, focusId) }
    val state = remember { TreeViewState() }
    val focus = family.person(focusId)

    // Frame the view around the focused person, and again once the canvas has a size.
    LaunchedEffect(layout, state.size) { state.show(layout) }

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
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("Find", selected = searching, onClick = { searching = !searching })
                Pill("Recenter", selected = false, onClick = { state.show(layout) })
                Pill("Oldest", selected = false, onClick = { family.oldest()?.let { onFocus(it.id) } })
                Pill("Backup", selected = false, onClick = onBackup)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                TreeCanvas(layout = layout, state = state, onFocus = { onFocus(it.person.id) })
                if (searching) {
                    SearchPanel(
                        family = family,
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
                        Text(
                            "Undo",
                            style = MaterialTheme.typography.labelLarge,
                            color = Palette.Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onUndo)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
            // Below the tree, not over it, so the rows at the bottom are never hidden behind the card.
            if (!searching) {
                FocusCard(
                    family = family,
                    person = focus,
                    onAdd = onAdd,
                    onEdit = { onEdit(focus.id) },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/** Who the tree is centred on, how many relatives they have, and the buttons to change them or add a relative. */
@Composable
private fun FocusCard(
    family: Family,
    person: Person,
    onAdd: (Relation) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parents = family.parentsOf(person.id).size
    val spouses = family.spousesOf(person.id).size
    val children = family.childrenOf(person.id).size
    AppCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            if (person.years().isNotEmpty()) {
                Text(
                    person.years(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "${plural(parents, "parent")}  ·  ${plural(spouses, "spouse")}  ·  ${plural(children, "child", "children")}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Accent,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Edit", selected = false, onClick = onEdit)
                // A person has at most two parents. Several spouses are not drawn yet, so one is offered.
                if (parents < 2) Pill("+ Parent", selected = false, onClick = { onAdd(Relation.ParentOf(person.id)) })
                if (spouses == 0) Pill("+ Spouse", selected = false, onClick = { onAdd(Relation.SpouseOf(person.id)) })
                Pill("+ Child", selected = false, onClick = { onAdd(Relation.ChildOf(person.id)) })
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

/** A filled pill to type a name in, with the matches listed under it. */
@Composable
private fun SearchPanel(family: Family, onPick: (Person) -> Unit, modifier: Modifier = Modifier) {
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
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(p) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
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
