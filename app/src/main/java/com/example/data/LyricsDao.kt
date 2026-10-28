package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @androidx.room.Transaction
    suspend fun claimLegacy(legacyId: String, newId: String): CachedLyrics? {
        getLyricsById(newId)?.let { return it }
        val legacy = getLyricsById(legacyId) ?: return null
        if (!legacy.isUserSaved || legacy.isFallback ||
            (legacy.identityBinding != null && legacy.identityBinding != newId && legacy.identityBinding != legacyId)) return null
        insertLyrics(legacy.copy(identityBinding = newId))
        return legacy.copy(id = newId, identityBinding = newId).also { insertLyrics(it) }
    }
    @Query("SELECT * FROM cached_lyrics WHERE id = :id")
    suspend fun getLyricsById(id: String): CachedLyrics?

    @Query("SELECT * FROM cached_lyrics ORDER BY timestamp DESC")
    fun getAllCachedLyrics(): Flow<List<CachedLyrics>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: CachedLyrics)

    @Query("DELETE FROM cached_lyrics WHERE id = :id")
    suspend fun deleteLyricsById(id: String)

    @Query("DELETE FROM cached_lyrics WHERE isUserSaved = 0")
    suspend fun clearAutomaticCache()
}
