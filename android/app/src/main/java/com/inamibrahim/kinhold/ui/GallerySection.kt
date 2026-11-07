package com.inamibrahim.kinhold.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inamibrahim.kinhold.data.GalleryItem
import com.inamibrahim.kinhold.data.KinholdViewModel

/** The extra photos of one person: a grid of small pictures, a viewer, and buttons to add one from the gallery or the camera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GallerySection(vm: KinholdViewModel, personId: Int) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val items by produceState<List<GalleryItem>>(emptyList(), personId, refresh) { value = vm.gallery(personId) }
    var viewing by remember { mutableStateOf<GalleryItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var shot by remember { mutableStateOf<CameraShot.Pending?>(null) }

    fun add(uri: Uri) = vm.addGalleryPhoto(personId, uri) { r ->
        notice = r.exceptionOrNull()?.let { it.message ?: "That picture could not be used." }
        refresh++
        CameraShot.clean(context)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) add(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> val p = shot; if (ok && p != null) add(p.uri) else CameraShot.clean(context) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in items) {
                    val bitmap = remember(item.id) { BitmapFactory.decodeByteArray(item.thumb, 0, item.thumb.size)?.asImageBitmap() }
                    if (bitmap != null) {
                        Image(bitmap, null, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).clickable { viewing = item }, contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        EqualRow(2) {
            DialogButton("Add a photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), kind = ButtonKind.Tonal, icon = Icons.Filled.Add)
            DialogButton("Take a photo", { val p = CameraShot.prepare(context); shot = p; camera.launch(p.uri) }, Modifier.weight(1f), kind = ButtonKind.Neutral)
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Danger) }
    }

    viewing?.let { item ->
        val full by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.id) {
            value = vm.galleryPhoto(item.id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        AlertDialog(
            onDismissRequest = { viewing = null },
            containerColor = Palette.Surface,
            shape = RoundedCornerShape(28.dp),
            text = {
                val img = full
                if (img != null) Image(img, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                else Text("Opening the photo…", color = Palette.TextMuted)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton("Remove", { vm.removeGalleryPhoto(item.id) { refresh++ }; viewing = null }, kind = ButtonKind.Danger, icon = Icons.Filled.Delete)
                    DialogButton("Close", { viewing = null }, kind = ButtonKind.Neutral)
                }
            },
        )
    }
}
