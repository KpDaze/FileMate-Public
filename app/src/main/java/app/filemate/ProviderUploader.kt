package app.filemate

import android.content.Context
import android.net.Uri
import java.io.File

data class ProviderUploadPlan(
    val sourcePath: String,
    val displayName: String,
    val mime: String,
    val projectId: Long?,
    val projectName: String?,
    val removeLocalAfterVerifiedCopy: Boolean
)

class ProviderUploader(private val context: Context, private val store: Store) {
    fun upload(plan: ProviderUploadPlan): String? {
        val folder=store.externalStorageFolder() ?: return "Choose an optional storage folder in Setup first."
        val tree=runCatching { Uri.parse(folder.uri) }.getOrNull() ?: return "The saved storage folder is invalid. Choose it again."
        val source=File(plan.sourcePath)
        if(!source.isFile) return "The local file is no longer available."
        return try {
            val result=VerifiedProviderCopy(context).copy(source,tree,plan.mime,plan.displayName)
            store.recordProviderUpload(source,result.uri,folder.name,plan.projectId)
            if(plan.removeLocalAfterVerifiedCopy) {
                // This option is intentionally conservative: only remove the exact local file that was
                // fingerprinted before and after the verified provider copy.
                val fingerprint=ContentFingerprint.read(source)
                    ?: return "Copy verified, but the local file changed afterward. It was kept."
                if(fingerprint.size!=result.bytes || fingerprint.hash!=result.hash)
                    return "Copy verified, but the local file changed afterward. It was kept."
                val path=source.absolutePath
                if(!source.delete()) return "Copy verified, but Android could not remove the local file. It was kept."
                store.markLocalRemovedAfterProviderCopy(path,source.name)
            }
            null
        } catch(e: Exception) { e.message ?: "The external copy could not be completed. The local file was kept." }
    }
}
