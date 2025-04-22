package nl.berrygrove.sft.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import nl.berrygrove.sft.data.model.WeightRecord
import java.time.LocalDateTime

/**
 * Data Access Object for the WeightRecord entity.
 */
@Dao
interface WeightRecordDao {
    @Insert
    suspend fun insert(weightRecord: WeightRecord): Long

    @Update
    suspend fun update(weightRecord: WeightRecord)

    @Delete
    suspend fun delete(weightRecord: WeightRecord)

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC")
    fun getAllWeightRecords(): LiveData<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC")
    fun getAllWeightRecordsFlow(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentWeightRecords(limit: Int): LiveData<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC LIMIT 1")
    fun getLatestWeightRecord(): LiveData<WeightRecord?>

    @Query("SELECT * FROM weight_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWeightRecordDirect(): WeightRecord?

    @Query("SELECT * FROM weight_records WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp ASC")
    fun getWeightRecordsBetweenDates(startDate: LocalDateTime, endDate: LocalDateTime): LiveData<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY timestamp ASC")
    suspend fun getAllWeightRecordsDirect(): List<WeightRecord>

    @Query("SELECT * FROM weight_records WHERE id = :id")
    suspend fun getWeightRecord(id: Long): WeightRecord?
} 