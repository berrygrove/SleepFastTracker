package nl.berrygrove.sft.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a weight measurement record in the database.
 */
@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weight: Float, // Weight in kg
    val timestamp: LocalDateTime
) 