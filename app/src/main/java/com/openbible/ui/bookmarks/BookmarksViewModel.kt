package com.openbible.ui.bookmarks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.OpenBibleApp
import com.openbible.data.db.dao.BookmarkDao
import com.openbible.data.db.dao.BookmarkWithVerse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bookmarks screen state — lists all saved bookmarks with verse info.
 */
class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenBibleApp
    private val bookmarkDao: BookmarkDao = app.database.bookmarkDao()

    /** All bookmarks joined with verse/book info, sorted by newest first. */
    val bookmarks: StateFlow<List<BookmarkWithVerse>> = bookmarkDao
        .getBookmarksWithVerse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Delete a bookmark by ID. */
    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            bookmarkDao.deleteById(bookmarkId)
        }
    }
}
