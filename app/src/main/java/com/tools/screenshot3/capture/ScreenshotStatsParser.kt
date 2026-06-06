package com.tools.screenshot3.capture

import java.util.Locale

/**
 * Pure, Context-free parsing + aggregation for per-app screenshot statistics.
 *
 * Stats are derived live from the data MediaStore already holds: the foreground app suffix
 * baked into each screenshot's DISPLAY_NAME (see [ScreenshotFilenameFormatter]) and the
 * collection encoded in its RELATIVE_PATH. No separate bookkeeping, so counts cannot drift.
 */
object ScreenshotStatsParser {

    private const val PREFIX = "Screenshot_"
    private const val ROOT_DIR = "Screenshot3"
    private val VALID_EXTENSIONS = setOf(".png", ".jpg")
    private const val SCROLL_MARKER = ScreenshotFilenameFormatter.SCROLL_MARKER

    /** One app bucket with its total and per-collection breakdown (all keyed, not labeled). */
    data class RawAppStat(
        val appKey: String,
        val total: Int,
        val byCollection: Map<String, Int>,
        /** Subset of [byCollection] that came from scroll captures (the rest are regular). */
        val scrollByCollection: Map<String, Int>
    )

    /**
     * The post-timestamp/post-extension portion of a screenshot DISPLAY_NAME, or `null` when the
     * name does not match the screenshot scheme. For `Screenshot_<ts>_<rest>.ext` returns `<rest>`;
     * for `Screenshot_<ts>.ext` returns `""`. `<rest>` may contain '_' and may carry a scroll marker.
     */
    private fun coreSuffix(displayName: String): String? {
        if (!displayName.startsWith(PREFIX)) return null
        val extension = VALID_EXTENSIONS.firstOrNull { displayName.endsWith(it, ignoreCase = true) }
            ?: return null
        val core = displayName.removePrefix(PREFIX).removeSuffix(extension)
        val firstUnderscore = core.indexOf('_')
        return if (firstUnderscore < 0) "" else core.substring(firstUnderscore + 1)
    }

    /**
     * True when the screenshot was saved as a stitched scroll capture, i.e. its name carries the
     * trailing `_scroll` marker (or is exactly `Screenshot_<ts>_scroll.ext`). Returns false for
     * names that don't match the screenshot scheme.
     * Caveat: an app whose sanitized name is literally `scroll` (or ends in `_scroll`) would be
     * misread as a scroll capture — negligible in practice.
     */
    fun isScrollCapture(displayName: String): Boolean {
        val rest = coreSuffix(displayName) ?: return false
        return rest == SCROLL_MARKER || rest.endsWith("_$SCROLL_MARKER")
    }

    /**
     * Extracts the sanitized app suffix from a screenshot DISPLAY_NAME, stripping any scroll marker.
     * Format: `Screenshot_<timestamp>_<appSuffix>.(png|jpg)` (optionally `_scroll` before the ext)
     * or `Screenshot_<timestamp>.(png|jpg)`. Returns `""` when there is no app suffix (the
     * "Unknown" bucket), or `null` when the name does not match the screenshot scheme.
     */
    fun appKeyFromDisplayName(displayName: String): String? {
        val rest = coreSuffix(displayName) ?: return null
        return when {
            rest == SCROLL_MARKER -> ""
            rest.endsWith("_$SCROLL_MARKER") -> rest.removeSuffix("_$SCROLL_MARKER")
            else -> rest
        }
    }

    /**
     * Extracts the collection key (sanitized subdirectory) from a MediaStore RELATIVE_PATH.
     * `Pictures/Screenshot3/` or `Pictures/Screenshot3` -> `""` (default root collection);
     * `Pictures/Screenshot3/movies/` -> `"movies"`.
     */
    fun collectionKeyFromRelativePath(relativePath: String): String =
        relativePath.substringAfter("$ROOT_DIR/", missingDelimiterValue = "").trim('/')

    /** Title-cases a sanitized key for display: `google_maps` -> "Google Maps". */
    fun titleCase(key: String): String =
        key.split('_', '-')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }

    /**
     * Aggregates raw (displayName, relativePath) rows into per-app buckets, each with a
     * per-collection breakdown, sorted by total descending. Rows whose display name does
     * not match the screenshot scheme are ignored.
     */
    fun aggregate(rows: List<Pair<String, String>>): List<RawAppStat> {
        val acc = LinkedHashMap<String, MutableMap<String, Int>>()
        val scrollAcc = LinkedHashMap<String, MutableMap<String, Int>>()
        for ((displayName, relativePath) in rows) {
            val appKey = appKeyFromDisplayName(displayName) ?: continue
            val collectionKey = collectionKeyFromRelativePath(relativePath)
            val byCollection = acc.getOrPut(appKey) { LinkedHashMap() }
            byCollection[collectionKey] = (byCollection[collectionKey] ?: 0) + 1
            if (isScrollCapture(displayName)) {
                val scrollByCollection = scrollAcc.getOrPut(appKey) { LinkedHashMap() }
                scrollByCollection[collectionKey] = (scrollByCollection[collectionKey] ?: 0) + 1
            }
        }
        return acc.map { (appKey, byCollection) ->
            RawAppStat(
                appKey = appKey,
                total = byCollection.values.sum(),
                byCollection = byCollection.toMap(),
                scrollByCollection = scrollAcc[appKey]?.toMap() ?: emptyMap()
            )
        }.sortedByDescending { it.total }
    }

    /** One collection bucket with its total, per-app breakdown (app keys), and scroll subtotal. */
    data class RawCategoryStat(
        val categoryKey: String,
        val total: Int,
        val byApp: Map<String, Int>,
        /** How many of [total] were scroll captures (the rest are regular). */
        val scrollTotal: Int
    )

    /**
     * Transpose of [ScreenCaptureManager.AppStat]: regroups app -> collection counts into
     * collection -> app counts, sorted by total descending. Same numbers, just inverted, so
     * the category view never disagrees with the app view. Label resolution stays in the UI.
     * Also carries each category's scroll subtotal from [ScreenCaptureManager.AppStat.scrollByCollection].
     */
    fun invertToCategories(apps: List<ScreenCaptureManager.AppStat>): List<RawCategoryStat> {
        val acc = LinkedHashMap<String, MutableMap<String, Int>>()
        val scrollAcc = LinkedHashMap<String, Int>()
        for (app in apps) {
            for ((collectionKey, count) in app.byCollection) {
                val byApp = acc.getOrPut(collectionKey) { LinkedHashMap() }
                byApp[app.appKey] = (byApp[app.appKey] ?: 0) + count
            }
            for ((collectionKey, scrollCount) in app.scrollByCollection) {
                scrollAcc[collectionKey] = (scrollAcc[collectionKey] ?: 0) + scrollCount
            }
        }
        return acc.map { (categoryKey, byApp) ->
            RawCategoryStat(
                categoryKey = categoryKey,
                total = byApp.values.sum(),
                byApp = byApp.toMap(),
                scrollTotal = scrollAcc[categoryKey] ?: 0
            )
        }.sortedByDescending { it.total }
    }
}
