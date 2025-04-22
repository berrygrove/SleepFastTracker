package nl.berrygrove.sft.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import nl.berrygrove.sft.data.model.SleepRecord
import java.time.LocalDateTime

/**
 * Data Access Object for the SleepRecord entity.
 */
@Dao
interface SleepRecordDao {
    @Query("SELECT * FROM sleep_records ORDER BY timestamp DESC")
    fun getAllSleepRecords(): Flow<List<SleepRecord>>

    @Query("SELECT * FROM sleep_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSleepRecord(): Flow<SleepRecord?>

    @Query("SELECT * FROM sleep_records WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getSleepRecordsInRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<SleepRecord>>
    
    @Query("SELECT * FROM sleep_records WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getSleepRecordsInDateRange(startDate: LocalDateTime, endDate: LocalDateTime): List<SleepRecord>
    
    @Query("SELECT * FROM sleep_records ORDER BY timestamp DESC")
    suspend fun getAllSleepRecordsDirect(): List<SleepRecord>

    @Insert
    suspend fun insert(sleepRecord: SleepRecord): Long

    @Update
    suspend fun update(sleepRecord: SleepRecord)

    @Delete
    suspend fun delete(sleepRecord: SleepRecord)

    @Query("DELETE FROM sleep_records")
    suspend fun deleteAll()
} 