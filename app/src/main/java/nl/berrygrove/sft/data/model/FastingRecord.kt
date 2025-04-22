package nl.berrygrove.sft.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a fasting state record in the database.
 */
@Entity(tableName = "fasting_records")
data class FastingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val state: Boolean, // true = fasting, false = eating
    val timestamp: LocalDateTime,
    val delta_minutes: Int? = null, // How many minutes over/under target fasting window
    val eating_window_minutes: Int? = null // Snapshot of user's eating window setting when fast ended
) 