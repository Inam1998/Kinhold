@file:OptIn(ExperimentalLayoutApi::class)

package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.draw.rotate
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
import com.inamibrahim.kinhold.model.PersonDraft
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
    onQuickAdd: (PersonDraft, Relation) -> Unit,
    sampleLoaded: Boolean,
    onClearTree: () -> Unit,
    cardCollapsed: Boolean,
    onToggleCard: () -> Unit,
    onKeepSample: () -> Unit,
    suggest: (Relation) -> PersonDraft,
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
    var quickAdding by rememberSaveable { mutableStateOf(false) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    var keeping by rememberSaveable { mutableStateOf(false) }
    if (clearing) ClearSampleDialog(family.size, { clearing = false; onClearTree() }, { clearing = false })
    if (keeping) KeepSampleDialog({ keeping = false; onKeepSample() }, { keeping = false })
    // Back closes the search first, and only then leaves the tree.
    androidx.activity.compose.BackHandler(enabled = searching) { searching = false }
    val layout = remember(family, focusId) { layoutFocus(family, focusId) }
    // Placed before the first draw, so the new tree never flashes in the old position; see TreeViewState.prepareSlide.
    remember(layout) { state.prepareSlide(layout) }
    val slideScope = androidx.compose.runtime.rememberCoroutineScope()

    val focus = family.person(focusId)

    // Frame the view around the focused person, and again once the canvas has a size.
    LaunchedEffect(layout, state.size) {
        state.adopt(layout)
        if (state.size.width > 0 && state.framedFocus != layout.focusId) {
            state.framedFocus = layout.focusId
            state.slideOrShow(layout, slideScope)
        } else if (state.size.width > 0 && state.framedFocus == layout.focusId && state.size != state.framedSize && !state.userMoved) {
            state.reframe(layout, slideScope)
        }
    }

    if (quickAdding) QuickAddDialog(family, focusId, suggest, onQuickAdd) { quickAdding = false }

    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    // With large text the title, card and bar take most of the screen, so they use the compact layout (the same as landscape, except the action tiles stay in two rows).
    val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    val compactUi = landscape || bigText

    val header: @Composable () -> Unit = {
        // Landscape is short, so the big title is left out there and only the one-line status is kept.
        if (!compactUi) ScreenHeader("Kinhold", "Family tree", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
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
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = if (compactUi) 10.dp else 4.dp, bottom = if (compactUi) 0.dp else 12.dp),
        )
        // "On this day": birthdays and remembrance days that fall today, worked out off the main thread. Tap to go to the first one.
        val onThisDay by androidx.compose.runtime.produceState(emptyList<com.inamibrahim.kinhold.model.UpcomingDate>(), family) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.inamibrahim.kinhold.model.Upcoming.compute(family, emptyList(), java.time.LocalDate.now(), withinDays = 0, limit = 6)
            }
        }
        if (onThisDay.isNotEmpty() && !searching) {
            val shown = onThisDay.take(2).joinToString(", ") { d ->
                when (d.kind) {
                    com.inamibrahim.kinhold.model.UpcomingKind.Birthday -> "${d.person.name}'s birthday" + (d.years?.let { " ($it)" } ?: "")
                    com.inamibrahim.kinhold.model.UpcomingKind.Remembrance -> "remembering ${d.person.name}"
                    com.inamibrahim.kinhold.model.UpcomingKind.Anniversary -> "${d.person.name}'s anniversary"
                }
            }
            val more = onThisDay.size - 2
            Text(
                "Today: $shown" + if (more > 0) " and $more more" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Accent,
                maxLines = 2,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onFocus(onThisDay.first().person.id) },
            )
        }
    }
    val banner: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedVisibility(sampleLoaded && !searching) {
            AppCard(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth()) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("This is a sample family.", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                    DialogButton("Keep", { keeping = true }, kind = ButtonKind.Neutral)
                    DialogButton("Clear", { clearing = true }, Modifier.padding(start = 6.dp), kind = ButtonKind.Danger)
                }
            }
        }
    }
    val treeBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(boxModifier.fillMaxWidth()) {
            TreeCanvas(layout = layout, state = state, onFocus = { onFocus(it.person.id) })
            androidx.compose.animation.AnimatedVisibility(!searching, Modifier.align(Alignment.TopCenter), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Pill("Quick add", false, { quickAdding = true }, Modifier.shadow(4.dp, RoundedCornerShape(50)), icon = Icons.Filled.Add)
                    Pill("Recenter", false, { state.reframe(layout, slideScope) }, Modifier.shadow(4.dp, RoundedCornerShape(50)), icon = Icons.Filled.Refresh)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                searching,
                Modifier.align(Alignment.TopCenter),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 3 },
                exit = androidx.compose.animation.fadeOut(),
            ) {
                SearchPanel(
                    family = family,
                    onOldest = { family.oldest()?.let { onFocus(it.id) }; searching = false },
                    onClose = { searching = false },
                    onPick = {
                        onFocus(it.id)
                        searching = false
                    },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
    val undoBar: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedVisibility(undoText != null && !searching) {
            AppCard(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(undoText.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = Palette.Text, modifier = Modifier.weight(1f))
                    DialogButton("Undo", onUndo, Modifier.padding(vertical = 8.dp), kind = ButtonKind.Tonal)
                }
            }
        }
    }
    val card: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedVisibility(
            !searching,
            enter = androidx.compose.animation.expandVertically(cardSize(), Alignment.Bottom) + androidx.compose.animation.fadeIn(cardFade()),
            exit = androidx.compose.animation.shrinkVertically(cardSize(), Alignment.Bottom) + androidx.compose.animation.fadeOut(cardFade()),
        ) {
            FocusCard(
                family = family,
                person = focus,
                onAdd = onAdd,
                onAddChild = { onAddChild(focus.id) },
                onEdit = { onEdit(focus.id) },
                onDetails = { onDetails(focus.id) },
                onFocus = onFocus,
                collapsed = cardCollapsed,
                onToggle = onToggleCard,
                compact = compactUi,
                singleRow = landscape,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = if (compactUi) 8.dp else 12.dp)
                    .fillMaxWidth(),
            )
        }
    }
    val nav: @Composable (Modifier) -> Unit = { navModifier ->
        FloatingNav(
            listOf(
                NavItem("Me", Icons.Filled.Person, false) { onMe(); state.reframe(layout, slideScope) },
                NavItem("Find", Icons.Filled.Search, searching) { searching = !searching },
                NavItem("More", Icons.Filled.Menu, false, onMore),
            ),
            navModifier.padding(horizontal = 20.dp, vertical = if (compactUi) 6.dp else 12.dp).fillMaxWidth(),
        )
    }

    ScreenBackground(maxContentWidth = androidx.compose.ui.unit.Dp.Infinity) {
        if (landscape) {
            // Wide and short: the tree gets the whole left side and everything else sits in a column on the right.
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.displayCutout).imePadding()) {
                treeBox(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(440.dp).fillMaxHeight()) {
                    Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        header()
                        banner()
                        undoBar()
                        card()
                    }
                    nav(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
                header()
                banner()
                treeBox(Modifier.weight(1f).padding(top = 12.dp))
                undoBar()
                // Below the tree, not over it, so the rows at the bottom are never hidden behind the card.
                card()
                nav(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

/** One duration and easing for everything that moves when the card folds or the search opens, so nothing runs ahead of the rest. */
private fun <T> cardSize() = androidx.compose.animation.core.tween<T>(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
private fun <T> cardFade() = androidx.compose.animation.core.tween<T>(220, easing = androidx.compose.animation.core.LinearOutSlowInEasing)

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
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    singleRow: Boolean = false,
) {
    val parents = family.parentsOf(person.id).size
    val spouses = family.spousesOf(person.id).size
    val children = family.childrenOf(person.id).size
    AppCard(modifier) {
        Column(Modifier.padding(if (compact) 12.dp else 20.dp)) {
            // Swiping down on the heading folds the card and swiping up opens it, the same as the chevron.
            val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
            Row(
                Modifier.pointerInput(collapsed) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > swipeThreshold && !collapsed) onToggle() else if (total < -swipeThreshold && collapsed) onToggle() },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(person, androidx.compose.animation.core.animateDpAsState(if (collapsed) 44.dp else if (compact) 52.dp else 64.dp, cardSize(), label = "avatar").value, ring = 3.dp)
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(person.name, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (person.isMe) {
                            Text("YOU", style = MaterialTheme.typography.labelMedium, color = Palette.OnAccent, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Palette.Accent).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    val age = person.ageText(java.time.LocalDate.now())
                    if (age.isNotEmpty()) Text(age, style = MaterialTheme.typography.bodyMedium, color = Tones.Amber, modifier = Modifier.padding(top = 2.dp))
                    val lifeLine = if (AppSettings.showHijri) com.inamibrahim.kinhold.model.HijriDates.lifeLine(person.birthDate, person.deathDate) else person.lifeDates()
                    if (lifeLine.isNotEmpty()) {
                        LifeText(
                            lifeLine,
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
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Palette.SurfaceHigh).clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    val turn by androidx.compose.animation.core.animateFloatAsState(if (collapsed) 180f else 0f, cardSize(), label = "chevron")
                    androidx.compose.material3.Icon(Icons.Filled.KeyboardArrowDown, if (collapsed) "Show actions" else "Hide actions", tint = Palette.Text, modifier = Modifier.rotate(turn))
                }
            }
            // The actions fold up and down only vertically (the default also grows sideways from a corner, which looked cluttered),
            // with the same duration and easing as the avatar and the chevron so the whole card moves as one.
            androidx.compose.animation.AnimatedVisibility(
                !collapsed,
                enter = androidx.compose.animation.expandVertically(cardSize(), Alignment.Top) + androidx.compose.animation.fadeIn(cardFade()),
                exit = androidx.compose.animation.shrinkVertically(cardSize(), Alignment.Top) + androidx.compose.animation.fadeOut(cardFade()),
            ) {
            Column {
            // Siblings, half-siblings and step relatives, worked out from the links. Tapping one moves the focus to them.
            // At most four are shown here (the rest are on the Details page), wrapping instead of scrolling sideways.
            val relatives = remember(family, person.id) { family.relativesOf(person.id) }
            val shown = if (compact) 2 else 4
            if (relatives.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = if (compact) 8.dp else 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (r in relatives.take(shown)) {
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
                    if (relatives.size > shown) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.SurfaceHigh)
                                .clickable(onClick = onDetails)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("${relatives.size - shown} more", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                            Text("See all", style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                        }
                    }
                }
            }
            // Two rows of equal icon tiles, so six actions never get cramped or hidden off the edge.
            val sibling = remember(family, person.id) { siblingRelation(family, person.id) }
            val first: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                ActionTile("Details", Icons.Filled.Person, onDetails, Modifier.weight(1f), tone = Tones.Sky, compact = compact)
                ActionTile("Edit", Icons.Filled.Edit, onEdit, Modifier.weight(1f), tone = Tones.Amber, compact = compact)
                // A person can have up to four parents recorded (birth, adoptive, foster, step) and any number of spouses.
                if (parents < Family.MAX_PARENTS) ActionTile("Parent", FamilyIcons.Parent, { onAdd(Relation.ParentOf(person.id)) }, Modifier.weight(1f), tone = Tones.Violet, compact = compact)
            }
            val second: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                if (sibling != null) ActionTile("Sibling", FamilyIcons.Sibling, { onAdd(sibling) }, Modifier.weight(1f), tone = Tones.Teal, compact = compact)
                ActionTile("Spouse", Icons.Filled.Favorite, { onAdd(Relation.SpouseOf(person.id)) }, Modifier.weight(1f), tone = Tones.Rose, compact = compact)
                ActionTile("Child", FamilyIcons.Child, onAddChild, Modifier.weight(1f), tone = Tones.Mint, compact = compact)
            }
            if (singleRow) {
                // Landscape is short: all the tiles share one row, so the card needs no scrolling.
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { first(); second() }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = first)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = second)
            }
            }
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

/** A filled pill to type a name in, with the matches listed under it. */
@Composable
private fun SearchPanel(family: Family, onPick: (Person) -> Unit, onOldest: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    // The text to search is folded once, off the main thread, when the panel opens; typing then only compares.
    val index by androidx.compose.runtime.produceState<com.inamibrahim.kinhold.model.SearchIndex?>(null, family) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.inamibrahim.kinhold.model.TreeSearch.index(family) }
    }
    val hits = remember(index, query) { index?.find(query).orEmpty() }
    val results = remember(hits) { hits.map { it.person } }
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
                            Text("Name, place or note", style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
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
                    if (index == null) "Getting ready…" else "No one matches that",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = if (results.isEmpty()) 0.dp else 8.dp)) {
                items(hits, key = { it.person.id }) { hit ->
                    val p = hit.person
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
                            // When the match was a place or a note, say which one, so the result makes sense.
                            if (hit.snippet.isNotEmpty()) Text(hit.snippet, style = MaterialTheme.typography.bodySmall, color = Palette.Accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
