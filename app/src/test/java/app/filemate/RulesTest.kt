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
}
