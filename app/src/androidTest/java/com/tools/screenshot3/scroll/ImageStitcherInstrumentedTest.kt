package com.tools.screenshot3.scroll

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageStitcherInstrumentedTest {

    @Test
    fun stitch_removesStatusBarAndNavBarFromAppendedFrame() {
        val top = bitmapOfRows(
            0xFF110000.toInt(),
            0xFF220000.toInt(),
            0xFF330000.toInt(),
            0xFF440000.toInt()
        )
        val bottom = bitmapOfRows(
            0xFF999999.toInt(),
            0xFF330000.toInt(),
            0xFF440000.toInt(),
            0xFF550000.toInt(),
            0xFF666666.toInt()
        )

        val result = ImageStitcher.stitch(
            top = top,
            bottom = bottom,
            statusBarHeight = 1,
            navigationBarHeight = 1
        )

        val stitched = result.bitmap
        assertNotNull(stitched)
        stitched ?: return

        assertEquals(5, stitched.height)
        assertEquals(0xFF110000.toInt(), stitched.getPixel(0, 0))
        assertEquals(0xFF220000.toInt(), stitched.getPixel(0, 1))
        assertEquals(0xFF330000.toInt(), stitched.getPixel(0, 2))
        assertEquals(0xFF440000.toInt(), stitched.getPixel(0, 3))
        assertEquals(0xFF550000.toInt(), stitched.getPixel(0, 4))
    }

    @Test
    fun cropBitmap_removesOnlyBottomNavBarFromInitialFrame() {
        val source = bitmapOfRows(
            0xFF101010.toInt(),
            0xFF202020.toInt(),
            0xFF303030.toInt(),
            0xFF404040.toInt()
        )

        val cropped = ImageStitcher.cropBitmap(source, bottomCrop = 1)

        assertEquals(3, cropped.height)
        assertEquals(0xFF101010.toInt(), cropped.getPixel(0, 0))
        assertEquals(0xFF202020.toInt(), cropped.getPixel(0, 1))
        assertEquals(0xFF303030.toInt(), cropped.getPixel(0, 2))
    }

    @Test
    fun stitch_withZeroNavBarHeight_preservesCurrentBottomContent() {
        val top = bitmapOfRows(
            0xFF010101.toInt(),
            0xFF020202.toInt(),
            0xFF030303.toInt()
        )
        val bottom = bitmapOfRows(
            0xFF020202.toInt(),
            0xFF030303.toInt(),
            0xFF040404.toInt()
        )

        val result = ImageStitcher.stitch(
            top = top,
            bottom = bottom,
            statusBarHeight = 0,
            navigationBarHeight = 0
        )

        val stitched = result.bitmap
        assertNotNull(stitched)
        stitched ?: return

        assertEquals(4, stitched.height)
        assertEquals(0xFF040404.toInt(), stitched.getPixel(0, 3))
    }

    @Test
    fun stitch_prefersKeepingContentWhenOverlapIsAmbiguous() {
        val top = bitmapOfRows(
            0xFF101010.toInt(),
            0xFF202020.toInt(),
            0xFF303030.toInt(),
            0xFF202020.toInt(),
            0xFF303030.toInt(),
            0xFF404040.toInt()
        )
        val bottom = bitmapOfRows(
            0xFF202020.toInt(),
            0xFF303030.toInt(),
            0xFF404040.toInt(),
            0xFF505050.toInt(),
            0xFF606060.toInt()
        )

        val result = ImageStitcher.stitch(
            top = top,
            bottom = bottom,
            statusBarHeight = 0,
            navigationBarHeight = 0
        )

        val stitched = result.bitmap
        assertNotNull(stitched)
        stitched ?: return

        assertTrue(result.usedConservativeFallback)
        assertEquals(4, result.chosenOverlapRows)
        assertEquals(7, stitched.height)
        assertEquals(0xFF404040.toInt(), stitched.getPixel(0, 5))
        assertEquals(0xFF505050.toInt(), stitched.getPixel(0, 6))
    }

    @Test
    fun stitch_overlapChoiceIsIndependentOfNavBarToggle() {
        val top = bitmapOfRows(
            0xFF111111.toInt(),
            0xFF222222.toInt(),
            0xFF333333.toInt(),
            0xFF444444.toInt()
        )
        val bottom = bitmapOfRows(
            0xFF333333.toInt(),
            0xFF444444.toInt(),
            0xFF555555.toInt(),
            0xFF666666.toInt(),
            0xFF777777.toInt()
        )

        val withoutNavCrop = ImageStitcher.stitch(
            top = top,
            bottom = bottom,
            statusBarHeight = 0,
            navigationBarHeight = 0
        )
        val withNavCrop = ImageStitcher.stitch(
            top = top,
            bottom = bottom,
            statusBarHeight = 0,
            navigationBarHeight = 1
        )

        assertEquals(withoutNavCrop.chosenOverlapRows, withNavCrop.chosenOverlapRows)
        assertFalse(withoutNavCrop.usedConservativeFallback)
    }

    private fun bitmapOfRows(vararg colors: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(1, colors.size, Bitmap.Config.ARGB_8888)
        colors.forEachIndexed { index, color ->
            bitmap.setPixel(0, index, color)
        }
        return bitmap
    }
}
