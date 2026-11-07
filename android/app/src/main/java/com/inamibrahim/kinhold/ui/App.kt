package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import com.inamibrahim.kinhold.data.DeletedPerson
import com.inamibrahim.kinhold.data.PhotoChange
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.describeAge
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.delay

/** How long the Undo button stays after a delete. */
private const val UNDO_MILLIS = 10_000L

/** Which screen is showing. Kept across rotation by [ScreenSaver]. */
sealed interface Screen {
    data object Tree : Screen

    data object Backup : Screen

    data object Help : Screen

    data object Settings : Screen

    data object More : Screen

    /** Checks for impossible data and for people entered twice. */
    data object Checks : Screen

    /** Birthdays, remembrance days and anniversaries coming up. */
    data object Upcoming : Screen

    /** How two people are related (the two people can be changed on the screen). */
    data class Related(val first: Int, val second: Int) : Screen

    /** Setting up, changing or turning off the optional PIN lock. */
    data class LockSetup(val mode: LockSetupMode) : Screen

    /** The page with everything recorded about one person. */
    data class Person(val id: Int) : Screen

    /** Choose and save a share file for the branch from one person. */
    data class Share(val id: Int) : Screen

    /** Review a file being merged into the tree. */
    data object Merge : Screen

    /** The person form: [editing] is the person being changed, or null when adding one with [relation] to someone. */
    data class Form(val editing: Int?, val relation: Relation?, val fromPerson: Boolean = false) : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = { s ->
        when (s) {
            Screen.Tree -> "tree"
            Screen.Backup -> "backup"
            Screen.Help -> "help"
            Screen.Settings -> "settings"
            Screen.More -> "more"
            Screen.Checks -> "checks"
            Screen.Upcoming -> "upcoming"
            is Screen.Related -> "relation|${s.first}|${s.second}"
            is Screen.LockSetup -> "lock|${s.mode.name}"
            is Screen.Person -> "person|${s.id}"
            is Screen.Share -> "share|${s.id}"
            Screen.Merge -> "merge"
            is Screen.Form ->"form|${s.editing ?: ""}|" + when (val r = s.relation) {
                null -> ""
                is Relation.ParentOf -> "p${r.childId}"
                is Relation.ChildOf -> "c${r.parentId}:${r.otherParentId ?: ""}"
                is Relation.SpouseOf -> "s${r.personId}"
            } + if (s.fromPerson) "|f" else ""
        }
    },
    restore = { text ->
        if (text == "backup") {
            Screen.Backup
        } else if (text == "help") {
            Screen.Help
        } else if (text == "settings") {
            Screen.Settings
        } else if (text.startsWith("lock|")) {
            Screen.LockSetup(LockSetupMode.entries.firstOrNull { it.name == text.substringAfter("|") } ?: LockSetupMode.On)
        } else if (text.startsWith("relation|")) {
            val p = text.split("|")
            Screen.Related(p.getOrNull(1)?.toIntOrNull() ?: -1, p.getOrNull(2)?.toIntOrNull() ?: -1)
        } else if (text == "upcoming") {
            Screen.Upcoming
        } else if (text == "checks") {
            Screen.Checks
        } else if (text == "more") {
            Screen.More
        } else if (text == "merge") {
            Screen.Merge
        } else if (text.startsWith("share|")) {
            text.substringAfter("|").toIntOrNull()?.let { Screen.Share(it) } ?: Screen.Tree
        } else if (text.startsWith("person|")) {
            text.substringAfter("|").toIntOrNull()?.let { Screen.Person(it) } ?: Screen.Tree
        } else if (!text.startsWith("form|")) {
            Screen.Tree
        } else {
            val parts = text.split("|")
            val relation = parts.getOrElse(2) { "" }.let { r ->
                val id = r.drop(1).substringBefore(':').toIntOrNull()
                when {
                    id == null -> null
                    r.startsWith("p") -> Relation.ParentOf(id)
                    r.startsWith("c") -> Relation.ChildOf(id, r.substringAfter(':', "").toIntOrNull())
                    else -> Relation.SpouseOf(id)
                }
            }
            Screen.Form(parts.getOrNull(1)?.toIntOrNull(), relation, parts.getOrNull(3) == "f")
        }
    },
)

/** The whole app: the stored tree, and which of its screens is showing. */
@Composable
fun KinholdApp(vm: KinholdViewModel = viewModel()) {
    // One cache of people's pictures for every screen, so a photo is decoded once however many places show it.
    val scope = rememberCoroutineScope()
    val avatars = remember { AvatarCache(scope) { id -> vm.thumb(id) } }
    CompositionLocalProvider(LocalAvatars provides avatars) { KinholdContent(vm) }
}

@Composable
private fun KinholdContent(vm: KinholdViewModel) {
    val family by vm.family.collectAsState()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Tree) }
    var focusId by rememberSaveable { mutableIntStateOf(-1) }
    // The person just deleted, offered for undo for a few seconds.
    var undo by remember { mutableStateOf<DeletedPerson?>(null) }
    // Set while asking who the other parent is for a child of someone with several spouses.
    var choosingParentFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var noMeNotice by rememberSaveable { mutableStateOf(false) }
    // Each screen keeps its scroll position while the person goes deeper and comes back; going forward to it starts fresh.
    val scrolls = remember { ScrollStore() }
    val nav = remember { NavMemory(screen) }
    if (nav.previous != screen) {
        if (screen.depth() > nav.previous.depth()) scrolls.reset(screen.key())
        nav.previous = screen
    }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_MILLIS)
            undo = null
        }
    }

    if (noMeNotice) {
        AlertDialog(
            onDismissRequest = { noMeNotice = false },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Who are you?", color = Palette.Text) },
            text = { Text("Open yourself in the tree, choose Details, then This is me. After that the Me button brings the tree back to you from anywhere.", color = Palette.TextMuted) },
            confirmButton = {
                DialogButton("OK", { noMeNotice = false }, kind = ButtonKind.Primary)
            },
        )
    }

    choosingParentFor?.let { parentId ->
        OtherParentDialog(
            family = family,
            parentId = parentId,
            onChoose = { other ->
                choosingParentFor = null
                screen = Screen.Form(null, Relation.ChildOf(parentId, other))
            },
            onDismiss = { choosingParentFor = null },
        )
    }

    AnimatedContent(
        targetState = screen,
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        transitionSpec = {
            // The new screen slides over the old one, which only drifts back a little and never fades: every screen is opaque,
            // so nothing but the app's own background can show through (no flash of black between screens).
            val back = targetState.depth() < initialState.depth()
            val ease = androidx.compose.animation.core.FastOutSlowInEasing
            val enter = slideInHorizontally(tween(280, easing = ease)) { if (back) -it / 4 else it }
            val exit = slideOutHorizontally(tween(280, easing = ease)) { if (back) it else -it / 4 }
            ContentTransform(enter, exit, targetContentZIndex = if (back) -1f else 1f, sizeTransform = null)
        },
        label = "screen",
    ) { target ->
    val form = target as? Screen.Form
    when {
        !vm.loaded -> ScreenBackground { }

        target is Screen.More -> {
            BackHandler { screen = Screen.Tree }
            val status = vm.lastBackup?.let { "Last backup: ${describeAge(it, System.currentTimeMillis())}" } ?: "Not backed up yet"
            val firstFocus = family.people.firstOrNull { it.isMe }?.id ?: focusId
            MoreScreen(
                items = listOf(
                    MoreItem(Icons.Filled.Lock, Tones.Mint, "Backup and sharing", "$status. Save, restore, merge and import") { screen = Screen.Backup },
                    MoreItem(Icons.Filled.Favorite, Tones.Rose, "How are we related?", "Pick two people and see the relationship, with Urdu and Punjabi words") { if (family.size > 0) screen = Screen.Related(firstFocus, focusId) },
                    MoreItem(Icons.Filled.Notifications, Tones.Amber, "Upcoming dates", "Birthdays, anniversaries and remembrance days, with an optional reminder") { screen = Screen.Upcoming },
                    MoreItem(Icons.Filled.Check, Tones.Teal, "Checks and duplicates", "Impossible dates, and people entered twice") { screen = Screen.Checks },
                    MoreItem(Icons.Filled.Settings, Tones.Sky, "Settings", "Who you are, app lock, theme and accent colour") { screen = Screen.Settings },
                    MoreItem(Icons.Filled.Info, Tones.Amber, "How Kinhold works", "A page for every feature, with its limits") { screen = Screen.Help },
                ),
                onBack = { screen = Screen.Tree },
            )
        }

        target is Screen.Help -> {
            BackHandler { screen = if (family.size == 0) Screen.Tree else Screen.More }
            HelpScreen(listScroll = scrolls.scroll("help"), onClose = { screen = if (family.size == 0) Screen.Tree else Screen.More })
        }

        target is Screen.Related -> {
            val rel = target as Screen.Related
            BackHandler { screen = Screen.More }
            if (family.size > 0) {
                val me = family.people.firstOrNull { it.isMe }?.id
                val fallback = focusId.takeIf { it in family } ?: family.people.first().id
                RelationScreen(family, rel.first.takeIf { it in family } ?: me ?: fallback, rel.second.takeIf { it in family } ?: fallback, onBack = { screen = Screen.More })
            } else {
                screen = Screen.Tree
            }
        }

        target is Screen.Upcoming -> {
            BackHandler { screen = Screen.More }
            val marriages by produceState<List<com.inamibrahim.kinhold.model.MarriageDate>?>(null, family) { value = vm.marriageDates() }
            UpcomingScreen(family, marriages, onOpen = { id -> focusId = id; screen = Screen.Tree }, onBack = { screen = Screen.More })
        }

        target is Screen.Checks -> {
            BackHandler { screen = Screen.More }
            val marriages by produceState<List<com.inamibrahim.kinhold.model.MarriageDate>?>(null, family) { value = vm.marriageDates(false) }
            ChecksScreen(vm, family, marriages, onOpen = { id -> focusId = id; screen = Screen.Tree }, onBack = { screen = Screen.More })
        }

        target is Screen.LockSetup -> {
            BackHandler { screen = Screen.Settings }
            LockSetupScreen((target as Screen.LockSetup).mode, onDone = { screen = Screen.Settings }, onCancel = { screen = Screen.Settings })
        }

        target is Screen.Share -> {
            val shown = (target as Screen.Share).id
            BackHandler { screen = Screen.Person(shown) }
            if (shown in family) {
                ShareScreen(vm, family, family.person(shown), onBack = { screen = Screen.Person(shown) })
            } else {
                screen = Screen.Tree
            }
        }

        target is Screen.Merge -> {
            val session = vm.mergeSession
            BackHandler { vm.cancelMerge(); screen = Screen.Backup }
            if (session != null) MergeScreen(vm, session, onDone = { screen = Screen.Tree }) else screen = Screen.Backup
        }

        target is Screen.Person -> {
            val shown = (target as Screen.Person).id
            BackHandler { screen = Screen.Tree }
            if (shown in family) {
                PersonPageScreen(
                    family = family,
                    person = family.person(shown),
                    loadDetails = vm::details,
                    onEdit = { screen = Screen.Form(shown, null, fromPerson = true) },
                    listState = scrolls.lazy("person"),
                    onShare = { screen = Screen.Share(shown) },
                    onBack = { screen = Screen.Tree },
                )
            } else {
                screen = Screen.Tree
            }
        }

        target is Screen.Settings -> {
            BackHandler { screen = if (family.size == 0) Screen.Tree else Screen.More }
            SettingsScreen(family, scroll = scrolls.scroll("settings"), onChooseMe = vm::setMe, onLock = { screen = Screen.LockSetup(it) }, onBack = { screen = if (family.size == 0) Screen.Tree else Screen.More })
        }

        target is Screen.Backup -> {
            BackHandler { screen = Screen.More }
            BackupScreen(vm = vm, peopleCount = family.size, scroll = scrolls.scroll("backup"), onBack = { screen = Screen.More }, onMerge = { screen = Screen.Merge })
        }

        form != null -> {
            val editing = form.editing?.takeIf { it in family }?.let { family.person(it) }
            // Back, Cancel and Save return to where the form was opened from: the person page or the tree.
            val backTo: Screen = if (form.fromPerson && editing != null) Screen.Person(editing.id) else Screen.Tree
            BackHandler { screen = backTo }
            // An existing person's events are read from the database once, when the form opens.
            val loaded by produceState<PersonDetails?>(if (editing == null) PersonDetails.Empty else null, editing?.id) {
                if (editing != null) value = vm.details(editing.id)
            }
            val initialDetails = loaded
            if (initialDetails == null) {
                ScreenBackground { }
            } else {
                PersonFormScreen(
                    title = formTitle(family, form, editing != null),
                    initial = editing?.toDraft() ?: suggestedDraft(family, form.relation),
                    person = editing,
                    isEditing = editing != null,
                    initialDetails = initialDetails,
                    spouses = formSpouses(family, editing?.id, form.relation),
                    onPreparePhoto = vm::preparePhoto,
                    onSave = { draft, photo, details ->
                        if (editing != null) {
                            vm.update(editing.id, draft, photo, details) { screen = backTo }
                        } else {
                            vm.add(draft, form.relation, (photo as? PhotoChange.Set)?.photo, details) { id ->
                                // The first person becomes the focus; anyone added to a relative leaves the focus where it was.
                                if (form.relation == null) focusId = id
                                screen = Screen.Tree
                            }
                        }
                    },
                    onDelete = if (editing != null) {
                        {
                            vm.delete(editing.id) { deleted ->
                                undo = deleted
                                screen = Screen.Tree
                            }
                        }
                    } else {
                        null
                    },
                    onCancel = { screen = backTo },
                    scroll = scrolls.scroll("form"),
                )
            }
        }

        family.size == 0 -> StartScreen(
            vm = vm,
            onHelp = { screen = Screen.Help },
            onSettings = { screen = Screen.Settings },
            onAddFirst = { screen = Screen.Form(null, null) },
            onSample = vm::addSampleFamily,
        )

        else -> TreeScreen(
            state = scrolls.tree,
            family = family,
            // The person in focus may have been deleted, or none was chosen yet.
            focusId = if (focusId in family) focusId else family.people.firstOrNull { it.isMe }?.id ?: family.defaultFocus()?.id ?: family.people.first().id,
            onFocus = { focusId = it },
            onMe = {
                val me = family.people.firstOrNull { it.isMe }
                if (me != null) focusId = me.id else noMeNotice = true
            },
            onAdd = {
                undo = null
                screen = Screen.Form(null, it)
            },
            onAddChild = { parentId ->
                undo = null
                val spouses = family.spousesInOrder(parentId)
                // With one spouse the child is theirs; with several the person is asked who the other parent is.
                when (spouses.size) {
                    0 -> screen = Screen.Form(null, Relation.ChildOf(parentId, null))
                    1 -> screen = Screen.Form(null, Relation.ChildOf(parentId, spouses[0].id))
                    else -> choosingParentFor = parentId
                }
            },
            onDetails = {
                undo = null
                screen = Screen.Person(it)
            },
            onEdit = {
                undo = null
                screen = Screen.Form(it, null)
            },
            lastBackup = vm.lastBackup,
            onMore = {
                undo = null
                screen = Screen.More
            },
            undoText = undo?.let { "Deleted ${it.person.name}" },
            onUndo = {
                undo?.let(vm::undoDelete)
                undo = null
            },
        )
    }
    }
}

/** Asks who the other parent of a new child is when the person has several spouses. */
@Composable
private fun OtherParentDialog(
    family: Family,
    parentId: Int,
    onChoose: (otherParentId: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (parentId !in family) {
        onDismiss()
        return
    }
    val parent = family.person(parentId)
    val spouses = family.spousesInOrder(parentId)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Who is the other parent?", color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (s in spouses) {
                    ChoiceRow("${s.name}${if (s.years().isEmpty()) "" else "  ·  ${s.years()}"}") { onChoose(s.id) }
                }
                ChoiceRow("Only ${parent.name}") { onChoose(null) }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogButton("Cancel", onDismiss, kind = ButtonKind.Neutral)
        },
    )
}

@Composable
private fun ChoiceRow(label: String, onClick: () -> Unit) {
    DialogButton(label, onClick, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
}

private fun formTitle(family: Family, form: Screen.Form, editing: Boolean): String = when {
    editing -> "Edit person"
    form.relation is Relation.ParentOf -> "Add a parent of ${family.nameOf(form.relation.childId)}"
    form.relation is Relation.ChildOf -> {
        val other = form.relation.otherParentId?.takeIf { it in family }
        "Add a child of ${family.nameOf(form.relation.parentId)}" + if (other != null) " and ${family.nameOf(other)}" else ""
    }
    form.relation is Relation.SpouseOf -> "Add a spouse of ${family.nameOf(form.relation.personId)}"
    family.size == 0 -> "Add the first person"
    else -> "Add a person"
}

private fun Family.nameOf(id: Int): String = if (id in this) person(id).name else "someone"

/** Starting values that save typing: a child gets their father's surname, a spouse the opposite sex. */
private fun suggestedDraft(family: Family, relation: Relation?): PersonDraft = when (relation) {
    null -> PersonDraft()
    is Relation.ChildOf -> {
        if (relation.parentId !in family) {
            PersonDraft()
        } else {
            val parent = family.person(relation.parentId)
            val other = relation.otherParentId?.takeIf { it in family }?.let(family::person)
            val father = listOfNotNull(parent, other).firstOrNull { it.sex == Sex.Male } ?: parent
            PersonDraft(surname = father.surname)
        }
    }
    is Relation.SpouseOf -> {
        val sex = if (relation.personId in family) family.person(relation.personId).sex else Sex.Unknown
        PersonDraft(sex = opposite(sex))
    }
    is Relation.ParentOf -> {
        val other = if (relation.childId in family) family.parentsOf(relation.childId).firstOrNull() else null
        PersonDraft(sex = opposite(other?.sex ?: Sex.Unknown))
    }
}

private fun opposite(sex: Sex): Sex = when (sex) {
    Sex.Male -> Sex.Female
    Sex.Female -> Sex.Male
    Sex.Unknown -> Sex.Unknown
}

/** The partners whose marriage and other couple events can be edited in the person form, with their names. */
private fun formSpouses(family: Family, editingId: Int?, relation: Relation?): List<Pair<Int, String>> = when {
    editingId != null -> family.spousesInOrder(editingId).map { it.id to it.name }
    relation is Relation.SpouseOf && relation.personId in family -> listOf(relation.personId to family.person(relation.personId).name)
    relation is Relation.ParentOf && relation.childId in family ->
        family.parentsOf(relation.childId).take(1).map { it.id to it.name }
    else -> emptyList()
}

/** How deep a screen is: going to a lower number is "back", so the screen slides in from the left, otherwise from the right. */
private fun Screen.depth(): Int = when (this) {
    Screen.Tree -> 0
    Screen.More, is Screen.Person -> 1
    Screen.Backup, Screen.Settings, Screen.Help, is Screen.Share, is Screen.Form, is Screen.Related, Screen.Upcoming, Screen.Checks -> 2
    Screen.Merge, is Screen.LockSetup -> 3
}

private class NavMemory(var previous: Screen)

/** The scroll positions of the screens that scroll, kept while the app runs so Back finds each screen where it was left. */
class ScrollStore {
    private val scrolls = HashMap<String, androidx.compose.foundation.ScrollState>()
    private val lists = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()

    /** Zoom and position of the tree, kept so Back returns to the same view. */
    val tree = TreeViewState()

    fun scroll(key: String) = scrolls.getOrPut(key) { androidx.compose.foundation.ScrollState(0) }

    fun lazy(key: String) = lists.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }

    fun reset(key: String) {
        scrolls.remove(key)
        lists.remove(key)
    }
}

private fun Screen.key(): String = when (this) {
    Screen.Tree -> "tree"
    Screen.More -> "more"
    Screen.Backup -> "backup"
    Screen.Settings -> "settings"
    Screen.Help -> "help"
    Screen.Merge -> "merge"
    is Screen.LockSetup -> "locksetup"
    is Screen.Related -> "relation"
    Screen.Upcoming -> "upcoming"
    Screen.Checks -> "checks"
    is Screen.Person -> "person"
    is Screen.Share -> "share"
    is Screen.Form -> "form"
}
