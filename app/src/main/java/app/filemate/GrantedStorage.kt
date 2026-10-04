package app.filemate

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream

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
