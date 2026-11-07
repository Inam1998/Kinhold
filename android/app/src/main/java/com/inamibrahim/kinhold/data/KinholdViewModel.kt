package com.inamibrahim.kinhold.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.GedcomException
import com.inamibrahim.kinhold.model.GedcomResult
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Holds the stored tree for the screens and runs changes off the main thread. */
class KinholdViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository(app)
    private val prefs = app.getSharedPreferences("kinhold", Application.MODE_PRIVATE)

    val family: StateFlow<Family> = repo.family

    /** False until the stored tree has been read, so the first screen is not wrongly shown as empty. */
    var loaded by mutableStateOf(false)
        private set

    /** When a backup file was last saved, in milliseconds, or null if never. */
    var lastBackup by mutableStateOf(prefs.getLong(LAST_BACKUP, 0L).takeIf { it > 0 })
        private set

    /** Whether the tree from before the last restore is still kept, so the restore can be undone. */
    var hasSafetyCopy by mutableStateOf(repo.hasSafetyCopy())
        private set

    init {
        viewModelScope.launch {
            repo.load()
            loaded = true
        }
    }

    fun add(draft: PersonDraft, relation: Relation?, photo: PreparedPhoto?, details: PersonDetails, onAdded: (Int) -> Unit) {
        viewModelScope.launch { onAdded(repo.addPerson(draft, relation, photo, details)) }
    }

    fun update(id: Int, draft: PersonDraft, photo: PhotoChange, details: PersonDetails, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updatePerson(id, draft, photo, details)
            onDone()
        }
    }

    /** A person's events, sources and couple events, read when a screen needs them (never all at once). */
    suspend fun details(id: Int): PersonDetails = repo.details(id)

    /** The small square picture of a person, for the tree and the lists. Null if they have no photo. */
    suspend fun thumb(id: Int): ByteArray? = repo.thumb(id)

    /** Reads and shrinks a picture the person chose. Nothing is stored until the form is saved. */
    fun preparePhoto(uri: Uri, onResult: (Result<PreparedPhoto>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { PhotoCodec.prepare(getApplication<Application>().contentResolver, uri) }
            }
            onResult(result)
        }
    }

    /** Deletes a person; [onDeleted] gets what is needed to undo it. */
    fun delete(id: Int, onDeleted: (DeletedPerson?) -> Unit) {
        viewModelScope.launch { onDeleted(repo.deletePerson(id)) }
    }

    fun undoDelete(deleted: DeletedPerson) {
        viewModelScope.launch { repo.restoreDeleted(deleted) }
    }

    fun addSampleFamily(count: Int) {
        viewModelScope.launch { repo.addSampleFamily(count) }
    }

    /** Writes the whole tree to the file the person chose. [onResult] gets the number of people saved, or the error. */
    fun saveBackup(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val text = repo.exportBackup()
                val count = repo.family.value.size
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the file to write to.")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                prefs.edit { putLong(LAST_BACKUP, System.currentTimeMillis()) }
                lastBackup = System.currentTimeMillis()
                count
            }
            onResult(result)
        }
    }

    /** Reads and checks a backup file without changing anything. The result is the data, or why it cannot be used. */
    fun readBackup(uri: Uri, onResult: (Result<BackupData>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open that file.")
                    val bytes = input.use { it.readAtMost(MAX_BACKUP_BYTES) }
                        ?: throw BackupException("This file is too large to be a Kinhold backup.")
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
            }
            onResult(result)
        }
    }

    /** Writes the whole tree as a GEDCOM file to the place the person chose. [onResult] gets the people saved. */
    fun exportGedcom(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val app = getApplication<Application>()
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
                val today = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(LocalDate.now()).uppercase(Locale.ENGLISH)
                val text = repo.exportGedcom(version, today)
                val count = repo.family.value.size
                withContext(Dispatchers.IO) {
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the file to write to.")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                count
            }
            onResult(result)
        }
    }

    /** Reads and checks a GEDCOM file without changing anything. The result is what it holds and what cannot be kept. */
    fun readGedcom(uri: Uri, onResult: (Result<GedcomResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open that file.")
                    val bytes = input.use { it.readAtMost(MAX_BACKUP_BYTES) }
                        ?: throw GedcomException("This file is too large to be a family tree.")
                    Gedcom.read(bytes)
                }
            }
            onResult(result)
        }
    }

    /** Puts a GEDCOM file that [readGedcom] already checked into the tree: replacing it, or adding to it. */
    fun importGedcom(data: BackupData, replace: Boolean, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { if (replace) repo.replaceAll(data) else repo.addAll(data) }
            hasSafetyCopy = repo.hasSafetyCopy()
            onResult(result)
        }
    }

    /** Replaces the tree with a backup that [readBackup] already checked. */
    fun restore(data: BackupData, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.replaceAll(data) }
            hasSafetyCopy = repo.hasSafetyCopy()
            onResult(result)
        }
    }

    fun undoRestore(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.undoRestore() }
            hasSafetyCopy = repo.hasSafetyCopy()
            onResult(result)
        }
    }

    private companion object {
        const val LAST_BACKUP = "last_backup"
        const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
    }
}

/** Everything in the stream, or null as soon as it turns out to be longer than [limit] bytes. */
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
