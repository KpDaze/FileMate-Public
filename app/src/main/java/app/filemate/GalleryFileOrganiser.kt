package app.filemate

import android.content.ContentValues
import android.os.Environment
import java.io.File

data class GalleryMovePlan(
    val identity: String,
    val sourcePath: String,
    val targetPath: String,
    val sourceName: String,
    val targetName: String,
    val size: Long,
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
        val supported = insideShared && source?.isFile == true && fingerprint != null && source.path != target.path
        val note = when {
            !insideShared -> "This media item is not exposed as a normal shared-storage file."
            source?.isFile != true -> "The media file is no longer available at its indexed path."
            fingerprint == null -> "FileMate could not verify the media contents."
            source.path == target.path -> "Already at this location."
            else -> "Reviewed move to Pictures/FileMate/${FileNaming.folder(project.name)}."
        }
        return GalleryMovePlan(item.identity,source?.path ?: item.currentPath,target.path,item.name,target.name,item.size,
            fingerprint?.hash.orEmpty(),supported,note)
    }

    fun apply(plan: GalleryMovePlan, projectId: Long): String? {
        if(!plan.supported) return plan.note
        val source = File(plan.sourcePath);val target = File(plan.targetPath)
        if(target.parentFile?.mkdirs() == false && target.parentFile?.isDirectory != true) return "Destination folder could not be created."
        return try {
            val project = store.projects().firstOrNull { it.id == projectId } ?: return "Project no longer exists."
            val journal = OrganisePlan(plan.sourcePath,plan.targetPath,plan.sourceName,plan.targetName,"Gallery",
                plan.size,source.lastModified(),plan.hash,project.id,project.name,true,"Reviewed Gallery move")
            val actionId = store.beginFileAction(journal)
            try {
                transfer.move(source,target,plan.size,plan.hash)
                store.completeFileAction(actionId,target.length(),target.lastModified())
                store.completeGalleryMove(plan.identity,plan.sourcePath,plan.targetPath,plan.targetName,projectId)
            } catch(e: Exception) {
                if(source.isFile && !target.exists()) store.failFileAction(actionId,e.message ?: "Gallery move failed")
                else store.reviewFileAction(actionId,e.message ?: "Gallery move needs review")
                throw e
            }
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

fun Store.completeGalleryMove(identity: String, oldPath: String, newPath: String, newName: String, projectId: Long) = synchronized(this) {
    val db=writableDatabase;db.beginTransaction()
    try {
        val projectName=db.rawQuery("SELECT name FROM projects WHERE id=?",arrayOf(projectId.toString())).use {
            require(it.moveToFirst()) { "Project no longer exists." };it.getString(0)
        }
        // The next Gallery refresh obtains a fresh MediaStore identity/path for the moved file.
        // Keep the old row unavailable rather than pretending its content URI still identifies the new path.
        db.update("media",ContentValues().apply {
            put("available",0);put("project_id",projectId);put("project_confidence","Confirmed")
        },"identity=?",arrayOf(identity))
        db.delete("files","path=?",arrayOf(oldPath))
        db.insertWithOnConflict("files",null,ContentValues().apply {
            put("path",newPath);put("size",File(newPath).length());put("modified",File(newPath).lastModified())
            put("project_id",projectId);put("project_confidence","Confirmed")
        },android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL("UPDATE projects SET updated=? WHERE id=?",arrayOf(System.currentTimeMillis(),projectId))
        history("Gallery file moved","${newName} → Pictures/FileMate/${projectName}. Original media path was ${oldPath}.")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
