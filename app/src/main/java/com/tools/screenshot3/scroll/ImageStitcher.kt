package com.tools.screenshot3.scroll

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageStitcher {

    private const val TAG = "ImageStitcher"
    private const val MAX_STITCHED_HEIGHT = 20000

    data class StitchResult(
        val bitmap: Bitmap?,
        val limitReached: Boolean = false
    )

    fun stitch(top: Bitmap, bottom: Bitmap, statusBarHeight: Int = 0): StitchResult {
        val searchMin = (top.height * 0.45f).toInt()
        val searchMax = (top.height * 0.75f).toInt()

        val overlapRows = findOverlap(top, bottom, searchMin, searchMax, statusBarHeight)
        Log.d(TAG, "Overlap detected: $overlapRows rows")

        val cropTop = if (statusBarHeight > 0) statusBarHeight else 0
        val bottomUsableHeight = bottom.height - cropTop
        val newHeight = top.height + bottomUsableHeight - overlapRows
        if (newHeight > MAX_STITCHED_HEIGHT) {
            Log.w(TAG, "Stitched height $newHeight exceeds max $MAX_STITCHED_HEIGHT")
            return StitchResult(bitmap = null, limitReached = true)
        }

        val result = Bitmap.createBitmap(top.width, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(top, 0f, 0f, null)
        val srcRect = android.graphics.Rect(0, cropTop + overlapRows, bottom.width, bottom.height)
        val dstRect = android.graphics.Rect(0, top.height, top.width, newHeight)
        canvas.drawBitmap(bottom, srcRect, dstRect, null)

        return StitchResult(bitmap = result)
    }

    private fun findOverlap(
        top: Bitmap,
        bottom: Bitmap,
        searchMin: Int,
        searchMax: Int,
        statusBarHeight: Int
    ): Int {
        val width = min(top.width, bottom.width)
        val sampleStep = max(4, width / 80)
        val sampleRows = 3
        val bottomStart = max(statusBarHeight, 0)

        var bestOffset = (top.height * 0.60f).toInt()
        var bestSad = Long.MAX_VALUE

        for (overlap in searchMin..min(searchMax, min(top.height, bottom.height - bottomStart))) {
            var totalSad = 0L
            var sampleCount = 0

            for (rowIdx in 0 until sampleRows) {
                val bottomRow = bottomStart + rowIdx * (overlap / max(sampleRows, 1))
                val topRow = top.height - overlap + bottomRow - bottomStart
                if (topRow < 0 || topRow >= top.height || bottomRow >= bottom.height) continue

                for (x in 0 until width step sampleStep) {
                    val topPixel = top.getPixel(x, topRow)
                    val bottomPixel = bottom.getPixel(x, bottomRow)
                    totalSad += pixelDiff(topPixel, bottomPixel)
                    sampleCount++
                }
            }

            if (sampleCount == 0) continue
            val avgSad = totalSad / sampleCount

            if (avgSad < bestSad) {
                bestSad = avgSad
                bestOffset = overlap
            }
        }

        val threshold = 30L
        if (bestSad > threshold) {
            Log.w(TAG, "Best SAD $bestSad exceeds threshold, using fallback overlap")
            return (top.height * 0.60f).toInt()
        }

        return bestOffset
    }

    private fun pixelDiff(a: Int, b: Int): Long {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return (dr + dg + db).toLong()
    }
}
