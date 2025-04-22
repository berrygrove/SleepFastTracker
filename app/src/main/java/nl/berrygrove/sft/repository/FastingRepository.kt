package nl.berrygrove.sft.repository

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.dao.FastingRecordDao
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.utils.StreakCalculator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Repository for accessing and managing FastingRecord data.
 */
class FastingRepository(
    private val fastingRecordDao: FastingRecordDao,
    private val userSettingsRepository: UserSettingsRepository? = null
) {

    val allFastingRecords: Flow<List<FastingRecord>> = fastingRecordDao.getAllFastingRecords()
    val latestFastingRecord: Flow<FastingRecord?> = fastingRecordDao.getLatestFastingRecord()

    // Result class for validation
    sealed class FastingOperationResult {
        object Success : FastingOperationResult()
        data class Error(val message: String) : FastingOperationResult()
    }

    fun getFastingRecordsInRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<FastingRecord>> {
        return fastingRecordDao.getFastingRecordsInRange(startDate, endDate)
    }

    /**
     * Get fasting records between the given dates (alias for getFastingRecordsInRange)
     */
    fun getFastingRecordsBetweenDates(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<FastingRecord>> {
        return fastingRecordDao.getFastingRecordsInRange(startDate, endDate)
    }

    /**
     * Insert a new fasting record with validation to enforce one completed fast per calendar day
     * Multi-day fasts (where start_date < end_date - 1 day) are an exception to this rule
     * 
     * @param state The fasting state (true = fasting, false = eating)
     * @return A FastingOperationResult indicating success or showing an error message
     */
    suspend fun insertFastingRecord(state: Boolean): FastingOperationResult {
        // If ending a fast (state = false), we need to validate
        if (!state) {
            // Check if ending a fast on this calendar day is allowed
            val validationResult = validateFastEnd()
            if (validationResult is FastingOperationResult.Error) {
                return validationResult
            }
        }
        
        // Variables to store record data
        val deltaMinutes: Int?
        val eatingWindowMinutes: Int?
        
        // If toggling from fasting to eating, calculate delta_minutes and capture eating window
        if (!state) { // false = eating, which means ending a fasting period
            deltaMinutes = calculateDeltaMinutes()
            
            // Get current eating window setting and convert to minutes
            val settings = userSettingsRepository?.getUserSettings()
            val eatingWindowHours = settings?.eatingWindowHours ?: 8 // Default to 8 if not set
            eatingWindowMinutes = eatingWindowHours * 60
        } else {
            deltaMinutes = null
            eatingWindowMinutes = null
        }

        val fastingRecord = FastingRecord(
            state = state,
            timestamp = LocalDateTime.now(),
            delta_minutes = deltaMinutes,
            eating_window_minutes = eatingWindowMinutes
        )
        fastingRecordDao.insert(fastingRecord)
        
        return FastingOperationResult.Success
    }

    /**
     * Validate that ending a fast on the current calendar day is allowed
     * according to the business rule: only one completed fast per calendar day,
     * unless it's a multi-day fast
     * 
     * @return A validation result
     */
    private suspend fun validateFastEnd(): FastingOperationResult {
        // Get the latest fasting record - needed to determine if we're currently fasting
        val latestRecord = getLatestFastingRecord()
        
        // If there's no record, or we're not in a fasting state, we can't end a fast
        if (latestRecord == null || !latestRecord.state) {
            return FastingOperationResult.Error("You are not currently fasting.")
        }
        
        // Get today's date
        val today = LocalDate.now()
        val fastingStartDate = latestRecord.timestamp.toLocalDate()
        
        // Check if this is a multi-day fast (start date < end date - 1 day)
        val isMultiDayFast = fastingStartDate.isBefore(today.minusDays(1))
        
        // If it's a multi-day fast, allow it
        if (isMultiDayFast) {
            SleepFastTrackerApplication.addDebugLog("FastingRepository", "Multi-day fast detected, allowing end record")
            return FastingOperationResult.Success
        }
        
        // Check if there's already a completed fast that ended today
        val recordsEndingToday = fastingRecordDao.getFastingRecordsEndingOnDate(today).first()
        
        // If there are already completed fasts ending today, reject
        if (recordsEndingToday.any { !it.state }) {
            return FastingOperationResult.Error(
                "You've already ended a fast today. For a multi-day fast, simply wait until you finish."
            )
        }
        
        return FastingOperationResult.Success
    }

    suspend fun insertFastingRecord(fastingRecord: FastingRecord) {
        fastingRecordDao.insert(fastingRecord)
        
        // Log that a new record is created
        // Note: Delta backfill should be called by the ViewModel/UI layer
        if (!fastingRecord.state) {
            SleepFastTrackerApplication.addDebugLog("FastingRepository", "Inserted eating record - consider running delta backfill")
        }
    }

    /**
     * Calculates the delta (in minutes) between actual fasting time and target fasting window
     * @return Integer representing minutes over/under target, or null if previous fasting record not found
     */
    private suspend fun calculateDeltaMinutes(): Int? {
        // Get the latest fasting record
        val latestRecord = getLatestFastingRecord()
        
        // If no record exists or the latest record is not a fasting record (state=true), return null
        if (latestRecord == null || !latestRecord.state) {
            SleepFastTrackerApplication.addDebugLog("FastingRepository", "No previous fasting record found for delta calculation")
            return null
        }
        
        // Get the timestamp of when fasting started
        val fastingStartTime = latestRecord.timestamp
        val nowTime = LocalDateTime.now()
        
        // Calculate actual fasting duration in minutes
        val actualFastMinutes = ChronoUnit.MINUTES.between(fastingStartTime, nowTime)
        
        // Get target fasting minutes based on user settings
        val targetFastingMinutes = calculateTargetFastingMinutes()
        
        // Calculate delta (positive = exceeded target, negative = fell short)
        return (actualFastMinutes - targetFastingMinutes).toInt()
    }
    
    /**
     * Calculate the target fasting window in minutes based on user settings
     */
    private suspend fun calculateTargetFastingMinutes(): Long {
        // Default eating window is 8 hours if we can't determine it
        var eatingWindowHours = 8
        
        // Try to get the user's actual eating window hours from settings
        if (userSettingsRepository != null) {
            val settings = userSettingsRepository.getUserSettings()
            eatingWindowHours = settings?.eatingWindowHours ?: eatingWindowHours
        }
        
        // Calculate fasting window minutes: (24h - eating_window_hours) * 60
        return ((24 - eatingWindowHours) * 60).toLong()
    }

    suspend fun updateFastingRecord(fastingRecord: FastingRecord) {
        fastingRecordDao.update(fastingRecord)
    }

    suspend fun deleteFastingRecord(fastingRecord: FastingRecord) {
        fastingRecordDao.delete(fastingRecord)
    }

    suspend fun clearAll() {
        fastingRecordDao.deleteAll()
    }

    // Function to calculate fasting streak
    suspend fun calculateFastingStreak(eatingWindowHours: Int): Int {
        // Get all fasting records and pass to streak calculator
        val fastingRecords = fastingRecordDao.getAllFastingRecordsDirect()
        
        // Use the new delta-based streak calculation algorithm
        val streak = StreakCalculator.calculateFastingStreakWithDelta(fastingRecords, eatingWindowHours)
        
        // Update max streak if needed
        if (streak > maxFastingStreak) {
            maxFastingStreak = streak
            SleepFastTrackerApplication.addDebugLog("FastingRepository", "New max fasting streak: $maxFastingStreak")
        }
        
        return streak
    }
    
    // Function to get latest fasting record (non-Flow version)
    suspend fun getLatestFastingRecord(): FastingRecord? {
        return latestFastingRecord.first()
    }

    // Maximum recorded fasting streak
    private var maxFastingStreak: Int = 0

    /**
     * Get the maximum fasting streak ever achieved
     */
    suspend fun getMaxFastingStreak(): Int? {
        return maxFastingStreak.takeIf { it > 0 }
    }

    /**
     * Update max streak if the current streak is higher
     */
    private suspend fun updateMaxStreakIfNeeded() {
        // Use a default eating window of 8 hours for this calculation
        val currentStreak = calculateFastingStreak(8)
        if (currentStreak > maxFastingStreak) {
            maxFastingStreak = currentStreak
            SleepFastTrackerApplication.addDebugLog("FastingRepository", "Updated max fasting streak: $maxFastingStreak")
        }
    }
} 