package nl.berrygrove.sft.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Utility class for backfilling delta_minutes values in existing fasting records.
 */
class FastingDeltaBackfill(
    private val app: SleepFastTrackerApplication?,
    private val fastingRepository: FastingRepository,
    private val userSettingsRepository: UserSettingsRepository
) {
    
    private val TAG = "FastingDeltaBackfill"
    
    /**
     * Process all fasting records in the database and populate delta_minutes values.
     * Also populates eating_window_minutes values in eating records.
     * @return Number of records updated
     */
    suspend fun backfillDeltaMinutes(): Int = withContext(Dispatchers.IO) {
        var updatedCount = 0
        
        try {
            // Get eating window hours from user settings
            val settings = userSettingsRepository.getUserSettings()
            val eatingWindowHours = settings?.eatingWindowHours ?: 8
            val eatingWindowMinutes = eatingWindowHours * 60
            val targetFastingMinutes = ((24 - eatingWindowHours) * 60).toLong()
            
            // Get all fasting records
            val records = fastingRepository.allFastingRecords.first()
            if (records.isEmpty()) return@withContext 0
            
            // Sort them by timestamp (oldest first)
            val sortedRecords = records.sortedBy { record -> record.timestamp }
            
            // Find pairs of fasting -> eating records to calculate delta_minutes
            var currentIndex = 0
            while (currentIndex < sortedRecords.size - 1) {
                val currentRecord = sortedRecords[currentIndex]
                val nextRecord = sortedRecords[currentIndex + 1]
                
                // Look for a fasting record (true) followed by an eating record (false)
                if (currentRecord.state && !nextRecord.state) {
                    // Calculate fasting duration
                    val fastingStartTime = currentRecord.timestamp
                    val fastingEndTime = nextRecord.timestamp
                    val actualFastMinutes = ChronoUnit.MINUTES.between(fastingStartTime, fastingEndTime)
                    
                    // Calculate delta from target
                    val deltaMinutes = (actualFastMinutes - targetFastingMinutes).toInt()
                    
                    // Update the eating record with the delta_minutes value and eating_window_minutes
                    val updatedRecord = nextRecord.copy(
                        delta_minutes = deltaMinutes,
                        eating_window_minutes = eatingWindowMinutes
                    )
                    fastingRepository.updateFastingRecord(updatedRecord)
                    updatedCount++
                }
                
                currentIndex++
            }
            
            logDebug("Updated $updatedCount fasting records with delta_minutes and eating_window_minutes values")
        } catch (e: Exception) {
            logDebug("Error during backfill: ${e.message}")
        }
        
        return@withContext updatedCount
    }
    
    /**
     * Helper method for logging that works with or without app instance
     */
    private fun logDebug(message: String) {
        if (app != null) {
            SleepFastTrackerApplication.addDebugLog(TAG, message)
        } else {
            Log.d(TAG, message)
        }
    }
} 