package com.tools.screenshot3

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.tools.screenshot3.ui.theme.Screenshot3Theme

class AboutActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.updateLocale(newBase, LocaleHelper.getSavedLanguage(newBase)))
    }
    
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
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
    var showReleaseNotes by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showSetupHelp by remember { mutableStateOf(false) }
    var showCollectionHelp by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val requiresConfirmation by ScreenCaptureManager.requiresConfirmation.collectAsState()
    val currentLanguage = LocaleHelper.getSavedLanguage(context)
    val releaseNotesText = remember {
        try {
            context.assets.open("release_notes.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Unable to load release notes."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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

        // How To Setup button
        Button(
            onClick = { showSetupHelp = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_how_to_setup))
        }

        // How To Collect Screenshots button
        Button(
            onClick = { showCollectionHelp = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_how_to_collect))
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
        
        // Parse build time from version code (MMddyyHHmm format)
        val buildTime = try {
            if (versionCode != "Unknown" && versionCode.length == 10) {
                val month = versionCode.substring(0, 2)
                val day = versionCode.substring(2, 4)
                val year = "20" + versionCode.substring(4, 6)
                val hour = versionCode.substring(6, 8)
                val minute = versionCode.substring(8, 10)
                "$month/$day/$year $hour:$minute"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
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

        // Spacer to push buttons to bottom
        Spacer(modifier = Modifier.weight(1f))

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

        // ReleaseNotes button
        Button(
            onClick = { showReleaseNotes = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.button_release_notes))
        }

        // Close button at bottom
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.setup_help_close))
        }
    }

    // Release Notes Dialog
    if (showReleaseNotes) {
        Dialog(onDismissRequest = { showReleaseNotes = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.button_release_notes),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = releaseNotesText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = { showReleaseNotes = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.setup_help_close))
                    }
                }
            }
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

    // Setup Help Dialog
    if (showSetupHelp) {
        Dialog(onDismissRequest = { showSetupHelp = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.setup_help_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val horizontalScrollState = rememberScrollState()
                    val setupFlowResId = remember {
                        context.resources.getIdentifier("screenshot_setup_flow", "drawable", context.packageName)
                    }
                    if (setupFlowResId != 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Image(
                                painter = painterResource(setupFlowResId),
                                contentDescription = stringResource(R.string.setup_flow_content_description),
                                modifier = Modifier.fillMaxHeight(),
                                contentScale = ContentScale.FillHeight
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.setup_help_missing_art),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showSetupHelp = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(R.string.setup_help_close))
                    }
                }
            }
        }
    }

    // Collection Help Dialog
    if (showCollectionHelp) {
        Dialog(onDismissRequest = { showCollectionHelp = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.collection_help_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val horizontalScrollState = rememberScrollState()
                    val collectionFlowResId = remember {
                        context.resources.getIdentifier("screenshot_collection_flow", "drawable", context.packageName)
                    }
                    if (collectionFlowResId != 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Image(
                                painter = painterResource(collectionFlowResId),
                                contentDescription = stringResource(R.string.collection_flow_content_description),
                                modifier = Modifier.fillMaxHeight(),
                                contentScale = ContentScale.FillHeight
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.collection_help_missing_art),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showCollectionHelp = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(R.string.setup_help_close))
                    }
                }
            }
        }
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
