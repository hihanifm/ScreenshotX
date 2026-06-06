package com.tools.screenshot3.scroll

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageStitcher {

    private const val TAG = "ImageStitcher"
    private const val MAX_STITCHED_HEIGHT = 20000
    private const val DEFAULT_EXPECTED_RETAINED_OVERLAP_RATIO = 0.60f
    private const val SEARCH_MIN_RATIO = 0.12f
    private const val SEARCH_MAX_RATIO = 0.85f
    private const val ROW_SAMPLE_COUNT = 24
    private const val ROW_EDGE_TRIM_RATIO = 0.12f
    private const val MATCH_BOTTOM_EXCLUSION_RATIO = 0.28f
    private const val SCORE_RELATIVE_TOLERANCE = 0.12
    private const val SCORE_ABSOLUTE_TOLERANCE = 12.0
    private const val CONFIDENCE_THRESHOLD = 0.10f
    private const val LARGE_OVERLAP_PENALTY_PER_ROW = 0.015
    private const val OVERLAP_SAFETY_MARGIN_RATIO = 0.035f
    private const val MAX_OVERLAP_SAFETY_MARGIN_ROWS = 72

    data class VerticalBounds(
        val top: Int,
        val bottomExclusive: Int
    ) {
        val height: Int
            get() = bottomExclusive - top
    }

    data class StitchResult(
        val bitmap: Bitmap?,
        val limitReached: Boolean = false,
        val chosenOverlapRows: Int = 0,
        val confidenceScore: Float = 1f,
        val usedConservativeFallback: Boolean = false
    )

    internal data class OverlapCandidate(
        val overlapRows: Int,
        val averageDifference: Double
    )

    internal data class OverlapDecision(
        val chosenOverlapRows: Int,
        val confidenceScore: Float,
        val usedConservativeFallback: Boolean
    )

    fun stitch(
        top: Bitmap,
        bottom: Bitmap,
        statusBarHeight: Int = 0,
        navigationBarHeight: Int = 0,
        expectedRetainedOverlapRatio: Float = DEFAULT_EXPECTED_RETAINED_OVERLAP_RATIO
    ): StitchResult {
        val safeExpectedOverlapRatio = expectedRetainedOverlapRatio.coerceIn(0.05f, 0.90f)
        // Fling makes the page travel further than the finger, so the true overlap is usually
        // smaller than the gesture-derived estimate. Search a wide band rather than a narrow
        // window around the estimate, so the real seam is always in range; the estimate only
        // biases tie-breaks via the large-overlap penalty in scoreOverlapCandidates.
        val searchMin = (top.height * SEARCH_MIN_RATIO).roundToInt().coerceAtLeast(1)
        val searchMax = (top.height * SEARCH_MAX_RATIO).roundToInt().coerceAtLeast(searchMin + 1)
        val bottomBounds = computeContentBounds(
            height = bottom.height,
            topCrop = statusBarHeight,
            bottomCrop = navigationBarHeight
        )

        val decision = findOverlap(
            top = top,
            bottom = bottom,
            searchMin = searchMin,
            searchMax = searchMax,
            bottomBounds = bottomBounds,
            expectedRetainedOverlapRatio = safeExpectedOverlapRatio
        )
        val safeOverlapRows = applyLossSafetyMargin(
            overlapRows = decision.chosenOverlapRows,
            usableBottomHeight = bottomBounds.height
        )
        Log.d(
            TAG,
            "Overlap detected: ${decision.chosenOverlapRows} rows, using $safeOverlapRows rows " +
                "(confidence=${decision.confidenceScore}, conservative=${decision.usedConservativeFallback})"
        )

        val bottomUsableHeight = bottomBounds.height
        val newHeight = top.height + bottomUsableHeight - safeOverlapRows
        if (newHeight > MAX_STITCHED_HEIGHT) {
            Log.w(TAG, "Stitched height $newHeight exceeds max $MAX_STITCHED_HEIGHT")
            return StitchResult(bitmap = null, limitReached = true)
        }

        val result = Bitmap.createBitmap(top.width, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(top, 0f, 0f, null)
        val srcRect = android.graphics.Rect(
            0,
            bottomBounds.top + safeOverlapRows,
            bottom.width,
            bottomBounds.bottomExclusive
        )
        val dstRect = android.graphics.Rect(0, top.height, top.width, newHeight)
        canvas.drawBitmap(bottom, srcRect, dstRect, null)

        return StitchResult(
            bitmap = result,
            chosenOverlapRows = safeOverlapRows,
            confidenceScore = decision.confidenceScore,
            usedConservativeFallback = decision.usedConservativeFallback
        )
    }

    fun cropBitmap(bitmap: Bitmap, topCrop: Int = 0, bottomCrop: Int = 0): Bitmap {
        val bounds = computeContentBounds(bitmap.height, topCrop, bottomCrop)
        if (bounds.top == 0 && bounds.bottomExclusive == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, 0, bounds.top, bitmap.width, bounds.height)
    }

    internal fun computeContentBounds(
        height: Int,
        topCrop: Int = 0,
        bottomCrop: Int = 0
    ): VerticalBounds {
        val safeTop = topCrop.coerceAtLeast(0).coerceAtMost(max(height - 1, 0))
        val maxBottom = max(height - safeTop - 1, 0)
        val safeBottom = bottomCrop.coerceAtLeast(0).coerceAtMost(maxBottom)
        return VerticalBounds(
            top = safeTop,
            bottomExclusive = max(height - safeBottom, safeTop + 1)
        )
    }

    private fun findOverlap(
        top: Bitmap,
        bottom: Bitmap,
        searchMin: Int,
        searchMax: Int,
        bottomBounds: VerticalBounds,
        expectedRetainedOverlapRatio: Float
    ): OverlapDecision {
        val expectedOverlapRows = (top.height * expectedRetainedOverlapRatio).roundToInt()
        val candidates = scoreOverlapCandidates(
            top, bottom, searchMin, searchMax, bottomBounds, expectedOverlapRows
        )
        return chooseOverlap(candidates, expectedOverlapRows)
    }

    internal fun chooseOverlap(
        candidates: List<OverlapCandidate>,
        expectedOverlapRows: Int
    ): OverlapDecision {
        if (candidates.isEmpty()) {
            return OverlapDecision(
                chosenOverlapRows = expectedOverlapRows,
                confidenceScore = 0f,
                usedConservativeFallback = true
            )
        }

        val sorted = candidates.sortedWith(
            compareBy<OverlapCandidate> { it.averageDifference }.thenBy { it.overlapRows }
        )
        val best = sorted.first()
        val second = sorted.getOrNull(1)
        val confidence = if (second == null) {
            1f
        } else {
            ((second.averageDifference - best.averageDifference) /
                max(best.averageDifference, 1.0)).toFloat().coerceIn(0f, 1f)
        }

        val tolerance = max(
            SCORE_ABSOLUTE_TOLERANCE,
            best.averageDifference * SCORE_RELATIVE_TOLERANCE
        )
        val plausible = sorted.filter {
            it.averageDifference <= best.averageDifference + tolerance
        }
        val conservativeCandidate = plausible.minByOrNull { it.overlapRows } ?: best
        val useConservativeFallback = confidence < CONFIDENCE_THRESHOLD &&
            conservativeCandidate.overlapRows < best.overlapRows

        val chosen = if (useConservativeFallback) conservativeCandidate else best
        return OverlapDecision(
            chosenOverlapRows = chosen.overlapRows,
            confidenceScore = confidence,
            usedConservativeFallback = useConservativeFallback
        )
    }

    private fun scoreOverlapCandidates(
        top: Bitmap,
        bottom: Bitmap,
        searchMin: Int,
        searchMax: Int,
        bottomBounds: VerticalBounds,
        expectedOverlapRows: Int
    ): List<OverlapCandidate> {
        val width = min(top.width, bottom.width)
        val sampleStep = max(2, width / 48)
        val candidates = mutableListOf<OverlapCandidate>()

        for (overlap in searchMin..min(searchMax, min(top.height, bottomBounds.height))) {
            val innerTrim = (overlap * ROW_EDGE_TRIM_RATIO).roundToInt()
            val lowerTrim = max(innerTrim, (overlap * MATCH_BOTTOM_EXCLUSION_RATIO).roundToInt())
            val sampleStart = innerTrim.coerceAtMost(max(overlap - 1, 0))
            val sampleEnd = max(sampleStart + 1, overlap - lowerTrim)
            var totalSad = 0.0
            var sampleCount = 0

            for (rowIdx in 0 until ROW_SAMPLE_COUNT) {
                val relativeRow = if (ROW_SAMPLE_COUNT == 1) {
                    0
                } else {
                    sampleStart + ((sampleEnd - sampleStart - 1) * rowIdx) / (ROW_SAMPLE_COUNT - 1)
                }
                val bottomRow = bottomBounds.top + relativeRow
                val topRow = top.height - overlap + relativeRow
                if (
                    topRow < 0 ||
                    topRow >= top.height ||
                    bottomRow < bottomBounds.top ||
                    bottomRow >= bottomBounds.bottomExclusive
                ) {
                    continue
                }

                for (x in 0 until width step sampleStep) {
                    val topPixel = top.getPixel(x, topRow)
                    val bottomPixel = bottom.getPixel(x, bottomRow)
                    totalSad += pixelDiff(topPixel, bottomPixel).toDouble()
                    sampleCount++
                }
            }

            if (sampleCount == 0) continue
            val averageDifference = totalSad / sampleCount
            // Fling only ever shrinks overlap below the geometry estimate; an overlap larger
            // than expected is physically implausible (only legitimate at end-of-page), so
            // nudge against it to break ties toward the plausible, smaller-overlap region.
            val overlapPenalty = max(overlap - expectedOverlapRows, 0) * LARGE_OVERLAP_PENALTY_PER_ROW
            candidates.add(
                OverlapCandidate(
                    overlapRows = overlap,
                    averageDifference = averageDifference + overlapPenalty
                )
            )
        }
        return candidates
    }

    private fun applyLossSafetyMargin(overlapRows: Int, usableBottomHeight: Int): Int {
        val safetyMargin = min(
            (usableBottomHeight * OVERLAP_SAFETY_MARGIN_RATIO).roundToInt(),
            MAX_OVERLAP_SAFETY_MARGIN_ROWS
        )
        return max(overlapRows - safetyMargin, 0)
    }

    private fun pixelDiff(a: Int, b: Int): Long {
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return (dr + dg + db).toLong()
    }
}
