package nl.berrygrove.sft.ui.debug

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.debug.BatteryUsageMonitor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryDebugActivity : AppCompatActivity() {

    private lateinit var memoryTextView: TextView
    private lateinit var batteryReportTextView: TextView
    private lateinit var generateReportButton: Button
    private lateinit var optimizeWidgetsButton: Button
    
    private val handler = Handler(Looper.getMainLooper())
    private val memoryUpdateRunnable = object : Runnable {
        override fun run() {
            updateMemoryInfo()
            handler.postDelayed(this, 2000) // Update every 2 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_debug)
        
        // Setup toolbar with back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Battery Diagnostics"
        
        // Initialize views
        memoryTextView = findViewById(R.id.memory_info)
        batteryReportTextView = findViewById(R.id.battery_report)
        generateReportButton = findViewById(R.id.generate_report_button)
        optimizeWidgetsButton = findViewById(R.id.optimize_widgets_button)
        
        // Generate report button
        generateReportButton.setOnClickListener {
            generateBatteryReport()
        }
        
        // Optimize widgets button
        optimizeWidgetsButton.setOnClickListener {
            optimizeWidgets()
        }
        
        // Initial memory update
        updateMemoryInfo()
    }
    
    override fun onResume() {
        super.onResume()
        // Start memory updates
        handler.post(memoryUpdateRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // Stop memory updates
        handler.removeCallbacks(memoryUpdateRunnable)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun updateMemoryInfo() {
        val memoryInfo = getMemoryInfo()
        memoryTextView.text = memoryInfo
    }
    
    private fun getMemoryInfo(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val availableMB = memInfo.availMem / 1048576L
        val totalMB = memInfo.totalMem / 1048576L
        val percentUsed = 100 - (availableMB * 100 / totalMB)
        
        val runtimeMaxMB = Runtime.getRuntime().maxMemory() / 1048576L
        val runtimeTotalMB = Runtime.getRuntime().totalMemory() / 1048576L
        val runtimeFreeMB = Runtime.getRuntime().freeMemory() / 1048576L
        val runtimeUsedMB = runtimeTotalMB - runtimeFreeMB
        
        return "System Memory:\n" +
               "Available: $availableMB MB\n" +
               "Total: $totalMB MB\n" +
               "Used: $percentUsed%\n\n" +
               "App Memory:\n" +
               "Max Available: $runtimeMaxMB MB\n" +
               "Currently Used: $runtimeUsedMB MB\n" +
               "Total Allocated: $runtimeTotalMB MB"
    }
    
    private fun generateBatteryReport() {
        lifecycleScope.launch {
            val app = application as SleepFastTrackerApplication
            val report = app.generateBatteryReport()
            
            withContext(Dispatchers.Main) {
                batteryReportTextView.text = "Battery report generated. Check the file at:\n" + 
                    "${getExternalFilesDir(null)}/battery_usage_report.txt"
            }
        }
    }
    
    private fun optimizeWidgets() {
        lifecycleScope.launch {
            // Cancel and reschedule widget updates with optimal settings
            withContext(Dispatchers.IO) {
                val monitor = BatteryUsageMonitor.getInstance()
                monitor.startTiming("widget_optimization")
                
                try {
                    // Import the necessary class first to avoid problems
                    val widgetClass = Class.forName("nl.berrygrove.sft.widget.FastingBedtimeWidget")
                    val scheduleMethod = widgetClass.getDeclaredMethod("schedulePeriodicUpdates", Context::class.java)
                    scheduleMethod.invoke(null, this@BatteryDebugActivity)
                    
                    batteryReportTextView.text = "Widgets optimized successfully!"
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        batteryReportTextView.text = "Error optimizing widgets: ${e.message}"
                    }
                } finally {
                    monitor.stopTiming("widget_optimization")
                }
            }
        }
    }
} 