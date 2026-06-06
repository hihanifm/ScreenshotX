package com.tools.screenshot3.capture

import java.util.Locale

object ScreenshotFilenameFormatter {

    /** Trailing token marking a stitched scroll capture; parsed back out by [ScreenshotStatsParser]. */
    const val SCROLL_MARKER = "scroll"

    fun buildScreenshotFilename(
        timestamp: Long = System.currentTimeMillis(),
        appSuffix: String? = null,
        format: ScreenshotImageFormat = ScreenshotImageFormat.default,
        isScroll: Boolean = false
    ): String {
        val sanitizedSuffix = sanitizeFileSegment(appSuffix).ifEmpty { null }
        val scrollToken = if (isScroll) "_$SCROLL_MARKER" else ""
        return if (sanitizedSuffix != null) {
            "Screenshot_${timestamp}_${sanitizedSuffix}${scrollToken}${format.extension}"
        } else {
            "Screenshot_${timestamp}${scrollToken}${format.extension}"
        }
    }

    fun sanitizeFileSegment(input: String?): String =
        input.orEmpty()
            .trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
}
