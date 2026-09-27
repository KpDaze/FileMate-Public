package app.filemate

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object CleanupFlags {
    const val LIKELY_AI = 1
    const val UNSORTED_DOWNLOAD = 1 shl 1
    const val LARGE = 1 shl 2
    const val OLD = 1 shl 3
    const val ARCHIVE = 1 shl 4
    const val DUPLICATE = 1 shl 5
}

object CleanupRules {
    private const val OLD_MILLIS = 365L * 24 * 60 * 60 * 1000
    private val ARCHIVES = setOf("zip","7z","rar","tar","gz","bz2","xz","tgz")
    fun flags(name: String, root: String, size: Long, modified: Long,
        assigned: Boolean, knownAi: Boolean, now: Long): Int {
        var value = 0
        if(knownAi || FileRules.classify(name,null,now).candidate) value = value or CleanupFlags.LIKELY_AI
        if(root == "Downloads" && !assigned) value = value or CleanupFlags.UNSORTED_DOWNLOAD
        if(size >= CleanupScanner.LARGE_BYTES) value = value or CleanupFlags.LARGE
        if(root != "Camera" && root != "Pictures" && modified in 1 until (now - OLD_MILLIS)) value = value or CleanupFlags.OLD
        if(name.substringAfterLast('.',"").lowercase() in ARCHIVES) value = value or CleanupFlags.ARCHIVE
        return value
    }
}

data class CleanupEntry(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val root: String,
    val flags: Int,
    val hash: String? = null
)

data class CleanupSummary(
    val id: Long = 0,
    val completed: Long,
    val totalFiles: Int,
    val totalBytes: Long,
    val likelyAi: Int,
    val unsortedDownloads: Int,
    val large: Int,
    val old: Int,
    val archives: Int,
    val duplicateGroups: Int,
    val duplicateFiles: Int,
    val reclaimableBytes: Long
)

data class CleanupProgress(
    val running: Boolean = false,
    val stage: String = "",
    val files: Int = 0,
    val error: String? = null
)

data class SelectedFolder(val uri: String, val name: String)

private data class Candidate(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val root: String,
    val open: () -> InputStream?,
    val unchanged: () -> Boolean
)

class CleanupScanner(private val resolver: ContentResolver) {
    companion object {
        const val LARGE_BYTES = 100L * 1024 * 1024
        private val TREE_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }

    suspend fun scan(
        roots: List<Pair<String,File>>,
        selectedFolders: List<SelectedFolder>,
        assignedPaths: Set<String>,
        knownAiPaths: Set<String>,
        progress: (CleanupProgress) -> Unit
    ): Pair<List<CleanupEntry>,CleanupSummary> {
        val candidates = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()
        var warnings = 0
        progress(CleanupProgress(true,"Reading shared folders"))
        roots.forEach { (label,root) ->
            currentCoroutineContext().ensureActive()
            if(!root.exists()) return@forEach
            walkFiles(root,{ warnings++ }) { file ->
                val path = file.absolutePath
                val size = file.length()
                val modified = file.lastModified()
                if(seen.add(path)) candidates += Candidate(
                    path = path, name = path.substringAfterLast('/'), size = size, modified = modified,
                    root = label,
                    open = { runCatching { file.inputStream() }.getOrNull() },
                    unchanged = { file.length() == size && file.lastModified() == modified }
                )
            }
            progress(CleanupProgress(true,"Reading shared folders",candidates.size))
        }
        selectedFolders.forEach { folder ->
            currentCoroutineContext().ensureActive()
            warnings += readTree(folder,candidates,seen,progress)
        }

        val now = System.currentTimeMillis()
        val flags = IntArray(candidates.size)
        candidates.forEachIndexed { index,item ->
            currentCoroutineContext().ensureActive()
            flags[index] = CleanupRules.flags(item.name,item.root,item.size,item.modified,
                item.path in assignedPaths,item.path in knownAiPaths,now)
        }

        progress(CleanupProgress(true,"Checking exact duplicates",candidates.size))
        val hashes = arrayOfNulls<String>(candidates.size)
        var hashed = 0
        candidates.withIndex().groupBy { it.value.size }.values
            .filter { group -> group.first().value.size > 0 && group.size > 1 }
            .forEach { group ->
                group.forEach { indexed ->
                    currentCoroutineContext().ensureActive()
                    hashes[indexed.index] = indexed.value.open()?.use { sha256(it,indexed.value.size) }
                        ?.takeIf { indexed.value.unchanged() }
                    hashed++
                    if(hashed % 25 == 0) progress(CleanupProgress(true,"Checking exact duplicates",candidates.size))
                }
            }
        val duplicateGroups = hashes.withIndex().filter { it.value != null }
            .groupBy { "${candidates[it.index].size}:${it.value}" }.values.filter { it.size > 1 }
        duplicateGroups.flatten().forEach { flags[it.index] = flags[it.index] or CleanupFlags.DUPLICATE }
        val duplicateFiles = duplicateGroups.sumOf { it.size }
        val reclaimable = duplicateGroups.sumOf { group -> candidates[group.first().index].size * (group.size - 1) }

        val entries = candidates.mapIndexed { index,item ->
            CleanupEntry(item.path,item.name,item.size,item.modified,item.root,flags[index],hashes[index])
        }
        val summary = CleanupSummary(
            completed = System.currentTimeMillis(), totalFiles = entries.size,
            totalBytes = entries.sumOf { it.size },
            likelyAi = flags.count { it and CleanupFlags.LIKELY_AI != 0 },
            unsortedDownloads = flags.count { it and CleanupFlags.UNSORTED_DOWNLOAD != 0 },
            large = flags.count { it and CleanupFlags.LARGE != 0 },
            old = flags.count { it and CleanupFlags.OLD != 0 },
            archives = flags.count { it and CleanupFlags.ARCHIVE != 0 },
            duplicateGroups = duplicateGroups.size, duplicateFiles = duplicateFiles,
            reclaimableBytes = reclaimable
        )
        val stage = if(warnings == 0) "Scan complete" else "Scan complete · $warnings folders unavailable"
        progress(CleanupProgress(true,stage,entries.size))
        return entries to summary
    }

    private suspend fun readTree(
        folder: SelectedFolder,
        output: MutableList<Candidate>,
        seen: MutableSet<String>,
        progress: (CleanupProgress) -> Unit
    ): Int {
        val tree = Uri.parse(folder.uri)
        val pending = ArrayDeque<String>()
        val seenDirectories = mutableSetOf<String>()
        return try {
            pending.add(DocumentsContract.getTreeDocumentId(tree))
            var warnings = 0
            while(pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val parent = pending.removeFirst()
                if(!seenDirectories.add(parent)) continue
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
                val cursor = runCatching { resolver.query(children,TREE_COLUMNS,null,null,null) }.getOrNull()
                if(cursor == null) { warnings++;continue }
                cursor.use { c ->
                    val idIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIndex = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while(c.moveToNext()) {
                        val id = c.getString(idIndex)
                        if(c.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR) pending.add(id)
                        else {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree,id)
                            val primaryFile = primaryDocumentFile(tree,id)
                            val path = primaryFile?.absolutePath ?: uri.toString()
                            val size = if(c.isNull(sizeIndex)) 0 else c.getLong(sizeIndex)
                            val modified = if(c.isNull(modifiedIndex)) 0 else c.getLong(modifiedIndex)
                            if(seen.add(path)) output += Candidate(
                                path = path, name = c.getString(nameIndex) ?: "Unnamed file",
                                size = size, modified = modified,
                                root = folder.name,
                                open = { runCatching { primaryFile?.inputStream() ?: resolver.openInputStream(uri) }.getOrNull() },
                                unchanged = { primaryFile == null || (primaryFile.length() == size && primaryFile.lastModified() == modified) }
                            )
                        }
                    }
                }
                if(output.size % 100 == 0) progress(CleanupProgress(true,"Reading ${folder.name}",output.size))
            }
            warnings
        } catch(_: Exception) { 1 }
    }

    private fun sha256(input: InputStream, expectedSize: Long): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while(true) {
            val read = input.read(buffer)
            if(read < 0) break
            if(read > 0) { digest.update(buffer,0,read);total += read }
        }
        if(total != expectedSize) return null
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun primaryDocumentFile(tree: Uri, documentId: String): File? {
        if(tree.authority != "com.android.externalstorage.documents" || !documentId.startsWith("primary:")) return null
        val relative = documentId.substringAfter("primary:")
        return File(android.os.Environment.getExternalStorageDirectory(),relative)
    }
}
