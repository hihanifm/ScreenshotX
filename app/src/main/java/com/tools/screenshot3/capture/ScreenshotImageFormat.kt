package com.tools.screenshot3.capture

import android.graphics.Bitmap

enum class ScreenshotImageFormat(
    val storageValue: String,
    val extension: String,
    val mimeType: String,
    val compressFormat: Bitmap.CompressFormat,
    val compressQuality: Int
) {
    JPEG(
        storageValue = "jpeg",
        extension = ".jpg",
        mimeType = "image/jpeg",
        compressFormat = Bitmap.CompressFormat.JPEG,
        compressQuality = 95
    ),
    PNG(
        storageValue = "png",
        extension = ".png",
        mimeType = "image/png",
        compressFormat = Bitmap.CompressFormat.PNG,
        compressQuality = 100
    );

    companion object {
        val default: ScreenshotImageFormat = JPEG

        fun fromStorageValue(value: String?): ScreenshotImageFormat =
            entries.firstOrNull { it.storageValue == value } ?: default
    }
}
