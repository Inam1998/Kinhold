package com.inamibrahim.kinhold.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.inamibrahim.kinhold.model.AutoBackupPolicy
import java.io.File
import java.io.IOException

/** Automatic backups kept inside the app's own storage. An uninstall deletes them, so they guard against mistakes, not against losing the app. */
class AutoBackupStore(private val dir: File) {
    /** Writes [text] as [name] by way of a temporary file, so a crash never leaves a half-written backup. */
    fun write(name: String, text: String) {
        dir.mkdirs()
        val temp = File(dir, "$name.tmp")
        temp.writeText(text)
        val target = File(dir, name)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Could not save the automatic backup")
        }
    }

    /** The automatic backup files, newest first. */
    fun list(): List<File> =
        dir.listFiles { f -> AutoBackupPolicy.isAutoBackup(f.name) }.orEmpty().sortedByDescending { AutoBackupPolicy.timeOf(it.name) }

    fun latest(): File? = list().firstOrNull()

    fun prune() {
        val names = list().map { it.name }
        for (n in AutoBackupPolicy.toDelete(names)) File(dir, n).delete()
        dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }
}

/**
 * Automatic backups in a folder the person chose once with the system folder picker. The app keeps permission for that
 * one folder only; it asks for no storage permission. Because the folder is outside the app, its files survive an uninstall.
 */
object ExternalBackupFolder {
    private fun root(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** The folder's name as shown to the person, or null if it can no longer be read. */
    fun displayName(context: Context, tree: Uri): String? = runCatching {
        context.contentResolver.query(root(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /** Writes the backup, then removes old automatic backups there. Throws if the folder cannot be written. */
    fun write(context: Context, tree: Uri, name: String, text: String) {
        val resolver = context.contentResolver
        val doc = DocumentsContract.createDocument(resolver, root(tree), "application/json", name)
            ?: throw IOException("Could not create a file in the backup folder")
        try {
            resolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("Could not write to the backup folder")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        prune(context, tree)
    }

    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val byName = HashMap<String, String>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { while (it.moveToNext()) byName[it.getString(1)] = it.getString(0) }
        for (n in AutoBackupPolicy.toDelete(byName.keys.toList())) {
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, byName.getValue(n))) }
        }
    }
}
