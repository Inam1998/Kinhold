package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
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

    /** The page with everything recorded about one person. */
    data class Person(val id: Int) : Screen

    /** The person form: [editing] is the person being changed, or null when adding one with [relation] to someone. */
    data class Form(val editing: Int?, val relation: Relation?) : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = { s ->
        when (s) {
            Screen.Tree -> "tree"
            Screen.Backup -> "backup"
            Screen.Help -> "help"
            Screen.Settings -> "settings"
            is Screen.Person -> "person|${s.id}"
            is Screen.Form ->"form|${s.editing ?: ""}|" + when (val r = s.relation) {
                null -> ""
                is Relation.ParentOf -> "p${r.childId}"
                is Relation.ChildOf -> "c${r.parentId}:${r.otherParentId ?: ""}"
                is Relation.SpouseOf -> "s${r.personId}"
            }
        }
    },
    restore = { text ->
        if (text == "backup") {
            Screen.Backup
        } else if (text == "help") {
            Screen.Help
        } else if (text == "settings") {
            Screen.Settings
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
            Screen.Form(parts.getOrNull(1)?.toIntOrNull(), relation)
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
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_MILLIS)
            undo = null
        }
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

    val form = screen as? Screen.Form
    when {
        !vm.loaded -> ScreenBackground { }

        screen is Screen.Help -> HelpScreen(onClose = { screen = Screen.Tree })

        screen is Screen.Person -> {
            val shown = (screen as Screen.Person).id
            BackHandler { screen = Screen.Tree }
            if (shown in family) {
                PersonPageScreen(
                    family = family,
                    person = family.person(shown),
                    loadDetails = vm::details,
                    onEdit = { screen = Screen.Form(shown, null) },
                    onBack = { screen = Screen.Tree },
                )
            } else {
                screen = Screen.Tree
            }
        }

        screen is Screen.Settings -> {
            BackHandler { screen = Screen.Tree }
            SettingsScreen(onBack = { screen = Screen.Tree })
        }

        screen is Screen.Backup -> {
            BackHandler { screen = Screen.Tree }
            BackupScreen(vm = vm, peopleCount = family.size, onBack = { screen = Screen.Tree })
        }

        form != null -> {
            val editing = form.editing?.takeIf { it in family }?.let { family.person(it) }
            BackHandler { screen = Screen.Tree }
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
                            vm.update(editing.id, draft, photo, details) { screen = Screen.Tree }
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
                    onCancel = { screen = Screen.Tree },
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
            family = family,
            // The person in focus may have been deleted, or none was chosen yet.
            focusId = if (focusId in family) focusId else family.defaultFocus()?.id ?: family.people.first().id,
            onFocus = { focusId = it },
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
            onHelp = {
                undo = null
                screen = Screen.Help
            },
            onBackup = {
                undo = null
                screen = Screen.Backup
            },
            onSettings = {
                undo = null
                screen = Screen.Settings
            },
            undoText = undo?.let { "Deleted ${it.person.name}" },
            onUndo = {
                undo?.let(vm::undoDelete)
                undo = null
            },
        )
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
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Text,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
    )
}

@Composable
private fun ChoiceRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.Accent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
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
