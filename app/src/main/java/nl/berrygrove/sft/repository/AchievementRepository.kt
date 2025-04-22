package nl.berrygrove.sft.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.data.dao.AchievementDao
import nl.berrygrove.sft.data.model.Achievement
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Repository for accessing and managing achievements
 */
class AchievementRepository(private val achievementDao: AchievementDao) {
    
    // Get all achievements
    val allAchievements: Flow<List<Achievement>> = achievementDao.getAllAchievements()
    
    // Get all achieved achievements
    val achievedAchievements: Flow<List<Achievement>> = achievementDao.getAchievedAchievements()
    
    // Get achievements by category
    fun getAchievementsByCategory(category: String): Flow<List<Achievement>> {
        return achievementDao.getAchievementsByCategory(category)
    }
    
    // Get count of achieved achievements
    val achievedCount: Flow<Int> = achievementDao.getAchievedAchievementsCount()
    
    // Get total points from achieved achievements
    val totalPoints: Flow<Int?> = achievementDao.getTotalAchievementPoints()
    
    // Update an achievement's achieved status
    suspend fun updateAchievedStatus(id: Long, achieved: Boolean) {
        achievementDao.updateAchievedStatus(id, achieved)
    }
    
    // Insert multiple achievements
    suspend fun insertAll(achievements: List<Achievement>) {
        achievementDao.insertAll(achievements)
    }
    
    // Check and unlock sleep achievements based on streak count
    suspend fun checkAndUnlockSleepAchievements(streakDays: Int) {
        val eligibleAchievements = achievementDao.getUnachievedEligibleAchievements("sleep", streakDays.toFloat())
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Found ${eligibleAchievements.size} eligible sleep achievements for streak: $streakDays")
        for (achievement in eligibleAchievements) {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Unlocking sleep achievement: ${achievement.name}")
            achievementDao.updateAchievedStatus(achievement.id, true)
        }
    }
    
    // Check and unlock fasting achievements based on streak count
    suspend fun checkAndUnlockFastingAchievements(streakDays: Int) {
        val eligibleAchievements = achievementDao.getUnachievedEligibleAchievements("fasting", streakDays.toFloat())
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Found ${eligibleAchievements.size} eligible fasting achievements for streak: $streakDays")
        for (achievement in eligibleAchievements) {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Unlocking fasting achievement: ${achievement.name}")
            achievementDao.updateAchievedStatus(achievement.id, true)
        }
    }
    
    // Check and unlock weight loss achievements based on weight lost
    suspend fun checkAndUnlockWeightAchievements(weightLostKg: Float) {
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Checking weight achievements for weight loss of $weightLostKg kg")
        
        // Get all eligible weight loss achievements based on weight lost
        val eligibleAchievements = achievementDao.getUnachievedEligibleWeightAchievements(weightLostKg)
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Found ${eligibleAchievements.size} eligible weight achievements to unlock")
        
        // If there are eligible achievements, update their status to achieved
        if (eligibleAchievements.isNotEmpty()) {
            for (achievement in eligibleAchievements) {
                SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Unlocking weight achievement: ${achievement.name} for weight loss of $weightLostKg kg")
                achievementDao.updateAchievedStatus(achievement.id, true)
            }
        }
    }
    
    /**
     * Recalculate weight achievements based on the user's height and start weight
     * Creates 10 equally spaced goals between start weight and perfect weight (BMI 20)
     */
    suspend fun recalculateWeightAchievements(height: Float, startWeight: Float) {
        try {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Recalculating weight achievements. Height: $height cm, Start weight: $startWeight kg")
            
            // First, delete all existing weight achievements
            achievementDao.deleteAchievementsByCategory("weight")
            
            // Calculate the user's start BMI
            val heightInMeters = height / 100f
            val startBmi = startWeight / (heightInMeters * heightInMeters)
            
            // Calculate the perfect weight (BMI of 20)
            val perfectWeight = 20f * heightInMeters * heightInMeters
            
            // Make sure perfect weight is less than start weight (we're targeting weight loss)
            if (perfectWeight >= startWeight) {
                SleepFastTrackerApplication.addDebugLog("AchievementRepository", 
                    "Perfect weight ($perfectWeight) >= start weight ($startWeight). Using fixed weight loss goals.")
                // Create default achievements with fixed weight loss goals
                createDefaultWeightAchievements()
                return
            }
            
            // Calculate total weight to lose
            val totalWeightToLose = startWeight - perfectWeight
            
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", 
                "Start BMI: $startBmi, Perfect weight: $perfectWeight kg, Total to lose: $totalWeightToLose kg")
            
            // Generate 10 goals between start weight and perfect weight
            val rawGoals = mutableListOf<Float>()
            for (i in 1..10) {
                // Calculate the weight loss for this goal (as a percentage of the total)
                val percentageToLose = i / 10f
                val weightLoss = totalWeightToLose * percentageToLose
                rawGoals.add(weightLoss)
            }
            
            // Round each goal to 0.5kg increments
            val roundedGoals = rawGoals.map { roundToHalf(it) }.toMutableList()
            
            // Check for overlapping goals and adjust by rounding to 0.25kg if needed
            for (i in 1 until roundedGoals.size) {
                if (roundedGoals[i] <= roundedGoals[i-1]) {
                    // If this goal is not bigger than the previous one, round to 0.25kg increments
                    roundedGoals[i] = roundToQuarter(rawGoals[i])
                    // If they're still the same, force a minimum increment
                    if (roundedGoals[i] <= roundedGoals[i-1]) {
                        roundedGoals[i] = roundedGoals[i-1] + 0.25f
                    }
                }
            }
            
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Calculated weight loss goals: ${roundedGoals.joinToString()}")
            
            // Create the achievements with dynamic goals
            val weightAchievements = mutableListOf<Achievement>()
            
            // Names and emoticons for the achievements (in order)
            val achievementData = listOf(
                Pair("Light Starter", "😊"),
                Pair("On a Roll", "😃"),
                Pair("Momentum Maker", "😄"),
                Pair("Scale Shaker", "🌱"),
                Pair("Milestone Maker", "😎"),
                Pair("Halfway Hero", "💪"),
                Pair("Titan Tamer", "🌟"),
                Pair("Weight Warrior", "👑"),
                Pair("Weight Conqueror", "🏆"),
                Pair("Transformation Master", "⚜️")
            )
            
            // The points for each achievement (scaled by milestone)
            val basePoints = 15
            
            // Create the achievements with the calculated thresholds
            for (i in roundedGoals.indices) {
                val goal = roundedGoals[i]
                val (name, emoticon) = achievementData[i]
                val points = basePoints * (i + 1)
                
                val achievement = Achievement(
                    name = name,
                    points = points,
                    category = "weight",
                    description = "Lost ${goal} kg total.",
                    emoticon = emoticon,
                    threshold = goal
                )
                weightAchievements.add(achievement)
            }
            
            // Add the achievements to the database
            achievementDao.insertAll(weightAchievements)
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Generated ${weightAchievements.size} custom weight achievements")
            
        } catch (e: Exception) {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Error recalculating weight achievements: ${e.message}")
            // If anything goes wrong, create the default achievements
            createDefaultWeightAchievements()
        }
    }
    
    // Helper function to round a number to the nearest 0.5
    private fun roundToHalf(value: Float): Float {
        return (ceil(value * 2) / 2)
    }
    
    // Helper function to round a number to the nearest 0.25
    private fun roundToQuarter(value: Float): Float {
        return (ceil(value * 4) / 4)
    }
    
    // Create the default weight achievements with fixed thresholds
    private suspend fun createDefaultWeightAchievements() {
        val weightAchievements = listOf(
            Achievement(name = "Light Starter", points = 15, category = "weight", description = "Lost 1 kg total.", emoticon = "😊", threshold = 1f),
            Achievement(name = "On a Roll", points = 30, category = "weight", description = "Lost 2 kg total.", emoticon = "😃", threshold = 2f),
            Achievement(name = "Momentum Maker", points = 75, category = "weight", description = "Lost 5 kg total.", emoticon = "😄", threshold = 5f),
            Achievement(name = "Scale Shaker", points = 105, category = "weight", description = "Lost 7 kg total.", emoticon = "🌱", threshold = 7f),
            Achievement(name = "Milestone Maker", points = 150, category = "weight", description = "Lost 10 kg total.", emoticon = "😎", threshold = 10f),
            Achievement(name = "Halfway Hero", points = 225, category = "weight", description = "Lost 15 kg total.", emoticon = "💪", threshold = 15f),
            Achievement(name = "Titan Tamer", points = 300, category = "weight", description = "Lost 20 kg total.", emoticon = "🌟", threshold = 20f),
            Achievement(name = "Weight Warrior", points = 375, category = "weight", description = "Lost 25 kg total.", emoticon = "👑", threshold = 25f),
            Achievement(name = "Weight Conqueror", points = 450, category = "weight", description = "Lost 30 kg total.", emoticon = "🏆", threshold = 30f),
            Achievement(name = "Transformation Master", points = 525, category = "weight", description = "Lost 35 kg total.", emoticon = "⚜️", threshold = 35f)
        )
        
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Using default weight achievements")
        achievementDao.insertAll(weightAchievements)
    }
    
    /**
     * Check if achievements exist in the database and create them if they don't
     */
    suspend fun ensureAchievementsExist() {
        val count = achievementDao.getAchievementCount()
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Current achievement count in database: $count")
        
        // Check for duplicate achievements
        if (count > 32) { // There should be exactly 32 achievements (11 sleep + 11 fasting + 10 weight)
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Too many achievements found, cleaning duplicates...")
            cleanupDuplicateAchievements()
            return // After cleanup, this method will be called again on next check
        }
        
        if (count < 30) {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Achievement count incorrect ($count found, expected 30+), reinitializing...")
            cleanupDuplicateAchievements()
            initializeAchievements()
        }
    }
    
    /**
     * Clean up duplicate achievements by removing all and recreating them
     */
    private suspend fun cleanupDuplicateAchievements() {
        try {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Deleting all achievements to fix duplicates/incorrect count")
            achievementDao.deleteAllAchievements()
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Deleted all achievements, will recreate on next check")
        } catch (e: Exception) {
            SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Error cleaning duplicates: ${e.message}")
        }
    }
    
    /**
     * Initialize all achievements in the database
     */
    private suspend fun initializeAchievements() {
        // Sleep achievements
        val sleepAchievements = listOf(
            Achievement(name = "Starting Sleeper", points = 1, category = "sleep", description = "Slept consistently for 1 day.", emoticon = "😴", threshold = 1f),
            Achievement(name = "Steady Sleeper", points = 5, category = "sleep", description = "Slept consistently for 5 days.", emoticon = "😊", threshold = 5f),
            Achievement(name = "Solid Sleeper", points = 10, category = "sleep", description = "Slept consistently for 10 days.", emoticon = "😃", threshold = 10f),
            Achievement(name = "Sleeping Beauty", points = 25, category = "sleep", description = "Slept consistently for 25 days.", emoticon = "😄", threshold = 25f),
            Achievement(name = "Dream Chaser", points = 50, category = "sleep", description = "Slept consistently for 50 days.", emoticon = "😁", threshold = 50f),
            Achievement(name = "Rhythm Master", points = 100, category = "sleep", description = "Slept consistently for 100 days.", emoticon = "😎", threshold = 100f),
            Achievement(name = "Sleep Sage", points = 200, category = "sleep", description = "Slept consistently for 200 days.", emoticon = "🌟", threshold = 200f),
            Achievement(name = "Slumber Legend", points = 365, category = "sleep", description = "Slept consistently for 1 year.", emoticon = "👑", threshold = 365f),
            Achievement(name = "Sleep Immortal", points = 500, category = "sleep", description = "Slept consistently for 500 days.", emoticon = "⚜️", threshold = 500f),
            Achievement(name = "Sleep Grand Master", points = 730, category = "sleep", description = "Slept consistently for 2 years.", emoticon = "🌠", threshold = 730f)
        )
        
        // Fasting achievements
        val fastingAchievements = listOf(
            Achievement(name = "Fast Starter", points = 1, category = "fasting", description = "Fasted consistently for 1 day.", emoticon = "😊", threshold = 1f),
            Achievement(name = "Fast Explorer", points = 5, category = "fasting", description = "Fasted consistently for 5 days.", emoticon = "😃", threshold = 5f),
            Achievement(name = "Fast Tracker", points = 10, category = "fasting", description = "Fasted consistently for 10 days.", emoticon = "😄", threshold = 10f),
            Achievement(name = "Hunger Tamer", points = 25, category = "fasting", description = "Fasted consistently for 25 days.", emoticon = "😁", threshold = 25f),
            Achievement(name = "Fasting Warrior", points = 50, category = "fasting", description = "Fasted consistently for 50 days.", emoticon = "😎", threshold = 50f),
            Achievement(name = "Metabolic Master", points = 100, category = "fasting", description = "Fasted consistently for 100 days.", emoticon = "🤩", threshold = 100f),
            Achievement(name = "Fast Legend", points = 200, category = "fasting", description = "Fasted consistently for 200 days.", emoticon = "🌟", threshold = 200f),
            Achievement(name = "Fasting Champion", points = 365, category = "fasting", description = "Fasted consistently for 1 year.", emoticon = "👑", threshold = 365f),
            Achievement(name = "Fasting Immortal", points = 500, category = "fasting", description = "Fasted consistently for 500 days.", emoticon = "⚜️", threshold = 500f),
            Achievement(name = "Fasting Grand Master", points = 730, category = "fasting", description = "Fasted consistently for 2 years.", emoticon = "🌠", threshold = 730f)
        )
        
        // Create initial weight achievements
        createDefaultWeightAchievements()
        
        // Add all achievements to database
        achievementDao.insertAll(sleepAchievements + fastingAchievements)
        SleepFastTrackerApplication.addDebugLog("AchievementRepository", "Achievements initialized successfully")
    }
} 