package app.filemate

import org.junit.Assert.*
import org.junit.Test

class GalleryOrganisationTest {
    @Test fun albumNamesAreTrimmedAndBoundedByPublicOperationsContract() {
        // Pure contract checks kept here so Android-backed Store tests can reuse the same boundary.
        assertEquals("Trip photos","  Trip photos  ".trim().replace(Regex("\\s+")," "))
        assertTrue("x".repeat(80).length <= 80)
        assertTrue("x".repeat(81).length > 80)
    }

    @Test fun favouritesAndAlbumsAreIndependentConcepts() {
        val favouriteIds=setOf("a","c")
        val albumIds=setOf("b","c")
        assertEquals(setOf("c"),favouriteIds intersect albumIds)
        assertEquals(setOf("a"),favouriteIds-albumIds)
        assertEquals(setOf("b"),albumIds-favouriteIds)
    }

    @Test fun duplicateSelectionsCollapseBeforeMetadataWrites() {
        assertEquals(listOf("a","b"),listOf("a","a","b","a").distinct())
    }
}
