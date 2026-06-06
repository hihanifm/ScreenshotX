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
    private const val EXTENSION = ".png"
    private const val ROOT_DIR = "Screenshot3"

    /** One app bucket with its total and per-collection breakdown (all keyed, not labeled). */
    data class RawAppStat(
        val appKey: String,
        val total: Int,
        val byCollection: Map<String, Int>
    )

    /**
     * Extracts the sanitized app suffix from a screenshot DISPLAY_NAME.
     * Format: `Screenshot_<timestamp>_<appSuffix>.png` or `Screenshot_<timestamp>.png`.
     * Returns `""` when there is no app suffix (the "Unknown" bucket), or `null` when the
     * name does not match the screenshot scheme (caller ignores it).
     */
    fun appKeyFromDisplayName(displayName: String): String? {
        if (!displayName.startsWith(PREFIX)) return null
        val core = displayName.removePrefix(PREFIX).removeSuffix(EXTENSION)
        // core is "<timestamp>" or "<timestamp>_<suffix>"; the suffix itself may contain '_'.
        val firstUnderscore = core.indexOf('_')
        return if (firstUnderscore < 0) "" else core.substring(firstUnderscore + 1)
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
        for ((displayName, relativePath) in rows) {
            val appKey = appKeyFromDisplayName(displayName) ?: continue
            val collectionKey = collectionKeyFromRelativePath(relativePath)
            val byCollection = acc.getOrPut(appKey) { LinkedHashMap() }
            byCollection[collectionKey] = (byCollection[collectionKey] ?: 0) + 1
        }
        return acc.map { (appKey, byCollection) ->
            RawAppStat(appKey, byCollection.values.sum(), byCollection.toMap())
        }.sortedByDescending { it.total }
    }
}
