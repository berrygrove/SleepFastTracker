package nl.berrygrove.sft.ui.debug

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.databinding.ActivityDebugBinding
import java.text.SimpleDateFormat
import java.util.*

class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding
    private lateinit var viewModel: DebugViewModel
    private val debugLogBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.debug_title)
        
        // Setup ViewModel
        val application = application as SleepFastTrackerApplication
        val factory = DebugViewModelFactory(
            application.userSettingsRepository,
            application.fastingRepository,
            application.sleepRepository,
            application.weightRecordRepository,
            application.achievementRepository,
            application
        )
        viewModel = ViewModelProvider(this, factory)[DebugViewModel::class.java]
        
        // Setup weight records observer
        setupWeightRecordsObserver()
        
        // Setup achievements observer
        setupAchievementsObserver()
        
        // Setup collapsible cards
        setupCollapsibleCards()
        
        // Load data
        loadData(false)
        logDebug("DebugActivity loaded")
        
        // Setup refresh button
        binding.btnRefresh.setOnClickListener {
            loadData(true)
            viewModel.refreshWeightRecords()
            logDebug("Data refreshed")
        }
        
        // Setup fasting delta backfill button
        binding.btnRunFastingDeltaBackfill.setOnClickListener {
            runFastingDeltaBackfill()
        }
        
        // Setup fasting delta backfill button inside card
        binding.btnRunFastingDeltaBackfillInCard.setOnClickListener {
            runFastingDeltaBackfill()
        }
        
        // Setup clear logs button
        binding.btnClearLogs.setOnClickListener {
            clearLogs()
        }
        
        // Setup recalculate weight achievements button
        binding.btnRecalculateWeightAchievements.setOnClickListener {
            recalculateWeightAchievements()
        }
        
        // Setup debug switch
        setupDebugSwitch()
        
        // Display any existing logs
        updateDebugLogDisplay()
    }
    
    /**
     * Sets up all collapsible cards with click listeners
     */
    private fun setupCollapsibleCards() {
        // Setup each card with its header, content, and arrow
        setupCollapsibleCard(
            binding.userSettingsHeader,
            binding.userSettingsContent,
            binding.userSettingsArrow
        )
        
        setupCollapsibleCard(
            binding.fastingRecordsHeader,
            binding.fastingRecordsContent,
            binding.fastingRecordsArrow
        )
        
        setupCollapsibleCard(
            binding.sleepRecordsHeader,
            binding.sleepRecordsContent,
            binding.sleepRecordsArrow
        )
        
        setupCollapsibleCard(
            binding.weightRecordsHeader,
            binding.weightRecordsContent,
            binding.weightRecordsArrow
        )
        
        setupCollapsibleCard(
            binding.achievementsHeader,
            binding.achievementsContent,
            binding.achievementsArrow
        )
        
        setupCollapsibleCard(
            binding.debugLogHeader,
            binding.debugLogContent,
            binding.debugLogArrow
        )

        // Initially collapse all cards
        collapseAllCards()
    }
    
    /**
     * Sets up a single collapsible card with click listener
     */
    private fun setupCollapsibleCard(header: LinearLayout, content: LinearLayout, arrow: ImageView) {
        header.setOnClickListener {
            // Toggle content visibility
            val isVisible = content.visibility == View.VISIBLE
            content.visibility = if (isVisible) View.GONE else View.VISIBLE
            
            // Toggle arrow direction
            arrow.setImageResource(
                if (isVisible) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up
            )
        }
    }

    /**
     * Collapses all cards initially
     */
    private fun collapseAllCards() {
        // Set all content sections to GONE
        binding.userSettingsContent.visibility = View.GONE
        binding.fastingRecordsContent.visibility = View.GONE
        binding.sleepRecordsContent.visibility = View.GONE
        binding.weightRecordsContent.visibility = View.GONE
        binding.achievementsContent.visibility = View.GONE
        binding.debugLogContent.visibility = View.GONE
        
        // Set all arrows to point down (collapsed state)
        binding.userSettingsArrow.setImageResource(R.drawable.ic_arrow_down)
        binding.fastingRecordsArrow.setImageResource(R.drawable.ic_arrow_down)
        binding.sleepRecordsArrow.setImageResource(R.drawable.ic_arrow_down)
        binding.weightRecordsArrow.setImageResource(R.drawable.ic_arrow_down)
        binding.achievementsArrow.setImageResource(R.drawable.ic_arrow_down)
        binding.debugLogArrow.setImageResource(R.drawable.ic_arrow_down)
    }
    
    /**
     * Run the fasting delta backfill process
     */
    private fun runFastingDeltaBackfill() {
        logDebug("Starting fasting delta backfill process...")
        binding.btnRunFastingDeltaBackfill.isEnabled = false
        binding.btnRunFastingDeltaBackfillInCard.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val updatedCount = withContext(Dispatchers.IO) {
                    viewModel.runFastingDeltaBackfill()
                }
                
                logDebug("Fasting delta backfill completed. Records processed: $updatedCount")
                
                // Refresh fasting records display
                val fastingRecords = withContext(Dispatchers.IO) {
                    viewModel.getAllFastingRecords()
                }
                
                // Format fasting records to include delta_minutes
                val formattedFastingRecords = fastingRecords.joinToString("\n\n") { record ->
                    val stateText = if (record.state) "Fasting" else "Eating"
                    val deltaText = if (record.delta_minutes != null) {
                        val sign = if (record.delta_minutes >= 0) "+" else ""
                        "Delta: $sign${record.delta_minutes} minutes"
                    } else {
                        "Delta: N/A"
                    }
                    
                    "ID: ${record.id}\n" +
                    "State: $stateText\n" +
                    "Timestamp: ${record.timestamp}\n" +
                    deltaText
                }
                
                binding.fastingRecordsText.text = formattedFastingRecords
            } catch (e: Exception) {
                logDebug("Error during fasting delta backfill: ${e.message}")
                e.printStackTrace()
            } finally {
                binding.btnRunFastingDeltaBackfill.isEnabled = true
                binding.btnRunFastingDeltaBackfillInCard.isEnabled = true
            }
        }
    }
    
    private fun setupDebugSwitch() {
        // Initialize switch state based on current debug setting
        binding.switchDebugEnabled.isChecked = SleepFastTrackerApplication.isDebugEnabled()
        
        // Add listener to update debug enabled state
        binding.switchDebugEnabled.setOnCheckedChangeListener { _, isChecked ->
            SleepFastTrackerApplication.setDebugEnabled(isChecked)
            logDebug("Debug logging ${if (isChecked) "enabled" else "disabled"}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update debug log display each time the activity resumes
        updateDebugLogDisplay()
    }
    
    private fun setupWeightRecordsObserver() {
        viewModel.weightRecords.observe(this) { weightRecords ->
            val weightTextTitle = "Weight Records (Count: ${weightRecords.size})"
            val weightText = if (weightRecords.isNotEmpty()) {
                weightRecords.joinToString("\n\n") { record ->
                    "ID: ${record.id}\nWeight: ${record.weight} kg\nTimestamp: ${record.timestamp}"
                }
            } else {
                "No weight records found (Total count: 0)"
            }
            binding.weightRecordsTitle.text = weightTextTitle
            binding.weightRecordsText.text = weightText
        }
    }
    
    private fun setupAchievementsObserver() {
        viewModel.achievements.observe(this) { achievements ->
            val achievementTextTitle = "Achievement Records (Count: ${achievements.size})"
            val achievementText = if (achievements.isNotEmpty()) {
                val achievedCount = achievements.count { it.achieved }
                val totalPoints = achievements.filter { it.achieved }.sumOf { it.points }
                
                "Total Achieved: $achievedCount/${achievements.size}\n" +
                "Total Points: $totalPoints\n\n" +
                achievements.joinToString("\n\n") { achievement ->
                    "ID: ${achievement.id}\n" +
                    "Name: ${achievement.name}\n" +
                    "Category: ${achievement.category}\n" +
                    "Points: ${achievement.points}\n" +
                    "Achieved: ${achievement.achieved}\n" +
                    "Description: ${achievement.description}\n" +
                    "Emoticon: ${achievement.emoticon}\n" +
                    "Threshold: ${achievement.threshold}"
                }
            } else {
                "No achievement records found (Total count: 0)"
            }
            binding.achievementsTitle.text = achievementTextTitle
            binding.achievementsText.text = achievementText
        }
    }
    
    private fun loadData(forceRefresh: Boolean) {
        logDebug("Loading data" + if (forceRefresh) " (forced refresh)" else "")
        lifecycleScope.launch {
            try {
                // Get UserSettings using the ViewModel
                val userSettings = withContext(Dispatchers.IO) {
                    viewModel.getUserSettings()
                }
                
                if (userSettings != null) {
                    binding.userSettingsText.text = userSettings.toString()
                    logDebug("User settings loaded: ${userSettings.name}, age: ${userSettings.age}, bedtime: ${userSettings.bedTime}, wake-up time: ${userSettings.wakeUpTime}")
                } else {
                    binding.userSettingsText.text = "No user settings found"
                    logDebug("No user settings found")
                }

                // Get Fasting records using the ViewModel
                val fastingRecords = withContext(Dispatchers.IO) {
                    viewModel.getAllFastingRecords()
                }
                
                // Format fasting records to include delta_minutes
                val formattedFastingRecords = fastingRecords.joinToString("\n\n") { record ->
                    val stateText = if (record.state) "Fasting" else "Eating"
                    val deltaText = if (record.delta_minutes != null) {
                        val sign = if (record.delta_minutes >= 0) "+" else ""
                        "Delta: $sign${record.delta_minutes} minutes"
                    } else {
                        "Delta: N/A"
                    }
                    
                    "ID: ${record.id}\n" +
                    "State: $stateText\n" +
                    "Timestamp: ${record.timestamp}\n" +
                    deltaText
                }
                
                binding.fastingRecordsText.text = formattedFastingRecords
                logDebug("Loaded ${fastingRecords.size} fasting records")

                // Get Sleep records using the ViewModel
                val sleepRecords = withContext(Dispatchers.IO) {
                    viewModel.getAllSleepRecords()
                }
                binding.sleepRecordsText.text = sleepRecords.joinToString("\n")
                logDebug("Loaded ${sleepRecords.size} sleep records")
                
                // Get Achievement records using the ViewModel
                val achievements = withContext(Dispatchers.IO) {
                    viewModel.getAllAchievements()
                }
                logDebug("Loaded ${achievements.size} achievement records")
            } catch (e: Exception) {
                logDebug("Error loading data: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Updates the debug log display from the global log storage
     */
    private fun updateDebugLogDisplay() {
        // Get logs from global storage
        val logs = SleepFastTrackerApplication.getDebugLogs()
        
        // Display logs
        binding.debugLogText.text = logs
        
        // Scroll to bottom
        binding.debugLogText.post {
            val scrollView = binding.debugLogText.parent as ScrollView
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    /**
     * Adds a debug log message to the debug log TextView with different colors based on log level
     */
    private fun logDebug(message: String) {
        // Add to global logs
        SleepFastTrackerApplication.addDebugLog("DebugActivity", message)
        
        // Update display
        updateDebugLogDisplay()
    }
    
    /**
     * Clears all debug logs
     */
    private fun clearLogs() {
        SleepFastTrackerApplication.clearDebugLogs()
        binding.debugLogText.text = ""
        logDebug("Logs cleared")
    }
    
    /**
     * Recalculates weight achievements
     */
    private fun recalculateWeightAchievements() {
        logDebug("Starting weight achievement recalculation process...")
        binding.btnRecalculateWeightAchievements.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    viewModel.recalculateWeightAchievements()
                }
                
                if (result.first) {
                    logDebug("Weight achievement recalculation completed successfully: ${result.second}")
                } else {
                    logDebug("Weight achievement recalculation failed: ${result.second}")
                }
                
                // Refresh achievements data
                val achievements = withContext(Dispatchers.IO) {
                    viewModel.getAllAchievements()
                }
                
                // Filter to show only weight achievements
                val weightAchievements = achievements.filter { it.category == "weight" }
                
                // Format achievements to show name, threshold and achieved status
                val formattedAchievements = weightAchievements.joinToString("\n\n") { achievement ->
                    val achievedStatus = if (achievement.achieved) "Achieved" else "Not achieved"
                    "${achievement.name} (${achievement.emoticon})\n" +
                    "Description: ${achievement.description}\n" +
                    "Threshold: ${achievement.threshold} kg\n" +
                    "Points: ${achievement.points}\n" +
                    "Status: $achievedStatus"
                }
                
                binding.achievementsText.text = formattedAchievements
                
                // Show achievements section
                binding.achievementsContent.visibility = View.VISIBLE
                binding.achievementsArrow.setImageResource(R.drawable.ic_arrow_up)
                
            } catch (e: Exception) {
                logDebug("Error during weight achievement recalculation: ${e.message}")
                e.printStackTrace()
            } finally {
                binding.btnRecalculateWeightAchievements.isEnabled = true
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 