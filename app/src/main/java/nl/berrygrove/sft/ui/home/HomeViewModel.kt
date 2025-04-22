package nl.berrygrove.sft.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.SleepRecord
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import nl.berrygrove.sft.widget.FastingBedtimeWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import nl.berrygrove.sft.utils.StreakCalculator
import nl.berrygrove.sft.SleepFastTrackerApplication

class HomeViewModel(
    private val fastingRepository: FastingRepository,
    private val sleepRepository: SleepRepository,
    private val weightRepository: WeightRecordRepository,
    private val userSettingsRepository: UserSettingsRepository,
    application: Application
) : AndroidViewModel(application) {

    // Fasting state
    private val _currentFastingState = MutableLiveData<Boolean>()
    val currentFastingState: LiveData<Boolean> = _currentFastingState

    private val _fastingCountdown = MutableLiveData<String>()
    val fastingCountdown: LiveData<String> = _fastingCountdown
    
    private val _fastingProgress = MutableLiveData<Int>()
    val fastingProgress: LiveData<Int> = _fastingProgress

    private val _fastingStreak = MutableLiveData<Int>()
    val fastingStreak: LiveData<Int> = _fastingStreak
    
    // New LiveData for start and end times
    private val _fastingStartTime = MutableLiveData<String>()
    val fastingStartTime: LiveData<String> = _fastingStartTime
    
    private val _fastingEndTime = MutableLiveData<String>()
    val fastingEndTime: LiveData<String> = _fastingEndTime

    // Overall Score
    private val _overallScore = MutableLiveData<Int>()
    val overallScore: LiveData<Int> = _overallScore
    
    private val _weightLost = MutableLiveData<Float>()
    
    // Bedtime
    private val _bedtimeCountdown = MutableLiveData<String>()
    val bedtimeCountdown: LiveData<String> = _bedtimeCountdown
    
    private val _bedtimeProgress = MutableLiveData<Int>()
    val bedtimeProgress: LiveData<Int> = _bedtimeProgress
    
    private val _bedtimeCountdownLabel = MutableLiveData<String>()
    val bedtimeCountdownLabel: LiveData<String> = _bedtimeCountdownLabel

    private val _shouldShowBedtimeQuestion = MutableLiveData<Boolean>()
    val shouldShowBedtimeQuestion: LiveData<Boolean> = _shouldShowBedtimeQuestion

    private val _bedtimeResponseRemainingTime = MutableLiveData<Int>()
    val bedtimeResponseRemainingTime: LiveData<Int> = _bedtimeResponseRemainingTime

    private val _bedtimeStreak = MutableLiveData<Int>()
    val bedtimeStreak: LiveData<Int> = _bedtimeStreak

    // Add new property for bedtime info
    private val _bedtimeInfo = MutableLiveData<String>()
    val bedtimeInfo: LiveData<String> = _bedtimeInfo

    // Add new property for bedtime warning state
    private val _bedtimeWarningActive = MutableLiveData<Boolean>()
    val bedtimeWarningActive: LiveData<Boolean> = _bedtimeWarningActive
    
    // Add new property to track if we're showing wake-up countdown
    private val _isShowingWakeUpCountdown = MutableLiveData<Boolean>()
    val isShowingWakeUpCountdown: LiveData<Boolean> = _isShowingWakeUpCountdown

    // Last check-in
    private val _lastWeight = MutableLiveData<Float>()
    val lastWeight: LiveData<Float> = _lastWeight

    private val _currentBmi = MutableLiveData<Float>()
    val currentBmi: LiveData<Float> = _currentBmi

    private val _daysSinceLastCheckin = MutableLiveData<Int>()
    val daysSinceLastCheckin: LiveData<Int> = _daysSinceLastCheckin

    // Timing jobs
    private var countdownJob: Job? = null
    private var bedtimeResponseTimerJob: Job? = null
    
    private var eatingWindowStart: LocalTime = LocalTime.of(10, 0)
    private var eatingWindowDuration: Duration = Duration.ofHours(8)
    private var targetBedTime: LocalTime = LocalTime.of(22, 0)
    private var targetWakeUpTime: LocalTime = LocalTime.of(6, 30)
    private var userHeight: Float = 175f

    private var _tempBedtimeResponse: Boolean? = null

    init {
        viewModelScope.launch {
            // Load user settings
            loadUserSettings()
            
            // Load initial data
            loadFastingState()
            calculateFastingStreak()
            loadLastWeightRecord()
            calculateBedtimeStreak()
            calculateWeightLost()
            
            // Start countdown timers
            startCountdownTimers()
            
            // Check if bedtime question should be shown
            checkBedtimeQuestion()
            
            // Set up observers for recalculating score
            fastingStreak.observeForever { _ -> recalculateScore() }
            bedtimeStreak.observeForever { _ -> recalculateScore() }
            _weightLost.observeForever { _ -> recalculateScore() }
        }
    }

    private suspend fun loadUserSettings() {
        val settings = userSettingsRepository.getUserSettings()
        settings?.let {
            eatingWindowStart = LocalTime.parse(it.eatingStartTime, DateTimeFormatter.ofPattern("HH:mm"))
            eatingWindowDuration = Duration.ofHours(it.eatingWindowHours.toLong())
            targetBedTime = LocalTime.parse(it.bedTime, DateTimeFormatter.ofPattern("HH:mm"))
            targetWakeUpTime = LocalTime.parse(it.wakeUpTime, DateTimeFormatter.ofPattern("HH:mm"))
            userHeight = it.height
        }
    }

    private suspend fun loadFastingState() {
        val latestRecord = fastingRepository.getLatestFastingRecord()
        _currentFastingState.postValue(latestRecord?.state ?: false)
    }

    private fun startCountdownTimers() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                updateFastingCountdown()
                updateSleepCountdown()
                delay(1000) // Update every second
            }
        }
    }

    private fun updateFastingCountdown() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val fasting = _currentFastingState.value ?: false
            
            if (fasting) {
                // FASTING STATE: Count down to fasting window duration
                
                // Get the latest fasting record to find when fasting started
                val latestRecord = fastingRepository.getLatestFastingRecord()
                
                if (latestRecord != null && latestRecord.state) {
                    val fastingStartTime = latestRecord.timestamp
                    
                    // Calculate total fasting duration so far in seconds
                    val fastingDurationSoFar = Duration.between(fastingStartTime, now)
                    
                    // Calculate target fasting duration (24h - eating window)
                    val targetFastingDuration = Duration.ofHours(24).minus(eatingWindowDuration)
                    
                    // Update start time display (when fasting started)
                    val startTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    _fastingStartTime.postValue(fastingStartTime.format(startTimeFormatter))
                    
                    // Calculate and update end time display (when fasting will end)
                    val fastingEndTime = fastingStartTime.plus(targetFastingDuration)
                    _fastingEndTime.postValue(fastingEndTime.format(startTimeFormatter))
                    
                    if (fastingDurationSoFar.compareTo(targetFastingDuration) < 0) {
                        // Still counting down to reach target fasting duration
                        val remainingDuration = targetFastingDuration.minus(fastingDurationSoFar)
                        
                        val hours = remainingDuration.toHours()
                        val minutes = remainingDuration.toMinutesPart()
                        val seconds = remainingDuration.toSecondsPart()
                        
                        _fastingCountdown.postValue(String.format("%02d:%02d:%02d", hours, minutes, seconds))
                        
                        // Calculate progress percentage (how far along the fasting window)
                        val progressPercentage = ((fastingDurationSoFar.seconds * 100) / targetFastingDuration.seconds).toInt()
                        _fastingProgress.postValue(progressPercentage)
                    } else {
                        // Exceeded target fasting duration - show extra time as count up
                        val extraDuration = fastingDurationSoFar.minus(targetFastingDuration)
                        
                        val hours = extraDuration.toHours()
                        val minutes = extraDuration.toMinutesPart()
                        val seconds = extraDuration.toSecondsPart()
                        
                        // Display with a plus sign to indicate counting up beyond target
                        _fastingCountdown.postValue(String.format("+%02d:%02d:%02d", hours, minutes, seconds))
                        
                        // Progress is 100% once we've reached or exceeded target
                        _fastingProgress.postValue(100)
                    }
                }
            } else {
                // EATING STATE: Countdown to end of eating window
                val today = now.toLocalDate()
                val eatingStartToday = LocalDateTime.of(today, eatingWindowStart)
                val eatingEndToday = eatingStartToday.plus(eatingWindowDuration)
                
                // Get the latest fasting record to find when eating started
                val latestRecord = fastingRepository.getLatestFastingRecord()
                
                if (latestRecord != null && !latestRecord.state) {
                    // Update start time display (when eating started)
                    val startTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    val eatingStartTime = latestRecord.timestamp
                    _fastingStartTime.postValue(eatingStartTime.format(startTimeFormatter))
                    
                    // Calculate and update end time display (when eating window will end)
                    val eatingEndTime = eatingStartTime.plus(eatingWindowDuration)
                    _fastingEndTime.postValue(eatingEndTime.format(startTimeFormatter))
                    
                    // Use actual session end time for countdown calculation
                    val duration = Duration.between(now, eatingEndTime)
                    if (duration.isNegative) return@launch
                    
                    val hours = duration.toHours()
                    val minutes = duration.toMinutesPart()
                    val seconds = duration.toSecondsPart()
                    
                    _fastingCountdown.postValue(String.format("%02d:%02d:%02d", hours, minutes, seconds))
                    
                    // Calculate progress percentage based on actual eating session
                    val totalEatingDuration = eatingWindowDuration.toSeconds()
                    val elapsedSeconds = Duration.between(eatingStartTime, now).seconds
                    val progressPercentage = ((elapsedSeconds * 100) / totalEatingDuration).toInt().coerceIn(0, 100)
                    _fastingProgress.postValue(progressPercentage)
                } else {
                    // No active eating session record, fall back to schedule-based countdown
                    val countdownEndTime = if (now.isAfter(eatingEndToday)) {
                        // If already past today's eating end, count to tomorrow's end
                        LocalDateTime.of(today.plusDays(1), eatingWindowStart).plus(eatingWindowDuration)
                    } else {
                        eatingEndToday
                    }
                    
                    val duration = Duration.between(now, countdownEndTime)
                    if (duration.isNegative) return@launch
                    
                    val hours = duration.toHours()
                    val minutes = duration.toMinutesPart()
                    val seconds = duration.toSecondsPart()
                    
                    _fastingCountdown.postValue(String.format("%02d:%02d:%02d", hours, minutes, seconds))
                    
                    // Calculate progress percentage (how far along the eating window)
                    val totalEatingDuration = eatingWindowDuration.toSeconds()
                    val remainingSeconds = duration.seconds
                    val progressPercentage = ((totalEatingDuration - remainingSeconds) * 100 / totalEatingDuration).toInt()
                    _fastingProgress.postValue(progressPercentage)
                }
            }
        }
    }

    private fun updateSleepCountdown() {
        viewModelScope.launch {
            try {
                val now = LocalDateTime.now()
                val today = now.toLocalDate()
                
                val bedTimeToday = LocalDateTime.of(today, targetBedTime)
                val wakeUpTimeToday = LocalDateTime.of(today, targetWakeUpTime)
                val wakeUpTimeTomorrow = LocalDateTime.of(today.plusDays(1), targetWakeUpTime)
                val bedTimeTomorrow = LocalDateTime.of(today.plusDays(1), targetBedTime)
                
                // Format and update the bedtime info
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                _bedtimeInfo.postValue(targetBedTime.format(timeFormatter))
                
                // Check if wake-up time is before bedtime (e.g., 07:00 < 22:30)
                val isWakeUpBeforeBedtime = targetWakeUpTime.isBefore(targetBedTime)
                
                // Determine which time period we're in
                val isAfterBedtimeButBeforeWakeUp = if (isWakeUpBeforeBedtime) {
                    // Normal case: bedtime is at night, wake-up is in morning
                    (now.toLocalTime().isAfter(targetBedTime) || 
                    now.toLocalTime().isBefore(targetWakeUpTime))
                } else {
                    // Edge case: bedtime is before wake-up time
                    now.toLocalTime().isAfter(targetBedTime) && 
                    now.toLocalTime().isBefore(targetWakeUpTime)
                }
                
                val isBeforeBedtime = !isAfterBedtimeButBeforeWakeUp
                
                // Debug overall conditions
                println("DEBUG: Sleep evaluation - Current time: ${now.toLocalTime()}, Bedtime: $targetBedTime, Wake-up time: $targetWakeUpTime")
                println("DEBUG: Conditions - isBeforeBedtime: $isBeforeBedtime, isAfterBedtimeButBeforeWakeUp: $isAfterBedtimeButBeforeWakeUp")
                
                if (isAfterBedtimeButBeforeWakeUp) {
                    // CASE 1: AFTER BEDTIME, BEFORE WAKE-UP
                    
                    // Determine wake-up target time
                    val targetWakeUp = if (now.toLocalTime().isBefore(targetWakeUpTime)) {
                        wakeUpTimeToday
                    } else {
                        wakeUpTimeTomorrow
                    }
                    
                    // Calculate countdown to wake-up
                    val duration = Duration.between(now, targetWakeUp)
                    if (duration.isNegative) return@launch
                    
                    val hours = duration.toHours()
                    val minutes = duration.toMinutesPart()
                    
                    _bedtimeCountdown.postValue(String.format("%02d:%02d", hours, minutes))
                    _bedtimeCountdownLabel.postValue("until wake-up")
                    
                    // Calculate sleep progress
                    var referenceTime: LocalDateTime
                    
                    if (isWakeUpBeforeBedtime) {
                        // NORMAL CASE: Bedtime at night (e.g. 22:00), wake up in morning (e.g. 06:00)
                        if (now.toLocalTime().isBefore(targetWakeUpTime)) {
                            // We're after midnight before wake-up
                            referenceTime = LocalDateTime.of(today.minusDays(1), targetBedTime)
                        } else {
                            // We're after bedtime before midnight
                            referenceTime = LocalDateTime.of(today, targetBedTime)
                        }
                    } else {
                        // EDGE CASE: Bedtime and wake-up on same day (e.g. bedtime 22:00, wake up 23:00)
                        referenceTime = LocalDateTime.of(today, targetBedTime)
                    }
                    
                    // Calculate sleep total duration and elapsed time
                    val bedToWakeupDuration = Duration.between(referenceTime, targetWakeUp)
                    val elapsedSleepTime = Duration.between(referenceTime, now)
                    
                    // Safety check to prevent division by zero
                    if (bedToWakeupDuration.toMillis() > 0) {
                        val progressPercentageDouble = (elapsedSleepTime.toMillis() * 100.0) / bedToWakeupDuration.toMillis()
                        val progressPercentage = progressPercentageDouble.toInt().coerceIn(0, 100)
                        _bedtimeProgress.postValue(progressPercentage)
                        println("DEBUG: Sleep progress: ${progressPercentage}%")
                    } else {
                        _bedtimeProgress.postValue(0)
                    }
                    
                    // Check if it's within 4 hours after bedtime to display warning
                    val reference = if (now.toLocalTime().isBefore(targetBedTime)) {
                        // After midnight case
                        bedTimeToday.minusDays(1)
                    } else {
                        // Before midnight case
                        bedTimeToday
                    }
                    
                    val hoursSinceBedtime = Duration.between(reference, now).toHours()
                    
                    // Show warning if less than 4 hours have passed since bedtime
                    _bedtimeWarningActive.postValue(hoursSinceBedtime < 4)
                    _isShowingWakeUpCountdown.postValue(true)
                } else {
                    // CASE 2: AFTER WAKE-UP, BEFORE BEDTIME
                    
                    // Calculate countdown to bedtime
                    val duration = Duration.between(now, bedTimeToday)
                    val targetBed: LocalDateTime
                    val hours: Long
                    val minutes: Int
                    
                    if (duration.isNegative) {
                        // After bedtime, count to tomorrow's bedtime
                        targetBed = bedTimeTomorrow
                        val durationTomorrow = Duration.between(now, bedTimeTomorrow)
                        if (durationTomorrow.isNegative) return@launch
                        
                        hours = durationTomorrow.toHours()
                        minutes = durationTomorrow.toMinutesPart()
                    } else {
                        // Before bedtime, count to today's bedtime
                        targetBed = bedTimeToday
                        hours = duration.toHours()
                        minutes = duration.toMinutesPart()
                    }
                    
                    _bedtimeCountdown.postValue(String.format("%02d:%02d", hours, minutes))
                    _bedtimeCountdownLabel.postValue("until bedtime")
                    
                    // Calculate awake progress - from wake-up to bedtime
                    
                    // Reference wake-up time (today or yesterday)
                    val referenceWakeUp = if (now.toLocalTime().isAfter(targetWakeUpTime)) {
                        // After wake-up time, reference is today's wake-up
                        wakeUpTimeToday
                    } else {
                        // Before wake-up time, reference is yesterday's wake-up
                        LocalDateTime.of(today.minusDays(1), targetWakeUpTime)
                    }
                    
                    // Calculate total wake duration and elapsed time
                    val wakeupToBedDuration = Duration.between(referenceWakeUp, targetBed)
                    val elapsedWakeTime = Duration.between(referenceWakeUp, now)
                    
                    // Safety check to prevent division by zero
                    if (wakeupToBedDuration.toMillis() > 0) {
                        val progressPercentageDouble = (elapsedWakeTime.toMillis() * 100.0) / wakeupToBedDuration.toMillis()
                        val progressPercentage = progressPercentageDouble.toInt().coerceIn(0, 100)
                        _bedtimeProgress.postValue(progressPercentage)
                        println("DEBUG: Awake progress: ${progressPercentage}%")
                    } else {
                        _bedtimeProgress.postValue(0)
                    }
                    
                    _bedtimeWarningActive.postValue(false)
                    _isShowingWakeUpCountdown.postValue(false)
                }
            } catch (e: Exception) {
                // Handle any errors gracefully
                println("ERROR in updateSleepCountdown: ${e.message}")
                e.printStackTrace()
                _bedtimeProgress.postValue(0)
                _bedtimeCountdown.postValue("--:--")
                _bedtimeCountdownLabel.postValue("until bedtime")
                _bedtimeWarningActive.postValue(false)
                _isShowingWakeUpCountdown.postValue(false)
            }
        }
    }

    private suspend fun calculateFastingStreak() {
        val settings = userSettingsRepository.getUserSettings()
        val eatingWindowHours = settings?.eatingWindowHours ?: 8 // Default to 8 if not set
        val streak = fastingRepository.calculateFastingStreak(eatingWindowHours)
        _fastingStreak.postValue(streak)
        
        // Request widget update
        updateWidgets()
    }

    private suspend fun calculateBedtimeStreak() {
        val streak = sleepRepository.calculateBedtimeStreak()
        _bedtimeStreak.postValue(streak)
        
        // Request widget update
        updateWidgets()
    }

    private suspend fun calculateWeightLost() {
        val weightRecords = weightRepository.getAllWeightRecords()
        
        // Get the maximum weight loss relative to the starting weight
        if (weightRecords.size >= 2) {
            val sortedRecords = weightRecords.sortedBy { it.timestamp }
            val startWeight = sortedRecords.first().weight
            
            // Calculate the maximum lost weight (ignoring any weight gains)
            var maxWeightLoss = 0f
            for (record in sortedRecords.drop(1)) {
                val currentLoss = kotlin.math.max(0f, startWeight - record.weight)
                if (currentLoss > maxWeightLoss) {
                    maxWeightLoss = currentLoss
                }
            }
            
            // Also calculate current weight based on last record
            val currentWeight = sortedRecords.last().weight
            
            // Use the maximum historical weight loss for achievements
            _weightLost.postValue(maxWeightLoss)
        } else {
            // No weight loss if we don't have enough records
            _weightLost.postValue(0f)
        }
    }

    private fun recalculateScore() {
        viewModelScope.launch {
            val sleepStreakValue = bedtimeStreak.value ?: 0
            val fastingStreakValue = fastingStreak.value ?: 0
            val weightLostValue = _weightLost.value ?: 0f
            
            // Get achievement points from repository
            val app = getApplication<SleepFastTrackerApplication>()
            val achievementRepository = app.achievementRepository
            // Need to use .first() because totalPoints is a Flow<Int?>
            val achievementPointsValue = achievementRepository.totalPoints.first() ?: 0
            
            // Calculate the score based on the formula: ((sleep streak + fasting streak) * 5) + (weight lost in KG * 10) + achievement points
            val streakComponent = (sleepStreakValue + fastingStreakValue) * 5
            val weightComponent = (weightLostValue * 10).toInt()
            
            val score = streakComponent + weightComponent + achievementPointsValue
            _overallScore.postValue(score)
        }
    }

    private suspend fun loadLastWeightRecord() {
        val lastRecord = weightRepository.getLatestWeightRecord()
        
        if (lastRecord != null) {
            _lastWeight.postValue(lastRecord.weight)
            
            // Calculate BMI
            val heightInMeters = userHeight / 100
            val bmi = lastRecord.weight / (heightInMeters * heightInMeters)
            _currentBmi.postValue((bmi * 10).roundToInt() / 10f) // Round to 1 decimal place
            
            // Calculate days since last check-in
            val today = LocalDate.now()
            val lastCheckinDate = lastRecord.timestamp.toLocalDate()
            val daysSince = Duration.between(lastCheckinDate.atStartOfDay(), today.atStartOfDay()).toDays().toInt()
            
            println("DEBUG: Last weight recorded on: ${lastRecord.timestamp}, days since: $daysSince")
            
            _daysSinceLastCheckin.postValue(daysSince)
        } else {
            println("DEBUG: No weight records found in database")
            _lastWeight.postValue(0f)
            _currentBmi.postValue(0f)
            _daysSinceLastCheckin.postValue(-1) // Use -1 to indicate no records
        }
    }

    suspend fun toggleFastingState() {
        val currentState = _currentFastingState.value ?: false
        val newState = !currentState
        
        // Update the live data
        _currentFastingState.postValue(newState)
        
        // Create a new fasting record in the repository
        val now = LocalDateTime.now()
        fastingRepository.insertFastingRecord(
            FastingRecord(
                timestamp = now,
                state = newState
            )
        )
        
        // Recalculate streak
        calculateFastingStreak()
        
        // Request widget update
        updateWidgets()
        
        // Run the fasting delta backfill
        runFastingDeltaBackfill()
    }

    private fun checkBedtimeQuestion() {
        viewModelScope.launch {
            // Show bedtime question between 6 AM and 8 PM
            val now = LocalTime.now()
            val startTime = LocalTime.of(6, 0)
            val endTime = LocalTime.of(20, 0)
            
            val shouldShow = (now.isAfter(startTime) && now.isBefore(endTime)) && 
                            !sleepRepository.hasAnsweredBedtimeToday()
            
            _shouldShowBedtimeQuestion.postValue(shouldShow)
        }
    }

    suspend fun setBedtimeResponse(onTime: Boolean) {
        // Store the response temporarily, don't save to database yet
        _tempBedtimeResponse = onTime
        
        // Start the 5-second timer for changing the response
        startBedtimeResponseTimer()
    }

    private fun startBedtimeResponseTimer() {
        bedtimeResponseTimerJob?.cancel()
        
        _bedtimeResponseRemainingTime.postValue(3) // Changed from 5 to 3 to approximate 2.5 seconds
        
        bedtimeResponseTimerJob = viewModelScope.launch {
            // Use shorter countdown for approximately 2.5 seconds total
            // First countdown from 3 to 2
            _bedtimeResponseRemainingTime.postValue(3)
            delay(500) // Wait 0.5 seconds
            
            // Then countdown from 2 to 1
            _bedtimeResponseRemainingTime.postValue(2)
            delay(1000) // Wait 1 second
            
            // Final countdown to 1
            _bedtimeResponseRemainingTime.postValue(1)
            delay(1000) // Wait 1 second
            
            // After timer expires, save the response to the database
            _tempBedtimeResponse?.let { onTime ->
                val sleepRecord = SleepRecord(
                    onTime = onTime,
                    timestamp = LocalDateTime.now()
                )
                sleepRepository.insertSleepRecord(sleepRecord)
                
                // Update the bedtime streak
                calculateBedtimeStreak()
            }
            
            // Hide the question
            _shouldShowBedtimeQuestion.postValue(false)
        }
    }

    // Add widget update method
    private fun updateWidgets() {
        val context = getApplication<Application>().applicationContext
        FastingBedtimeWidget.requestUpdate(context)
    }

    // Function to refresh weight data when coming back to home screen
    suspend fun refreshLastWeightRecord() {
        loadLastWeightRecord()
        calculateWeightLost()  // Also recalculate weight lost when refreshing
        recalculateScore()     // Explicitly recalculate the score after weight data is refreshed
    }

    /**
     * Trigger the backfill process for fasting delta_minutes
     */
    fun runFastingDeltaBackfill() {
        val app = getApplication<SleepFastTrackerApplication>()
        app.runFastingDeltaBackfill()
    }

    override fun onCleared() {
        countdownJob?.cancel()
        bedtimeResponseTimerJob?.cancel()
        super.onCleared()
    }
}

class HomeViewModelFactory(
    private val fastingRepository: FastingRepository,
    private val sleepRepository: SleepRepository,
    private val weightRepository: WeightRecordRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                fastingRepository,
                sleepRepository,
                weightRepository,
                userSettingsRepository,
                application
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 