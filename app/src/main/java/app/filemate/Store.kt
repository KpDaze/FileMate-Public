package app.filemate

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class HubApp(val packageName: String, val label: String)
data class DetectedFile(val id: Long, val name: String, val path: String, val size: Long,
    val time: Long, val source: String?, val confidence: String, val reason: String, val via: String,
    val projectId: Long? = null, val projectName: String? = null,
    val projectConfidence: String = "Unassigned")
data class HistoryItem(val title: String, val detail: String, val time: Long)
data class Project(val id: Long, val name: String, val created: Long, val updated: Long,
    val fileCount: Int)

class Store(context: Context) : SQLiteOpenHelper(context, "filemate.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE hub(package TEXT PRIMARY KEY,label TEXT NOT NULL)")
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL COLLATE NOCASE UNIQUE,created INTEGER NOT NULL,updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE files(path TEXT PRIMARY KEY,size INTEGER NOT NULL,modified INTEGER NOT NULL,project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,project_confidence TEXT NOT NULL DEFAULT 'Unassigned')")
        db.execSQL("CREATE INDEX files_project_id ON files(project_id)")
        db.execSQL("CREATE TABLE observations(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,path TEXT NOT NULL,size INTEGER NOT NULL,detected INTEGER NOT NULL,source TEXT,confidence TEXT NOT NULL,reason TEXT NOT NULL,via TEXT NOT NULL)")
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,detail TEXT NOT NULL,time INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.beginTransaction()
        try {
            if(old < 2) {
                db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL COLLATE NOCASE UNIQUE,created INTEGER NOT NULL,updated INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE files ADD COLUMN project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL")
                db.execSQL("ALTER TABLE files ADD COLUMN project_confidence TEXT NOT NULL DEFAULT 'Unassigned'")
                db.execSQL("CREATE INDEX files_project_id ON files(project_id)")
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
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
    @Synchronized fun recent(): List<DetectedFile> = readableDatabase.rawQuery("""
        SELECT o.id,o.name,o.path,o.size,o.detected,o.source,o.confidence,o.reason,o.via,
               f.project_id,p.name,f.project_confidence
        FROM observations o
        LEFT JOIN files f ON f.path=o.path
        LEFT JOIN projects p ON p.id=f.project_id
        ORDER BY o.id DESC LIMIT 200
    """.trimIndent(),null).use { c ->
        buildList { while(c.moveToNext()) add(detectedFile(c)) }
    }
    @Synchronized fun needsSorting(): List<DetectedFile> = readableDatabase.rawQuery("""
        SELECT o.id,o.name,o.path,o.size,o.detected,o.source,o.confidence,o.reason,o.via,
               f.project_id,p.name,f.project_confidence
        FROM observations o
        JOIN files f ON f.path=o.path
        LEFT JOIN projects p ON p.id=f.project_id
        WHERE f.project_id IS NULL
          AND o.id=(SELECT MAX(latest.id) FROM observations latest WHERE latest.path=o.path)
        ORDER BY CASE o.confidence WHEN 'Low' THEN 0 WHEN 'Medium' THEN 1 ELSE 2 END,o.detected DESC
    """.trimIndent(),null).use { c -> buildList {
        while(c.moveToNext()) add(detectedFile(c))
    } }
    @Synchronized fun projects(): List<Project> = readableDatabase.rawQuery("""
        SELECT p.id,p.name,p.created,p.updated,COUNT(f.path)
        FROM projects p LEFT JOIN files f ON f.project_id=p.id
        GROUP BY p.id ORDER BY p.updated DESC,p.name COLLATE NOCASE
    """.trimIndent(),null).use { c -> buildList {
        while(c.moveToNext()) add(Project(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getInt(4)))
    } }
    @Synchronized fun projectFiles(projectId: Long): List<DetectedFile> = readableDatabase.rawQuery("""
        SELECT o.id,o.name,o.path,o.size,o.detected,o.source,o.confidence,o.reason,o.via,
               f.project_id,p.name,f.project_confidence
        FROM observations o
        JOIN files f ON f.path=o.path
        JOIN projects p ON p.id=f.project_id
        WHERE f.project_id=? AND o.id=(SELECT MAX(latest.id) FROM observations latest WHERE latest.path=o.path)
        ORDER BY o.detected DESC
    """.trimIndent(),arrayOf(projectId.toString())).use { c -> buildList {
        while(c.moveToNext()) add(detectedFile(c))
    } }
    @Synchronized fun createProject(rawName: String): Long {
        val name = ProjectNames.clean(rawName)
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("projects",null,ContentValues().apply {
                put("name",name);put("created",now);put("updated",now)
            })
            insertHistory(db,"Project created",name,now)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    @Synchronized fun renameProject(id: Long, rawName: String) {
        val name = ProjectNames.clean(rawName)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val oldName = db.rawQuery("SELECT name FROM projects WHERE id=?",arrayOf(id.toString())).use {
                if(it.moveToFirst()) it.getString(0) else throw IllegalArgumentException("Project no longer exists")
            }
            val now = System.currentTimeMillis()
            db.update("projects",ContentValues().apply { put("name",name);put("updated",now) },"id=?",arrayOf(id.toString()))
            insertHistory(db,"Project renamed","$oldName → $name",now)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun deleteProject(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val project = db.rawQuery("""
                SELECT p.name,COUNT(f.path) FROM projects p LEFT JOIN files f ON f.project_id=p.id
                WHERE p.id=? GROUP BY p.id
            """.trimIndent(),arrayOf(id.toString())).use {
                if(it.moveToFirst()) it.getString(0) to it.getInt(1) else throw IllegalArgumentException("Project no longer exists")
            }
            db.update("files",ContentValues().apply { put("project_confidence","Unassigned") },"project_id=?",arrayOf(id.toString()))
            db.delete("projects","id=?",arrayOf(id.toString()))
            val detail = if(project.second == 0) project.first else "${project.first}. ${project.second} files returned to Needs Sorting."
            insertHistory(db,"Project deleted",detail,System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun assignFiles(paths: Collection<String>, projectId: Long) {
        val distinctPaths = paths.distinct()
        require(distinctPaths.isNotEmpty()) { "Choose at least one file" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val projectName = db.rawQuery("SELECT name FROM projects WHERE id=?",arrayOf(projectId.toString())).use {
                if(it.moveToFirst()) it.getString(0) else throw IllegalArgumentException("Project no longer exists")
            }
            var changed = 0
            val values = ContentValues().apply { put("project_id",projectId);put("project_confidence","Confirmed") }
            distinctPaths.forEach { changed += db.update("files",values,"path=?",arrayOf(it)) }
            require(changed > 0) { "The selected files are no longer available" }
            val now = System.currentTimeMillis()
            db.update("projects",ContentValues().apply { put("updated",now) },"id=?",arrayOf(projectId.toString()))
            insertHistory(db,"Assigned to $projectName",if(changed == 1) "1 file" else "$changed files",now)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun unassignFiles(paths: Collection<String>) {
        val distinctPaths = paths.distinct()
        require(distinctPaths.isNotEmpty()) { "Choose at least one file" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            var changed = 0
            distinctPaths.forEach { path ->
                changed += db.update("files",ContentValues().apply {
                    putNull("project_id");put("project_confidence","Unassigned")
                },"path=? AND project_id IS NOT NULL",arrayOf(path))
            }
            require(changed > 0) { "The selected files were already unassigned" }
            insertHistory(db,"Returned to Needs Sorting",if(changed == 1) "1 file" else "$changed files",System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
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
            val values = ContentValues().apply {
                put("path",file.absolutePath);put("size",stamp.size);put("modified",stamp.modified)
            }
            if(previous == null) db.insertOrThrow("files",null,values)
            else db.update("files",values,"path=?",arrayOf(file.absolutePath))
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

    private fun detectedFile(c: android.database.Cursor) = DetectedFile(
        id = c.getLong(0), name = c.getString(1), path = c.getString(2), size = c.getLong(3),
        time = c.getLong(4), source = c.getString(5), confidence = c.getString(6),
        reason = c.getString(7), via = c.getString(8),
        projectId = if(c.isNull(9)) null else c.getLong(9), projectName = c.getString(10),
        projectConfidence = c.getString(11) ?: "Unassigned"
    )
    private fun insertHistory(db: SQLiteDatabase, title: String, detail: String, time: Long) {
        db.insertOrThrow("history",null,ContentValues().apply {
            put("title",title);put("detail",detail);put("time",time)
        })
    }
}

object ProjectNames {
    const val MAX_LENGTH = 80
    fun clean(raw: String): String {
        val name = raw.trim().replace(Regex("\\s+")," ")
        require(name.isNotEmpty()) { "Enter a project name" }
        require(name.length <= MAX_LENGTH) { "Use $MAX_LENGTH characters or fewer" }
        return name
    }
}
