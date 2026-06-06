package com.tools.screenshot3

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tools.screenshot3.capture.ScreenCaptureManager
import com.tools.screenshot3.capture.ScreenshotImageFormat
import com.tools.screenshot3.ui.theme.Screenshot3Theme
import java.util.Date

class AboutActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateLocale(newBase, LocaleHelper.getSavedLanguage(newBase)))
    }
    
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenCaptureManager.initializeSettings(applicationContext)
        enableEdgeToEdge()
        setContent {
            Screenshot3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AboutScreen(
                        onDismiss = { finish() },
                        context = LocalContext.current,
                        activity = this
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    onDismiss: () -> Unit,
    context: Context,
    activity: ComponentActivity
) {
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val requiresConfirmation by ScreenCaptureManager.requiresConfirmation.collectAsState()
    val imageFormat by ScreenCaptureManager.imageFormat.collectAsState()
    val stripScrollNavBar by ScreenCaptureManager.stripScrollNavBar.collectAsState()
    val currentLanguage = LocaleHelper.getSavedLanguage(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Centered title
        Text(
            text = stringResource(R.string.button_about),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // User Guide button
        Button(
            onClick = { context.startActivity(Intent(context, UserGuideActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_user_guide))
        }

        // Language selection button
        Button(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_language, LocaleHelper.getLanguageDisplayName(currentLanguage)))
        }

        // Ask before saving option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    ScreenCaptureManager.updateRequiresConfirmation(context, !requiresConfirmation)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = requiresConfirmation,
                onCheckedChange = { 
                    ScreenCaptureManager.updateRequiresConfirmation(context, it)
                }
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_require_confirmation_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.setting_require_confirmation_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.setting_image_format_title),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.setting_image_format_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ImageFormatOption(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.setting_image_format_jpeg),
                    selected = imageFormat == ScreenshotImageFormat.JPEG,
                    onClick = {
                        ScreenCaptureManager.updateImageFormat(context, ScreenshotImageFormat.JPEG)
                    }
                )
                ImageFormatOption(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.setting_image_format_png),
                    selected = imageFormat == ScreenshotImageFormat.PNG,
                    onClick = {
                        ScreenCaptureManager.updateImageFormat(context, ScreenshotImageFormat.PNG)
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    ScreenCaptureManager.updateStripScrollNavBar(context, !stripScrollNavBar)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = stripScrollNavBar,
                onCheckedChange = {
                    ScreenCaptureManager.updateStripScrollNavBar(context, it)
                }
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_strip_scroll_nav_bar_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.setting_strip_scroll_nav_bar_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Build information
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }

        val versionName = packageInfo?.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString() ?: "Unknown"
        } else {
            @Suppress("DEPRECATION")
            (packageInfo?.versionCode ?: 0).toString()
        }
        val buildType = if ((packageInfo?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "debug"
        } else {
            "release"
        }
        
        // Show the actual build timestamp generated at build time in the user's locale.
        val buildTime = try {
            val buildTimeMillis = context.getString(R.string.generated_build_time_millis).toLong()
            val buildDate = Date(buildTimeMillis)
            val dateText = DateFormat.getMediumDateFormat(context).format(buildDate)
            val timeText = DateFormat.getTimeFormat(context).format(buildDate)
            "$dateText $timeText"
        } catch (_: Exception) {
            "Unknown"
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.about_build_type, buildType),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.about_build_time, buildTime),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.about_version_code, versionCode),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.about_version_name, versionName),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.heightIn(min = 8.dp))

        // Feedback button
        Button(
            onClick = { showFeedbackDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(text = stringResource(R.string.button_feedback))
        }

        // Online User Guide button (opens the GitHub Pages docs guide)
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://hihanifm.github.io/ScreenshotX/docs/user-guide.html")
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_user_guide_online))
        }

        // Close button at bottom
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.setup_help_close))
        }
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Text(text = stringResource(R.string.feedback_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.feedback_dialog_message))
            },
            confirmButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text(text = stringResource(R.string.setup_help_close))
                }
            }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(text = stringResource(R.string.language_dialog_title))
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocaleHelper.getAvailableLanguages().forEach { language ->
                        val isSelected = language == currentLanguage
                        Button(
                            onClick = {
                                LocaleHelper.saveLanguage(context, language)
                                // Restart activity to apply new language
                                activity.recreate()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(text = LocaleHelper.getLanguageDisplayName(language))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(text = stringResource(R.string.setup_help_close))
                }
            }
        )
    }
}

@Composable
private fun ImageFormatOption(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
