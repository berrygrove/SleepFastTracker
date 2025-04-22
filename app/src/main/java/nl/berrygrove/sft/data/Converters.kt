package nl.berrygrove.sft.data

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Type converters for Room database.
 * Handles conversion of complex types that Room cannot store directly.
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(value), ZoneId.systemDefault()) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? {
        return date?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }
} 