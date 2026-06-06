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
import android.widget.ImageButton
import android.widget.LinearLayout
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

    // Scroll toolbar state (separate overlay)
    private var toolbarView: View? = null
    private var toolbarBackdropView: View? = null
    private var toolbarWindowManager: WindowManager? = null

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

        val captureButton = view.findViewById<ImageButton>(R.id.overlayCaptureButton).apply {
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
                            v.performClick()
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

    // ---- Scroll toolbar (bottom bar) ----

    fun showScrollToolbar(
        context: Context,
        folderLabel: String,
        onScrollMore: () -> Unit,
        onDone: () -> Unit
    ) {
        hideScrollToolbar(context)

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        toolbarWindowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Full-screen transparent backdrop — tap anywhere triggers done
        val backdrop = View(appContext)
        backdrop.setOnClickListener { onDone() }
        val backdropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        toolbarBackdropView = backdrop
        wm.addView(backdrop, backdropParams)

        // Toolbar bar at bottom
        val inflater = LayoutInflater.from(appContext)
        val toolbar = inflater.inflate(R.layout.overlay_scroll_toolbar, null)

        val navBarHeight = getNavBarHeight(appContext)
        val bottomMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f,
            appContext.resources.displayMetrics
        ).roundToInt() + navBarHeight

        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomMargin
        }

        val label = toolbar.findViewById<TextView>(R.id.toolbarLabel)
        label.text = folderLabel

        toolbar.findViewById<ImageButton>(R.id.toolbarScrollMoreButton).setOnClickListener {
            onScrollMore()
        }
        toolbar.findViewById<ImageButton>(R.id.toolbarDoneButton).setOnClickListener {
            onDone()
        }

        toolbarView = toolbar
        wm.addView(toolbar, toolbarParams)
    }

    fun updateScrollToolbarLabel(text: String) {
        val toolbar = toolbarView ?: return
        toolbar.findViewById<TextView>(R.id.toolbarLabel)?.text = text
    }

    /**
     * Morphs the toolbar into a confirmation message: removes the tap-to-dismiss
     * backdrop, hides the action buttons, and centers the message in the bar.
     */
    fun showScrollToolbarMessage(message: String) {
        toolbarBackdropView?.let { bd ->
            try { toolbarWindowManager?.removeView(bd) } catch (_: IllegalArgumentException) {}
        }
        toolbarBackdropView = null

        val toolbar = toolbarView ?: return
        // Keep the bar's footprint (XML minWidth) and center the message in it.
        (toolbar as? LinearLayout)?.gravity = Gravity.CENTER

        toolbar.findViewById<ImageButton>(R.id.toolbarScrollMoreButton)?.visibility = View.GONE
        toolbar.findViewById<ImageButton>(R.id.toolbarDoneButton)?.visibility = View.GONE
        toolbar.findViewById<TextView>(R.id.toolbarLabel)?.apply {
            text = message
            maxWidth = Int.MAX_VALUE
            gravity = Gravity.CENTER
            textSize = 20f
            (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.marginStart = 0
                lp.marginEnd = 0
                layoutParams = lp
            }
        }
    }

    fun hideScrollToolbar(context: Context) {
        val wm = toolbarWindowManager ?: return
        toolbarView?.let {
            try { wm.removeView(it) } catch (_: IllegalArgumentException) {}
        }
        toolbarBackdropView?.let {
            try { wm.removeView(it) } catch (_: IllegalArgumentException) {}
        }
        toolbarView = null
        toolbarBackdropView = null
        toolbarWindowManager = null
    }

    // ---- Status chip on capture overlay ----

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
        val view = overlayView ?: return
        val button = view.findViewById<View>(R.id.overlayCaptureButton)
        val targetView = button ?: view

        if (targetView.width > 0 && targetView.height > 0) {
            targetView.post { jiggleButton(targetView) }
        } else {
            val observer = targetView.viewTreeObserver
            observer.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    targetView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    targetView.post { jiggleButton(targetView) }
                }
            })
        }
    }

    private fun jiggleButton(button: View) {
        val width = if (button.width > 0) button.width else 72
        val height = if (button.height > 0) button.height else 72
        button.pivotX = width / 2f
        button.pivotY = height / 2f

        val rotationValues = floatArrayOf(0f, -12f, 12f, -8f, 8f, -4f, 4f, 0f)
        val scaleValues = floatArrayOf(1f, 1.1f, 0.95f, 1.05f, 0.98f, 1.02f, 1f)

        val rotationAnimator = ObjectAnimator.ofFloat(button, "rotation", *rotationValues).apply {
            duration = 500
            repeatCount = 1
        }
        val scaleXAnimator = ObjectAnimator.ofFloat(button, "scaleX", *scaleValues).apply {
            duration = 500
            repeatCount = 1
        }
        val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", *scaleValues).apply {
            duration = 500
            repeatCount = 1
        }

        AnimatorSet().apply {
            playTogether(rotationAnimator, scaleXAnimator, scaleYAnimator)
            interpolator = AccelerateDecelerateInterpolator()
        }.start()
    }

    fun hide(context: Context) {
        hideScrollToolbar(context)
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

    private fun getNavBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
