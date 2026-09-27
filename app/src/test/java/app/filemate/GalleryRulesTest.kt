package app.filemate

import org.junit.Assert.*
import org.junit.Test

class GalleryRulesTest {
    @Test fun screenshotFolderIsAClueNotCameraEvenInsideDcim() {
        val c = GalleryRules.clues("IMG_1.png","DCIM/Screenshots/","Screenshots",1080,1920,false)
        assertTrue(c.screenshot);assertFalse(c.camera);assertEquals("High",c.screenshotConfidence)
        assertTrue(c.explanation.contains("not proof"))
    }
    @Test fun ordinaryCameraPhotoWithScreenShapeStaysCamera() {
        val c = GalleryRules.clues("IMG_20260927.jpg","DCIM/Camera/","Camera",1080,1920,false)
        assertTrue(c.camera);assertFalse(c.screenshot);assertEquals("Low",c.screenshotConfidence)
    }
    @Test fun nameClueIsCaseInsensitiveAndDownloadsStayDownloads() {
        val c = GalleryRules.clues("SCREENSHOT_2026.png","Download/","Download",120,180,false)
        assertTrue(c.screenshot);assertTrue(c.downloads);assertEquals("Medium",c.screenshotConfidence)
    }
    @Test fun embeddedWordDoesNotTurnOrdinaryImageIntoScreenshot() {
        assertFalse(GalleryRules.clues("my_screenshot_art.png","Pictures/","Pictures",500,500,false).screenshot)
        assertFalse(GalleryRules.clues("screenshotastic.png","Pictures/","Pictures",500,500,false).screenshot)
    }
    @Test fun videoIsNotAScreenshotEvenWithScreenshotName() {
        val c = GalleryRules.clues("Screenshot_1.mp4","Pictures/Screenshots/","Screenshots",1080,1920,true)
        assertFalse(c.screenshot);assertEquals("Not assessed",c.screenshotConfidence)
    }
    @Test fun missingDimensionsAndPathRemainUnclassified() {
        val c = GalleryRules.clues("image.png","","",0,0,false)
        assertFalse(c.screenshot);assertFalse(c.camera);assertFalse(c.downloads);assertEquals("Unknown",c.screenshotConfidence)
    }
    @Test fun orderingPrefersTakenThenAddedThenModifiedWithSafeFallback() {
        assertEquals(500L,GalleryRules.chronologicalTime(500,700,900))
        assertEquals(700000L,GalleryRules.chronologicalTime(0,700,900))
        assertEquals(900000L,GalleryRules.chronologicalTime(0,0,900))
        assertEquals(0L,GalleryRules.chronologicalTime(-1,Long.MAX_VALUE,-5))
    }
    @Test fun recycledIdsAndIndependentVolumesCannotShareIdentity() {
        val a = GalleryRules.identity("primary","v1","image",1,2)
        assertNotEquals(a,GalleryRules.identity("primary","v2","image",1,2))
        assertNotEquals(a,GalleryRules.identity("primary","v1","image",1,3))
        assertNotEquals(a,GalleryRules.identity("sdcard","v1","image",1,2))
        assertNotEquals(a,GalleryRules.identity("primary","v1","video",1,2))
        assertNotEquals(GalleryRules.identity("a|b","c","image",1,2),GalleryRules.identity("a","b|c","image",1,2))
    }
}
