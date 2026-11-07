package com.inamibrahim.kinhold.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inamibrahim.kinhold.model.AutoBackupPolicy
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.MergeResult
import com.inamibrahim.kinhold.model.ShareScope
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.GedcomException
import com.inamibrahim.kinhold.model.GedcomResult
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** When a backup file was last saved by hand, in milliseconds, or null if never. */
    private var lastManualBackup by mutableStateOf(prefs.getLong(LAST_BACKUP, 0L).takeIf { it > 0 })

    /** When the last automatic backup was written, or null if none yet. */
    var lastAutoBackup by mutableStateOf(prefs.getLong(LAST_AUTO, 0L).takeIf { it > 0 })
        private set

    /** The latest backup of either kind, for the "backed up" line on the tree screen. */
    val lastBackup: Long? get() = listOfNotNull(lastManualBackup, lastAutoBackup).maxOrNull()

    /** The folder chosen for automatic backups outside the app, its name, and the last problem writing a backup (or null). */
    var autoFolderUri by mutableStateOf(prefs.getString(AUTO_FOLDER, null)?.let(Uri::parse))
        private set
    var autoFolderName by mutableStateOf<String?>(null)
        private set
    var autoError by mutableStateOf<String?>(null)
        private set

    private val autoStore = AutoBackupStore(java.io.File(app.filesDir, "auto-backups"))
    private val autoLock = Mutex()
    private var lastSavedSnapshot: Family? = null

    /** Whether the tree from before the last restore is still kept, so the restore can be undone. */
    var hasSafetyCopy by mutableStateOf(repo.hasSafetyCopy())
        private set

    init {
        viewModelScope.launch {
            repo.load()
            loaded = true
            refreshAutoFolderName()
            // A tree that changed gets an automatic copy shortly after, and one is made at start if the last is a day old.
            if (repo.family.value.size > 0 && (lastAutoBackup == null || System.currentTimeMillis() - lastAutoBackup!! > 86_400_000L)) runAutoBackup(force = true)
        }
        viewModelScope.launch {
            repo.family.drop(1).debounce(45_000).collect { runAutoBackup(force = false) }
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

    fun setMe(id: Int?) {
        viewModelScope.launch { repo.setMe(id) }
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
                lastManualBackup = System.currentTimeMillis()
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

    /** A file waiting to be merged, with the comparison against the tree. Kept here so it survives a screen rotation. */
    var mergeSession by mutableStateOf<MergeSession?>(null)
        private set

    /** Writes a share file for the branch from [rootId] to the place the person chose. [onResult] gets the number of people. */
    fun exportShare(uri: Uri, rootId: Int, scope: ShareScope, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val (text, count) = repo.exportShare(rootId, scope, LocalDate.now().year)
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the file to write to.")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                count
            }
            onResult(result)
        }
    }

    /** Reads a share file, a backup or a GEDCOM file and compares it with the tree; nothing is changed. */
    fun startMerge(uri: Uri, onResult: (Result<MergeSession>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val data = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open that file.")
                    val bytes = input.use { it.readAtMost(MAX_BACKUP_BYTES) }
                        ?: throw BackupException("This file is too large to be a family tree.")
                    val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() && it.toInt() != 0xEF && it.toInt() != 0xBB && it.toInt() != 0xBF }
                    if (first?.toInt()?.toChar() == '{') BackupCodec.decode(bytes.toString(Charsets.UTF_8)) else Gedcom.read(bytes).data
                }
                MergeSession(data, repo.planMerge(data)).also { mergeSession = it }
            }
            onResult(result)
        }
    }

    fun cancelMerge() {
        mergeSession = null
    }

    /** Applies the choices made on the merge screen. [onResult] gets what was done. */
    fun applyMerge(session: MergeSession, accepted: Set<Int>, useHis: Set<Pair<Int, MergeField>>, addEvents: Boolean, onResult: (Result<MergeResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.applyMerge(session.data, accepted, useHis, addEvents) }
            hasSafetyCopy = repo.hasSafetyCopy()
            onResult(result)
        }
    }

    // ---------------------------------------------------------------- automatic backup

    private suspend fun refreshAutoFolderName() {
        val tree = autoFolderUri ?: run { autoFolderName = null; return }
        autoFolderName = withContext(Dispatchers.IO) { ExternalBackupFolder.displayName(getApplication(), tree) }
    }

    /**
     * Writes an automatic backup if one is due: always inside the app, and also to the chosen folder. The text is decoded
     * again before it is saved, so a backup that could not be restored is never kept. [onlyIfChanged] skips the write when
     * nothing has changed since the last one (used when the app goes to the background).
     */
    private suspend fun runAutoBackup(force: Boolean, onlyIfChanged: Boolean = false) = autoLock.withLock {
        val snapshot = repo.family.value
        if (snapshot.size == 0) return@withLock
        val changed = snapshot !== lastSavedSnapshot
        if (onlyIfChanged && !changed) return@withLock
        val now = System.currentTimeMillis()
        if (!AutoBackupPolicy.isDue(lastAutoBackup, now, changed, force)) return@withLock
        val app = getApplication<Application>()
        try {
            val name = AutoBackupPolicy.fileName(java.time.LocalDateTime.now())
            val text = withContext(Dispatchers.IO) {
                val text = repo.exportBackup()
                BackupCodec.decode(text)
                autoStore.write(name, text)
                autoStore.prune()
                text
            }
            lastSavedSnapshot = snapshot
            lastAutoBackup = now
            prefs.edit { putLong(LAST_AUTO, now) }
            autoError = null
            autoFolderUri?.let { tree ->
                try {
                    withContext(Dispatchers.IO) { ExternalBackupFolder.write(app, tree, name, text) }
                } catch (e: Exception) {
                    autoError = "The backup folder could not be written to (${e.message ?: "unknown problem"}). Choose it again."
                }
            }
        } catch (e: Exception) {
            autoError = "The automatic backup failed: ${e.message ?: "unknown problem"}."
        }
    }

    /** Backs up now, whatever the time since the last one. */
    fun autoBackupNow() {
        viewModelScope.launch { runAutoBackup(force = true) }
    }

    /** Called when the app leaves the screen: saves a copy if anything changed since the last one. */
    fun autoBackupIfChanged() {
        viewModelScope.launch { runAutoBackup(force = true, onlyIfChanged = true) }
    }

    private val folderFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Keeps [uri] as the folder for automatic backups, with permission that lasts, and backs up to it at once. */
    fun setAutoFolder(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            runCatching { resolver.takePersistableUriPermission(uri, folderFlags) }
            autoFolderUri?.takeIf { it != uri }?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, folderFlags) } }
            prefs.edit { putString(AUTO_FOLDER, uri.toString()) }
            autoFolderUri = uri
            refreshAutoFolderName()
            runAutoBackup(force = true)
        }
    }

    fun clearAutoFolder() {
        val resolver = getApplication<Application>().contentResolver
        autoFolderUri?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, folderFlags) } }
        prefs.edit { remove(AUTO_FOLDER) }
        autoFolderUri = null
        autoFolderName = null
    }

    /** The newest automatic backup kept inside the app, read and checked, with when it was made; null if there is none. */
    fun latestAutoCopy(onResult: (Result<Pair<BackupData, java.time.LocalDateTime>?>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = autoStore.latest() ?: return@withContext null
                    BackupCodec.decode(file.readText()) to (AutoBackupPolicy.timeOf(file.name) ?: java.time.LocalDateTime.now())
                }
            }
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
        const val LAST_AUTO = "last_auto"
        const val AUTO_FOLDER = "auto_folder"
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

/** A file read for merging and its comparison with the tree on this phone. */
class MergeSession(val data: BackupData, val plan: Merge)
