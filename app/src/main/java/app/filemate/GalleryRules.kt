package app.filemate

import java.util.Locale

data class MediaClues(val camera: Boolean, val screenshot: Boolean, val downloads: Boolean,
    val screenshotConfidence: String, val explanation: String)

object GalleryRules {
    private val screenshotName = Regex("^(screenshot|screen[ _-]?shot|screen[ _-]?capture)([ _.-]|[0-9]|$)")
    fun clues(name: String, relativePath: String, bucket: String, width: Int, height: Int, video: Boolean): MediaClues {
        val folders = relativePath.lowercase(Locale.ROOT).split('/').filter { it.isNotBlank() }
        val folderClue = folders.any { it == "screenshots" || it == "screen captures" } || bucket.equals("Screenshots",true)
        val nameClue = screenshotName.containsMatchIn(name.lowercase(Locale.ROOT))
        val screenShape = minOf(width,height) in 600..4000 && maxOf(width,height) in 1000..10000 &&
            maxOf(width,height).toDouble() / minOf(width,height).coerceAtLeast(1) >= 1.6
        val likely = !video && (folderClue || nameClue)
        val confidence = when { video -> "Not assessed";folderClue -> "High";nameClue -> "Medium";screenShape -> "Low";else -> "Unknown" }
        val explanation = when {
            video -> "Screenshot clues apply to still images, not videos."
            folderClue -> "Listed in a Screenshots collection or folder. This is a clue, not proof of how it was created."
            nameClue -> "The filename starts with a screenshot-style name. This is a clue, not proof."
            screenShape -> "Screen-shaped dimensions only; that is not enough to classify this as a screenshot."
            else -> "No clear screenshot clue in the available metadata."
        }
        return MediaClues(folders.firstOrNull() == "dcim" && !folderClue,
            likely,folders.firstOrNull() in setOf("download","downloads"),confidence,explanation)
    }
    fun chronologicalTime(takenMillis: Long, addedSeconds: Long, modifiedSeconds: Long): Long = when {
        takenMillis > 0 -> takenMillis
        addedSeconds in 1..Long.MAX_VALUE / 1000 -> addedSeconds * 1000
        modifiedSeconds in 1..Long.MAX_VALUE / 1000 -> modifiedSeconds * 1000
        else -> 0
    }
    fun identity(volume: String, version: String, kind: String, id: Long, generationAdded: Long): String =
        listOf(volume,version,kind,id.toString(),generationAdded.toString()).joinToString("|") { "${it.length}:$it" }
}
