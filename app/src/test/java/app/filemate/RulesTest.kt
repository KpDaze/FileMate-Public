package app.filemate

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private val now = 1_000_000L
    private val qwen = AiContext("Qwen","example.qwen",now - 3000)
    @Test fun sourceAgreementRaisesConfidenceButDoesNotCreateProjectOrMoveRule() {
        val result = FileRules.classify("Qwen_Mountain.png",qwen,now)
        assertTrue(result.candidate);assertEquals("High",result.confidence);assertEquals("Qwen",result.source)
    }
    @Test fun ambiguousTimingRemainsLowConfidence() {
        val result = FileRules.classify("image_001.png",qwen,now)
        assertTrue(result.candidate);assertEquals("Low",result.confidence)
    }
    @Test fun unrelatedAndTemporaryDownloadsAreIgnoredDuringAiActivity() {
        listOf("bank_statement.pdf","receipt.pdf","new_app.apk","image.png.crdownload",".pending-1.png").forEach {
            assertFalse(it,FileRules.classify(it,qwen,now).candidate)
        }
    }
    @Test fun catchUpDoesNotInventAiSourcesFromGenericNames() {
        assertFalse(FileRules.classify("photo.png",null,now).candidate)
        assertEquals("Medium",FileRules.classify("ChatGPT-export.md",null,now).confidence)
    }
    @Test fun staleOrFutureActivityDoesNotAttributeSource() {
        assertFalse(FileRules.classify("image.png",qwen.copy(lastUsedAt = now - 120_001),now).candidate)
        assertFalse(FileRules.classify("image.png",qwen.copy(lastUsedAt = now + 1),now).candidate)
    }
    @Test fun mismatchedFilenameAndAppNeverBecomeHighConfidence() {
        val finding = FileRules.classify("Claude-notes.md",qwen,now)
        assertEquals("Medium",finding.confidence);assertEquals("Claude",finding.source)
    }
    @Test fun inactivityExpiresAtBoundaryAndSwitchingToSelectedAiExtendsSession() {
        val clock = SessionClock();clock.touch(1000)
        assertFalse(clock.expired(1_800_999));assertTrue(clock.expired(1_801_000))
        clock.touch(1_700_000);assertFalse(clock.expired(1_801_000));assertTrue(clock.expired(3_500_000))
    }
    @Test fun catchUpUsesPathLookupAndSizeNotJustModificationDate() {
        val stamp = FileStamp(10,123)
        assertTrue(FileRules.changed(null,stamp)) // Newly copied path with an old date.
        assertFalse(FileRules.changed(stamp,stamp))
        assertTrue(FileRules.changed(stamp,FileStamp(11,123)))
        assertTrue(FileRules.changed(stamp,FileStamp(10,124)))
    }
    @Test fun projectMatchingRequiresOneUnambiguousFullProjectName() {
        val projects = listOf(
            Project(1,"Killerfect Security",0,0,0),
            Project(2,"House",0,0,0)
        )
        val match = ProjectRules.classify("ChatGPT_Killerfect-Security_quote.pdf",projects)
        assertNotNull(match);assertEquals(1,match!!.projectId);assertEquals("High",match.confidence)
        assertNull(ProjectRules.classify("ChatGPT_notes.pdf",projects))
    }
    @Test fun overlappingProjectNamesDoNotAutoChoose() {
        val projects = listOf(
            Project(1,"House",0,0,0),
            Project(2,"House Renovation",0,0,0)
        )
        assertNull(ProjectRules.classify("House_Renovation_plan.pdf",projects))
    }

    @Test fun projectMatchingDoesNotGuessFromPartialOrGenericNames() {
        val projects = listOf(
            Project(1,"Life Map",0,0,0),
            Project(2,"FileMate",0,0,0)
        )
        assertNull(ProjectRules.classify("ChatGPT_map_notes.pdf",projects))
        assertNull(ProjectRules.classify("Qwen_export.pdf",projects))
        assertEquals(1,ProjectRules.classify("ChatGPT_Life_Map_notes.pdf",projects)?.projectId)
    }

    @Test fun projectLearningIgnoresProviderAndGenericNoise() {
        assertEquals(listOf("killerfect","security","quote"),ProjectLearning.tokens("ChatGPT_Killerfect-Security_quote.pdf"))
        assertEquals(emptyList<String>(),ProjectLearning.tokens("Qwen_export_20261004.pdf"))
    }

    @Test fun driveAfterUploadNeverRemovesLocalBeforeVerifiedSuccess() {
        assertFalse(DriveRules.mayRemoveLocal(uploadVerified = false,driveAfterUpload = true))
        assertFalse(DriveRules.mayRemoveLocal(uploadVerified = true,driveAfterUpload = false))
        assertTrue(DriveRules.mayRemoveLocal(uploadVerified = true,driveAfterUpload = true))
        assertEquals(StorageRule.PHONE_ONLY,DriveRules.parse(null))
        assertEquals(StorageRule.PHONE_ONLY,DriveRules.parse("nonsense"))
    }

    @Test fun monitoringTimeoutIsBoundedAndDefaultsSafely() {
        assertEquals(30L,MonitoringSettings.minutes(null))
        assertEquals(30L,MonitoringSettings.minutes("999"))
        assertEquals(15L,MonitoringSettings.minutes("15"))
        assertEquals(60L,MonitoringSettings.minutes("60"))
    }

    @Test fun namingPreferenceDefaultsToPreservingNames() {
        assertEquals(NamingPreference.KEEP_CURRENT,NamingSettings.parse(null))
        assertEquals(NamingPreference.KEEP_CURRENT,NamingSettings.parse("broken"))
        assertEquals(NamingPreference.TIDY_WHEN_REVIEWED,NamingSettings.parse("TIDY_WHEN_REVIEWED"))
    }

    @Test fun projectNamesAreManualCleanAndBounded() {
        assertEquals("Family Holiday",ProjectNames.clean("  Family   Holiday\n"))
        assertThrows(IllegalArgumentException::class.java) { ProjectNames.clean("   ") }
        assertThrows(IllegalArgumentException::class.java) { ProjectNames.clean("x".repeat(ProjectNames.MAX_LENGTH + 1)) }
    }
    @Test fun cleanupSuggestionsAreReviewOnlyAndConservativeWithPhotos() {
        val scanNow = 500L * 24 * 60 * 60 * 1000
        val old = scanNow - 366L * 24 * 60 * 60 * 1000
        val camera = CleanupRules.flags("photo.jpg","Camera",10,old,false,false,scanNow)
        assertEquals(0,camera and CleanupFlags.OLD)
        val archive = CleanupRules.flags("ChatGPT-export.zip","Downloads",10,old,false,false,scanNow)
        assertTrue(archive and CleanupFlags.LIKELY_AI != 0)
        assertTrue(archive and CleanupFlags.UNSORTED_DOWNLOAD != 0)
        assertTrue(archive and CleanupFlags.OLD != 0)
        assertTrue(archive and CleanupFlags.ARCHIVE != 0)
    }
    @Test fun tidyNamesStayFilesystemSafeAndKeepTheExtension() {
        val name = FileNaming.tidy("Tax / 2026","invoice: final.PDF",1_000_000_000_000)
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertTrue(name.startsWith("Tax_2026_"))
        assertTrue(name.endsWith(".PDF"))
        assertFalse(FileNaming.folder("Tax / 2026").contains('/'))
    }
}
