package app.filemate

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.security.MessageDigest

data class GrantedStorageFolder(val uri: String, val name: String)

/**
 * Android Storage Access Framework only. No network client, OAuth client, API key or metered
 * developer service is used by FileMate. The selected provider (for example the Drive Android
 * app) owns any remote transport behind Android's document provider.
 */
class GrantedStorage(private val context: Context) {
    fun isTree(uri: Uri): Boolean = DocumentsContract.isTreeUri(uri)
    fun name(uri: Uri): String = DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { "Selected storage" }

    fun createFile(tree: Uri, mime: String, displayName: String): Uri {
        require(isTree(tree)) { "Choose a folder through Android's folder picker." }
        val root = DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(context.contentResolver,root,mime,displayName)
            ?: error("The selected storage provider could not create the file.")
    }

    fun openWrite(uri: Uri): OutputStream = context.contentResolver.openOutputStream(uri,"w")
        ?: error("The selected storage provider could not open the destination.")
    fun openRead(uri: Uri): InputStream = context.contentResolver.openInputStream(uri)
        ?: error("The selected storage provider could not open the file.")
}


data class ProviderCopyResult(val uri: Uri, val bytes: Long, val hash: String)

class VerifiedProviderCopy(private val context: Context) {
    private val storage = GrantedStorage(context)

    fun copy(source: File, tree: Uri, mime: String, displayName: String): ProviderCopyResult {
        val original = ContentFingerprint.read(source) ?: error("The source file could not be verified. Nothing was copied.")
        check(source.isFile) { "The source file is no longer available." }
        val destination = storage.createFile(tree,mime,displayName)
        try {
            storage.openWrite(destination).use { output ->
                source.inputStream().use { input ->
                    val buffer=ByteArray(64*1024);var written=0L
                    while(true) {
                        val n=input.read(buffer);if(n<0) break
                        written += n
                        check(written <= original.size) { "The source changed during upload." }
                        output.write(buffer,0,n)
                    }
                    check(written == original.size) { "The source changed during upload." }
                    output.flush()
                }
            }
            val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
            storage.openRead(destination).use { input ->
                val buffer=ByteArray(64*1024)
                while(true) { val n=input.read(buffer);if(n<0) break;bytes+=n;check(bytes<=original.size) { "The provider copy differs from the source." };digest.update(buffer,0,n) }
            }
            val hash=digest.digest().joinToString("") { "%02x".format(it) }
            check(bytes==original.size && hash==original.hash) { "The provider copy could not be verified. The local file was kept." }
            check(ContentFingerprint.matches(source,original.size,original.hash)) { "The local file changed during upload. It was kept." }
            return ProviderCopyResult(destination,bytes,hash)
        } catch(e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver,destination) }
            throw e
        }
    }
}


fun Store.recordProviderUpload(source: File, destination: Uri, providerName: String, projectId: Long?) = synchronized(this) {
    val db=writableDatabase;db.beginTransaction()
    try {
        val projectName=projectId?.let { id -> db.rawQuery("SELECT name FROM projects WHERE id=?",arrayOf(id.toString())).use { if(it.moveToFirst()) it.getString(0) else null } }
        addHistory(db,"File copied to external storage",
            "${source.name} → ${providerName}${projectName?.let { " / $it" }.orEmpty()}. Verified copy; local file kept.",System.currentTimeMillis())
        db.insertWithOnConflict("state",null,android.content.ContentValues().apply { put("key","provider_uri:${source.absolutePath}");put("value",destination.toString()) },android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        projectId?.let { db.execSQL("UPDATE projects SET updated=? WHERE id=?",arrayOf(System.currentTimeMillis(),it)) }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
