package app.filemate

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** One record per reviewed file, replaced on correction: repeated taps never add votes. */
internal fun createLearningExamples(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS learning_examples(
        path TEXT PRIMARY KEY,name TEXT NOT NULL,project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL,
        source TEXT,source_reviewed INTEGER NOT NULL DEFAULT 0,hash TEXT,size INTEGER,modified INTEGER)""")
}
internal fun seedReviewedProjects(db: SQLiteDatabase) {
    // Old automatic moves were also labelled Confirmed. Exclude all action paths and camera media.
    db.execSQL("""INSERT OR IGNORE INTO learning_examples(path,name,project_id)
        SELECT f.path,COALESCE((SELECT m.name FROM media m WHERE m.current_path=f.path LIMIT 1),
          (SELECT o.name FROM observations o WHERE o.path=f.path ORDER BY o.id DESC LIMIT 1),f.path),f.project_id
        FROM files f WHERE f.project_confidence='Confirmed' AND f.project_id IS NOT NULL
        AND NOT EXISTS(SELECT 1 FROM file_actions a WHERE a.source_path=f.path OR a.target_path=f.path)
        AND NOT EXISTS(SELECT 1 FROM media m WHERE m.current_path=f.path AND m.camera=1)""")
}
fun Store.learningExamples(): List<LearningExample> = synchronized(this) {
    readableDatabase.rawQuery("SELECT path,name,project_id,source,source_reviewed FROM learning_examples",null).use { c -> buildList {
        while(c.moveToNext()) add(LearningExample(c.getString(0),c.getString(1).substringAfterLast('/'),
            if(c.isNull(2)) null else c.getLong(2),c.getString(3),c.getInt(4)==1))
    } }
}
fun Store.learnReviewedProject(path: String, name: String, projectId: Long?) = synchronized(this) {
    val values=ContentValues().apply { if(projectId==null) putNull("project_id") else put("project_id",projectId) }
    if(writableDatabase.update("learning_examples",values,"path=?",arrayOf(path))==0) {
        values.put("path",path);values.put("name",name);writableDatabase.insertOrThrow("learning_examples",null,values)
    }
}
fun Store.relocateLearning(from: String, to: String) = synchronized(this) {
    // Keep reviewed-name provenance; generated tidy names must not train their own project prefix.
    writableDatabase.delete("learning_examples","path=?",arrayOf(to))
    writableDatabase.execSQL("UPDATE learning_examples SET path=? WHERE path=?",arrayOf(to,from))
}
fun Store.learnedProject(name: String): ProjectMatch? = synchronized(this) {
    ProjectRules.suggest(name,projects(),learningExamples())
}
fun Store.classifyFile(file: File, context: AiContext?, now: Long): Finding = synchronized(this) {
    val corrected = readableDatabase.rawQuery("SELECT source,hash,size,modified FROM learning_examples WHERE path=? AND source_reviewed=1",arrayOf(file.absolutePath)).use { c ->
        if(c.moveToFirst() && file.length()==c.getLong(2) && file.lastModified()==c.getLong(3) && ContentFingerprint.matches(file,c.getLong(2),c.getString(1))) c.getString(0) else null
    }
    if(corrected!=null) Finding(corrected!="Not AI",corrected.takeUnless { it=="Not AI" },if(corrected=="Not AI") "Not AI" else "Confirmed","Source reviewed by you. No automatic move follows a correction.")
    else FileRules.classify(file.name,context,now,file.parent.orEmpty(),ProjectLearning.source(file.name,learningExamples()))
}
fun Store.correctSource(path: String, source: String) = synchronized(this) {
    require(source in FileRules.providers || source=="Not AI") { "Choose a source" }
    val file=File(path)
    val stamp=ContentFingerprint.read(file) ?: error("This file changed or is unavailable. Refresh before correcting it.")
    val db=writableDatabase;db.beginTransaction()
    try {
        val values=ContentValues().apply {
            put("source",source);put("source_reviewed",1)
            put("hash",stamp.hash);put("size",stamp.size);put("modified",stamp.modified)
        }
        if(db.update("learning_examples",values,"path=?",arrayOf(path))==0) {
            values.put("path",path);values.put("name",file.name);db.insertOrThrow("learning_examples",null,values)
        }
        db.update("observations",ContentValues().apply {
            put("source",source.takeUnless { it=="Not AI" });put("confidence",if(source=="Not AI") "Not AI" else "Confirmed")
            put("reason","Source reviewed by you. Similar filename patterns can now suggest a source; they cannot trigger moves.")
        },"path=?",arrayOf(path))
        if(source=="Not AI") db.execSQL("UPDATE cleanup_entries SET flags=flags & ? WHERE path=?",arrayOf(CleanupFlags.LIKELY_AI.inv(),path))
        else db.execSQL("UPDATE cleanup_entries SET flags=flags | ? WHERE path=?",arrayOf(CleanupFlags.LIKELY_AI,path))
        refreshCleanupCounts(db)
        history("Source corrected","${file.name}: $source. File unchanged.")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
fun Store.knownPath(path: String): Boolean = synchronized(this) {
    readableDatabase.rawQuery("SELECT 1 FROM files WHERE path=? UNION ALL SELECT 1 FROM file_actions WHERE source_path=? OR target_path=? LIMIT 1",arrayOf(path,path,path)).use { it.moveToFirst() }
}

/** Catch-up may have indexed this new live file; a review or prior action still blocks automation. */
fun Store.eligibleAfterLiveEvent(path: String): Boolean = synchronized(this) {
    !readableDatabase.rawQuery("""SELECT 1 FROM files WHERE path=? AND project_id IS NOT NULL
        UNION ALL SELECT 1 FROM file_actions WHERE source_path=? OR target_path=?
        UNION ALL SELECT 1 FROM learning_examples WHERE path=? LIMIT 1""",arrayOf(path,path,path,path)).use { it.moveToFirst() }
}
