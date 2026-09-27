package app.filemate

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class HubApp(val packageName: String, val label: String)
data class DetectedFile(val id: Long, val name: String, val path: String, val size: Long,
    val time: Long, val source: String?, val confidence: String, val reason: String, val via: String)
data class HistoryItem(val title: String, val detail: String, val time: Long)

class Store(context: Context) : SQLiteOpenHelper(context, "filemate.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE hub(package TEXT PRIMARY KEY,label TEXT NOT NULL)")
        db.execSQL("CREATE TABLE files(path TEXT PRIMARY KEY,size INTEGER NOT NULL,modified INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE observations(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,path TEXT NOT NULL,size INTEGER NOT NULL,detected INTEGER NOT NULL,source TEXT,confidence TEXT NOT NULL,reason TEXT NOT NULL,via TEXT NOT NULL)")
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,detail TEXT NOT NULL,time INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("A non-destructive migration is required") }
    @Synchronized fun hub(): List<HubApp> = readableDatabase.rawQuery("SELECT package,label FROM hub ORDER BY rowid", null).use { c ->
        buildList { while(c.moveToNext()) add(HubApp(c.getString(0),c.getString(1))) }
    }
    @Synchronized fun add(app: HubApp) { writableDatabase.insertWithOnConflict("hub",null,ContentValues().apply {
        put("package",app.packageName);put("label",app.label)
    },SQLiteDatabase.CONFLICT_REPLACE) }
    @Synchronized fun remove(app: HubApp) { writableDatabase.delete("hub","package=?",arrayOf(app.packageName)) }
    @Synchronized fun state(key: String): String? = readableDatabase.rawQuery("SELECT value FROM state WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else null }
    @Synchronized fun state(key: String, value: String) { writableDatabase.insertWithOnConflict("state",null,ContentValues().apply { put("key",key);put("value",value) },SQLiteDatabase.CONFLICT_REPLACE) }
    @Synchronized fun history(title: String, detail: String) { writableDatabase.insertOrThrow("history",null,ContentValues().apply {
        put("title",title);put("detail",detail);put("time",System.currentTimeMillis())
    }) }
    @Synchronized fun history(): List<HistoryItem> = readableDatabase.rawQuery("SELECT title,detail,time FROM history ORDER BY id DESC LIMIT 200",null).use { c -> buildList { while(c.moveToNext()) add(HistoryItem(c.getString(0),c.getString(1),c.getLong(2))) } }
    @Synchronized fun recent(): List<DetectedFile> = readableDatabase.rawQuery("SELECT id,name,path,size,detected,source,confidence,reason,via FROM observations ORDER BY id DESC LIMIT 200",null).use { c ->
        buildList { while(c.moveToNext()) add(DetectedFile(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getString(5),c.getString(6),c.getString(7),c.getString(8))) }
    }
    /** Deduplication and record insertion are one transaction; catch-up and live events may race. */
    @Synchronized fun observe(file: File, finding: Finding, via: String, baseline: Boolean = false): Boolean {
        if (!file.isFile || FileRules.temporary(file.name)) return false
        val db = writableDatabase
        val stamp = FileStamp(file.length(),file.lastModified())
        db.beginTransaction()
        try {
            val previous = db.rawQuery("SELECT size,modified FROM files WHERE path=?",arrayOf(file.absolutePath)).use {
                if(it.moveToFirst()) FileStamp(it.getLong(0),it.getLong(1)) else null
            }
            if(!FileRules.changed(previous,stamp)) { db.setTransactionSuccessful();return false }
            db.insertWithOnConflict("files",null,ContentValues().apply {
                put("path",file.absolutePath);put("size",stamp.size);put("modified",stamp.modified)
            },SQLiteDatabase.CONFLICT_REPLACE)
            // Baseline must never overwrite or remove any existing detection records.
            if(!baseline && finding.candidate) db.insertOrThrow("observations",null,ContentValues().apply {
                put("name",file.name);put("path",file.absolutePath);put("size",stamp.size);put("detected",System.currentTimeMillis())
                put("source",finding.source);put("confidence",finding.confidence);put("reason",finding.reason);put("via",via)
            })
            if(!baseline && !finding.candidate) {
                val count = (state("ignored")?.toLongOrNull() ?: 0) + 1
                state("ignored",count.toString())
            }
            db.setTransactionSuccessful()
            return !baseline && finding.candidate
        } finally { db.endTransaction() }
    }
}
