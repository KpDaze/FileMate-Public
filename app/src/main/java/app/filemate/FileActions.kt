package app.filemate

import android.os.Environment
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OrganisePlan(
    val sourcePath: String,
    val targetPath: String,
    val sourceName: String,
    val targetName: String,
    val sourceRoot: String,
    val expectedSize: Long,
    val expectedModified: Long,
    val hash: String?,
    val projectId: Long,
    val projectName: String,
    val supported: Boolean,
    val note: String
)

data class FileActionRecord(
    val id: Long,
    val sourcePath: String,
    val targetPath: String,
    val sourceName: String,
    val targetName: String,
    val sourceRoot: String,
    val expectedSize: Long,
    val expectedModified: Long,
    val hash: String?,
    val projectId: Long?,
    val projectName: String,
    val previousProjectId: Long?,
    val previousProjectConfidence: String,
    val created: Long,
    val status: String,
    val error: String?
)

data class OrganiseResult(val applied: Int, val skipped: Int, val failed: Int, val messages: List<String>)

object FileNaming {
    private val unsafe = Regex("[^\\p{L}\\p{N}._ -]+")
    private val spaces = Regex("[ _-]+")
    fun folder(raw: String): String = safe(raw,60).ifBlank { "Project" }
    fun tidy(project: String, original: String, modified: Long): String {
        val extension = original.substringAfterLast('.',"").takeIf { it.isNotEmpty() && original.contains('.') }
        val stem = if(extension == null) original else original.dropLast(extension.length + 1)
        val projectPart = safe(project,50).replace(' ','_')
        val description = safe(stem,75).replace(' ','_').ifBlank { "File" }
        val date = SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Date(modified.takeIf { it > 0 } ?: System.currentTimeMillis()))
        return "$projectPart" + "_${description}_$date" + (extension?.let { ".${safe(it,12)}" } ?: "")
    }
    private fun safe(raw: String, limit: Int): String = unsafe.replace(raw,"_").trim().let { spaces.replace(it," ") }.trim('.',' ').take(limit)
}

class FileOrganiser(private val store: Store, private val transfer: VerifiedFileTransfer = VerifiedFileTransfer()) {
    // UI requests and startup recovery use separate instances; serialise journal transitions.
    companion object { private val actionLock = Any() }
    @Suppress("DEPRECATION")
    fun plans(entries: List<CleanupEntry>, project: Project, tidyNames: Boolean): List<OrganisePlan> {
        val sharedRoot = Environment.getExternalStorageDirectory().canonicalFile
        val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"FileMate/${FileNaming.folder(project.name)}")
        val reserved = mutableSetOf<String>()
        return entries.distinctBy { it.path }.map { entry ->
            val source = if(entry.path.startsWith('/')) runCatching { File(entry.path).canonicalFile }.getOrNull() else null
            val insideShared = source != null && source.path.startsWith("${sharedRoot.path}/")
            val requestedName = if(tidyNames) FileNaming.tidy(project.name,entry.name,entry.modified) else entry.name
            val target = uniqueTarget(destination,requestedName,reserved)
            val same = source?.path == target.path
            val fingerprint = if(insideShared && source?.isFile == true && !same) ContentFingerprint.read(source) else null
            val unchanged = fingerprint != null && fingerprint.size == entry.size &&
                (entry.modified <= 0 || fingerprint.modified == entry.modified) &&
                (entry.hash == null || fingerprint.hash == entry.hash)
            val supported = insideShared && source?.isFile == true && !same && unchanged
            val note = when {
                !entry.path.startsWith('/') -> "This selected-folder provider supports project assignment here, but physical moves are not enabled."
                !insideShared -> "The source is outside Android shared storage."
                source?.isFile != true -> "The file is no longer available."
                same -> "Already organised with this name."
                !unchanged -> "This file changed or could not be fingerprinted. Scan again before moving it."
                entry.root == "Camera" || entry.root == "Pictures" -> "Personal image selected deliberately; FileMate will not choose it automatically."
                else -> if(tidyNames) "Move and use the reviewed tidy name." else "Move and keep the current filename."
            }
            OrganisePlan(entry.path,target.path,entry.name,target.name,entry.root,entry.size,entry.modified,fingerprint?.hash,
                project.id,project.name,supported,note)
        }
    }

    fun apply(plans: List<OrganisePlan>): OrganiseResult = synchronized(actionLock) {
        var applied = 0
        var skipped = 0
        var failed = 0
        val messages = mutableListOf<String>()
        plans.forEach { plan ->
            if(!plan.supported) { skipped++;messages += "${plan.sourceName}: ${plan.note}";return@forEach }
            val source = File(plan.sourcePath)
            val target = File(plan.targetPath)
            if(!source.isFile || source.length() != plan.expectedSize || (plan.expectedModified > 0 && source.lastModified() != plan.expectedModified)) {
                failed++;messages += "${plan.sourceName}: changed since the scan; scan again.";return@forEach
            }
            if(!ContentFingerprint.matches(source,plan.expectedSize,plan.hash)) {
                failed++;messages += "${plan.sourceName}: content changed or its original fingerprint is unavailable; preview again.";return@forEach
            }
            if(target.exists()) { failed++;messages += "${plan.targetName}: a file now uses this name; preview again.";return@forEach }
            if(target.parentFile?.mkdirs() == false && target.parentFile?.isDirectory != true) {
                failed++;messages += "${plan.sourceName}: destination folder couldn't be created.";return@forEach
            }
            val actionId = store.beginFileAction(plan)
            try {
                transfer.move(source,target,plan.expectedSize,plan.hash)
                if(!target.isFile || source.exists()) throw IllegalStateException("Android did not finish the move")
                store.completeFileAction(actionId,target.length(),target.lastModified())
                applied++
            } catch(e: Exception) {
                if(e is FileAlreadyExistsException || (source.isFile && !target.exists())) store.failFileAction(actionId,e.message ?: "Move failed")
                else store.reviewFileAction(actionId,e.message ?: "Move needs review")
                failed++;messages += "${plan.sourceName}: ${e.message ?: "move failed"}"
            }
        }
        OrganiseResult(applied,skipped,failed,messages)
    }

    fun undo(action: FileActionRecord): String? = synchronized(actionLock) {
        val recorded = store.fileAction(action.id) ?: return@synchronized "This change is no longer available."
        // A second tap must not act on a stale record after the first Undo completes.
        if(recorded != action || action.status != "applied") return@synchronized "This change is not available to undo."
        if(action.hash == null) return@synchronized "This older move has no saved content fingerprint. FileMate cannot safely verify Undo, so nothing was changed."
        val current = File(action.targetPath)
        val original = File(action.sourcePath)
        if(!current.isFile) return@synchronized "The organised file is no longer at the recorded location."
        if(original.exists()) return@synchronized "The original location now contains another file. Nothing was overwritten."
        if(!ContentFingerprint.matches(current,action.expectedSize,action.hash)) return@synchronized "The file content changed or could not be verified. Undo was stopped."
        try {
            if(original.parentFile?.mkdirs() == false && original.parentFile?.isDirectory != true) return@synchronized "The original folder couldn't be restored."
            store.beginFileUndo(action.id)
            transfer.move(current,original,action.expectedSize,action.hash)
            if(!original.isFile || current.exists()) throw IllegalStateException("Android did not finish the undo")
            store.completeFileUndo(action.id,original.length(),original.lastModified())
            null
        } catch(e: Exception) {
            if(e is FileAlreadyExistsException || (current.isFile && !original.exists())) store.cancelFileUndo(action.id,e.message ?: "Undo did not start")
            else store.reviewFileAction(action.id,"Undo interrupted. Both paths need review; no copy was automatically removed.")
            "Undo couldn't finish: ${e.message ?: "unknown error"}"
        }
    }

    fun recoverPending() = synchronized(actionLock) {
        store.pendingFileActions().forEach { action ->
            val source = File(action.sourcePath)
            val target = File(action.targetPath)
            if(action.hash == null) {
                store.reviewFileAction(action.id,"No saved content fingerprint. File locations need review; nothing was changed.")
                return@forEach
            }
            if(action.status == "undo_pending") when {
                source.isFile && !target.exists() && ContentFingerprint.matches(source,action.expectedSize,action.hash) -> runCatching { store.completeFileUndo(action.id,source.length(),source.lastModified()) }
                    .onFailure { store.reviewFileAction(action.id,it.message ?: "Undo needs review") }
                target.isFile && !source.exists() && ContentFingerprint.matches(target,action.expectedSize,action.hash) -> store.cancelFileUndo(action.id,"Undo did not start")
                else -> store.reviewFileAction(action.id,"Undo locations require review")
            } else when {
                !source.exists() && target.isFile && ContentFingerprint.matches(target,action.expectedSize,action.hash) -> runCatching { store.completeFileAction(action.id,target.length(),target.lastModified()) }
                    .onFailure { store.reviewFileAction(action.id,it.message ?: "Moved file needs review") }
                source.isFile && !target.exists() -> store.failFileAction(action.id,"Move did not start")
                else -> store.reviewFileAction(action.id,"Move locations require review")
            }
        }
    }

    private fun uniqueTarget(folder: File, requestedName: String, reserved: MutableSet<String>): File {
        val extension = requestedName.substringAfterLast('.',"").takeIf { requestedName.contains('.') }
        val stem = if(extension == null) requestedName else requestedName.dropLast(extension.length + 1)
        var number = 1
        while(true) {
            val name = if(number == 1) requestedName else "$stem ($number)" + (extension?.let { ".$it" } ?: "")
            val candidate = File(folder,name)
            val key = candidate.absolutePath.lowercase(Locale.ROOT)
            if(!candidate.exists() && reserved.add(key)) return candidate
            number++
        }
    }
}
