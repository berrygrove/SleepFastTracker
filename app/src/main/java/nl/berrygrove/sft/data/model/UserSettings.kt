package nl.berrygrove.sft.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing user settings in the database.
 * Since the app only has one user, we use the fixed ID = 1
 */
@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey
    val id: Int = 1, // Fixed ID for single user
    val name: String = "",
    val age: Int = 0,
    val height: Float = 0f, // Height in cm
    val eatingStartTime: String = "11:00", // Default eating start time
    val eatingWindowHours: Int = 6, // Default eating window in hours
    val bedTime: String = "22:30", // Default bedtime in HH:MM format
    val wakeUpTime: String = "06:30", // Default wake-up time in HH:MM format
    val setupCompleted: Boolean = false, // Flag to track if setup wizard is completed
    val notificationsEnabled: Boolean = true, // Master switch for all notifications
    val bedtimeCheckNotificationEnabled: Boolean = true,
    val bedtimeReminderNotificationEnabled: Boolean = true,
    val weightUpdateNotificationEnabled: Boolean = true,
    val fastingEndNotificationEnabled: Boolean = true,
    val eatingEndNotificationEnabled: Boolean = true
) 