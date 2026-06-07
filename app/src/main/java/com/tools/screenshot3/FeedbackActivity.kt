package com.tools.screenshot3

import android.content.Context
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tools.screenshot3.ui.theme.Screenshot3Theme

private const val FEEDBACK_ASSET = "feedback.html"

class FeedbackActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.updateLocale(newBase, LocaleHelper.getSavedLanguage(newBase))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Screenshot3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FeedbackScreen(onClose = { finish() })
                }
            }
        }
    }
}

@Composable
private fun FeedbackScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val hasFeedbackAsset = remember { feedbackAssetExists(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.feedback_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (hasFeedbackAsset) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = false
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.domStorageEnabled = false
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        overScrollMode = WebView.OVER_SCROLL_NEVER
                        loadUrl("file:///android_asset/$FEEDBACK_ASSET")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = stringResource(R.string.feedback_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.setup_help_close))
        }
    }
}

private fun feedbackAssetExists(context: Context): Boolean =
    try {
        context.assets.open(FEEDBACK_ASSET).close()
        true
    } catch (_: Exception) {
        false
    }
