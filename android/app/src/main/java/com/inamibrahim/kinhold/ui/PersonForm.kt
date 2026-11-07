package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.inamibrahim.kinhold.data.PhotoChange
import com.inamibrahim.kinhold.data.PreparedPhoto
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.Limits
import com.inamibrahim.kinhold.model.EventJson
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Sex

/** Add or edit one person. Nothing is saved until Save is pressed, and Back leaves without saving. */
@Composable
fun PersonFormScreen(
    title: String,
    initial: PersonDraft,
    person: Person?,
    isEditing: Boolean,
    onPreparePhoto: (Uri, (Result<PreparedPhoto>) -> Unit) -> Unit,
    initialDetails: PersonDetails,
    spouses: List<Pair<Int, String>>,
    onSave: (PersonDraft, PhotoChange, PersonDetails) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    // A picked photo is only prepared here; it is stored when Save is pressed. It is not kept across rotation.
    var photo by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            preparing = true
            photoError = null
            onPreparePhoto(uri) { result ->
                preparing = false
                result.fold({ photo = PhotoChange.Set(it) }, { photoError = it.message ?: "That picture could not be used." })
            }
        }
    }
    val hasPhoto = when (photo) {
        is PhotoChange.Set -> true
        PhotoChange.Remove -> false
        PhotoChange.Keep -> person?.hasPhoto == true
    }
    var given by rememberSaveable { mutableStateOf(initial.given) }
    var surname by rememberSaveable { mutableStateOf(initial.surname) }
    var sex by rememberSaveable { mutableStateOf(initial.sex) }
    var born by rememberSaveable { mutableStateOf(initial.born?.toString() ?: "") }
    var bornMonth by rememberSaveable { mutableStateOf(initial.birthMonth?.toString() ?: "") }
    var bornDay by rememberSaveable { mutableStateOf(initial.birthDay?.toString() ?: "") }
    var died by rememberSaveable { mutableStateOf(initial.died?.toString() ?: "") }
    var diedMonth by rememberSaveable { mutableStateOf(initial.deathMonth?.toString() ?: "") }
    var diedDay by rememberSaveable { mutableStateOf(initial.deathDay?.toString() ?: "") }
    // Events and sources are kept as text so a half-finished list survives a screen rotation.
    var detailsJson by rememberSaveable { mutableStateOf(EventJson.encodeDetails(initialDetails)) }
    val details = remember(detailsJson) { EventJson.decodeDetails(detailsJson, editing = true) }
    fun setDetails(d: PersonDetails) { detailsJson = EventJson.encodeDetails(d) }
    // Which event is open in the editor: "" for none, "p:N" for the person's event N (-1 = new), "c:ID:N" for a couple's.
    var editingEvent by rememberSaveable { mutableStateOf("") }
    var birthPlace by rememberSaveable { mutableStateOf(initial.birthPlace) }
    var deathPlace by rememberSaveable { mutableStateOf(initial.deathPlace) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val draft = PersonDraft(
        given, surname, sex, born.toIntOrNull(), died.toIntOrNull(), birthPlace, deathPlace, notes,
        bornMonth.toIntOrNull(), bornDay.toIntOrNull(), diedMonth.toIntOrNull(), diedDay.toIntOrNull(),
    )
    val displayName = listOf(given, surname).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "this person" }
    val problem = when {
        !draft.hasName -> "Add a first name or a surname to save."
        !draft.datesAreReal -> "A birth or death date is not a real date. Check the day, month and year (a month needs a year, and a day needs a month)."
        !draft.datesMakeSense -> "The death date is before the birth date."
        else -> null
    }

    if (editingEvent.isNotEmpty()) {
        // One event is open: the editor takes the whole screen, and Save, Remove or Back return to the form.
        val parts = editingEvent.split(":")
        val forCouple = parts[0] == "c"
        val partnerId = if (forCouple) parts.getOrNull(1)?.toIntOrNull() ?: -1 else -1
        val index = parts.last().toIntOrNull() ?: -1
        val list = if (forCouple) details.coupleEvents[partnerId].orEmpty() else details.events
        fun store(updated: List<Event>) {
            setDetails(
                if (forCouple) details.copy(coupleEvents = details.coupleEvents + (partnerId to updated))
                else details.copy(events = updated),
            )
            editingEvent = ""
        }
        BackHandler { editingEvent = "" }
        EventEditorScreen(
            initial = list.getOrNull(index) ?: Event(if (forCouple) EventKind.Marriage else EventKind.Residence),
            forCouple = forCouple,
            heading = if (forCouple) "With ${spouses.firstOrNull { it.first == partnerId }?.second ?: "partner"}" else displayName,
            onSave = { e -> store(if (index in list.indices) list.mapIndexed { i, old -> if (i == index) e else old } else list + e) },
            onRemove = if (index in list.indices) ({ store(list.filterIndexed { i, _ -> i != index }) }) else null,
            onCancel = { editingEvent = "" },
        )
        return
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

            Row(Modifier.padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                when (val p = photo) {
                    is PhotoChange.Set -> AvatarPreview(p.photo.thumb, sex, 96.dp, ring = 3.dp)
                    PhotoChange.Remove -> AvatarPreview(null, sex, 96.dp, ring = 3.dp)
                    PhotoChange.Keep ->
                        if (person != null && person.hasPhoto) Avatar(person, 96.dp, ring = 3.dp) else AvatarPreview(null, sex, 96.dp, ring = 3.dp)
                }
                Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        if (hasPhoto) "Change photo" else "Add a photo",
                        selected = false,
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                    if (hasPhoto) Pill("Remove photo", selected = false, onClick = { photo = PhotoChange.Remove })
                }
            }
            if (preparing) {
                Text("Preparing the photo…", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            }
            photoError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            }

            Field("First names", given, { given = it }, Modifier.fillMaxWidth())
            Field("Surname", surname, { surname = it }, Modifier.fillMaxWidth().padding(top = 14.dp))

            Label("Sex", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Male", sex == Sex.Male, { sex = Sex.Male })
                Pill("Female", sex == Sex.Female, { sex = Sex.Female })
                Pill("Not stated", sex == Sex.Unknown, { sex = Sex.Unknown })
            }

            DateFields("Born (the year is enough)", bornDay, bornMonth, born, { d, m, y -> bornDay = d; bornMonth = m; born = y }, Modifier.padding(top = 14.dp))
            Field("Place of birth", birthPlace, { birthPlace = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            DateFields("Died (the year is enough)", diedDay, diedMonth, died, { d, m, y -> diedDay = d; diedMonth = m; died = y }, Modifier.padding(top = 14.dp))
            Field("Place of death", deathPlace, { deathPlace = it }, Modifier.fillMaxWidth().padding(top = 14.dp))
            Field("Notes", notes, { notes = it }, Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = false)

            Label("Events", Modifier.padding(top = 24.dp, bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.events.forEachIndexed { i, e -> EventRow(e, { editingEvent = "p:$i" }) }
                if (details.events.size < Limits.EVENTS) Pill("+ Add an event", false, { editingEvent = "p:-1" })
            }
            for ((partnerId, partnerName) in spouses) {
                Label("With $partnerName (marriage and the like)", Modifier.padding(top = 20.dp, bottom = 8.dp))
                val list = details.coupleEvents[partnerId].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEachIndexed { i, e -> EventRow(e, { editingEvent = "c:$partnerId:$i" }) }
                    if (list.size < Limits.EVENTS) Pill("+ Add marriage or divorce", false, { editingEvent = "c:$partnerId:-1" })
                }
            }
            Label("Sources for this person in general", Modifier.padding(top = 20.dp, bottom = 8.dp))
            SourcesEditor(details.sources, { setDetails(details.copy(sources = it)) })

            if (problem != null) {
                Text(
                    problem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Danger,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                )
            }
            PrimaryButton("Save", { onSave(draft, photo, details.copy(sources = details.sources.filter { !it.isBlank })) }, Modifier.fillMaxWidth().padding(top = 24.dp), enabled = problem == null && !preparing)
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
internal fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted, modifier = modifier.padding(start = 4.dp))
}

/** A small label above a filled rounded text box, as in the shared design. */
@Composable
internal fun Field(
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
