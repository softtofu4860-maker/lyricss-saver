package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE id = :id")
    suspend fun getLyricsById(id: String): CachedLyrics?

    @Query("SELECT * FROM cached_lyrics ORDER BY timestamp DESC")
    fun getAllCachedLyrics(): Flow<List<CachedLyrics>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: CachedLyrics)

    @Query("DELETE FROM cached_lyrics WHERE id = :id")
    suspend fun deleteLyricsById(id: String)

    @Query("DELETE FROM cached_lyrics")
    suspend fun clearAll()
}
