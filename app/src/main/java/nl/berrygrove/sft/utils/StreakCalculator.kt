package nl.berrygrove.sft.utils

import nl.berrygrove.sft.data.model.FastingRecord
import nl.berrygrove.sft.data.model.SleepRecord
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Utility class to calculate streaks for various features in the app.
 */
object StreakCalculator {
    
    /**
     * Calculates the bed time streak based on the specified algorithm:
     * - If there are 0 bed time entries, the streak is 0
     * - If the latest entry is false, the streak is 0
     * - If there are more than 0 entries and the last entry is true, add 1 to the streak
     * - Find the oldest consecutive true entry and count days from that date to today
     * 
     * @param sleepRecords List of sleep records to calculate streak from
     * @return The current bed time streak
     */
    fun calculateBedtimeStreak(sleepRecords: List<SleepRecord>): Int {
        // If there are no entries, streak is 0
        if (sleepRecords.isEmpty()) {
            return 0
        }
        
        // Sort from newest to oldest
        val sortedRecords = sleepRecords.sortedByDescending { it.timestamp }
        
        // If the latest entry is false, streak is 0
        if (!sortedRecords[0].onTime) {
            return 0
        }
        
        // The streak includes at least the latest true entry
        var streak = 1
        
        // Find the oldest consecutive true entry
        var oldestTrueEntryIndex = 0
        
        // Find the oldest entry that is true before encountering a false entry
        for (i in 1 until sortedRecords.size) {
            if (!sortedRecords[i].onTime) {
                break
            }
            oldestTrueEntryIndex = i
        }
        
        // If all entries are true, use the oldest entry
        val oldestConsecutiveTrueEntry = sortedRecords[oldestTrueEntryIndex]
        
        // Calculate days between oldest consecutive true entry and today
        val today = LocalDate.now()
        val oldestDate = oldestConsecutiveTrueEntry.timestamp.toLocalDate()
        
        // Add days between oldest consecutive true entry and today to the streak
        val additionalDays = ChronoUnit.DAYS.between(oldestDate, today)
        
        return streak + additionalDays.toInt()
    }
    
    /**
     * Calculates the fasting streak based on the specified algorithm:
     * - Count consecutive successful fasts going back from the current day
     * - A successful fast is: 24 hours - eating window - 30 minutes
     * - If the most recent state is fasting start, don't add to streak but don't break it
     * - If the most recent completed fast was unsuccessful, the streak is 0
     * - If there are no successful fasts, the streak is 0
     *
     * @param fastingRecords List of fasting records to calculate streak from
     * @param eatingWindowHours The configured eating window duration in hours
     * @return The current fasting streak
     */
    fun calculateFastingStreak(fastingRecords: List<FastingRecord>, eatingWindowHours: Int): Int {
        // If there are no entries, streak is 0
        if (fastingRecords.isEmpty()) {
            return 0
        }
        
        // Sort records from newest to oldest
        val sortedRecords = fastingRecords.sortedByDescending { it.timestamp }
        
        // Calculate the minimum duration for a successful fast (in hours)
        // 24 hours - eating window - 30 minutes (converted to hours)
        val minSuccessfulFastHours = 24 - eatingWindowHours - 0.5f
        val minSuccessfulFastMinutes = (minSuccessfulFastHours * 60).toLong()
        
        // Initialize streak counter
        var streak = 0
        
        // Check if currently fasting (latest record has state=true meaning fasting started)
        val isCurrentlyFasting = sortedRecords[0].state
        
        // Determine start index for checking fasts
        // If the last action was starting a fast (state=true), we need to look for a complete
        // fast cycle that ended before this, so we start at index 1
        // If the last action was ending a fast (state=false), we might have a complete cycle
        // starting from this record, so we start at index 0
        var currentIndex = if (isCurrentlyFasting) 1 else 0
        
        // Iterate through records to find completed fast cycles
        while (currentIndex + 1 < sortedRecords.size) {
            // We need at least a pair of records to analyze one complete fasting period
            // A fast starts with state=true and ends with state=false
            
            // In reverse chronological order we should see:
            // ... -> Fast Start (true) -> Fast End (false) -> Fast Start (true) -> Fast End (false) -> ...
            
            // If we're starting at an end-fast record (state=false), we need to look for the
            // matching start-fast record (state=true)
            // If we're starting at a start-fast record (state=true), we need to look for the
            // previous end-fast record (state=false)
            
            // Get the current and next record to analyze
            val currentRecord = sortedRecords[currentIndex]
            val nextRecord = sortedRecords[currentIndex + 1]
            
            // Skip incomplete or invalid fast cycles
            if (currentRecord.state == nextRecord.state) {
                // Two consecutive records with the same state
                // This is an error condition, as we can't have two starts or two ends in a row
                currentIndex++
                continue
            }
            
            // Get the fast start and fast end records
            val fastEndRecord: FastingRecord
            val fastStartRecord: FastingRecord
            
            if (!currentRecord.state && nextRecord.state) {
                // Current record is fast end (false), next record is fast start (true)
                fastEndRecord = currentRecord
                fastStartRecord = nextRecord
            } else if (currentRecord.state && !nextRecord.state) {
                // Current record is fast start (true), next record is fast end (false)
                // This is out of order for a proper cycle. Skip to the next pair.
                currentIndex += 2
                continue
            } else {
                // Shouldn't reach here due to earlier check, but if we do, skip to next record
                currentIndex++
                continue
            }
            
            // Calculate duration of the fasting period
            val fastDuration = Duration.between(fastStartRecord.timestamp, fastEndRecord.timestamp)
            
            // Check if this fast was successful
            if (fastDuration.toMinutes() < minSuccessfulFastMinutes) {
                // This fast was too short, break the streak
                break
            }
            
            // This was a successful fast, increment streak and move to next pair
            streak++
            currentIndex += 2
        }
        
        return streak
    }

    /**
     * Calculates the fasting streak using delta compensation as described in US2.
     * This version allows for small deficits in fasting time to be compensated by 
     * surplus minutes from other days.
     *
     * @param fastingRecords List of fasting records to calculate streak from
     * @param eatingWindowHours The configured eating window duration in hours (used as fallback)
     * @return The current fasting streak with delta compensation
     */
    fun calculateFastingStreakWithDelta(fastingRecords: List<FastingRecord>, eatingWindowHours: Int): Int {
        // If there are no entries, streak is 0
        if (fastingRecords.isEmpty()) {
            return 0
        }
        
        // Sort records by timestamp for processing multi-day fasts
        val sortedByTimestamp = fastingRecords.sortedBy { it.timestamp }
        
        // Maps to store dates and their associated deltas
        val dateDeltas = mutableMapOf<LocalDate, Long>()
        
        // Default target fasting minutes for missing days
        val fastingWindowMinutes = ((24 - eatingWindowHours) * 60).toLong()
        
        // First, process all real delta records
        val nonFastingRecords = fastingRecords.filter { !it.state }
        for (record in nonFastingRecords) {
            val date = record.timestamp.toLocalDate()
            val delta = record.delta_minutes?.toLong() ?: 0L
            dateDeltas[date] = (dateDeltas[date] ?: 0L) + delta
        }
        
        // Next, identify and process multi-day fasts
        processMutliDayFasts(sortedByTimestamp, fastingWindowMinutes, dateDeltas)
        
        // Get all dates with records, sorted by date (oldest to newest)
        val dates = dateDeltas.keys.sorted()
        
        if (dates.isEmpty()) {
            return 0
        }
        
        println("Processing dates in order: $dates")
        println("Date deltas: $dateDeltas")
        
        // Initialize variables
        var streak = 0
        var cumulativeDelta: Long = 0
        
        // Process dates chronologically
        for (date in dates) {
            val dayDelta = dateDeltas[date] ?: 0L
            println("Date $date: delta = $dayDelta")
            
            // Handle gaps between dates
            if (streak > 0 && date.isAfter(dates.first())) {
                val previousDate = dates.filter { it.isBefore(date) }.maxOrNull()
                if (previousDate != null) {
                    val daysBetween = date.toEpochDay() - previousDate.toEpochDay() - 1
                    
                    if (daysBetween > 0) {
                        // There are missing days between records
                        println("Found $daysBetween missing days between $previousDate and $date")
                        
                        // Apply penalty for each missing day
                        val missingDaysPenalty = daysBetween * -fastingWindowMinutes
                        cumulativeDelta += missingDaysPenalty
                        
                        println("Applied penalty of $missingDaysPenalty, new cumulative delta: $cumulativeDelta")
                        
                        // Check if streak breaks due to missing days
                        if (cumulativeDelta < 0) {
                            println("Streak breaks due to missing days")
                            streak = 0
                            cumulativeDelta = 0
                        }
                    }
                }
            }
            
            // Update cumulative delta with this day's value
            cumulativeDelta += dayDelta
            println("Cumulative delta after adding day's delta: $cumulativeDelta")
            
            // If still non-negative, this day is part of the streak
            if (cumulativeDelta >= 0) {
                streak++
                println("Streak now: $streak (day added)")
            } else {
                // Once cumulative delta is negative, we reset the streak and delta
                println("Streak breaks, resetting")
                streak = 0
                cumulativeDelta = 0
            }
        }
        
        return streak
    }

    /**
     * Processes multi-day fasts and adds virtual deltas of 0 for intermediate days.
     * 
     * @param sortedRecords List of all fasting records sorted by timestamp
     * @param fastingWindowMinutes The configured fasting window in minutes
     * @param dateDeltas Mutable map to store date-to-delta mappings
     */
    private fun processMutliDayFasts(
        sortedRecords: List<FastingRecord>,
        fastingWindowMinutes: Long,
        dateDeltas: MutableMap<LocalDate, Long>
    ) {
        // Find start-end pairs for fasting periods
        var i = 0
        while (i < sortedRecords.size - 1) {
            val currentRecord = sortedRecords[i]
            
            // Look for a start record (state=true)
            if (currentRecord.state) {
                // Find the corresponding end record
                var j = i + 1
                while (j < sortedRecords.size && sortedRecords[j].state) {
                    j++
                }
                
                // If we found an end record
                if (j < sortedRecords.size) {
                    val endRecord = sortedRecords[j]
                    
                    // Calculate total minutes and full windows
                    val startTs = currentRecord.timestamp
                    val endTs = endRecord.timestamp
                    val totalMinutes = ChronoUnit.MINUTES.between(startTs, endTs)
                    val fullWindows = (totalMinutes / fastingWindowMinutes).toInt()
                    
                    // Check if this is a multi-day fast (at least 2 full windows)
                    if (fullWindows >= 2) {
                        println("Found multi-day fast: $startTs to $endTs, full windows: $fullWindows")
                        
                        // Calculate virtual days
                        val virtualDays = fullWindows - 1
                        val endDate = endTs.toLocalDate()
                        
                        // Create virtual deltas for intermediate days
                        for (day in 1..virtualDays) {
                            val virtualDate = endDate.minusDays(day.toLong())
                            // Only add virtual delta if there's no real record for this date
                            if (!dateDeltas.containsKey(virtualDate)) {
                                dateDeltas[virtualDate] = 0L
                                println("Added virtual delta 0 for date: $virtualDate")
                            }
                        }
                    }
                    
                    // Move to next record
                    i = j + 1
                } else {
                    // No end record found, break
                    break
                }
            } else {
                // Not a start record, move to next record
                i++
            }
        }
    }
} 