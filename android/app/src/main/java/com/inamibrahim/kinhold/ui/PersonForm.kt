package com.inamibrahim.kinhold.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Delete
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
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.GeoPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import kotlinx.coroutines.launch
import com.inamibrahim.kinhold.model.LinkKind
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
    linkChoice: Boolean,
    onSave: (PersonDraft, PhotoChange, PersonDetails, LinkKind) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    referencePin: GeoPin? = null,
) {
    // A picked photo is only prepared here; it is stored when Save is pressed. It is not kept across rotation.
    var photo by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    // A new picture is shown in a crop step first; nothing is kept until Use this is pressed.
    var cropping by remember { mutableStateOf<com.inamibrahim.kinhold.data.PreparedPhoto?>(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            preparing = true
            photoError = null
            onPreparePhoto(uri) { result ->
                preparing = false
                result.fold({ cropping = it }, { photoError = it.message ?: "That picture could not be used." })
            }
        }
    }
    var shot by remember { mutableStateOf<CameraShot.Pending?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val pending = shot
        if (ok && pending != null) {
            preparing = true
            photoError = null
            onPreparePhoto(pending.uri) { result ->
                preparing = false
                result.fold({ cropping = it }, { photoError = it.message ?: "That picture could not be used." })
                CameraShot.clean(appContext)
            }
        } else {
            CameraShot.clean(appContext)
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
    // How sure each date is (about, before, after, between), kept as a short text so it survives rotation like the other boxes.
    var bornQual by rememberSaveable { mutableStateOf(com.inamibrahim.kinhold.model.DateExtra.encode(initial.birthQualifier, initial.birthEndYear)) }
    var diedQual by rememberSaveable { mutableStateOf(com.inamibrahim.kinhold.model.DateExtra.encode(initial.deathQualifier, initial.deathEndYear)) }
    var address by rememberSaveable { mutableStateOf(initial.address) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var pinText by rememberSaveable { mutableStateOf(if (initial.lat != null && initial.lng != null) GeoPin(initial.lat, initial.lng).text() else "") }
    val pin = if (pinText.isBlank()) null else GeoPin.parse(pinText)
    val context = androidx.compose.ui.platform.LocalContext.current
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
    var linkKindName by rememberSaveable { mutableStateOf(LinkKind.Birth.name) }
    val linkKind = LinkKind.valueOf(linkKindName)
    // Picking a pin in Google Maps: when the person comes back, a location they copied there is offered.
    var waitingForMaps by rememberSaveable { mutableStateOf(false) }
    var offered by remember { mutableStateOf<GeoPin?>(null) }
    var pasteNotice by remember { mutableStateOf(false) }
    var nothingCopied by remember { mutableStateOf(false) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Works out a pin from clipboard text (coordinates, a Maps link or a Plus Code); with [offer] it is shown for approval instead of being set.
    fun fromClipboard(offer: Boolean) {
        scope.launch {
            finding = true
            val text = Actions.clipboardText(context).orEmpty()
            val r = PinFinder.resolve(context, text, address, referencePin)
            finding = false
            pinMessage = r.message
            if (r.pin != null) {
                if (offer) offered = r.pin else pinText = r.pin.text()
                pasteNotice = false
                nothingCopied = false
            } else if (offer) nothingCopied = r.message == null else pasteNotice = r.message == null
        }
    }
    // A Plus Code typed or pasted into the pin box is turned into coordinates.
    androidx.compose.runtime.LaunchedEffect(pinText) {
        if (pinText.isNotBlank() && GeoPin.parse(pinText) == null && com.inamibrahim.kinhold.model.PlusCode.find(pinText) != null) {
            val r = PinFinder.resolve(context, pinText, address, referencePin)
            if (r.pin != null) { pinText = r.pin.text(); pinMessage = null } else pinMessage = r.message
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && waitingForMaps) {
                waitingForMaps = false
                fromClipboard(offer = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val draft = PersonDraft(
        given, surname, sex, born.toIntOrNull(), died.toIntOrNull(), birthPlace, deathPlace, notes,
        bornMonth.toIntOrNull(), bornDay.toIntOrNull(), diedMonth.toIntOrNull(), diedDay.toIntOrNull(),
        initial.uid, address, phone, pin?.lat, pin?.lng, email,
        birthQualifier = com.inamibrahim.kinhold.model.DateExtra.qualifier(bornQual), birthEndYear = com.inamibrahim.kinhold.model.DateExtra.endYear(bornQual).toIntOrNull(),
        deathQualifier = com.inamibrahim.kinhold.model.DateExtra.qualifier(diedQual), deathEndYear = com.inamibrahim.kinhold.model.DateExtra.endYear(diedQual).toIntOrNull(),
    )
    val displayName = listOf(given, surname).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "this person" }
    val problem = when {
        !draft.hasName -> "Add a first name or a surname to save."
        pinText.isNotBlank() && pin == null -> "The map pin is not a location. Paste two numbers such as 31.5204, 74.3587, or a long Google Maps link that contains them."
        !draft.datesAreReal -> "A birth or death date is not a real date. Check the day, month and year (a month needs a year, and a day needs a month)."
        !draft.datesMakeSense -> "The death date is before the birth date."
        else -> null
    }

    BackHandler(enabled = editingEvent.isNotEmpty()) { editingEvent = "" }

    // Opening an event slides the editor in; Save, Remove or Back slide the form back, scrolled where it was.
    AnimatedContent(
        targetState = editingEvent,
        modifier = Modifier.fillMaxSize().background(Palette.Background),
        transitionSpec = {
            val forward = targetState.isNotEmpty()
            val ease = FastOutSlowInEasing
            ContentTransform(
                slideInHorizontally(tween(280, easing = ease)) { if (forward) it else -it / 4 },
                slideOutHorizontally(tween(280, easing = ease)) { if (forward) -it / 4 else it },
                targetContentZIndex = if (forward) 1f else -1f,
                sizeTransform = null,
            )
        },
        label = "event",
    ) { editing ->
        if (editing.isNotEmpty()) {
            val parts = editing.split(":")
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
            EventEditorScreen(
                initial = list.getOrNull(index) ?: Event(if (forCouple) EventKind.Marriage else EventKind.Residence),
                forCouple = forCouple,
                heading = if (forCouple) "With ${spouses.firstOrNull { it.first == partnerId }?.second ?: "partner"}" else displayName,
                onSave = { e -> store(if (index in list.indices) list.mapIndexed { i, old -> if (i == index) e else old } else list + e) },
                onRemove = if (index in list.indices) ({ store(list.filterIndexed { i, _ -> i != index }) }) else null,
                onCancel = { editingEvent = "" },
            )
        } else {
            ScreenBackground {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .imePadding()
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ScreenHeader(
                        if (isEditing) "Person" else "New person", title, Modifier.padding(top = 12.dp, bottom = 6.dp),
                        trailing = {
                            DialogButton("Save", { onSave(draft, photo, details.copy(sources = details.sources.filter { !it.isBlank }, fields = details.fields.filter { !it.isBlank }), linkKind) }, kind = ButtonKind.Primary, enabled = problem == null && !preparing, icon = Icons.Filled.Check)
                        },
                    )

                    FormCard("Who they are", Icons.Filled.Person, Tones.Violet) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (val p = photo) {
                                is PhotoChange.Set -> AvatarPreview(p.photo.thumb, sex, 88.dp, ring = 3.dp)
                                PhotoChange.Remove -> AvatarPreview(null, sex, 88.dp, ring = 3.dp)
                                PhotoChange.Keep ->
                                    if (person != null && person.hasPhoto) Avatar(person, 88.dp, ring = 3.dp) else AvatarPreview(null, sex, 88.dp, ring = 3.dp)
                            }
                            Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconAction(AppIcons.Gallery, if (hasPhoto) "Choose a different photo from the gallery" else "Choose a photo from the gallery", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                                    IconAction(AppIcons.Camera, "Take a photo with the camera", { val p = CameraShot.prepare(appContext); shot = p; camera.launch(p.uri) })
                                    (photo as? PhotoChange.Set)?.let { set -> IconAction(AppIcons.Crop, "Move and zoom the photo", { cropping = set.photo }) }
                                    if (hasPhoto) IconAction(Icons.Filled.Delete, "Remove the photo", { photo = PhotoChange.Remove }, danger = true)
                                }
                            }
                        }
                        if (preparing) {
                            Text("Preparing the photo…", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, modifier = Modifier.padding(start = 4.dp))
                        }
                        cropping?.let { raw -> PhotoCropDialog(raw, { photo = PhotoChange.Set(it); cropping = null }, { cropping = null }) }
                        photoError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger, modifier = Modifier.padding(start = 4.dp))
                        }
                        Field("First names", given, { given = it }, Modifier.fillMaxWidth(), placeholder = "e.g. Ahmad Ali")
                        Field("Surname", surname, { surname = it }, Modifier.fillMaxWidth(), placeholder = "e.g. Khan")
                        Column {
                            Label("Sex", Modifier.padding(bottom = 8.dp))
                            EqualRow(3) {
                                Pill("Male", sex == Sex.Male, { sex = Sex.Male }, Modifier.weight(1f), icon = AppIcons.Male, small = true)
                                Pill("Female", sex == Sex.Female, { sex = Sex.Female }, Modifier.weight(1f), icon = AppIcons.Female, small = true)
                                Pill("Not stated", sex == Sex.Unknown, { sex = Sex.Unknown }, Modifier.weight(1f), icon = AppIcons.Unknown, small = true)
                            }
                        }
                    }

                    if (linkChoice) {
                        FormCard("How are they linked?", Icons.Filled.Favorite, Tones.Rose) {
                            Text("Birth is the usual choice. Adoptive, foster and step parents and children are also kept, and a person can have up to ${Family.MAX_PARENTS} parents.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                            EqualRow(2) {
                                for (k in LinkKind.entries) Pill(k.label.replace(" parent", ""), linkKind == k, { linkKindName = k.name }, Modifier.weight(1f))
                            }
                        }
                    }
                    FormCard("Life", Icons.Filled.DateRange, Tones.Amber) {
                        DateFields("Born", bornDay, bornMonth, born, bornQual, { d, m, y, q -> bornDay = d; bornMonth = m; born = y; bornQual = q })
                        PlaceField("Place of birth", birthPlace, { birthPlace = it }, Modifier.fillMaxWidth())
                        DateFields("Died", diedDay, diedMonth, died, diedQual, { d, m, y, q -> diedDay = d; diedMonth = m; died = y; diedQual = q })
                        PlaceField("Place of death", deathPlace, { deathPlace = it }, Modifier.fillMaxWidth())
                    }

                    FormCard("Contact", Icons.Filled.Call, Tones.Mint) {
                        PhoneField(phone, { phone = it }, Modifier.fillMaxWidth())
                        Field("Email", email, { email = it }, Modifier.fillMaxWidth(), KeyboardType.Email, placeholder = "name@example.com")
                        Field("Address", address, { address = it }, Modifier.fillMaxWidth(), singleLine = false, placeholder = "House, street, city")
                        Field("Map pin", pinText, { pinText = it }, Modifier.fillMaxWidth(), KeyboardType.Uri, placeholder = "31.5204, 74.3587 or a Plus Code")
                        offered?.let { found ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tones.Mint.copy(alpha = 0.16f)).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Use the location you copied (${found.text()})?", style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
                                DialogButton("Use it", { pinText = found.text(); offered = null }, kind = ButtonKind.Primary)
                                DialogButton("No", { offered = null }, kind = ButtonKind.Neutral)
                            }
                        }
                        EqualRow(2) {
                            DialogButton(
                                "Choose on map",
                                {
                                    waitingForMaps = true
                                    nothingCopied = false
                                    pasteNotice = false
                                    Actions.map(context, displayName, address, pin)
                                },
                                Modifier.weight(1f),
                                kind = ButtonKind.Tonal,
                                icon = Icons.Filled.LocationOn,
                            )
                            DialogButton(
                                "Paste",
                                { fromClipboard(offer = false) },
                                Modifier.weight(1f),
                                kind = ButtonKind.Neutral,
                                icon = Icons.Filled.Edit,
                            )
                        }
                        DialogButton(
                            if (finding) "Looking…" else "Find from address",
                            {
                                scope.launch {
                                    finding = true
                                    pinMessage = null
                                    val found = PinFinder.geocode(context, address)
                                    finding = false
                                    if (found != null) pinText = found.text() else pinMessage = if (address.isBlank()) "Type an address first." else "Your phone could not find that address (it needs the internet and a map service on the phone). Try adding the town, or use Choose on map."
                                }
                            },
                            Modifier.fillMaxWidth(),
                            kind = ButtonKind.Tonal,
                            enabled = !finding,
                            icon = Icons.Filled.Search,
                        )
                        pinMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger) }
                        if (pasteNotice) {
                            Text("The clipboard holds no location. Copy a Plus Code, coordinates or a full Google Maps link first.", style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
                        }
                        if (nothingCopied) {
                            Text("No location was copied. In your maps app press and hold the place, then tap the Plus Code under About (for example H88G+PF7) to copy it. Come back here and Kinhold offers it.", style = MaterialTheme.typography.bodySmall, color = Tones.Amber)
                        }
                        // The steps are shown only until a pin is set, so they do not crowd the card afterwards.
                        if (pin == null) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.SurfaceHigh).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("How to choose a place", style = MaterialTheme.typography.labelMedium, color = Palette.Text)
                                for ((n, step) in listOf("Tap Choose on map. It opens your maps app, at the address if you typed one.", "Press and hold the place.", "Tap the Plus Code under About (like H88G+PF7 Lahore). This copies it.", "Come back to Kinhold and tap Use it. Or just type the address and tap Find from address.").withIndex()) {
                                    Text("${n + 1}.  $step", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                                }
                            }
                        }
                    }

                    FormCard("Notes", Icons.Filled.Edit, Tones.Sky) {
                        Field("Notes", notes, { notes = it }, Modifier.fillMaxWidth(), singleLine = false, placeholder = "Anything worth remembering about them", labelHidden = true)
                    }

                    FormCard("Events", Icons.Filled.Star, Tones.Coral) {
                        details.events.forEachIndexed { i, e -> EventRow(e, { editingEvent = "p:$i" }) }
                        if (details.events.size < Limits.EVENTS) DialogButton("Add an event", { editingEvent = "p:-1" }, kind = ButtonKind.Tonal, icon = Icons.Filled.Add)
                        for ((partnerId, partnerName) in spouses) {
                            Label("With $partnerName (marriage and the like)", Modifier.padding(top = 8.dp))
                            val list = details.coupleEvents[partnerId].orEmpty()
                            list.forEachIndexed { i, e -> EventRow(e, { editingEvent = "c:$partnerId:$i" }) }
                            if (list.size < Limits.EVENTS) DialogButton("Add marriage or divorce", { editingEvent = "c:$partnerId:-1" }, kind = ButtonKind.Tonal, icon = Icons.Filled.Favorite)
                        }
                    }

                    FormCard("Your own facts", Icons.Filled.Info, Tones.Teal) {
                        FieldsEditor(details.fields, { setDetails(details.copy(fields = it)) })
                    }

                    FormCard("Sources for this person", androidx.compose.material.icons.Icons.AutoMirrored.Filled.List, Tones.Rose) {
                        SourcesEditor(details.sources, { setDetails(details.copy(sources = it)) })
                    }

                    if (problem != null) {
                        Text(problem, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger, modifier = Modifier.padding(start = 4.dp))
                    }
                    FormActions(
                        "Save",
                        { onSave(draft, photo, details.copy(sources = details.sources.filter { !it.isBlank }, fields = details.fields.filter { !it.isBlank }), linkKind) },
                        problem == null && !preparing,
                        onCancel,
                        "Delete this person",
                        if (onDelete != null) ({ confirmDelete = true }) else null,
                    )
                    Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
                }
            }
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
                DialogButtons {
                    DialogButton("Keep", { confirmDelete = false }, kind = ButtonKind.Neutral)
                    DialogButton("Delete", { confirmDelete = false; onDelete() }, kind = ButtonKind.Danger)
                }
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
    hidden: Boolean = false,
    placeholder: String = "",
    labelHidden: Boolean = false,
) {
    Column(modifier) {
        if (!labelHidden) Label(label, Modifier.padding(bottom = 6.dp))
        BasicTextField(
            visualTransformation = if (hidden) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = TextStyle(fontFamily = AppFont, fontSize = 16.sp, color = Palette.Text, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
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
                ) {
                    // A hint in the empty box says what goes there, so a blank field is never a mystery.
                    if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted.copy(alpha = 0.65f))
                    inner()
                }
            },
        )
    }
}

/** One section of the person form: a card with a tinted icon and a title, holding its fields, so the fields stand out from the background. */
@Composable
private fun FormCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(icon, tone, 36.dp)
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
            }
            content()
        }
    }
}
