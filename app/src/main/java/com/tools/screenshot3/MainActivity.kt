package com.tools.screenshot3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tools.screenshot3.ui.theme.SamsungBlueGradientEnd
import com.tools.screenshot3.ui.theme.SamsungBlueGradientStart
import com.tools.screenshot3.ui.theme.SamsungBlueMedium
import com.tools.screenshot3.ui.theme.SamsungBlueSubtle
import com.tools.screenshot3.ui.theme.SamsungGreen
import com.tools.screenshot3.ui.theme.SamsungRedSoft
import com.tools.screenshot3.ui.theme.SamsungRedText
import com.tools.screenshot3.ui.theme.SamsungShadowBlue
import com.tools.screenshot3.ui.theme.SamsungTextTertiary
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tools.screenshot3.capture.ForegroundAppResolver
import com.tools.screenshot3.scroll.ScrollCaptureAccessibilityService
import com.tools.screenshot3.capture.ScreenCaptureManager
import com.tools.screenshot3.data.CollectionRepository
import com.tools.screenshot3.ui.theme.Screenshot3Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val OVERLAY_PERMISSION_PACKAGE_PREFIX = "package"

private data class ZipProgressUiState(
    val isRunning: Boolean = false,
    val currentCollectionLabel: String? = null,
    val completedCollections: Int = 0,
    val totalCollections: Int = 0,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val message: String? = null
)

class MainActivity : ComponentActivity() {
    private val showOverlayPermissionHelp = mutableStateOf(false)
    private val showUsageAccessHelp = mutableStateOf(false)
    private val hasUsageAccess = mutableStateOf(false)
    private val pendingCaptureAfterUsageAccess = mutableStateOf(false)
    private val hasAccessibilityService = mutableStateOf(false)

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                ScreenshotService.start(this, result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ensurePermissionsAndRequestCapture()
            } else {
                Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = requiredStoragePermissions().all { permission ->
                results[permission] == true || !shouldRequestStoragePermission()
            }
            if (granted) {
                ensurePermissionsAndRequestCapture()
            } else {
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (canDrawOverlays()) {
                ensurePermissionsAndRequestCapture()
            } else {
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val browseSnapshotsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val usageAccessSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshUsageAccessState()
            if (hasUsageAccess.value && pendingCaptureAfterUsageAccess.value) {
                pendingCaptureAfterUsageAccess.value = false
                ensurePermissionsAndRequestCapture()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateLocale(newBase, LocaleHelper.getSavedLanguage(newBase)))
    }

    override fun onResume() {
        super.onResume()
        refreshUsageAccessState()
        hasAccessibilityService.value = ScrollCaptureAccessibilityService.isEnabled(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScreenCaptureManager.initializeSettings(applicationContext)
        refreshUsageAccessState()
        val isFirstLaunch = savedInstanceState == null
        setContent {
            Screenshot3Theme(darkTheme = false, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val captureActive by ScreenCaptureManager.isSessionActive.collectAsState()
                    val selectedFolder by ScreenCaptureManager.currentSubdirectory.collectAsState()
                    val requiresConfirmation by ScreenCaptureManager.requiresConfirmation.collectAsState()
                    val scrollCaptureEnabled by ScreenCaptureManager.scrollCaptureEnabled.collectAsState()
                    MainScreen(
                        isCaptureReady = captureActive,
                        selectedFolder = selectedFolder,
                        onSelectFolder = { ScreenCaptureManager.updateSubdirectory(it) },
                        onRequestCapture = { startCaptureFlow() },
                        onStopService = { stopCaptureService() },
                        onOpenCollectionGuide = {
                            startActivity(Intent(this, CollectionGuideActivity::class.java))
                        },
                        onSaveCapture = { ScreenCaptureManager.captureAndStore(applicationContext) },
                        onBrowseSnapshots = { openSnapshotFolder() },
                        onOpenZipFolder = { openZipFolder() },
                        onOpenMyFiles = { openMyFilesApp() },
                        requiresConfirmation = requiresConfirmation,
                        onRequiresConfirmationChanged = {
                            ScreenCaptureManager.updateRequiresConfirmation(applicationContext, it)
                        },
                        hasUsageAccess = hasUsageAccess.value,
                        onOpenUsageAccessSettings = { requestUsageAccessPermission() },
                        showUsageAccessHelp = showUsageAccessHelp.value,
                        onDismissUsageAccessHelp = {
                            showUsageAccessHelp.value = false
                            pendingCaptureAfterUsageAccess.value = false
                        },
                        onContinueWithoutUsageAccess = {
                            showUsageAccessHelp.value = false
                            pendingCaptureAfterUsageAccess.value = false
                            ensurePermissionsAndRequestCapture()
                        },
                        onOpenUsageAccessFromDialog = {
                            showUsageAccessHelp.value = false
                            pendingCaptureAfterUsageAccess.value = true
                            requestUsageAccessPermission()
                        },
                        isFirstLaunch = isFirstLaunch,
                        showOverlayPermissionHelp = showOverlayPermissionHelp.value,
                        onDismissOverlayPermissionHelp = { showOverlayPermissionHelp.value = false },
                        onOpenOverlayPermissionSettings = {
                            showOverlayPermissionHelp.value = false
                            requestOverlayPermission()
                        },
                        hasAccessibilityService = hasAccessibilityService.value,
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        scrollCaptureEnabled = scrollCaptureEnabled,
                        onScrollCaptureChanged = {
                            ScreenCaptureManager.updateScrollCaptureEnabled(applicationContext, it)
                        }
                    )
                }
            }
        }
    }

    private fun startCaptureFlow() {
        if (!canDrawOverlays()) {
            showOverlayPermissionHelp.value = true
            return
        }
        if (!hasUsageAccess.value) {
            showUsageAccessHelp.value = true
            return
        }
        ensurePermissionsAndRequestCapture()
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("$OVERLAY_PERMISSION_PACKAGE_PREFIX:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun refreshUsageAccessState() {
        hasUsageAccess.value = ForegroundAppResolver.hasUsageAccess(this)
    }

    private fun requestUsageAccessPermission() {
        usageAccessSettingsLauncher.launch(ForegroundAppResolver.createUsageAccessSettingsIntent())
    }

    private fun openSnapshotFolder() {
        val initialUri = buildSnapshotDocumentUri()
        launchSamsungMyFiles(initialUri)
    }

    private fun openZipFolder() {
        val initialUri = buildZipDocumentUri()
        launchSamsungMyFiles(initialUri)
    }

    private fun openMyFilesApp() {
        val samsungMyFilesPackage = "com.sec.android.app.myfiles"
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(samsungMyFilesPackage)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "MyFiles app not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch MyFiles app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCaptureService() {
        ScreenshotService.stop(this)
    }

    private fun buildSnapshotDocumentUri(): Uri {
        val docId = "primary:${android.os.Environment.DIRECTORY_PICTURES}/Screenshot3"
        val encodedDocId = Uri.encode(docId)
        return Uri.parse("content://com.android.externalstorage.documents/document/$encodedDocId")
    }

    private fun buildZipDocumentUri(): Uri {
        val docId = "primary:Download/ScreenshotCollections"
        val encodedDocId = Uri.encode(docId)
        return Uri.parse("content://com.android.externalstorage.documents/document/$encodedDocId")
    }

    /**
     * Attempts to launch Samsung My Files app with a specific folder.
     * Falls back to generic file browser if Samsung My Files is not available.
     * 
     * @param folderUri The content:// URI of the folder to open
     */
    private fun launchSamsungMyFiles(folderUri: Uri) {
        val TAG = "MainActivity"
        Log.d(TAG, "SSM-launch-start uri=$folderUri")
        Log.d(TAG, "SSM-launch-android-sdk=${Build.VERSION.SDK_INT}")
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "SSM-launch-error android-too-old sdk=${Build.VERSION.SDK_INT}")
            Toast.makeText(this, R.string.snapshot_browse_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        val samsungMyFilesPackage = "com.sec.android.app.myfiles"
        Log.d(TAG, "SSM-launch-target-package=$samsungMyFilesPackage")
        
        // First, check if any app can handle this intent (to verify the URI is valid)
        val testIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "SSM-launch-creating-intent-q+")
            Intent(Intent.ACTION_VIEW).apply {
                data = folderUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            }
        } else {
            Log.d(TAG, "SSM-launch-creating-intent-pre-q")
            Intent(Intent.ACTION_VIEW).apply {
                data = folderUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        Log.d(TAG, "SSM-launch-test-intent action=${testIntent.action} data=${testIntent.data} extras=${testIntent.extras?.keySet()}")

        // Check if Samsung My Files is installed
        val isSamsungMyFilesInstalled = try {
            val packageInfo = packageManager.getPackageInfo(samsungMyFilesPackage, 0)
            Log.d(TAG, "SSM-launch-samsung-installed versionName=${packageInfo.versionName} versionCode=${packageInfo.longVersionCode}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "SSM-launch-samsung-not-installed error=${e.message}")
            false
        }

        // Try Samsung My Files specific intent if installed
        if (isSamsungMyFilesInstalled) {
            Log.d(TAG, "SSM-launch-attempting-samsung")
            
            // Try approach 1: OPEN_OPERATION_DESTINATION (Samsung-specific intent for opening folders)
            try {
                val samsungIntent1 = Intent("com.sec.android.app.myfiles.OPEN_OPERATION_DESTINATION").apply {
                    setPackage(samsungMyFilesPackage)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    data = folderUri
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                }
                Log.d(TAG, "SSM-launch-samsung-intent1 action=${samsungIntent1.action} package=${samsungIntent1.`package`} data=${samsungIntent1.data}")
                
                val resolved1 = samsungIntent1.resolveActivity(packageManager)
                Log.d(TAG, "SSM-launch-samsung-resolve1 result=$resolved1")
                
                if (resolved1 != null) {
                    Log.d(TAG, "SSM-launch-samsung-starting1")
                    startActivity(samsungIntent1)
                    Log.d(TAG, "SSM-launch-samsung-success1")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "SSM-launch-samsung-failed1", e)
            }
            
            // Try approach 2: ACTION_VIEW with EXTRA_INITIAL_URI only (no data URI)
            try {
                val samsungIntent2 = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(samsungMyFilesPackage)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                }
                Log.d(TAG, "SSM-launch-samsung-intent2 package=${samsungIntent2.`package`} action=${samsungIntent2.action} data=${samsungIntent2.data} hasExtra=${samsungIntent2.hasExtra(DocumentsContract.EXTRA_INITIAL_URI)}")
                
                val resolved2 = samsungIntent2.resolveActivity(packageManager)
                Log.d(TAG, "SSM-launch-samsung-resolve2 result=$resolved2")
                
                if (resolved2 != null) {
                    Log.d(TAG, "SSM-launch-samsung-starting2")
                    startActivity(samsungIntent2)
                    Log.d(TAG, "SSM-launch-samsung-success2")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "SSM-launch-samsung-failed2", e)
            }
            
            // Try approach 3: ACTION_VIEW with data URI
            try {
                val samsungIntent3 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage(samsungMyFilesPackage)
                        data = folderUri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage(samsungMyFilesPackage)
                        data = folderUri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                Log.d(TAG, "SSM-launch-samsung-intent3 package=${samsungIntent3.`package`} action=${samsungIntent3.action} data=${samsungIntent3.data}")
                
                val resolved3 = samsungIntent3.resolveActivity(packageManager)
                Log.d(TAG, "SSM-launch-samsung-resolve3 result=$resolved3")
                
                if (resolved3 != null) {
                    Log.d(TAG, "SSM-launch-samsung-starting3")
                    startActivity(samsungIntent3)
                    Log.d(TAG, "SSM-launch-samsung-success3")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "SSM-launch-samsung-failed3", e)
            }
            
            // Try approach 4: Try without package restriction to see if it works
            try {
                val samsungIntent4 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = folderUri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = folderUri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                Log.d(TAG, "SSM-launch-samsung-intent4 (no package) action=${samsungIntent4.action} data=${samsungIntent4.data}")
                
                val resolved4 = samsungIntent4.resolveActivity(packageManager)
                Log.d(TAG, "SSM-launch-samsung-resolve4 result=$resolved4")
                
                if (resolved4 != null) {
                    Log.d(TAG, "SSM-launch-samsung-starting4")
                    startActivity(samsungIntent4)
                    Log.d(TAG, "SSM-launch-samsung-success4")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "SSM-launch-samsung-failed4", e)
            }
            
            Log.w(TAG, "SSM-launch-samsung-all-attempts-failed")
        }

        // Fallback to generic file browser
        Log.d(TAG, "SSM-launch-fallback-generic")
        val resolvedTest = testIntent.resolveActivity(packageManager)
        Log.d(TAG, "SSM-launch-test-resolve result=$resolvedTest")
        
        if (resolvedTest != null) {
            Log.d(TAG, "SSM-launch-test-resolved attempting-start")
            try {
                startActivity(testIntent)
                Log.d(TAG, "SSM-launch-generic-success")
            } catch (e: Exception) {
                Log.e(TAG, "SSM-launch-generic-failed", e)
                Log.e(TAG, "SSM-launch-generic-exception type=${e.javaClass.simpleName} message=${e.message}")
                // Try with ACTION_OPEN_DOCUMENT_TREE as last resort
                Log.d(TAG, "SSM-launch-trying-tree-fallback")
                try {
                    val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                    }
                    Log.d(TAG, "SSM-launch-tree-intent action=${treeIntent.action} extras=${treeIntent.extras?.keySet()}")
                    val resolvedTree = treeIntent.resolveActivity(packageManager)
                    Log.d(TAG, "SSM-launch-tree-resolve result=$resolvedTree")
                    
                    // Try launching even if resolveActivity returns null
                    try {
                        Log.d(TAG, "SSM-launch-tree-launching")
                        browseSnapshotsLauncher.launch(treeIntent)
                    } catch (e3: Exception) {
                        Log.e(TAG, "SSM-launch-tree-launcher-failed", e3)
                        if (resolvedTree == null) {
                            Log.e(TAG, "SSM-launch-tree-cannot-resolve")
                            Toast.makeText(this, "Unable to open folder", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "SSM-launch-tree-failed", e2)
                    Log.e(TAG, "SSM-launch-tree-exception type=${e2.javaClass.simpleName} message=${e2.message}")
                    Toast.makeText(this, "Unable to open folder", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.w(TAG, "SSM-launch-test-cannot-resolve trying-tree")
            // Last resort: try ACTION_OPEN_DOCUMENT_TREE (should always work on Android 5.0+)
            try {
                val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                }
                Log.d(TAG, "SSM-launch-tree-intent action=${treeIntent.action} extras=${treeIntent.extras?.keySet()}")
                
                // ACTION_OPEN_DOCUMENT_TREE should always resolve, but check anyway
                val resolvedTree = treeIntent.resolveActivity(packageManager)
                Log.d(TAG, "SSM-launch-tree-resolve result=$resolvedTree")
                
                // Even if resolveActivity returns null, try launching it (some systems have issues with resolveActivity)
                try {
                    Log.d(TAG, "SSM-launch-tree-launching-via-launcher")
                    browseSnapshotsLauncher.launch(treeIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "SSM-launch-tree-launcher-failed", e)
                    if (resolvedTree == null) {
                        Log.e(TAG, "SSM-launch-tree-cannot-resolve-no-browser")
                        Toast.makeText(this, "No file browser available", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSM-launch-tree-create-failed", e)
                Log.e(TAG, "SSM-launch-tree-exception type=${e.javaClass.simpleName} message=${e.message}")
                Toast.makeText(this, "No file browser available", Toast.LENGTH_SHORT).show()
            }
        }
        Log.d(TAG, "SSM-launch-end")
    }

    private fun ensurePermissionsAndRequestCapture() {
        if (shouldRequestStoragePermission()) {
            storagePermissionLauncher.launch(requiredStoragePermissions())
            return
        }

        if (shouldRequestNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestMediaProjection()
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun shouldRequestStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return requiredStoragePermissions().any { permission ->
            ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredStoragePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> emptyArray()
        }
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            manager.createScreenCaptureIntent()
        }
        mediaProjectionLauncher.launch(intent)
    }
}

private data class CollectionOption(val key: String, val label: String, val assignee: String = "")

@Composable
fun MainScreen(
    isCaptureReady: Boolean,
    selectedFolder: String,
    onSelectFolder: (String) -> Unit,
    onRequestCapture: () -> Unit,
    onStopService: () -> Unit,
    onOpenCollectionGuide: () -> Unit,
    onSaveCapture: suspend () -> android.net.Uri?,
    onBrowseSnapshots: () -> Unit,
    onOpenZipFolder: () -> Unit,
    onOpenMyFiles: () -> Unit,
    requiresConfirmation: Boolean,
    onRequiresConfirmationChanged: (Boolean) -> Unit,
    hasUsageAccess: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    showUsageAccessHelp: Boolean = false,
    onDismissUsageAccessHelp: () -> Unit = {},
    onContinueWithoutUsageAccess: () -> Unit = {},
    onOpenUsageAccessFromDialog: () -> Unit = {},
    isFirstLaunch: Boolean = false,
    showOverlayPermissionHelp: Boolean = false,
    onDismissOverlayPermissionHelp: () -> Unit = {},
    onOpenOverlayPermissionSettings: () -> Unit = {},
    hasAccessibilityService: Boolean = true,
    onOpenAccessibilitySettings: () -> Unit = {},
    scrollCaptureEnabled: Boolean = false,
    onScrollCaptureChanged: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val scope = rememberCoroutineScope()
    val (statusMessage, setStatusMessage) = remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val captureEvents by ScreenCaptureManager.captureEvents.collectAsState()

    val customCollectionsState = remember { mutableStateOf<List<CollectionRepository.CustomCollection>>(emptyList()) }
    val customCollections = customCollectionsState.value

    val defaultOption = CollectionOption("", stringResource(R.string.folder_default))
    val folderOptions = listOf(defaultOption) + customCollections.map { CollectionOption(it.key, it.label, it.assignee) }
    val folderKeys = remember(folderOptions) { folderOptions.map { it.key } }

    val folderCountsState = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val folderCountsLoadedState = remember { mutableStateOf(false) }
    val folderCounts = folderCountsState.value
    val folderCountsLoaded = folderCountsLoadedState.value

    val selectedOption = folderOptions.firstOrNull { it.key == selectedFolder }
    val selectedLabel = selectedOption?.label
        ?: selectedFolder.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() }
        ?: defaultOption.label
    val selectedCount = folderCounts[selectedFolder]
    val selectedLabelWithCount = if (folderCountsLoaded && selectedCount != null) {
        "$selectedLabel ($selectedCount)"
    } else {
        selectedLabel
    }

    val totalCollections = folderOptions.size
    val totalSnapshots = if (folderCountsLoaded) folderCounts.values.sum() else 0

    val assigneeFilter = remember { mutableStateOf("") }

    val showAddDialog = remember { mutableStateOf(false) }
    val newCollectionName = remember { mutableStateOf("") }
    val addCollectionError = remember { mutableStateOf<Int?>(null) }
    val isAddingCollection = remember { mutableStateOf(false) }

    val showZipDialog = remember { mutableStateOf(false) }
    val zipUsername = remember {
        mutableStateOf(
            CollectionRepository.getLastZipUsername(context.applicationContext) ?: ""
        )
    }
    val zipErrorMessage = remember { mutableStateOf<Int?>(null) }
    val zipStatusMessage = remember { mutableStateOf<String?>(null) }
    val isZipping = remember { mutableStateOf(false) }
    val zipProgressState = remember { mutableStateOf(ZipProgressUiState()) }
    val showZipSuccessDialog = remember { mutableStateOf(false) }
    val showAboutDialog = remember { mutableStateOf(false) }
    val showInitialReadyDialog = remember { mutableStateOf(false) }
    val showReadyToGoDialog = remember { mutableStateOf(false) }

    // Show initial ready dialog when app starts and service is not running
    LaunchedEffect(Unit) {
        if (!isCaptureReady && !isPreview) {
            showInitialReadyDialog.value = true
        }
    }

    // Show ready to go dialog when service starts running
    LaunchedEffect(isCaptureReady) {
        if (isCaptureReady && !isPreview) {
            showReadyToGoDialog.value = true
        }
    }

    // Jiggle floating button only on first app launch
    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch && !isPreview) {
            // Delay to ensure overlay is shown if service is already running
            kotlinx.coroutines.delay(500)
            com.tools.screenshot3.overlay.FloatingCaptureOverlay.jiggleButton()
        }
    }

    LaunchedEffect(isPreview) {
        if (!isPreview) {
            CollectionRepository.loadCsvIfNeeded(context.applicationContext)
            customCollectionsState.value = CollectionRepository.load(context.applicationContext)
        } else {
            folderCountsState.value = folderKeys.associateWith { 0 }
            folderCountsLoadedState.value = true
        }
    }

    LaunchedEffect(folderKeys, captureEvents, isPreview, customCollections) {
        if (!isPreview) {
            folderCountsLoadedState.value = false
            folderCountsState.value = ScreenCaptureManager.getFolderItemCounts(
                context.applicationContext,
                folderKeys
            )
            folderCountsLoadedState.value = true
        } else {
            folderCountsState.value = folderKeys.associateWith { 0 }
            folderCountsLoadedState.value = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(SamsungBlueGradientStart, SamsungBlueGradientEnd)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }

        val sortedFolderOptions = listOf(folderOptions.first()) +
            folderOptions.drop(1).sortedBy { it.label.lowercase(Locale.getDefault()) }

        val filterText = assigneeFilter.value.trim()
        val filteredFolderOptions = if (filterText.isBlank()) {
            sortedFolderOptions
        } else {
            sortedFolderOptions.filter { option ->
                option.key.isEmpty() ||
                    option.label.contains(filterText, ignoreCase = true) ||
                    option.assignee.contains(filterText, ignoreCase = true)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.setup_section_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 6.dp,
                                shape = MaterialTheme.shapes.medium,
                                spotColor = SamsungShadowBlue,
                                ambientColor = SamsungShadowBlue
                            ),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.03f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )

                                Button(
                                    onClick = onRequestCapture,
                                    enabled = !isCaptureReady,
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer(
                                            scaleX = if (!isCaptureReady) scale else 1f,
                                            scaleY = if (!isCaptureReady) scale else 1f
                                        ),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(SamsungBlueGradientStart, SamsungBlueMedium)
                                                ),
                                                shape = RoundedCornerShape(50)
                                            )
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.button_start_capture),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Button(
                                    onClick = onStopService,
                                    enabled = isCaptureReady,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SamsungRedSoft,
                                        contentColor = SamsungRedText,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_stop_capture),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Text(
                                text = if (isCaptureReady) {
                                    stringResource(R.string.capture_ready)
                                } else {
                                    stringResource(R.string.capture_not_ready)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isCaptureReady) SamsungGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onScrollCaptureChanged(!scrollCaptureEnabled) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = scrollCaptureEnabled,
                                    onCheckedChange = { onScrollCaptureChanged(it) }
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.setting_scroll_capture_title),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.setting_scroll_capture_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!hasUsageAccess) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    tonalElevation = 2.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    color = SamsungBlueSubtle
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.usage_access_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.usage_access_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(onClick = onOpenUsageAccessSettings) {
                                            Text(text = stringResource(R.string.usage_access_open_settings))
                                        }
                                    }
                                }
                            }
                            if (scrollCaptureEnabled && !hasAccessibilityService) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    tonalElevation = 2.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    color = SamsungBlueSubtle
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.accessibility_help_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.accessibility_help_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(onClick = onOpenAccessibilitySettings) {
                                            Text(text = stringResource(R.string.accessibility_help_open_settings))
                                        }
                                    }
                                }
                            }
                            statusMessage?.let { message ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    tonalElevation = 2.dp,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onOpenCollectionGuide) {
                                    Text(text = stringResource(R.string.button_learn_more))
                                }
                                TextButton(
                                    onClick = {
                                        context.startActivity(Intent(context, AboutActivity::class.java))
                                    }
                                ) {
                                    Text(text = stringResource(R.string.button_help))
                                }
                            }
                        }
                    }

                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.collections_section_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 6.dp,
                                shape = MaterialTheme.shapes.medium,
                                spotColor = SamsungShadowBlue,
                                ambientColor = SamsungShadowBlue
                            ),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.selected_folder, selectedLabelWithCount),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.total_collections_format, totalCollections),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = if (folderCountsLoaded) {
                                        stringResource(R.string.total_snapshots_format, totalSnapshots)
                                    } else {
                                        stringResource(R.string.total_snapshots_loading)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = assigneeFilter.value,
                    onValueChange = { assigneeFilter.value = it },
                    label = { Text(text = stringResource(R.string.hint_filter_assignee)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = Color.White
                    ),
                    trailingIcon = {
                        if (assigneeFilter.value.isNotEmpty()) {
                            TextButton(onClick = { assigneeFilter.value = "" }) {
                                Text("✕")
                            }
                        }
                    }
                )
            }

            items(filteredFolderOptions, key = { it.key }) { option ->
                val count = folderCounts[option.key]
                val displayLabel = if (folderCountsLoaded && count != null) {
                    "${option.label} ($count)"
                } else {
                    option.label
                }
                val isSelected = option.key == selectedFolder
                CollectionItem(
                    label = displayLabel,
                    assignee = option.assignee,
                    isSelected = isSelected,
                    onClick = { onSelectFolder(option.key) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 6.dp,
                            shape = MaterialTheme.shapes.medium,
                            spotColor = SamsungShadowBlue,
                            ambientColor = SamsungShadowBlue
                        ),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        zipStatusMessage.value?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        newCollectionName.value = ""
                                        addCollectionError.value = null
                                        showAddDialog.value = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_add_collection),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Button(
                                    onClick = onBrowseSnapshots,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_browse_snapshots),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        zipErrorMessage.value = null
                                        showZipDialog.value = true
                                    },
                                    enabled = !isZipping.value,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_zip_collections),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                Button(
                                    onClick = onOpenZipFolder,
                                    enabled = !isZipping.value,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_open_zip_folder),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = onOpenMyFiles,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_open_myfiles),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 6.dp,
                            shape = MaterialTheme.shapes.medium,
                            spotColor = SamsungShadowBlue,
                            ambientColor = SamsungShadowBlue
                        ),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showAboutDialog.value = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = stringResource(R.string.button_about),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showAboutDialog.value) {
        if (showAboutDialog.value) {
            val intent = Intent(context, AboutActivity::class.java)
            context.startActivity(intent)
            showAboutDialog.value = false
        }
    }

    if (showAddDialog.value) {
        AlertDialog(
            onDismissRequest = {
                if (!isAddingCollection.value) {
                    showAddDialog.value = false
                    newCollectionName.value = ""
                    addCollectionError.value = null
                }
            },
            title = {
                Text(text = stringResource(R.string.add_collection_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCollectionName.value,
                        onValueChange = {
                            newCollectionName.value = it
                            addCollectionError.value = null
                        },
                        label = { Text(text = stringResource(R.string.add_collection_label)) },
                        singleLine = true
                    )
                    addCollectionError.value?.let { resId ->
                        Text(
                            text = stringResource(id = resId),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isAddingCollection.value) return@TextButton
                        isAddingCollection.value = true
                        scope.launch {
                            val reservedKeys =
                                customCollectionsState.value.map { it.key }.toSet()
                            val result = withContext(Dispatchers.IO) {
                                CollectionRepository.add(
                                    context.applicationContext,
                                    newCollectionName.value,
                                    reservedKeys
                                )
                            }
                            when (result) {
                                is CollectionRepository.AddResult.Success -> {
                                    customCollectionsState.value = result.collections
                                    showAddDialog.value = false
                                    newCollectionName.value = ""
                                    addCollectionError.value = null
                                }
                                is CollectionRepository.AddResult.Error -> {
                                    addCollectionError.value = result.messageRes
                                }
                            }
                            isAddingCollection.value = false
                        }
                    },
                    enabled = !isAddingCollection.value
                ) {
                    Text(text = stringResource(R.string.add_collection_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isAddingCollection.value) return@TextButton
                        showAddDialog.value = false
                        newCollectionName.value = ""
                        addCollectionError.value = null
                    }
                ) {
                    Text(text = stringResource(R.string.add_collection_cancel))
                }
            }
        )
    }

    if (showUsageAccessHelp && !isPreview) {
        AlertDialog(
            onDismissRequest = onDismissUsageAccessHelp,
            title = {
                Text(text = stringResource(R.string.usage_access_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.usage_access_dialog_body))
            },
            confirmButton = {
                TextButton(onClick = onOpenUsageAccessFromDialog) {
                    Text(text = stringResource(R.string.usage_access_open_settings))
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onContinueWithoutUsageAccess) {
                        Text(text = stringResource(R.string.usage_access_continue_without_suffix))
                    }
                    TextButton(onClick = onDismissUsageAccessHelp) {
                        Text(text = stringResource(R.string.overlay_permission_help_close))
                    }
                }
            }
        )
    }

    if (showZipDialog.value) {
        AlertDialog(
            onDismissRequest = {
                if (!isZipping.value) {
                    showZipDialog.value = false
                    zipErrorMessage.value = null
                }
            },
            title = {
                Text(text = stringResource(R.string.zip_collections_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = zipUsername.value,
                        onValueChange = {
                            zipUsername.value = it
                            zipErrorMessage.value = null
                        },
                        label = { Text(text = stringResource(R.string.zip_collections_username_label)) },
                        singleLine = true
                    )
                    zipErrorMessage.value?.let { resId ->
                        Text(
                            text = stringResource(id = resId),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isZipping.value) return@TextButton
                        val enteredName = zipUsername.value.trim()
                        if (enteredName.isEmpty()) {
                            zipErrorMessage.value = R.string.error_zip_username_empty
                            return@TextButton
                        }
                        isZipping.value = true
                        zipStatusMessage.value = context.getString(R.string.zip_collections_progress)
                        zipErrorMessage.value = null
                        zipProgressState.value = ZipProgressUiState(
                            isRunning = true,
                            message = context.getString(R.string.zip_progress_preparing)
                        )
                        showZipDialog.value = false
                        scope.launch {
                            try {
                                val collectionsForZip = folderOptions.map { it.key to it.label }
                                val results = withContext(Dispatchers.IO) {
                                    ScreenCaptureManager.zipCollections(
                                        context.applicationContext,
                                        collectionsForZip,
                                        enteredName
                                    ) { progress ->
                                        withContext(Dispatchers.Main) {
                                            zipProgressState.value = ZipProgressUiState(
                                                isRunning = true,
                                                currentCollectionLabel = progress.currentCollectionLabel,
                                                completedCollections = progress.completedCollections,
                                                totalCollections = progress.totalCollections,
                                                completedFiles = progress.completedFiles,
                                                totalFiles = progress.totalFiles,
                                                message = if (progress.totalFiles > 0) {
                                                    context.getString(R.string.zip_progress_please_wait)
                                                } else {
                                                    context.getString(R.string.zip_progress_preparing)
                                                }
                                            )
                                        }
                                    }
                                }
                                val successCount = results.count { it.success }
                                val totalZips = results.size
                                val failureCount = totalZips - successCount
                                zipStatusMessage.value = when {
                                    totalZips == 0 -> context.getString(R.string.zip_collections_none)
                                    successCount == 0 -> context.getString(R.string.zip_collections_failure)
                                    failureCount == 0 -> context.getString(
                                        R.string.zip_collections_success,
                                        successCount
                                    )
                                    else -> context.getString(
                                        R.string.zip_collections_partial,
                                        successCount,
                                        failureCount
                                    )
                                }
                                if (successCount > 0) {
                                    CollectionRepository.saveLastZipUsername(
                                        context.applicationContext,
                                        enteredName
                                    )
                                    // Show success dialog if all zips succeeded
                                    if (failureCount == 0) {
                                        showZipSuccessDialog.value = true
                                    }
                                }
                                zipUsername.value = enteredName
                            } catch (_: Throwable) {
                                zipStatusMessage.value = context.getString(R.string.zip_collections_failure)
                            } finally {
                                zipProgressState.value = ZipProgressUiState()
                                zipErrorMessage.value = null
                                showZipDialog.value = false
                                isZipping.value = false
                            }
                        }
                    },
                    enabled = !isZipping.value
                ) {
                    Text(text = stringResource(R.string.zip_collections_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isZipping.value) return@TextButton
                        showZipDialog.value = false
                        zipErrorMessage.value = null
                    }
                ) {
                    Text(text = stringResource(R.string.zip_collections_cancel))
                }
            }
        )
    }

    if (zipProgressState.value.isRunning) {
        val progressState = zipProgressState.value
        val progressFraction = if (progressState.totalFiles > 0) {
            progressState.completedFiles.toFloat() / progressState.totalFiles.toFloat()
        } else {
            null
        }
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.zip_progress_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = progressState.currentCollectionLabel?.let {
                            context.getString(R.string.zip_progress_current_collection, it)
                        } ?: stringResource(R.string.zip_progress_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (progressFraction != null) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    Text(
                        text = if (progressState.totalFiles > 0) {
                            stringResource(
                                R.string.zip_progress_files,
                                progressState.completedFiles,
                                progressState.totalFiles
                            )
                        } else {
                            stringResource(R.string.zip_progress_preparing)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (progressState.totalCollections > 0) {
                            stringResource(
                                R.string.zip_progress_collections,
                                progressState.completedCollections,
                                progressState.totalCollections
                            )
                        } else {
                            stringResource(R.string.zip_progress_preparing)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    progressState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = SamsungTextTertiary
                        )
                    }
                }
            }
        }
    }

    // Zip Success Dialog
    if (showZipSuccessDialog.value) {
        AlertDialog(
            onDismissRequest = { showZipSuccessDialog.value = false },
            title = {
                Text(text = stringResource(R.string.zip_collections_title))
            },
            text = {
                Text(text = stringResource(R.string.zip_collections_success_dialog))
            },
            confirmButton = {
                TextButton(onClick = { showZipSuccessDialog.value = false }) {
                    Text(text = stringResource(R.string.setup_help_close))
                }
            }
        )
    }

    // Initial Ready Dialog
    if (showInitialReadyDialog.value) {
        AlertDialog(
            onDismissRequest = { showInitialReadyDialog.value = false },
            title = {
                Text(text = stringResource(R.string.screen_title))
            },
            text = {
                Text(text = stringResource(R.string.capture_not_ready))
            },
            confirmButton = {
                TextButton(onClick = { showInitialReadyDialog.value = false }) {
                    Text(text = stringResource(R.string.setup_help_close))
                }
            }
        )
    }

    // Ready to Go Dialog
    if (showReadyToGoDialog.value) {
        AlertDialog(
            onDismissRequest = { showReadyToGoDialog.value = false },
            title = {
                Text(text = stringResource(R.string.screen_title))
            },
            text = {
                Text(text = stringResource(R.string.capture_ready))
            },
            confirmButton = {
                TextButton(onClick = { showReadyToGoDialog.value = false }) {
                    Text(text = stringResource(R.string.setup_help_close))
                }
            }
        )
    }

    if (showOverlayPermissionHelp && !isPreview) {
        OverlayPermissionHelpDialog(
            appName = stringResource(R.string.app_name),
            onDismiss = onDismissOverlayPermissionHelp,
            onOpenSettings = onOpenOverlayPermissionSettings
        )
    }

}

@Composable
private fun OverlayPermissionHelpDialog(
    appName: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.overlay_permission_help_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.overlay_permission_help_body, appName),
                    style = MaterialTheme.typography.bodyMedium
                )
                OverlayPermissionMockGraphic(
                    appName = appName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 360.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.overlay_permission_help_close))
                    }
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.overlay_permission_help_open_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayPermissionMockGraphic(
    appName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color(0xFFF5F1FA),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.overlay_permission_mock_heading),
                style = MaterialTheme.typography.headlineSmall
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFE5F0FF),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF4A78D6), MaterialTheme.shapes.medium)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color(0xFFFFE7B8),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_permission_mock_callout, appName),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    OverlayPermissionMockRow(
                        label = appName,
                        status = stringResource(R.string.overlay_permission_mock_not_allowed),
                        iconText = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                        highlighted = true
                    )
                }
            }

            OverlayPermissionMockRow(
                label = "Android Auto",
                status = stringResource(R.string.overlay_permission_mock_not_allowed),
                iconText = "A"
            )
            OverlayPermissionMockRow(
                label = "Google",
                status = stringResource(R.string.overlay_permission_mock_allowed),
                iconText = "G"
            )
            OverlayPermissionMockRow(
                label = "Google Play services",
                status = stringResource(R.string.overlay_permission_mock_not_allowed),
                iconText = "P"
            )
            OverlayPermissionMockRow(
                label = "Phone",
                status = stringResource(R.string.overlay_permission_mock_allowed),
                iconText = "P"
            )
            OverlayPermissionMockRow(
                label = "Photos",
                status = stringResource(R.string.overlay_permission_mock_not_allowed),
                iconText = "P"
            )
        }
    }
}

@Composable
private fun OverlayPermissionMockRow(
    label: String,
    status: String,
    iconText: String? = null,
    highlighted: Boolean = false
) {
    val rowColor = if (highlighted) Color(0xFFEAF2FF) else Color.Transparent
    val borderColor = if (highlighted) Color(0xFF4A78D6) else Color.Transparent
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = rowColor,
        tonalElevation = if (highlighted) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (highlighted) Color(0xFFDCE9FF) else Color(0xFFE6E6E6),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = iconText.orEmpty(),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CollectionItem(
    label: String,
    assignee: String = "",
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) SamsungBlueSubtle else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "itemBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "itemBorder"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 4.dp else 2.dp,
                shape = MaterialTheme.shapes.medium,
                spotColor = if (isSelected) SamsungShadowBlue else Color(0x0D000000),
                ambientColor = if (isSelected) SamsungShadowBlue else Color(0x0D000000)
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = if (isSelected) {
                    stringResource(R.string.selected_folder_format, label)
                } else {
                    label
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )
            if (assignee.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = assignee,
                    style = MaterialTheme.typography.bodySmall,
                    color = SamsungTextTertiary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    Screenshot3Theme {
        MainScreen(
            isCaptureReady = false,
            selectedFolder = "",
            onSelectFolder = {},
            onRequestCapture = {},
            onStopService = {},
            onOpenCollectionGuide = {},
            onSaveCapture = { null },
            onBrowseSnapshots = {},
            onOpenZipFolder = {},
            onOpenMyFiles = {},
            requiresConfirmation = false,
            onRequiresConfirmationChanged = {},
            hasUsageAccess = false,
            onOpenUsageAccessSettings = {},
            showUsageAccessHelp = false,
            onDismissUsageAccessHelp = {},
            onContinueWithoutUsageAccess = {},
            onOpenUsageAccessFromDialog = {},
            isFirstLaunch = false,
            contentPadding = PaddingValues()
        )
    }
}
