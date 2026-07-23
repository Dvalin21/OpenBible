package com.openbible.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openbible.data.model.ThemeMode
import kotlin.math.roundToInt

/**
 * Settings screen — real preference controls backed by DataStore.
 *
 * Sections:
 * 1. Display — theme mode, font sizes, line spacing
 * 2. Bible Reading — page flip, default translation
 * 3. Notifications — daily verse toggle and time
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontSizeNumbers by viewModel.fontSizeVerseNumbers.collectAsState()
    val fontSizeText by viewModel.fontSizeVerseText.collectAsState()
    val lineSpacing by viewModel.lineSpacing.collectAsState()
    val pageFlipAnim by viewModel.pageFlipAnimation.collectAsState()
    val pageFlipSound by viewModel.pageFlipSound.collectAsState()
    val dailyVerseEnabled by viewModel.dailyVerseEnabled.collectAsState()
    val dailyVerseTime by viewModel.dailyVerseTime.collectAsState()
    val defaultTranslation by viewModel.defaultTranslation.collectAsState()
    val translations by viewModel.translations.collectAsState()

    var showTranslationPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ───────────────────────────────────────────────
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        Text(
            text = "Customize your reading experience",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        )

        // ════════════════════════════════════════════════════════════
        // DISPLAY SECTION
        // ════════════════════════════════════════════════════════════
        SettingsSectionHeader("Display")

        // Theme mode selector
        SettingsLabel("Theme")
        ThemeModeSelector(
            current = themeMode,
            onSelect = { viewModel.setThemeMode(it) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Font size: verse numbers
        SettingsLabel("Verse Number Size (${fontSizeNumbers.roundToInt()}sp)")
        Slider(
            value = fontSizeNumbers,
            onValueChange = { viewModel.setFontSizeVerseNumbers(it) },
            valueRange = 10f..28f,
            steps = 17,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Font size: verse text
        SettingsLabel("Verse Text Size (${fontSizeText.roundToInt()}sp)")
        Slider(
            value = fontSizeText,
            onValueChange = { viewModel.setFontSizeVerseText(it) },
            valueRange = 12f..32f,
            steps = 19,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Line spacing
        SettingsLabel("Line Spacing (${"%.1f".format(lineSpacing)}x)")
        Slider(
            value = lineSpacing,
            onValueChange = { viewModel.setLineSpacing(it) },
            valueRange = 1.0f..2.5f,
            steps = 14,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // ════════════════════════════════════════════════════════════
        // BIBLE READING SECTION
        // ════════════════════════════════════════════════════════════
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        SettingsSectionHeader("Bible Reading")

        // Default translation
        SettingsLabel("Default Translation")
        OutlinedCard(
            onClick = { showTranslationPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            val displayName = translations
                .firstOrNull { it.id == defaultTranslation }
                ?.let { "${it.abbreviation} — ${it.name}" }
                ?: defaultTranslation.uppercase()

            Text(
                text = displayName,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Page flip animation toggle
        SettingsSwitch(
            label = "Page Flip Animation",
            subtitle = "2D slide with page-edge shadow effect",
            checked = pageFlipAnim,
            onCheckedChange = { viewModel.setPageFlipAnimation(it) }
        )

        // Page flip sound toggle
        SettingsSwitch(
            label = "Page Flip Sound",
            subtitle = "Play a paper rustle sound when turning pages",
            checked = pageFlipSound,
            onCheckedChange = { viewModel.setPageFlipSound(it) }
        )

        // ════════════════════════════════════════════════════════════
        // NOTIFICATIONS SECTION
        // ════════════════════════════════════════════════════════════
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        SettingsSectionHeader("Notifications")

        // Daily verse toggle
        SettingsSwitch(
            label = "Daily Verse Notification",
            subtitle = "Receive a daily notification with a verse of the day",
            checked = dailyVerseEnabled,
            onCheckedChange = { viewModel.setDailyVerseEnabled(it) }
        )
        if (dailyVerseEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingsLabel("Notification Time")
            TimeSelector(
                hour = dailyVerseTime.first,
                minute = dailyVerseTime.second,
                onSelect = { h, m -> viewModel.setDailyVerseTime(h, m) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Translation picker dialog
    if (showTranslationPicker) {
        AlertDialog(
            onDismissRequest = { showTranslationPicker = false },
            title = { Text("Default Translation") },
            text = {
                Column {
                    translations.forEach { t ->
                        val isSelected = t.id == defaultTranslation
                        TextButton(
                            onClick = {
                                viewModel.setDefaultTranslation(t.id)
                                showTranslationPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${t.abbreviation} — ${t.name}",
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold
                                            else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTranslationPicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}

// ── Reusable Subcomponents ─────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeModeSelector(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onSelect(mode) },
                label = {
                    Text(
                        text = when (mode) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SEPIA -> "Sepia"
                            ThemeMode.AUTO_TIME -> "Auto"
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeSelector(
    hour: Int,
    minute: Int,
    onSelect: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val timeText = "%02d:%02d".format(hour, minute)

    OutlinedCard(onClick = { showPicker = true }, modifier = modifier.fillMaxWidth()) {
        Text(
            text = timeText,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }

    if (showPicker) {
        var tempHour by remember(hour) { mutableStateOf(hour) }
        var tempMinute by remember(minute) { mutableStateOf(minute) }

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Notification Time") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", style = MaterialTheme.typography.labelMedium)
                        // Simple hour picker
                        TextButton(onClick = { tempHour = (tempHour + 1) % 24 }) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                        Text(
                            text = "%02d".format(tempHour),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        TextButton(onClick = { tempHour = if (tempHour == 0) 23 else tempHour - 1 }) {
                            Text("-", style = MaterialTheme.typography.headlineMedium)
                        }
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { tempMinute = (tempMinute + 5) % 60 }) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                        Text(
                            text = "%02d".format(tempMinute),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        TextButton(onClick = { tempMinute = if (tempMinute < 5) 55 else tempMinute - 5 }) {
                            Text("-", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelect(tempHour, tempMinute)
                    showPicker = false
                }) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

