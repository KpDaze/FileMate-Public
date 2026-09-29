package app.filemate

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class GalleryScreenshotRulesTest {
    private val zone = ZoneId.of("Australia/Brisbane")
    private val time = Instant.parse("2026-09-29T05:00:00Z").toEpochMilli()
    private fun item(id: String) = ScreenshotCandidate(id,"primary","Pictures/Screenshots/",time,true,false,false,true,false)
    @Test fun sameDayAndFolderGroupWithoutGuessingAProject() {
        val groups = GalleryScreenshotRules.groups(listOf(item("a"),item("b").copy(time = time + 60000)),true,zone)
        assertEquals(1,groups.size);assertEquals(listOf("b","a"),groups.single().identities)
        assertEquals("2026-09-29",groups.single().day)
    }
    @Test fun intakeExcludesAssignedUnavailableCameraVideoAndDimensionOnlyClues() {
        val a = item("a")
        val items = listOf(a,a.copy(identity="assigned",assigned=true),a.copy(identity="absent",available=false),
            a.copy(identity="camera",camera=true),a.copy(identity="video",video=true),a.copy(identity="shape",screenshot=false))
        assertEquals(listOf("a"),GalleryScreenshotRules.groups(items,true,zone).flatMap { it.identities })
        assertEquals(setOf("a","assigned"),GalleryScreenshotRules.groups(items,false,zone).flatMap { it.identities }.toSet())
    }
    @Test fun foldersVolumesAndDaysStaySeparate() {
        val a = item("a")
        assertEquals(4,GalleryScreenshotRules.groups(listOf(a,a.copy(identity="b",folder="Download/"),
            a.copy(identity="c",volume="sdcard"),a.copy(identity="d",time=time-86400000)),true,zone).size)
    }
    @Test fun localMidnightDefinesDateBoundary() {
        val a = item("a").copy(time=Instant.parse("2026-09-28T13:59:59Z").toEpochMilli())
        val b = a.copy(identity="b",time=a.time+1000)
        assertEquals(listOf("2026-09-29","2026-09-28"),GalleryScreenshotRules.groups(listOf(a,b),true,zone).map { it.day })
    }
    @Test fun unknownDateOrFolderNeverSuggestsARelationship() {
        val a = item("a").copy(time=0)
        assertEquals(2,GalleryScreenshotRules.groups(listOf(a,a.copy(identity="b")),true,zone).size)
        val b = item("c").copy(folder="")
        assertEquals(2,GalleryScreenshotRules.groups(listOf(b,b.copy(identity="d")),true,zone).size)
        assertEquals("Date unavailable",GalleryScreenshotRules.groups(listOf(a),true,zone).single().day)
    }
    @Test fun refreshDoesNotDuplicateAndAssignmentClearRestoresIntake() {
        val a = item("a")
        assertEquals(1,GalleryScreenshotRules.groups(listOf(a,a),true,zone).single().identities.size)
        assertTrue(GalleryScreenshotRules.groups(listOf(a.copy(assigned=true)),true,zone).isEmpty())
        assertEquals(listOf("a"),GalleryScreenshotRules.groups(listOf(a),true,zone).single().identities)
    }
    @Test fun groupIdentitySurvivesAssignmentAndInputOrdering() {
        val a = item("a");val b = item("b")
        val before = GalleryScreenshotRules.groups(listOf(a,b),true,zone).single().key
        assertEquals(before,GalleryScreenshotRules.groups(listOf(b,a.copy(assigned=true)),true,zone).single().key)
    }
    @Test fun allScreenshotGroupsKeepAssignedMediaButStillExcludeCamera() {
        val a = item("a").copy(assigned=true)
        assertEquals(listOf("a"),GalleryScreenshotRules.groups(listOf(a,a.copy(identity="camera",camera=true)),false,zone).single().identities)
    }
}
