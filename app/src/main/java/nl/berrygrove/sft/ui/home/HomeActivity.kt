package nl.berrygrove.sft.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.databinding.ActivityHomeBinding
import nl.berrygrove.sft.ui.checkin.CheckInActivity
import nl.berrygrove.sft.ui.debug.DebugActivity
import nl.berrygrove.sft.ui.profile.ProfileActivity
import nl.berrygrove.sft.ui.progress.ProgressActivity
import nl.berrygrove.sft.ui.settings.SettingsActivity
import nl.berrygrove.sft.ui.streaks.StreaksActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers

class HomeActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupToolbar()
        setupBottomNavigation()
        setupFastingCard()
        setupBedTimeCard()
        setupLastCheckInCard()
        
        observeData()
        
        // Make sure all achievements exist in the database
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                (application as SleepFastTrackerApplication).achievementRepository.ensureAchievementsExist()
            }
        }
    }
    
    private fun setupViewModel() {
        val application = application as SleepFastTrackerApplication
        val fastingRepository = application.fastingRepository
        val sleepRepository = application.sleepRepository
        val weightRepository = application.weightRecordRepository
        val userSettingsRepository = application.userSettingsRepository
        
        val factory = HomeViewModelFactory(
            fastingRepository,
            sleepRepository,
            weightRepository,
            userSettingsRepository,
            application
        )
        
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        
        // Start the countdown timer when screen is visible
        viewModel.startCountdown()
        
        // Refresh the weight data when returning to this screen
        lifecycleScope.launch {
            viewModel.refreshLastWeightRecord()
        }
    }

    override fun onPause() {
        super.onPause()
        
        // Stop the countdown timer when screen is not visible to save battery
        viewModel.stopCountdown()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.topAppBar)
        
        // Setup menu item click listeners
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_profile -> {
                    // Start the profile activity
                    val profileIntent = Intent(this, nl.berrygrove.sft.ui.profile.ProfileActivity::class.java)
                    startActivity(profileIntent)
                    true
                }
                R.id.menu_settings -> {
                    // Start the settings activity
                    val settingsIntent = Intent(this, nl.berrygrove.sft.ui.settings.SettingsActivity::class.java)
                    startActivity(settingsIntent)
                    true
                }
                R.id.menu_check_in -> {
                    // Start the check-in activity
                    val checkInIntent = Intent(this, CheckInActivity::class.java)
                    startActivity(checkInIntent)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_home -> true
            R.id.nav_streaks -> {
                startActivity(Intent(this, StreaksActivity::class.java))
                false
            }
            R.id.nav_progress -> {
                startActivity(Intent(this, ProgressActivity::class.java))
                false
            }
            else -> false
        }
    }

    private fun setupFastingCard() {
        binding.cardFasting.toggleButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.toggleFastingState()
            }
        }
    }

    private fun setupBedTimeCard() {
        binding.cardBedtime.yesButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.setBedtimeResponse(true)
                updateBedtimeResponseButtons(true)
            }
        }
        
        binding.cardBedtime.noButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.setBedtimeResponse(false)
                updateBedtimeResponseButtons(false)
            }
        }
    }
    
    private fun updateBedtimeResponseButtons(showResponseButtons: Boolean) {
        // Show response container
        val responseContainer = binding.cardBedtime.bedtimeQuestionContainer
        responseContainer.visibility = View.VISIBLE
        
        // Set black border on the selected button
        val yesButton = binding.cardBedtime.yesButton
        val noButton = binding.cardBedtime.noButton
        
        // Clear borders
        yesButton.strokeWidth = 0
        noButton.strokeWidth = 0
        
        // Add black border to the selected button
        if (showResponseButtons) {
            // Yes button selected
            yesButton.strokeWidth = 2
            yesButton.strokeColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.black))
        } else {
            // No button selected
            noButton.strokeWidth = 2
            noButton.strokeColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.black))
        }
        
        // Show the countdown timer text
        binding.cardBedtime.responseCountdownText.visibility = View.VISIBLE
    }

    private fun setupLastCheckInCard() {
        binding.cardLastCheckin.checkInButton.setOnClickListener {
            startActivity(Intent(this, CheckInActivity::class.java))
        }
    }

    private fun observeData() {
        // Observe fasting state
        viewModel.currentFastingState.observe(this) { fastingState ->
            binding.cardFasting.apply {
                fastingStateText.text = if (fastingState) getString(R.string.state_fasting) else getString(R.string.state_eating)
                toggleButton.text = if (fastingState) getString(R.string.start_eating) else getString(R.string.start_fasting)
                
                // Update start/end time labels based on fasting state
                startTimeLabel.text = if (fastingState) "Started" else "Started"
                endTimeLabel.text = if (fastingState) "Ends" else "Ends"
            }
        }
        
        // Observe overall score
        viewModel.overallScore.observe(this) { score ->
            binding.overallScoreText.text = score.toString()
        }
        
        // Observe fasting countdown
        viewModel.fastingCountdown.observe(this) { countdown ->
            binding.cardFasting.countdownText.text = countdown
            
            // Update progress circle based on countdown percentage
            viewModel.fastingProgress.observe(this) { progress ->
                // Set progress value
                binding.cardFasting.progressCircle.progress = progress
                
                // Update color based on progress - fade from primary (purple) to success (green)
                val primaryColor = getColor(R.color.primary)
                val successColor = getColor(R.color.magenta_medium)
                val interpolatedColor = nl.berrygrove.sft.utils.ColorUtils.interpolateColor(
                    primaryColor, 
                    successColor, 
                    progress
                )
                
                // Apply the interpolated color to the progress indicator
                binding.cardFasting.progressCircle.setIndicatorColor(interpolatedColor)
            }
        }
        
        // Observe fasting start time
        viewModel.fastingStartTime.observe(this) { startTime ->
            binding.cardFasting.startTimeText.text = startTime
        }
        
        // Observe fasting end time
        viewModel.fastingEndTime.observe(this) { endTime ->
            binding.cardFasting.endTimeText.text = endTime
        }
        
        // Observe fasting streak
        viewModel.fastingStreak.observe(this) { streak ->
            binding.cardFasting.streakText.text = getString(R.string.streak_days, streak)
        }
        
        // Observe bedtime countdown
        viewModel.bedtimeCountdown.observe(this) { countdown ->
            binding.cardBedtime.countdownText.text = countdown
        }
        
        // Observe bedtime progress
        viewModel.bedtimeProgress.observe(this) { progress ->
            // Set progress value for bedtime circle
            binding.cardBedtime.progressCircle.progress = progress
            
            // Update color based on progress - fade from primary (purple) to success (green)
            val primaryColor = getColor(R.color.primary)
            val successColor = getColor(R.color.bluepurple_light)
            val interpolatedColor = nl.berrygrove.sft.utils.ColorUtils.interpolateColor(
                primaryColor, 
                successColor, 
                progress
            )
            
            // Apply the interpolated color to the progress indicator
            binding.cardBedtime.progressCircle.setIndicatorColor(interpolatedColor)
        }
        
        // Observe bedtime countdown label
        viewModel.bedtimeCountdownLabel.observe(this) { label ->
            // Direct reference to the countdownLabel TextView since it's in the binding hierarchy
            binding.cardBedtime.countdownLabel.text = label
        }
        
        // Observe bedtime warning state
        viewModel.bedtimeWarningActive.observe(this) { showWarning ->
            binding.cardBedtime.warningContainer.visibility = if (showWarning) View.VISIBLE else View.GONE
        }
        
        // Observe wake-up countdown state
        viewModel.isShowingWakeUpCountdown.observe(this) { showingWakeUpCountdown ->
            val title = if (showingWakeUpCountdown) {
                getString(R.string.card_title_wake_up)
            } else {
                getString(R.string.card_title_bedtime)
            }
            binding.cardBedtime.titleText.text = title
        }
        
        // Observe bedtime question visibility
        viewModel.shouldShowBedtimeQuestion.observe(this) { shouldShow ->
            binding.cardBedtime.bedtimeQuestionContainer.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }
        
        // Observe bedtime streak
        viewModel.bedtimeStreak.observe(this) { streak ->
            binding.cardBedtime.streakText.text = getString(R.string.streak_days, streak)
        }
        
        // Observe bedtime info
        viewModel.bedtimeInfo.observe(this) { bedtime ->
            binding.cardBedtime.bedtimeText.text = "Bedtime: $bedtime"
        }
        
        // Observe last check-in weight
        viewModel.lastWeight.observe(this) { weight ->
            binding.cardLastCheckin.weightText.text = getString(R.string.weight_kg, weight)
        }
        
        // Observe BMI
        viewModel.currentBmi.observe(this) { bmi ->
            binding.cardLastCheckin.bmiText.text = getString(R.string.bmi_value, bmi)
            
            // Set BMI progress bar
            // Normal BMI range is 18.5-25, but we'll show up to 35 for the progress bar
            val minBmi = 18.5f
            val maxBmi = 35f
            val progressPercent = ((bmi - minBmi) / (maxBmi - minBmi) * 100).toInt().coerceIn(0, 100)
            binding.cardLastCheckin.bmiProgressBar.progress = progressPercent
            
            // Set color based on BMI category
            val colorRes = when {
                bmi < 18.5f -> R.color.magenta_medium // Underweight - using yellow-gold from secondary #1
                bmi < 25f -> R.color.bluepurple_light // Normal - using light orange-amber from secondary #2
                bmi < 30f -> R.color.magenta_medium // Overweight - using yellow-gold from secondary #1
                else -> R.color.magenta_dark  // Obese - using darker yellow-gold from secondary #1
            }
            binding.cardLastCheckin.bmiProgressBar.setIndicatorColor(ContextCompat.getColor(this, colorRes))
        }
        
        // Observe days since last check-in
        viewModel.daysSinceLastCheckin.observe(this) { days ->
            if (days == -1) {
                // No weight records exist yet
                binding.cardLastCheckin.lastCheckinText.text = getString(R.string.no_checkin_yet)
            } else {
                // Format as "Last check-in: X days ago"
                val daysText = if (days == 0) {
                    getString(R.string.today)
                } else if (days == 1) {
                    getString(R.string.yesterday)
                } else {
                    resources.getQuantityString(R.plurals.days_ago, days, days)
                }
                binding.cardLastCheckin.lastCheckinText.text = getString(R.string.last_checkin_format, daysText)
            }
        }
        
        // Observe bedtime response timer
        viewModel.bedtimeResponseRemainingTime.observe(this) { timeRemaining ->
            val countdownText = binding.cardBedtime.responseCountdownText
            if (timeRemaining > 0) {
                countdownText.text = timeRemaining.toString()
                countdownText.visibility = View.VISIBLE
                
                // Optional: animate the text to make it more noticeable
                countdownText.alpha = 1.0f
                countdownText.animate()
                    .alpha(0.6f)
                    .setDuration(800)
                    .start()
            } else {
                countdownText.visibility = View.GONE
            }
        }
    }

    // Add this method to inflate the top menu
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home_top, menu)
        return true
    }
} 