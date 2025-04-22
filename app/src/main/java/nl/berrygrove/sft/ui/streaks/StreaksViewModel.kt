package nl.berrygrove.sft.ui.streaks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.Achievement
import nl.berrygrove.sft.data.model.SleepRecord
import nl.berrygrove.sft.data.model.UserSettings
import nl.berrygrove.sft.repository.AchievementRepository
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import nl.berrygrove.sft.utils.StreakCalculator
import kotlin.math.abs
import kotlin.math.max

class StreaksViewModel(
    private val sleepRepository: SleepRepository,
    private val fastingRepository: FastingRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val weightRecordRepository: WeightRecordRepository,
    private val achievementRepository: AchievementRepository
) : ViewModel() {
    
    private val _bedtimeStreak = MutableLiveData<Int>()
    val bedtimeStreak: LiveData<Int> = _bedtimeStreak
    
    private val _fastingStreak = MutableLiveData<Int>()
    val fastingStreak: LiveData<Int> = _fastingStreak
    
    private val _userSettings = MutableLiveData<UserSettings?>()
    val userSettings: LiveData<UserSettings?> = _userSettings
    
    private val _overallScore = MutableLiveData<Int>()
    val overallScore: LiveData<Int> = _overallScore
    
    private val _weightLost = MutableLiveData<Float>()
    val weightLost: LiveData<Float> = _weightLost
    
    // Achievement-related LiveData
    private val _achievements = MutableLiveData<List<Achievement>>()
    val achievements: LiveData<List<Achievement>> = _achievements
    
    private val _achievementsByCategory = MutableLiveData<Map<String, List<Achievement>>>()
    val achievementsByCategory: LiveData<Map<String, List<Achievement>>> = _achievementsByCategory
    
    private val _selectedCategory = MutableLiveData<String>("all")
    val selectedCategory: LiveData<String> = _selectedCategory
    
    private val _achievedAchievementsCount = MutableLiveData<Int>()
    val achievedAchievementsCount: LiveData<Int> = _achievedAchievementsCount
    
    private val _totalAchievements = MutableLiveData<Int>()
    val totalAchievements: LiveData<Int> = _totalAchievements
    
    private val _achievementPoints = MutableLiveData<Int>()
    val achievementPoints: LiveData<Int> = _achievementPoints
    
    init {
        viewModelScope.launch {
            // First ensure achievements exist
            achievementRepository.ensureAchievementsExist()
            
            loadUserSettings()
            calculateBedtimeStreak()
            calculateFastingStreak()
            calculateWeightLoss()
            loadAchievements()
            
            // Check and update achievements based on current stats
            checkAndUpdateAchievements()
        }
        
        // Observe changes to recalculate score
        bedtimeStreak.observeForever { _ -> recalculateScore() }
        fastingStreak.observeForever { _ -> recalculateScore() }
        weightLost.observeForever { _ -> recalculateScore() }
        achievementPoints.observeForever { _ -> recalculateScore() }
    }
    
    /**
     * Set the selected category for filtering achievements
     */
    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
        filterAchievementsByCategory()
    }
    
    /**
     * Filter achievements by the selected category
     */
    private fun filterAchievementsByCategory() {
        val category = _selectedCategory.value ?: "all"
        val allAchievements = _achievementsByCategory.value ?: emptyMap()
        
        val filteredAchievements = if (category == "all") {
            // For "all" tab, show only earned/unlocked achievements
            allAchievements.values.flatten().filter { it.achieved }
        } else {
            // For specific category tabs, show all achievements in that category
            allAchievements[category] ?: emptyList()
        }
        
        _achievements.postValue(filteredAchievements)
    }
    
    /**
     * Load all achievements and organize them by category
     */
    private suspend fun loadAchievements() {
        // Get all achievements
        val allAchievements = achievementRepository.allAchievements.first()
        _totalAchievements.postValue(allAchievements.size)
        
        // Get achieved achievements count
        val achievedCount = achievementRepository.achievedCount.first()
        _achievedAchievementsCount.postValue(achievedCount)
        
        // Get total achievement points
        val points = achievementRepository.totalPoints.first() ?: 0
        _achievementPoints.postValue(points)
        
        // Organize achievements by category
        val achievementMap = allAchievements.groupBy { it.category }
        _achievementsByCategory.postValue(achievementMap)
        
        // Apply filtering based on current selected category instead of showing all achievements
        filterAchievementsByCategory()
    }
    
    /**
     * Check and update achievement status based on current stats
     */
    private suspend fun checkAndUpdateAchievements() {
        // Get current streaks
        val currentSleepStreak = bedtimeStreak.value ?: 0
        val currentFastingStreak = fastingStreak.value ?: 0
        val weightLostValue = weightLost.value ?: 0f
        
        // Get max historical streaks (this requires adding these methods to the repositories)
        val maxSleepStreak = sleepRepository.getMaxBedtimeStreak() ?: currentSleepStreak
        val maxFastingStreak = fastingRepository.getMaxFastingStreak() ?: currentFastingStreak
        
        // Log for debugging
        SleepFastTrackerApplication.addDebugLog("StreaksViewModel", 
            "Achievement check with - Current sleep: $currentSleepStreak, Max: $maxSleepStreak, " +
            "Current fasting: $currentFastingStreak, Max: $maxFastingStreak, " +
            "Weight lost: $weightLostValue kg")
        
        // Use the maximum values for achievements
        achievementRepository.checkAndUnlockSleepAchievements(maxSleepStreak)
        achievementRepository.checkAndUnlockFastingAchievements(maxFastingStreak)
        achievementRepository.checkAndUnlockWeightAchievements(weightLostValue)
        
        // Refresh achievements data
        loadAchievements()
    }
    
    private fun recalculateScore() {
        val sleepStreakValue = bedtimeStreak.value ?: 0
        val fastingStreakValue = fastingStreak.value ?: 0
        val weightLostValue = weightLost.value ?: 0f
        val achievementPointsValue = achievementPoints.value ?: 0
        
        // Calculate the score based on the formula: ((sleep streak + fasting streak) * 5) + (weight lost in KG * 10) + achievement points
        val streakComponent = (sleepStreakValue + fastingStreakValue) * 5
        val weightComponent = (weightLostValue * 10).toInt()
        
        val score = streakComponent + weightComponent + achievementPointsValue
        _overallScore.postValue(score)
    }
    
    private suspend fun loadUserSettings() {
        val settings = userSettingsRepository.getUserSettings()
        _userSettings.postValue(settings)
    }
    
    private suspend fun calculateBedtimeStreak() {
        val sleepRecords = sleepRepository.getAllSleepRecordsDirect()
        val streak = StreakCalculator.calculateBedtimeStreak(sleepRecords)
        _bedtimeStreak.postValue(streak)
    }
    
    private suspend fun calculateFastingStreak() {
        val settings = userSettingsRepository.getUserSettings()
        val eatingWindowHours = settings?.eatingWindowHours ?: 8 // Default to 8 if not set
        val streak = fastingRepository.calculateFastingStreak(eatingWindowHours)
        _fastingStreak.postValue(streak)
    }
    
    private suspend fun calculateWeightLoss() {
        // Use direct repository call instead of LiveData value
        val weightRecords = weightRecordRepository.getAllWeightRecords()
        SleepFastTrackerApplication.addDebugLog("StreaksViewModel", "Calculating weight loss, found ${weightRecords.size} records directly from repository")
        
        if (weightRecords.size > 1) {
            // Sort by timestamp to ensure correct order
            val sortedRecords = weightRecords.sortedBy { it.timestamp }
            
            // Get the starting weight (first recorded weight)
            val startWeight = sortedRecords.first().weight
            
            // Initialize variables for tracking maximum weight loss
            var maxWeightLoss = 0f
            var lowestWeight = startWeight
            
            // Iterate through records to find maximum weight loss from starting weight
            for (record in sortedRecords.drop(1)) { // Skip the first record
                if (record.weight < lowestWeight) {
                    lowestWeight = record.weight
                    val currentLoss = startWeight - lowestWeight
                    if (currentLoss > maxWeightLoss) {
                        maxWeightLoss = currentLoss
                    }
                }
            }
            
            // Also calculate current weight loss
            val currentWeight = sortedRecords.last().weight
            val currentWeightLoss = max(0f, startWeight - currentWeight)
            
            SleepFastTrackerApplication.addDebugLog("StreaksViewModel", 
                "Weight calculation: Start weight: $startWeight kg, Lowest weight: $lowestWeight kg, " +
                "Current weight: $currentWeight kg, Max loss: $maxWeightLoss kg, Current loss: $currentWeightLoss kg")
            
            // Use the maximum historical weight loss for achievements
            _weightLost.postValue(maxWeightLoss)
        } else {
            // No weight loss if we don't have enough records
            SleepFastTrackerApplication.addDebugLog("StreaksViewModel", "Not enough weight records to calculate loss")
            _weightLost.postValue(0f)
        }
    }
    
    /**
     * Public method to check achievements and refresh data
     * Called when StreaksActivity is resumed
     */
    fun checkAchievementsAndRefresh() {
        viewModelScope.launch {
            // Make sure achievements exist
            achievementRepository.ensureAchievementsExist()
            
            // Recalculate data
            calculateWeightLoss()
            
            // Get current and max streaks
            val currentSleepStreak = bedtimeStreak.value ?: 0
            val currentFastingStreak = fastingStreak.value ?: 0
            val maxSleepStreak = sleepRepository.getMaxBedtimeStreak() ?: currentSleepStreak
            val maxFastingStreak = fastingRepository.getMaxFastingStreak() ?: currentFastingStreak
            val weightLostValue = weightLost.value ?: 0f
            
            // Log values for debugging
            SleepFastTrackerApplication.addDebugLog("StreaksViewModel", 
                "Manual achievement check - Current sleep: $currentSleepStreak, Max: $maxSleepStreak, " +
                "Current fasting: $currentFastingStreak, Max: $maxFastingStreak, " +
                "Weight lost: $weightLostValue kg")
            
            // Check achievements using max streaks
            achievementRepository.checkAndUnlockSleepAchievements(maxSleepStreak)
            achievementRepository.checkAndUnlockFastingAchievements(maxFastingStreak)
            achievementRepository.checkAndUnlockWeightAchievements(weightLostValue)
            
            // Refresh achievements data
            loadAchievements()
        }
    }
}

class StreaksViewModelFactory(
    private val sleepRepository: SleepRepository,
    private val fastingRepository: FastingRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val weightRecordRepository: WeightRecordRepository,
    private val achievementRepository: AchievementRepository
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreaksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StreaksViewModel(
                sleepRepository,
                fastingRepository,
                userSettingsRepository,
                weightRecordRepository,
                achievementRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 