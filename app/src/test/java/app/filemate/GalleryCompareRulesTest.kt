package app.filemate

import org.junit.Assert.*
import org.junit.Test

class GalleryCompareRulesTest {
    private fun pixels(delta: Int = 0) = IntArray(72) { i -> val c = (i*3+delta).coerceIn(0,255);(c shl 16) or (c shl 8) or c }
    private fun item(id: String, hash: String = id, name: String = "$id.png", p: IntArray? = pixels(), folder: String = "Pictures/") =
        ImageSignature(id,name,folder,123,"1:2:123",GalleryCompareRules.digest(hash),900,800,p)
    @Test fun exactRequiresFullValidHashAndSize() {
        val a = item("a", "same");val b = item("b","same")
        assertEquals(listOf("a","b"),GalleryCompareRules.matches(listOf(a,b)).single().ids)
        assertTrue(GalleryCompareRules.matches(listOf(a,b.copy(size=124,pixels=null),item("bad").copy(sha256=""))).none { it.kind == ImageMatchKind.EXACT })
    }
    @Test fun identityDeduplicatedAndSingletonExcluded() {
        assertTrue(GalleryCompareRules.matches(listOf(item("a"),item("a"))).isEmpty())
    }
    @Test fun similarIsSeparateAndBlankImagesAreNotEvidence() {
        val groups = GalleryCompareRules.matches(listOf(item("a"),item("b",p=pixels(3))))
        assertEquals(ImageMatchKind.SIMILAR,groups.single().kind)
        assertFalse(GalleryCompareRules.similar(item("a",p=IntArray(72)),item("b",p=IntArray(72))))
    }
    @Test fun aspectAndColourDifferencesRejectSimilarity() {
        assertFalse(GalleryCompareRules.similar(item("a"),item("b").copy(width=300)))
        assertFalse(GalleryCompareRules.similar(item("a"),item("b",p=pixels(80))))
        assertFalse(GalleryCompareRules.similar(item("a"),item("b",p=null)))
    }
    @Test fun versionNamesRequireSameFolderAndMeaningfulStem() {
        assertEquals("garden",GalleryCompareRules.versionStem("Garden_v2 (1).PNG"))
        val a = item("a",name="Garden.png",p=null)
        val b = item("b",name="Garden_v2.png",p=null)
        assertEquals(ImageMatchKind.VERSION,GalleryCompareRules.matches(listOf(a,b)).single().kind)
        assertTrue(GalleryCompareRules.matches(listOf(a,b.copy(folder="Camera/"))).isEmpty())
        assertTrue(GalleryCompareRules.matches(listOf(a.copy(name="a.png"),b.copy(name="a_v2.png"))).isEmpty())
    }
    @Test fun stableKeepKeyChangesWhenContentOrMetadataChanges() {
        val a = item("a");val b = item("b")
        val key = GalleryCompareRules.group(ImageMatchKind.SIMILAR,listOf(a,b)).key
        assertEquals(key,GalleryCompareRules.group(ImageMatchKind.SIMILAR,listOf(b,a)).key)
        assertNotEquals(key,GalleryCompareRules.group(ImageMatchKind.SIMILAR,listOf(a,b.copy(stamp="changed"))).key)
        assertNotEquals(key,GalleryCompareRules.group(ImageMatchKind.SIMILAR,listOf(a,b.copy(sha256=GalleryCompareRules.digest("edited")))).key)
    }
    @Test fun approximatePairsDoNotClaimTransitiveGroups() {
        val groups = GalleryCompareRules.matches(listOf(item("a"),item("b"),item("c")))
        assertEquals(3,groups.size);assertTrue(groups.all { it.ids.size == 2 })
    }
    @Test fun heuristicWorkIsBoundedButExactMatchesContinue() {
        val input = (0..501).map { item("$it",hash=if(it>=500) "duplicate" else "$it",p=null) }
        val groups = GalleryCompareRules.matches(input)
        assertEquals(listOf("500","501"),groups.single().ids)
        var cancelled = false
        try { GalleryCompareRules.matches(input) { error("cancelled") } } catch(_: IllegalStateException) { cancelled = true }
        assertTrue(cancelled)
    }
}
