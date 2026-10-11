package app.filemate

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Independent regression cases derived from the V1 safety requirements. */
class VerifierSafetyTest {
    private val now = 1_000_000L
    private val qwen = AiContext("Qwen", "example.qwen", now - 1_000)
    @get:Rule val temporary = TemporaryFolder()

    @Test fun providerSubstringIsNotEnoughForHighConfidence() {
        val grok = AiContext("Grok", "example.grok", now)
        assertNotEquals("High", FileRules.classify("grokery_Project_Plan.png", grok, now).confidence)
    }

    @Test fun twoNamedProvidersRemainAmbiguousEvenWhenOneIsActive() {
        val chatgpt = AiContext("ChatGPT", "com.openai.chatgpt", now)
        assertNotEquals("High", FileRules.classify("ChatGPT_Qwen_Project_Plan.md", chatgpt, now).confidence)
    }

    @Test fun mismatchedProviderCannotBecomeHighThroughTiming() {
        assertNotEquals("High", FileRules.classify("Claude_Project_Plan.md", qwen, now).confidence)
    }

    @Test fun pastAndFutureActivityCannotAuthorizeAutomaticFiling() {
        listOf(now - 120_001, now + 1).forEach { activityTime ->
            assertNotEquals("High", FileRules.classify("Qwen_Project_Plan.png", qwen.copy(lastUsedAt = activityTime), now).confidence)
        }
    }

    @Test fun unrelatedFilesAreExcludedEvenIfProviderNamed() {
        listOf("Qwen_bank_statement.pdf", "Qwen_receipt.pdf", "Qwen_Project.apk", "Qwen_Project.png.crdownload").forEach { name ->
            assertFalse(name, FileRules.classify(name, qwen, now).candidate)
        }
    }

    @Test fun genericArchiveAndImageDoNotObtainHighFromTiming() {
        listOf("export.zip", "image_001.png").forEach { name ->
            assertNotEquals("High", FileRules.classify(name, qwen, now).confidence)
        }
    }

    @Test fun conflictingFolderAndLearnedEvidenceCannotBecomeHigh() {
        assertNotEquals("High", FileRules.classify("Qwen_Project_Plan.png", qwen, now,
            "/storage/emulated/0/Download/Claude").confidence)
        assertNotEquals("High", FileRules.classify("Qwen_Project_Plan.png", qwen, now,
            learned = LearnedSource(setOf("Claude"), 4)).confidence)
        assertNotEquals("High", FileRules.classify("Qwen_Project_Plan.png", qwen, now,
            learned = LearnedSource(setOf("Not AI"), 4)).confidence)
    }

    @Test fun learnedPatternWithoutExplicitProviderRemainsReviewOnly() {
        val result = FileRules.classify("Mountain_Asset_v03.png", qwen, now,
            learned = LearnedSource(setOf("Qwen"), 5))
        assertTrue(result.candidate)
        assertEquals("Qwen", result.source)
        assertEquals("Medium", result.confidence)
    }

    @Test fun cameraLocationOverridesOtherwiseStrongSourceClues() {
        assertFalse(FileRules.classify("Qwen_Project_Plan.png", qwen, now,
            "/storage/emulated/0/DCIM/Camera").candidate)
    }

    private fun project(id: Long, name: String) = Project(id, name, 0, 0, 0)

    @Test fun projectMatchingRequiresWholeOrderedProjectWords() {
        val projects = listOf(project(1, "Life Map"))
        assertNull(ProjectRules.classify("ChatGPT_Wildlife_Map.png", projects))
        assertNull(ProjectRules.classify("Qwen_Map_Of_Life.png", projects))
        assertEquals(1L, ProjectRules.classify("Qwen_LifeMap_Assets.png", projects)?.projectId)
    }

    @Test fun multipleExistingProjectNamesAreNotAutomaticallyDisambiguated() {
        val projects = listOf(project(1, "Life Map"), project(2, "Map"))
        assertNull(ProjectRules.classify("Qwen_Life_Map_Assets.png", projects))
    }

    @Test fun reviewedProjectCorrectionCapsOtherwiseExactMatch() {
        val projects = listOf(project(1, "Life Map"), project(2, "Other Work"))
        val corrected = LearningExample("one", "Life_Map_Assets_v01.png", 2, "Qwen", true)
        val suggestion = ProjectRules.suggest("Qwen_Life_Map_Assets_v02.png", projects, listOf(corrected))
        assertNotEquals("High", suggestion?.confidence)
    }

    @Test fun catchUpKnownPathsCameraAndManagedFoldersCannotAutoMove() {
        val finding = Finding(true, "Qwen", "High", "matching clues")
        val match = ProjectMatch(1, "Life Map", "High", "unique exact project")
        assertTrue(AutomaticOrganisationPolicy.permits(true, true, finding, match, "Downloads", false))
        assertFalse(AutomaticOrganisationPolicy.permits(false, true, finding, match, "Downloads", false))
        assertFalse(AutomaticOrganisationPolicy.permits(true, false, finding, match, "Downloads", false))
        assertFalse(AutomaticOrganisationPolicy.permits(true, true, finding, match, "Camera", false))
        assertFalse(AutomaticOrganisationPolicy.permits(true, true, finding, match, "Documents", true))
        assertFalse(AutomaticOrganisationPolicy.permits(true, true, finding.copy(confidence = "Medium"), match, "Downloads", false))
        assertFalse(AutomaticOrganisationPolicy.permits(true, true, finding, match.copy(confidence = "Medium"), "Downloads", false))
    }

    @Test fun repeatedExampleIdentityDoesNotPretendToBeIndependentSourceEvidence() {
        val example = LearningExample("same-file", "Mountain_Asset_v01.png", 1, "Qwen", true)
        assertNull(ProjectLearning.source("Mountain_Asset_v02.png", listOf(example, example, example)))
    }

    @Test fun aMoveNeverFollowsSourceSymlink() {
        val personal = temporary.newFile("personal").apply { writeText("keep this") }
        val source = File(temporary.root, "link")
        Files.createSymbolicLink(source.toPath(), personal.toPath())
        val target = File(temporary.root, "target")
        val original = requireNotNull(ContentFingerprint.read(personal))
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedFileTransfer().move(source, target, original.size, original.hash)
        }
        assertEquals("keep this", personal.readText())
        assertFalse(target.exists())
    }

    @Test fun sameContentSourceReplacementDuringCopyRequiresReview() {
        val source = temporary.newFile("source").apply { writeText("same bytes") }
        val fingerprint = requireNotNull(ContentFingerprint.read(source))
        val target = File(temporary.root, "target")
        val oldIdentity = File(temporary.root, "old-source")
        val transfer = VerifiedFileTransfer(afterCopy = { from, _ ->
            Files.move(from.toPath(), oldIdentity.toPath())
            from.writeText("same bytes")
            assertTrue(from.setLastModified(fingerprint.modified))
        })
        assertThrows(IOException::class.java) { transfer.move(source, target, fingerprint.size, fingerprint.hash) }
        assertTrue("A replacement path must not be removed", source.isFile)
        assertEquals("same bytes", source.readText())
        assertEquals("same bytes", target.readText())
    }

    @Test fun sameContentDestinationReplacementIsNotEvenRetimestamped() {
        val source = temporary.newFile("source").apply { writeText("same bytes") }
        assertTrue(source.setLastModified(1_600_000_000_000))
        val fingerprint = requireNotNull(ContentFingerprint.read(source))
        val target = File(temporary.root, "target")
        val ownedCopy = File(temporary.root, "owned-copy")
        val replacementTime = 1_700_000_000_000L
        val transfer = VerifiedFileTransfer(afterCopy = { _, to ->
            Files.move(to.toPath(), ownedCopy.toPath())
            to.writeText("same bytes")
            assertTrue(to.setLastModified(replacementTime))
        })
        assertThrows(IOException::class.java) { transfer.move(source, target, fingerprint.size, fingerprint.hash) }
        assertTrue(source.isFile)
        assertEquals(replacementTime, target.lastModified())
        assertEquals("same bytes", target.readText())
    }
}
