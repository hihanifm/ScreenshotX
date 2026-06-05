package com.tools.screenshot3.capture

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

object ForegroundAppResolver {

    private const val LOOKBACK_WINDOW_MS = 10_000L
    private const val TAG = "ForegroundAppResolver"
    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui"
    )

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun createUsageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun resolveForegroundAppSuffix(context: Context): String? {
        if (!hasUsageAccess(context)) return null

        val packageName = resolveForegroundPackageName(context) ?: return null
        if (packageName == context.packageName || packageName in ignoredPackages) return null

        val label = resolveApplicationLabel(context, packageName) ?: return null
        val suffix = ScreenshotFilenameFormatter.sanitizeFileSegment(label).ifEmpty { null }
        Log.d(TAG, "SSM-foreground-app package=$packageName label=$label suffix=$suffix")
        return suffix
    }

    private fun resolveForegroundPackageName(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - LOOKBACK_WINDOW_MS

        queryRecentForegroundPackage(usageStatsManager, context.packageName, startTime, endTime)?.let {
            return it
        }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ).orEmpty()

        val fallbackPackage = stats
            .asSequence()
            .filter { stat ->
                stat.packageName != context.packageName &&
                    stat.packageName !in ignoredPackages &&
                    stat.lastTimeUsed > 0L
            }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName

        Log.d(TAG, "SSM-foreground-package-fallback package=$fallbackPackage")
        return fallbackPackage
    }

    private fun queryRecentForegroundPackage(
        usageStatsManager: UsageStatsManager,
        ownPackageName: String,
        startTime: Long,
        endTime: Long
    ): String? {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!isForegroundEvent(event.eventType)) continue

            val packageName = event.packageName ?: continue
            if (packageName == ownPackageName || packageName in ignoredPackages) continue
            latestPackage = packageName
        }

        Log.d(TAG, "SSM-foreground-package-events package=$latestPackage")
        return latestPackage
    }

    private fun isForegroundEvent(eventType: Int): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            else -> eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun resolveApplicationLabel(context: Context, packageName: String): String? {
        return try {
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (error: PackageManager.NameNotFoundException) {
            Log.w(TAG, "SSM-foreground-label-missing package=$packageName", error)
            null
        }
    }
}
