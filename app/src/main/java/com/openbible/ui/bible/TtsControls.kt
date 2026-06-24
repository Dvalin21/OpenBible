package com.openbible.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openbible.tts.TtsState

/** Cycle of speech rates. */
private val SPEED_CYCLE = listOf(1.0f, 1.5f, 2.0f, 0.5f)

/**
 * Bottom control bar for TTS playback.
 *
 * Displays play/pause, skip prev/next, stop, and a speed toggle.
 * Only visible when [TtsState.isPlaying] or [TtsState.currentVerseIndex] >= 0.
 */
@Composable
fun TtsControls(
    state: TtsState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed toggle
            TextButton(
                onClick = {
                    val next = SPEED_CYCLE[
                        (SPEED_CYCLE.indexOf(state.speed) + 1) % SPEED_CYCLE.size
                    ]
                    onSpeedChange(next)
                }
            ) {
                Text(
                    text = "${state.speed}x",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp
                )
            }

            // Skip previous
            IconButton(onClick = onSkipPrev) {
                Icon(Icons.Filled.FastRewind, contentDescription = "Previous verse")
            }

            // Play / Pause
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause
                                  else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Skip next
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Filled.FastForward, contentDescription = "Next verse")
            }

            // Stop
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop")
            }
        }
    }
}
