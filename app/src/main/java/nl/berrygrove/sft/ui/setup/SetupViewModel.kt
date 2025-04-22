package nl.berrygrove.sft.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.UserSettings
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.notification.NotificationHelper
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ViewModel for the setup wizard.
 * Handles data operations during the setup process.
 */
class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val userSettingsRepository = (application as SleepFastTrackerApplication).userSettingsRepository
    private val fastingRepository = (application as SleepFastTrackerApplication).fastingRepository
    private val weightRepository = (application as SleepFastTrackerApplication).weightRecordRepository
    private val achievementRepository = (application as SleepFastTrackerApplication).achievementRepository
    private val notificationHelper = NotificationHelper(application)
    private val app = application
    
    // User data
    private var name: String = ""
    private var age: Int = 0
    private var height: Float = 0f
    private var weight: Float = 0f
    private var wakeUpTime: String = "07:00"
    private var bedTime: String = "22:30"
    private var eatingStartTime: String = "11:00"
    private var eatingWindowHours: Int = 6
    
    // Set user's name
    fun setUserName(name: String) {
        this.name = name
    }
    
    // Get user's name
    fun getUserName(): String {
        return name
    }
    
    // Save user name and age
    fun savePersonalInfo(name: String, age: Int) {
        this.name = name
        this.age = age
    }
    
    // Save user height and weight
    fun saveBodyInfo(height: Float, weight: Float) {
        this.height = height
        this.weight = weight
    }
    
    // Save sleep schedule
    fun saveSleepSchedule(wakeUpTime: String, bedTime: String) {
        this.wakeUpTime = wakeUpTime
        this.bedTime = bedTime
    }
    
    // Save fasting schedule
    fun saveFastingSchedule(eatingStartTime: String, eatingWindowHours: Int) {
        this.eatingStartTime = eatingStartTime
        this.eatingWindowHours = eatingWindowHours
    }
    
    // Calculate BMI based on height (in cm) and weight (kg)
    fun calculateBMI(): String {
        if (height <= 0f || weight <= 0f) return "0.0"
        
        // Convert height from cm to meters
        val heightInMeters = height / 100f
        val bmi = weight / (heightInMeters.pow(2))
        
        // Log raw BMI calculation for debugging
        println("Height: ${height}cm (${heightInMeters}m), Weight: ${weight}kg, Raw BMI: $bmi")
        
        // Format BMI to one decimal place
        val df = DecimalFormat("#.0")
        val formattedBmi = df.format(bmi)
        println("Formatted BMI: $formattedBmi")
        
        return formattedBmi
    }
    
    // Get BMI category (Underweight, Normal, Overweight, Obese)
    fun getBMICategory(): String {
        val bmiStr = calculateBMI()
        // Make sure to handle locale-specific decimal separators
        val bmiValue = bmiStr.replace(',', '.').toFloatOrNull() ?: 0f
        
        println("BMI value: $bmiValue")
        
        return when {
            bmiValue < 18.5f -> "Underweight"
            bmiValue < 25f -> "Healthy"
            bmiValue < 30f -> "Overweight"
            else -> "Obese"
        }
    }
    
    // Calculate recommended bedtime based on wake up time
    fun calculateRecommendedBedtime(wakeUpTime: String): String {
        // Parse wake up time
        val parts = wakeUpTime.split(":")
        var hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        
        // Subtract 8.5 hours
        hours -= 8
        var newMinutes = minutes - 30
        
        // Handle underflow
        if (newMinutes < 0) {
            newMinutes += 60
            hours--
        }
        
        // Handle day change
        if (hours < 0) {
            hours += 24
        }
        
        // Format as HH:MM
        return String.format("%02d:%02d", hours, newMinutes)
    }
    
    // Calculate fasting window based on eating window
    fun calculateFastingHours(eatingWindowHours: Int): Int {
        return 24 - eatingWindowHours
    }
    
    // Calculate eating end time
    fun calculateEatingEndTime(startTime: String, windowHours: Int): String {
        val timeParts = startTime.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        
        val totalMinutes = hour * 60 + minute + (windowHours * 60)
        val endHour = (totalMinutes / 60) % 24
        val endMinute = totalMinutes % 60
        
        return String.format("%02d:%02d", endHour, endMinute)
    }
    
    // Get eating start time
    fun getEatingStartTime(): String = eatingStartTime
    
    // Get eating window hours
    fun getEatingWindowHours(): Int = eatingWindowHours
    
    // Create first fasting entry
    fun createFirstFastingEntry(state: Boolean, timestamp: Long) {
        viewModelScope.launch {
            val fastingRecord = FastingRecord(
                state = state,
                timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            )
            fastingRepository.insertFastingRecord(fastingRecord)
            
            // Run fasting delta backfill after creating the record
            runFastingDeltaBackfill()
        }
    }
    
    /**
     * Trigger the backfill process for fasting delta_minutes
     */
    private fun runFastingDeltaBackfill() {
        (getApplication() as SleepFastTrackerApplication).runFastingDeltaBackfill()
    }
    
    // Create first weight record
    fun createWeightRecord() {
        viewModelScope.launch {
            val weightRecord = WeightRecord(
                weight = weight,
                timestamp = LocalDateTime.now()
            )
            weightRepository.insert(weightRecord)
            
            // Recalculate weight achievements based on initial weight and height
            if (height > 0) {
                try {
                    achievementRepository.recalculateWeightAchievements(height, weight)
                    SleepFastTrackerApplication.addDebugLog("SetupViewModel", 
                        "Calculated personalized weight achievements based on height: $height cm and weight: $weight kg")
                } catch (e: Exception) {
                    SleepFastTrackerApplication.addDebugLog("SetupViewModel", 
                        "Error calculating weight achievements: ${e.message}")
                }
            }
        }
    }
    
    fun setSetupCompleted() {
        viewModelScope.launch {
            val userSettings = UserSettings(
                name = name,
                age = age,
                height = height,
                eatingStartTime = eatingStartTime,
                eatingWindowHours = eatingWindowHours,
                bedTime = bedTime,
                wakeUpTime = wakeUpTime,
                setupCompleted = true
            )
            userSettingsRepository.saveUserSettings(userSettings)
            
            // Schedule notifications
            notificationHelper.scheduleNotifications(userSettings)
        }
    }
} 