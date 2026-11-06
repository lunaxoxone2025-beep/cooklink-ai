package com.cooklink.ai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cooklink.ai.data.settings.NeonAccent
import com.cooklink.ai.ui.CookLinkRoot
import com.cooklink.ai.ui.theme.CookLinkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Text/links shared into the app from other apps; consumed by the Import screen. */
    private val sharedText = MutableStateFlow<String?>(null)

    /** Photos/videos shared into the app from the gallery or other apps. */
    private val sharedMedia = MutableStateFlow<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The neon interface is always dark, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        val container = (application as CookLinkApp).container
        setContent {
            val accent by container.settings.accent.collectAsState(initial = NeonAccent.LIME)
            CookLinkTheme(accent = accent) {
                CookLinkRoot(
                    container = container,
                    sharedText = sharedText,
                    onSharedTextConsumed = { sharedText.value = null },
                    sharedMedia = sharedMedia,
                    onSharedMediaConsumed = { sharedMedia.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val type = intent.type.orEmpty()
        when (intent.action) {
            Intent.ACTION_SEND -> when {
                type.startsWith("text/") ->
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it }
                type.startsWith("image/") || type.startsWith("video/") ->
                    streamExtra(intent)?.let { sharedMedia.value = listOf(it) }
            }
            Intent.ACTION_SEND_MULTIPLE ->
                streamListExtra(intent)?.takeIf { it.isNotEmpty() }?.let { sharedMedia.value = it }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListExtra(intent: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
}
