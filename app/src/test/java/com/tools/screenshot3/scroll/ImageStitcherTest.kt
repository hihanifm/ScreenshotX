package com.tools.screenshot3.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageStitcherTest {

    @Test
    fun computeContentBounds_usesFullHeightWhenCropsAreZero() {
        assertEquals(
            ImageStitcher.VerticalBounds(top = 0, bottomExclusive = 100),
            ImageStitcher.computeContentBounds(height = 100)
        )
    }

    @Test
    fun computeContentBounds_appliesTopAndBottomCrops() {
        assertEquals(
            ImageStitcher.VerticalBounds(top = 10, bottomExclusive = 88),
            ImageStitcher.computeContentBounds(height = 100, topCrop = 10, bottomCrop = 12)
        )
    }

    @Test
    fun computeContentBounds_clampsOversizedBottomCrop() {
        assertEquals(
            ImageStitcher.VerticalBounds(top = 10, bottomExclusive = 11),
            ImageStitcher.computeContentBounds(height = 20, topCrop = 10, bottomCrop = 50)
        )
    }

    @Test
    fun computeContentBounds_ignoresNegativeCrops() {
        assertEquals(
            ImageStitcher.VerticalBounds(top = 0, bottomExclusive = 40),
            ImageStitcher.computeContentBounds(height = 40, topCrop = -5, bottomCrop = -8)
        )
    }

    @Test
    fun chooseOverlap_prefersSmallerPlausibleCandidateWhenConfidenceIsLow() {
        val decision = ImageStitcher.chooseOverlap(
            candidates = listOf(
                ImageStitcher.OverlapCandidate(overlapRows = 52, averageDifference = 100.0),
                ImageStitcher.OverlapCandidate(overlapRows = 46, averageDifference = 104.0),
                ImageStitcher.OverlapCandidate(overlapRows = 60, averageDifference = 130.0)
            ),
            expectedOverlapRows = 60
        )

        assertEquals(46, decision.chosenOverlapRows)
        assertTrue(decision.usedConservativeFallback)
        assertTrue(decision.confidenceScore < 0.10f)
    }

    @Test
    fun chooseOverlap_keepsBestCandidateWhenConfidenceIsGood() {
        val decision = ImageStitcher.chooseOverlap(
            candidates = listOf(
                ImageStitcher.OverlapCandidate(overlapRows = 54, averageDifference = 60.0),
                ImageStitcher.OverlapCandidate(overlapRows = 48, averageDifference = 90.0),
                ImageStitcher.OverlapCandidate(overlapRows = 58, averageDifference = 110.0)
            ),
            expectedOverlapRows = 60
        )

        assertEquals(54, decision.chosenOverlapRows)
        assertFalse(decision.usedConservativeFallback)
        assertTrue(decision.confidenceScore > 0.10f)
    }
}
