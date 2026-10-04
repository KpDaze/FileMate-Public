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

class Store(context: Context, databaseName: String = "filemate.db") : SQLiteOpenHelper(context, databaseName, null, 8) {
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
        createCleanupTables(db)
        createFileActionTable(db)
        createGalleryTables(db)
        createGalleryOrganisationTables(db)
        createProjectLearningTable(db)
        createDriveRuleTable(db)
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
            if(old < 3) createCleanupTables(db)
            if(old < 4) createFileActionTable(db)
            if(old < 5) createGalleryTables(db)
            if(old < 6) createGalleryOrganisationTables(db)
            if(old < 7) createProjectLearningTable(db)
            if(old < 8) createDriveRuleTable(db)
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
        SELECT COALESCE(o.id,-f.rowid),COALESCE(o.name,c.name,f.path),f.path,f.size,
               COALESCE(o.detected,c.modified,f.modified),o.source,COALESCE(o.confidence,'Unknown'),
               COALESCE(o.reason,'Found during a deliberate phone scan'),COALESCE(o.via,'Phone scan'),
               f.project_id,p.name,f.project_confidence
        FROM files f
        LEFT JOIN projects p ON p.id=f.project_id
        LEFT JOIN observations o ON o.id=(SELECT MAX(latest.id) FROM observations latest WHERE latest.path=f.path)
        LEFT JOIN cleanup_entries c ON c.path=f.path
        WHERE f.project_id IS NULL
          AND (o.id IS NOT NULL OR c.path IS NOT NULL)
        ORDER BY CASE COALESCE(o.confidence,'Unknown') WHEN 'Low' THEN 0 WHEN 'Unknown' THEN 1 WHEN 'Medium' THEN 2 ELSE 3 END,
                 COALESCE(o.detected,c.modified,f.modified) DESC
    """.trimIndent(),null).use { c -> buildList {
        while(c.moveToNext()) add(detectedFile(c))
    } }
    @Synchronized fun projects(): List<Project> = readableDatabase.rawQuery("""
        SELECT p.id,p.name,p.created,p.updated,
            (SELECT COUNT(*) FROM files f WHERE f.project_id=p.id AND NOT EXISTS
                (SELECT 1 FROM media m WHERE m.current_path=f.path OR m.original_path=f.path)) +
            (SELECT COUNT(*) FROM media m WHERE m.project_id=p.id AND m.available=1)
        FROM projects p ORDER BY p.updated DESC,p.name COLLATE NOCASE
    """.trimIndent(),null).use { c -> buildList {
        while(c.moveToNext()) add(Project(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getInt(4)))
    } }
    @Synchronized fun projectFiles(projectId: Long): List<DetectedFile> = readableDatabase.rawQuery("""
        SELECT COALESCE(o.id,-f.rowid),COALESCE(o.name,c.name,f.path),f.path,f.size,
               COALESCE(o.detected,c.modified,f.modified),o.source,COALESCE(o.confidence,'Unknown'),
               COALESCE(o.reason,'Added from a reviewed phone scan'),COALESCE(o.via,'Phone scan'),
               f.project_id,p.name,f.project_confidence
        FROM files f
        JOIN projects p ON p.id=f.project_id
        LEFT JOIN observations o ON o.id=(SELECT MAX(latest.id) FROM observations latest WHERE latest.path=f.path)
        LEFT JOIN cleanup_entries c ON c.path=f.path
        WHERE f.project_id=? AND NOT EXISTS (SELECT 1 FROM media m WHERE m.current_path=f.path OR m.original_path=f.path)
        ORDER BY COALESCE(o.detected,c.modified,f.modified) DESC
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
            db.execSQL("UPDATE cleanup_entries SET flags=flags | ? WHERE root='Downloads' AND path IN (SELECT path FROM files WHERE project_id=?)",arrayOf(CleanupFlags.UNSORTED_DOWNLOAD,id))
            db.update("files",ContentValues().apply { put("project_confidence","Unassigned") },"project_id=?",arrayOf(id.toString()))
            db.execSQL("UPDATE media SET project_confidence='Unassigned' WHERE project_id=?",arrayOf(id))
            db.delete("projects","id=?",arrayOf(id.toString()))
            refreshCleanupCounts(db)
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
            distinctPaths.forEach { path ->
                var updated = db.update("files",values,"path=?",arrayOf(path))
                if(updated == 0) {
                    val indexed = db.rawQuery("SELECT size,modified FROM cleanup_entries WHERE path=?",arrayOf(path)).use {
                        if(it.moveToFirst()) FileStamp(it.getLong(0),it.getLong(1)) else null
                    }
                    if(indexed != null) {
                        db.insertOrThrow("files",null,ContentValues().apply {
                            put("path",path);put("size",indexed.size);put("modified",indexed.modified)
                            put("project_id",projectId);put("project_confidence","Confirmed")
                        })
                        updated = 1
                    }
                }
                if(updated > 0) db.update("media",values,"current_path=? AND available=1",arrayOf(path))
                changed += updated
            }
            require(changed > 0) { "The selected files are no longer available" }
            val now = System.currentTimeMillis()
            db.update("projects",ContentValues().apply { put("updated",now) },"id=?",arrayOf(projectId.toString()))
            distinctPaths.forEach { path ->
                db.execSQL("UPDATE cleanup_entries SET flags=flags & ? WHERE path=?",arrayOf(CleanupFlags.UNSORTED_DOWNLOAD.inv(),path))
                learnProjectTokens(db,path,projectId)
            }
            refreshCleanupCounts(db)
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
                db.execSQL("UPDATE media SET project_id=NULL,project_confidence='Unassigned' WHERE current_path=? AND available=1",arrayOf(path))
                db.execSQL("UPDATE cleanup_entries SET flags=flags | ? WHERE path=? AND root='Downloads'",arrayOf(CleanupFlags.UNSORTED_DOWNLOAD,path))
            }
            require(changed > 0) { "The selected files were already unassigned" }
            refreshCleanupCounts(db)
            insertHistory(db,"Returned to Needs Sorting",if(changed == 1) "1 file" else "$changed files",System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun projectStorageRule(projectId: Long): StorageRule = readableDatabase.rawQuery(
        "SELECT rule FROM project_storage_rules WHERE project_id=?",arrayOf(projectId.toString())
    ).use { if(it.moveToFirst()) DriveRules.parse(it.getString(0)) else StorageRule.PHONE_ONLY }

    @Synchronized fun setProjectStorageRule(projectId: Long, rule: StorageRule) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(db.rawQuery("SELECT 1 FROM projects WHERE id=?",arrayOf(projectId.toString())).use { it.moveToFirst() }) { "Project no longer exists" }
            db.insertWithOnConflict("project_storage_rules",null,ContentValues().apply {
                put("project_id",projectId);put("rule",rule.name)
            },SQLiteDatabase.CONFLICT_REPLACE)
            insertHistory(db,"Project storage rule changed","Future files for this project: ${rule.label}. Existing files were not moved.",System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized fun assignedPaths(): Set<String> = readableDatabase.rawQuery("SELECT path FROM files WHERE project_id IS NOT NULL",null).use { c -> buildSet {
        while(c.moveToNext()) add(c.getString(0))
    } }
    @Synchronized fun knownAiPaths(): Set<String> = readableDatabase.rawQuery("SELECT DISTINCT path FROM observations",null).use { c -> buildSet {
        while(c.moveToNext()) add(c.getString(0))
    } }
    @Synchronized fun selectedFolders(): List<SelectedFolder> = readableDatabase.rawQuery("SELECT uri,name FROM selected_folders ORDER BY added,name",null).use { c -> buildList {
        while(c.moveToNext()) add(SelectedFolder(c.getString(0),c.getString(1)))
    } }
    @Synchronized fun addSelectedFolder(folder: SelectedFolder) {
        writableDatabase.insertWithOnConflict("selected_folders",null,ContentValues().apply {
            put("uri",folder.uri);put("name",folder.name);put("added",System.currentTimeMillis())
        },SQLiteDatabase.CONFLICT_REPLACE)
        history("Folder added","${folder.name} can be included in deliberate phone scans.")
    }
    @Synchronized fun removeSelectedFolder(uri: String) {
        val db = writableDatabase
        val name = db.rawQuery("SELECT name FROM selected_folders WHERE uri=?",arrayOf(uri)).use { if(it.moveToFirst()) it.getString(0) else null }
        if(db.delete("selected_folders","uri=?",arrayOf(uri)) > 0) history("Folder removed","${name ?: "Selected folder"} is no longer included in scans.")
    }
    @Synchronized fun saveCleanupScan(entries: List<CleanupEntry>, summary: CleanupSummary) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val scanId = db.insertOrThrow("cleanup_scans",null,ContentValues().apply {
                put("completed",summary.completed);put("total_files",summary.totalFiles);put("total_bytes",summary.totalBytes)
                put("likely_ai",summary.likelyAi);put("unsorted_downloads",summary.unsortedDownloads)
                put("large_files",summary.large);put("old_files",summary.old);put("archives",summary.archives)
                put("duplicate_groups",summary.duplicateGroups);put("duplicate_files",summary.duplicateFiles)
                put("reclaimable_bytes",summary.reclaimableBytes)
            })
            db.delete("cleanup_entries",null,null)
            entries.forEach { entry -> db.insertOrThrow("cleanup_entries",null,ContentValues().apply {
                put("path",entry.path);put("name",entry.name);put("size",entry.size);put("modified",entry.modified)
                put("root",entry.root);put("flags",entry.flags);put("hash",entry.hash);put("scan_id",scanId)
            }) }
            insertHistory(db,"Phone scan complete","${summary.totalFiles} files reviewed in place. No files changed.",summary.completed)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun cleanupSummary(): CleanupSummary? = readableDatabase.rawQuery("""
        SELECT id,completed,total_files,total_bytes,likely_ai,unsorted_downloads,large_files,old_files,
               archives,duplicate_groups,duplicate_files,reclaimable_bytes
        FROM cleanup_scans ORDER BY id DESC LIMIT 1
    """.trimIndent(),null).use { c -> if(c.moveToFirst()) CleanupSummary(
        c.getLong(0),c.getLong(1),c.getInt(2),c.getLong(3),c.getInt(4),c.getInt(5),c.getInt(6),
        c.getInt(7),c.getInt(8),c.getInt(9),c.getInt(10),c.getLong(11)
    ) else null }
    @Synchronized fun cleanupEntries(flag: Int = 0): List<CleanupEntry> {
        val where = if(flag == 0) "" else "WHERE (flags & ?) != 0"
        val args = if(flag == 0) null else arrayOf(flag.toString())
        return readableDatabase.rawQuery("SELECT path,name,size,modified,root,flags,hash FROM cleanup_entries $where ORDER BY size DESC,modified DESC",args).use { c -> buildList {
            while(c.moveToNext()) add(CleanupEntry(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4),c.getInt(5),c.getString(6)))
        } }
    }
    @Synchronized fun beginFileAction(plan: OrganisePlan): Long {
        val db = writableDatabase
        val previous = db.rawQuery("SELECT project_id,project_confidence FROM files WHERE path=?",arrayOf(plan.sourcePath)).use {
            if(it.moveToFirst()) (if(it.isNull(0)) null else it.getLong(0)) to it.getString(1) else null to "Unassigned"
        }
        return db.insertOrThrow("file_actions",null,ContentValues().apply {
            put("source_path",plan.sourcePath);put("target_path",plan.targetPath)
            put("source_name",plan.sourceName);put("target_name",plan.targetName);put("source_root",plan.sourceRoot)
            put("expected_size",plan.expectedSize);put("expected_modified",plan.expectedModified);put("hash",plan.hash)
            put("project_id",plan.projectId);put("project_name",plan.projectName)
            if(previous.first == null) putNull("previous_project_id") else put("previous_project_id",previous.first)
            put("previous_project_confidence",previous.second);put("created",System.currentTimeMillis());put("status","pending")
        })
    }
    @Synchronized fun completeFileAction(id: Long, size: Long, modified: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val action = fileAction(db,id) ?: throw IllegalArgumentException("File action no longer exists")
            if(action.status == "applied") { db.setTransactionSuccessful();return }
            require(action.status == "pending" || action.status == "review") { "File action cannot be completed" }
            db.delete("files","path=?",arrayOf(action.targetPath))
            db.delete("files","path=?",arrayOf(action.sourcePath))
            db.insertOrThrow("files",null,ContentValues().apply {
                put("path",action.targetPath);put("size",size);put("modified",modified)
                if(action.projectId == null) { putNull("project_id");put("project_confidence","Unassigned") }
                else { put("project_id",action.projectId);put("project_confidence","Confirmed") }
            })
            db.execSQL("UPDATE media SET available=0 WHERE current_path=?",arrayOf(action.sourcePath))
            db.update("observations",ContentValues().apply { put("path",action.targetPath);put("name",action.targetName) },"path=?",arrayOf(action.sourcePath))
            db.delete("cleanup_entries","path=?",arrayOf(action.targetPath))
            db.execSQL("UPDATE cleanup_entries SET path=?,name=?,root='Documents',flags=flags & ? WHERE path=?",arrayOf(action.targetPath,action.targetName,CleanupFlags.UNSORTED_DOWNLOAD.inv(),action.sourcePath))
            db.update("file_actions",ContentValues().apply { put("status","applied");putNull("error") },"id=?",arrayOf(id.toString()))
            refreshCleanupCounts(db)
            insertHistory(db,"File organised","${action.sourceName} → ${action.targetName} in ${action.projectName}",System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun completeFileUndo(id: Long, size: Long, modified: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val action = fileAction(db,id) ?: throw IllegalArgumentException("File action no longer exists")
            require(action.status == "undo_pending") { "File action is not available to undo" }
            db.delete("files","path=?",arrayOf(action.sourcePath))
            db.delete("files","path=?",arrayOf(action.targetPath))
            db.insertOrThrow("files",null,ContentValues().apply {
                put("path",action.sourcePath);put("size",size);put("modified",modified)
                if(action.previousProjectId == null) { putNull("project_id");put("project_confidence","Unassigned") }
                else { put("project_id",action.previousProjectId);put("project_confidence",action.previousProjectConfidence) }
            })
            db.execSQL("UPDATE media SET available=0 WHERE current_path=?",arrayOf(action.targetPath))
            db.update("observations",ContentValues().apply { put("path",action.sourcePath);put("name",action.sourceName) },"path=?",arrayOf(action.targetPath))
            db.delete("cleanup_entries","path=?",arrayOf(action.sourcePath))
            db.execSQL("UPDATE cleanup_entries SET path=?,name=?,root=? WHERE path=?",arrayOf(action.sourcePath,action.sourceName,action.sourceRoot,action.targetPath))
            if(action.previousProjectId == null && action.sourceRoot == "Downloads") db.execSQL("UPDATE cleanup_entries SET flags=flags | ? WHERE path=?",arrayOf(CleanupFlags.UNSORTED_DOWNLOAD,action.sourcePath))
            db.update("file_actions",ContentValues().apply { put("status","undone");putNull("error") },"id=?",arrayOf(id.toString()))
            refreshCleanupCounts(db)
            insertHistory(db,"File change undone","${action.targetName} returned to its original location.",System.currentTimeMillis())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun failFileAction(id: Long, message: String) { updateFileActionStatus(id,"failed",message) }
    @Synchronized fun reviewFileAction(id: Long, message: String) { updateFileActionStatus(id,"review",message) }
    @Synchronized fun beginFileUndo(id: Long) {
        require(writableDatabase.update("file_actions",ContentValues().apply { put("status","undo_pending");putNull("error") },"id=? AND status='applied'",arrayOf(id.toString())) == 1) { "File action is not available to undo" }
    }
    @Synchronized fun cancelFileUndo(id: Long, message: String) { updateFileActionStatus(id,"applied",message) }
    // Review is terminal until a deliberate future recovery UI: never reinterpret an
    // interrupted Undo as a forward move, or trust a replacement file on later restarts.
    @Synchronized fun pendingFileActions(): List<FileActionRecord> = fileActions("WHERE status IN ('pending','undo_pending')")
    @Synchronized fun fileAction(id: Long): FileActionRecord? = fileAction(readableDatabase,id)
    @Synchronized fun fileActions(): List<FileActionRecord> = fileActions("WHERE status IN ('applied','undone','review','undo_pending') ORDER BY id DESC LIMIT 100")
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
    @Synchronized fun learnProjectAssignment(fileName: String, projectId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            learningTokens(fileName).forEach { token ->
                db.execSQL("""INSERT INTO project_learning(token,project_id,hits) VALUES(?,?,1)
                    ON CONFLICT(token,project_id) DO UPDATE SET hits=hits+1""",arrayOf(token,projectId))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized fun learnedProject(fileName: String): ProjectMatch? {
        val tokens = learningTokens(fileName)
        if(tokens.isEmpty()) return null
        val marks = tokens.joinToString(",") { "?" }
        val scores = readableDatabase.rawQuery("""
            SELECT l.project_id,p.name,SUM(l.hits) AS score,COUNT(DISTINCT l.token) AS matched
            FROM project_learning l JOIN projects p ON p.id=l.project_id
            WHERE l.token IN ($marks)
            GROUP BY l.project_id,p.name
            ORDER BY score DESC,matched DESC,p.name COLLATE NOCASE
        """.trimIndent(),tokens.toTypedArray()).use { c -> buildList {
            while(c.moveToNext()) add(ProjectMatch(c.getLong(0),c.getString(1),"Medium","Learned from earlier manual assignments.") to (c.getInt(2) to c.getInt(3)))
        } }
        val best = scores.firstOrNull() ?: return null
        val runner = scores.getOrNull(1)
        if(best.second.second < 2 || best.second.first < 2) return null
        if(runner != null && runner.second == best.second) return null
        return best.first
    }

    private fun createDriveRuleTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_storage_rules(project_id INTEGER PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,rule TEXT NOT NULL DEFAULT 'PHONE_ONLY')")
    }

    private fun createProjectLearningTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS project_learning(token TEXT NOT NULL,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,hits INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(token,project_id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS project_learning_project ON project_learning(project_id)")
    }
    private fun learningTokens(name: String): List<String> = ProjectLearning.tokens(name)
    private fun learnProjectTokens(db: SQLiteDatabase, path: String, projectId: Long) {
        learningTokens(File(path).name).forEach { token ->
            db.execSQL("""INSERT INTO project_learning(token,project_id,hits) VALUES(?,?,1)
                ON CONFLICT(token,project_id) DO UPDATE SET hits=hits+1""",arrayOf(token,projectId))
        }
    }

    private fun createCleanupTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cleanup_scans(id INTEGER PRIMARY KEY AUTOINCREMENT,completed INTEGER NOT NULL,total_files INTEGER NOT NULL,total_bytes INTEGER NOT NULL,likely_ai INTEGER NOT NULL,unsorted_downloads INTEGER NOT NULL,large_files INTEGER NOT NULL,old_files INTEGER NOT NULL,archives INTEGER NOT NULL,duplicate_groups INTEGER NOT NULL,duplicate_files INTEGER NOT NULL,reclaimable_bytes INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE cleanup_entries(path TEXT PRIMARY KEY,name TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,root TEXT NOT NULL,flags INTEGER NOT NULL,hash TEXT,scan_id INTEGER NOT NULL REFERENCES cleanup_scans(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX cleanup_entries_flags ON cleanup_entries(flags)")
        db.execSQL("CREATE INDEX cleanup_entries_hash ON cleanup_entries(hash)")
        db.execSQL("CREATE TABLE selected_folders(uri TEXT PRIMARY KEY,name TEXT NOT NULL,added INTEGER NOT NULL)")
    }
    private fun createFileActionTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE file_actions(id INTEGER PRIMARY KEY AUTOINCREMENT,source_path TEXT NOT NULL,target_path TEXT NOT NULL,source_name TEXT NOT NULL,target_name TEXT NOT NULL,source_root TEXT NOT NULL,expected_size INTEGER NOT NULL,expected_modified INTEGER NOT NULL,hash TEXT,project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,project_name TEXT NOT NULL,previous_project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,previous_project_confidence TEXT NOT NULL,created INTEGER NOT NULL,status TEXT NOT NULL,error TEXT)")
        db.execSQL("CREATE INDEX file_actions_status ON file_actions(status)")
    }
    private fun fileActions(where: String): List<FileActionRecord> = readableDatabase.rawQuery("""
        SELECT id,source_path,target_path,source_name,target_name,source_root,expected_size,expected_modified,
               hash,project_id,project_name,previous_project_id,previous_project_confidence,created,status,error
        FROM file_actions $where
    """.trimIndent(),null).use { c -> buildList { while(c.moveToNext()) add(fileAction(c)) } }
    private fun fileAction(db: SQLiteDatabase, id: Long): FileActionRecord? = db.rawQuery("""
        SELECT id,source_path,target_path,source_name,target_name,source_root,expected_size,expected_modified,
               hash,project_id,project_name,previous_project_id,previous_project_confidence,created,status,error
        FROM file_actions WHERE id=?
    """.trimIndent(),arrayOf(id.toString())).use { if(it.moveToFirst()) fileAction(it) else null }
    private fun fileAction(c: android.database.Cursor) = FileActionRecord(
        c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6),c.getLong(7),c.getString(8),
        if(c.isNull(9)) null else c.getLong(9),c.getString(10),if(c.isNull(11)) null else c.getLong(11),c.getString(12),c.getLong(13),c.getString(14),c.getString(15)
    )
    private fun updateFileActionStatus(id: Long, status: String, message: String) {
        writableDatabase.update("file_actions",ContentValues().apply { put("status",status);put("error",message) },"id=?",arrayOf(id.toString()))
    }
    internal fun refreshCleanupCounts(db: SQLiteDatabase) {
        val id = db.rawQuery("SELECT MAX(id) FROM cleanup_scans",null).use { if(it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } ?: return
        fun count(flag: Int) = db.rawQuery("SELECT COUNT(*) FROM cleanup_entries WHERE (flags & ?) != 0",arrayOf(flag.toString())).use { it.moveToFirst();it.getInt(0) }
        db.update("cleanup_scans",ContentValues().apply {
            put("likely_ai",count(CleanupFlags.LIKELY_AI));put("unsorted_downloads",count(CleanupFlags.UNSORTED_DOWNLOAD))
            put("large_files",count(CleanupFlags.LARGE));put("old_files",count(CleanupFlags.OLD));put("archives",count(CleanupFlags.ARCHIVE))
            put("duplicate_files",count(CleanupFlags.DUPLICATE))
        },"id=?",arrayOf(id.toString()))
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
