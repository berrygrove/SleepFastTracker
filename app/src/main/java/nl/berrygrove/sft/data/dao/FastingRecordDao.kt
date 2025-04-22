package nl.berrygrove.sft.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import nl.berrygrove.sft.data.model.FastingRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Data Access Object for the FastingRecord entity.
 */
@Dao
interface FastingRecordDao {
    @Query("SELECT * FROM fasting_records ORDER BY timestamp DESC")
    fun getAllFastingRecords(): Flow<List<FastingRecord>>

    @Query("SELECT * FROM fasting_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestFastingRecord(): Flow<FastingRecord?>

    @Query("SELECT * FROM fasting_records WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getFastingRecordsInRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<FastingRecord>>
    
    /**
     * Get fasting records that end on a specific date
     * This method returns all records whose timestamp falls on the given date
     */
    @Query("SELECT * FROM fasting_records WHERE timestamp BETWEEN :startOfDay AND :endOfDay")
    fun getFastingRecordsEndingOnDate(date: LocalDate): Flow<List<FastingRecord>> {
        val startOfDay = LocalDateTime.of(date, LocalTime.MIN)
        val endOfDay = LocalDateTime.of(date, LocalTime.MAX)
        return getFastingRecordsInRange(startOfDay, endOfDay)
    }

    @Insert
    suspend fun insert(fastingRecord: FastingRecord): Long

    @Update
    suspend fun update(fastingRecord: FastingRecord)

    @Delete
    suspend fun delete(fastingRecord: FastingRecord)

    @Query("DELETE FROM fasting_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM fasting_records ORDER BY timestamp DESC")
    suspend fun getAllFastingRecordsDirect(): List<FastingRecord>
} 