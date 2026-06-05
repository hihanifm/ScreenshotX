package com.tools.screenshot3.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import com.tools.screenshot3.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object FloatingCaptureOverlay {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var hideStatusRunnable: Runnable? = null

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
        val prefs = overlayPrefs(appContext)
        val savedX = prefs.getInt(KEY_OVERLAY_X, NO_SAVED_POSITION)
        val savedY = prefs.getInt(KEY_OVERLAY_Y, NO_SAVED_POSITION)

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
            x = if (savedX != NO_SAVED_POSITION) savedX else metrics.widthPixels - defaultMargin * 4
            y = if (savedY != NO_SAVED_POSITION) savedY else metrics.heightPixels / 3
        }

        clampPosition(
            lp = params,
            metrics = metrics,
            defaultMargin = defaultMargin
        )

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
                            clampPosition(lp, metrics, defaultMargin, v, captureButton)
                            windowManager?.updateViewLayout(view, lp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val elapsed = event.eventTime - downTime
                        if (!hasMoved && elapsed < CLICK_MAX_DURATION_MS) {
                            captureButton.performClick()
                        } else if (hasMoved) {
                            clampPosition(lp, metrics, defaultMargin, v, captureButton)
                            windowManager?.updateViewLayout(view, lp)
                            savePosition(prefs, lp.x, lp.y)
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

    fun showStatus(message: String) {
        val view = overlayView ?: return
        val chip = view.findViewById<TextView>(R.id.overlayStatusChip)
        val pendingHide = hideStatusRunnable
        if (pendingHide != null) {
            chip.removeCallbacks(pendingHide)
            hideStatusRunnable = null
        }

        chip.text = message
        chip.alpha = 1f
        chip.visibility = View.VISIBLE

        val hideRunnable = Runnable {
            chip.animate()
                .alpha(0f)
                .setDuration(STATUS_FADE_DURATION_MS)
                .withEndAction {
                    chip.visibility = View.GONE
                    chip.alpha = 1f
                }
                .start()
        }
        hideStatusRunnable = hideRunnable
        chip.postDelayed(hideRunnable, STATUS_VISIBLE_DURATION_MS)
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
        view.findViewById<TextView>(R.id.overlayStatusChip).let { chip ->
            hideStatusRunnable?.let { chip.removeCallbacks(it) }
            hideStatusRunnable = null
        }
        try {
            wm.removeView(view)
        } catch (_: IllegalArgumentException) {
        }
        overlayView = null
        layoutParams = null
        windowManager = null
    }

    private const val CLICK_MAX_DURATION_MS = 200L
    private const val PREF_NAME = "floating_capture_overlay"
    private const val KEY_OVERLAY_X = "overlay_x"
    private const val KEY_OVERLAY_Y = "overlay_y"
    private const val NO_SAVED_POSITION = Int.MIN_VALUE
    private const val STATUS_VISIBLE_DURATION_MS = 1000L
    private const val STATUS_FADE_DURATION_MS = 150L

    private fun clampPosition(
        lp: WindowManager.LayoutParams,
        metrics: android.util.DisplayMetrics,
        defaultMargin: Int,
        dragView: View? = null,
        captureButton: View? = null
    ) {
        val targetView = overlayView ?: dragView
        val overlayWidth = targetView?.width?.takeIf { it > 0 }
            ?: dragView?.width?.takeIf { it > 0 }
            ?: captureButton?.width?.takeIf { it > 0 }
            ?: 0
        val overlayHeight = targetView?.height?.takeIf { it > 0 }
            ?: dragView?.height?.takeIf { it > 0 }
            ?: captureButton?.height?.takeIf { it > 0 }
            ?: 0

        val maxXPos = max(-defaultMargin, metrics.widthPixels - overlayWidth)
        val maxYPos = max(defaultMargin / 2, metrics.heightPixels - overlayHeight - defaultMargin)

        lp.x = min(max(lp.x, -defaultMargin), maxXPos)
        lp.y = min(max(lp.y, defaultMargin / 2), maxYPos)
    }

    private fun overlayPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun savePosition(prefs: SharedPreferences, x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_OVERLAY_X, x)
            .putInt(KEY_OVERLAY_Y, y)
            .apply()
    }
}
