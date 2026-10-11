package app.filemate

import android.os.Environment
import java.io.File

/** Only the stable, live event path can invoke physical automation. Catch-up merely records. */
class AutoSorter(private val store: Store) {
    @Suppress("DEPRECATION")
    fun observeLive(file: File, context: AiContext?, now: Long, freshLiveEvent: Boolean, newAtFirstEvent: Boolean): OrganiseResult? =
        synchronized(FileOrganiser.actionLock) { synchronized(store) observed@ {
            val finding = store.classifyFile(file,context,now)
            store.observe(file,finding,"Live monitoring")
            val untouched = store.eligibleAfterLiveEvent(file.absolutePath)
            val match = store.learnedProject(file.name)
            val source = runCatching { file.canonicalFile }.getOrNull() ?: return@observed null
            // Refuse aliases: an automatic move must act on the exact path that was observed.
            if(source.absolutePath != file.absolutePath) return@observed null
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).canonicalPath
            val root = when {
                source.path.startsWith("$downloads/") -> "Downloads"
                source.path.startsWith("$documents/") -> "Documents"
                else -> "Unsupported"
            }
            val managed = source.path.startsWith("$documents/FileMate/")
            if(!AutomaticOrganisationPolicy.permits(freshLiveEvent,newAtFirstEvent && untouched,finding,match,root,managed)) return@observed null
            val project = store.projects().firstOrNull { it.id == match?.projectId } ?: return@observed null
            val fingerprint = ContentFingerprint.read(source) ?: return@observed null
            val entry = CleanupEntry(source.path,source.name,fingerprint.size,fingerprint.modified,root,0,fingerprint.hash)
            val organiser = FileOrganiser(store)
            val plan = organiser.plans(listOf(entry),project,tidyNames=false).single().copy(automatic=true)
            organiser.apply(listOf(plan))
        } }
}
