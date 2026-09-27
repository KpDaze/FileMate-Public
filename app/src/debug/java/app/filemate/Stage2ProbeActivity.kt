package app.filemate

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import org.json.JSONObject

class Stage2ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FileMateApp
        val resultFile = File(filesDir,"stage2-probe.json")
        resultFile.delete()
        app.scope.launch {
            var testRoot: File? = null
            var destination: File? = null
            try {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                testRoot = File(downloads,"FileMateStage2Probe-${System.currentTimeMillis()}").apply { check(mkdirs()) }
                val first = File(testRoot,"same.txt").apply { writeText("FileMate stage two duplicate proof") }
                check(first.setLastModified(1_600_000_000_123))
                check(ContentFingerprint.readChecked(first).modified == first.lastModified())
                val second = File(testRoot,"copy.txt").apply { writeText("FileMate stage two duplicate proof") }

                val scan = CleanupScanner(contentResolver).scan(listOf("Downloads" to testRoot),emptyList(),emptySet(),emptySet()) {}
                check(scan.second.totalFiles == 2)
                check(scan.second.duplicateGroups == 1)
                check(scan.second.duplicateFiles == 2)
                check(scan.first.all { it.flags and CleanupFlags.DUPLICATE != 0 })
                app.store.saveCleanupScan(scan.first,scan.second)

                val projectId = app.store.createProject("Stage 2 Probe ${System.currentTimeMillis()}")
                app.store.assignFiles(listOf(first.absolutePath),projectId)
                val project = app.store.projects().single { it.id == projectId }
                check(project.fileCount == 1)

                @Suppress("DEPRECATION")
                val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                destination = File(documents,"FileMate/${FileNaming.folder(project.name)}").apply { check(mkdirs()) }
                val conflict = File(destination,"same.txt").apply { writeText("keep this existing file") }
                val entry = scan.first.single { it.path == first.absolutePath }
                val plan = FileOrganiser(app.store).plans(listOf(entry),project,false).single()
                check(plan.supported) { "Preview refused: ${plan.note}; scanned=$entry; fingerprint=${ContentFingerprint.readChecked(first)}" }
                check(plan.targetName == "same (2).txt")

                val applied = FileOrganiser(app.store).apply(listOf(plan))
                check(applied.applied == 1 && applied.failed == 0) { "Move result: $applied" }
                check(!first.exists() && File(plan.targetPath).isFile)
                check(File(plan.targetPath).lastModified() == plan.expectedModified)
                check(conflict.readText() == "keep this existing file")
                val action = app.store.fileActions().first { it.status == "applied" }
                check(FileOrganiser(app.store).undo(action) == null)
                check(first.isFile && !File(plan.targetPath).exists())
                check(app.store.fileActions().first { it.id == action.id }.status == "undone")
                check(app.store.projectFiles(projectId).single().path == first.absolutePath)

                verifySafeguards(app,testRoot,project,destination)
                resultFile.writeText("""{"passed":true,"files":2,"duplicate_groups":1,"move":true,"undo":true,"conflict_preserved":true,"late_move_collision":true,"late_undo_collision":true,"unique_file_fingerprint":true,"same_size_edit_refused":true,"legacy_undo_refused":true,"interrupted_copy_preserved":true,"recovery_validated":true,"millisecond_timestamp_preserved":true}""")
                second.delete()
                first.delete()
                conflict.delete()
                testRoot.delete()
                destination.delete()
            } catch(e: Throwable) {
                resultFile.writeText(JSONObject().put("passed",false).put("error",e.stackTraceToString()).toString())
            } finally {
                runOnUiThread { finish() }
            }
        }
    }

    private suspend fun verifySafeguards(app: FileMateApp, root: File, project: Project, destination: File) {
        val store = app.store
        val organiser = FileOrganiser(store)
        fun plan(name: String, content: String = "original"): OrganisePlan {
            val source = File(root,name).apply { writeText(content) }
            return organiser.plans(listOf(CleanupEntry(source.path,name,source.length(),source.lastModified(),"Downloads",0)),project,false).single().also {
                check(it.supported && it.hash != null)
            }
        }
        fun action(plan: OrganisePlan) = store.fileActions().first { it.sourcePath == plan.sourcePath }
        fun clear(plan: OrganisePlan) { File(plan.sourcePath).delete();File(plan.targetPath).delete() }

        // A unique-size scan entry has no duplicate hash; preview must fingerprint it anyway.
        val unique = File(root,"unique.txt").apply { writeText("Unique fixture content with a different byte length.") }
        val scan = CleanupScanner(contentResolver).scan(listOf("Downloads" to root),emptyList(),emptySet(),emptySet()) {}
        val entry = scan.first.single { it.path == unique.path }
        check(entry.hash == null)
        val uniquePlan = organiser.plans(listOf(entry),project,false).single()
        check(uniquePlan.supported && uniquePlan.hash != null)
        check(organiser.apply(listOf(uniquePlan)).applied == 1)
        val moved = File(uniquePlan.targetPath)
        val saved = action(uniquePlan)
        check(saved.hash == uniquePlan.hash)
        val originalContent = moved.readBytes()
        val modified = moved.lastModified()
        moved.writeBytes(originalContent.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })
        check(moved.setLastModified(modified))
        check(organiser.undo(saved) != null)
        check(moved.isFile && !unique.exists())
        check(moved.readBytes()[0] != originalContent[0])
        moved.writeBytes(originalContent)
        check(moved.setLastModified(modified))
        check(organiser.undo(saved) == null)
        check(unique.readBytes().contentEquals(originalContent))
        clear(uniquePlan)

        val collision = plan("late-move.txt")
        val racingMove = FileOrganiser(store,VerifiedFileTransfer(beforeCreate = { it.writeText("keep competing destination") }))
        check(racingMove.apply(listOf(collision)).failed == 1)
        check(File(collision.sourcePath).readText() == "original")
        check(File(collision.targetPath).readText() == "keep competing destination")
        clear(collision)

        val undoCollision = plan("late-undo.txt")
        check(organiser.apply(listOf(undoCollision)).applied == 1)
        val racingUndo = FileOrganiser(store,VerifiedFileTransfer(beforeCreate = { it.writeText("keep new original-path file") }))
        check(racingUndo.undo(action(undoCollision)) != null)
        check(File(undoCollision.sourcePath).readText() == "keep new original-path file")
        check(File(undoCollision.targetPath).readText() == "original")
        check(action(undoCollision).status == "applied")
        clear(undoCollision)

        val changed = plan("changed-after-preview.txt")
        File(changed.sourcePath).apply { writeText("modified");check(setLastModified(changed.expectedModified)) }
        check(organiser.apply(listOf(changed)).failed == 1)
        check(File(changed.sourcePath).readText() == "modified" && !File(changed.targetPath).exists())
        clear(changed)

        val legacy = plan("legacy.txt")
        check(organiser.apply(listOf(legacy)).applied == 1)
        store.writableDatabase.execSQL("UPDATE file_actions SET hash=NULL WHERE id=?",arrayOf(action(legacy).id))
        check(organiser.undo(action(legacy))?.contains("no saved content fingerprint") == true)
        check(File(legacy.targetPath).readText() == "original" && !File(legacy.sourcePath).exists())
        clear(legacy)

        val interrupted = plan("interrupted.txt")
        val interruptedMove = FileOrganiser(store,VerifiedFileTransfer(afterCopy = { _,_ -> throw IOException("Fixture interruption") }))
        check(interruptedMove.apply(listOf(interrupted)).failed == 1)
        check(File(interrupted.sourcePath).readText() == "original" && File(interrupted.targetPath).readText() == "original")
        check(action(interrupted).status == "review")
        organiser.recoverPending()
        check(action(interrupted).status == "review")
        check(File(interrupted.sourcePath).isFile && File(interrupted.targetPath).isFile)
        clear(interrupted)

        // Simulate interruption after the physical move but before journal completion.
        val recover = plan("recover.txt")
        val recoverId = store.beginFileAction(recover)
        VerifiedFileTransfer().move(File(recover.sourcePath),File(recover.targetPath),recover.expectedSize,recover.hash)
        organiser.recoverPending()
        check(store.fileAction(recoverId)?.status == "applied")
        check(organiser.undo(requireNotNull(store.fileAction(recoverId))) == null)
        clear(recover)

        val replaced = plan("recovery-replaced.txt")
        val replacedId = store.beginFileAction(replaced)
        VerifiedFileTransfer().move(File(replaced.sourcePath),File(replaced.targetPath),replaced.expectedSize,replaced.hash)
        File(replaced.targetPath).writeText("modified")
        organiser.recoverPending()
        check(store.fileAction(replacedId)?.status == "review")
        check(File(replaced.targetPath).readText() == "modified")
        clear(replaced)

        val legacyPending = plan("legacy-pending.txt")
        val legacyId = store.beginFileAction(legacyPending.copy(hash = null))
        organiser.recoverPending()
        check(store.fileAction(legacyId)?.status == "review")
        check(File(legacyPending.sourcePath).readText() == "original")
        clear(legacyPending)
        check(destination.isDirectory)
    }

}
