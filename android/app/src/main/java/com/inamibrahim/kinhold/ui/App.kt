package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inamibrahim.kinhold.data.DeletedPerson
import com.inamibrahim.kinhold.data.KinholdViewModel
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.delay

/** How long the Undo button stays after a delete. */
private const val UNDO_MILLIS = 10_000L

/** Which screen is showing. Kept across rotation by [ScreenSaver]. */
sealed interface Screen {
    data object Tree : Screen

    data object Backup : Screen

    /** The person form: [editing] is the person being changed, or null when adding one with [relation] to someone. */
    data class Form(val editing: Int?, val relation: Relation?) : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = { s ->
        when (s) {
            Screen.Tree -> "tree"
            Screen.Backup -> "backup"
            is Screen.Form ->"form|${s.editing ?: ""}|" + when (val r = s.relation) {
                null -> ""
                is Relation.ParentOf -> "p${r.childId}"
                is Relation.ChildOf -> "c${r.parentId}"
                is Relation.SpouseOf -> "s${r.personId}"
            }
        }
    },
    restore = { text ->
        if (text == "backup") {
            Screen.Backup
        } else if (!text.startsWith("form|")) {
            Screen.Tree
        } else {
            val parts = text.split("|")
            val relation = parts.getOrElse(2) { "" }.let { r ->
                val id = r.drop(1).toIntOrNull()
                when {
                    id == null -> null
                    r.startsWith("p") -> Relation.ParentOf(id)
                    r.startsWith("c") -> Relation.ChildOf(id)
                    else -> Relation.SpouseOf(id)
                }
            }
            Screen.Form(parts.getOrNull(1)?.toIntOrNull(), relation)
        }
    },
)

/** The whole app: the stored tree, and which of its three screens is showing. */
@Composable
fun KinholdApp(vm: KinholdViewModel = viewModel()) {
    val family by vm.family.collectAsState()
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Tree) }
    var focusId by rememberSaveable { mutableIntStateOf(-1) }
    // The person just deleted, offered for undo for a few seconds.
    var undo by remember { mutableStateOf<DeletedPerson?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_MILLIS)
            undo = null
        }
    }

    val form = screen as? Screen.Form
    when {
        !vm.loaded -> ScreenBackground { }

        screen is Screen.Backup -> {
            BackHandler { screen = Screen.Tree }
            BackupScreen(vm = vm, peopleCount = family.size, onBack = { screen = Screen.Tree })
        }

        form != null -> {
            val editing = form.editing?.takeIf { it in family }?.let { family.person(it) }
            BackHandler { screen = Screen.Tree }
            PersonFormScreen(
                title = formTitle(family, form, editing != null),
                initial = editing?.toDraft() ?: suggestedDraft(family, form.relation),
                isEditing = editing != null,
                onSave = { draft ->
                    if (editing != null) {
                        vm.update(editing.id, draft) { screen = Screen.Tree }
                    } else {
                        vm.add(draft, form.relation) { id ->
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

        family.size == 0 -> StartScreen(
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
            onEdit = {
                undo = null
                screen = Screen.Form(it, null)
            },
            lastBackup = vm.lastBackup,
            onBackup = {
                undo = null
                screen = Screen.Backup
            },
            undoText = undo?.let { "Deleted ${it.person.name}" },
            onUndo = {
                undo?.let(vm::undoDelete)
                undo = null
            },
        )
    }
}

private fun formTitle(family: Family, form: Screen.Form, editing: Boolean): String = when {
    editing -> "Edit person"
    form.relation is Relation.ParentOf -> "Add a parent of ${family.nameOf(form.relation.childId)}"
    form.relation is Relation.ChildOf -> "Add a child of ${family.nameOf(form.relation.parentId)}"
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
            val father = (listOf(parent) + family.spousesOf(parent.id)).firstOrNull { it.sex == Sex.Male } ?: parent
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
