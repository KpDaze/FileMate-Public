package app.filemate

import java.util.Locale

/** Pure policies: an estimate of source never establishes a project. */
data class FileStamp(val size: Long, val modified: Long)
data class AiContext(val label: String, val packageName: String, val lastUsedAt: Long)
data class Finding(val candidate: Boolean, val source: String?, val confidence: String, val reason: String)
data class LearnedSource(val sources: Set<String>, val examples: Int)

object FileRules {
    val providers = listOf("ChatGPT", "Qwen", "Grok", "Claude", "Gemini", "DALL-E")
    private val aliases = mapOf("ChatGPT" to "chat[ _.-]?gpt", "Qwen" to "qwen", "Grok" to "grok",
        "Claude" to "claude", "Gemini" to "gemini", "DALL-E" to "dall[ _·.-]?e")
    private val excludedExtensions = setOf("apk", "apks", "xapk", "aab")
    private val supported = setOf("png", "jpg", "jpeg", "webp", "pdf", "md", "txt", "csv", "json", "zip", "docx", "svg", "html", "xlsx", "pptx")
    private val generatedName = Regex("(?i)(^|[ _.-])(ai[ _.-]?generated|generated[ _.-](image|asset|text)|image[ _.-]?gen)([ _.-]|[0-9]|$)")
    private val unrelated = Regex("(?i)(^|[ _.-])(receipt|invoice|bank|statement|payslip)([ _.-]|$)")
    fun temporary(name: String): Boolean = name.startsWith(".") ||
        listOf(".part", ".partial", ".tmp", ".crdownload", ".download").any { name.endsWith(it, true) }
    fun changed(previous: FileStamp?, current: FileStamp) = previous != current
    fun namedProviders(text: String): Set<String> {
        val separated = text.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        return aliases.filterValues { Regex("(?i)(^|[^\\p{L}\\p{N}])(?:$it)(?=$|[^\\p{L}\\p{N}])").containsMatchIn(separated) }.keys
    }
    private fun activeProvider(context: AiContext): String? {
        val label = namedProviders(context.label)
        val pkg = when(context.packageName.lowercase(Locale.ROOT)) {
            "com.openai.chatgpt" -> "ChatGPT"
            "ai.x.grok" -> "Grok"
            "com.anthropic.claude" -> "Claude"
            "com.google.android.apps.bard" -> "Gemini"
            else -> namedProviders(context.packageName).singleOrNull()
        }
        return (label + listOfNotNull(pkg)).singleOrNull()
    }
    fun classify(name: String, context: AiContext?, now: Long, location: String = "", learned: LearnedSource? = null): Finding {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (temporary(name) || ext in excludedExtensions || unrelated.containsMatchIn(name))
            return Finding(false, null, "None", "No useful AI evidence; left untouched.")
        if(location.split('/').any { it.equals("DCIM",true) || it.equals("Camera",true) })
            return Finding(false,null,"None","Camera media stays manual.")
        val named = namedProviders(name)
        val folder = namedProviders(location)
        val recent = context?.takeIf { now - it.lastUsedAt in 0..120_000 }
        val active = recent?.let(::activeProvider)
        val learnedSources = learned?.sources.orEmpty()
        val positive = (named + folder + learnedSources.filter { it != "Not AI" }).toSet()
        if(positive.size > 1 || ("Not AI" in learnedSources && positive.isNotEmpty()))
            return Finding(true,null,"Medium","Source clues conflict. Review the source; nothing can be organised automatically.")
        if(learnedSources == setOf("Not AI"))
            return Finding(false,null,"None","Similar reviewed filenames were marked Not AI; left untouched.")
        val source = positive.singleOrNull()
        if(source != null) {
            val high = named.size == 1 && active == source && ext in supported
            return Finding(true,source,if(high) "High" else "Medium",when {
                high -> "Filename names $source; recent selected-app activity agrees and the file type is supported. Source is still an estimate."
                active != null && active != source -> "Filename, folder or learned clues suggest $source, but recent activity suggests $active. Review required."
                source in learnedSources -> "Similar filenames were reviewed as $source (${learned?.examples} local examples). Learned evidence remains a suggestion."
                source in folder && named.isEmpty() -> "The folder names $source${if(active == source) " and recent app activity agrees" else ""}. Folder clues require review."
                else -> "Filename mentions $source. App activity does not confirm the source."
            })
        }
        if(ext in supported && generatedName.containsMatchIn(name)) {
            val downloadLocation = location.split('/').any { it.equals("Download",true) || it.equals("Downloads",true) || it.equals("Documents",true) }
            return Finding(true,active,if(active!=null && downloadLocation) "Medium" else "Low",
                "Generation-style filename and supported file type${if(active!=null) "; recent $active activity" else ""}${if(downloadLocation) "; shared download location" else ""}. These clues need review.")
        }
        if(recent != null && ext in supported)
            return Finding(true,active ?: recent.label,"Low","Appeared near ${recent.label} activity. Timing and file type alone cannot identify its source.")
        return Finding(false,null,"None","No useful AI evidence; left untouched.")
    }
}

class SessionClock(private val timeoutMs: Long = MonitoringSettings.DEFAULT_MINUTES * 60_000L) {
    var lastAiActivity: Long = 0; private set
    fun touch(elapsed: Long) { lastAiActivity = elapsed }
    fun expired(elapsed: Long) = elapsed - lastAiActivity >= timeoutMs
}
object MonitoringSettings {
    const val DEFAULT_MINUTES = 30L
    val allowedMinutes = listOf(15L,30L,45L,60L)
    fun minutes(raw: String?): Long = raw?.toLongOrNull()?.takeIf { it in allowedMinutes } ?: DEFAULT_MINUTES
}
enum class NamingPreference(val label: String) { KEEP_CURRENT("Keep current names"), TIDY_WHEN_REVIEWED("Suggest tidy names") }
object NamingSettings {
    fun parse(raw: String?): NamingPreference = runCatching { NamingPreference.valueOf(raw.orEmpty()) }.getOrDefault(NamingPreference.KEEP_CURRENT)
}
