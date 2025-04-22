package nl.berrygrove.sft.repository

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.dao.WeightRecordDao
import nl.berrygrove.sft.data.model.WeightRecord
import java.time.LocalDateTime

/**
 * Repository for accessing WeightRecord data.
 */
class WeightRecordRepository(
    val weightRecordDao: WeightRecordDao
) {

    val allWeightRecords: LiveData<List<WeightRecord>> = weightRecordDao.getAllWeightRecords()
    val latestWeightRecord: LiveData<WeightRecord?> = weightRecordDao.getLatestWeightRecord()

    fun getRecentWeightRecords(limit: Int): LiveData<List<WeightRecord>> {
        return weightRecordDao.getRecentWeightRecords(limit)
    }

    suspend fun insert(weightRecord: WeightRecord): Long {
        SleepFastTrackerApplication.addDebugLog("WeightRecordRepository", "Inserting weight record: $weightRecord")
        return weightRecordDao.insert(weightRecord)
    }

    suspend fun update(weightRecord: WeightRecord) {
        SleepFastTrackerApplication.addDebugLog("WeightRecordRepository", "Updating weight record: $weightRecord")
        weightRecordDao.update(weightRecord)
        SleepFastTrackerApplication.addDebugLog("WeightRecordRepository", "Weight record updated in DAO")
    }

    suspend fun delete(weightRecord: WeightRecord) {
        SleepFastTrackerApplication.addDebugLog("WeightRecordRepository", "Deleting weight record: $weightRecord")
        weightRecordDao.delete(weightRecord)
    }

    fun getWeightRecordsBetweenDates(startDate: LocalDateTime, endDate: LocalDateTime): LiveData<List<WeightRecord>> {
        return weightRecordDao.getWeightRecordsBetweenDates(startDate, endDate)
    }
    
    // Function to get latest weight record directly from database
    suspend fun getLatestWeightRecord(): WeightRecord? {
        // Use a direct query to get the latest weight record as a non-LiveData value
        return weightRecordDao.getLatestWeightRecordDirect()
    }
    
    // Function to get all weight records directly (not as LiveData)
    suspend fun getAllWeightRecords(): List<WeightRecord> {
        return weightRecordDao.getAllWeightRecordsDirect()
    }

    fun getAllWeightRecordsFlow(): Flow<List<WeightRecord>> = weightRecordDao.getAllWeightRecordsFlow()

    suspend fun getWeightRecord(id: Long): WeightRecord? = weightRecordDao.getWeightRecord(id)

    /**
     * Clear all weight records from the database
     */
    suspend fun clearAll() {
        SleepFastTrackerApplication.addDebugLog("WeightRecordRepository", "Clearing all weight records")
        // Add a deleteAll query in the DAO if needed
        val records = getAllWeightRecords()
        for (record in records) {
            delete(record)
        }
    }
} 