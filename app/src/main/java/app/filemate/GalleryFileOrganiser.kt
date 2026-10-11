package app.filemate

import android.os.Environment
import java.io.File

data class GalleryMovePlan(
    val identity: String,
    val sourcePath: String,
    val targetPath: String,
    val sourceName: String,
    val targetName: String,
    val size: Long,
    val modified: Long,
    val hash: String,
    val supported: Boolean,
    val note: String
)

class GalleryFileOrganiser(private val store: Store, private val transfer: VerifiedFileTransfer = VerifiedFileTransfer()) {
    @Suppress("DEPRECATION")
    fun plan(item: IndexedMedia, project: Project, tidyName: Boolean): GalleryMovePlan {
        val source = runCatching { File(item.currentPath).canonicalFile }.getOrNull()
        val shared = Environment.getExternalStorageDirectory().canonicalFile
        val insideShared = source != null && (source.path == shared.path || source.path.startsWith("${shared.path}/"))
        val fingerprint = if(insideShared && source?.isFile == true) ContentFingerprint.read(source) else null
        val requested = if(tidyName) FileNaming.tidy(project.name,item.name,item.modified) else item.name
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"FileMate/${FileNaming.folder(project.name)}")
        val target = unique(folder,requested)
        val unchanged = fingerprint != null && fingerprint.size == item.size &&
            (item.modified <= 0 || fingerprint.modified / 1000 == item.modified / 1000)
        val supported = insideShared && source?.isFile == true && unchanged && source.path != target.path
        val note = when {
            !insideShared -> "This media item is not exposed as a normal shared-storage file."
            source?.isFile != true -> "The media file is no longer available at its indexed path."
            fingerprint == null -> "FileMate could not verify the media contents."
            !unchanged -> "This media changed since Gallery was loaded. Refresh before moving it."
            source.path == target.path -> "Already at this location."
            else -> "Reviewed move to Pictures/FileMate/${FileNaming.folder(project.name)}."
        }
        return GalleryMovePlan(item.identity,source?.path ?: item.currentPath,target.path,item.name,target.name,
            fingerprint?.size ?: item.size,fingerprint?.modified ?: item.modified,fingerprint?.hash.orEmpty(),supported,note)
    }

    fun apply(plan: GalleryMovePlan, projectId: Long): String? {
        if(!plan.supported) return plan.note
        val item = store.mediaItems().firstOrNull { it.identity == plan.identity }
            ?: return "This media item is no longer available. Refresh Gallery."
        if(item.currentPath != plan.sourcePath || item.size != plan.size ||
            (item.modified > 0 && item.modified / 1000 != plan.modified / 1000))
            return "This media item changed after review. Refresh Gallery and preview again."
        val source = File(plan.sourcePath);val target = File(plan.targetPath)
        if(target.parentFile?.mkdirs() == false && target.parentFile?.isDirectory != true) return "Destination folder could not be created."
        return try {
            val project = store.projects().firstOrNull { it.id == projectId } ?: return "Project no longer exists."
            if(!ContentFingerprint.matches(source,plan.size,plan.hash))
                return "The media changed after review. Refresh Gallery and review the move again."
            val journal = OrganisePlan(plan.sourcePath,plan.targetPath,plan.sourceName,plan.targetName,"Gallery",
                plan.size,plan.modified,plan.hash,project.id,project.name,true,"Reviewed Gallery move")
            // Use the same action lock, journal and recovery policy as other physical moves.
            // completeFileAction already retires the old media URI and records the new path.
            val result = FileOrganiser(store,transfer).apply(listOf(journal))
            if(result.applied != 1) return result.messages.joinToString("\n").ifBlank { "The move did not finish. Refresh and review it again." }
            null
        } catch(e: Exception) { e.message ?: "Media move failed. The original was not silently replaced." }
    }

    private fun unique(folder: File, name: String): File {
        var candidate=File(folder,name);if(!candidate.exists()) return candidate
        val ext=name.substringAfterLast('.',"").takeIf { name.contains('.') }
        val stem=if(ext==null) name else name.dropLast(ext.length+1)
        var n=2
        while(candidate.exists()) { candidate=File(folder,"${stem}_${n++}${ext?.let { ".$it" }.orEmpty()}") }
        return candidate
    }
}
