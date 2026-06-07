package com.tools.screenshot3.capture

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import com.tools.screenshot3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.jvm.Volatile

object ScreenCaptureManager {

    private const val VIRTUAL_DISPLAY_NAME = "Screenshot3Capture"
    private const val IMAGE_MAX_IMAGES = 4
    private const val PREFS_NAME = "screenshot3_preferences"
    private const val KEY_REQUIRE_CONFIRMATION = "require_confirmation"
    private const val KEY_SCROLL_CAPTURE = "scroll_capture_enabled"
    private const val KEY_IMAGE_FORMAT = "image_format"
    private const val KEY_STRIP_SCROLL_NAV_BAR = "strip_scroll_nav_bar"

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _currentSubdirectory = MutableStateFlow(DEFAULT_SUBDIRECTORY)
    val currentSubdirectory: StateFlow<String> = _currentSubdirectory.asStateFlow()

    private val _captureEvents = MutableStateFlow(0L)
    val captureEvents: StateFlow<Long> = _captureEvents.asStateFlow()

    private val _requiresConfirmation = MutableStateFlow(false)
    val requiresConfirmation: StateFlow<Boolean> = _requiresConfirmation.asStateFlow()

    private val _scrollCaptureEnabled = MutableStateFlow(false)
    val scrollCaptureEnabled: StateFlow<Boolean> = _scrollCaptureEnabled.asStateFlow()

    private val _imageFormat = MutableStateFlow(ScreenshotImageFormat.default)
    val imageFormat: StateFlow<ScreenshotImageFormat> = _imageFormat.asStateFlow()

    private val _stripScrollNavBar = MutableStateFlow(true)
    val stripScrollNavBar: StateFlow<Boolean> = _stripScrollNavBar.asStateFlow()

    @Volatile
    private var preferences: SharedPreferences? = null

    @Volatile
    private var preferencesInitialized = false

    private fun ensurePreferences(context: Context) {
        if (preferencesInitialized) return
        synchronized(this) {
            if (preferencesInitialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            preferences = prefs
            _requiresConfirmation.value = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, false)
            _scrollCaptureEnabled.value = prefs.getBoolean(KEY_SCROLL_CAPTURE, false)
            _imageFormat.value = ScreenshotImageFormat.fromStorageValue(
                prefs.getString(KEY_IMAGE_FORMAT, ScreenshotImageFormat.default.storageValue)
            )
            _stripScrollNavBar.value = prefs.getBoolean(KEY_STRIP_SCROLL_NAV_BAR, true)
            preferencesInitialized = true
        }
    }

    init {
        ensureLegacyDirectoryExists(DEFAULT_SUBDIRECTORY)
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private var captureDensity: Int = 0
    private var captureSurface: Surface? = null

    private var pendingCapture: PendingCaptureSnapshot? = null

    private var projectionCallback: MediaProjection.Callback? = null

    @Synchronized
    fun initialize(context: Context, projection: MediaProjection) {
        if (_isSessionActive.value) return

        ensurePreferences(context)

        val metrics = context.resources.displayMetrics
        captureWidth = metrics.widthPixels
        captureHeight = metrics.heightPixels
        captureDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            IMAGE_MAX_IMAGES
        )
        captureSurface = imageReader?.surface

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }

        projection.registerCallback(projectionCallback!!, null)

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            captureWidth,
            captureHeight,
            captureDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            captureSurface,
            null,
            null
        )

        mediaProjection = projection
        _isSessionActive.value = true
    }

    fun isReady(): Boolean = _isSessionActive.value

    @Synchronized
    fun release() {
        _isSessionActive.value = false
        projectionCallback?.let { callback ->
            mediaProjection?.unregisterCallback(callback)
        }
        projectionCallback = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        captureSurface?.release()
        captureSurface = null

        imageReader?.close()
        imageReader = null
    }

    suspend fun captureForPreview(context: Context, subDirectory: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (!_isSessionActive.value) return@withContext false
            synchronized(this@ScreenCaptureManager) {
                if (pendingCapture != null) {
                    Log.w(TAG, "SSM-preview-pending-existing")
                    return@withContext false
                }
            }
            val reader = imageReader ?: return@withContext false
            val image = reader.acquireLatestImage() ?: return@withContext false
            val targetDirectory = sanitizeSubdirectory(subDirectory ?: _currentSubdirectory.value)
            val appSuffix = ForegroundAppResolver.resolveForegroundAppSuffix(context.applicationContext)
            Log.d(TAG, "SSM-preview-capture-start targetDir=$targetDirectory")
            val success = try {
                val bitmap = image.toBitmap(captureWidth, captureHeight)
                setPendingCapture(bitmap, targetDirectory, appSuffix)
                Log.d(TAG, "SSM-preview-capture-success targetDir=$targetDirectory")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "SSM-preview-capture-error targetDir=$targetDirectory", t)
                false
            } finally {
                image.close()
                Log.d(TAG, "SSM-preview-imageClosed")
            }
            success
        }

    suspend fun captureAndStore(context: Context, subDirectory: String? = null): Uri? =
        withContext(Dispatchers.IO) {
            if (!_isSessionActive.value) return@withContext null
            val reader = imageReader ?: return@withContext null
            val image = reader.acquireLatestImage() ?: return@withContext null
            val targetDirectory = sanitizeSubdirectory(subDirectory ?: _currentSubdirectory.value)
            val appSuffix = ForegroundAppResolver.resolveForegroundAppSuffix(context.applicationContext)
            Log.d(TAG, "SSM-capture-start targetDir=$targetDirectory")

            val savedUri = try {
                val bitmap = image.toBitmap(captureWidth, captureHeight)
                saveBitmap(context, bitmap, targetDirectory, appSuffix)
            } finally {
                image.close()
                Log.d(TAG, "SSM-capture-imageClosed")
            }

            if (savedUri != null) {
                _captureEvents.value = System.currentTimeMillis()
                Log.d(TAG, "SSM-capture-success uri=$savedUri")
            } else {
                Log.e(TAG, "SSM-capture-failed targetDir=$targetDirectory")
            }

            savedUri
        }

    suspend fun captureToBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        if (!_isSessionActive.value) return@withContext null
        val reader = imageReader ?: return@withContext null
        val image = reader.acquireLatestImage() ?: return@withContext null
        try {
            image.toBitmap(captureWidth, captureHeight)
        } catch (t: Throwable) {
            Log.e(TAG, "SSM-captureToBitmap-error", t)
            null
        } finally {
            image.close()
        }
    }

    suspend fun saveStitchedBitmap(context: Context, bitmap: Bitmap): Uri? =
        withContext(Dispatchers.IO) {
            val subDirectory = _currentSubdirectory.value
            val appSuffix = ForegroundAppResolver.resolveForegroundAppSuffix(context.applicationContext)
            val uri = saveBitmap(context, bitmap, subDirectory, appSuffix, isScroll = true)
            if (uri != null) {
                _captureEvents.value = System.currentTimeMillis()
            }
            uri
        }

    suspend fun persistPendingCapture(context: Context): Uri? {
        val capture = synchronized(this) { pendingCapture } ?: run {
            Log.w(TAG, "SSM-persist-noPending")
            return null
        }
        Log.d(TAG, "SSM-persist-start targetDir=${capture.subDirectory}")
        val uri = withContext(Dispatchers.IO) {
            saveBitmap(context, capture.bitmap, capture.subDirectory, capture.appSuffix)
        }
        if (uri != null) {
            _captureEvents.value = System.currentTimeMillis()
            Log.d(TAG, "SSM-persist-success uri=$uri")
        } else {
            Log.e(TAG, "SSM-persist-failed targetDir=${capture.subDirectory}")
        }
        discardPendingCapture()
        return uri
    }

    fun discardPendingCapture() {
        synchronized(this) {
            if (pendingCapture != null) {
                Log.d(TAG, "SSM-persist-discard targetDir=${pendingCapture?.subDirectory}")
            }
            pendingCapture = null
        }
    }

    fun getPendingCapture(): PendingCaptureSnapshot? =
        synchronized(this) { pendingCapture }

    fun initializeSettings(context: Context) {
        ensurePreferences(context)
    }

    fun updateRequiresConfirmation(context: Context, enabled: Boolean) {
        ensurePreferences(context)
        if (_requiresConfirmation.value == enabled) return
        _requiresConfirmation.value = enabled
        preferences?.edit()?.putBoolean(KEY_REQUIRE_CONFIRMATION, enabled)?.apply()
        if (!enabled) {
            discardPendingCapture()
        }
    }

    fun shouldConfirmBeforeSaving(): Boolean = _requiresConfirmation.value

    fun updateScrollCaptureEnabled(context: Context, enabled: Boolean) {
        ensurePreferences(context)
        if (_scrollCaptureEnabled.value == enabled) return
        _scrollCaptureEnabled.value = enabled
        preferences?.edit()?.putBoolean(KEY_SCROLL_CAPTURE, enabled)?.apply()
    }

    fun isScrollCaptureEnabled(): Boolean = _scrollCaptureEnabled.value

    fun updateImageFormat(context: Context, format: ScreenshotImageFormat) {
        ensurePreferences(context)
        if (_imageFormat.value == format) return
        _imageFormat.value = format
        preferences?.edit()?.putString(KEY_IMAGE_FORMAT, format.storageValue)?.apply()
    }

    fun currentImageFormat(): ScreenshotImageFormat = _imageFormat.value

    fun updateStripScrollNavBar(context: Context, enabled: Boolean) {
        ensurePreferences(context)
        if (_stripScrollNavBar.value == enabled) return
        _stripScrollNavBar.value = enabled
        preferences?.edit()?.putBoolean(KEY_STRIP_SCROLL_NAV_BAR, enabled)?.apply()
    }

    fun shouldStripScrollNavBar(): Boolean = _stripScrollNavBar.value

    fun getFolderLabel(context: Context, folder: String = currentSubdirectory.value): String {
        val current = sanitizeSubdirectory(folder)
        if (current.isEmpty()) {
            return context.applicationContext.resources.getString(com.tools.screenshot3.R.string.folder_default)
        }
        val match = com.tools.screenshot3.data.CollectionRepository.load(context.applicationContext)
            .firstOrNull { it.key == current }
        if (match != null) return match.label
        return current.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    }

    private fun Image.toBitmap(width: Int, height: Int): Bitmap {
        val plane = planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    private fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        subDirectory: String,
        appSuffix: String?,
        isScroll: Boolean = false
    ): Uri? {
        val collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val resolver = context.contentResolver
        val format = currentImageFormat()
        val name = ScreenshotFilenameFormatter.buildScreenshotFilename(
            appSuffix = appSuffix,
            format = format,
            isScroll = isScroll
        )
        val values = ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    buildRelativePath(subDirectory)
                )
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val directory = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    buildLegacyPath(subDirectory)
                )
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val file = java.io.File(directory, name)
                put(android.provider.MediaStore.Images.Media.DATA, file.absolutePath)
            }
        }

        val uri = resolver.insert(collection, values) ?: return null
        val outputStream = resolver.openOutputStream(uri) ?: run {
            resolver.delete(uri, null, null)
            return null
        }

        outputStream.use { stream ->
            if (!bitmap.compress(format.compressFormat, format.compressQuality, stream)) {
                resolver.delete(uri, null, null)
                return null
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val completedValues = ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, completedValues, null, null)
        }

        return uri
    }

    suspend fun getFolderItemCounts(
        context: Context,
        subDirectories: List<String>
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val counts = mutableMapOf<String, Int>()
        val resolver = context.contentResolver
        Log.d(TAG, "SSM-count-start folders=${subDirectories.size}")

        subDirectories.forEach { originalKey ->
            val sanitized = sanitizeSubdirectory(originalKey)

            val relativePath = buildRelativePath(sanitized)
            val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val trimmedPath = relativePath.removeSuffix("/")
                val selection: String
                val selectionArgs: Array<String>
                if (trimmedPath.isNotEmpty()) {
                    selection =
                        "${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                    selectionArgs = arrayOf(relativePath, trimmedPath)
                } else {
                    selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                    selectionArgs = arrayOf(relativePath)
                }
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    cursor.count
                } ?: 0
            } else {
                val directory = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    buildLegacyPath(sanitized)
                )
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles()?.size ?: 0
                } else {
                    0
                }
            }
            counts[originalKey] = count
            Log.d(TAG, "SSM-count-folder key=$sanitized count=$count")
        }
        Log.d(TAG, "SSM-count-end")
        counts
    }

    /** One app's screenshot stats: total plus a per-collection breakdown (collection keys). */
    data class AppStat(
        val appKey: String,
        val appLabel: String,
        val total: Int,
        val byCollection: Map<String, Int>,
        /** Subset of [byCollection] from scroll captures (the rest are regular). */
        val scrollByCollection: Map<String, Int> = emptyMap()
    )

    /**
     * Per-app screenshot counts derived live from MediaStore. Plain suspend fun (like
     * [getFolderItemCounts]) — the caller holds the result in composable-local state.
     * App labels are title-cased from the sanitized suffix; the empty suffix bucket gets
     * the localized "Unknown" label.
     */
    suspend fun getAppStats(context: Context): List<AppStat> = withContext(Dispatchers.IO) {
        val rows = queryAllScreenshots(context)
        val unknownLabel = context.getString(R.string.stats_unknown_app)
        ScreenshotStatsParser.aggregate(rows).map { raw ->
            AppStat(
                appKey = raw.appKey,
                appLabel = if (raw.appKey.isEmpty()) {
                    unknownLabel
                } else {
                    ScreenshotStatsParser.titleCase(raw.appKey)
                },
                total = raw.total,
                byCollection = raw.byCollection,
                scrollByCollection = raw.scrollByCollection
            )
        }
    }

    /**
     * Single query over the whole `Pictures/Screenshot3/` tree, returning
     * (DISPLAY_NAME, RELATIVE_PATH) pairs for [ScreenshotStatsParser] to aggregate.
     */
    private fun queryAllScreenshots(context: Context): List<Pair<String, String>> {
        val resolver = context.contentResolver
        val rows = mutableListOf<Pair<String, String>>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val base = "${Environment.DIRECTORY_PICTURES}/Screenshot3"
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            val selection =
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? OR " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ? OR " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("$base/", base, "$base/%")

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val path = cursor.getString(pathIndex) ?: ""
                    rows.add(name to path)
                }
            }
        } else {
            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Screenshot3"
            )
            val basePath = root.absolutePath
            val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$basePath%")

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue
                    val name = cursor.getString(nameIndex) ?: file.name
                    // Synthesize a relative path so the parser can extract the collection key.
                    val parent = file.parentFile?.name ?: ""
                    val relativePath = if (parent == "Screenshot3") {
                        "Screenshot3/"
                    } else {
                        "Screenshot3/$parent/"
                    }
                    rows.add(name to relativePath)
                }
            }
        }

        Log.d(TAG, "SSM-appstats-rows count=${rows.size}")
        return rows
    }

    data class CollectionZipResult(
        val key: String,
        val label: String,
        val fileCount: Int,
        val success: Boolean,
        val uri: Uri?,
        val outputPath: String? = null,
        val errorMessage: String? = null
    )

    data class ZipProgress(
        val currentCollectionLabel: String? = null,
        val completedCollections: Int = 0,
        val totalCollections: Int = 0,
        val completedFiles: Int = 0,
        val totalFiles: Int = 0
    )

    private data class CollectionZipJob(
        val key: String,
        val label: String,
        val items: List<MediaItem>
    )

    suspend fun zipCollections(
        context: Context,
        collections: List<Pair<String, String>>,
        username: String,
        onProgress: suspend (ZipProgress) -> Unit = { }
    ): List<CollectionZipResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CollectionZipResult>()
        val normalizedUser = ScreenshotFilenameFormatter.sanitizeFileSegment(username).ifEmpty { "user" }
        Log.d(TAG, "SSM-zip-start totalCollections=${collections.size} user=$normalizedUser")

        val jobs = collections.mapNotNull { (key, label) ->
            val sanitizedKey = sanitizeSubdirectory(key)
            val effectiveLabel = label.ifBlank { DEFAULT_COLLECTION_LABEL }
            val items = queryCollectionItems(context, sanitizedKey)
            if (items.isEmpty()) {
                Log.d(TAG, "SSM-zip-skip-empty key=$sanitizedKey")
                null
            } else {
                CollectionZipJob(
                    key = sanitizedKey,
                    label = effectiveLabel,
                    items = items
                )
            }
        }
        val totalCollections = jobs.size
        val totalFiles = jobs.sumOf { it.items.size }
        onProgress(
            ZipProgress(
                totalCollections = totalCollections,
                totalFiles = totalFiles
            )
        )

        var completedCollections = 0
        var completedFiles = 0

        jobs.forEach { job ->
            val sanitizedKey = job.key
            val effectiveLabel = job.label
            val items = job.items
            val zipFileName = buildZipFileName(normalizedUser, effectiveLabel, items.size)
            onProgress(
                ZipProgress(
                    currentCollectionLabel = effectiveLabel,
                    completedCollections = completedCollections,
                    totalCollections = totalCollections,
                    completedFiles = completedFiles,
                    totalFiles = totalFiles
                )
            )
            Log.d(TAG, "SSM-zip-prepare key=$sanitizedKey items=${items.size}")

            val destination = createZipDestination(context, zipFileName)
            if (destination == null) {
                Log.e(TAG, "SSM-zip-destination-null key=$sanitizedKey")
                results.add(
                    CollectionZipResult(
                        key = sanitizedKey,
                        label = effectiveLabel,
                        fileCount = items.size,
                        success = false,
                        uri = null,
                        errorMessage = "Unable to create zip destination"
                    )
                )
                completedCollections += 1
                completedFiles += items.size
                onProgress(
                    ZipProgress(
                        currentCollectionLabel = effectiveLabel,
                        completedCollections = completedCollections,
                        totalCollections = totalCollections,
                        completedFiles = completedFiles,
                        totalFiles = totalFiles
                    )
                )
                return@forEach
            }

            var success = false
            try {
                Log.d(TAG, "SSM-zip-writing key=$sanitizedKey file=${destination.path}")
                BufferedOutputStream(destination.outputStream).use { buffered ->
                    ZipOutputStream(buffered).use { zipStream ->
                        items.forEach { item ->
                            try {
                                val inputStream = item.open()
                                if (inputStream != null) {
                                    val entryName = item.name.ifBlank { "image_${System.currentTimeMillis()}" }
                                    val zipEntry = ZipEntry(entryName)
                                    zipStream.putNextEntry(zipEntry)
                                    BufferedInputStream(inputStream).use { bufferedInput ->
                                        bufferedInput.copyTo(zipStream)
                                    }
                                    zipStream.closeEntry()
                                } else {
                                    Log.w(TAG, "SSM-zip-missingInput key=$sanitizedKey file=${item.name}")
                                }
                            } finally {
                                completedFiles += 1
                                onProgress(
                                    ZipProgress(
                                        currentCollectionLabel = effectiveLabel,
                                        completedCollections = completedCollections,
                                        totalCollections = totalCollections,
                                        completedFiles = completedFiles,
                                        totalFiles = totalFiles
                                    )
                                )
                            }
                        }
                    }
                }
                success = true
                destination.finalize()
                Log.d(TAG, "SSM-zip-success key=$sanitizedKey path=${destination.path}")
            } catch (t: Throwable) {
                destination.cleanup()
                Log.e(TAG, "SSM-zip-error key=$sanitizedKey", t)
                results.add(
                    CollectionZipResult(
                        key = sanitizedKey,
                        label = effectiveLabel,
                        fileCount = items.size,
                        success = false,
                        uri = destination.uri,
                        errorMessage = t.message
                    )
                )
            }

            completedCollections += 1
            onProgress(
                ZipProgress(
                    currentCollectionLabel = effectiveLabel,
                    completedCollections = completedCollections,
                    totalCollections = totalCollections,
                    completedFiles = completedFiles,
                    totalFiles = totalFiles
                )
            )

            if (success) {
                results.add(
                    CollectionZipResult(
                        key = sanitizedKey,
                        label = effectiveLabel,
                        fileCount = items.size,
                        success = true,
                        uri = destination.uri,
                        outputPath = destination.path
                    )
                )
            }
        }

        Log.d(TAG, "SSM-zip-end success=${results.count { it.success }} failures=${results.count { !it.success }}")
        results
    }

    fun updateSubdirectory(target: String) {
        val sanitized = sanitizeSubdirectory(target)
        _currentSubdirectory.value = sanitized
        ensureLegacyDirectoryExists(sanitized)
        Log.d(TAG, "SSM-update-subdirectory $sanitized")
    }

    private fun buildRelativePath(subDirectory: String): String {
        val sanitized = sanitizeSubdirectory(subDirectory)
        return if (sanitized == DEFAULT_SUBDIRECTORY) {
            "${Environment.DIRECTORY_PICTURES}/Screenshot3/"
        } else {
            "${Environment.DIRECTORY_PICTURES}/Screenshot3/$sanitized/"
        }
    }

    private fun buildLegacyPath(subDirectory: String): String {
        val sanitized = sanitizeSubdirectory(subDirectory)
        return if (sanitized == DEFAULT_SUBDIRECTORY) {
            "Screenshot3"
        } else {
            "Screenshot3/$sanitized"
        }
    }

    private fun sanitizeSubdirectory(input: String?): String {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_SUBDIRECTORY
        val cleaned = trimmed.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return cleaned.ifEmpty { DEFAULT_SUBDIRECTORY }
    }

    private fun ensureLegacyDirectoryExists(subDirectory: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        val directory = java.io.File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            buildLegacyPath(subDirectory)
        )
        if (!directory.exists()) {
            directory.mkdirs()
            Log.d(TAG, "SSM-legacyDir-created path=${directory.absolutePath}")
        }
    }

    fun normalizeDirectoryName(input: String): String = sanitizeSubdirectory(input)

    private fun setPendingCapture(bitmap: Bitmap, subDirectory: String, appSuffix: String?) {
        synchronized(this) {
            pendingCapture = PendingCaptureSnapshot(bitmap, subDirectory, appSuffix)
        }
    }

    class PendingCaptureSnapshot internal constructor(
        val bitmap: Bitmap,
        val subDirectory: String,
        val appSuffix: String?
    )

    private data class MediaItem(
        val name: String,
        val open: () -> InputStream?
    )

    private data class ZipDestination(
        val outputStream: java.io.OutputStream,
        val uri: Uri?,
        val path: String,
        val finalize: () -> Unit,
        val cleanup: () -> Unit
    )

    private fun createZipDestination(context: Context, filename: String): ZipDestination? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, ZIP_RELATIVE_PATH)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    val outputStream = resolver.openOutputStream(uri)
                    if (outputStream != null) {
                        ZipDestination(
                            outputStream = outputStream,
                            uri = uri,
                            path = "$ZIP_RELATIVE_PATH/$filename",
                            finalize = {
                                val pendingValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                                }
                                resolver.update(uri, pendingValues, null, null)
                            },
                            cleanup = { resolver.delete(uri, null, null) }
                        )
                    } else {
                        resolver.delete(uri, null, null)
                        null
                    }
                } else null
            } catch (t: Throwable) {
                Log.e(TAG, "SSM-zip-mediaStore-failed", t)
                null
            }
        } else {
            val baseDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                ZIP_LEGACY_FOLDER
            )
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                Log.e(TAG, "SSM-zip-createDir-failed path=${baseDir.absolutePath}")
                return null
            }
            val file = File(baseDir, filename)
            try {
                val outputStream = FileOutputStream(file)
                ZipDestination(
                    outputStream = outputStream,
                    uri = Uri.fromFile(file),
                    path = file.absolutePath,
                    finalize = { },
                    cleanup = { file.delete() }
                )
            } catch (t: Throwable) {
                Log.e(TAG, "SSM-zip-output-create-failed path=${file.absolutePath}", t)
                null
            }
        }
    }

    private fun queryCollectionItems(context: Context, key: String): List<MediaItem> {
        val resolver = context.contentResolver
        val items = mutableListOf<MediaItem>()
        Log.d(TAG, "SSM-query-items key=$key")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = buildRelativePath(key)
            val trimmedPath = relativePath.removeSuffix("/")
            val selection: String
            val selectionArgs: Array<String>
            if (trimmedPath.isNotEmpty()) {
                selection =
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                selectionArgs = arrayOf(relativePath, trimmedPath)
            } else {
                selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                selectionArgs = arrayOf(relativePath)
            }

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val displayName = cursor.getString(nameIndex) ?: "image_$id.png"
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    items.add(
                        MediaItem(displayName) {
                            resolver.openInputStream(uri)
                        }
                    )
                }
            }
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                buildLegacyPath(key)
            )
            val basePath = directory.absolutePath
            val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$basePath%")

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue
                    val displayName = cursor.getString(nameIndex) ?: file.name
                    items.add(
                        MediaItem(displayName) {
                            FileInputStream(file)
                        }
                    )
                }
            }
        }

        Log.d(TAG, "SSM-query-items key=$key count=${items.size}")
        return items
    }

    private fun buildZipFileName(username: String, label: String, count: Int): String {
        val userSegment = ScreenshotFilenameFormatter.sanitizeFileSegment(username).ifEmpty { "user" }
        val labelSegment = ScreenshotFilenameFormatter.sanitizeFileSegment(label).ifEmpty { "collection" }
        return "${userSegment}_${labelSegment}_${count}.zip"
    }

    private const val DEFAULT_SUBDIRECTORY = ""
    private const val DEFAULT_COLLECTION_LABEL = "Default"
    private const val ZIP_RELATIVE_PATH = "Download/ScreenshotCollections"
    private const val ZIP_LEGACY_FOLDER = "ScreenshotCollections"
    private const val TAG = "ScreenCaptureManager"
}
