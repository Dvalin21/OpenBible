package com.openbible.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.jvm.Volatile
import java.util.Locale

/**
 * Wraps the Android [TextToSpeech] engine to read Bible verses aloud.
 *
 * Exposes [state] as a [StateFlow] for Compose observation.
 * Call [init] before first use and [shutdown] when done.
 *
 * Verse text is queued sequentially using utterance IDs of the form
 * `"verse_<index>"` so [UtteranceProgressListener.onStart] can track
 * which verse is currently being spoken.
 */
class TtsController(private val appContext: Context) {

    // ── Data ───────────────────────────────────────────────────────
    private var tts: TextToSpeech? = null
    private var verses: List<String> = emptyList()
    private var resumeIndex: Int = 0
    // ponytail: generation counter instead of single counter.
    // Each speak()/stop()/togglePlayPause() increments the generation;
    // onDone only advances if its generation matches the current one.
    // This handles the race where engine.stop() fires N async callbacks
    // after we've already queued new utterances.
    private var generation: Int = 0

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    // ── Lifecycle ──────────────────────────────────────────────────

    /** Initialize the TTS engine. Call once before [speak]. */
    fun init() {
        if (tts != null) return // already initialized

        tts = TextToSpeech(appContext) { status ->
            val s = _state.value
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(s.speed)
                tts?.setOnUtteranceProgressListener(utteranceListener)
                _state.value = s.copy(isInitialized = true, isAvailable = true)
                Log.i(TAG, "TTS engine initialized")
            } else {
                _state.value = s.copy(isInitialized = false, isAvailable = false)
                Log.w(TAG, "TTS engine init failed: status=$status")
            }
        }
    }

    /** Release all TTS resources. Call when the screen is disposed. */
    fun shutdown() {
        generation++
        tts?.stop()
        tts?.shutdown()
        tts = null
        _state.value = TtsState()
        verses = emptyList()
        resumeIndex = 0
        Log.i(TAG, "TTS engine shut down")
    }

    // ── Playback ───────────────────────────────────────────────────

    /**
     * Speak [verseTexts] starting at [startIndex].
     * Any current playback is stopped first.
     */
    fun speak(verseTexts: List<String>, startIndex: Int = 0) {
        val engine = tts ?: return
        if (!_state.value.isInitialized) return
        if (verseTexts.isEmpty()) return

        generation++  // mark any in-flight onDone as stale
        val myGen = generation

        verses = verseTexts
        resumeIndex = startIndex.coerceIn(0, verseTexts.size - 1)

        engine.stop()                            // cancel any prior queue
        engine.setSpeechRate(_state.value.speed)

        var queued = 0
        for (i in resumeIndex until verseTexts.size) {
            val id = "verse_${i}_gen$myGen"
            if (engine.speak(verseTexts[i], TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS) {
                queued++
            } else {
                Log.w(TAG, "Failed to queue verse $i")
            }
        }

        if (queued > 0) {
            _state.value = _state.value.copy(
                isPlaying = true,
                currentVerseIndex = resumeIndex,
                generation = myGen
            )
        }
    }

    /** Toggle between play and pause. */
    fun togglePlayPause() {
        val s = _state.value
        if (s.isPlaying) {
            // Pause — stop the engine, remember where we were
            generation++
            tts?.stop()
            _state.value = s.copy(isPlaying = false, currentWordRange = null)
        } else {
            // Resume from current position
            if (verses.isNotEmpty() && resumeIndex < verses.size) {
                speak(verses, resumeIndex)
            }
        }
    }

    /** Skip to the next verse. */
    fun skipNext() {
        val next = resumeIndex + 1
        if (next < verses.size) {
            speak(verses, next)
        }
    }

    /** Go back one verse. */
    fun skipPrev() {
        val prev = (resumeIndex - 1).coerceAtLeast(0)
        speak(verses, prev)
    }

    /** Stop playback and reset state. */
    fun stop() {
        generation++
        tts?.stop()
        resumeIndex = 0
        _state.value = _state.value.copy(isPlaying = false, currentVerseIndex = -1, currentWordRange = null)
        verses = emptyList()
    }

    /** Set speech rate (0.5–2.0). Restarts current playback if active. */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        val s = _state.value
        _state.value = s.copy(speed = clamped)
        tts?.setSpeechRate(clamped)
        // If currently playing, re-queue from current position with new speed
        if (s.isPlaying && verses.isNotEmpty() && resumeIndex < verses.size) {
            speak(verses, resumeIndex)
        }
    }

    // ── Utterance tracking ─────────────────────────────────────────

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (idx, gen) = utteranceId?.parseUtteranceId() ?: return
            if (gen != generation) return  // stale utterance from prior speak()
            resumeIndex = idx
            _state.value = _state.value.copy(
                currentVerseIndex = idx,
                currentWordRange = null  // reset word range on new verse
            )
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            // Word-level sync: called when a new word begins.
            // 'start' and 'end' are character offsets into the spoken text.
            val (_, gen) = utteranceId?.parseUtteranceId() ?: return
            if (gen != generation) return  // stale utterance
            _state.value = _state.value.copy(currentWordRange = start until end)
        }

        override fun onDone(utteranceId: String?) {
            val (idx, gen) = utteranceId?.parseUtteranceId() ?: return
            // ponytail: skip stale callbacks from prior generation
            if (gen != generation) return
            resumeIndex = idx + 1
            if (idx >= verses.size - 1) {
                // Last verse finished
                _state.value = _state.value.copy(isPlaying = false, currentVerseIndex = -1, currentWordRange = null)
                resumeIndex = 0
                Log.i(TAG, "TTS finished all verses")
            } else {
                // Between verses: clear word highlight
                _state.value = _state.value.copy(currentWordRange = null)
            }
        }

        override fun onError(utteranceId: String?) {
            Log.e(TAG, "TTS error on utterance: $utteranceId")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    /** Parse "verse_{index}_gen{generation}" → Pair(index, generation). */
    private fun String?.parseUtteranceId(): Pair<Int, Int>? {
        val s = this ?: return null
        val parts = s.removePrefix("verse_").split("_gen")
        if (parts.size != 2) return null
        val idx = parts[0].toIntOrNull() ?: return null
        val gen = parts[1].toIntOrNull() ?: return null
        return Pair(idx, gen)
    }

    companion object {
        private const val TAG = "TtsController"
    }
}

/** Reflects the current state of the TTS engine. */
data class TtsState(
    val isPlaying: Boolean = false,
    val isInitialized: Boolean = false,
    val currentVerseIndex: Int = -1,
    /** Character range [start, end) of the word currently being spoken within
     *  the verse text of [currentVerseIndex]. null when no word is active
     *  (between verses or before first word). */
    val currentWordRange: IntRange? = null,
    val speed: Float = 1.0f,
    val isAvailable: Boolean = true,
    /** Monotonic generation counter incremented on each speak/stop/pause.
     *  onDone callbacks with a stale generation are ignored. */
    val generation: Int = 0
)
