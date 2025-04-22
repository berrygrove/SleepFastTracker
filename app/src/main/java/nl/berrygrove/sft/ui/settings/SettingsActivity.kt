package nl.berrygrove.sft.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.UserSettings
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.databinding.ActivitySettingsBinding
import nl.berrygrove.sft.notification.NotificationHelper
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import nl.berrygrove.sft.ui.debug.DebugActivity
import java.time.LocalDateTime

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var userSettingsRepository: nl.berrygrove.sft.repository.UserSettingsRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var fastingRepository: FastingRepository
    private lateinit var sleepRepository: SleepRepository
    private lateinit var weightRecordRepository: WeightRecordRepository
    private var userSettings: UserSettings? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, enable notifications
            enableNotifications()
        } else {
            // Permission denied, show dialog to open settings
            showNotificationPermissionDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_settings)
        
        // Get repositories
        val app = application as SleepFastTrackerApplication
        userSettingsRepository = app.userSettingsRepository
        fastingRepository = app.fastingRepository
        sleepRepository = app.sleepRepository
        weightRecordRepository = app.weightRecordRepository
        notificationHelper = NotificationHelper(this)
        
        // Load user settings
        loadUserSettings()
        
        // Setup notification switches
        setupNotificationSwitches()
        
        // Setup debug button
        setupDebugButton()
        
        // Setup reset data button
        setupResetDataButton()
    }
    
    private fun loadUserSettings() {
        lifecycleScope.launch {
            userSettings = userSettingsRepository.getUserSettings()
            userSettings?.let { settings ->
                updateSwitchStates(settings)
            }
        }
    }
    
    private fun setupNotificationSwitches() {
        // Master switch
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check system permission when enabling notifications
                checkNotificationPermission()
            } else {
                // Directly disable notifications if turning off
                updateNotificationSettings(false)
            }
        }
        
        // Individual notification switches
        binding.switchBedtimeCheck.setOnCheckedChangeListener { _, isChecked ->
            if (hasNotificationPermission()) {
                userSettings?.let { settings ->
                    val updatedSettings = settings.copy(bedtimeCheckNotificationEnabled = isChecked)
                    updateUserSettings(updatedSettings)
                }
            } else {
                binding.switchBedtimeCheck.isChecked = false
                checkNotificationPermission()
            }
        }
        
        binding.switchBedtimeReminder.setOnCheckedChangeListener { _, isChecked ->
            if (hasNotificationPermission()) {
                userSettings?.let { settings ->
                    val updatedSettings = settings.copy(bedtimeReminderNotificationEnabled = isChecked)
                    updateUserSettings(updatedSettings)
                }
            } else {
                binding.switchBedtimeReminder.isChecked = false
                checkNotificationPermission()
            }
        }
        
        binding.switchWeightUpdate.setOnCheckedChangeListener { _, isChecked ->
            if (hasNotificationPermission()) {
                userSettings?.let { settings ->
                    val updatedSettings = settings.copy(weightUpdateNotificationEnabled = isChecked)
                    updateUserSettings(updatedSettings)
                }
            } else {
                binding.switchWeightUpdate.isChecked = false
                checkNotificationPermission()
            }
        }
        
        binding.switchFastingEnd.setOnCheckedChangeListener { _, isChecked ->
            if (hasNotificationPermission()) {
                userSettings?.let { settings ->
                    val updatedSettings = settings.copy(fastingEndNotificationEnabled = isChecked)
                    updateUserSettings(updatedSettings)
                }
            } else {
                binding.switchFastingEnd.isChecked = false
                checkNotificationPermission()
            }
        }
        
        binding.switchEatingEnd.setOnCheckedChangeListener { _, isChecked ->
            if (hasNotificationPermission()) {
                userSettings?.let { settings ->
                    val updatedSettings = settings.copy(eatingEndNotificationEnabled = isChecked)
                    updateUserSettings(updatedSettings)
                }
            } else {
                binding.switchEatingEnd.isChecked = false
                checkNotificationPermission()
            }
        }
    }
    
    private fun updateSwitchStates(settings: UserSettings) {
        // Check system permission status
        val hasPermission = hasNotificationPermission()
        
        // Temporarily remove listeners to avoid triggering updates
        binding.switchNotifications.setOnCheckedChangeListener(null)
        binding.switchBedtimeCheck.setOnCheckedChangeListener(null)
        binding.switchBedtimeReminder.setOnCheckedChangeListener(null)
        binding.switchWeightUpdate.setOnCheckedChangeListener(null)
        binding.switchFastingEnd.setOnCheckedChangeListener(null)
        binding.switchEatingEnd.setOnCheckedChangeListener(null)
        
        // Update switch states based on both settings and permission
        binding.switchNotifications.isChecked = settings.notificationsEnabled && hasPermission
        binding.switchBedtimeCheck.isChecked = settings.bedtimeCheckNotificationEnabled && hasPermission
        binding.switchBedtimeReminder.isChecked = settings.bedtimeReminderNotificationEnabled && hasPermission
        binding.switchWeightUpdate.isChecked = settings.weightUpdateNotificationEnabled && hasPermission
        binding.switchFastingEnd.isChecked = settings.fastingEndNotificationEnabled && hasPermission
        binding.switchEatingEnd.isChecked = settings.eatingEndNotificationEnabled && hasPermission
        
        // Disable individual switches if master switch is off or no permission
        val enableIndividualSwitches = settings.notificationsEnabled && hasPermission
        binding.switchBedtimeCheck.isEnabled = enableIndividualSwitches
        binding.switchBedtimeReminder.isEnabled = enableIndividualSwitches
        binding.switchWeightUpdate.isEnabled = enableIndividualSwitches
        binding.switchFastingEnd.isEnabled = enableIndividualSwitches
        binding.switchEatingEnd.isEnabled = enableIndividualSwitches
        
        // Re-enable listeners
        setupNotificationSwitches()
    }
    
    private fun updateUserSettings(settings: UserSettings) {
        lifecycleScope.launch {
            userSettingsRepository.updateUserSettings(settings)
            userSettings = settings
            
            // Reschedule notifications with new settings
            notificationHelper.scheduleNotifications(settings)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                hasNotificationPermission() -> {
                    // Permission already granted
                    enableNotifications()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale and request permission
                    showNotificationPermissionDialog()
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // No runtime permission needed for Android < 13
            enableNotifications()
        }
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("To use notifications, you need to enable them in system settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                // Ensure switches reflect the disabled state
                userSettings?.let { settings ->
                    updateNotificationSettings(false)
                }
            }
            .show()
    }

    private fun enableNotifications() {
        updateNotificationSettings(true)
    }

    private fun updateNotificationSettings(enabled: Boolean) {
        userSettings?.let { currentSettings ->
            val updatedSettings = currentSettings.copy(
                notificationsEnabled = enabled,
                bedtimeCheckNotificationEnabled = enabled && currentSettings.bedtimeCheckNotificationEnabled,
                bedtimeReminderNotificationEnabled = enabled && currentSettings.bedtimeReminderNotificationEnabled,
                weightUpdateNotificationEnabled = enabled && currentSettings.weightUpdateNotificationEnabled,
                fastingEndNotificationEnabled = enabled && currentSettings.fastingEndNotificationEnabled,
                eatingEndNotificationEnabled = enabled && currentSettings.eatingEndNotificationEnabled
            )
            updateUserSettings(updatedSettings)
            updateSwitchStates(updatedSettings)
        }
    }
    
    private fun setupDebugButton() {
        binding.btnDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
    }
    
    private fun setupResetDataButton() {
        binding.btnResetData.setOnClickListener {
            showResetDataConfirmationDialog()
        }
    }
    
    private fun showResetDataConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Data")
            .setMessage("This will delete all your fasting, sleep, and weight records. This action cannot be undone. Are you sure you want to continue?")
            .setPositiveButton("Reset Data") { _, _ ->
                showDataInputDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDataInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_data, null)
        val weightEditText = dialogView.findViewById<EditText>(R.id.edit_current_weight)
        val fastingStateRadioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group_fasting_state)
        
        // Pre-fill with last weight if available
        lifecycleScope.launch {
            val lastWeight = weightRecordRepository.getLatestWeightRecord()
            if (lastWeight != null) {
                weightEditText.setText(lastWeight.weight.toString())
            }
        }
        
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Reset") { _, _ ->
                val weightText = weightEditText.text.toString()
                val isFasting = fastingStateRadioGroup.checkedRadioButtonId == R.id.radio_fasting
                
                if (weightText.isNotBlank()) {
                    try {
                        val weight = weightText.toFloat()
                        resetAllData(weight, isFasting)
                    } catch (e: NumberFormatException) {
                        showErrorDialog("Please enter a valid weight.")
                    }
                } else {
                    showErrorDialog("Please enter your current weight.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun resetAllData(currentWeight: Float, isFasting: Boolean) {
        lifecycleScope.launch {
            // Clear all data
            fastingRepository.clearAll()
            sleepRepository.clearAll()
            weightRecordRepository.clearAll()
            
            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "All data cleared")
            
            // Create new weight record
            val weightRecord = WeightRecord(
                weight = currentWeight,
                timestamp = LocalDateTime.now()
            )
            weightRecordRepository.insert(weightRecord)
            
            // Get user settings to determine eating window
            val settings = userSettingsRepository.getUserSettings()
            
            // Set appropriate timestamp based on fasting state
            val now = LocalDateTime.now()
            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Current time: $now")
            
            val timestamp = if (settings != null) {
                // Parse eating start time (e.g., "11:00")
                val timeParts = settings.eatingStartTime.split(":")
                val startHour = timeParts[0].toInt()
                val startMinute = timeParts[1].toInt()
                
                // Calculate eating end time
                val eatingWindowMinutes = settings.eatingWindowHours * 60
                val totalMinutes = startHour * 60 + startMinute + eatingWindowMinutes
                val endHour = (totalMinutes / 60) % 24
                val endMinute = totalMinutes % 60
                
                // Check if eating window spans overnight (end time is earlier than start time)
                val isOvernightWindow = endHour < startHour || (endHour == startHour && endMinute < startMinute)
                
                SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Eating window: $startHour:$startMinute to $endHour:$endMinute (${settings.eatingWindowHours} hours, overnight: $isOvernightWindow)")
                
                val today = now.toLocalDate()
                val currentTime = now.toLocalTime()
                val startTime = java.time.LocalTime.of(startHour, startMinute)
                val endTime = java.time.LocalTime.of(endHour, endMinute)
                
                if (isFasting) {
                    // For fasting state (state=true), timestamp should be when fasting started,
                    // which is at the end of the eating window
                    
                    val result = if (isOvernightWindow) {
                        // For overnight windows, we need special handling
                        if (currentTime.isBefore(endTime)) {
                            // Before end time: fasting started yesterday evening
                            val yesterdayEndTime = LocalDateTime.of(today, endTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Overnight window - Current time is before today's eating end, using today's end time: $yesterdayEndTime")
                            yesterdayEndTime
                        } else if (currentTime.isAfter(startTime)) {
                            // After start time: fasting started today evening
                            val todayEndTime = LocalDateTime.of(today.plusDays(1), endTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Overnight window - Current time is after today's eating start, using tomorrow's end time: $todayEndTime")
                            todayEndTime
                        } else {
                            // Between end and start: fasting started today morning
                            val todayEndTime = LocalDateTime.of(today, endTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Overnight window - Current time is between eating end and start, using today's end time: $todayEndTime")
                            todayEndTime
                        }
                    } else {
                        // Normal eating window
                        val eatingEndTimeToday = LocalDateTime.of(today, endTime)
                        
                        if (now.isBefore(eatingEndTimeToday)) {
                            // Before today's end time, use yesterday's end time
                            val yesterdayEndTime = LocalDateTime.of(today.minusDays(1), endTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Current time is before today's eating end, using yesterday's end time: $yesterdayEndTime")
                            yesterdayEndTime
                        } else {
                            // After today's end time, use today's end time
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Current time is after today's eating end, using today's end time: $eatingEndTimeToday")
                            eatingEndTimeToday
                        }
                    }
                    
                    result
                } else {
                    // For eating state (state=false), timestamp should be when eating started,
                    // which is at the start of the eating window
                    
                    val result = if (isOvernightWindow) {
                        // For overnight windows, we need special handling
                        if (currentTime.isBefore(endTime)) {
                            // Before end time: eating started yesterday evening
                            val yesterdayStartTime = LocalDateTime.of(today.minusDays(1), startTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Overnight window - Current time is before today's eating end, using yesterday's start time: $yesterdayStartTime")
                            yesterdayStartTime
                        } else if (currentTime.isAfter(startTime)) {
                            // After start time: eating started today evening
                            val todayStartTime = LocalDateTime.of(today, startTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Overnight window - Current time is after today's eating start, using today's start time: $todayStartTime")
                            todayStartTime
                        } else {
                            // Between end and start: not in eating window, use next start time
                            val todayStartTime = LocalDateTime.of(today, startTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Overnight window - Current time is between eating end and start, using today's start time: $todayStartTime")
                            todayStartTime
                        }
                    } else {
                        // Normal eating window
                        val eatingStartTimeToday = LocalDateTime.of(today, startTime)
                        val eatingEndTimeToday = LocalDateTime.of(today, endTime)
                        
                        if (now.isBefore(eatingStartTimeToday)) {
                            // Before eating window - use today's start time
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Current time is before today's eating start, using today's start time: $eatingStartTimeToday")
                            eatingStartTimeToday
                        } else if (now.isAfter(eatingEndTimeToday)) {
                            // After eating window - use tomorrow's start time
                            val tomorrowStartTime = LocalDateTime.of(today.plusDays(1), startTime)
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Current time is after today's eating end, using tomorrow's start time: $tomorrowStartTime")
                            tomorrowStartTime
                        } else {
                            // Within eating window - use today's start time
                            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Current time is within eating window, using today's start time: $eatingStartTimeToday")
                            eatingStartTimeToday
                        }
                    }
                    
                    result
                }
            } else {
                // Fallback to current time if settings aren't available
                SleepFastTrackerApplication.addDebugLog("SettingsActivity", "No user settings available, using current time as timestamp")
                now
            }
            
            // Create new fasting record with the calculated timestamp
            val fastingRecord = FastingRecord(
                state = isFasting, // true = fasting, false = eating
                timestamp = timestamp
            )
            fastingRepository.insertFastingRecord(fastingRecord)
            
            SleepFastTrackerApplication.addDebugLog("SettingsActivity", "Created new ${if (isFasting) "fasting" else "eating"} record with timestamp: $timestamp")
            
            // Force backfill of delta minutes for fasting records
            (application as SleepFastTrackerApplication).runFastingDeltaBackfill()
            
            // Show success message
            runOnUiThread {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Data Reset Complete")
                    .setMessage("Your data has been reset and new initial records have been created.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 