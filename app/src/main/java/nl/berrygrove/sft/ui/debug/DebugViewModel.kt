package nl.berrygrove.sft.ui.debug

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import kotlinx.coroutines.flow.first
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.Achievement
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.SleepRecord
import nl.berrygrove.sft.data.model.UserSettings
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.repository.AchievementRepository
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import nl.berrygrove.sft.utils.FastingDeltaBackfill

class DebugViewModel(
    private val userSettingsRepository: UserSettingsRepository,
    private val fastingRepository: FastingRepository,
    private val sleepRepository: SleepRepository,
    private val weightRepository: WeightRecordRepository,
    private val achievementRepository: AchievementRepository,
    private val application: SleepFastTrackerApplication? = null
) : ViewModel() {

    suspend fun getUserSettings(): UserSettings? {
        return userSettingsRepository.getUserSettings()
    }
    
    suspend fun getAllFastingRecords(): List<FastingRecord> {
        return try {
            fastingRepository.allFastingRecords.first()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun getAllSleepRecords(): List<SleepRecord> {
        return try {
            sleepRepository.allSleepRecords.first()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // LiveData to observe weight records
    val weightRecords: LiveData<List<WeightRecord>> = weightRepository.allWeightRecords
    
    // LiveData to observe achievements
    val achievements: LiveData<List<Achievement>> = liveData {
        emit(achievementRepository.allAchievements.first())
    }
    
    // Force refresh method for testing
    fun refreshWeightRecords(): LiveData<List<WeightRecord>> {
        return liveData {
            // Create a placeholder value in case the LiveData is null
            emit(emptyList())
            
            // Then emit the actual value
            val currentValue = weightRepository.allWeightRecords.value
            if (currentValue != null) {
                emit(currentValue)
            }
        }
    }
    
    suspend fun getAllWeightRecords(): List<WeightRecord> {
        // Since LiveData is challenging to directly access in a coroutine, 
        // we need this solution for the Debug screen
        val currentRecords = weightRecords.value ?: emptyList()
        
        // Log for debugging
        println("DEBUG: Retrieved ${currentRecords.size} weight records")
        for (record in currentRecords) {
            println("DEBUG: Weight record: ID=${record.id}, Weight=${record.weight}, Time=${record.timestamp}")
        }
        
        return currentRecords
    }
    
    suspend fun getAllAchievements(): List<Achievement> {
        return try {
            achievementRepository.allAchievements.first()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Recalculate weight achievements based on user's height and start weight
     * @return A pair with (success flag, message)
     */
    suspend fun recalculateWeightAchievements(): Pair<Boolean, String> {
        try {
            SleepFastTrackerApplication.addDebugLog("DebugViewModel", "Starting weight achievement recalculation")
            
            // Get user height from settings
            val userSettings = userSettingsRepository.getUserSettings()
            if (userSettings == null || userSettings.height <= 0f) {
                SleepFastTrackerApplication.addDebugLog("DebugViewModel", "Error: User height not available or invalid")
                return Pair(false, "Error: User height not available or invalid")
            }
            
            // Get all weight records
            val allWeightRecords = weightRepository.getAllWeightRecords()
            if (allWeightRecords.isEmpty()) {
                SleepFastTrackerApplication.addDebugLog("DebugViewModel", "Error: No weight records found")
                return Pair(false, "Error: No weight records found")
            }
            
            // Sort by timestamp to find first weight
            val sortedRecords = allWeightRecords.sortedBy { it.timestamp }
            val firstWeight = sortedRecords.first().weight
            
            SleepFastTrackerApplication.addDebugLog("DebugViewModel", 
                "Recalculating weight achievements with height: ${userSettings.height} cm and start weight: $firstWeight kg")
            
            // Recalculate achievements
            achievementRepository.recalculateWeightAchievements(userSettings.height, firstWeight)
            
            return Pair(true, "Success: Recalculated weight achievements based on height: ${userSettings.height} cm and start weight: $firstWeight kg")
        } catch (e: Exception) {
            val errorMsg = "Error recalculating weight achievements: ${e.message}"
            SleepFastTrackerApplication.addDebugLog("DebugViewModel", errorMsg)
            return Pair(false, errorMsg)
        }
    }
    
    /**
     * Run the fasting delta backfill process to calculate delta_minutes for existing records
     * @return Number of records updated or -1 if there was an error
     */
    suspend fun runFastingDeltaBackfill(): Int {
        return try {
            if (application != null) {
                // Use the application's runFastingDeltaBackfill method
                application.runFastingDeltaBackfill()
                // Since this runs asynchronously, we can't return the actual count
                // Return a placeholder value
                0
            } else {
                // Direct calculation if application is not available
                val backfill = FastingDeltaBackfill(
                    null, // We don't have the application instance
                    fastingRepository,
                    userSettingsRepository
                )
                backfill.backfillDeltaMinutes()
            }
        } catch (e: Exception) {
            SleepFastTrackerApplication.addDebugLog("DebugViewModel", "Error running backfill: ${e.message}")
            -1
        }
    }
}

class DebugViewModelFactory(
    private val userSettingsRepository: UserSettingsRepository,
    private val fastingRepository: FastingRepository,
    private val sleepRepository: SleepRepository,
    private val weightRepository: WeightRecordRepository,
    private val achievementRepository: AchievementRepository,
    private val application: SleepFastTrackerApplication? = null
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DebugViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DebugViewModel(
                userSettingsRepository,
                fastingRepository,
                sleepRepository,
                weightRepository,
                achievementRepository,
                application
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 