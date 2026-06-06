package com.tools.screenshot3.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotFilenameFormatterTest {

    @Test
    fun sanitizeFileSegment_normalizesSimpleAppLabel() {
        assertEquals("amazon", ScreenshotFilenameFormatter.sanitizeFileSegment("Amazon"))
    }

    @Test
    fun sanitizeFileSegment_replacesSpacesAndPunctuation() {
        assertEquals(
            "amazon_shopping",
            ScreenshotFilenameFormatter.sanitizeFileSegment("Amazon Shopping!!")
        )
    }

    @Test
    fun sanitizeFileSegment_returnsEmptyForInvalidOnlyInput() {
        assertEquals("", ScreenshotFilenameFormatter.sanitizeFileSegment("!!!"))
    }

    @Test
    fun buildScreenshotFilename_appendsSuffixWhenAvailable() {
        assertEquals(
            "Screenshot_1717556400000_amazon.jpg",
            ScreenshotFilenameFormatter.buildScreenshotFilename(
                timestamp = 1717556400000L,
                appSuffix = "Amazon"
            )
        )
    }

    @Test
    fun buildScreenshotFilename_fallsBackToTimestampOnly() {
        assertEquals(
            "Screenshot_1717556400000.jpg",
            ScreenshotFilenameFormatter.buildScreenshotFilename(
                timestamp = 1717556400000L,
                appSuffix = "!!!"
            )
        )
    }

    @Test
    fun buildScreenshotFilename_usesPngWhenRequested() {
        assertEquals(
            "Screenshot_1717556400000_amazon.png",
            ScreenshotFilenameFormatter.buildScreenshotFilename(
                timestamp = 1717556400000L,
                appSuffix = "Amazon",
                format = ScreenshotImageFormat.PNG
            )
        )
    }
}
