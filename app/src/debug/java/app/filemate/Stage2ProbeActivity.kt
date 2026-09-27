package app.filemate

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import java.io.File

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
                check(plan.supported)
                check(plan.targetName == "same (2).txt")

                val applied = FileOrganiser(app.store).apply(listOf(plan))
                check(applied.applied == 1 && applied.failed == 0)
                check(!first.exists() && File(plan.targetPath).isFile)
                check(conflict.readText() == "keep this existing file")
                val action = app.store.fileActions().first { it.status == "applied" }
                check(FileOrganiser(app.store).undo(action) == null)
                check(first.isFile && !File(plan.targetPath).exists())
                check(app.store.fileActions().first { it.id == action.id }.status == "undone")
                check(app.store.projectFiles(projectId).single().path == first.absolutePath)

                resultFile.writeText("""{"passed":true,"files":2,"duplicate_groups":1,"move":true,"undo":true,"conflict_preserved":true}""")
                second.delete()
                first.delete()
                conflict.delete()
                testRoot.delete()
                destination.delete()
            } catch(e: Throwable) {
                resultFile.writeText("""{"passed":false,"error":"${escape(e.message ?: e::class.java.simpleName)}"}""")
            } finally {
                runOnUiThread { finish() }
            }
        }
    }

    private fun escape(value: String): String = value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
}
