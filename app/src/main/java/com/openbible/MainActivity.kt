package com.openbible

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.openbible.navigation.OpenBibleNavGraph
import com.openbible.ui.theme.OpenBibleTheme
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
            // ponytail: hold the splash until the DB copy + migrations finish
            // on IO (first render otherwise runs them on main → ANR)
            val dbReady by app.databaseReady.collectAsState()
            val isTablet = LocalConfiguration.current.screenWidthDp >= 600

            OpenBibleTheme(themeMode = themeMode) {
                if (!dbReady) {
                    StartupSplash()
                } else {
                    OpenBibleNavGraph(
                        isTablet = isTablet,
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

/** First-launch gate while the 200MB prepop DB copies + migrates on IO. */
@Composable
private fun StartupSplash() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "OpenBible",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Preparing your library…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}
