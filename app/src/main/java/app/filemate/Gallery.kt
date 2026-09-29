package app.filemate

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** User-owned project/original fields are never replaced by a rescan. */
data class IndexedMedia(val identity: String, val uri: String, val volume: String, val kind: String,
    val name: String, val relativePath: String, val currentPath: String, val bucket: String,
    val mime: String, val size: Long, val modified: Long, val taken: Long, val added: Long,
    val width: Int, val height: Int, val duration: Long, val generation: Long, val sortTime: Long,
    val clues: MediaClues, val available: Boolean = true, val projectId: Long? = null,
    val projectName: String? = null, val projectConfidence: String = "Unassigned",
    val originalName: String = name, val originalPath: String = currentPath)

data class MediaAccess(val images: Boolean, val videos: Boolean, val selected: Boolean) {
    val any get() = images || videos || selected
    val description get() = when {
        images && videos -> "Photos and videos allowed"
        selected -> "Selected photos and videos only" + if(images) " · all photos allowed" else if(videos) " · all videos allowed" else ""
        images -> "Photos allowed · videos not allowed"
        videos -> "Videos allowed · photos not allowed"
        else -> "Photo and video access is off"
    }
    companion object {
        fun read(context: Context): MediaAccess {
            fun granted(permission: String) = ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_GRANTED
            val broad = Environment.isExternalStorageManager()
            val legacy = Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE)
            return MediaAccess(broad || legacy || (Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES)),
                broad || legacy || (Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_VIDEO)),
                Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        }
        fun permissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

data class GalleryProgress(val running: Boolean = false, val ready: Boolean = false, val message: String? = null)

class GalleryScanner(private val context: Context) {
    /** Query-only: no media insert/update/delete and no filesystem writes. */
    suspend fun scan(access: MediaAccess = MediaAccess.read(context)): List<IndexedMedia> {
        if(!access.any) return emptyList()
        val items = mutableListOf<IndexedMedia>()
        val volumes = MediaStore.getExternalVolumeNames(context)
        val storage = context.getSystemService(StorageManager::class.java)
        for(volume in volumes.sorted()) {
            currentCoroutineContext().ensureActive()
            val version = MediaStore.getVersion(context,volume) ?: throw IllegalStateException("Storage became unavailable. Refresh to try again.")
            @Suppress("DEPRECATION")
            val root = if(volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) Environment.getExternalStorageDirectory()
                else storage.storageVolumes.firstOrNull { it.mediaStoreVolumeName == volume }?.directory
            for(kind in listOf("image","video")) {
                if(kind == "image" && !access.images && !access.selected) continue
                if(kind == "video" && !access.videos && !access.selected) continue
                val collection = if(kind == "image") MediaStore.Images.Media.getContentUri(volume) else MediaStore.Video.Media.getContentUri(volume)
                val columns = arrayOf("_id","_display_name","relative_path","bucket_display_name","mime_type","_size","date_modified",
                    "datetaken","date_added","width","height","duration","generation_added","generation_modified")
                val cursor = context.contentResolver.query(collection,columns,"is_pending=0 AND is_trashed=0",null,null)
                    ?: throw IllegalStateException("Android did not return a media list. Refresh to try again.")
                cursor.use { c ->
                    fun string(column: Int) = if(c.isNull(column)) "" else c.getString(column).orEmpty()
                    fun number(column: Int) = if(c.isNull(column)) 0L else c.getLong(column).coerceAtLeast(0)
                    while(c.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = c.getLong(0)
                        val name = string(1).ifBlank { "Unnamed ${if(kind == "video") "video" else "image"}" }
                        val relative = string(2)
                        val uri = ContentUris.withAppendedId(collection,id).toString()
                        // This path is metadata for display/project links, never opened by the Gallery.
                        val path = if(root != null && relative.isNotBlank() && !relative.startsWith('/') &&
                            relative.split('/').none { it == ".." } && !name.contains('/')) File(root,relative + name).path else uri
                        val width = number(9).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val height = number(10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val clues = GalleryRules.clues(name,relative,string(3),width,height,kind == "video")
                        items += IndexedMedia(GalleryRules.identity(volume,version,kind,id,number(12)),uri,volume,kind,name,relative,path,string(3),
                            string(4),number(5),number(6)*1000,number(7),number(8)*1000,width,height,number(11),number(13),
                            GalleryRules.chronologicalTime(number(7),number(8),number(6)),clues)
                    }
                }
            }
            check(MediaStore.getVersion(context,volume) == version) { "Android's media index changed. Refresh to try again." }
        }
        check(MediaAccess.read(context) == access) { "Media access changed. Refresh to use the current selection." }
        return items
    }
}

fun screenshotGroups(items: List<IndexedMedia>, unassignedOnly: Boolean = false): List<ScreenshotGroup> =
    GalleryScreenshotRules.groups(items.map { ScreenshotCandidate(it.identity,it.volume,it.relativePath,it.sortTime,
        it.clues.screenshot,it.clues.camera,it.kind == "video",it.available,it.projectId != null) },unassignedOnly)
