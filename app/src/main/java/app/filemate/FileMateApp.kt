package app.filemate

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class MonitorState(val running: Boolean = false, val starting: Boolean = false,
    val readyRequest: String? = null, val error: String? = null, val watchedFolders: Int = 0,
    val usageAvailable: Boolean = false)

class FileMateApp : Application() {
    lateinit var store: Store; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val revision = MutableStateFlow(0L)
    val monitor = MutableStateFlow(MonitorState())
    val checking = MutableStateFlow(false)
    val cleanup = MutableStateFlow(CleanupProgress())
    val gallery = MutableStateFlow(GalleryProgress())
    private val galleryLock = Mutex()
    private val scanLock = Mutex()
    override fun onCreate() {
        super.onCreate(); store = Store(this)
        scope.launch {
            FileOrganiser(store).recoverPending()
            if(store.state("session_active") == "true") {
                store.history("Previous session interrupted", "Android ended the previous process. Reopening FileMate checks for missed files.")
                store.state("session_active","false"); changed()
            }
        }
    }
    fun changed() { revision.update { it + 1 } }
    fun refreshGallery() {
        gallery.value = GalleryProgress(running = true)
        scope.launch {
            galleryLock.withLock {
                gallery.value = GalleryProgress(running = true)
                try {
                    val access = MediaAccess.read(this@FileMateApp)
                    if(!access.any) {
                        store.hideUnavailableMedia()
                        gallery.value = GalleryProgress(message = "Choose photo and video access to browse Gallery. Your saved project assignments are kept.")
                    } else {
                        val items = GalleryScanner(this@FileMateApp).scan(access)
                        store.saveMediaSnapshot(items)
                        gallery.value = GalleryProgress(ready = true)
                    }
                } catch(e: Exception) {
                    store.hideUnavailableMedia()
                    gallery.value = GalleryProgress(message = e.message ?: "Gallery could not refresh. Your saved assignments are kept.")
                } finally { changed() }
            }
        }
    }

    // First proof: these locations only. Gallery and user-selected roots follow in later milestones.
    @Suppress("DEPRECATION")
    fun roots(): List<File> = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    )
    @Suppress("DEPRECATION")
    fun cleanupRoots(): List<Pair<String,File>> = listOf(
        "Downloads" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Documents" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "Camera" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        "Pictures" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "Movies" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        "Music" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    ).distinctBy { runCatching { it.second.canonicalPath }.getOrDefault(it.second.absolutePath) }

    fun scanPhone() {
        if(cleanup.value.running) return
        scope.launch {
            if(!Environment.isExternalStorageManager()) {
                cleanup.value = CleanupProgress(error = "Allow file access before scanning shared folders.")
                return@launch
            }
            cleanup.value = CleanupProgress(running = true,stage = "Preparing scan")
            try {
                val scanner = CleanupScanner(contentResolver)
                val result = scanner.scan(cleanupRoots(),store.selectedFolders(),store.assignedPaths(),store.knownAiPaths()) {
                    cleanup.value = it
                }
                val finishedStage = cleanup.value.stage.ifBlank { "Scan complete" }
                store.saveCleanupScan(result.first,result.second)
                cleanup.value = CleanupProgress(stage = finishedStage,files = result.second.totalFiles)
                changed()
            } catch(e: Exception) {
                cleanup.value = CleanupProgress(error = e.message ?: "The scan couldn't finish. No files were changed.")
            }
        }
    }

    fun addSelectedFolder(uri: Uri) {
        scope.launch {
            val name = runCatching {
                contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                    if(it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast(':') ?: "Selected folder"
            store.addSelectedFolder(SelectedFolder(uri.toString(),name))
            changed()
        }
    }
    suspend fun reconcile() = scanLock.withLock {
        if (!Environment.isExternalStorageManager()) return@withLock
        checking.value = true
        try {
            var found = 0;var indexed = 0
            val errors = mutableListOf<String>()
            for(root in roots()) {
                if(!root.exists()) continue
                val key = "baseline:${root.absolutePath}"
                val baseline = store.state(key) == null
                var complete = true
                walkFiles(root, { complete = false; errors.add(it.name) }) { file ->
                    if(!FileRules.temporary(file.name)) {
                        indexed++
                        // Only use names in catch-up: past source activity cannot be reconstructed reliably.
                        if(store.observe(file,FileRules.classify(file.name,null,System.currentTimeMillis()),"Catch-up",baseline)) found++
                    }
                }
                if(complete) store.state(key,System.currentTimeMillis().toString())
            }
            if(errors.isEmpty()) {
                store.state("last_check",System.currentTimeMillis().toString())
                store.history("Catch-up complete", "$found likely AI files found. $indexed accessible files checked in Downloads and Documents. Existing files are indexed on first access.")
            } else store.history("Catch-up incomplete", "Some folders could not be read: ${errors.distinct().joinToString()}. They will be retried next time.")
        } catch(e: Exception) { store.history("Catch-up needs another try",e.message ?: "Storage is unavailable") }
        finally { checking.value = false; changed() }
    }
}

/** Do not follow symlinks outside a granted shared root. Report unreadable folders, never silent success. */
fun walkFiles(root: File, onError: (File) -> Unit, visit: (File) -> Unit) {
    val boundary = root.canonicalPath
    val pending = ArrayDeque<File>(); pending.add(root)
    val seen = mutableSetOf<String>()
    while(pending.isNotEmpty()) {
        val dir = pending.removeFirst()
        val canonical = try { dir.canonicalPath } catch(_: Exception) { onError(dir);continue }
        if(canonical != boundary && !canonical.startsWith("$boundary/")) continue
        if(!seen.add(canonical)) continue
        val children = dir.listFiles() ?: run { onError(dir);continue }
        for(file in children) {
            if(file.name.startsWith('.')) continue
            if(file.isDirectory) pending.add(file)
            else if(file.isFile && file.canonicalPath.startsWith("$boundary/")) visit(file)
        }
    }
}
