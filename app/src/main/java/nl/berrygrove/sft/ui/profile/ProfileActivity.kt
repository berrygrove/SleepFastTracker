package nl.berrygrove.sft.ui.profile

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.model.UserSettings
import nl.berrygrove.sft.data.model.WeightRecord
import nl.berrygrove.sft.databinding.ActivityProfileBinding
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.repository.WeightRecordRepository
import java.text.DecimalFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProfileBinding
    private lateinit var userSettingsRepository: UserSettingsRepository
    private lateinit var weightRepository: WeightRecordRepository
    private var userSettings: UserSettings? = null
    private var height: Float = 0f
    private var weight: Float = 0f
    private var currentWeightRecord: WeightRecord? = null
    private var eatingWindowHours: Int = 8 // Default value
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_profile)
        
        // Get repositories
        val app = application as SleepFastTrackerApplication
        userSettingsRepository = app.userSettingsRepository
        weightRepository = app.weightRecordRepository
        
        // Set up UI components
        setupTimePickerFields()
        setupListeners()
        
        // Load user data
        loadUserData()
    }
    
    private fun setupTimePickerFields() {
        // Set up bedtime picker
        binding.etBedtime.setOnClickListener {
            showTimePickerDialog(binding.etBedtime.text.toString()) { time ->
                binding.etBedtime.setText(time)
            }
        }
        
        // Set up wake up time picker
        binding.etWakeup.setOnClickListener {
            showTimePickerDialog(binding.etWakeup.text.toString()) { time ->
                binding.etWakeup.setText(time)
            }
        }
        
        // Set up eating start time picker
        binding.etEatingStart.setOnClickListener {
            showTimePickerDialog(binding.etEatingStart.text.toString()) { time ->
                binding.etEatingStart.setText(time)
                updateEatingSchedule()
            }
        }
        
        // Update eating window display when hours change
        binding.etEatingWindow.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateEatingSchedule()
            }
        })
    }
    
    private fun setupListeners() {
        // Set up save button
        binding.btnSave.setOnClickListener {
            saveUserData()
        }
        
        // Update BMI when height changes
        binding.etHeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                height = s.toString().toFloatOrNull() ?: 0f
                updateBMI()
            }
        })
        
        // Setup eating window SeekBar
        binding.seekBarEatingWindow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                eatingWindowHours = progress + 2 // Min value is 2 hours
                binding.tvSelectedHours.text = "$eatingWindowHours hours"
                updateEatingSchedule()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun loadUserData() {
        CoroutineScope(Dispatchers.IO).launch {
            userSettings = userSettingsRepository.getUserSettings()
            
            // Get latest weight record
            val latestWeightRecord = weightRepository.getLatestWeightRecord()
            currentWeightRecord = latestWeightRecord
            weight = latestWeightRecord?.weight ?: 0f
            
            withContext(Dispatchers.Main) {
                userSettings?.let { settings ->
                    // Fill personal info
                    binding.etName.setText(settings.name)
                    binding.etAge.setText(settings.age.toString())
                    
                    // Fill body metrics
                    binding.etHeight.setText(settings.height.toString())
                    height = settings.height
                    updateBMI()
                    
                    // Fill sleep schedule
                    binding.etBedtime.setText(settings.bedTime)
                    binding.etWakeup.setText(settings.wakeUpTime)
                    
                    // Fill fasting schedule
                    binding.etEatingStart.setText(settings.eatingStartTime)
                    eatingWindowHours = settings.eatingWindowHours
                    binding.seekBarEatingWindow.progress = eatingWindowHours - 2 // Adjust for the minimum of 2
                    binding.tvSelectedHours.text = "$eatingWindowHours hours"
                    updateEatingSchedule()
                }
            }
        }
    }
    
    private fun saveUserData() {
        // Validate inputs
        if (!validateInputs()) {
            return
        }
        
        // Get values from UI
        val name = binding.etName.text.toString()
        val age = binding.etAge.text.toString().toIntOrNull() ?: 0
        val height = binding.etHeight.text.toString().toFloatOrNull() ?: 0f
        val bedTime = binding.etBedtime.text.toString()
        val wakeUpTime = binding.etWakeup.text.toString()
        val eatingStartTime = binding.etEatingStart.text.toString()
        
        // Create updated settings object
        val updatedSettings = UserSettings(
            id = 1,
            name = name,
            age = age,
            height = height,
            bedTime = bedTime,
            wakeUpTime = wakeUpTime,
            eatingStartTime = eatingStartTime,
            eatingWindowHours = eatingWindowHours,
            setupCompleted = true
        )
        
        // Save settings to repository
        CoroutineScope(Dispatchers.IO).launch {
            userSettingsRepository.updateUserSettings(updatedSettings)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ProfileActivity, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun validateInputs(): Boolean {
        // Validate name
        if (binding.etName.text.toString().isBlank()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Validate age
        val age = binding.etAge.text.toString().toIntOrNull()
        if (age == null || age <= 0 || age > 120) {
            Toast.makeText(this, "Please enter a valid age (1-120)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Validate height
        val height = binding.etHeight.text.toString().toFloatOrNull()
        if (height == null || height <= 0 || height > 300) {
            Toast.makeText(this, "Please enter a valid height (in cm)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Validate eating window
        if (eatingWindowHours <= 0 || eatingWindowHours >= 24) {
            Toast.makeText(this, "Please enter a valid eating window (1-23 hours)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }
    
    private fun showTimePickerDialog(currentTime: String, onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        
        // Parse current time or use current time if invalid
        try {
            val timeParts = currentTime.split(":")
            if (timeParts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                calendar.set(Calendar.MINUTE, timeParts[1].toInt())
            }
        } catch (e: Exception) {
            // Use current time if parsing fails
        }
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        val timePickerDialog = TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                onTimeSelected(formattedTime)
            },
            hour,
            minute,
            true // 24-hour format
        )
        
        timePickerDialog.show()
    }
    
    private fun updateBMI() {
        if (height <= 0f || weight <= 0f) {
            binding.tvBmi.text = "BMI: N/A (No weight data)"
            return
        }
        
        // Calculate BMI
        val heightInMeters = height / 100f
        val bmiValue = weight / (heightInMeters * heightInMeters)
        
        // Format BMI to one decimal place
        val df = DecimalFormat("#.0")
        val formattedBmi = df.format(bmiValue)
        
        // Determine BMI category
        val category = when {
            bmiValue < 18.5f -> "Underweight"
            bmiValue < 25f -> "Healthy"
            bmiValue < 30f -> "Overweight"
            else -> "Obese"
        }
        
        binding.tvBmi.text = "BMI: $formattedBmi ($category)"
    }
    
    private fun updateEatingSchedule() {
        val eatingStartTime = binding.etEatingStart.text.toString()
        
        if (eatingStartTime.isBlank() || eatingWindowHours <= 0) {
            return
        }

        // Calculate fasting hours
        val fastingHours = 24 - eatingWindowHours
        binding.tvFastingHours.text = "Fasting hours: $fastingHours"
        
        // Calculate eating end time
        val endTime = calculateEatingEndTime(eatingStartTime, eatingWindowHours)
        binding.tvEatingSchedule.text = "Eating window: $eatingStartTime - $endTime"
    }
    
    private fun calculateEatingEndTime(startTime: String, windowHours: Int): String {
        // Parse start time
        val parts = startTime.split(":")
        if (parts.size != 2) return ""
        
        var hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        
        // Add window hours
        hours += windowHours
        
        // Handle overflow
        if (hours >= 24) {
            hours -= 24
        }
        
        // Format as HH:MM
        return String.format("%02d:%02d", hours, minutes)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 