package com.mynote.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MyNoteDao {
    @Query("SELECT * FROM documents ORDER BY addedAt DESC") fun documents(): Flow<List<DocumentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDocument(document: DocumentEntity)
    @Query("SELECT * FROM page_annotations WHERE documentId = :documentId AND pageNumber = :page") suspend fun annotation(documentId: String, page: Int): PageAnnotationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAnnotation(annotation: PageAnnotationEntity)
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun notes(): Flow<List<NoteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNote(note: NoteEntity)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: String)
}

@Database(entities = [DocumentEntity::class, PageAnnotationEntity::class, NoteEntity::class], version = 1, exportSchema = true)
abstract class MyNoteDatabase : RoomDatabase() {
    abstract fun dao(): MyNoteDao
    companion object {
        @Volatile private var INSTANCE: MyNoteDatabase? = null
        fun get(context: Context): MyNoteDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, MyNoteDatabase::class.java, "mynote.db").build().also { INSTANCE = it }
        }
    }
}
