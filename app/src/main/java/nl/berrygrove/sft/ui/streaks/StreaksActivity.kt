package nl.berrygrove.sft.ui.streaks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.databinding.ActivityStreaksBinding
import nl.berrygrove.sft.databinding.CardAchievementsBinding
import nl.berrygrove.sft.databinding.CardStreakBinding
import nl.berrygrove.sft.databinding.CardOverallScoreBinding
import kotlin.math.ceil
import nl.berrygrove.sft.utils.EmojiUtils

class StreaksActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityStreaksBinding
    private lateinit var streaksViewModel: StreaksViewModel
    private lateinit var achievementAdapter: AchievementAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreaksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_streaks)
        
        val application = application as SleepFastTrackerApplication
        val sleepRepository = application.sleepRepository
        val fastingRepository = application.fastingRepository
        val userSettingsRepository = application.userSettingsRepository
        val weightRecordRepository = application.weightRecordRepository
        val achievementRepository = application.achievementRepository
        
        val factory = StreaksViewModelFactory(
            sleepRepository, 
            fastingRepository, 
            userSettingsRepository,
            weightRecordRepository,
            achievementRepository
        )
        streaksViewModel = ViewModelProvider(this, factory).get(StreaksViewModel::class.java)
        
        // Set up streak cards
        setupBedtimeStreakCard()
        setupFastingStreakCard()
        setupOverallScoreCard()
        setupAchievementsCard()
    }
    
    private fun setupBedtimeStreakCard() {
        val cardBinding = CardStreakBinding.bind(binding.cardBedtimeStreak.root)
        cardBinding.tvStreakTitle.text = getString(R.string.card_title_bedtime)
        
        streaksViewModel.bedtimeStreak.observe(this) { streak ->
            val emoji = EmojiUtils.getEmoticonsByStreakLength(streak)
            cardBinding.tvStreakValue.text = getString(R.string.streak_days, streak) + " " + emoji
        }
        
        cardBinding.tvStreakExplanation.text = getString(R.string.bedtime_streak_explanation) + 
            "\n\n" + getString(R.string.streak_emoji_info)
    }
    
    private fun setupFastingStreakCard() {
        val cardBinding = CardStreakBinding.bind(binding.cardFastingStreak.root)
        cardBinding.tvStreakTitle.text = getString(R.string.card_title_fasting)
        
        streaksViewModel.fastingStreak.observe(this) { streak ->
            val emoji = EmojiUtils.getEmoticonsByStreakLength(streak)
            cardBinding.tvStreakValue.text = getString(R.string.streak_days, streak) + " " + emoji
        }
        
        // Update explanation with required fast duration
        streaksViewModel.userSettings.observe(this) { settings ->
            if (settings != null) {
                val requiredFastHours = 24 - settings.eatingWindowHours - 0.5
                cardBinding.tvStreakExplanation.text = 
                    getString(R.string.fasting_streak_explanation, ceil(requiredFastHours).toInt()) +
                    "\n\n" + getString(R.string.streak_emoji_info)
            }
        }
    }
    
    private fun setupOverallScoreCard() {
        val cardBinding = CardOverallScoreBinding.bind(binding.cardOverallScore.root)
        
        streaksViewModel.overallScore.observe(this) { score ->
            cardBinding.tvScoreValue.text = score.toString()
        }
        
        streaksViewModel.weightLost.observe(this) { weightLost ->
            cardBinding.tvWeightLost.text = getString(R.string.weight_lost, weightLost)
            cardBinding.tvExplanation.text = getString(R.string.overall_score_explanation)
        }
    }
    
    private fun setupAchievementsCard() {
        val cardBinding = CardAchievementsBinding.bind(binding.cardAchievements.root)
        
        // Set up RecyclerView with adapter
        achievementAdapter = AchievementAdapter()
        cardBinding.rvAchievements.apply {
            layoutManager = LinearLayoutManager(this@StreaksActivity)
            adapter = achievementAdapter
            isNestedScrollingEnabled = false // Disable nested scrolling to let parent handle scrolling
        }
        
        // Initially set to show category icons if "all" tab is selected by default
        achievementAdapter.setShowCategoryIcons(cardBinding.tabAchievements.selectedTabPosition == 0)
        
        // Observe achievements
        streaksViewModel.achievements.observe(this) { achievements ->
            achievementAdapter.submitList(achievements)
        }
        
        // Set up achievement counts
        streaksViewModel.achievedAchievementsCount.observe(this) { achievedCount ->
            streaksViewModel.totalAchievements.observe(this) { totalCount ->
                if (totalCount > 0) {
                    cardBinding.tvAchievementsCount.text = getString(
                        R.string.achievements_unlocked_format,
                        achievedCount,
                        totalCount
                    )
                }
            }
        }
        
        // Set up achievement points
        streaksViewModel.achievementPoints.observe(this) { points ->
            cardBinding.tvAchievementsPointsSummary.text = 
                getString(R.string.achievements_points_earned, points)
        }
        
        // Set up tab selection listener
        cardBinding.tabAchievements.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val category = when (tab.position) {
                    0 -> "all"
                    1 -> "sleep"
                    2 -> "fasting"
                    3 -> "weight"
                    else -> "all"
                }
                
                // Show category icons only for the "all" tab (position 0)
                achievementAdapter.setShowCategoryIcons(tab.position == 0)
                
                streaksViewModel.setSelectedCategory(category)
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    override fun onResume() {
        super.onResume()
        // Force check achievements when activity is resumed
        streaksViewModel.checkAchievementsAndRefresh()
        
        // Reapply current tab selection to ensure proper filtering
        val cardBinding = CardAchievementsBinding.bind(binding.cardAchievements.root)
        val selectedTab = cardBinding.tabAchievements.selectedTabPosition
        val category = when (selectedTab) {
            0 -> "all"
            1 -> "sleep"
            2 -> "fasting"
            3 -> "weight"
            else -> "all"
        }
        
        // Show category icons only for the "all" tab (position 0)
        achievementAdapter.setShowCategoryIcons(selectedTab == 0)
        
        streaksViewModel.setSelectedCategory(category)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 