package com.tools.screenshot3.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotStatsParserTest {

    // --- appKeyFromDisplayName ---

    @Test
    fun appKey_extractsSimpleSuffix() {
        assertEquals("amazon", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000_amazon.png"))
    }

    @Test
    fun appKey_keepsSuffixWithUnderscores() {
        assertEquals(
            "google_maps",
            ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000_google_maps.png")
        )
    }

    @Test
    fun appKey_emptyWhenNoSuffix() {
        assertEquals("", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000.png"))
    }

    @Test
    fun appKey_nullForNonMatchingName() {
        assertNull(ScreenshotStatsParser.appKeyFromDisplayName("IMG_20240101.jpg"))
    }

    // --- collectionKeyFromRelativePath ---

    @Test
    fun collectionKey_rootIsEmpty() {
        assertEquals("", ScreenshotStatsParser.collectionKeyFromRelativePath("Pictures/Screenshot3/"))
        assertEquals("", ScreenshotStatsParser.collectionKeyFromRelativePath("Pictures/Screenshot3"))
    }

    @Test
    fun collectionKey_extractsSubdirectoryWithTrailingSlash() {
        assertEquals("movies", ScreenshotStatsParser.collectionKeyFromRelativePath("Pictures/Screenshot3/movies/"))
    }

    @Test
    fun collectionKey_extractsSubdirectoryWithoutTrailingSlash() {
        assertEquals("movies", ScreenshotStatsParser.collectionKeyFromRelativePath("Pictures/Screenshot3/movies"))
    }

    // --- titleCase ---

    @Test
    fun titleCase_splitsAndCapitalizes() {
        assertEquals("Google Maps", ScreenshotStatsParser.titleCase("google_maps"))
        assertEquals("Amazon", ScreenshotStatsParser.titleCase("amazon"))
    }

    // --- aggregate ---

    @Test
    fun aggregate_groupsByAppAndCollectionSortedByTotal() {
        val rows = listOf(
            "Screenshot_1_chrome.png" to "Pictures/Screenshot3/movies/",
            "Screenshot_2_chrome.png" to "Pictures/Screenshot3/shopping/",
            "Screenshot_3_chrome.png" to "Pictures/Screenshot3/shopping/",
            "Screenshot_4_amazon.png" to "Pictures/Screenshot3/shopping/",
            "Screenshot_5.png" to "Pictures/Screenshot3/",
            "IMG_unrelated.jpg" to "Pictures/Screenshot3/movies/"
        )

        val result = ScreenshotStatsParser.aggregate(rows)

        // chrome (3) > amazon (1) == unknown (1); chrome first by total.
        assertEquals(listOf("chrome", "amazon", ""), result.map { it.appKey })

        val chrome = result.first { it.appKey == "chrome" }
        assertEquals(3, chrome.total)
        assertEquals(mapOf("movies" to 1, "shopping" to 2), chrome.byCollection)

        val unknown = result.first { it.appKey == "" }
        assertEquals(1, unknown.total)
        assertEquals(mapOf("" to 1), unknown.byCollection)
    }

    @Test
    fun aggregate_ignoresNonScreenshotRows() {
        val rows = listOf("photo.png" to "Pictures/Screenshot3/movies/")
        assertEquals(emptyList<ScreenshotStatsParser.RawAppStat>(), ScreenshotStatsParser.aggregate(rows))
    }
}
