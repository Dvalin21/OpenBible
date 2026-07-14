package com.openbible.data.repository

import com.openbible.data.db.dao.NoteDao
import com.openbible.data.db.entity.NoteAudioEntity
import com.openbible.data.db.entity.NoteEntity
import com.openbible.data.db.entity.NoteImageEntity
import com.openbible.data.db.entity.NoteVerseLinkEntity
import com.openbible.data.db.entity.NotebookEntity
import com.openbible.data.model.PenMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
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
        color: Int? = null,
        pagesJson: String? = null,
        isFavorite: Boolean = false
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
                pagesJson = pagesJson,
                isFavorite = isFavorite,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(note: NoteEntity) {
        // Best-effort cleanup of audio files before the row (and its
        // note_audio children via CASCADE) is removed.
        runCatching {
            noteDao.getAudiosForNote(note.id).first().forEach { File(it.filePath).delete() }
        }
        noteDao.deleteNote(note)
    }

    // -- Audio memos --

    fun getAudiosForNote(noteId: Long): Flow<List<NoteAudioEntity>> = noteDao.getAudiosForNote(noteId)
    suspend fun insertAudio(audio: NoteAudioEntity): Long = noteDao.insertAudio(audio)
    suspend fun deleteAudio(audio: NoteAudioEntity) = noteDao.deleteAudio(audio)
    suspend fun deleteAllAudiosForNote(noteId: Long) = noteDao.deleteAllAudiosForNote(noteId)

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
