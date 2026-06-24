package com.openbible.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openbible.ui.bible.BibleScreen
import com.openbible.ui.bookmarks.BookmarksScreen
import com.openbible.ui.home.HomeScreen
import com.openbible.ui.readingplan.ReadingPlanScreen
import com.openbible.ui.search.SearchScreen
import com.openbible.ui.settings.SettingsScreen

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
    const val NOTES = "notes"
    const val READING_PLANS = "reading_plans"

    fun bibleChapter(translationId: String, bookId: Int, chapter: Int) =
        "bible/$translationId/$bookId/$chapter"
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
    BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * Main navigation graph with bottom navigation bar.
 *
 * @param initialTranslationId Optional — navigate directly to a chapter on first render
 * @param initialBookId Optional — navigate directly to a chapter on first render
 * @param initialChapter Optional — navigate directly to a chapter on first render
 */
@Composable
fun OpenBibleNavGraph(
    isTablet: Boolean,
    initialTranslationId: String? = null,
    initialBookId: Int? = null,
    initialChapter: Int? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Navigate to chapter if launched from a notification
    val hasInitialNav = androidx.compose.runtime.remember {
        initialTranslationId != null && initialBookId != null && initialChapter != null
    }
    if (hasInitialNav && initialTranslationId != null && initialBookId != null && initialChapter != null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            navController.navigate(
                Routes.bibleChapter(initialTranslationId, initialBookId, initialChapter)
            )
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            OpenBibleBottomBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenBible = { navController.navigate(Routes.BIBLE) },
                    onOpenChapter = { translationId, bookId, chapter ->
                        navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                    },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenReadingPlans = { navController.navigate(Routes.READING_PLANS) }
                )
            }

            composable(Routes.BIBLE) {
                BibleScreen(
                    onNavigateToChapter = { translationId, bookId, chapter ->
                        navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                    },
                    isTablet = isTablet
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
                    isTablet = isTablet
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

            composable(Routes.READING_PLANS) {
                ReadingPlanScreen(
                    onOpenChapter = { translationId, bookId, chapter ->
                        navController.navigate(Routes.bibleChapter(translationId, bookId, chapter))
                    }
                )
            }
        }
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
