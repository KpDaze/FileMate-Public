package app.filemate

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal fun createGalleryTables(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE media(
        identity TEXT PRIMARY KEY,uri TEXT NOT NULL,volume TEXT NOT NULL,kind TEXT NOT NULL,
        name TEXT NOT NULL,relative_path TEXT NOT NULL,current_path TEXT NOT NULL,bucket TEXT NOT NULL,
        mime TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,taken INTEGER NOT NULL,added INTEGER NOT NULL,
        width INTEGER NOT NULL,height INTEGER NOT NULL,duration INTEGER NOT NULL,generation INTEGER NOT NULL,sort_time INTEGER NOT NULL,
        camera INTEGER NOT NULL,screenshot INTEGER NOT NULL,downloads INTEGER NOT NULL,screenshot_confidence TEXT NOT NULL,clues TEXT NOT NULL,
        available INTEGER NOT NULL,project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,
        project_confidence TEXT NOT NULL DEFAULT 'Unassigned',original_name TEXT NOT NULL,original_path TEXT NOT NULL,first_seen INTEGER NOT NULL
    )""")
    db.execSQL("CREATE INDEX media_order ON media(available,sort_time DESC)")
    db.execSQL("CREATE INDEX media_project ON media(project_id)")
    db.execSQL("CREATE INDEX media_path ON media(current_path)")
}

fun Store.saveMediaSnapshot(items: List<IndexedMedia>) = synchronized(this) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        // Absence is unavailability, never deletion or loss of user-owned metadata.
        db.execSQL("UPDATE media SET available=0")
        items.distinctBy { it.identity }.forEach { item ->
            val values = ContentValues().apply {
                put("uri",item.uri);put("volume",item.volume);put("kind",item.kind);put("name",item.name)
                put("relative_path",item.relativePath);put("current_path",item.currentPath);put("bucket",item.bucket)
                put("mime",item.mime);put("size",item.size);put("modified",item.modified);put("taken",item.taken);put("added",item.added)
                put("width",item.width);put("height",item.height);put("duration",item.duration);put("generation",item.generation);put("sort_time",item.sortTime)
                put("camera",if(item.clues.camera) 1 else 0);put("screenshot",if(item.clues.screenshot) 1 else 0);put("downloads",if(item.clues.downloads) 1 else 0)
                put("screenshot_confidence",item.clues.screenshotConfidence);put("clues",item.clues.explanation);put("available",1)
            }
            // Explicit organiser assignments are shared metadata for this same current path.
            db.rawQuery("SELECT project_id,project_confidence FROM files WHERE path=?",arrayOf(item.currentPath)).use { c ->
                if(c.moveToFirst()) {
                    if(c.isNull(0)) values.putNull("project_id") else values.put("project_id",c.getLong(0))
                    values.put("project_confidence",c.getString(1))
                }
            }
            if(db.update("media",values,"identity=?",arrayOf(item.identity)) == 0) {
                values.put("identity",item.identity);values.put("original_name",item.name);values.put("original_path",item.currentPath)
                values.put("first_seen",System.currentTimeMillis())
                db.insertOrThrow("media",null,values)
            }
        }
        state("gallery_last_scan",System.currentTimeMillis().toString())
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

fun Store.hideUnavailableMedia() = synchronized(this) { writableDatabase.execSQL("UPDATE media SET available=0") }

fun Store.mediaItems(includeUnavailable: Boolean = false): List<IndexedMedia> = synchronized(this) {
    readableDatabase.rawQuery("""SELECT m.identity,m.uri,m.volume,m.kind,m.name,m.relative_path,m.current_path,m.bucket,
        m.mime,m.size,m.modified,m.taken,m.added,m.width,m.height,m.duration,m.generation,m.sort_time,
        m.camera,m.screenshot,m.downloads,m.screenshot_confidence,m.clues,m.available,m.project_id,p.name,
        m.project_confidence,m.original_name,m.original_path FROM media m LEFT JOIN projects p ON p.id=m.project_id
        ${if(includeUnavailable) "" else "WHERE m.available=1"} ORDER BY m.sort_time DESC,m.identity""",null).use { c ->
        buildList { while(c.moveToNext()) add(IndexedMedia(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),
            c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.getLong(9),c.getLong(10),c.getLong(11),c.getLong(12),
            c.getInt(13),c.getInt(14),c.getLong(15),c.getLong(16),c.getLong(17),
            MediaClues(c.getInt(18)==1,c.getInt(19)==1,c.getInt(20)==1,c.getString(21),c.getString(22)),c.getInt(23)==1,
            if(c.isNull(24)) null else c.getLong(24),c.getString(25),c.getString(26),c.getString(27),c.getString(28))) }
    }
}

fun Store.assignMedia(identity: String, projectId: Long?) = synchronized(this) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val item = db.rawQuery("SELECT name,current_path,size,modified FROM media WHERE identity=? AND available=1",arrayOf(identity)).use {
            require(it.moveToFirst()) { "This media item is unavailable. Refresh Gallery before assigning it." }
            arrayOf(it.getString(0),it.getString(1),it.getLong(2).toString(),it.getLong(3).toString())
        }
        val projectName = projectId?.let { id -> db.rawQuery("SELECT name FROM projects WHERE id=?",arrayOf(id.toString())).use {
            require(it.moveToFirst()) { "Project no longer exists." };it.getString(0)
        } }
        val values = ContentValues().apply {
            if(projectId == null) putNull("project_id") else put("project_id",projectId)
            put("project_confidence",if(projectId == null) "Unassigned" else "Confirmed")
        }
        db.update("media",values,"identity=?",arrayOf(identity))
        // The same file may already appear in the organiser; keep its manual assignment consistent.
        if(db.update("files",values,"path=?",arrayOf(item[1])) == 0 && projectId != null) {
            db.insertOrThrow("files",null,ContentValues(values).apply {
                put("path",item[1]);put("size",item[2].toLong());put("modified",item[3].toLong())
            })
        }
        if(projectId == null) db.execSQL("UPDATE cleanup_entries SET flags=flags | ? WHERE path=? AND root='Downloads'",arrayOf(CleanupFlags.UNSORTED_DOWNLOAD,item[1]))
        else db.execSQL("UPDATE cleanup_entries SET flags=flags & ? WHERE path=?",arrayOf(CleanupFlags.UNSORTED_DOWNLOAD.inv(),item[1]))
        refreshCleanupCounts(db)
        if(projectId != null) {
            db.execSQL("UPDATE projects SET updated=? WHERE id=?",arrayOf(System.currentTimeMillis(),projectId))
            learnProjectAssignment(item[0] as String,projectId)
        }
        history(if(projectId == null) "Gallery project cleared" else "Gallery assigned to $projectName", "${item[0]}. File unchanged.")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

fun Store.galleryProjectCount(projectId: Long): Int = synchronized(this) {
    readableDatabase.rawQuery("SELECT COUNT(*) FROM media WHERE project_id=? AND available=1",arrayOf(projectId.toString())).use { it.moveToFirst();it.getInt(0) }
}

/** One outer transaction makes the complete selection succeed or roll back together. */
fun Store.assignMediaBatch(identities: List<String>, projectId: Long?) = synchronized(this) {
    val ids = identities.distinct()
    require(ids.isNotEmpty()) { "Select at least one media item." }
    val db = writableDatabase
    db.beginTransaction()
    try {
        ids.forEach { assignMedia(it,projectId) }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
