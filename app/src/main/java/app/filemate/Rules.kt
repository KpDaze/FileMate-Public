package app.filemate

/** Pure policies, kept independent of Android so important boundaries can be tested. */
data class FileStamp(val size: Long, val modified: Long)
data class AiContext(val label: String, val packageName: String, val lastUsedAt: Long)
data class Finding(val candidate: Boolean, val source: String?, val confidence: String, val reason: String)

object FileRules {
    private val providers = listOf("ChatGPT", "Qwen", "Grok", "Claude", "Gemini", "DALL-E")
    private val excludedExtensions = setOf("apk", "apks", "xapk", "aab")
    private val unrelated = Regex("(?i)(^|[ _.-])(receipt|invoice|bank|statement|payslip)([ _.-]|$)")
    fun temporary(name: String): Boolean = name.startsWith(".") ||
        listOf(".part", ".partial", ".tmp", ".crdownload", ".download").any { name.endsWith(it, true) }
    fun changed(previous: FileStamp?, current: FileStamp) = previous != current
    fun classify(name: String, context: AiContext?, now: Long): Finding {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (temporary(name) || ext in excludedExtensions || unrelated.containsMatchIn(name))
            return Finding(false, null, "None", "No useful AI evidence; left untouched.")
        val normalized = name.lowercase().replace("dall·e", "dall-e").replace("dall_e", "dall-e")
        val named = providers.firstOrNull { normalized.contains(it.lowercase()) }
        val recent = context?.takeIf { now - it.lastUsedAt in 0..120_000 }
        if (named != null) {
            val agrees = recent?.label?.contains(named, ignoreCase = true) == true
            return Finding(true, named, if (agrees) "High" else "Medium",
                if (agrees) "Filename mentions $named and $named was recently used. Source is still an estimate."
                else "Filename mentions $named. App activity does not confirm the source.")
        }
        // Timing alone never justifies moving/renaming or a certain source attribution.
        if (recent != null && ext in setOf("png", "jpg", "jpeg", "webp", "pdf", "md", "txt", "csv", "json", "zip", "docx", "svg", "html"))
            return Finding(true, recent.label, "Low", "Appeared near ${recent.label} activity. Timing alone cannot identify its source; review when convenient.")
        return Finding(false, null, "None", "No useful AI evidence; left untouched.")
    }
}

class SessionClock(private val timeoutMs: Long = MonitoringSettings.DEFAULT_MINUTES * 60_000L) {
    var lastAiActivity: Long = 0; private set
    fun touch(elapsed: Long) { lastAiActivity = elapsed }
    fun expired(elapsed: Long) = elapsed - lastAiActivity >= timeoutMs
}


data class ProjectMatch(val projectId: Long, val projectName: String, val confidence: String, val reason: String)

object ProjectRules {
    private fun words(value: String): List<String> = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().split(Regex("\\s+")).filter { it.length >= 2 }

    fun classify(fileName: String, projects: List<Project>): ProjectMatch? {
        val stemWords = words(fileName.substringBeforeLast('.', fileName)).toSet()
        if(stemWords.isEmpty()) return null
        val exact = projects.mapNotNull { project ->
            val projectWords = words(project.name)
            val distinctive = projectWords.size >= 2 ||
                (projectWords.size == 1 && projectWords.single() !in setOf("filemate","chatgpt","qwen","grok","claude","gemini"))
            if(distinctive && projectWords.all { it in stemWords })
                ProjectMatch(project.id, project.name, "High", "Filename uniquely names the project.")
            else null
        }
        return exact.singleOrNull()
    }
}


object ProjectLearning {
    private val ignored = setOf("chatgpt","qwen","grok","claude","gemini","dall","export","download","file","image","document")
    fun tokens(name: String): List<String> = name.substringBeforeLast('.',name).lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in ignored && !it.all(Char::isDigit) }.distinct()
}


object MonitoringSettings {
    const val DEFAULT_MINUTES = 30L
    val allowedMinutes = listOf(15L,30L,45L,60L)
    fun minutes(raw: String?): Long = raw?.toLongOrNull()?.takeIf { it in allowedMinutes } ?: DEFAULT_MINUTES
}


enum class NamingPreference(val label: String) {
    KEEP_CURRENT("Keep current names"),
    TIDY_WHEN_REVIEWED("Suggest tidy names")
}
object NamingSettings {
    fun parse(raw: String?): NamingPreference = runCatching { NamingPreference.valueOf(raw.orEmpty()) }.getOrDefault(NamingPreference.KEEP_CURRENT)
}
