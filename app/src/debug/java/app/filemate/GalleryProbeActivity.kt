package app.filemate

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Debug-only, bounded, read-only media proof. The shell owns the disposable fixtures. */
class GalleryProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FileMateApp
        val phase = intent.getStringExtra("phase") ?: "full"
        val result = File(filesDir,"gallery-$phase.json")
        result.delete()
        app.scope.launch {
            try {
                check(!Environment.isExternalStorageManager()) { "Gallery proof must not use All files access" }
                val store = app.store
                val access = MediaAccess.read(this@GalleryProbeActivity)
                if(phase == "selected") {
                    check(access.selected && !access.images && !access.videos)
                    val selected = GalleryScanner(this@GalleryProbeActivity).scan()
                    check(selected.isEmpty()) { "No fixture was selected in Android" }
                    store.saveMediaSnapshot(selected)
                    check(store.mediaItems().isEmpty())
                    check(store.mediaItems(true).any { it.projectId != null })
                } else if(phase == "denied") {
                    check(!access.any) { "Expected permission denial: $access" }
                    check(GalleryScanner(this@GalleryProbeActivity).scan().isEmpty())
                    store.hideUnavailableMedia()
                    check(store.mediaItems().isEmpty())
                    check(store.mediaItems(true).any { it.projectId != null })
                } else {
                    val expected = if(phase == "images") 3 else 4
                    var media = emptyList<IndexedMedia>()
                    for(attempt in 0..29) {
                        media = GalleryScanner(this@GalleryProbeActivity).scan().filter { "FileMateFixture" in it.name }
                        if(media.size == expected && (phase != "changed" || media.any { it.clues.downloads && it.width == 700 })) break
                        delay(500)
                    }
                    check(media.size == expected) { "Expected $expected fixtures, got ${media.map { it.name }}; access=$access" }
                    check(media.all { it.width > 0 && it.height > 0 && it.size > 0 })
                    val before = media.associate { it.uri to hash(it) }
                    store.saveMediaSnapshot(media)
                    check(store.mediaItems().size == expected)
                    if(phase == "full") {
                        check(access.images && access.videos)
                        check(media.single { it.clues.screenshot }.name.startsWith("Screenshot"))
                        check(media.single { it.clues.camera }.name.contains("camera"))
                        check(media.single { it.kind == "video" }.duration > 0)
                        val download = media.single { it.clues.downloads }
                        val project = store.createProject("Gallery fixture project")
                        val batch = media.filter { it.clues.camera || it.clues.screenshot }.map { it.identity }
                        val historyBefore = store.history().size
                        check(runCatching { store.assignMediaBatch(batch + "missing-fixture",project) }.isFailure)
                        check(store.mediaItems().filter { it.identity in batch }.all { it.projectId == null })
                        check(store.history().size == historyBefore) { "Failed batch left partial history" }
                        check(store.assignedPaths().isEmpty()) { "Failed batch left organiser assignments" }
                        store.assignMediaBatch(batch,project)
                        check(store.mediaItems().filter { it.identity in batch }.all { it.projectId == project })
                        store.assignMediaBatch(batch,null)
                        check(store.mediaItems().filter { it.identity in batch }.all { it.projectId == null })
                        store.assignMedia(download.identity,project)
                        check(store.projects().single { it.id == project }.fileCount == 1)
                        check(store.assignedPaths().contains(download.currentPath))
                        store.saveMediaSnapshot(media)
                        check(store.mediaItems().single { it.identity == download.identity }.projectId == project)
                        // A missing entry keeps metadata, then returns with the same assignment.
                        store.saveMediaSnapshot(media.filter { it.identity != download.identity })
                        check(store.mediaItems().size == 3)
                        check(store.mediaItems(true).single { it.identity == download.identity }.projectId == project)
                        store.saveMediaSnapshot(media)
                        // A changed observation preserves original identity and explicit assignment.
                        store.saveMediaSnapshot(media.map { if(it.identity == download.identity) it.copy(name="changed metadata.png",size=123) else it })
                        val updated = store.mediaItems().single { it.identity == download.identity }
                        check(updated.name == "changed metadata.png" && updated.originalName == download.name && updated.projectId == project)
                        store.saveMediaSnapshot(media)
                        verifyMigration()
                        // Deleting a disposable project clears assignments without deleting media.
                        val disposable = store.createProject("Disposable gallery project")
                        val camera = media.single { it.clues.camera }
                        store.assignMedia(camera.identity,disposable)
                        store.deleteProject(disposable)
                        check(store.mediaItems().single { it.identity == camera.identity }.projectId == null)
                    } else {
                        val assigned = store.mediaItems().single { it.clues.downloads }
                        check(assigned.projectId != null && assigned.originalName == "FileMateFixture_download.png")
                        if(phase == "images") check(media.none { it.kind == "video" })
                        if(phase == "changed") check(assigned.width == 700)
                    }
                    check(media.associate { it.uri to hash(it) } == before) { "Fixture bytes changed during Gallery operations" }
                }
                result.writeText(JSONObject().put("passed",true).put("phase",phase).put("all_files_access",false).toString())
            } catch(e: Throwable) {
                result.writeText(JSONObject().put("passed",false).put("phase",phase).put("error",e.stackTraceToString()).toString())
            } finally { runOnUiThread { finish() } }
        }
    }
    private fun hash(item: IndexedMedia): String = requireNotNull(contentResolver.openInputStream(item.uri.toUri())).use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while(true) { val count=stream.read(buffer);if(count < 0) break;digest.update(buffer,0,count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun verifyMigration() {
        val name = "gallery-migration-fixture.db"
        deleteDatabase(name)
        val old = Store(this,name)
        val project = old.createProject("Preserved project")
        old.add(HubApp("fixture.app","Fixture"));old.state("proof","preserved");old.history("Preserved history","fixture")
        old.writableDatabase.execSQL("INSERT INTO files(path,size,modified,project_id,project_confidence) VALUES('fixture-path',123,456,?,'Confirmed')",arrayOf(project))
        old.writableDatabase.execSQL("DROP TABLE media")
        old.writableDatabase.version = 4
        old.close()
        Store(this,name).use { upgraded ->
            check(upgraded.readableDatabase.version == 5)
            check(upgraded.projects().single().id == project && upgraded.projects().single().fileCount == 1)
            check(upgraded.hub().single().packageName == "fixture.app")
            check(upgraded.state("proof") == "preserved")
            check(upgraded.history().any { it.title == "Preserved history" })
            check(upgraded.mediaItems().isEmpty())
        }
        deleteDatabase(name)
    }
}
