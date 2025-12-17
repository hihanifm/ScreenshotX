package com.tools.screenshot3.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import com.tools.screenshot3.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object FloatingCaptureOverlay {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun show(
        context: Context,
        onCapture: () -> Unit
    ) {
        if (overlayView != null) return

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val inflater = LayoutInflater.from(appContext)
        val view = inflater.inflate(R.layout.overlay_capture_button, null)

        val metrics = appContext.resources.displayMetrics
        val defaultMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            metrics
        ).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - defaultMargin * 4
            y = metrics.heightPixels / 3
        }

        layoutParams = params
        overlayView = view
        val captureButton = view.findViewById<View>(R.id.overlayCaptureButton).apply {
            setOnClickListener { onCapture() }
        }

        val touchListener = object : View.OnTouchListener {
            private var downTime = 0L
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var hasMoved = false
            private val touchSlop = appContext.resources.displayMetrics.density * 4

            private fun clampPosition(lp: WindowManager.LayoutParams, dragView: View) {
                val targetView = overlayView ?: dragView
                val overlayWidth = targetView.width.takeIf { it > 0 }
                    ?: dragView.width.takeIf { it > 0 }
                    ?: captureButton.width
                val overlayHeight = targetView.height.takeIf { it > 0 }
                    ?: dragView.height.takeIf { it > 0 }
                    ?: captureButton.height

                val maxXPos = metrics.widthPixels - overlayWidth
                val maxYPos = metrics.heightPixels - overlayHeight - defaultMargin

                lp.x = min(max(lp.x, -defaultMargin), maxXPos)
                lp.y = min(max(lp.y, defaultMargin / 2), maxYPos)
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = layoutParams ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        downTime = event.downTime
                        hasMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (!hasMoved && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                            hasMoved = true
                        }
                        if (hasMoved) {
                            lp.x = (initialX + deltaX).toInt()
                            lp.y = (initialY + deltaY).toInt()
                            clampPosition(lp, v)
                            windowManager?.updateViewLayout(view, lp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val elapsed = event.eventTime - downTime
                        if (!hasMoved && elapsed < CLICK_MAX_DURATION_MS) {
                            captureButton.performClick()
                        } else if (hasMoved) {
                            clampPosition(lp, v)
                            windowManager?.updateViewLayout(view, lp)
                            hasMoved = false
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        hasMoved = false
                        return false
                    }
                }
                return true
            }
        }

        view.setOnTouchListener(touchListener)
        captureButton.setOnTouchListener(touchListener)

        wm.addView(view, params)
    }
    
    fun jiggleButton() {
        android.util.Log.d("SSM-FloatingOverlay", "jiggleButton() public function called, overlayView: $overlayView")
        val view = overlayView ?: run {
            android.util.Log.w("SSM-FloatingOverlay", "overlayView is null")
            return
        }
        
        // Try to animate the button, but if not found, animate the parent view
        val button = view.findViewById<View>(R.id.overlayCaptureButton)
        val targetView = button ?: view
        
        android.util.Log.d("SSM-FloatingOverlay", "Target view for animation: $targetView (button: $button, view: $view)")
        
        // Wait for the view to be laid out before animating
        if (targetView.width > 0 && targetView.height > 0) {
            // View is already laid out, animate immediately
            targetView.post {
                jiggleButton(targetView)
            }
        } else {
            // Wait for layout to complete
            val observer = targetView.viewTreeObserver
            observer.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    targetView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    android.util.Log.d("SSM-FloatingOverlay", "View laid out, starting animation")
                    targetView.post {
                        jiggleButton(targetView)
                    }
                }
            })
        }
    }
    
    private fun jiggleButton(button: View) {
        android.util.Log.d("SSM-FloatingOverlay", "jiggleButton called, button: $button, width: ${button.width}, height: ${button.height}")
        
        // Ensure pivot point is at center for rotation
        val width = if (button.width > 0) button.width else 72 // fallback to 72dp
        val height = if (button.height > 0) button.height else 72
        button.pivotX = width / 2f
        button.pivotY = height / 2f
        
        android.util.Log.d("SSM-FloatingOverlay", "Pivot set: x=${button.pivotX}, y=${button.pivotY}")
        
        // Create a more aggressive and visible jiggle animation
        // Use larger rotation angles and combine with scale for maximum visibility
        val rotationValues = floatArrayOf(0f, -12f, 12f, -8f, 8f, -4f, 4f, 0f)
        val scaleValues = floatArrayOf(1f, 1.1f, 0.95f, 1.05f, 0.98f, 1.02f, 1f)
        
        val rotationAnimator = ObjectAnimator.ofFloat(button, "rotation", *rotationValues).apply {
            duration = 500
            repeatCount = 1 // Play twice (initial + 1 repeat)
        }
        
        val scaleXAnimator = ObjectAnimator.ofFloat(button, "scaleX", *scaleValues).apply {
            duration = 500
            repeatCount = 1 // Play twice (initial + 1 repeat)
        }
        
        val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", *scaleValues).apply {
            duration = 500
            repeatCount = 1 // Play twice (initial + 1 repeat)
        }
        
        val animatorSet = AnimatorSet().apply {
            playTogether(rotationAnimator, scaleXAnimator, scaleYAnimator)
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        android.util.Log.d("SSM-FloatingOverlay", "Starting animation with ${rotationValues.size} rotation steps (will play twice)")
        animatorSet.start()
    }

    fun hide(context: Context) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        try {
            wm.removeView(view)
        } catch (_: IllegalArgumentException) {
        }
        overlayView = null
        layoutParams = null
        windowManager = null
    }

    private const val CLICK_MAX_DURATION_MS = 200L
}
