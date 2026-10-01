package app.filemate

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class GalleryAlbum(val id: Long, val name: String, val created: Long, val updated: Long, val itemCount: Int)

internal fun createGalleryOrganisationTables(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS media_favourites(
        identity TEXT PRIMARY KEY REFERENCES media(identity) ON DELETE CASCADE,
        added INTEGER NOT NULL
    )""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS media_albums(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL COLLATE NOCASE UNIQUE,
        created INTEGER NOT NULL,
        updated INTEGER NOT NULL
    )""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS media_album_items(
        album_id INTEGER NOT NULL REFERENCES media_albums(id) ON DELETE CASCADE,
        identity TEXT NOT NULL REFERENCES media(identity) ON DELETE CASCADE,
        added INTEGER NOT NULL,
        PRIMARY KEY(album_id,identity)
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS media_album_identity ON media_album_items(identity)")
}

private fun cleanAlbumName(raw: String): String {
    val name = raw.trim().replace(Regex("\\s+")," ")
    require(name.isNotEmpty()) { "Album name cannot be empty." }
    require(name.length <= 80) { "Album name must be 80 characters or fewer." }
    return name
}

fun Store.favouriteMediaIds(): Set<String> = synchronized(this) {
    readableDatabase.rawQuery("""SELECT f.identity FROM media_favourites f
        JOIN media m ON m.identity=f.identity WHERE m.available=1 ORDER BY f.added DESC""",null).use { c ->
        buildSet { while(c.moveToNext()) add(c.getString(0)) }
    }
}

fun Store.setMediaFavourite(identities: Collection<String>, favourite: Boolean) = synchronized(this) {
    val ids = identities.distinct()
    require(ids.isNotEmpty()) { "Select at least one media item." }
    val db = writableDatabase
    db.beginTransaction()
    try {
        ids.forEach { id ->
            val available = db.rawQuery("SELECT 1 FROM media WHERE identity=? AND available=1",arrayOf(id)).use { it.moveToFirst() }
            require(available) { "A selected media item is unavailable. Refresh Gallery." }
            if(favourite) db.insertWithOnConflict("media_favourites",null,ContentValues().apply {
                put("identity",id);put("added",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_IGNORE)
            else db.delete("media_favourites","identity=?",arrayOf(id))
        }
        history(if(favourite) "Added to Favourites" else "Removed from Favourites",
            if(ids.size == 1) "1 Gallery item. File unchanged." else "${ids.size} Gallery items. Files unchanged.")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

fun Store.galleryAlbums(): List<GalleryAlbum> = synchronized(this) {
    readableDatabase.rawQuery("""SELECT a.id,a.name,a.created,a.updated,
        COUNT(CASE WHEN m.available=1 THEN 1 END)
        FROM media_albums a
        LEFT JOIN media_album_items i ON i.album_id=a.id
        LEFT JOIN media m ON m.identity=i.identity
        GROUP BY a.id ORDER BY a.updated DESC,a.name COLLATE NOCASE""",null).use { c ->
        buildList { while(c.moveToNext()) add(GalleryAlbum(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getInt(4))) }
    }
}

fun Store.createGalleryAlbum(rawName: String): Long = synchronized(this) {
    val name=cleanAlbumName(rawName);val now=System.currentTimeMillis()
    val id=writableDatabase.insertOrThrow("media_albums",null,ContentValues().apply {
        put("name",name);put("created",now);put("updated",now)
    })
    history("Gallery album created","$name. No media moved.")
    id
}

fun Store.addMediaToAlbum(albumId: Long, identities: Collection<String>) = synchronized(this) {
    val ids=identities.distinct();require(ids.isNotEmpty()) { "Select at least one media item." }
    val db=writableDatabase;db.beginTransaction()
    try {
        val album=db.rawQuery("SELECT name FROM media_albums WHERE id=?",arrayOf(albumId.toString())).use {
            require(it.moveToFirst()) { "Album no longer exists." };it.getString(0)
        }
        val now=System.currentTimeMillis()
        ids.forEach { id ->
            val available=db.rawQuery("SELECT 1 FROM media WHERE identity=? AND available=1",arrayOf(id)).use { it.moveToFirst() }
            require(available) { "A selected media item is unavailable. Refresh Gallery." }
            db.insertWithOnConflict("media_album_items",null,ContentValues().apply {
                put("album_id",albumId);put("identity",id);put("added",now)
            },SQLiteDatabase.CONFLICT_IGNORE)
        }
        db.update("media_albums",ContentValues().apply { put("updated",now) },"id=?",arrayOf(albumId.toString()))
        history("Added to Gallery album","$album · ${ids.size} ${if(ids.size==1) "item" else "items"}. Files unchanged.")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

fun Store.albumMediaIds(albumId: Long): Set<String> = synchronized(this) {
    readableDatabase.rawQuery("""SELECT i.identity FROM media_album_items i JOIN media m ON m.identity=i.identity
        WHERE i.album_id=? AND m.available=1 ORDER BY i.added DESC""",arrayOf(albumId.toString())).use { c ->
        buildSet { while(c.moveToNext()) add(c.getString(0)) }
    }
}

fun Store.removeMediaFromAlbum(albumId: Long, identities: Collection<String>) = synchronized(this) {
    val ids=identities.distinct();require(ids.isNotEmpty()) { "Select at least one media item." }
    val db=writableDatabase;db.beginTransaction()
    try {
        val album=db.rawQuery("SELECT name FROM media_albums WHERE id=?",arrayOf(albumId.toString())).use {
            require(it.moveToFirst()) { "Album no longer exists." };it.getString(0)
        }
        ids.forEach { db.delete("media_album_items","album_id=? AND identity=?",arrayOf(albumId.toString(),it)) }
        db.update("media_albums",ContentValues().apply { put("updated",System.currentTimeMillis()) },"id=?",arrayOf(albumId.toString()))
        history("Removed from Gallery album","$album · ${ids.size} ${if(ids.size==1) "item" else "items"}. Files unchanged.")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

fun Store.renameGalleryAlbum(albumId: Long, rawName: String) = synchronized(this) {
    val name=cleanAlbumName(rawName);val db=writableDatabase;val now=System.currentTimeMillis()
    val old=db.rawQuery("SELECT name FROM media_albums WHERE id=?",arrayOf(albumId.toString())).use {
        require(it.moveToFirst()) { "Album no longer exists." };it.getString(0)
    }
    db.update("media_albums",ContentValues().apply { put("name",name);put("updated",now) },"id=?",arrayOf(albumId.toString()))
    history("Gallery album renamed","$old → $name. Files unchanged.")
}

fun Store.deleteGalleryAlbum(albumId: Long) = synchronized(this) {
    val db=writableDatabase
    val album=db.rawQuery("SELECT name FROM media_albums WHERE id=?",arrayOf(albumId.toString())).use {
        require(it.moveToFirst()) { "Album no longer exists." };it.getString(0)
    }
    db.delete("media_albums","id=?",arrayOf(albumId.toString()))
    history("Gallery album deleted","$album. Media stayed in place and remains in Gallery.")
}
