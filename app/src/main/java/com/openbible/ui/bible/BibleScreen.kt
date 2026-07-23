package com.openbible.ui.bible

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openbible.OpenBibleApp
import com.openbible.R
import com.openbible.data.db.dao.CrossReferenceDisplay
import com.openbible.data.db.entity.BookEntity
import com.openbible.data.db.entity.VerseEntity
import com.openbible.tts.TtsController
import com.openbible.tts.TtsState
import com.openbible.ui.theme.*
import com.openbible.data.model.HighlightColor
import com.openbible.data.model.RedLetterData
import com.openbible.ui.strongs.StrongVerseBottomSheet
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Bible reading screen.
 *
 * On devices 7" and larger, the retro pixel Bible theme is applied
 * with parchment background, pixel font for verse numbers, and
 * ornamental borders.
 *
 * On smaller devices, the standard Material 3 theme is used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleScreen(
    initialTranslationId: String? = null,
    initialBookId: Int? = null,
    initialChapter: Int? = null,
    onNavigateToChapter: (translationId: String, bookId: Int, chapter: Int) -> Unit,
    isTablet: Boolean,
    onAddNote: (verseNumber: Int) -> Unit = {},
    onStudyMode: (translationId: String, bookId: Int, chapter: Int) -> Unit = { _, _, _ -> },
    onOpenStrongDetail: (strongNumber: String) -> Unit = {},
    viewModel: BibleViewModel = viewModel()
) {
    val verses by viewModel.verses.collectAsState()
    val currentBook by viewModel.currentBook.collectAsState()
    val selectedChapter by viewModel.selectedChapter.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val selectedTranslationId by viewModel.selectedTranslationId.collectAsState()
    val selectedBookId by viewModel.selectedBookId.collectAsState()
    val books by viewModel.books.collectAsState()

    // ── Bookmarks, highlights, cross-refs for current chapter ────
    val bookmarkedIds by viewModel.bookmarkedVerseIds.collectAsState()
    val highlightMap by viewModel.highlightMap.collectAsState()
    val crossReferenceMap by viewModel.crossReferenceMap.collectAsState()

    // ── Split-pane compare mode ──────────────────────────────────
    val secondaryTranslationId by viewModel.selectedSecondaryTranslationId.collectAsState()
    val secondaryVerses by viewModel.secondaryVerses.collectAsState()
    val secondaryTranslation by viewModel.secondaryTranslation.collectAsState()
    val isCompareMode = isTablet && secondaryTranslationId != null

    // ── Strong's Concordance ──────────────────────────────────────
    var strongVerseId by remember { mutableStateOf<Long?>(null) }
    val onStrongsClick: (Long) -> Unit = { verseId -> strongVerseId = verseId }

    // ── Read user preferences for font sizes & toggles ──────────
    val context = LocalContext.current
    val app = context.applicationContext as OpenBibleApp
    val prefs = app.userPreferences

    val fontSizeVerseNumbers by prefs.fontSizeVerseNumbers.collectAsState(initial = 18f)
    val fontSizeVerseText by prefs.fontSizeVerseText.collectAsState(initial = 16f)
    val lineSpacing by prefs.lineSpacing.collectAsState(initial = 1.6f)
    val pageFlipAnimEnabled by prefs.pageFlipAnimation.collectAsState(initial = true)
    val pageFlipSoundEnabled by prefs.pageFlipSound.collectAsState(initial = true)

    // Canonical book number (1=Gen…40=Mat…66=Rev) for red-letter support
    val bookNumber = currentBook?.number ?: 1

    // ── Page flip sound (SoundPool) ────────────────────────────────
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    }
    val pageFlipSoundId = remember {
        soundPool.load(context, R.raw.page_flip, 1)
    }

    // ── Haptic feedback for chapter changes ────────────────────────
    val haptic = LocalHapticFeedback.current

    // ── Swipe navigation state ─────────────────────────────────────
    val swipeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isSwiping by remember { mutableStateOf(false) }

    // ── Text-to-Speech ────────────────────────────────────────────
    val ttsController = remember { TtsController(context.applicationContext) }
    val ttsState by ttsController.state.collectAsState()

    // Initialise engine once
    LaunchedEffect(Unit) { ttsController.init() }

    // Release engines when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            ttsController.shutdown()
            soundPool.release()
        }
    }

    // Shared LazyListState for TTS auto-scroll
    val primaryListState = rememberLazyListState()

    // Auto-scroll to the currently spoken verse
    LaunchedEffect(ttsState.currentVerseIndex) {
        if (ttsState.isPlaying && ttsState.currentVerseIndex >= 0) {
            primaryListState.animateScrollToItem(ttsState.currentVerseIndex)
        }
    }

    // Apply initial navigation if provided
    LaunchedEffect(initialTranslationId, initialBookId, initialChapter) {
        if (initialTranslationId != null) viewModel.selectTranslation(initialTranslationId)
        if (initialBookId != null) viewModel.selectBook(initialBookId)
        if (initialChapter != null) viewModel.selectChapter(initialChapter)
    }

    // Record reading + play page flip sound on chapter change
    var prevChapter by remember { mutableStateOf(selectedChapter) }
    LaunchedEffect(selectedChapter) {
        viewModel.recordReading()
        if (prevChapter != selectedChapter) {
            if (pageFlipSoundEnabled && pageFlipSoundId != 0) {
                soundPool.play(pageFlipSoundId, 0.6f, 0.6f, 1, 0, 1f)
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            prevChapter = selectedChapter
        }
    }

    // Book/chapter selector + translation picker states
    var showSelector by remember { mutableStateOf(false) }
    var showTranslationPicker by remember { mutableStateOf(false) }
    var showComparePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentBook?.name ?: "Bible",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Chapter $selectedChapter — ${selectedTranslationId.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    // Translation picker
                    IconButton(onClick = { showTranslationPicker = true }) {
                        Text(
                            text = selectedTranslationId.uppercase(),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    // Chapter/Book selector
                    IconButton(onClick = { showSelector = true }) {
                        Text(
                            text = "$selectedChapter",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    // Read Aloud (TTS)
                    IconButton(
                        onClick = {
                            val verseTexts = verses.map { it.text }
                            if (verseTexts.isNotEmpty() && ttsState.isAvailable) {
                                ttsController.speak(verseTexts)
                            }
                        },
                        enabled = ttsState.isAvailable && verses.isNotEmpty()
                    ) {
                        Text(
                            text = if (ttsState.isPlaying) "\u266B" else "\u266A",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ttsState.isPlaying) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Split-pane compare (tablets only)
                    if (isTablet) {
                        IconButton(
                            onClick = {
                                if (isCompareMode) {
                                    viewModel.closeSecondaryPane()
                                } else {
                                    showComparePicker = true
                                }
                            }
                        ) {
                            Text(
                                text = if (isCompareMode) "✕" else "⇔",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isCompareMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Study Mode: Bible + notes side-by-side (tablets show split; phones toggle)
                    IconButton(
                        onClick = {
                            onStudyMode(selectedTranslationId, selectedBookId, selectedChapter)
                        }
                    ) {
                        Icon(
                            Icons.Filled.EditNote,
                            contentDescription = "Study mode (Bible + notes)"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Chapter navigation
            ChapterNavigationBar(
                currentChapter = selectedChapter,
                chapters = chapters,
                onPrevious = { viewModel.previousChapter() },
                onNext = { viewModel.nextChapter() }
            )
        }
    ) { padding ->
        // ── Swipe gesture state ─────────────────────────────────────
        var dragAccumulator by remember { mutableStateOf(0f) }

        // ── Center content with max width for readability on tablets ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(selectedChapter) {
                    val chaptersList = chapters
                    val maxChapter = chaptersList.maxOrNull() ?: selectedChapter
                    val commitThreshold = size.width.toFloat() * 0.25f // 25% of width
                    val edgeResistance = 0.3f // rubber-band strength at boundaries

                    detectHorizontalDragGestures(
                        onDragStart = {
                            isSwiping = true
                            dragAccumulator = 0f
                        },
                        onDragEnd = {
                            isSwiping = false
                            scope.launch {
                                if (dragAccumulator > commitThreshold && selectedChapter > 1) {
                                    swipeOffset.snapTo(0f)
                                    viewModel.previousChapter()
                                } else if (dragAccumulator < -commitThreshold && selectedChapter < maxChapter) {
                                    swipeOffset.snapTo(0f)
                                    viewModel.nextChapter()
                                } else {
                                    // Spring back — not enough distance
                                    swipeOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.6f,
                                            stiffness = 500f
                                        )
                                    )
                                }
                                dragAccumulator = 0f
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newAccum = dragAccumulator + dragAmount
                            // Rubber-band at chapter boundaries (Gen 1 / Rev 22 edge)
                            val clamped = if (newAccum > 0 && selectedChapter <= 1) {
                                newAccum * edgeResistance
                            } else if (newAccum < 0 && selectedChapter >= maxChapter) {
                                newAccum * edgeResistance
                            } else {
                                newAccum
                            }
                            dragAccumulator = clamped
                            scope.launch { swipeOffset.snapTo(clamped) }
                        }
                    )
                },
            contentAlignment = androidx.compose.ui.Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 800.dp)  // ponytail: ~72ch at 16sp. Adjust if needed.
                    .graphicsLayer {
                        translationX = swipeOffset.value
                    }
            ) {
            // ── Primary content (verses) ─────────────────────────
            if (isCompareMode) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left pane: primary translation (full features)
                    Box(modifier = Modifier.weight(1f)) {
                        StandardBibleContent(
                            verses = verses,
                            bookNumber = bookNumber,
                            chapter = selectedChapter,
                            fontSizeNumbers = fontSizeVerseNumbers,
                            fontSizeText = fontSizeVerseText,
                            lineSpacing = lineSpacing,
                            bookmarkedIds = bookmarkedIds,
                            highlightMap = highlightMap,
                            crossReferenceMap = crossReferenceMap,
                            onBookmarkToggle = { viewModel.toggleBookmark(it) },
                            onHighlightToggle = { id, color -> viewModel.toggleHighlight(id, color) },
                            onStrongsClick = onStrongsClick,
                            speakingVerseIndex = ttsState.currentVerseIndex,
                            wordRange = ttsState.currentWordRange,
                            listState = primaryListState,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Right pane: secondary translation (simple, read-only)
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleBibleContent(
                            verses = secondaryVerses,
                            translationLabel = secondaryTranslation?.abbreviation ?: "",
                            fontSizeNumbers = fontSizeVerseNumbers,
                            fontSizeText = fontSizeVerseText,
                            lineSpacing = lineSpacing,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            } else if (pageFlipAnimEnabled) {
                AnimatedContent(
                    targetState = selectedChapter,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        val slideDuration = 300
                        (slideInHorizontally(
                            animationSpec = tween(slideDuration),
                            initialOffsetX = { fullWidth -> direction * fullWidth }
                        ) + fadeIn(animationSpec = tween(slideDuration)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(slideDuration),
                                    targetOffsetX = { fullWidth -> -direction * fullWidth }
                                ) + fadeOut(animationSpec = tween(slideDuration))
                            )
                    },
                    label = "pageFlip"
                ) { targetChapter ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        StandardBibleContent(
                            verses = verses,
                            bookNumber = bookNumber,
                            chapter = targetChapter,
                            fontSizeNumbers = fontSizeVerseNumbers,
                            fontSizeText = fontSizeVerseText,
                            lineSpacing = lineSpacing,
                            bookmarkedIds = bookmarkedIds,
                            highlightMap = highlightMap,
                            crossReferenceMap = crossReferenceMap,
                            onBookmarkToggle = { viewModel.toggleBookmark(it) },
                            onHighlightToggle = { id, color -> viewModel.toggleHighlight(id, color) },
                            onStrongsClick = onStrongsClick,
                            speakingVerseIndex = ttsState.currentVerseIndex,
                            wordRange = ttsState.currentWordRange,
                            listState = primaryListState
                        )
                    }
                }
            } else {
                StandardBibleContent(
                    verses = verses,
                    bookNumber = bookNumber,
                    chapter = selectedChapter,
                    fontSizeNumbers = fontSizeVerseNumbers,
                    fontSizeText = fontSizeVerseText,
                    lineSpacing = lineSpacing,
                    bookmarkedIds = bookmarkedIds,
                    highlightMap = highlightMap,
                    crossReferenceMap = crossReferenceMap,
                    onBookmarkToggle = { viewModel.toggleBookmark(it) },
                    onHighlightToggle = { id, color -> viewModel.toggleHighlight(id, color) },
                    onStrongsClick = onStrongsClick,
                    speakingVerseIndex = ttsState.currentVerseIndex,
                    listState = primaryListState
                )
            }

            // ── TTS Controls Overlay ─────────────────────────────
            if (ttsState.isPlaying || ttsState.currentVerseIndex >= 0) {
                TtsControls(
                    state = ttsState,
                    onPlayPause = { ttsController.togglePlayPause() },
                    onSkipNext = { ttsController.skipNext() },
                    onSkipPrev = { ttsController.skipPrev() },
                    onStop = { ttsController.stop() },
                    onSpeedChange = { ttsController.setSpeed(it) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } // end widthIn Box
    }
    }

    // -- Book/Chapter Selector Dialog --

    if (showSelector) {
        BookChapterSelectorDialog(
            translationId = selectedTranslationId,
            selectedBookId = currentBook?.id ?: 1,
            selectedChapter = selectedChapter,
            onSelect = { bookId, chapter ->
                viewModel.navigateToBook(bookId, chapter)
                showSelector = false
            },
            onDismiss = { showSelector = false }
        )
    }

    // -- Translation Picker Dialog --

    if (showTranslationPicker) {
        TranslationPickerDialog(
            translations = translations,
            selectedTranslationId = selectedTranslationId,
            onSelect = { translationId ->
                viewModel.selectTranslation(translationId)
                showTranslationPicker = false
            },
            onDismiss = { showTranslationPicker = false }
        )
    }

    // -- Compare Translation Picker Dialog --

    if (showComparePicker) {
        TranslationPickerDialog(
            translations = translations,
            selectedTranslationId = selectedTranslationId,
            compareMode = true,
            onSelect = { translationId ->
                viewModel.setSecondaryTranslation(translationId)
                showComparePicker = false
            },
            onDismiss = { showComparePicker = false }
        )
    }

    // -- Strong's Concordance Bottom Sheet --

    strongVerseId?.let { verseId ->
        StrongVerseBottomSheet(
            verseId = verseId,
            onOpenStrongDetail = onOpenStrongDetail,
            onDismiss = { strongVerseId = null }
        )
    }
}

    // ── Standard Content (phones and when retro is disabled) ────────

@Composable
private fun StandardBibleContent(
    verses: List<VerseEntity>,
    bookNumber: Int,
    chapter: Int,
    fontSizeNumbers: Float,
    fontSizeText: Float,
    lineSpacing: Float,
    bookmarkedIds: Set<Long>,
    highlightMap: Map<Long, HighlightColor>,
    crossReferenceMap: Map<Long, List<CrossReferenceDisplay>>,
    onBookmarkToggle: (Long) -> Unit,
    onHighlightToggle: (Long, HighlightColor) -> Unit,
    onAddNote: (verseNumber: Int) -> Unit = {},
    onStrongsClick: (verseId: Long) -> Unit = {},
    speakingVerseIndex: Int = -1,
    wordRange: IntRange? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val expandedRefs = remember { mutableStateMapOf<Long, Boolean>() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(verses.withIndex().toList(), key = { it.value.id }) { (index, verse) ->
            val redLetter = RedLetterData.isRedLetter(bookNumber, chapter, verse.verse)
            VerseLine(
                verseNumber = verse.verse,
                text = verse.text,
                isRedLetter = redLetter,
                fontSizeNumber = fontSizeNumbers,
                fontSizeText = fontSizeText,
                lineSpacing = lineSpacing,
                verseId = verse.id,
                currentChapter = chapter,
                isBookmarked = verse.id in bookmarkedIds,
                highlightColor = highlightMap[verse.id],
                crossRefs = crossReferenceMap[verse.id] ?: emptyList(),
                isExpanded = expandedRefs[verse.id] == true,
                isSpeaking = index == speakingVerseIndex,
                wordRange = if (index == speakingVerseIndex) wordRange else null,
                onToggleRefs = {
                    expandedRefs[verse.id] = expandedRefs[verse.id] != true
                },
                onBookmarkToggle = { onBookmarkToggle(verse.id) },
                onHighlightToggle = { color -> onHighlightToggle(verse.id, color) },
                onAddNote = onAddNote,
                onStrongsClick = { onStrongsClick(verse.id) }
            )
        }
    }
}

// ── Simple Bible Content (secondary pane, no interactions) ──────

@Composable
private fun SimpleBibleContent(
    verses: List<VerseEntity>,
    translationLabel: String,
    fontSizeNumbers: Float,
    fontSizeText: Float,
    lineSpacing: Float,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        // Translation label header
        Text(
            text = translationLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(verses, key = { it.id }) { verse ->
                val annotated = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = fontSizeNumbers.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        append("${verse.verse} ")
                    }
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = fontSizeText.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        append(verse.text)
                    }
                }
                Text(
                    text = annotated,
                    lineHeight = (fontSizeText * lineSpacing).sp
                )
            }
        }
    }
}

// ── Verse Line Component ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerseLine(
    verseNumber: Int,
    text: String,
    fontSizeNumber: Float = 18f,
    fontSizeText: Float = 16f,
    lineSpacing: Float = 1.6f,
    verseId: Long = 0L,
    currentChapter: Int = 0,
    isBookmarked: Boolean = false,
    highlightColor: HighlightColor? = null,
    crossRefs: List<CrossReferenceDisplay> = emptyList(),
    isExpanded: Boolean = false,
    isSpeaking: Boolean = false,
    wordRange: IntRange? = null,
    isRedLetter: Boolean = false,
    onToggleRefs: () -> Unit = {},
    onBookmarkToggle: () -> Unit = {},
    onHighlightToggle: (HighlightColor) -> Unit = {},
    onAddNote: (verseNumber: Int) -> Unit = {},
    onStrongsClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Resolve highlight background color
    val highlightBg = highlightColor?.let { hc ->
        when (hc) {
            HighlightColor.YELLOW -> com.openbible.ui.theme.HighlightColors.Yellow
            HighlightColor.GREEN -> com.openbible.ui.theme.HighlightColors.Green
            HighlightColor.BLUE -> com.openbible.ui.theme.HighlightColors.Blue
            HighlightColor.PINK -> com.openbible.ui.theme.HighlightColors.Pink
            HighlightColor.ORANGE -> com.openbible.ui.theme.HighlightColors.Orange
        }
    }

    // ponytail: isDark via system theme. Follows app theme toggle.
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val verseColor = if (isRedLetter) Color(RedLetterData.redLetterColor(isDark))
                     else MaterialTheme.colorScheme.onBackground

    val annotatedString = buildAnnotatedString {
        // Bookmark indicator
        if (isBookmarked) {
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = (fontSizeNumber * 0.9f).sp
                )
            ) {
                append("✦ ")
            }
        }

        // Verse number
        withStyle(
            SpanStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = fontSizeNumber.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        ) {
            append("$verseNumber ")
        }

        // Verse text with optional highlight background and word-level TTS glow
        val baseTextStyle = SpanStyle(
            fontFamily = FontFamily.Serif,
            fontSize = fontSizeText.sp,
            color = verseColor,
            background = highlightBg ?: Color.Transparent
        )

        val wordGlowStyle = SpanStyle(
            fontFamily = FontFamily.Serif,
            fontSize = fontSizeText.sp,
            color = verseColor,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )

        val wordStart: Int? = if (wordRange != null && isSpeaking &&
            wordRange.first >= 0 && wordRange.last < text.length && wordRange.first <= wordRange.last
        ) wordRange.first else null

        if (wordStart != null) {
            val wEnd = wordRange!!.last + 1
            if (wordStart > 0) {
                withStyle(baseTextStyle) { append(text.substring(0, wordStart)) }
            }
            withStyle(wordGlowStyle) { append(text.substring(wordStart, wEnd)) }
            if (wEnd < text.length) {
                withStyle(baseTextStyle) { append(text.substring(wEnd)) }
            }
        } else {
            withStyle(baseTextStyle) { append(text) }
        }
    }

    // Background colour for the currently spoken verse
    val speakingBg = if (isSpeaking) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else Color.Transparent

    Column {
        // ── Verse text with bookmark/highlight ─────────────────
        Text(
            text = annotatedString,
            lineHeight = (fontSizeText * lineSpacing).sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(speakingBg, shape = RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = { /* scroll or select — future use */ },
                    onLongClick = { showMenu = true }
                )
                .padding(vertical = 1.dp)
        )

        // ── Cross-reference indicator and expandable list ──────
        if (crossRefs.isNotEmpty()) {
            TextButton(
                onClick = onToggleRefs,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            ) {
                Text(
                    text = if (isExpanded) "▲ ${crossRefs.size} cross-references"
                           else "▼ ${crossRefs.size} cross-references",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontSize = (fontSizeText * 0.75f).sp
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 2.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    crossRefs.take(10).forEach { ref ->
                        val range = if (ref.toVerseEnd != null && ref.toVerseEnd != ref.toVerseStart)
                            "${ref.toVerseStart}-${ref.toVerseEnd}"
                        else ref.toVerseStart.toString()
                        val refText = "${ref.toBookAbbreviation} ${ref.toChapter}:$range"

                        // ponytail: skip verse snippet when cross-ref points to same chapter —
                        // the full verse text is visible below as regular content,
                        // showing it again in the snippet creates redundancy.
                        val snippet = if (ref.toChapter != currentChapter) {
                            ref.toVerseSnippet
                                .replaceFirst("^\\d+\\s*".toRegex(), "")
                                .let { if (it.length > 80) it.take(80) + "…" else it }
                        } else ""

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                                    append(refText)
                                }
                                if (snippet.isNotEmpty()) {
                                    append(" — ")
                                    append(snippet)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = (fontSizeText * 0.8f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = (fontSizeText * 1.3f).sp
                        )
                    }

                    if (crossRefs.size > 10) {
                        Text(
                            text = "+ ${crossRefs.size - 10} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            fontSize = (fontSizeText * 0.7f).sp
                        )
                    }
                }
            }
        }

        // ── Context Menu (long-press) ───────────────────────────
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            // Bookmark toggle
            DropdownMenuItem(
                text = {
                    Text(if (isBookmarked) "Remove Bookmark" else "Add Bookmark")
                },
                onClick = {
                    onBookmarkToggle()
                    showMenu = false
                }
            )

            // Highlight section header
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Highlight",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                },
                onClick = {},
                enabled = false
            )

            // Color chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HighlightColor.entries.forEach { hc ->
                    val chipColor = when (hc) {
                        HighlightColor.YELLOW -> com.openbible.ui.theme.HighlightColors.Yellow
                        HighlightColor.GREEN -> com.openbible.ui.theme.HighlightColors.Green
                        HighlightColor.BLUE -> com.openbible.ui.theme.HighlightColors.Blue
                        HighlightColor.PINK -> com.openbible.ui.theme.HighlightColors.Pink
                        HighlightColor.ORANGE -> com.openbible.ui.theme.HighlightColors.Orange
                    }
                    val isActive = hc == highlightColor

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(chipColor)
                            .border(
                                width = if (isActive) 2.dp else 0.dp,
                                color = if (isActive) MaterialTheme.colorScheme.onSurface
                                        else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onHighlightToggle(hc)
                                showMenu = false
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Add Note
            DropdownMenuItem(
                text = { Text("Add Note") },
                onClick = {
                    onAddNote(verseNumber)
                    showMenu = false
                }
            )

            // Strong's Concordance
            DropdownMenuItem(
                text = { Text("Strong's Concordance") },
                onClick = {
                    onStrongsClick()
                    showMenu = false
                }
            )

            // Copy verse
            val citationText = "$verseNumber $text"
            DropdownMenuItem(
                text = { Text("Copy Verse") },
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(citationText))
                    showMenu = false
                }
            )
        }
    }
}

// ── Chapter Navigation Bar ─────────────────────────────────────

@Composable
private fun ChapterNavigationBar(
    currentChapter: Int,
    chapters: List<Int>,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = currentChapter > 1
            ) {
                Text("← Previous")
            }

            Text(
                text = "$currentChapter / ${chapters.maxOrNull() ?: currentChapter}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            TextButton(
                onClick = onNext,
                enabled = currentChapter < (chapters.maxOrNull() ?: currentChapter)
            ) {
                Text("Next →")
            }
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────

/**
 * Book/chapter selector in an AlertDialog wrapper.
 * Delegates to the reusable [BookChapterSelector] composable.
 */
@Composable
private fun BookChapterSelectorDialog(
    translationId: String,
    selectedBookId: Int,
    selectedChapter: Int,
    onSelect: (bookId: Int, chapter: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = remember(selectedBookId, selectedChapter) {
        BookChapterSelectorState(selectedBookId, selectedChapter)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Book & Chapter") },
        text = {
            BookChapterSelector(
                translationId = translationId,
                state = state,
                onChapterSelected = onSelect
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TranslationPickerDialog(
    translations: List<com.openbible.data.db.entity.TranslationEntity>,
    selectedTranslationId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    compareMode: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (compareMode) "Compare Translation" else "Translation")
        },
        text = {
            Column {
                translations.forEach { translation ->
                    val isSelected = translation.id == selectedTranslationId
                    TextButton(
                        onClick = { onSelect(translation.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${translation.abbreviation} — ${translation.name}",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (compareMode) "Done" else "Cancel")
            }
        }
    )
}
