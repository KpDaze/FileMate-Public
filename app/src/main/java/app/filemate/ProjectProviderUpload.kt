package app.filemate

import android.content.Context

object MimeGuess {
    fun fromName(name: String): String = when(name.substringAfterLast('.', "").lowercase()) {
        "jpg","jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "txt","md" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "zip" -> "application/zip"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}

class ProjectProviderUpload(private val context: Context, private val store: Store) {
    fun upload(file: DetectedFile, rule: StorageRule): String? {
        if(rule == StorageRule.PHONE_ONLY) return null
        val remove = rule == StorageRule.DRIVE_AFTER_UPLOAD
        return ProviderUploader(context,store).upload(ProviderUploadPlan(file.path,file.name,MimeGuess.fromName(file.name),
            file.projectId,file.projectName,remove))
    }
}
