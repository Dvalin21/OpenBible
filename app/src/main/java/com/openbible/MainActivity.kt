package com.openbible

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.openbible.navigation.OpenBibleNavGraph
import com.openbible.ui.theme.LocalRetroPixel
import com.openbible.ui.theme.OpenBibleTheme
import com.openbible.ui.theme.rememberRetroPixelConfig
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for OpenBible.
 * All navigation is handled within Compose — no fragment transactions.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ponytail: observable state for notification-tap navigation; survives activity reuse
    private val pendingNav = mutableStateOf<Triple<String, Int, Int>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val app = application as OpenBibleApp
        val preferences = app.userPreferences

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = com.openbible.data.model.ThemeMode.LIGHT)
            val defaultTranslation by preferences.defaultTranslation.collectAsState(initial = "kjv")
            val retroConfig = rememberRetroPixelConfig()

            CompositionLocalProvider(LocalRetroPixel provides retroConfig) {
                OpenBibleTheme(themeMode = themeMode) {
                    OpenBibleNavGraph(
                        isTablet = retroConfig.isTablet,
                        defaultTranslation = defaultTranslation,
                        initialTranslationId = pendingNav.value?.first,
                        initialBookId = pendingNav.value?.second,
                        initialChapter = pendingNav.value?.third,
                        onNotificationConsumed = { pendingNav.value = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val translationId = intent?.getStringExtra("translationId")
        val bookId = intent?.getIntExtra("bookId", -1)?.takeIf { it > 0 }
        val chapter = intent?.getIntExtra("chapter", -1)?.takeIf { it > 0 }
        if (translationId != null && bookId != null && chapter != null) {
            pendingNav.value = Triple(translationId, bookId, chapter)
        }
    }
}
