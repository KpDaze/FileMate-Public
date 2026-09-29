package app.filemate

import java.time.Instant
import java.time.ZoneId

/** Metadata groups are browsing aids, never a prediction of topic, app or project. */
data class ScreenshotCandidate(val identity: String, val volume: String, val folder: String,
    val time: Long, val screenshot: Boolean, val camera: Boolean, val video: Boolean,
    val available: Boolean, val assigned: Boolean)
data class ScreenshotGroup(val key: String, val day: String, val folder: String, val identities: List<String>)

object GalleryScreenshotRules {
    fun eligible(item: ScreenshotCandidate, unassignedOnly: Boolean): Boolean =
        item.available && item.screenshot && !item.camera && !item.video && (!unassignedOnly || !item.assigned)

    fun groups(items: List<ScreenshotCandidate>, unassignedOnly: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault()): List<ScreenshotGroup> {
        val eligible = items.filter { eligible(it,unassignedOnly) }.distinctBy { it.identity }
            .sortedWith(compareByDescending<ScreenshotCandidate> { it.time }.thenBy { it.identity })
        return eligible.groupBy { item ->
            val day = if(item.time > 0) Instant.ofEpochMilli(item.time).atZone(zone).toLocalDate().toString() else "Date unavailable"
            // Missing folder/date metadata must not imply a relationship between unrelated items.
            val isolation = if(item.folder.isBlank() || item.time <= 0) item.identity else ""
            listOf(item.volume,item.folder.trimEnd('/'),day,isolation)
        }.map { (parts,members) ->
            ScreenshotGroup(parts.joinToString("") { "${it.length}:$it" },parts[2],
                parts[1].ifBlank { "Folder unavailable" },members.map { it.identity })
        }
    }
}
