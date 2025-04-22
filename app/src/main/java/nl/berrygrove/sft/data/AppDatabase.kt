package nl.berrygrove.sft.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.berrygrove.sft.data.dao.AchievementDao
import nl.berrygrove.sft.data.dao.FastingRecordDao
import nl.berrygrove.sft.data.dao.SleepRecordDao
import nl.berrygrove.sft.data.dao.UserSettingsDao
import nl.berrygrove.sft.data.dao.WeightRecordDao
import nl.berrygrove.sft.data.model.Achievement
import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.SleepRecord
import nl.berrygrove.sft.data.model.UserSettings
import nl.berrygrove.sft.data.model.WeightRecord
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * The Room database for the SleepFastTracker application.
 */
@Database(
    entities = [UserSettings::class, FastingRecord::class, SleepRecord::class, WeightRecord::class, Achievement::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun fastingRecordDao(): FastingRecordDao
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun weightRecordDao(): WeightRecordDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to version 2 to add bedTime column to user_settings table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add bedTime column with default value
                db.execSQL("ALTER TABLE user_settings ADD COLUMN bedTime TEXT NOT NULL DEFAULT '22:30'")
            }
        }
        
        // Migration from version 2 to version 3 to add wakeUpTime column to user_settings table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add wakeUpTime column with default value
                db.execSQL("ALTER TABLE user_settings ADD COLUMN wakeUpTime TEXT NOT NULL DEFAULT '06:30'")
            }
        }

        // Migration from version 3 to version 4 to add notification settings columns
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add notification settings columns with default values
                db.execSQL("ALTER TABLE user_settings ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN bedtimeCheckNotificationEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN bedtimeReminderNotificationEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN weightUpdateNotificationEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN fastingEndNotificationEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN eatingEndNotificationEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }
        
        // Migration from version 4 to version 5 to add achievements table
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create achievements table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        points INTEGER NOT NULL,
                        achieved INTEGER NOT NULL DEFAULT 0,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        emoticon TEXT NOT NULL,
                        threshold INTEGER NOT NULL
                    )
                    """
                )
            }
        }
        
        // Migration from version 5 to version 6 to add delta_minutes column to fasting_records table
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add delta_minutes column to fasting_records table with NULL as default
                db.execSQL("ALTER TABLE fasting_records ADD COLUMN delta_minutes INTEGER")
            }
        }
        
        // Migration from version 6 to version 7 to add eating_window_minutes column to fasting_records table
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add eating_window_minutes column to fasting_records table with NULL as default
                db.execSQL("ALTER TABLE fasting_records ADD COLUMN eating_window_minutes INTEGER")
            }
        }
        
        // Migration from version 7 to version 8 - just version bump to fix schema hash mismatch
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create a temporary table with the correct schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievements_temp (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        points INTEGER NOT NULL,
                        achieved INTEGER NOT NULL DEFAULT 0,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        emoticon TEXT NOT NULL,
                        threshold REAL NOT NULL
                    )
                    """
                )
                
                // Copy data from old table to new table, converting threshold to REAL
                db.execSQL(
                    """
                    INSERT INTO achievements_temp (id, name, points, achieved, category, description, emoticon, threshold)
                    SELECT id, name, points, achieved, category, description, emoticon, CAST(threshold AS REAL)
                    FROM achievements
                    """
                )
                
                // Drop the old table
                db.execSQL("DROP TABLE achievements")
                
                // Rename the new table to the correct name
                db.execSQL("ALTER TABLE achievements_temp RENAME TO achievements")
            }
        }

        // Migration from version 8 to version 9 - ensure threshold is REAL type
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Drop the old table completely
                    db.execSQL("DROP TABLE IF EXISTS achievements")
                    
                    // Create a new achievements table with the correct schema
                    db.execSQL(
                        """
                        CREATE TABLE achievements (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            points INTEGER NOT NULL,
                            achieved INTEGER NOT NULL DEFAULT 0,
                            category TEXT NOT NULL,
                            description TEXT NOT NULL,
                            emoticon TEXT NOT NULL,
                            threshold REAL NOT NULL
                        )
                        """
                    )
                } catch (e: Exception) {
                    // Log error but don't propagate - we'll handle with fallbackToDestructiveMigration
                    e.printStackTrace()
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath("sleep_fast_tracker_database")
                val dbDir = dbFile.parentFile
                
                // Ensure directories exist
                if (dbDir != null && !dbDir.exists()) {
                    dbDir.mkdirs()
                }
                
                // If database exists but is corrupt, delete it
                if (dbFile.exists() && !isDatabaseValid(dbFile)) {
                    dbFile.delete()
                }
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sleep_fast_tracker_database"
                )
                // Force destructive migration (losing all data) if a migration fails
                .fallbackToDestructiveMigration()
                // This setting will change the version number if schema hash doesn't match, but will lose data
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        // Check if the database is valid
        private fun isDatabaseValid(dbFile: File): Boolean {
            var connection: SQLiteDatabase? = null
            return try {
                connection = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                true
            } catch (e: Exception) {
                false
            } finally {
                connection?.close()
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        // Initialize default user settings
                        val userSettingsDao = database.userSettingsDao()
                        userSettingsDao.insert(UserSettings())
                        
                        // Initialize achievements
                        initializeAchievements(database.achievementDao())
                    }
                }
            }
            
            private suspend fun initializeAchievements(achievementDao: AchievementDao) {
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
                    Achievement(name = "Sleep Immortal", points = 500, category = "sleep", description = "Slept consistently for 500 days.", emoticon = "⚜️", threshold = 500f)
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
                    Achievement(name = "Fasting Immortal", points = 500, category = "fasting", description = "Fasted consistently for 500 days.", emoticon = "⚜️", threshold = 500f)
                )
                
                // Weight loss achievements
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
                
                // Add new ultimate achievements for long-term dedication
                val ultimateAchievements = listOf(
                    Achievement(name = "Sleep Grand Master", points = 730, category = "sleep", description = "Slept consistently for 2 years.", emoticon = "🌠", threshold = 730f),
                    Achievement(name = "Fasting Grand Master", points = 730, category = "fasting", description = "Fasted consistently for 2 years.", emoticon = "🌠", threshold = 730f)
                )
                
                // Add all achievements to database
                achievementDao.insertAll(sleepAchievements + fastingAchievements + weightAchievements + ultimateAchievements)
            }
        }
    }
} 