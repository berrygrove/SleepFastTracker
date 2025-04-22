package nl.berrygrove.sft.utils

import nl.berrygrove.sft.data.model.FastingRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class StreakCalculatorTest {

    private val eatingWindowHours = 6 // 6-hour eating window, 18-hour fasting window
    private val eatingWindowMinutes = eatingWindowHours * 60 // 360 minutes

    @Test
    fun `empty records should return 0 streak`() {
        val records = emptyList<FastingRecord>()
        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        assertEquals(0, streak)
    }

    @Test
    fun `single successful day should return 1 streak`() {
        // Create a fast with +30 minutes above target (positive delta)
        val deltaMinutes = 30 // 30 minutes over target

        val today = LocalDate.now()
        val fastStart = LocalDateTime.of(today.minusDays(1), LocalTime.of(20, 0)) // Started yesterday at 8 PM
        val fastEnd = LocalDateTime.of(today, LocalTime.of(14, 30)) // Ended today at 2:30 PM

        val records = listOf(
            FastingRecord(state = true, timestamp = fastStart), // Fast start
            FastingRecord(state = false, timestamp = fastEnd, delta_minutes = deltaMinutes, eating_window_minutes = eatingWindowMinutes) // Fast end
        )

        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        assertEquals(1, streak)
    }

    @Test
    fun `single unsuccessful day should return 0 streak`() {
        // Create a fast with -60 minutes below target (negative delta)
        val deltaMinutes = -60 // 60 minutes under target

        val today = LocalDate.now()
        val fastStart = LocalDateTime.of(today.minusDays(1), LocalTime.of(20, 0)) // Started yesterday at 8 PM
        val fastEnd = LocalDateTime.of(today, LocalTime.of(13, 0)) // Ended today at 1 PM

        val records = listOf(
            FastingRecord(state = true, timestamp = fastStart), // Fast start
            FastingRecord(state = false, timestamp = fastEnd, delta_minutes = deltaMinutes, eating_window_minutes = eatingWindowMinutes) // Fast end
        )

        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        assertEquals(0, streak)
    }

    @Test
    fun `compensating deficit with surplus from previous days`() {
        // Use fixed dates to avoid test confusion
        val today = LocalDate.of(2023, 1, 3)
        
        // Create a simpler test case that should clearly succeed
        // Day 1: Jan 1 - surplus of +100 minutes
        val day1FastStart = LocalDateTime.of(2023, 1, 1, 20, 0)
        val day1FastEnd = LocalDateTime.of(2023, 1, 2, 15, 40)
        
        // Day 2: Jan 2 - deficit of -50 minutes (should still have +50 minutes total and maintain streak)
        val day2FastStart = LocalDateTime.of(2023, 1, 2, 20, 0)
        val day2FastEnd = LocalDateTime.of(2023, 1, 3, 13, 10)

        // For debugging
        println("Today: $today")
        println("Day 1 date: ${day1FastEnd.toLocalDate()}")
        println("Day 2 date: ${day2FastEnd.toLocalDate()}")
        
        val records = listOf(
            // Day 2 (most recent)
            FastingRecord(state = true, timestamp = day2FastStart),
            FastingRecord(state = false, timestamp = day2FastEnd, delta_minutes = -50, eating_window_minutes = eatingWindowMinutes),
            
            // Day 1 (older)
            FastingRecord(state = true, timestamp = day1FastStart),
            FastingRecord(state = false, timestamp = day1FastEnd, delta_minutes = 100, eating_window_minutes = eatingWindowMinutes)
        )
        
        println("Records:")
        records.forEach { 
            println("- ${it.timestamp.toLocalDate()}: state=${it.state}, delta=${it.delta_minutes}")
        }

        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        println("Expected streak: 2, Actual streak: $streak")
        assertEquals(2, streak) // Both days should succeed with a net positive delta
    }

    @Test
    fun `multiple fasts in the same day should sum their deltas`() {
        val today = LocalDate.now()
        
        // First fast on the day: -30 minutes
        val fastStart1 = LocalDateTime.of(today, LocalTime.of(0, 0))
        val fastEnd1 = LocalDateTime.of(today, LocalTime.of(10, 30))
        
        // Second fast on the same day: +40 minutes
        val fastStart2 = LocalDateTime.of(today, LocalTime.of(12, 0))
        val fastEnd2 = LocalDateTime.of(today, LocalTime.of(21, 10))

        val records = listOf(
            // Second fast
            FastingRecord(state = true, timestamp = fastStart2),
            FastingRecord(state = false, timestamp = fastEnd2, delta_minutes = 40, eating_window_minutes = eatingWindowMinutes),
            
            // First fast
            FastingRecord(state = true, timestamp = fastStart1),
            FastingRecord(state = false, timestamp = fastEnd1, delta_minutes = -30, eating_window_minutes = eatingWindowMinutes)
        )

        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        assertEquals(1, streak) // Net delta is 10 minutes, so streak is 1
    }

    @Test
    fun `day with no fasts should count as missed day with negative delta`() {
        val today = LocalDate.now()
        
        // Day 1: surplus of +200 minutes
        val day1FastStart = LocalDateTime.of(today.minusDays(3), LocalTime.of(20, 0))
        val day1FastEnd = LocalDateTime.of(today.minusDays(2), LocalTime.of(17, 20))
        
        // Day 2: No fasts (should count as -targetFastingMinutes)
        
        // Day 3: surplus of +10 minutes
        val day3FastStart = LocalDateTime.of(today.minusDays(1), LocalTime.of(20, 0))
        val day3FastEnd = LocalDateTime.of(today, LocalTime.of(14, 10))

        val records = listOf(
            // Day 3
            FastingRecord(state = true, timestamp = day3FastStart),
            FastingRecord(state = false, timestamp = day3FastEnd, delta_minutes = 10, eating_window_minutes = eatingWindowMinutes),
            
            // Day 1
            FastingRecord(state = true, timestamp = day1FastStart),
            FastingRecord(state = false, timestamp = day1FastEnd, delta_minutes = 200, eating_window_minutes = eatingWindowMinutes)
        )

        // Negative delta for missed day should be -targetFastingMinutes
        // targetFastingMinutes = (24 - 6) * 60 = 18 * 60 = 1080
        // Calculation: Day1(+200) + Day2(-1080) + Day3(+10) = -870
        // Streak should break at day 2
        
        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        assertEquals(1, streak) // Only day 3 succeeds, day 2 had no fasts
    }

    @Test
    fun `fasts spanning midnight should count for the end date`() {
        val today = LocalDate.now()
        
        // Fast spanning from yesterday to today
        val fastStart = LocalDateTime.of(today.minusDays(1), LocalTime.of(20, 0))
        val fastEnd = LocalDateTime.of(today, LocalTime.of(14, 0))

        val records = listOf(
            FastingRecord(state = true, timestamp = fastStart),
            FastingRecord(state = false, timestamp = fastEnd, delta_minutes = 0, eating_window_minutes = eatingWindowMinutes)
        )

        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        assertEquals(1, streak) // Counts as today's fast
    }

    @Test
    fun `multi-day fast should create virtual deltas for intermediate days`() {
        // Create a 3-day fast (over 2 full fasting windows)
        // Day 1: Start fasting Jan 1 at 8 PM
        // Day 3: End fasting Jan 4 at 2 PM (total ~66 hours)
        val day1 = LocalDate.of(2023, 1, 1)
        val day3 = LocalDate.of(2023, 1, 4)
        
        val fastStart = LocalDateTime.of(day1, LocalTime.of(20, 0)) // Start Jan 1, 8 PM
        val fastEnd = LocalDateTime.of(day3, LocalTime.of(14, 0))   // End Jan 4, 2 PM
        
        // Calculate delta for this fast
        val totalFastMinutes = ChronoUnit.MINUTES.between(fastStart, fastEnd)
        val targetFastMinutes = ((24 - eatingWindowHours) * 60).toLong()
        val delta = (totalFastMinutes - targetFastMinutes).toInt() // Should be positive
        
        val records = listOf(
            FastingRecord(state = true, timestamp = fastStart),
            FastingRecord(state = false, timestamp = fastEnd, delta_minutes = delta, eating_window_minutes = eatingWindowMinutes)
        )
        
        // Run streak calculation - should inject virtual deltas for Jan 2 and Jan 3
        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        
        // Expected streak is 3 (Jan 2, Jan 3, Jan 4)
        // Jan 1 is not counted since it doesn't have a complete fasting window
        assertEquals(3, streak)
    }

    @Test
    fun `multi-day fast with missing gap days should maintain streak`() {
        // First a 3-day fast
        // Day 1: Start fasting Jan 1 at 8 PM
        // Day 3: End fasting Jan 4 at 2 PM (total ~66 hours)
        val day1Start = LocalDateTime.of(2023, 1, 1, 20, 0)
        val day3End = LocalDateTime.of(2023, 1, 4, 14, 0)
        
        // Then a gap of 1 day (Jan 5 - no fast)
        
        // Then another successful fast on Jan 6
        val day5Start = LocalDateTime.of(2023, 1, 6, 8, 0)
        val day5End = LocalDateTime.of(2023, 1, 6, 20, 0)
        
        // Calculate deltas
        val firstFastMinutes = ChronoUnit.MINUTES.between(day1Start, day3End)
        val firstFastDelta = (firstFastMinutes - ((24 - eatingWindowHours) * 60)).toInt()
        
        // Second fast exceeds target by 2 hours (120 minutes)
        val secondFastDelta = 120
        
        val records = listOf(
            // Second fast
            FastingRecord(state = true, timestamp = day5Start),
            FastingRecord(state = false, timestamp = day5End, delta_minutes = secondFastDelta, eating_window_minutes = eatingWindowMinutes),
            
            // First multi-day fast
            FastingRecord(state = true, timestamp = day1Start),
            FastingRecord(state = false, timestamp = day3End, delta_minutes = firstFastDelta, eating_window_minutes = eatingWindowMinutes)
        )
        
        // Run streak calculation
        val streak = StreakCalculator.calculateFastingStreakWithDelta(records, eatingWindowHours)
        
        // Expected streak is 4 (Jan 2, Jan 3, Jan 4, Jan 6)
        // Jan 5 is a gap day, but the surplus from the multi-day fast should cover it
        assertEquals(4, streak)
    }
} 