package nl.berrygrove.sft

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.berrygrove.sft.data.AppDatabase
import nl.berrygrove.sft.data.model.Achievement
import nl.berrygrove.sft.debug.BatteryUsageMonitor
import nl.berrygrove.sft.repository.AchievementRepository
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import nl.berrygrove.sft.utils.FastingDeltaBackfill
import nl.berrygrove.sft.widget.FastingBedtimeWidget
import java.text.SimpleDateFormat
import java.util.*

/**
 * Application class for SleepFastTracker.
 * Initializes the database and repositories.
 */
class SleepFastTrackerApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Using lazy so the database and repositories are only created when needed
    private val database by lazy { AppDatabase.getDatabase(this) }
    
    // Create repositories
    val userSettingsRepository by lazy { UserSettingsRepository(database.userSettingsDao()) }
    val fastingRepository by lazy { FastingRepository(database.fastingRecordDao(), userSettingsRepository) }
    val sleepRepository by lazy { SleepRepository(database.sleepRecordDao()) }
    val weightRecordRepository by lazy { WeightRecordRepository(database.weightRecordDao()) }
    val achievementRepository by lazy { AchievementRepository(database.achievementDao()) }
    
    // Battery usage monitor
    val batteryMonitor by lazy { BatteryUsageMonitor.getInstance() }
    
    // Global debug log storage
    companion object {
        private val debugLogBuilder = StringBuilder()
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        private val debugLogs = mutableListOf<Pair<String, String>>()
        
        // Flag to control debug logging
        private var debugEnabled = false
        
        // Static reference to the application instance
        private var instance: SleepFastTrackerApplication? = null
        
        /**
         * Get the application instance
         */
        fun getInstance(): SleepFastTrackerApplication? {
            return instance
        }
        
        /**
         * Enable or disable debug logging
         */
        @Synchronized
        fun setDebugEnabled(enabled: Boolean) {
            debugEnabled = enabled
        }
        
        /**
         * Get current debug enabled state
         */
        @Synchronized
        fun isDebugEnabled(): Boolean {
            return debugEnabled
        }
        
        /**
         * Add a message to the debug log that will be displayed in the Debug screen
         */
        @Synchronized
        fun addDebugLog(tag: String, message: String) {
            // Only log if debug is enabled
            if (!debugEnabled) return
            
            val timestamp = dateFormat.format(Date())
            val logMessage = "[$timestamp] [$tag] $message\n"
            
            // Add to debug log builder
            debugLogBuilder.append(logMessage)
            
            // Also add to the logs list for the new implementation
            debugLogs.add(Pair(tag, message))
            if (debugLogs.size > 1000) {  // Keep only the last 1000 logs
                debugLogs.removeAt(0)
            }
            
            // Also log to Android system log
            android.util.Log.d(tag, message)
            
            // Record in battery monitor
            BatteryUsageMonitor.getInstance().recordOperation("log:$tag")
        }
        
        /**
         * Get all debug logs
         */
        @Synchronized
        fun getDebugLogs(): String {
            return debugLogBuilder.toString()
        }
        
        /**
         * Clear all debug logs
         */
        @Synchronized
        fun clearDebugLogs() {
            debugLogBuilder.clear()
            debugLogs.clear()
        }
        
        /**
         * Get debug logs as a list of pairs
         */
        @Synchronized
        fun getDebugLogsList(): List<Pair<String, String>> {
            return debugLogs.toList()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Set instance
        instance = this
        
        // Initialize app
        initializeApp()
        
        // Schedule periodic widget updates instead of immediate update
        // This delays the initialization to reduce startup cost
        applicationScope.launch {
            // Delay the initial widget update to avoid startup performance impact
            kotlinx.coroutines.delay(5000) // 5 second delay
            FastingBedtimeWidget.schedulePeriodicUpdates(this@SleepFastTrackerApplication)
        }
    }
    
    // Provide WorkManager configuration with battery optimization settings
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
    
    private fun initializeApp() {
        applicationScope.launch {
            // Initialize database and populate with initial data if needed
            // This runs in a background thread
            
            // Record the app initialization in the battery monitor
            batteryMonitor.recordOperation("app_initialization")
        }
    }
    
    /**
     * Run the delta_minutes backfill process on existing fasting records
     */
    fun runFastingDeltaBackfill() {
        applicationScope.launch {
            batteryMonitor.startTiming("fasting_delta_backfill")
            
            val backfill = FastingDeltaBackfill(this@SleepFastTrackerApplication, fastingRepository, userSettingsRepository)
            val updatedCount = backfill.backfillDeltaMinutes()
            addDebugLog("SleepFastTrackerApplication", "Fasting delta backfill process completed. Updated $updatedCount records.")
            
            batteryMonitor.stopTiming("fasting_delta_backfill")
        }
    }
    
    /**
     * Generate a battery usage report for debugging
     */
    fun generateBatteryReport(): String {
        batteryMonitor.dumpStats(this)
        return "Battery usage report generated. Check the app's external files directory."
    }
    
    // Add a function to insert the new achievements if they don't exist
    fun addNewAchievements() {
        applicationScope.launch {
            // Add new weight achievements
            val newWeightAchievements = listOf(
                Achievement(
                    name = "Weight Conqueror", 
                    points = 450, 
                    category = "weight", 
                    description = "Lost 30 kg total.", 
                    emoticon = "🏆", 
                    threshold = 30f
                ),
                Achievement(
                    name = "Transformation Master", 
                    points = 525, 
                    category = "weight", 
                    description = "Lost 35 kg total.", 
                    emoticon = "⚜️", 
                    threshold = 35f
                )
            )
            
            // Add new ultimate achievements
            val newUltimateAchievements = listOf(
                Achievement(
                    name = "Sleep Grand Master", 
                    points = 730, 
                    category = "sleep", 
                    description = "Slept consistently for 2 years.", 
                    emoticon = "🌠", 
                    threshold = 730f
                ),
                Achievement(
                    name = "Fasting Grand Master", 
                    points = 730, 
                    category = "fasting", 
                    description = "Fasted consistently for 2 years.", 
                    emoticon = "🌠", 
                    threshold = 730f
                )
            )
            
            // Insert all new achievements
            achievementRepository.insertAll(newWeightAchievements + newUltimateAchievements)
            addDebugLog("Application", "Added 4 new achievements to database")
        }
    }
} 