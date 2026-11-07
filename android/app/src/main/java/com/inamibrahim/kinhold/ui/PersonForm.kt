package com.inamibrahim.kinhold.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex

/** Add or edit one person. Nothing is saved until Save is pressed, and Back leaves without saving. */
@Composable
fun PersonFormScreen(
    title: String,
    initial: PersonDraft,
    isEditing: Boolean,
    onSave: (PersonDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var given by rememberSaveable { mutableStateOf(initial.given) }
    var surname by rememberSaveable { mutableStateOf(initial.surname) }
    var sex by rememberSaveable { mutableStateOf(initial.sex) }
    var born by rememberSaveable { mutableStateOf(initial.born?.toString() ?: "") }
    var died by rememberSaveable { mutableStateOf(initial.died?.toString() ?: "") }
    var birthPlace by rememberSaveable { mutableStateOf(initial.birthPlace) }
    var deathPlace by rememberSaveable { mutableStateOf(initial.deathPlace) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val draft = PersonDraft(given, surname, sex, born.toIntOrNull(), died.toIntOrNull(), birthPlace, deathPlace, notes)
    val displayName = listOf(given, surname).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "this person" }
    val problem = when {
        !draft.hasName -> "Add a first name or a surname to save."
        !draft.datesMakeSense -> "The death year is before the birth year."
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
            ScreenHeader(if (isEditing) "Person" else "New person", title, Modifier.padding(top = 12.dp, bottom = 20.dp))

            Field("First names", given, { given = it }, Modifier.fillMaxWidth())
            Field("Surname", surname, { surname = it }, Modifier.fillMaxWidth().padding(top = 14.dp))

            Label("Sex", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Male", sex == Sex.Male, { sex = Sex.Male })
                Pill("Female", sex == Sex.Female, { sex = Sex.Female })
                Pill("Not stated", sex == Sex.Unknown, { sex = Sex.Unknown })
            }

            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Born (year)", born, { born = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), KeyboardType.Number)
                Field("Died (year)", died, { died = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), KeyboardType.Number)
            }
            Field("Place of birth", birthPlace, { birthPlace = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            Field("Place of death", deathPlace, { deathPlace = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            Field("Notes", notes, { notes = it }, Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = false)

            if (problem != null) {
                Text(
                    problem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Danger,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                )
            }
            PrimaryButton("Save", { onSave(draft) }, Modifier.fillMaxWidth().padding(top = 24.dp), enabled = problem == null)
            Pill("Cancel", false, onCancel, Modifier.fillMaxWidth().padding(top = 12.dp))
            if (onDelete != null) {
                Text(
                    "Delete this person",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .clickable { confirmDelete = true }
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete $displayName?", color = Palette.Text) },
            text = {
                Text(
                    "They are removed from the tree along with their links to parents, partners and children. " +
                        "Those relatives stay. You can undo it for a few seconds afterwards.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                Text(
                    "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            confirmDelete = false
                            onDelete()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            },
            dismissButton = {
                Text(
                    "Keep",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { confirmDelete = false }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            },
        )
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = modifier.padding(start = 4.dp))
}

/** A small label above a filled rounded text box, as in the shared design. */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    Column(modifier) {
        Label(label, Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = TextStyle(fontFamily = AppFont, fontSize = 16.sp, color = Palette.Text),
            cursorBrush = SolidColor(Palette.Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.SurfaceHigh)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}
