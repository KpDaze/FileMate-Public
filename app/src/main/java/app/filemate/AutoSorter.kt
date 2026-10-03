package app.filemate

import android.os.Environment
import java.io.File

/**
 * Automatic organisation is deliberately conservative: both source and project must be
 * independently high-confidence. Ambiguous files remain physically untouched in Needs Sorting.
 */
class AutoSorter(private val store: Store) {
    @Suppress("DEPRECATION")
    fun trySort(file: File, finding: Finding): OrganiseResult? {
        if(!file.isFile || finding.confidence != "High") return null
        val match = ProjectRules.classify(file.name, store.projects()) ?: return null
        if(match.confidence != "High") return null

        val sharedRoot = Environment.getExternalStorageDirectory().canonicalFile
        val source = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if(!source.path.startsWith("${sharedRoot.path}/")) return null

        val root = when {
            source.path.startsWith(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath + "/") -> "Downloads"
            source.path.startsWith(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).canonicalPath + "/") -> "Documents"
            else -> return null
        }
        val fingerprint = ContentFingerprint.read(source) ?: return null
        if(fingerprint.size != source.length() || fingerprint.modified != source.lastModified()) return null

        val entry = CleanupEntry(source.absolutePath,source.name,fingerprint.size,fingerprint.modified,root,0,fingerprint.hash)
        val project = store.projects().firstOrNull { it.id == match.projectId } ?: return null
        val plans = FileOrganiser(store).plans(listOf(entry),project,tidyNames = false)
        val plan = plans.singleOrNull()?.takeIf { it.supported } ?: return null
        return FileOrganiser(store).apply(listOf(plan))
    }
}
