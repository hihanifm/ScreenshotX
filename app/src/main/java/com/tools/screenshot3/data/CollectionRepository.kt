package com.tools.screenshot3.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import com.tools.screenshot3.R
import com.tools.screenshot3.capture.ScreenCaptureManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object CollectionRepository {

    private const val PREF_NAME = "collection_prefs"
    private const val KEY_CUSTOM_COLLECTIONS = "custom_collection_entries"
    private const val KEY_LAST_USERNAME = "last_zip_username"
    private const val KEY_CSV_LOADED_VERSION = "csv_loaded_version"
    private const val ENTRY_DELIMITER = "||"
    private const val CSV_FILENAME = "collections.csv"

    data class CustomCollection(val key: String, val label: String, val assignee: String = "")

    sealed class AddResult {
        data class Success(val collections: List<CustomCollection>) : AddResult()
        data class Error(@StringRes val messageRes: Int) : AddResult()
    }

    fun load(context: Context): List<CustomCollection> {
        val stored = prefs(context).getStringSet(KEY_CUSTOM_COLLECTIONS, emptySet()) ?: emptySet()
        return stored.mapNotNull { decodeEntry(it) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun add(
        context: Context,
        rawLabel: String,
        reservedKeys: Set<String>
    ): AddResult {
        val trimmed = rawLabel.trim()
        if (trimmed.isEmpty()) {
            return AddResult.Error(R.string.error_collection_empty)
        }

        val sanitizedKey = ScreenCaptureManager.normalizeDirectoryName(trimmed)
        if (sanitizedKey.isEmpty() || reservedKeys.contains(sanitizedKey)) {
            return AddResult.Error(R.string.error_collection_duplicate)
        }

        val current = load(context).toMutableList()
        if (current.any { it.key == sanitizedKey }) {
            return AddResult.Error(R.string.error_collection_duplicate)
        }

        val normalizedLabel = trimmed.replace(ENTRY_DELIMITER, " ")
        current.add(CustomCollection(sanitizedKey, normalizedLabel))
        val updated = current.sortedBy { it.label.lowercase(Locale.getDefault()) }
        prefs(context).edit()
            .putStringSet(KEY_CUSTOM_COLLECTIONS, updated.map { encodeEntry(it) }.toSet())
            .apply()

        return AddResult.Success(updated)
    }

    fun loadCsvIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val currentVersion = try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
            else @Suppress("DEPRECATION") info.versionCode
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
        val loadedVersion = prefs(appContext).getInt(KEY_CSV_LOADED_VERSION, -1)
        if (loadedVersion == currentVersion) return
        loadFromCsv(appContext)
        prefs(appContext).edit()
            .putInt(KEY_CSV_LOADED_VERSION, currentVersion)
            .apply()
    }

    private fun loadFromCsv(context: Context) {
        val collections = mutableListOf<CustomCollection>()
        val seenKeys = mutableSetOf<String>()
        try {
            context.assets.open(CSV_FILENAME).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val headerLine = reader.readLine() ?: return
                    val headers = headerLine.split(",").map { it.trim().lowercase() }
                    val titleIdx = headers.indexOf("title")
                    val assigneeIdx = headers.indexOf("assignee")
                    if (titleIdx < 0) return

                    reader.forEachLine { line ->
                        val fields = line.split(",", limit = headers.size)
                        val title = fields.getOrNull(titleIdx)?.trim() ?: return@forEachLine
                        if (title.isEmpty()) return@forEachLine
                        val assignee = if (assigneeIdx >= 0) fields.getOrNull(assigneeIdx)?.trim().orEmpty() else ""
                        val key = ScreenCaptureManager.normalizeDirectoryName(title)
                        if (key.isNotEmpty() && seenKeys.add(key)) {
                            collections.add(CustomCollection(key, title, assignee))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return
        }
        val encoded = collections.map { encodeEntry(it) }.toSet()
        prefs(context).edit()
            .putStringSet(KEY_CUSTOM_COLLECTIONS, encoded)
            .apply()
    }

    fun getLastZipUsername(context: Context): String? =
        prefs(context).getString(KEY_LAST_USERNAME, null)

    fun saveLastZipUsername(context: Context, username: String) {
        prefs(context).edit()
            .putString(KEY_LAST_USERNAME, username)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun encodeEntry(entry: CustomCollection): String =
        "${entry.key}$ENTRY_DELIMITER${entry.label}$ENTRY_DELIMITER${entry.assignee}"

    private fun decodeEntry(value: String): CustomCollection? {
        val parts = value.split(ENTRY_DELIMITER, limit = 3)
        if (parts.size < 2) return null
        val key = parts[0]
        val label = parts[1]
        if (key.isEmpty()) return null
        val assignee = if (parts.size >= 3) parts[2] else ""
        return CustomCollection(key, label, assignee)
    }
}


