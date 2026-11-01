package com.cooklink.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cooklink.ai.data.settings.ThemeMode
import com.cooklink.ai.ui.CookLinkRoot
import com.cooklink.ai.ui.theme.CookLinkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Text/links shared into the app from other apps; consumed by the Import screen. */
    private val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val container = (application as CookLinkApp).container
        setContent {
            val mode by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CookLinkTheme(darkTheme = dark) {
                CookLinkRoot(
                    container = container,
                    sharedText = sharedText,
                    onSharedTextConsumed = { sharedText.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it }
        }
    }
}
