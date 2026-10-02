package app.filemate

import android.content.ContentValues
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
                if(phase == "stage3d") {
                    verifyStage3d(app)
                } else if(phase == "stage3c") {
                    verifyStage3c(app)
                } else if(phase == "stage3c-after") {
                    val fresh = GalleryScanner(this@GalleryProbeActivity).scan()
                    store.saveMediaSnapshot(fresh)
                    val project = store.projects().single { it.name == "Comparison fixture project" }
                    check(project.fileCount == 2)
                    check(store.projectFiles(project.id).isEmpty())
                    check(store.mediaItems().count { it.projectId == project.id } == 2)
                    check(GalleryMediaActions(app).trashItems().isEmpty())
                    check(fresh.count { it.name.startsWith("FileMateCompare_") } == 3)
                } else if(phase == "stage3c-denied") {
                    check(!access.images && !access.selected)
                    check(runCatching { GalleryCompareScanner(this@GalleryProbeActivity).scan {} }.isFailure)
                } else if(phase == "stage3c-selected") {
                    check(access.selected && !access.images)
                    val output = GalleryCompareScanner(this@GalleryProbeActivity).scan {}
                    check(output.items.isEmpty() && output.matches.isEmpty())
                } else if(phase == "stage3b") {
                    verifyStage3b(store)
                } else if(phase == "selected") {
                    check(access.selected && !access.images && !access.videos)
                    val selected = GalleryScanner(this@GalleryProbeActivity).scan()
                    check(selected.isEmpty()) { "No fixture was selected in Android" }
                    store.saveMediaSnapshot(selected)
                    check(store.mediaItems().isEmpty())
                    check(screenshotGroups(store.mediaItems(),true).isEmpty())
                    check(store.mediaItems(true).any { it.projectId != null })
                } else if(phase == "denied") {
                    check(!access.any) { "Expected permission denial: $access" }
                    check(GalleryScanner(this@GalleryProbeActivity).scan().isEmpty())
                    store.hideUnavailableMedia()
                    check(store.mediaItems().isEmpty())
                    check(screenshotGroups(store.mediaItems(),true).isEmpty())
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
    private suspend fun verifyStage3d(app: FileMateApp) {
        var media = emptyList<IndexedMedia>()
        for(attempt in 0..29) {
            media = GalleryScanner(this).scan().filter { it.name.startsWith("FileMateCompare_") }
            if(media.size == 3) break
            delay(500)
        }
        check(media.size == 3) { "Stage 3D expected three fixtures, got ${media.map { it.name }}" }
        val before = media.associate { it.uri to hash(it) }
        app.store.saveMediaSnapshot(media)
        val ids = media.map { it.identity }
        app.store.setMediaFavourite(listOf(ids[0],ids[1],ids[0]),true)
        check(app.store.favouriteMediaIds() == setOf(ids[0],ids[1]))
        app.store.setMediaFavourite(listOf(ids[1]),false)
        check(app.store.favouriteMediaIds() == setOf(ids[0]))
        val album = app.store.createGalleryAlbum(" Stage   3D album ")
        check(app.store.galleryAlbums().single { it.id == album }.name == "Stage 3D album")
        app.store.addMediaToAlbum(album,listOf(ids[0],ids[1],ids[0]))
        check(app.store.albumMediaIds(album) == setOf(ids[0],ids[1]))
        check(app.store.galleryAlbums().single { it.id == album }.itemCount == 2)
        app.store.removeMediaFromAlbum(album,listOf(ids[1]))
        check(app.store.albumMediaIds(album) == setOf(ids[0]))
        app.store.renameGalleryAlbum(album,"Renamed album")
        check(app.store.galleryAlbums().single { it.id == album }.name == "Renamed album")
        app.store.saveMediaSnapshot(media)
        check(app.store.favouriteMediaIds() == setOf(ids[0]))
        check(app.store.albumMediaIds(album) == setOf(ids[0]))
        Store(this).use { reopened ->
            check(reopened.favouriteMediaIds() == setOf(ids[0]))
            check(reopened.albumMediaIds(album) == setOf(ids[0]))
        }
        app.store.deleteGalleryAlbum(album)
        check(app.store.galleryAlbums().none { it.id == album })
        check(app.store.mediaItems().map { it.identity }.toSet().containsAll(ids))
        check(media.associate { it.uri to hash(it) } == before) { "Stage 3D metadata actions changed fixture bytes" }
        verifyStage3dMigration()
    }

    private fun verifyStage3dMigration() {
        val name = "gallery-stage3d-migration.db"
        deleteDatabase(name)
        Store(this,name).use { current ->
            val project=current.createProject("Migration project")
            current.state("migration-proof","kept")
            current.writableDatabase.execSQL("DROP TABLE media_album_items")
            current.writableDatabase.execSQL("DROP TABLE media_albums")
            current.writableDatabase.execSQL("DROP TABLE media_favourites")
            current.writableDatabase.version=5
            check(project > 0)
        }
        Store(this,name).use { upgraded ->
            check(upgraded.readableDatabase.version == 6)
            check(upgraded.projects().single().name == "Migration project")
            check(upgraded.state("migration-proof") == "kept")
            check(upgraded.galleryAlbums().isEmpty())
            check(upgraded.favouriteMediaIds().isEmpty())
        }
        deleteDatabase(name)
    }

    private suspend fun verifyStage3c(app: FileMateApp) {
        var media = emptyList<IndexedMedia>()
        for(attempt in 0..29) {
            media = GalleryScanner(this).scan().filter { it.name.startsWith("FileMateCompare_") }
            if(media.size == 3) break
            delay(500)
        }
        check(media.size == 3) { "Expected three comparison fixtures, got ${media.map { it.name }}" }
        val before = media.associate { it.uri to hash(it) }
        app.store.saveMediaSnapshot(media)
        val project = app.store.createProject("Comparison fixture project")
        app.store.assignMedia(media.single { it.name == "FileMateCompare_copy.png" }.identity,project)
        val assignments = app.store.mediaItems().associate { it.identity to it.projectId }
        val output = GalleryCompareScanner(this).scan {}
        check(output.checked == 3 && output.skipped == 0 && output.visualChecked == 3) { "Unexpected scan coverage: $output" }
        ImageMatchKind.entries.forEach { kind -> check(output.matches.count { it.kind == kind } == 1) { "Expected one $kind group: ${output.matches}" } }
        val exact = output.matches.single { it.kind == ImageMatchKind.EXACT }
        check(exact.ids.toSet() == media.filter { it.name != "FileMateCompare_v2.png" }.map { it.identity }.toSet())
        val actions = GalleryMediaActions(app)
        val picked = listOf(media.single { it.name == "FileMateCompare_copy.png" })
        check(actions.validate(picked,output.fingerprints).size == 1)
        check(runCatching { actions.validate(picked,emptyMap()) }.isFailure) { "Missing content evidence was accepted" }
        check(runCatching { actions.validate(picked.map { it.copy(size=it.size+1) },output.fingerprints) }.isFailure) { "Changed metadata was accepted" }
        check(runCatching { actions.validate(emptyList(),output.fingerprints) }.isFailure)
        actions.track(picked)
        check(actions.trashItems().isEmpty()) { "Untrashed media appeared in Trash" }
        check(actions.trashState(picked.single())?.first == false)
        check(actions.trashState(picked.single().copy(identity="recycled-row")) == null)
        check(runCatching { actions.restoreRequest(picked) }.isFailure)
        app.store.hideUnavailableMedia()
        check(app.store.projectFiles(project).isEmpty()) { "Unavailable media leaked into ordinary project files" }
        check(app.store.projects().single { it.id == project }.fileCount == 0)
        app.store.saveMediaSnapshot(media)
        check(app.store.projects().single { it.id == project }.fileCount == 1)
        val again = GalleryCompareScanner(this).scan {}
        check(again.matches.toSet() == output.matches.toSet()) { "Rescan changed groups" }
        app.store.state("gallery_compare_kept_v1",exact.key)
        Store(this).use { check(it.state("gallery_compare_kept_v1") == exact.key) }
        app.store.state("gallery_compare_kept_v1","")
        check(app.store.mediaItems().associate { it.identity to it.projectId } == assignments)
        check(media.associate { it.uri to hash(it) } == before) { "Comparison changed fixture bytes" }
    }
    private suspend fun verifyStage3b(store: Store) {
        var media = emptyList<IndexedMedia>()
        for(attempt in 0..29) {
            media = GalleryScanner(this).scan().filter { "FileMateFixture" in it.name }
            if(media.size == 6) break
            delay(500)
        }
        check(media.size == 6) { "Stage 3B expected six fixtures, got ${media.map { it.name }}" }
        val before = media.associate { it.uri to hash(it) }
        store.saveMediaSnapshot(media)
        val screenshots = media.filter { it.clues.screenshot && !it.clues.camera }
        check(screenshots.size == 3)
        val ids = screenshots.map { it.identity }
        val groups = screenshotGroups(store.mediaItems(),true)
        check(groups.size == 2 && groups.map { it.identities.size }.sorted() == listOf(1,2)) { "Wrong date/folder groups: $groups" }
        check(groups.flatMap { it.identities }.toSet() == ids.toSet()) { "Camera/video or assigned items entered intake" }
        store.saveMediaSnapshot(media)
        check(screenshotGroups(store.mediaItems(),true) == groups) { "Repeated scan changed/duplicated groups" }
        val temporary = store.createProject("Stage 3B disposable project")
        store.assignMediaBatch(ids,temporary)
        check(screenshotGroups(store.mediaItems(),true).isEmpty())
        check(screenshotGroups(store.mediaItems()).flatMap { it.identities }.size == 3)
        store.saveMediaSnapshot(media)
        check(screenshotGroups(store.mediaItems(),true).isEmpty()) { "Refresh lost assignments" }
        store.hideUnavailableMedia()
        check(screenshotGroups(store.mediaItems(),true).isEmpty())
        check(store.mediaItems(true).filter { it.identity in ids }.all { it.projectId == temporary })
        store.saveMediaSnapshot(media)
        check(store.mediaItems().filter { it.identity in ids }.all { it.projectId == temporary })
        store.assignMedia(ids.first(),null)
        check(screenshotGroups(store.mediaItems(),true).flatMap { it.identities } == listOf(ids.first()))
        store.deleteProject(temporary)
        check(screenshotGroups(store.mediaItems(),true).flatMap { it.identities }.toSet() == ids.toSet())
        check(media.associate { it.uri to hash(it) } == before) { "Stage 3B changed fixture bytes" }
        // Give one screenshot an existing organiser observation. The actual Needs Sorting
        // screen must show its group, without repeating its file row under Other files.
        val overlap = screenshots.first { it.name == "Screenshot_FileMateFixture.png" }
        store.writableDatabase.insertOrThrow("observations",null,ContentValues().apply {
            put("name",overlap.name);put("path",overlap.currentPath);put("size",overlap.size)
            put("detected",System.currentTimeMillis());put("confidence","Low")
            put("reason","Disposable Stage 3B overlap fixture");put("via","Catch-up")
        })
        check(store.needsSorting().any { it.path == overlap.currentPath })
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
            check(upgraded.readableDatabase.version == 6)
            check(upgraded.projects().single().id == project && upgraded.projects().single().fileCount == 1)
            check(upgraded.hub().single().packageName == "fixture.app")
            check(upgraded.state("proof") == "preserved")
            check(upgraded.history().any { it.title == "Preserved history" })
            check(upgraded.mediaItems().isEmpty())
        }
        deleteDatabase(name)
    }
}
