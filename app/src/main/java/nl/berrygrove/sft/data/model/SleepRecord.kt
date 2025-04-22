package nl.berrygrove.sft.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a sleep record in the database.
 */
@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val onTime: Boolean, // true = went to bed on time, false = did not
    val timestamp: LocalDateTime
) 