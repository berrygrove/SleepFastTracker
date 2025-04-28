package nl.berrygrove.sft.debug

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Utility class to monitor battery usage by tracking CPU wake time 
 * and operation frequency across the app.
 */
class BatteryUsageMonitor private constructor() {
    
    private val operationCounts = ConcurrentHashMap<String, AtomicLong>()
    private val operationTimes = ConcurrentHashMap<String, AtomicLong>()
    private val wakeLocks = ConcurrentHashMap<String, Long>()
    
    fun recordOperation(tag: String) {
        operationCounts.computeIfAbsent(tag) { AtomicLong(0) }.incrementAndGet()
    }
    
    fun startTiming(tag: String) {
        wakeLocks[tag] = SystemClock.elapsedRealtime()
    }
    
    fun stopTiming(tag: String) {
        val startTime = wakeLocks.remove(tag) ?: return
        val duration = SystemClock.elapsedRealtime() - startTime
        operationTimes.computeIfAbsent(tag) { AtomicLong(0) }.addAndGet(duration)
    }
    
    fun dumpStats(context: Context) {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        sb.appendLine("===== SleepFastTracker Battery Usage Report =====")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine("\nOperation Counts:")
        
        operationCounts.entries.sortedByDescending { it.value.get() }.forEach { (tag, count) ->
            sb.appendLine("$tag: ${count.get()} times")
        }
        
        sb.appendLine("\nOperation Times (ms):")
        operationTimes.entries.sortedByDescending { it.value.get() }.forEach { (tag, time) ->
            sb.appendLine("$tag: ${time.get()}ms")
        }
        
        // Log the stats
        Log.i(TAG, sb.toString())
        
        // Save to file
        try {
            val file = File(context.getExternalFilesDir(null), "battery_usage_report.txt")
            file.writeText(sb.toString())
            Log.i(TAG, "Battery usage report saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save battery usage report", e)
        }
    }
    
    fun reset() {
        operationCounts.clear()
        operationTimes.clear()
        wakeLocks.clear()
    }
    
    companion object {
        private const val TAG = "BatteryUsageMonitor"
        
        @Volatile
        private var instance: BatteryUsageMonitor? = null
        
        fun getInstance(): BatteryUsageMonitor {
            return instance ?: synchronized(this) {
                instance ?: BatteryUsageMonitor().also { instance = it }
            }
        }
    }
} 