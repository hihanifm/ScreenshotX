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
    fun chooseOverlap_picksClearlyBetterSmallOverlapOverLargerCandidates() {
        // The fling case: the true seam sits at a small overlap (far below the old ~48%
        // window floor) and matches cleanly, while larger overlaps score poorly. The small,
        // clearly-better candidate must win so fresh content is not skipped.
        val decision = ImageStitcher.chooseOverlap(
            candidates = listOf(
                ImageStitcher.OverlapCandidate(overlapRows = 30, averageDifference = 8.0),
                ImageStitcher.OverlapCandidate(overlapRows = 48, averageDifference = 95.0),
                ImageStitcher.OverlapCandidate(overlapRows = 60, averageDifference = 120.0)
            ),
            expectedOverlapRows = 60
        )

        assertEquals(30, decision.chosenOverlapRows)
        assertFalse(decision.usedConservativeFallback)
        assertTrue(decision.confidenceScore > 0.10f)
    }

    @Test
    fun chooseOverlap_prefersSmallerOverlapWhenScoresAreNearlyTied() {
        // Near-equal scores => low confidence => bias toward the smaller overlap, which
        // duplicates a thin sliver rather than dropping content.
        val decision = ImageStitcher.chooseOverlap(
            candidates = listOf(
                ImageStitcher.OverlapCandidate(overlapRows = 70, averageDifference = 40.0),
                ImageStitcher.OverlapCandidate(overlapRows = 32, averageDifference = 41.0)
            ),
            expectedOverlapRows = 60
        )

        assertEquals(32, decision.chosenOverlapRows)
        assertTrue(decision.usedConservativeFallback)
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
