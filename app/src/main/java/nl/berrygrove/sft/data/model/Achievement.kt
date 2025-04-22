package nl.berrygrove.sft.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an achievement in the database.
 */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val points: Int,
    val achieved: Boolean = false,
    val category: String, // "sleep", "fasting", or "weight"
    val description: String,
    val emoticon: String,
    val threshold: Float  // The value needed to achieve this (days for streaks, kg for weight loss)
) 