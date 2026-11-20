package com.mynote.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val localPath: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "page_annotations", primaryKeys = ["documentId", "pageNumber"])
data class PageAnnotationEntity(
    val documentId: String,
    val pageNumber: Int,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)
