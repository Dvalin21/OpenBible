package com.openbible

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.openbible.navigation.OpenBibleNavGraph
import com.openbible.ui.theme.OpenBibleTheme
import com.openbible.ui.theme.rememberRetroPixelConfig
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for OpenBible.
 * All navigation is handled within Compose — no fragment transactions.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OpenBibleApp
        val preferences = app.userPreferences

        // Handle notification tap — read extras from the launching intent
        val notificationTranslationId = intent?.getStringExtra("translationId")
        val notificationBookId = intent?.getIntExtra("bookId", -1)?.takeIf { it > 0 }
        val notificationChapter = intent?.getIntExtra("chapter", -1)?.takeIf { it > 0 }

        setContent {
            val themeMode by preferences.themeMode.collectAsState(initial = com.openbible.data.model.ThemeMode.LIGHT)
            val retroConfig = rememberRetroPixelConfig()

            OpenBibleTheme(themeMode = themeMode) {
                OpenBibleNavGraph(
                    isTablet = retroConfig.isTablet,
                    initialTranslationId = notificationTranslationId,
                    initialBookId = notificationBookId,
                    initialChapter = notificationChapter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
