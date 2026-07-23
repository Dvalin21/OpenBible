package com.openbible.di

import android.content.Context
import com.openbible.data.db.OpenBibleDatabase
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.BookmarkDao
import com.openbible.data.db.dao.CrossReferenceDao
import com.openbible.data.db.dao.HighlightDao
import com.openbible.data.db.dao.ImportDao
import com.openbible.data.db.dao.NoteDao
import com.openbible.data.db.dao.ReadingHistoryDao
import com.openbible.data.db.dao.ReadingPlanDao
import com.openbible.data.db.dao.LocationDao
import com.openbible.data.db.dao.ParallelTraditionDao
import com.openbible.data.db.dao.StrongDao
import com.openbible.data.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenBibleDatabase =
        OpenBibleDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences =
        UserPreferences(context)

    @Provides fun provideBibleDao(db: OpenBibleDatabase): BibleDao = db.bibleDao()
    @Provides fun provideBookmarkDao(db: OpenBibleDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideCrossReferenceDao(db: OpenBibleDatabase): CrossReferenceDao = db.crossReferenceDao()
    @Provides fun provideHighlightDao(db: OpenBibleDatabase): HighlightDao = db.highlightDao()
    @Provides fun provideNoteDao(db: OpenBibleDatabase): NoteDao = db.noteDao()
    @Provides fun provideReadingPlanDao(db: OpenBibleDatabase): ReadingPlanDao = db.readingPlanDao()
    @Provides fun provideReadingHistoryDao(db: OpenBibleDatabase): ReadingHistoryDao = db.readingHistoryDao()
    @Provides fun provideStrongDao(db: OpenBibleDatabase): StrongDao = db.strongDao()
    @Provides fun provideLocationDao(db: OpenBibleDatabase): LocationDao = db.locationDao()
    @Provides fun provideParallelTraditionDao(db: OpenBibleDatabase): ParallelTraditionDao = db.parallelTraditionDao()
    @Provides fun provideImportDao(db: OpenBibleDatabase): ImportDao = db.importDao()
}
