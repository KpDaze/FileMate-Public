package app.filemate

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

data class ProviderEntry(val uri: String, val name: String, val mime: String, val size: Long, val modified: Long, val directory: Boolean)

class ProviderBrowser(private val context: Context) {
    fun children(tree: Uri): List<ProviderEntry> {
        require(DocumentsContract.isTreeUri(tree)) { "Choose a folder through Android's folder picker." }
        val parent=DocumentsContract.getTreeDocumentId(tree)
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
        val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return context.contentResolver.query(children,columns,null,null,null)?.use { c -> buildList {
            while(c.moveToNext()) {
                val id=c.getString(0);val mime=c.getString(2) ?: "application/octet-stream"
                add(ProviderEntry(DocumentsContract.buildDocumentUriUsingTree(tree,id).toString(),c.getString(1) ?: "Unnamed",
                    mime,if(c.isNull(3)) 0 else c.getLong(3),if(c.isNull(4)) 0 else c.getLong(4),mime==DocumentsContract.Document.MIME_TYPE_DIR))
            }
        } } ?: emptyList()
    }

    fun createFolder(tree: Uri, name: String): Uri {
        val clean=ProjectNames.clean(name)
        val root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(context.contentResolver,root,DocumentsContract.Document.MIME_TYPE_DIR,clean)
            ?: error("The selected storage provider could not create the folder.")
    }

    fun rename(uri: Uri, name: String): Uri = DocumentsContract.renameDocument(context.contentResolver,uri,ProjectNames.clean(name))
        ?: error("The selected storage provider could not rename this item.")

    fun delete(uri: Uri) {
        check(DocumentsContract.deleteDocument(context.contentResolver,uri)) { "The selected storage provider could not delete this item." }
    }
}


fun ProviderBrowser.search(tree: Uri, query: String): List<ProviderEntry> {
    val needle=query.trim().lowercase()
    if(needle.isBlank()) return children(tree)
    return children(tree).filter { it.name.lowercase().contains(needle) }
}
