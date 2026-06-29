package com.openbible.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.openbible.ui.bible.BibleScreen
import com.openbible.ui.bookmarks.BookmarksScreen
import com.openbible.ui.home.HomeScreen
import com.openbible.ui.notes.BibleWithNotesScreen
import com.openbible.ui.notes.NoteEditorScreen
import com.openbible.ui.notes.NotebookListScreen
import com.openbible.ui.readingplan.ReadingPlanScreen
import com.openbible.ui.search.SearchScreen
import com.openbible.ui.settings.SettingsScreen
import com.openbible.ui.locations.LocationDetailScreen
import com.openbible.ui.locations.LocationListScreen
import com.openbible.ui.strongs.StrongDetailScreen
import com.openbible.ui.strongs.StrongSearchScreen

/**
 * Navigation destinations for OpenBible.
 */
object Routes {
    const val HOME = "home"
    const val BIBLE = "bible"
    const val BIBLE_CHAPTER = "bible/{translationId}/{bookId}/{chapter}"
    const val SEARCH = "search"
    const val BOOKMARKS = "bookmarks"
    const val SETTINGS = "settings"
    const val NOTES = "notebooks"
    const val NOTE_EDITOR = "note_editor?noteId={noteId}&translationId={translationId}&bookId={bookId}&chapter={chapter}&verseNumber={verseNumber}"
    const val READING_PLANS = "reading_plans"
    const val BIBLE_WITH_NOTES = "bible_with_notes?translationId={translationId}&bookId={bookId}&chapter={chapter}&noteId={noteId}"
    const val STRONG_SEARCH = "strongs"
    const val STRONG_DETAIL = "strongs/{strongNumber}"
    const val LOCATIONS = "locations"
    const val LOCATION_DETAIL = "locations/{locationId}"

    fun bibleChapter(translationId: String, bookId: Int, chapter: Int) =
        "bible/$translationId/$bookId/$chapter"

    fun noteEditor(
        noteId: Long? = null,
        translationId: String? = null,
        bookId: Int? = null,
        chapter: Int? = null,
        verseNumber: Int? = null
    ): String {
        val params = mutableListOf<String>()
        noteId?.let { params.add("noteId=$it") }
        translationId?.let { params.add("translationId=$it") }
        bookId?.let { params.add("bookId=$it") }
        chapter?.let { params.add("chapter=$it") }
        verseNumber?.let { params.add("verseNumber=$it") }
        return if (params.isEmpty()) "note_editor" else "note_editor?${params.joinToString("&")}"
    }

    fun strongDetail(strongNumber: String) = "strongs/$strongNumber"
    fun locationDetail(locationId: String) = "locations/$locationId"

    fun bibleWithNotes(
        translationId: String = "kjv",
        bookId: Int = 1,
        chapter: Int = 1,
        noteId: Long? = null
    ): String {
        val params = mutableListOf("translationId=$translationId", "bookId=$bookId", "chapter=$chapter")
        noteId?.let { params.add("noteId=$it") }
        return "bible_with_notes?${params.joinToString("&")}"
    }
}

/**
 * Bottom navigation items (5 destinations).
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Routes.BIBLE, "Bible", Icons.Filled.Book, Icons.Outlined.Book),
    BottomNavItem(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(Routes.BOOKMARKS, "Bookmarks", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    BottomNavItem(Routes.NOTES, "Notes", Icons.Filled.EditNote, Icons.Outlined.EditNote),
    BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * Main navigation graph with bottom navigation bar.
 *
 * @param initialTranslationId Optional — navigate directly to a chapter on first render
 * @param initialBookId Optional — navigate directly to a chapter on first render
 * @param initialChapter Optional — navigate directly to a chapter on first render
 * @param onNotificationConsumed Called after navigating from a notification (clears the pending nav state)
 */
@Composable
fun OpenBibleNavGraph(
    isTablet: Boolean,
    initialTranslationId: String? = null,
    initialBookId: Int? = null,
    initialChapter: Int? = null,
    onNotificationConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Navigate to chapter if launched from a notification or onNewIntent
    val hasInitialNav = initialTranslationId != null && initialBookId != null && initialChapter != null
    if (hasInitialNav) {
        androidx.compose.runtime.LaunchedEffect(initialTranslationId, initialBookId, initialChapter) {
            navController.navigate(
                Routes.bibleChapter(initialTranslationId!!, initialBookId!!, initialChapter!!)
            )
            onNotificationConsumed()
        }
    }

    if (isTablet) {
        Row(modifier = modifier) {
            OpenBibleNavRail(navController = navController)
            Scaffold(modifier = Modifier.weight(1f)) { innerPadding ->
                NavContent(
                    navController = navController,
                    isTablet = isTablet,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    } else {
        Scaffold(
            modifier = modifier,
            bottomBar = { OpenBibleBottomBar(navController = navController) }
        ) { innerPadding ->
            NavContent(
                navController = navController,
                isTablet = isTablet,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

/**
 * Shared NavHost content used by both phone and tablet layouts.
 * All composable routes live here to avoid duplication.
 */
@Composable
private fun NavContent(
    navController: NavHostController,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenBible = { navController.navigate(Routes.BIBLE) },
                onOpenChapter = { translationId, bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenNotes = { navController.navigate(Routes.NOTES) },
                onOpenReadingPlans = { navController.navigate(Routes.READING_PLANS) },
                onOpenStrongs = { navController.navigate(Routes.STRONG_SEARCH) },
                onOpenLocations = { navController.navigate(Routes.LOCATIONS) }
            )
        }

        composable(Routes.BIBLE) {
            BibleScreen(
                onNavigateToChapter = { translationId, bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                },
                onAddNote = { verseNumber ->
                    navController.navigate(Routes.noteEditor(verseNumber = verseNumber))
                },
                isTablet = isTablet,
                onOpenStrongDetail = { number ->
                    navController.navigate(Routes.strongDetail(number))
                }
            )
        }

        composable(Routes.BIBLE_CHAPTER) { backStackEntry ->
            val translationId = backStackEntry.arguments?.getString("translationId") ?: "kjv"
            val bookId = backStackEntry.arguments?.getString("bookId")?.toIntOrNull() ?: 1
            val chapter = backStackEntry.arguments?.getString("chapter")?.toIntOrNull() ?: 1
            BibleScreen(
                initialTranslationId = translationId,
                initialBookId = bookId,
                initialChapter = chapter,
                onNavigateToChapter = { _, b, c ->
                    navController.navigate(Routes.bibleChapter(translationId, b, c))
                },
                onAddNote = { verseNumber ->
                    navController.navigate(Routes.noteEditor(
                        translationId = translationId,
                        bookId = bookId,
                        chapter = chapter,
                        verseNumber = verseNumber
                    ))
                },
                isTablet = isTablet,
                onOpenStrongDetail = { number ->
                    navController.navigate(Routes.strongDetail(number))
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onOpenVerse = { translationId, bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                }
            )
        }

        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onOpenVerse = { translationId, bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(Routes.NOTES) {
            NotebookListScreen(
                onOpenNote = { noteId ->
                    navController.navigate(Routes.noteEditor(noteId = noteId))
                },
                onNewNote = {
                    navController.navigate(Routes.noteEditor())
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.NOTE_EDITOR,
            arguments = listOf(
                navArgument("noteId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("translationId") { type = NavType.StringType; defaultValue = "" },
                navArgument("bookId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("chapter") { type = NavType.IntType; defaultValue = -1 },
                navArgument("verseNumber") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId").takeIf { it != -1L }

            NoteEditorScreen(
                noteId = noteId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.BIBLE_WITH_NOTES,
            arguments = listOf(
                navArgument("translationId") { type = NavType.StringType; defaultValue = "kjv" },
                navArgument("bookId") { type = NavType.IntType; defaultValue = 1 },
                navArgument("chapter") { type = NavType.IntType; defaultValue = 1 },
                navArgument("noteId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val translationId = backStackEntry.arguments?.getString("translationId") ?: "kjv"
            val bookId = backStackEntry.arguments?.getInt("bookId") ?: 1
            val chapter = backStackEntry.arguments?.getInt("chapter") ?: 1
            val noteId = backStackEntry.arguments?.getLong("noteId").takeIf { it != -1L }

            BibleWithNotesScreen(
                initialTranslationId = translationId,
                initialBookId = bookId,
                initialChapter = chapter,
                noteId = noteId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.READING_PLANS) {
            ReadingPlanScreen(
                onOpenChapter = { translationId, bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                }
            )
        }

        composable(Routes.STRONG_SEARCH) {
            StrongSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenDetail = { number ->
                    navController.navigate(Routes.strongDetail(number))
                }
            )
        }

        composable(Routes.LOCATIONS) {
            LocationListScreen(
                onNavigateBack = { navController.popBackStack() },
                onLocationSelected = { locationId ->
                    navController.navigate(Routes.locationDetail(locationId))
                }
            )
        }

        composable(
            route = Routes.LOCATION_DETAIL,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId") ?: "jerusalem"
            LocationDetailScreen(
                locationId = locationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.STRONG_DETAIL,
            arguments = listOf(navArgument("strongNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val strongNumber = backStackEntry.arguments?.getString("strongNumber") ?: "G1"
            StrongDetailScreen(
                strongNumber = strongNumber,
                onNavigateBack = { navController.popBackStack() },
                onOpenVerse = { translationId, bookId, chapter ->
                    navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                }
            )
        }
    }
}

@Composable
private fun OpenBibleNavRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationRail(
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(Modifier.weight(1f))
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OpenBibleBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
