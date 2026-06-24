package com.openbible.data.repository

import com.openbible.data.db.dao.NoteDao
import com.openbible.data.db.entity.NoteEntity
import com.openbible.data.db.entity.NoteImageEntity
import com.openbible.data.db.entity.NoteVerseLinkEntity
import com.openbible.data.db.entity.NotebookEntity
import com.openbible.data.model.PenMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    // -- Notebooks --

    fun getAllNotebooks(): Flow<List<NotebookEntity>> = noteDao.getAllNotebooks()

    suspend fun createNotebook(name: String, color: Int, icon: String? = null): Long {
        return noteDao.insertNotebook(
            NotebookEntity(name = name, color = color, icon = icon, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteNotebook(notebook: NotebookEntity) = noteDao.deleteNotebook(notebook)
    suspend fun updateNotebook(notebook: NotebookEntity) = noteDao.updateNotebook(notebook)

    // -- Notes --

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()
    fun getNotesByNotebook(notebookId: Long): Flow<List<NoteEntity>> = noteDao.getNotesByNotebook(notebookId)
    suspend fun getNote(noteId: Long): NoteEntity? = noteDao.getNote(noteId)
    fun getNoteFlow(noteId: Long): Flow<NoteEntity?> = noteDao.getNoteFlow(noteId)

    suspend fun createNote(
        notebookId: Long?,
        title: String,
        contentText: String? = null,
        penStrokes: String? = null,
        penMode: PenMode = PenMode.TEXT,
        tags: String? = null,
        color: Int? = null
    ): Long {
        return noteDao.insertNote(
            NoteEntity(
                notebookId = notebookId,
                title = title,
                contentText = contentText,
                penStrokes = penStrokes,
                penMode = penMode,
                tags = tags,
                color = color,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

    suspend fun searchNotes(query: String): List<NoteEntity> = noteDao.searchNotes(query)

    // -- Images --

    fun getImagesForNote(noteId: Long): Flow<List<NoteImageEntity>> = noteDao.getImagesForNote(noteId)
    suspend fun insertImage(image: NoteImageEntity): Long = noteDao.insertImage(image)
    suspend fun deleteAllImagesForNote(noteId: Long) = noteDao.deleteAllImagesForNote(noteId)

    // -- Verse Links --

    suspend fun getLinkedVerseIds(noteId: Long): List<Long> = noteDao.getLinkedVerseIds(noteId)
    suspend fun linkVerse(noteId: Long, verseId: Long) {
        noteDao.insertVerseLink(NoteVerseLinkEntity(noteId = noteId, verseId = verseId))
    }

    suspend fun unlinkVerse(noteId: Long, verseId: Long) {
        noteDao.deleteVerseLink(noteId, verseId)
    }
}
