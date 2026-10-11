package app.filemate

import org.junit.Assert.*
import org.junit.Test

class OrganiserLearningTest {
    private val now=1_000_000L
    private val projects=listOf(Project(1,"Life Map",0,0,0),Project(2,"ShiftMate",0,0,0))
    private fun example(key: String, project: Long?=1, source: String?=null)=
        LearningExample(key,"mountain assets v01.png",project,source,source!=null)

    @Test fun reviewedProjectPatternGivesSuggestionWithoutInventingProject() {
        val match=ProjectRules.suggest("mountain_assets_v02.png",projects,listOf(example("one")))
        assertEquals(1L,match?.projectId);assertEquals("Medium",match?.confidence)
        assertNull(ProjectRules.suggest("mountain_assets_v02.png",emptyList(),listOf(example("one"))))
    }
    @Test fun correctionReplacesEarlierProjectAndClearingRetractsIt() {
        val examples=mutableMapOf("one" to example("one"))
        examples["one"]=example("one",2)
        assertEquals(2L,ProjectLearning.project("mountain_assets_v02.png",projects,examples.values.toList())?.projectId)
        examples["one"]=example("one",null)
        assertNull(ProjectLearning.project("mountain_assets_v02.png",projects,examples.values.toList()))
    }
    @Test fun sourceLearningNeedsDistinctReviewedFilesAndKeepsConflicts() {
        assertNull(ProjectLearning.source("mountain_assets_v02.png",listOf(example("one",source="Qwen"))))
        val learned=ProjectLearning.source("mountain_assets_v02.png",listOf(example("one",source="Qwen"),example("two",source="Claude")))
        assertEquals(setOf("Qwen","Claude"),learned?.sources)
        assertEquals("Medium",FileRules.classify("mountain_assets_v02.png",AiContext("Qwen","example.qwen",now),now,learned=learned).confidence)
    }
    @Test fun dateOnlyScreenshotNamesNeverLearnAProject() {
        val sample=LearningExample("one","Screenshot_20261011_143500.png",1,null,false)
        assertNull(ProjectLearning.project("Screenshot_20261011_145800.png",projects,listOf(sample)))
    }
    @Test fun sourceAliasesAndPackageIdentitySupportSelectedAppLabels() {
        assertEquals("High",FileRules.classify("ChatGPT_LifeMap.png",AiContext("ChatGPT — AI Assistant","com.openai.chatgpt",now),now).confidence)
        assertEquals("Medium",FileRules.classify("Qwen_LifeMap.png",AiContext("Claude","com.openai.chatgpt",now),now).confidence)
        assertEquals("High",FileRules.classify("DALL_E_Life_Map.png",AiContext("DALL-E","example.dalle",now),now).confidence)
    }
    @Test fun generationPatternAndLiveFolderGiveReviewableEvidenceWithoutProviderInName() {
        val result=FileRules.classify("generated_image_004.png",AiContext("Qwen","example.qwen",now),now,"/storage/emulated/0/Download")
        assertEquals("Medium",result.confidence);assertEquals("Qwen",result.source)
        val historical=FileRules.classify("generated_image_004.png",null,now,"/storage/emulated/0/Download")
        assertTrue(historical.candidate);assertNull(historical.source);assertEquals("Low",historical.confidence)
    }
    @Test fun correctedNonAiRemainsExcludedEvenWhenNameLooksLikeProvider() {
        val flags=CleanupRules.flags("ChatGPT_LifeMap.zip","Downloads",100,now,false,true,now,classifiedAi=false)
        assertEquals(0,flags and CleanupFlags.LIKELY_AI)
        assertTrue(flags and CleanupFlags.ARCHIVE!=0)
    }
    @Test fun tidyNamesAreStableAndDoNotRepeatProjectPrefix() {
        val once=FileNaming.tidy("Life Map","mountain assets_v02.png",1_000_000_000_000)
        assertEquals(once,FileNaming.tidy("Life Map",once,1_000_000_000_000))
        assertFalse(once.contains("Life_Map_Life_Map"))
        assertTrue(once.endsWith(".png"))
    }
}
