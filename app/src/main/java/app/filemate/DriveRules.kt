package app.filemate

enum class StorageRule(val label: String) {
    PHONE_ONLY("Phone only"),
    PHONE_AND_DRIVE("Phone + external storage"),
    DRIVE_AFTER_UPLOAD("External storage after verified copy")
}

data class ProjectStorageRule(val projectId: Long, val rule: StorageRule)

object DriveRules {
    fun parse(value: String?): StorageRule = runCatching { StorageRule.valueOf(value.orEmpty()) }.getOrDefault(StorageRule.PHONE_ONLY)
    fun mayRemoveLocal(uploadVerified: Boolean, driveAfterUpload: Boolean): Boolean = uploadVerified && driveAfterUpload
}
