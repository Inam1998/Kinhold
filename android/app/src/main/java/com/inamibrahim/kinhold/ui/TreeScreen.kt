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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.TestFamily
import com.inamibrahim.kinhold.tree.layoutFocus

private val Sizes = listOf(60, 300, 1000, 3000)

/**
 * The first screen: the family around one person. Tap anyone to move the focus to them, or use Find. The family is a
 * generated test family for now; real people replace it later.
 */
@Composable
fun TreeScreen() {
    var count by rememberSaveable { mutableIntStateOf(300) }
    val family = remember(count) { TestFamily.generateFamily(count) }
    var focusId by rememberSaveable(count) { mutableIntStateOf(family.defaultFocus().id) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val layout = remember(family, focusId) { layoutFocus(family, focusId) }
    val state = remember { TreeViewState() }
    val focus = family.person(focusId)

    // Frame the view around the focused person, and again once the canvas has a size.
    LaunchedEffect(layout, state.size) { state.show(layout) }

    ScreenBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
            ScreenHeader("Kinhold", "Family tree", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            Text(
                "%,d people in this tree".format(family.size),
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
                Pill("Oldest", selected = false, onClick = { focusId = family.oldest().id })
                // Test families of different sizes, until real people replace them.
                for (n in Sizes) Pill("%,d".format(n), selected = n == count, onClick = { count = n })
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                TreeCanvas(layout = layout, state = state, onFocus = { focusId = it.person.id })
                if (searching) {
                    SearchPanel(
                        family = family,
                        onPick = {
                            focusId = it.id
                            searching = false
                        },
                        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 20.dp),
                    )
                }
            }
            // Below the tree, not over it, so the rows at the bottom are never hidden behind the card.
            if (!searching) {
                FocusCard(
                    family = family,
                    person = focus,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/** Who the tree is centred on, and how many relatives they have. */
@Composable
private fun FocusCard(family: Family, person: Person, modifier: Modifier = Modifier) {
    val parents = family.parentsOf(person.id).size
    val spouses = family.spousesOf(person.id).size
    val children = family.childrenOf(person.id).size
    AppCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Text(
                person.years(),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${plural(parents, "parent")}  ·  ${plural(spouses, "spouse")}  ·  ${plural(children, "child", "children")}",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Accent,
                modifier = Modifier.padding(top = 8.dp),
            )
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
                        val line = if (parents.isEmpty()) p.years() else "${p.years()}  ·  child of ${parents.joinToString(" & ") { it.name }}"
                        Text(line, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
            }
        }
    }
}
