package com.openbible.ui.bible

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.openbible.ui.strongs.StrongVerseBottomSheet
import kotlin.math.min

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
    onOpenStrongDetail: (strongNumber: String) -> Unit = {},
    viewModel: BibleViewModel = viewModel()
) {
    val verses by viewModel.verses.collectAsState()
    val currentBook by viewModel.currentBook.collectAsState()
    val selectedChapter by viewModel.selectedChapter.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val selectedTranslationId by viewModel.selectedTranslationId.collectAsState()
    val books by viewModel.books.collectAsState()
    val retroConfig = LocalRetroPixel.current

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
    val retroEnabledPref by prefs.retroThemeEnabled.collectAsState(initial = true)
    val pageFlipAnimEnabled by prefs.pageFlipAnimation.collectAsState(initial = true)
    val pageFlipSoundEnabled by prefs.pageFlipSound.collectAsState(initial = true)

    // Retro is enabled only when device is a tablet AND user hasn't disabled it
    val retroEffective = retroConfig.enabled && retroEnabledPref

    // ── Text-to-Speech ────────────────────────────────────────────
    val ttsController = remember { TtsController(context.applicationContext) }
    val ttsState by ttsController.state.collectAsState()

    // Initialise engine once
    LaunchedEffect(Unit) { ttsController.init() }

    // Release engine when leaving the screen
    DisposableEffect(Unit) {
        onDispose { ttsController.shutdown() }
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

    // Record reading on chapter change
    LaunchedEffect(selectedChapter) {
        viewModel.recordReading()
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
        // ── Outer wrapper: layers verses + TTS controls overlay ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Primary content (verses) ─────────────────────────
            if (isCompareMode) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left pane: primary translation (full features)
                    Box(modifier = Modifier.weight(1f)) {
                        if (retroEffective) {
                            RetroBibleContent(
                                verses = verses,
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
                                listState = primaryListState,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        } else {
                            StandardBibleContent(
                                verses = verses,
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
                                listState = primaryListState,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
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
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (retroEffective) {
                            RetroBibleContent(
                                verses = verses,
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
                        } else {
                            StandardBibleContent(
                                verses = verses,
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
                    }
                }
            } else {
                if (retroEffective) {
                    RetroBibleContent(
                        verses = verses,
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
                } else {
                    StandardBibleContent(
                        verses = verses,
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
            VerseLine(
                verseNumber = verse.verse,
                text = verse.text,
                isRetro = false,
                fontSizeNumber = fontSizeNumbers,
                fontSizeText = fontSizeText,
                lineSpacing = lineSpacing,
                verseId = verse.id,
                isBookmarked = verse.id in bookmarkedIds,
                highlightColor = highlightMap[verse.id],
                crossRefs = crossReferenceMap[verse.id] ?: emptyList(),
                isExpanded = expandedRefs[verse.id] == true,
                isSpeaking = index == speakingVerseIndex,
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

// ── Retro Pixel Content (7"+ tablets) ──────────────────────────

@Composable
private fun RetroBibleContent(
    verses: List<VerseEntity>,
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
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val retro = LocalRetroPixel.current
    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val parchmentColor = if (isDark) RetroParchmentDark else RetroParchment
    val textColor = if (isDark) RetroTextDark else RetroText
    val borderColor = if (isDark) RetroTextDark else RetroBorder
    val goldColor = RetroGold

    val expandedRefs = remember { mutableStateMapOf<Long, Boolean>() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = retro.pagePaddingHorizontal)
    ) {
        // ── Parchment Background Canvas ─────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = retro.ornamentHeight + 4.dp, bottom = retro.ornamentHeight + 4.dp)
        ) {
            val w = size.width
            val h = size.height

            // Fill parchment background
            drawRect(color = parchmentColor)

            // Page-edge shadow (left side)
            val shadowBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startX = 0f,
                endX = retro.pageShadowWidth.toPx()
            )
            drawRect(
                brush = shadowBrush,
                topLeft = Offset.Zero,
                size = Size(retro.pageShadowWidth.toPx(), h)
            )

            // Top ornamental gold line
            val lineY = 0f
            drawLine(
                color = goldColor,
                start = Offset(4.dp.toPx(), lineY),
                end = Offset(w - 4.dp.toPx(), lineY),
                strokeWidth = 2.dp.toPx()
            )

            // Second line (1dp below)
            drawLine(
                color = goldColor,
                start = Offset(4.dp.toPx(), lineY + 3.dp.toPx()),
                end = Offset(w - 4.dp.toPx(), lineY + 3.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            // Corner ornaments (top-left and top-right squares)
            val cornerSize = 4.dp.toPx()
            drawRect(
                color = goldColor,
                topLeft = Offset(0f, lineY - cornerSize / 2),
                size = Size(cornerSize, cornerSize)
            )
            drawRect(
                color = goldColor,
                topLeft = Offset(w - cornerSize, lineY - cornerSize / 2),
                size = Size(cornerSize, cornerSize)
            )

            // Bottom ornamental gold line
            val bottomY = h
            drawLine(
                color = goldColor,
                start = Offset(4.dp.toPx(), bottomY),
                end = Offset(w - 4.dp.toPx(), bottomY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = goldColor,
                start = Offset(4.dp.toPx(), bottomY - 3.dp.toPx()),
                end = Offset(w - 4.dp.toPx(), bottomY - 3.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            // Corner ornaments (bottom-left and bottom-right)
            drawRect(
                color = goldColor,
                topLeft = Offset(0f, bottomY - cornerSize / 2),
                size = Size(cornerSize, cornerSize)
            )
            drawRect(
                color = goldColor,
                topLeft = Offset(w - cornerSize, bottomY - cornerSize / 2),
                size = Size(cornerSize, cornerSize)
            )
        }

        // ── Verse List ──────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = retro.ornamentHeight + 8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = retro.ornamentHeight + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(verses.withIndex().toList(), key = { it.value.id }) { (index, verse) ->
                VerseLine(
                    verseNumber = verse.verse,
                    text = verse.text,
                    isRetro = true,
                    fontSizeNumber = fontSizeNumbers,
                    fontSizeText = fontSizeText,
                    lineSpacing = lineSpacing,
                    verseId = verse.id,
                    isBookmarked = verse.id in bookmarkedIds,
                    highlightColor = highlightMap[verse.id],
                    crossRefs = crossReferenceMap[verse.id] ?: emptyList(),
                    isExpanded = expandedRefs[verse.id] == true,
                    isSpeaking = index == speakingVerseIndex,
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
} // end RetroBibleContent

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
    val pixelFont = FontFamily(Font(R.font.pixelify_sans))

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
                            fontFamily = pixelFont,
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
    isRetro: Boolean,
    fontSizeNumber: Float = 18f,
    fontSizeText: Float = 16f,
    lineSpacing: Float = 1.6f,
    verseId: Long = 0L,
    isBookmarked: Boolean = false,
    highlightColor: HighlightColor? = null,
    crossRefs: List<CrossReferenceDisplay> = emptyList(),
    isExpanded: Boolean = false,
    isSpeaking: Boolean = false,
    onToggleRefs: () -> Unit = {},
    onBookmarkToggle: () -> Unit = {},
    onHighlightToggle: (HighlightColor) -> Unit = {},
    onAddNote: (verseNumber: Int) -> Unit = {},
    onStrongsClick: () -> Unit = {}
) {
    val pixelFont = FontFamily(Font(R.font.pixelify_sans))
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
                fontFamily = if (isRetro) pixelFont else FontFamily.SansSerif,
                fontSize = fontSizeNumber.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRetro) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                letterSpacing = if (isRetro) 1.sp else 0.sp
            )
        ) {
            append("$verseNumber ")
        }

        // Verse text with optional highlight background
        withStyle(
            SpanStyle(
                fontFamily = if (isRetro) pixelFont else FontFamily.Serif,
                fontSize = fontSizeText.sp,
                color = MaterialTheme.colorScheme.onBackground,
                background = highlightBg ?: Color.Transparent
            )
        ) {
            append(text)
        }
    }

    // Background colour for the currently spoken verse
    val speakingBg = if (isSpeaking) {
        if (isRetro) com.openbible.ui.theme.RetroGold.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                        val snippet = ref.toVerseSnippet
                            .replaceFirst("^\\d+\\s*".toRegex(), "") // strip leading verse number
                            .let { if (it.length > 80) it.take(80) + "…" else it }

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
