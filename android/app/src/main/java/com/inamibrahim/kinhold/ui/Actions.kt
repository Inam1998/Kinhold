package com.inamibrahim.kinhold.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.inamibrahim.kinhold.model.GeoPin
import com.inamibrahim.kinhold.model.PhoneNumber

/**
 * Hands a phone number or a place to the phone's own apps (dialler, messages, WhatsApp, maps). Kinhold only builds the
 * link: it asks for no permission and sends nothing itself. If no app can open it, a short message says so.
 */
object Actions {
    private fun launch(context: Context, intent: Intent, missing: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, missing, Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the dialler with the number filled in; the person still has to press call. */
    fun call(context: Context, phone: String) =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(PhoneNumber.dialable(phone)))), "No phone app found.")

    fun text(context: Context, phone: String) =
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(PhoneNumber.dialable(phone)))), "No messages app found.")

    /** WhatsApp needs the number with its country code; returns false if it has none. */
    fun whatsApp(context: Context, phone: String): Boolean {
        val digits = PhoneNumber.whatsApp(phone) ?: return false
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")), "No app found to open WhatsApp links.")
        return true
    }

    fun email(context: Context, address: String) =
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address.trim()))), "No email app found.")

    /** A location in the clipboard (coordinates or a long maps link), or null. Called only when the person asks for it or comes back from maps. */
    fun clipboardPin(context: Context): GeoPin? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return null
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
        return GeoPin.parse(text)
    }

    /** The text in the clipboard, or null. Read only when the person asks for it or comes back from maps. */
    fun clipboardText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return null
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    /** Shows the saved pin, or else searches the typed address, in the phone's maps app. */
    fun map(context: Context, label: String, address: String, pin: GeoPin?) {
        val uri = when {
            pin != null -> Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}(${Uri.encode(label)})")
            address.isNotBlank() -> Uri.parse("geo:0,0?q=" + Uri.encode(address))
            else -> Uri.parse("geo:0,0")
        }
        launch(context, Intent(Intent.ACTION_VIEW, uri), "No maps app found.")
    }
}

/** What reading a place gave: the pin, or a plain sentence saying why not. */
class PinResult(val pin: GeoPin?, val message: String?)

/**
 * Turns what a person pasted into a map pin without any map service key and without the app having internet permission: coordinates
 * and long Google Maps links are read directly, a full Plus Code is decoded, and a short Plus Code is placed near a known point
 * (a pin already saved in the tree, or a place the phone geocoder finds for the town written after the code or for the address).
 * The geocoder belongs to the phone (Google Play services); it needs the internet at that moment and may be missing on some phones.
 */
object PinFinder {
    /** The phone geocoder: the first point for a place name or address, or null (no geocoder, no connection, nothing found). */
    suspend fun geocode(context: Context, query: String): GeoPin? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (query.isBlank() || !android.location.Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(context, java.util.Locale.getDefault()).getFromLocationName(query.trim(), 1)?.firstOrNull()
                ?.let { GeoPin(it.latitude, it.longitude) }
        }.getOrNull()?.takeIf { it.isValid }
    }

    suspend fun resolve(context: Context, text: String, address: String, reference: GeoPin?): PinResult {
        GeoPin.parse(text)?.let { return PinResult(it, null) }
        val code = com.inamibrahim.kinhold.model.PlusCode.find(text)
        if (code != null) {
            if (!code.isShort) {
                com.inamibrahim.kinhold.model.PlusCode.decode(code.code)?.let { return PinResult(it, null) }
            } else {
                // A short code needs a point within about 50 km: a saved pin first, then the town written after the code, then the address.
                val refs = listOf(reference, if (code.locality.isNotBlank()) geocode(context, code.locality) else null, if (address.isNotBlank()) geocode(context, address) else null)
                for (r in refs.filterNotNull()) com.inamibrahim.kinhold.model.PlusCode.recover(code.code, r)?.let { return PinResult(it, null) }
                return PinResult(null, "That is a short Plus Code and Kinhold could not tell where it is. Type the town in the Address box and tap Find from address first, or copy the full code.")
            }
        }
        return PinResult(null, null)
    }
}

/**
 * Hands a file to the system share sheet, so it can be sent by WhatsApp, email, Bluetooth or saved to Drive in one step. Kinhold
 * writes the file to a private cache folder, gives the chosen app read access to that one file, and asks for no permission. The
 * file is deleted after half an hour (and at the next start), but whatever app received it keeps its own copy.
 */
object SendFile {
    private const val AUTHORITY = "com.inamibrahim.kinhold.files"
    private const val KEEP_MILLIS = 30L * 60_000L

    class Pending(val file: java.io.File, val uri: Uri, val mime: String, val title: String)

    /** A new empty file to write to, as a content link; the link is what the writers (the same ones that save to a chosen place) are given. */
    fun prepare(context: Context, fileName: String, mime: String, title: String): Pending {
        clean(context, KEEP_MILLIS)
        val dir = java.io.File(context.cacheDir, "outgoing").also { it.mkdirs() }
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val file = java.io.File(dir, safe)
        return Pending(file, androidx.core.content.FileProvider.getUriForFile(context, AUTHORITY, file), mime, title)
    }

    /** Opens the share sheet for a file that has been written. */
    fun send(context: Context, pending: Pending) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = pending.mime
            putExtra(Intent.EXTRA_STREAM, pending.uri)
            putExtra(Intent.EXTRA_SUBJECT, pending.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, Intent.createChooser(intent, pending.title), "No app found to send the file with.")
    }

    /** Deletes the files made for the share sheet that are older than [olderThanMillis]. */
    fun clean(context: Context, olderThanMillis: Long = 0L) {
        val now = System.currentTimeMillis()
        java.io.File(context.cacheDir, "outgoing").listFiles()?.forEach { if (now - it.lastModified() >= olderThanMillis) it.delete() }
    }

    private fun launch(context: Context, intent: Intent, missing: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, missing, Toast.LENGTH_SHORT).show()
        }
    }
}

/** A place for the system camera app to save one picture, and a way to clean it up. The app asks for no camera permission: the camera app takes the picture. */
object CameraShot {
    private const val AUTHORITY = "com.inamibrahim.kinhold.files"

    class Pending(val file: java.io.File, val uri: Uri)

    fun prepare(context: Context): Pending {
        val dir = java.io.File(context.cacheDir, "camera").also { it.mkdirs() }
        val file = java.io.File(dir, "shot-${System.nanoTime()}.jpg")
        return Pending(file, androidx.core.content.FileProvider.getUriForFile(context, AUTHORITY, file))
    }

    /** Removes every leftover camera file, once a picture has been read or the camera was cancelled. */
    fun clean(context: Context) {
        java.io.File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
    }
}
