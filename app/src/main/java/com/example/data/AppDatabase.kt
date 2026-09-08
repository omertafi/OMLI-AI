package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_consultations ORDER BY timestamp DESC")
    fun getAllConsultations(): Flow<List<ChatConsultationEntity>>

    @Query("SELECT * FROM chat_consultations WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarkedConsultations(): Flow<List<ChatConsultationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ChatConsultationEntity): Long

    @Query("UPDATE chat_consultations SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmark(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM chat_consultations WHERE id = :id")
    suspend fun deleteConsultation(id: Long)
}

@Dao
interface TaxCalcDao {
    @Query("SELECT * FROM tax_calculations ORDER BY timestamp DESC")
    fun getAllCalculations(): Flow<List<TaxCalculationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculation(calc: TaxCalculationEntity): Long

    @Query("DELETE FROM tax_calculations WHERE id = :id")
    suspend fun deleteCalculation(id: Long)
}

@Dao
interface BookmarkedStandardDao {
    @Query("SELECT * FROM bookmarked_standards ORDER BY timestamp DESC")
    fun getAllBookmarked(): Flow<List<BookmarkedStandardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(item: BookmarkedStandardEntity)

    @Query("DELETE FROM bookmarked_standards WHERE code = :code")
    suspend fun deleteBookmark(code: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarked_standards WHERE code = :code)")
    fun isBookmarked(code: String): Flow<Boolean>
}

@Database(
    entities = [
        ChatConsultationEntity::class,
        TaxCalculationEntity::class,
        BookmarkedStandardEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun taxCalcDao(): TaxCalcDao
    abstract fun bookmarkedStandardDao(): BookmarkedStandardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omly_ai_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
