package com.tools.screenshot3.scroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScrollCaptureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    suspend fun scrollDown(viewportHeight: Int, viewportWidth: Int): Boolean =
        suspendCancellableCoroutine { continuation ->
            val centerX = viewportWidth / 2f
            val startY = viewportHeight * 0.75f
            val endY = viewportHeight * 0.35f

            val path = Path().apply {
                moveTo(centerX, startY)
                lineTo(centerX, endY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS))
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Scroll gesture completed")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Scroll gesture cancelled")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)

            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch scroll gesture")
                if (continuation.isActive) continuation.resume(false)
            }
        }

    companion object {
        private const val TAG = "ScrollCaptureA11y"
        private const val GESTURE_DURATION_MS = 400L

        @Volatile
        var instance: ScrollCaptureAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val componentName = "${context.packageName}/${ScrollCaptureAccessibilityService::class.java.canonicalName}"
            return enabledServices.contains(componentName)
        }
    }
}
