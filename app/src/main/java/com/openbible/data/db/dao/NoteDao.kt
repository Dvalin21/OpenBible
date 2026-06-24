package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.openbible.data.db.entity.NoteEntity
import com.openbible.data.db.entity.NoteImageEntity
import com.openbible.data.db.entity.NoteVerseLinkEntity
import com.openbible.data.db.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // -- Notebooks --

    @Query("SELECT * FROM notebooks ORDER BY name")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)

    // -- Notes --

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE notebookId = :notebookId ORDER BY updatedAt DESC")
    fun getNotesByNotebook(notebookId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNote(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun getNoteFlow(noteId: Long): Flow<NoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR contentText LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchNotes(query: String): List<NoteEntity>

    // -- Note Images --

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY position")
    fun getImagesForNote(noteId: Long): Flow<List<NoteImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: NoteImageEntity): Long

    @Delete
    suspend fun deleteImage(image: NoteImageEntity)

    @Query("DELETE FROM note_images WHERE noteId = :noteId")
    suspend fun deleteAllImagesForNote(noteId: Long)

    // -- Note-Verse Links --

    @Query("SELECT * FROM note_verse_links WHERE noteId = :noteId")
    fun getVerseLinksForNote(noteId: Long): Flow<List<NoteVerseLinkEntity>>

    @Query("SELECT verseId FROM note_verse_links WHERE noteId = :noteId")
    suspend fun getLinkedVerseIds(noteId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVerseLink(link: NoteVerseLinkEntity): Long

    @Query("DELETE FROM note_verse_links WHERE noteId = :noteId AND verseId = :verseId")
    suspend fun deleteVerseLink(noteId: Long, verseId: Long)

    @Query("DELETE FROM note_verse_links WHERE noteId = :noteId")
    suspend fun deleteAllVerseLinksForNote(noteId: Long)
}
