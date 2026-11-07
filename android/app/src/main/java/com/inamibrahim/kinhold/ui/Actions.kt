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
