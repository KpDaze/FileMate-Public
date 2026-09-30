package app.filemate

import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import java.security.MessageDigest

data class ComparisonTrashItem(val media: IndexedMedia, val expires: Long)

/** Media changes are performed only by Android's user-confirmed trash/restore request. */
class GalleryMediaActions(private val app: FileMateApp) {
    suspend fun validate(selection: List<IndexedMedia>, fingerprints: Map<String,String>): List<IndexedMedia> {
        require(selection.isNotEmpty() && selection.size <= 2000) { "Choose between 1 and 2,000 images." }
        val fresh = GalleryScanner(app).scan()
        val byId = fresh.associateBy { it.identity }
        for(item in selection) {
            val now = byId[item.identity]
            check(now != null && GalleryCompareScanner.stamp(now) == GalleryCompareScanner.stamp(item)) {
                "An image changed or is unavailable. Cancel and scan again."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            app.contentResolver.openInputStream(item.uri.toUri())?.use { stream ->
                val buffer = ByteArray(64*1024);var bytes = 0L
                while(true) { currentCoroutineContext().ensureActive();val n=stream.read(buffer);if(n<0) break;bytes += n;check(bytes <= item.size) { "Image changed. Scan again." };digest.update(buffer,0,n) }
                check(bytes == item.size) { "Image changed. Scan again." }
            } ?: error("An image is unavailable. Cancel and scan again.")
            check(digest.digest().joinToString("") { "%02x".format(it) } == fingerprints[item.identity]) {
                "Image contents changed. Cancel and scan again."
            }
        }
        // Preserve all accessible media, including videos, and user-owned assignments.
        app.store.saveMediaSnapshot(fresh)
        return selection.map { byId.getValue(it.identity) }
    }
    private fun tracked(): List<String> {
        val data = JSONArray(app.store.state("comparison_trash_v1") ?: "[]")
        return (0 until data.length()).map { data.getString(it) }
    }
    fun track(items: List<IndexedMedia>) {
        app.store.state("comparison_trash_v1",JSONArray((tracked()+items.map { it.identity }).distinct()).toString())
    }
    /** Null means absent/inaccessible, never permission to change another row with a recycled ID. */
    fun trashState(item: IndexedMedia): Pair<Boolean,Long>? {
        val uri = item.uri.toUri()
        val version = MediaStore.getVersion(app,item.volume) ?: return null
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE) }
        app.contentResolver.query(uri,arrayOf("_id","generation_added","is_trashed","date_expires"),args,null)?.use { c ->
            if(!c.moveToFirst()) return null
            val identity = GalleryRules.identity(item.volume,version,item.kind,c.getLong(0),c.getLong(1))
            if(identity != item.identity) return null
            return (c.getInt(2)==1) to (if(c.isNull(3)) 0 else c.getLong(3)*1000)
        }
        return null
    }
    fun trashItems(): List<ComparisonTrashItem> {
        val ids = tracked().toSet()
        return app.store.mediaItems(true).filter { it.identity in ids }.mapNotNull { item ->
            val state = trashState(item)
            if(state?.first == true) ComparisonTrashItem(item,state.second) else null
        }
    }
    fun restoreRequest(items: List<IndexedMedia>): android.app.PendingIntent {
        require(items.isNotEmpty() && items.size <= 2000)
        check(items.all { trashState(it)?.first == true }) { "Some items are no longer available in Trash. Refresh and review again." }
        return MediaStore.createTrashRequest(app.contentResolver,items.map { it.uri.toUri() },false)
    }
}
