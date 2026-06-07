package com.tools.screenshot3.scroll

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.tools.screenshot3.capture.ScreenCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

class ScrollCaptureSession(context: Context, initialBitmap: Bitmap) {

    private val statusBarHeight = getSystemBarHeight(context, "status_bar_height")
    private val navigationBarHeight = if (ScreenCaptureManager.shouldStripScrollNavBar()) {
        getSystemBarHeight(context, "navigation_bar_height")
    } else {
        0
    }
    private val expectedRetainedOverlapRatio =
        ScrollCaptureAccessibilityService.expectedRetainedOverlapRatio()
    private var stitchedBitmap: Bitmap? = ImageStitcher.cropBitmap(
        bitmap = initialBitmap,
        bottomCrop = navigationBarHeight
    ).also { cropped ->
        if (cropped !== initialBitmap) {
            initialBitmap.recycle()
        }
    }
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

        val current = stitchedBitmap ?: run {
            newFrame.recycle()
            return ScrollResult.Error("No existing bitmap")
        }

        val result = withContext(Dispatchers.Default) {
            ImageStitcher.stitch(
                top = current,
                bottom = newFrame,
                statusBarHeight = statusBarHeight,
                navigationBarHeight = navigationBarHeight,
                expectedRetainedOverlapRatio = expectedRetainedOverlapRatio
            )
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
        // Only a true scroll capture if the user actually stitched at least one extra frame;
        // tapping Done on the first frame is a normal single-image capture.
        val uri = ScreenCaptureManager.saveStitchedBitmap(context, bitmap, isScroll = scrollCount > 0)
        if (uri != null) {
            Log.d(TAG, "Scroll capture saved: $uri")
        }
        return uri
    }

    /**
     * Returns a scaled copy of the current stitched draft for the live preview card.
     * Scaled to [targetWidthPx] preserving aspect ratio; if the result would exceed
     * [maxHeightPx] only the top portion is kept (a long page shows its head, not a
     * sliver). Always a fresh copy — never a reference to the recycled stitched bitmap,
     * so the caller owns and must recycle it.
     */
    fun buildThumbnail(targetWidthPx: Int, maxHeightPx: Int): Bitmap? {
        val src = stitchedBitmap ?: return null
        if (src.width <= 0 || src.height <= 0 || targetWidthPx <= 0 || maxHeightPx <= 0) return null
        val scale = targetWidthPx.toFloat() / src.width
        val fullHeight = (src.height * scale).roundToInt().coerceAtLeast(1)
        val finalHeight = min(fullHeight, maxHeightPx)
        return try {
            val out = Bitmap.createBitmap(targetWidthPx, finalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(src, Matrix().apply { setScale(scale, scale) }, null)
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Thumbnail build failed", t)
            null
        }
    }

    fun cancel() {
        isActive = false
        stitchedBitmap?.recycle()
        stitchedBitmap = null
        Log.d(TAG, "Session cancelled")
    }

    private fun getSystemBarHeight(context: Context, name: String): Int {
        val resourceId = context.resources.getIdentifier(name, "dimen", "android")
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
