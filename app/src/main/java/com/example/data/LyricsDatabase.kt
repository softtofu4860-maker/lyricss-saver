package com.example.data

import android.content.Context
import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

data class LyricLine(
    val timeSec: Float,
    val text: String
)

@Entity(
    tableName = "cached_lyrics",
    indices = [Index(value = ["timestamp"])]
)
data class CachedLyrics(
    @PrimaryKey val id: String, // key: "artist_title" in lowercase without special chars
    val title: String,
    val artist: String,
    val lyricsJson: String, // JSON representing List<LyricLine>
    val bpm: Int,
    val hexColorsJson: String, // JSON representing List<String> primary colors
    val genre: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class LyricsConverters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    @TypeConverter
    fun fromLyricLines(lines: List<LyricLine>?): String? {
        if (lines == null) return null
        val type = Types.newParameterizedType(List::class.java, LyricLine::class.java)
        val adapter = moshi.adapter<List<LyricLine>>(type)
        return adapter.toJson(lines)
    }

    @TypeConverter
    fun toLyricLines(json: String?): List<LyricLine>? {
        if (json == null) return null
        val type = Types.newParameterizedType(List::class.java, LyricLine::class.java)
        val adapter = moshi.adapter<List<LyricLine>>(type)
        return adapter.fromJson(json)
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(type)
        return adapter.toJson(list)
    }

    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        if (json == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(type)
        return adapter.fromJson(json)
    }
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE id = :id LIMIT 1")
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

@Database(entities = [CachedLyrics::class], version = 2, exportSchema = false)
@TypeConverters(LyricsConverters::class)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: LyricsDatabase? = null

        fun getDatabase(context: Context): LyricsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "lyrics_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
