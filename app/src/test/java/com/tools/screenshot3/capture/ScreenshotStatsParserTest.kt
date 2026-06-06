package com.tools.screenshot3.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotStatsParserTest {

    // --- appKeyFromDisplayName ---

    @Test
    fun appKey_extractsSimpleSuffix() {
        assertEquals("amazon", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000_amazon.jpg"))
    }

    @Test
    fun appKey_keepsSuffixWithUnderscores() {
        assertEquals(
            "google_maps",
            ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000_google_maps.jpg")
        )
    }

    @Test
    fun appKey_emptyWhenNoSuffix() {
        assertEquals("", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000.jpg"))
    }

    @Test
    fun appKey_acceptsLegacyPngNames() {
        assertEquals("amazon", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1717556400000_amazon.png"))
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
            "Screenshot_1_chrome.jpg" to "Pictures/Screenshot3/movies/",
            "Screenshot_2_chrome.png" to "Pictures/Screenshot3/shopping/",
            "Screenshot_3_chrome.jpg" to "Pictures/Screenshot3/shopping/",
            "Screenshot_4_amazon.jpg" to "Pictures/Screenshot3/shopping/",
            "Screenshot_5.jpg" to "Pictures/Screenshot3/",
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

    // --- invertToCategories ---

    private fun appStat(key: String, byCollection: Map<String, Int>) =
        ScreenCaptureManager.AppStat(
            appKey = key,
            appLabel = key,
            total = byCollection.values.sum(),
            byCollection = byCollection
        )

    @Test
    fun invert_transposesToCategoriesSortedByTotal() {
        val apps = listOf(
            appStat("chrome", mapOf("movies" to 1, "shopping" to 2)),
            appStat("amazon", mapOf("shopping" to 1)),
            appStat("", mapOf("" to 1))
        )

        val categories = ScreenshotStatsParser.invertToCategories(apps)

        // shopping (3) > movies (1) == root "" (1); shopping first.
        assertEquals(listOf("shopping", "movies", ""), categories.map { it.categoryKey })

        val shopping = categories.first { it.categoryKey == "shopping" }
        assertEquals(3, shopping.total)
        assertEquals(mapOf("chrome" to 2, "amazon" to 1), shopping.byApp)
    }

    @Test
    fun invert_totalsReconcileWithAppTotals() {
        val apps = listOf(
            appStat("chrome", mapOf("movies" to 1, "shopping" to 2)),
            appStat("amazon", mapOf("shopping" to 1))
        )

        val appsTotal = apps.sumOf { it.total }
        val categoriesTotal = ScreenshotStatsParser.invertToCategories(apps).sumOf { it.total }

        assertEquals(appsTotal, categoriesTotal)
    }

    // --- scroll marker ---

    @Test
    fun isScrollCapture_detectsTrailingMarker() {
        assertTrue(ScreenshotStatsParser.isScrollCapture("Screenshot_1717556400000_amazon_scroll.jpg"))
        assertTrue(ScreenshotStatsParser.isScrollCapture("Screenshot_1717556400000_scroll.png"))
    }

    @Test
    fun isScrollCapture_falseForRegularAndNonMatching() {
        assertFalse(ScreenshotStatsParser.isScrollCapture("Screenshot_1717556400000_amazon.jpg"))
        assertFalse(ScreenshotStatsParser.isScrollCapture("Screenshot_1717556400000.jpg"))
        assertFalse(ScreenshotStatsParser.isScrollCapture("IMG_20240101.jpg"))
    }

    @Test
    fun appKey_stripsScrollMarker() {
        assertEquals("amazon", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1_amazon_scroll.jpg"))
        assertEquals("google_maps", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1_google_maps_scroll.png"))
        // bare scroll capture (no app) maps to the Unknown bucket.
        assertEquals("", ScreenshotStatsParser.appKeyFromDisplayName("Screenshot_1_scroll.jpg"))
    }

    @Test
    fun aggregate_populatesScrollByCollection() {
        val rows = listOf(
            "Screenshot_1_chrome.jpg" to "Pictures/Screenshot3/movies/",
            "Screenshot_2_chrome_scroll.jpg" to "Pictures/Screenshot3/movies/",
            "Screenshot_3_chrome_scroll.jpg" to "Pictures/Screenshot3/shopping/"
        )

        val chrome = ScreenshotStatsParser.aggregate(rows).first { it.appKey == "chrome" }

        assertEquals(mapOf("movies" to 2, "shopping" to 1), chrome.byCollection)
        assertEquals(mapOf("movies" to 1, "shopping" to 1), chrome.scrollByCollection)
    }

    @Test
    fun invert_carriesScrollTotalPerCategory() {
        val apps = listOf(
            ScreenCaptureManager.AppStat(
                appKey = "chrome",
                appLabel = "chrome",
                total = 3,
                byCollection = mapOf("movies" to 2, "shopping" to 1),
                scrollByCollection = mapOf("movies" to 1, "shopping" to 1)
            ),
            ScreenCaptureManager.AppStat(
                appKey = "amazon",
                appLabel = "amazon",
                total = 1,
                byCollection = mapOf("movies" to 1),
                scrollByCollection = mapOf("movies" to 1)
            )
        )

        val categories = ScreenshotStatsParser.invertToCategories(apps)

        val movies = categories.first { it.categoryKey == "movies" }
        assertEquals(3, movies.total)
        assertEquals(2, movies.scrollTotal)

        val shopping = categories.first { it.categoryKey == "shopping" }
        assertEquals(1, shopping.total)
        assertEquals(1, shopping.scrollTotal)
    }
}
