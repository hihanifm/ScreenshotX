package com.tools.screenshot3.scroll

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.tools.screenshot3.capture.ScreenCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScrollCaptureSession(initialBitmap: Bitmap) {

    private var stitchedBitmap: Bitmap? = initialBitmap
    var scrollCount: Int = 0
        private set
    var isActive: Boolean = true
        private set

    suspend fun scrollAndCapture(context: Context): ScrollResult {
        if (!isActive) return ScrollResult.Error("Session not active")
        if (scrollCount >= MAX_SCROLLS) return ScrollResult.LimitReached

        val service = ScrollCaptureAccessibilityService.instance
            ?: return ScrollResult.Error("Accessibility service not connected")

        val metrics = context.resources.displayMetrics
        val scrolled = service.scrollDown(metrics.heightPixels, metrics.widthPixels)
        if (!scrolled) return ScrollResult.Error("Scroll gesture failed")

        delay(SETTLE_DELAY_MS)

        val newFrame = ScreenCaptureManager.captureToBitmap()
            ?: return ScrollResult.Error("Capture failed")

        val statusBarHeight = getStatusBarHeight(context)
        val current = stitchedBitmap ?: run {
            newFrame.recycle()
            return ScrollResult.Error("No existing bitmap")
        }

        val result = withContext(Dispatchers.Default) {
            ImageStitcher.stitch(current, newFrame, statusBarHeight)
        }

        newFrame.recycle()

        if (result.limitReached) {
            Log.w(TAG, "Stitch limit reached at scroll $scrollCount")
            return ScrollResult.LimitReached
        }

        val stitched = result.bitmap ?: return ScrollResult.Error("Stitching failed")

        val old = stitchedBitmap
        stitchedBitmap = stitched
        if (old !== stitched) old?.recycle()

        scrollCount++
        Log.d(TAG, "Scroll $scrollCount complete, stitched height: ${stitched.height}")
        return ScrollResult.Success(scrollCount)
    }

    suspend fun saveResult(context: Context): Uri? {
        val bitmap = stitchedBitmap ?: return null
        isActive = false
        val uri = ScreenCaptureManager.saveStitchedBitmap(context, bitmap)
        if (uri != null) {
            Log.d(TAG, "Scroll capture saved: $uri")
        }
        return uri
    }

    fun cancel() {
        isActive = false
        stitchedBitmap?.recycle()
        stitchedBitmap = null
        Log.d(TAG, "Session cancelled")
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    sealed class ScrollResult {
        data class Success(val totalScrolls: Int) : ScrollResult()
        data object LimitReached : ScrollResult()
        data class Error(val message: String) : ScrollResult()
    }

    companion object {
        private const val TAG = "ScrollCaptureSession"
        private const val SETTLE_DELAY_MS = 400L
        private const val MAX_SCROLLS = 20
    }
}
