package nl.berrygrove.sft.repository

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.dao.SleepRecordDao
import nl.berrygrove.sft.data.model.SleepRecord
import nl.berrygrove.sft.utils.StreakCalculator
import java.time.LocalDateTime
import java.time.LocalDate

/**
 * Repository for accessing and managing SleepRecord data.
 */
class SleepRepository(private val sleepRecordDao: SleepRecordDao) {

    val allSleepRecords: Flow<List<SleepRecord>> = sleepRecordDao.getAllSleepRecords()
    val latestSleepRecord: Flow<SleepRecord?> = sleepRecordDao.getLatestSleepRecord()

    fun getSleepRecordsInRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<SleepRecord>> {
        return sleepRecordDao.getSleepRecordsInRange(startDate, endDate)
    }

    suspend fun insertSleepRecord(onTime: Boolean) {
        val sleepRecord = SleepRecord(
            onTime = onTime,
            timestamp = LocalDateTime.now()
        )
        sleepRecordDao.insert(sleepRecord)
    }
    
    suspend fun insertSleepRecord(sleepRecord: SleepRecord) {
        sleepRecordDao.insert(sleepRecord)
    }

    suspend fun updateSleepRecord(sleepRecord: SleepRecord) {
        sleepRecordDao.update(sleepRecord)
    }

    suspend fun deleteSleepRecord(sleepRecord: SleepRecord) {
        sleepRecordDao.delete(sleepRecord)
    }

    suspend fun clearAll() {
        sleepRecordDao.deleteAll()
    }
    
    // Function to check if bedtime has been answered today
    suspend fun hasAnsweredBedtimeToday(): Boolean {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay()
        val endOfDay = today.plusDays(1).atStartOfDay().minusNanos(1)
        
        val records = sleepRecordDao.getSleepRecordsInDateRange(startOfDay, endOfDay)
        return records.isNotEmpty()
    }
    
    // Get all sleep records directly (not as Flow)
    suspend fun getAllSleepRecordsDirect(): List<SleepRecord> {
        return sleepRecordDao.getAllSleepRecordsDirect()
    }
    
    // Function to calculate bedtime streak using the StreakCalculator utility
    suspend fun calculateBedtimeStreak(): Int {
        val sleepRecords = sleepRecordDao.getAllSleepRecordsDirect()
        val streak = StreakCalculator.calculateBedtimeStreak(sleepRecords)
        
        // Update max streak if needed
        if (streak > maxBedtimeStreak) {
            maxBedtimeStreak = streak
            SleepFastTrackerApplication.addDebugLog("SleepRepository", "New max bedtime streak: $maxBedtimeStreak")
        }
        
        return streak
    }

    // Maximum recorded bedtime streak (stored in preferences or a dedicated table)
    private var maxBedtimeStreak: Int = 0
    
    /**
     * Insert a new sleep record
     */
    suspend fun insert(sleepRecord: SleepRecord): Long {
        SleepFastTrackerApplication.addDebugLog("SleepRepository", "Inserting sleep record: $sleepRecord")
        val id = sleepRecordDao.insert(sleepRecord)
        
        // After inserting, update max streak
        updateMaxStreakIfNeeded()
        
        return id
    }
    
    /**
     * Update an existing sleep record
     */
    suspend fun update(sleepRecord: SleepRecord) {
        SleepFastTrackerApplication.addDebugLog("SleepRepository", "Updating sleep record: $sleepRecord")
        sleepRecordDao.update(sleepRecord)
        
        // After updating, recalculate max streak
        updateMaxStreakIfNeeded()
    }
    
    /**
     * Delete a sleep record
     */
    suspend fun delete(sleepRecord: SleepRecord) {
        SleepFastTrackerApplication.addDebugLog("SleepRepository", "Deleting sleep record: $sleepRecord")
        sleepRecordDao.delete(sleepRecord)
    }
    
    /**
     * Get sleep records between the given dates
     */
    fun getSleepRecordsBetweenDates(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<SleepRecord>> {
        return sleepRecordDao.getSleepRecordsInRange(startDate, endDate)
    }
    
    /**
     * Get the maximum bedtime streak ever achieved
     */
    suspend fun getMaxBedtimeStreak(): Int? {
        // First calculate current streak to make sure max is updated
        val currentStreak = calculateBedtimeStreak()
        return maxBedtimeStreak.takeIf { it > 0 } ?: currentStreak
    }
    
    /**
     * Update max streak if the current streak is higher
     */
    private suspend fun updateMaxStreakIfNeeded() {
        val currentStreak = calculateBedtimeStreak()
        if (currentStreak > maxBedtimeStreak) {
            maxBedtimeStreak = currentStreak
            SleepFastTrackerApplication.addDebugLog("SleepRepository", "Updated max bedtime streak: $maxBedtimeStreak")
        }
    }
} 