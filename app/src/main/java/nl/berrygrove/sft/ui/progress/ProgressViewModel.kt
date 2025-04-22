package nl.berrygrove.sft.ui.progress

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.SleepRecord
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class ProgressViewModel(
    private val weightRepository: WeightRecordRepository,
    private val fastingRepository: FastingRepository,
    private val sleepRepository: SleepRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {
    
    // Weight progress
    private val _weightDifference = MutableLiveData<Double>()
    val weightDifference: LiveData<Double> = _weightDifference
    
    private val _weightProgress = MutableLiveData<Int>()
    val weightProgress: LiveData<Int> = _weightProgress
    
    private val _totalWeightLoss = MutableLiveData<Float>()
    val totalWeightLoss: LiveData<Float> = _totalWeightLoss
    
    // Weight and BMI chart data
    private val _weightRecords = MutableLiveData<List<WeightRecord>>()
    val weightRecords: LiveData<List<WeightRecord>> = _weightRecords
    
    private val _bmiRecords = MutableLiveData<List<Pair<LocalDateTime, Float>>>()
    val bmiRecords: LiveData<List<Pair<LocalDateTime, Float>>> = _bmiRecords
    
    // Fasting progress
    private val _fastingCompletion = MutableLiveData<Int>()
    val fastingCompletion: LiveData<Int> = _fastingCompletion
    
    private val _fastingProgress = MutableLiveData<Int>()
    val fastingProgress: LiveData<Int> = _fastingProgress
    
    // Sleep progress
    private val _sleepQualityImprovement = MutableLiveData<Int>()
    val sleepQualityImprovement: LiveData<Int> = _sleepQualityImprovement
    
    private val _sleepProgress = MutableLiveData<Int>()
    val sleepProgress: LiveData<Int> = _sleepProgress
    
    // Added new bedtime adherence metrics
    private val _bedtimeAdherencePercentage = MutableLiveData<Int>()
    val bedtimeAdherencePercentage: LiveData<Int> = _bedtimeAdherencePercentage
    
    private val _sleepTrend = MutableLiveData<SleepTrend>()
    val sleepTrend: LiveData<SleepTrend> = _sleepTrend
    
    // Track if we have enough sleep data (7+ days)
    private val _hasEnoughSleepData = MutableLiveData<Boolean>()
    val hasEnoughSleepData: LiveData<Boolean> = _hasEnoughSleepData
    
    // Added bedtime warning flag
    private val _shouldShowBedtimeWarning = MutableLiveData<Boolean>()
    val shouldShowBedtimeWarning: LiveData<Boolean> = _shouldShowBedtimeWarning
    
    // User height for BMI calculations
    private var userHeight: Float = 175f  // Default value
    
    init {
        viewModelScope.launch {
            // Load user settings first since it's needed for BMI calculation
            loadUserSettings()
            
            // Load data in parallel using multiple launch blocks
            launch(Dispatchers.IO) { loadWeightAndBmiData() }
            launch(Dispatchers.IO) { loadFastingData() }
            launch(Dispatchers.IO) { loadSleepData() }
            launch(Dispatchers.IO) { checkBedtimeWarning() }
        }
    }
    
    private suspend fun loadUserSettings() {
        withContext(Dispatchers.IO) {
            val settings = userSettingsRepository.getUserSettings()
            settings?.let {
                userHeight = it.height
            }
        }
    }
    
    private suspend fun loadWeightAndBmiData() {
        try {
            // Load weight data directly from repository to ensure most recent data
            val weightRecords = weightRepository.getAllWeightRecords()
            
            if (weightRecords.isNotEmpty()) {
                // Sort records by timestamp for proper display
                val sortedRecords = weightRecords.sortedBy { it.timestamp }
                _weightRecords.postValue(sortedRecords)
                
                // For single record, duplicate it to show a point on the chart
                val recordsForChart = if (sortedRecords.size == 1) {
                    // Clone the record with a timestamp 1 day earlier to create a flat line
                    val original = sortedRecords.first()
                    val earlierTimestamp = original.timestamp.minusDays(1)
                    val clonedRecord = WeightRecord(0, original.weight, earlierTimestamp)
                    listOf(clonedRecord, original)
                } else {
                    sortedRecords
                }
                
                // Calculate BMI records
                val bmiData = recordsForChart.map { record ->
                    val heightInMeters = userHeight / 100
                    val bmi = record.weight / (heightInMeters * heightInMeters)
                    Pair(record.timestamp, bmi)
                }.sortedBy { it.first }
                _bmiRecords.postValue(bmiData)
                
                // Calculate weight difference
                val firstWeight = recordsForChart.first().weight
                val lastWeight = recordsForChart.last().weight
                val difference = lastWeight - firstWeight
                _weightDifference.postValue(difference.toDouble())
                
                // Set total weight loss/gain
                val totalChange = abs(difference)
                _totalWeightLoss.postValue(totalChange)
                
                // Calculate progress towards goal
                // For demo, assuming goal is to lose 5kg
                val goalWeight = firstWeight - 5
                val progressPercentage = if (firstWeight != lastWeight) {
                    ((firstWeight - lastWeight) / (firstWeight - goalWeight) * 100).toInt()
                } else {
                    0 // No progress if weight hasn't changed
                }
                _weightProgress.postValue(progressPercentage.coerceIn(0, 100))
            } else {
                _weightDifference.postValue(0.0)
                _totalWeightLoss.postValue(0f)
                _weightProgress.postValue(0)
                _weightRecords.postValue(emptyList())
                _bmiRecords.postValue(emptyList())
            }
        } catch (e: Exception) {
            // Log error and send empty data
            android.util.Log.e("ProgressViewModel", "Error loading weight data", e)
            _weightRecords.postValue(emptyList())
            _bmiRecords.postValue(emptyList())
        }
    }
    
    private suspend fun loadFastingData() {
        try {
            // Get user settings to determine eating window hours
            val settings = userSettingsRepository.getUserSettings()
            val eatingWindowHours = settings?.eatingWindowHours ?: 8 // Default to 8 if not set
            
            // Calculate minimum fasting duration for a successful fast
            // Same formula as in StreakCalculator: 24 hours - eating window - 30 minutes
            val minSuccessfulFastHours = 24 - eatingWindowHours - 0.5f
            val minSuccessfulFastMinutes = (minSuccessfulFastHours * 60).toLong()
            
            // Get all fasting records directly
            val fastingRecords = fastingRepository.allFastingRecords.firstOrNull() ?: emptyList()
            
            if (fastingRecords.isNotEmpty()) {
                // Sort records from newest to oldest
                val sortedRecords = fastingRecords.sortedByDescending { it.timestamp }
                
                // Count total completed fasting cycles
                var totalCycles = 0
                var successfulCycles = 0
                
                // Determine start index for checking fasts
                var currentIndex = if (sortedRecords[0].state) 1 else 0
                
                // Iterate through records to find and evaluate completed fast cycles
                while (currentIndex + 1 < sortedRecords.size) {
                    // Get the current and next record to analyze
                    val currentRecord = sortedRecords[currentIndex]
                    val nextRecord = sortedRecords[currentIndex + 1]
                    
                    // Skip incomplete or invalid fast cycles
                    if (currentRecord.state == nextRecord.state) {
                        // Two consecutive records with the same state (invalid)
                        currentIndex++
                        continue
                    }
                    
                    // Get the fast start and fast end records
                    val fastEndRecord: FastingRecord
                    val fastStartRecord: FastingRecord
                    
                    if (!currentRecord.state && nextRecord.state) {
                        // Current record is fast end (false), next record is fast start (true)
                        fastEndRecord = currentRecord
                        fastStartRecord = nextRecord
                        
                        // This is a complete cycle, count it
                        totalCycles++
                        
                        // Calculate duration of the fasting period
                        val fastDuration = Duration.between(fastStartRecord.timestamp, fastEndRecord.timestamp)
                        
                        // Check if this fast was successful
                        if (fastDuration.toMinutes() >= minSuccessfulFastMinutes) {
                            successfulCycles++
                        }
                        
                        // Move to next pair
                        currentIndex += 2
                    } else {
                        // Current record is fast start (true), next record is fast end (false)
                        // This is out of order for a proper cycle. Skip to the next pair.
                        currentIndex += 2
                    }
                }
                
                // Calculate completion rate as percentage of successful cycles
                val completionRate = if (totalCycles > 0) {
                    (successfulCycles * 100) / totalCycles
                } else {
                    0
                }
                
                _fastingCompletion.postValue(completionRate)
                _fastingProgress.postValue(completionRate)
                
                // Add debug log
                android.util.Log.d("ProgressViewModel", "Fasting progress: $successfulCycles successful out of $totalCycles total cycles ($completionRate%)")
            } else {
                _fastingCompletion.postValue(0)
                _fastingProgress.postValue(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("ProgressViewModel", "Error calculating fasting progress", e)
            _fastingCompletion.postValue(0)
            _fastingProgress.postValue(0)
        }
    }
    
    private suspend fun loadSleepData() {
        // Load sleep data
        val sleepRecordsFlow = sleepRepository.allSleepRecords
        val sleepRecords = sleepRecordsFlow.firstOrNull()
        
        if (sleepRecords != null && sleepRecords.size >= 7) {
            // Compare first week average to latest week
            val firstWeekRecords = sleepRecords.take(7)
            val latestWeekRecords = sleepRecords.takeLast(7)
            
            val firstWeekQuality = firstWeekRecords.count { record -> record.onTime }.toDouble() / firstWeekRecords.size * 100
            val latestWeekQuality = latestWeekRecords.count { record -> record.onTime }.toDouble() / latestWeekRecords.size * 100
            
            val improvement = latestWeekQuality.toInt() - firstWeekQuality.toInt()
            _sleepQualityImprovement.postValue(improvement)
            
            // Set progress (0-100%)
            _sleepProgress.postValue(latestWeekQuality.toInt())
            
            // Set bedtime adherence percentage (from latest week)
            _bedtimeAdherencePercentage.postValue(latestWeekQuality.toInt())
            
            // Determine trend by comparing recent periods
            if (sleepRecords.size >= 14) {
                val prevWeekRecords = sleepRecords.subList(
                    sleepRecords.size - 14,
                    sleepRecords.size - 7
                )
                val prevWeekQuality = prevWeekRecords.count { it.onTime }.toDouble() / prevWeekRecords.size * 100
                
                _sleepTrend.postValue(when {
                    latestWeekQuality > prevWeekQuality + 5 -> SleepTrend.IMPROVING
                    latestWeekQuality < prevWeekQuality - 5 -> SleepTrend.DECLINING
                    else -> SleepTrend.STABLE
                })
            } else {
                _sleepTrend.postValue(SleepTrend.STABLE)
            }
            
            // Track if we have enough sleep data (7+ days)
            _hasEnoughSleepData.postValue(sleepRecords.size >= 7)
        } else {
            _sleepQualityImprovement.postValue(0)
            _sleepProgress.postValue(0)
            _bedtimeAdherencePercentage.postValue(0)
            _sleepTrend.postValue(SleepTrend.STABLE)
            _hasEnoughSleepData.postValue(false)
        }
    }
    
    /**
     * Refresh all data
     */
    fun loadData() {
        viewModelScope.launch {
            launch(Dispatchers.IO) { loadWeightAndBmiData() }
            launch(Dispatchers.IO) { loadFastingData() }
            launch(Dispatchers.IO) { loadSleepData() }
        }
    }
    
    /**
     * Check if it's past the user's bedtime but within a 4-hour window
     */
    private suspend fun checkBedtimeWarning() {
        // Get user settings for bedtime
        val userSettings = userSettingsRepository.getUserSettings()
        
        userSettings?.let { settings ->
            // Parse the bedtime from settings (e.g., "22:30")
            val bedTime = LocalTime.parse(settings.bedTime, DateTimeFormatter.ofPattern("HH:mm"))
            
            // Get current time
            val now = LocalTime.now()
            
            // Calculate the bedtime plus 4 hours (the warning window)
            var bedTimePlus4Hours = bedTime.plusHours(4)
            
            // Handle case when bedtime + 4 hours goes to the next day
            val isAfterBedtime = if (bedTimePlus4Hours.isBefore(bedTime)) {
                // If bedtime + 4 hours rolls over to next day
                now.isAfter(bedTime) || now.isBefore(bedTimePlus4Hours)
            } else {
                // Normal case, both times on same day
                now.isAfter(bedTime) && now.isBefore(bedTimePlus4Hours)
            }
            
            // Update LiveData
            _shouldShowBedtimeWarning.postValue(isAfterBedtime)
        }
    }
    
    // BMI category definitions
    companion object {
        const val BMI_UNDERWEIGHT = 18.5f
        const val BMI_NORMAL = 25f
        const val BMI_OVERWEIGHT = 30f
    }
    
    // New enum to represent sleep trend
    enum class SleepTrend { IMPROVING, STABLE, DECLINING }
}

class ProgressViewModelFactory(
    private val weightRepository: WeightRecordRepository,
    private val fastingRepository: FastingRepository,
    private val sleepRepository: SleepRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProgressViewModel(
                weightRepository, 
                fastingRepository, 
                sleepRepository,
                userSettingsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 