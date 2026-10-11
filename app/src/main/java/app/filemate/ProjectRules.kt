package app.filemate

import java.util.Locale

data class ProjectMatch(val projectId: Long, val projectName: String, val confidence: String, val reason: String)
data class LearningExample(val key: String, val name: String, val projectId: Long?, val source: String?, val sourceReviewed: Boolean)

object ProjectLearning {
    private val ignored = setOf("chatgpt","chat","gpt","qwen","grok","claude","gemini","dall","export","download","downloads",
        "file","files","image","img","document","screenshot","screen","shot","capture","copy","final","version","draft","photo","untitled")
    fun words(value: String): List<String> = value.replace(Regex("([a-z])([A-Z])"),"$1 $2").lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
    fun tokens(name: String): List<String> = words(name.substringBeforeLast('.',name))
        .filter { it.length >= 3 && it !in ignored && !it.all(Char::isDigit) && !it.matches(Regex("v?\\d+")) }
        .distinct().take(24)
    fun matches(name: String, example: LearningExample): Boolean =
        tokens(name).toSet().intersect(tokens(example.name).toSet()).size >= 2
    fun source(name: String, examples: List<LearningExample>): LearnedSource? {
        val matches = examples.distinctBy { it.key }.filter { it.sourceReviewed && matches(name,it) }
        if(matches.size < 2) return null
        return LearnedSource(matches.mapNotNull { it.source }.toSet(),matches.size).takeIf { it.sources.isNotEmpty() }
    }
    fun project(name: String, projects: List<Project>, examples: List<LearningExample>): ProjectMatch? {
        val ids = examples.filter { it.projectId != null && matches(name,it) }.mapNotNull { it.projectId }.distinct()
        val project = projects.singleOrNull { it.id in ids } ?: return null
        return ProjectMatch(project.id,project.name,"Medium","Similar filenames were assigned to this project. Review before applying.")
    }
}
object ProjectRules {
    fun classify(fileName: String, projects: List<Project>): ProjectMatch? {
        val words = ProjectLearning.words(fileName.substringBeforeLast('.',fileName))
        val matches = projects.filter { project ->
            val projectWords = ProjectLearning.words(project.name)
            projectWords.isNotEmpty() && words.windowed(projectWords.size).any { it == projectWords }
        }
        val project = matches.singleOrNull() ?: return null
        val parts = ProjectLearning.words(project.name)
        val distinctive = parts.size >= 2 || (parts.single().length >= 4 && ProjectLearning.tokens(project.name).isNotEmpty())
        return ProjectMatch(project.id,project.name,if(distinctive) "High" else "Medium","Filename uniquely names this existing project.")
    }
    fun suggest(fileName: String, projects: List<Project>, examples: List<LearningExample>): ProjectMatch? {
        val exact = classify(fileName,projects)
        val learned = ProjectLearning.project(fileName,projects,examples)
        val conflicting = examples.any { it.projectId != null && it.projectId != exact?.projectId && ProjectLearning.matches(fileName,it) }
        if(exact != null) return if(conflicting) exact.copy(confidence="Medium",reason="Filename and reviewed project patterns disagree. Choose the project.") else exact
        val namedCount = projects.count { classify(fileName,listOf(it)) != null }
        return if(namedCount > 1) null else learned
    }
}
object AutomaticOrganisationPolicy {
    fun permits(live: Boolean, newlyObserved: Boolean, finding: Finding, project: ProjectMatch?, root: String, managed: Boolean): Boolean =
        live && newlyObserved && finding.candidate && finding.source != null && finding.confidence == "High" &&
            project?.confidence == "High" && root in setOf("Downloads","Documents") && !managed
}
