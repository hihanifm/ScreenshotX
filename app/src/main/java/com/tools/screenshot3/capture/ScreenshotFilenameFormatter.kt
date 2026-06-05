package com.tools.screenshot3.capture

import java.util.Locale

object ScreenshotFilenameFormatter {

    fun buildScreenshotFilename(
        timestamp: Long = System.currentTimeMillis(),
        appSuffix: String? = null
    ): String {
        val sanitizedSuffix = sanitizeFileSegment(appSuffix).ifEmpty { null }
        return if (sanitizedSuffix != null) {
            "Screenshot_${timestamp}_${sanitizedSuffix}.png"
        } else {
            "Screenshot_${timestamp}.png"
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
