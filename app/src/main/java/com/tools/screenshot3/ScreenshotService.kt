package com.tools.screenshot3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tools.screenshot3.capture.ScreenCaptureManager
import com.tools.screenshot3.overlay.FloatingCaptureOverlay
import com.tools.screenshot3.preview.CapturePreviewActivity
import com.tools.screenshot3.scroll.ScrollCaptureAccessibilityService
import com.tools.screenshot3.scroll.ScrollCaptureSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class ScreenshotService : android.app.Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sessionObserverJob: Job? = null
    private var scrollCaptureSession: ScrollCaptureSession? = null
    private var scrollDoneInProgress = false
    private var currentThumbnail: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeSessionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                ensureForegroundNotification(getString(R.string.notification_preparing))
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    startProjection(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_PREVIEW_DECISION -> {
                val accepted = intent.getBooleanExtra(EXTRA_PREVIEW_ACCEPTED, false)
                handlePreviewDecision(accepted)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionObserverJob?.cancel()
        scrollCaptureSession?.cancel()
        scrollCaptureSession = null
        serviceScope.cancel()
        FloatingCaptureOverlay.hideScrollToolbar(this)
        FloatingCaptureOverlay.hide(this)
        recycleThumbnail()
        ScreenCaptureManager.release()
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)
            ?: run {
                stopSelf()
                return
            }
        ScreenCaptureManager.initialize(this, projection)
        ensureForegroundNotification(getString(R.string.notification_ready))
        showFloatingControls()
    }

    private fun ensureForegroundNotification(content: String) {
        val notification = buildNotification(content)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.tools.screenshot3.action.START"
        const val ACTION_PREVIEW_DECISION = "com.tools.screenshot3.action.PREVIEW_DECISION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_PREVIEW_ACCEPTED = "extra_preview_accepted"

        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "screenshot_capture"
        private const val OVERLAY_HIDE_DELAY_MS = 300L
        private const val CAPTURE_STABILIZE_DELAY_MS = 120L
        private const val OVERLAY_RESUME_DELAY_MS = 200L
        private const val PREVIEW_WIDTH_DP = 72f
        private const val PREVIEW_MAX_HEIGHT_DP = 180f

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenshotService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotService::class.java))
        }
    }

    private fun showFloatingControls() {
        FloatingCaptureOverlay.show(
            context = this,
            onCapture = {
                serviceScope.launch {
                    FloatingCaptureOverlay.hide(this@ScreenshotService)
                    delay(OVERLAY_HIDE_DELAY_MS)
                    handleCapture()
                }
            },
            onOpenApp = { openMainActivity() }
        )
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        startActivity(intent)
    }

    private suspend fun handleCapture() {
        if (ScreenCaptureManager.isScrollCaptureEnabled()) {
            if (ScrollCaptureAccessibilityService.isEnabled(applicationContext)) {
                handleScrollCaptureStart()
            } else {
                promptEnableAccessibility()
            }
            return
        }

        if (ScreenCaptureManager.shouldConfirmBeforeSaving()) {
            handleConfirmationCapture()
        } else {
            handleDirectCapture()
        }
    }

    /**
     * Enhanced capture mode is on but the accessibility service isn't granted yet — scrolling
     * can't work without it. Bounce the user to Accessibility Settings to enable it; capture
     * nothing this tap. Restores the floating button so they can retry after granting.
     */
    private fun promptEnableAccessibility() {
        Toast.makeText(
            applicationContext,
            getString(R.string.accessibility_help_body),
            Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
        }
        if (ScreenCaptureManager.isReady()) showFloatingControls()
    }

    private suspend fun handleConfirmationCapture() {
        val captured = try {
            delay(CAPTURE_STABILIZE_DELAY_MS)
            ScreenCaptureManager.captureForPreview(applicationContext)
        } finally {
            delay(OVERLAY_RESUME_DELAY_MS)
        }
        if (captured) {
            launchPreviewActivity()
        } else {
            Toast.makeText(applicationContext, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
            if (ScreenCaptureManager.isReady()) showFloatingControls()
        }
    }

    private suspend fun handleScrollCaptureStart() {
        val bitmap = try {
            delay(CAPTURE_STABILIZE_DELAY_MS)
            ScreenCaptureManager.captureToBitmap()
        } catch (_: Throwable) {
            null
        }

        if (bitmap == null) {
            delay(OVERLAY_RESUME_DELAY_MS)
            Toast.makeText(applicationContext, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
            if (ScreenCaptureManager.isReady()) showFloatingControls()
            return
        }

        enterScrollCaptureMode(bitmap)
    }

    private suspend fun handleDirectCapture() {
        val bitmap = try {
            delay(CAPTURE_STABILIZE_DELAY_MS)
            ScreenCaptureManager.captureToBitmap()
        } catch (_: Throwable) {
            null
        }

        if (bitmap == null) {
            delay(OVERLAY_RESUME_DELAY_MS)
            Toast.makeText(applicationContext, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
            if (ScreenCaptureManager.isReady()) showFloatingControls()
            return
        }

        val uri = withContext(Dispatchers.IO) {
            ScreenCaptureManager.saveStitchedBitmap(applicationContext, bitmap, isScroll = false)
        }
        bitmap.recycle()
        delay(OVERLAY_RESUME_DELAY_MS)
        if (uri != null) {
            showFloatingControlsWithSaveChip()
        } else {
            Toast.makeText(applicationContext, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
            if (ScreenCaptureManager.isReady()) showFloatingControls()
        }
    }

    private fun enterScrollCaptureMode(initialBitmap: Bitmap) {
        val session = ScrollCaptureSession(applicationContext, initialBitmap)
        scrollCaptureSession = session

        if (ScreenCaptureManager.isReady()) {
            FloatingCaptureOverlay.hide(this)
            showScrollToolbarFor(session, resolveFolderLabel())
        }
    }

    /**
     * Shows the live preview bar for [session] with [label]. Rebuilds the draft thumbnail each
     * time (the bar is torn down/re-shown per scroll step so it never appears in the capture)
     * and recycles the previous thumbnail after the new one is attached.
     */
    private fun showScrollToolbarFor(session: ScrollCaptureSession, label: String) {
        val density = resources.displayMetrics.density
        val widthPx = (PREVIEW_WIDTH_DP * density).roundToInt()
        val maxHeightPx = (PREVIEW_MAX_HEIGHT_DP * density).roundToInt()
        val newThumbnail = session.buildThumbnail(widthPx, maxHeightPx)

        FloatingCaptureOverlay.showScrollToolbar(
            this,
            label,
            newThumbnail,
            onScrollMore = { handleScrollMore(session) },
            onDone = { handleScrollDone(session) },
            onDelete = { handleScrollDelete(session) },
            onPreviewTap = { handleScrollOpen(session) }
        )

        val old = currentThumbnail
        currentThumbnail = newThumbnail
        if (old !== newThumbnail) old?.recycle()
    }

    private fun recycleThumbnail() {
        currentThumbnail?.recycle()
        currentThumbnail = null
    }

    private fun handleScrollMore(session: ScrollCaptureSession) {
        serviceScope.launch {
            FloatingCaptureOverlay.hideScrollToolbar(this@ScreenshotService)
            delay(OVERLAY_HIDE_DELAY_MS)
            delay(CAPTURE_STABILIZE_DELAY_MS)

            when (val result = session.scrollAndCapture(applicationContext)) {
                is ScrollCaptureSession.ScrollResult.Success -> {
                    delay(OVERLAY_RESUME_DELAY_MS)
                    if (ScreenCaptureManager.isReady()) {
                        showScrollToolbarFor(
                            session,
                            getString(R.string.scroll_capture_count, result.totalScrolls + 1)
                        )
                    }
                }
                is ScrollCaptureSession.ScrollResult.LimitReached -> {
                    delay(OVERLAY_RESUME_DELAY_MS)
                    Toast.makeText(applicationContext, getString(R.string.scroll_capture_limit), Toast.LENGTH_SHORT).show()
                    handleScrollDone(session)
                }
                is ScrollCaptureSession.ScrollResult.Error -> {
                    delay(OVERLAY_RESUME_DELAY_MS)
                    Toast.makeText(applicationContext, getString(R.string.scroll_capture_error, result.message), Toast.LENGTH_SHORT).show()
                    handleScrollDone(session)
                }
            }
        }
    }

    /** Done = save the current stitched draft directly (the live bar is itself the preview). */
    private fun handleScrollDone(session: ScrollCaptureSession) {
        if (scrollDoneInProgress) return
        scrollDoneInProgress = true
        serviceScope.launch {
            FloatingCaptureOverlay.hideScrollToolbar(this@ScreenshotService)
            scrollCaptureSession = null
            recycleThumbnail()

            val uri = session.saveResult(applicationContext)
            delay(OVERLAY_RESUME_DELAY_MS)
            if (uri != null) {
                showFloatingControlsWithSaveChip()
            } else {
                Toast.makeText(applicationContext, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
                if (ScreenCaptureManager.isReady()) showFloatingControls()
            }
            scrollDoneInProgress = false
        }
    }

    /** Tap preview = save the current draft directly, then open it in the system gallery. */
    private fun handleScrollOpen(session: ScrollCaptureSession) {
        if (scrollDoneInProgress) return
        scrollDoneInProgress = true
        serviceScope.launch {
            FloatingCaptureOverlay.hideScrollToolbar(this@ScreenshotService)
            scrollCaptureSession = null
            recycleThumbnail()

            val uri = session.saveResult(applicationContext)
            delay(OVERLAY_RESUME_DELAY_MS)
            if (uri != null) {
                if (ScreenCaptureManager.isReady()) showFloatingControls()
                openInGallery(uri)
            } else {
                Toast.makeText(applicationContext, getString(R.string.capture_failed), Toast.LENGTH_SHORT).show()
                if (ScreenCaptureManager.isReady()) showFloatingControls()
            }
            scrollDoneInProgress = false
        }
    }

    private fun openInGallery(uri: android.net.Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            // Image is already saved; no viewer app available — nothing more to do.
        }
    }

    /** Delete = discard the in-progress draft and exit. Saves nothing. */
    private fun handleScrollDelete(session: ScrollCaptureSession) {
        if (scrollDoneInProgress) return
        scrollDoneInProgress = true
        serviceScope.launch {
            FloatingCaptureOverlay.hideScrollToolbar(this@ScreenshotService)
            scrollCaptureSession = null
            session.cancel()
            recycleThumbnail()
            delay(OVERLAY_RESUME_DELAY_MS)
            if (ScreenCaptureManager.isReady()) showFloatingControls()
            scrollDoneInProgress = false
        }
    }

    /** Restores the floating button and shows the consistent bottom "Saved in {category}" bar. */
    private suspend fun showFloatingControlsWithSaveChip() {
        val folderLabel = resolveFolderLabel()
        val currentFolder = ScreenCaptureManager.currentSubdirectory.value
        val count = withContext(Dispatchers.IO) {
            ScreenCaptureManager.getFolderItemCounts(applicationContext, listOf(currentFolder))[currentFolder] ?: 0
        }
        if (ScreenCaptureManager.isReady()) {
            showFloatingControls()
            FloatingCaptureOverlay.showSavedMessage(
                applicationContext,
                getString(R.string.capture_saved_chip, folderLabel, count)
            )
        }
    }

    private fun launchPreviewActivity() {
        val intent = Intent(this, CapturePreviewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun handlePreviewDecision(accepted: Boolean) {
        serviceScope.launch {
            val successChipMessage = if (accepted) {
                val pending = ScreenCaptureManager.getPendingCapture()
                val folderLabel = pending?.let {
                    ScreenCaptureManager.getFolderLabel(applicationContext, it.subDirectory)
                } ?: resolveFolderLabel()
                val currentFolder = pending?.subDirectory ?: ScreenCaptureManager.currentSubdirectory.value
                val uri = ScreenCaptureManager.persistPendingCapture(applicationContext)
                if (uri != null) {
                    val count = withContext(Dispatchers.IO) {
                        ScreenCaptureManager.getFolderItemCounts(applicationContext, listOf(currentFolder))[currentFolder] ?: 0
                    }
                    getString(R.string.capture_saved_chip, folderLabel, count)
                } else {
                    null
                }
            } else {
                ScreenCaptureManager.discardPendingCapture()
                null
            }

            delay(OVERLAY_RESUME_DELAY_MS)
            if (ScreenCaptureManager.isReady()) {
                showFloatingControls()
                if (accepted) {
                    if (successChipMessage != null) {
                        FloatingCaptureOverlay.showSavedMessage(applicationContext, successChipMessage)
                    } else {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.capture_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.capture_discarded),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                stopSelf()
            }
        }
    }

    private fun observeSessionState() {
        sessionObserverJob = serviceScope.launch {
            ScreenCaptureManager.isSessionActive.collectLatest { active ->
                if (!active) {
                    FloatingCaptureOverlay.hide(this@ScreenshotService)
                }
            }
        }
    }

    private fun resolveFolderLabel(): String {
        return ScreenCaptureManager.getFolderLabel(this)
    }
}
