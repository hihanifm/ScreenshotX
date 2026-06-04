package com.tools.screenshot3

import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureE2ETest {

    private lateinit var device: UiDevice
    private var baselineCount = 0

    companion object {
        private const val FLOATING_BUTTON_DESC = "Test Me!"
        private const val TIMEOUT_SHORT = 3_000L
        private const val TIMEOUT_MEDIUM = 5_000L
        private const val TIMEOUT_LONG = 10_000L
        private const val APP_PACKAGE = "com.tools.screenshot3"
        private const val PICTURES_PREFIX = "Pictures/Screenshot3"
    }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantPermissions()
        baselineCount = queryScreenshotCount()
    }

    @After
    fun tearDown() {
        val newCount = queryScreenshotCount()
        if (newCount > baselineCount) {
            deleteLatestScreenshot()
        }
    }

    @Test
    fun captureScreenshot_savesToMediaStore() {
        ActivityScenario.launch(MainActivity::class.java)

        dismissInitialDialog()
        tapStart()
        handleMediaProjectionDialog()

        val floatingButton = device.wait(
            Until.findObject(By.desc(FLOATING_BUTTON_DESC)),
            TIMEOUT_LONG
        )
        assertNotNull(
            "Floating capture button did not appear after setup",
            floatingButton
        )

        floatingButton.click()

        // Handle preview dialog if "ask before saving" is enabled
        val yesButton = device.wait(
            Until.findObject(By.text("Yes")),
            TIMEOUT_SHORT
        )
        yesButton?.click()

        // Wait for capture cycle to finish (button reappears)
        device.wait(
            Until.findObject(By.desc(FLOATING_BUTTON_DESC)),
            TIMEOUT_MEDIUM
        )

        val newCount = queryScreenshotCount()
        assertTrue(
            "Screenshot count did not increase. Before: $baselineCount, After: $newCount",
            newCount > baselineCount
        )
    }

    private fun grantPermissions() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand(
            "appops set $APP_PACKAGE SYSTEM_ALERT_WINDOW allow"
        ).close()
        if (Build.VERSION.SDK_INT >= 33) {
            uiAutomation.executeShellCommand(
                "pm grant $APP_PACKAGE android.permission.POST_NOTIFICATIONS"
            ).close()
            uiAutomation.executeShellCommand(
                "pm grant $APP_PACKAGE android.permission.READ_MEDIA_IMAGES"
            ).close()
        } else {
            uiAutomation.executeShellCommand(
                "pm grant $APP_PACKAGE android.permission.READ_EXTERNAL_STORAGE"
            ).close()
        }
    }

    private fun dismissInitialDialog() {
        val closeButton = device.wait(
            Until.findObject(By.text("Close")),
            TIMEOUT_SHORT
        )
        closeButton?.click()
    }

    private fun tapStart() {
        val startButton = device.wait(
            Until.findObject(By.text("START")),
            TIMEOUT_SHORT
        )
        assertNotNull("START button not found", startButton)
        startButton.click()
    }

    private fun handleMediaProjectionDialog() {
        // Tap "Start" on the dialog (not "Start now" on this API level)
        val startButton = device.wait(
            Until.findObject(By.text("Start")),
            TIMEOUT_MEDIUM
        )
        if (startButton != null) {
            startButton.click()
            return
        }
        // Fallback for older API text
        val startNow = device.wait(
            Until.findObject(By.textContains("Start now")),
            TIMEOUT_SHORT
        )
        startNow?.click()
    }

    private fun queryScreenshotCount(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$PICTURES_PREFIX%")
        return context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { it.count } ?: 0
    }

    private fun deleteLatestScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$PICTURES_PREFIX%")
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                try { context.contentResolver.delete(uri, null, null) } catch (_: SecurityException) {}
            }
        }
    }
}
