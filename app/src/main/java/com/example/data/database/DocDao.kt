package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedTime DESC LIMIT 30")
    fun getRecentDocs(): Flow<List<DocEntity>>

    @Query("SELECT * FROM documents ORDER BY lastModified DESC")
    fun getAllDocs(): Flow<List<DocEntity>>

    @Query("SELECT * FROM documents WHERE isFavorite = 1 ORDER BY lastModified DESC")
    fun getFavorites(): Flow<List<DocEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocById(id: String): DocEntity?

    @Query("SELECT * FROM documents WHERE uriString = :uriString LIMIT 1")
    suspend fun getDocByUri(uriString: String): DocEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoc(doc: DocEntity)

    @Update
    suspend fun updateDoc(doc: DocEntity)

    @Query("UPDATE documents SET lastReadPage = :page, lastOpenedTime = :openedTime WHERE id = :id")
    suspend fun updateReadingProgress(id: String, page: Int, openedTime: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDoc(id: String)

    @Query("DELETE FROM documents WHERE uriString = :uriString")
    suspend fun deleteDocByUri(uriString: String)
}
