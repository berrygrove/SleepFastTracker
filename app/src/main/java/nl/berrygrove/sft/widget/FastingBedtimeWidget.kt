package nl.berrygrove.sft.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.berrygrove.sft.R
import nl.berrygrove.sft.SleepFastTrackerApplication
import nl.berrygrove.sft.repository.FastingRepository
import nl.berrygrove.sft.repository.SleepRepository
import nl.berrygrove.sft.repository.UserSettingsRepository
import nl.berrygrove.sft.ui.home.HomeActivity
import nl.berrygrove.sft.utils.EmojiUtils
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Implementation of App Widget functionality.
 * App Widget that displays the fasting and bedtime information.
 */
class FastingBedtimeWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val widgetScope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        android.util.Log.d("FastingBedtimeWidget", "onUpdate called at ${LocalDateTime.now()} for ${appWidgetIds.size} widgets")
        
        // Update all instances of the widget
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        
        // Schedule periodic updates for the widget
        schedulePeriodicUpdates(context)
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        android.util.Log.d("FastingBedtimeWidget", "onEnabled called at ${LocalDateTime.now()}")
        
        // Start the periodic updates when the first widget is added
        schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        android.util.Log.d("FastingBedtimeWidget", "onDisabled called at ${LocalDateTime.now()}")
        
        // Cancel the job when the last widget is removed
        job.cancel()
        
        // Cancel the work manager updates
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK_NAME)
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("FastingBedtimeWidget", "onReceive: ${intent.action} at ${LocalDateTime.now()}")
        super.onReceive(context, intent)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        android.util.Log.d("FastingBedtimeWidget", "updateAppWidget for widget $appWidgetId at ${LocalDateTime.now()}")
        
        // Get repositories from application
        val application = context.applicationContext as SleepFastTrackerApplication
        val fastingRepository = application.fastingRepository
        val sleepRepository = application.sleepRepository
        val userSettingsRepository = application.userSettingsRepository

        // Create RemoteViews for widget layout
        val views = RemoteViews(context.packageName, R.layout.widget_fasting_bedtime)

        // Set up click intent for widget
        val intent = Intent(context, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        // Make the entire widget clickable
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        try {
            // Call the function to update widget data (uses runBlocking internally)
            updateWidgetData(context, views, fastingRepository, sleepRepository, userSettingsRepository)
            // Update the widget after data is loaded
            appWidgetManager.updateAppWidget(appWidgetId, views)
            android.util.Log.d("FastingBedtimeWidget", "Widget $appWidgetId updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("FastingBedtimeWidget", "Error updating widget $appWidgetId", e)
            e.printStackTrace()
        }
    }

    private fun updateWidgetData(
        context: Context,
        views: RemoteViews,
        fastingRepository: FastingRepository,
        sleepRepository: SleepRepository,
        userSettingsRepository: UserSettingsRepository
    ) {
        try {
            runBlocking {
                // Get user settings
                val settings = userSettingsRepository.getUserSettings() ?: return@runBlocking

                // Calculate overall score
                val fastingStreak = fastingRepository.calculateFastingStreak(settings.eatingWindowHours)
                val sleepStreak = sleepRepository.calculateBedtimeStreak()
                
                // Get weight loss value
                val weightRepository = (context.applicationContext as SleepFastTrackerApplication).weightRecordRepository
                val weightRecords = weightRepository.getAllWeightRecords()
                var weightLost = 0f
                if (weightRecords.size > 1) {
                    val sortedRecords = weightRecords.sortedBy { record -> record.timestamp }
                    val startWeight = sortedRecords.first().weight
                    var maxWeightLoss = 0f
                    var lowestWeight = startWeight
                    
                    for (record in sortedRecords.drop(1)) {
                        if (record.weight < lowestWeight) {
                            lowestWeight = record.weight
                            val currentLoss = startWeight - lowestWeight
                            if (currentLoss > maxWeightLoss) {
                                maxWeightLoss = currentLoss
                            }
                        }
                    }
                    weightLost = maxWeightLoss
                }
                
                // Get achievement points
                val achievementRepository = (context.applicationContext as SleepFastTrackerApplication).achievementRepository
                val achievementPoints = achievementRepository.totalPoints.first() ?: 0
                
                // Calculate using same formula as StreaksViewModel: 
                // ((sleep streak + fasting streak) * 5) + (weight lost * 10) + achievement points
                val streakComponent = (fastingStreak + sleepStreak) * 5
                val weightComponent = (weightLost * 10).toInt()
                val overallScore = streakComponent + weightComponent + achievementPoints
                
                // Update widget header with overall score
                views.setTextViewText(R.id.widget_header, context.getString(R.string.widget_header_score, overallScore))

                // Update fasting information
                val latestFastingRecord = fastingRepository.getLatestFastingRecord()
                val currentFastingState = latestFastingRecord?.state ?: false
                
                // Get fasting time info
                val now = LocalDateTime.now()
                val eatingWindowStart = LocalTime.parse(settings.eatingStartTime)
                val eatingWindowDuration = Duration.ofHours(settings.eatingWindowHours.toLong())
                
                // Calculate and format fasting countdown
                val fastingCountdown = if (currentFastingState) {
                    // UPDATED LOGIC: Count down to fasting window duration
                    // Get the timestamp of when fasting started
                    val fastingStartTime = latestFastingRecord?.timestamp ?: now
                    
                    // Calculate total fasting duration so far
                    val fastingDurationSoFar = Duration.between(fastingStartTime, now)
                    
                    // Calculate target fasting duration (24h - eating window)
                    val targetFastingDuration = Duration.ofHours(24).minus(eatingWindowDuration)
                    
                    // Calculate and set the fasting end time (red square area)
                    val fastingEndTime = fastingStartTime.plus(targetFastingDuration)
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    views.setTextViewText(R.id.widget_fasting_end_time, "Ends: ${fastingEndTime.format(timeFormatter)}")
                    
                    if (fastingDurationSoFar.compareTo(targetFastingDuration) < 0) {
                        // Still counting down to reach target fasting duration
                        val remainingDuration = targetFastingDuration.minus(fastingDurationSoFar)
                        
                        // Calculate progress percentage (how far along the fasting window)
                        val progressPercentage = ((fastingDurationSoFar.seconds * 100) / targetFastingDuration.seconds).toInt().coerceIn(0, 100)
                        
                        // Set the progress level (0-10000 range for Android level-list drawables)
                        android.util.Log.d("WidgetProgress", "Fasting progress: $progressPercentage%")
                        try {
                            updateProgressDrawable(views, R.id.widget_fasting_progress, progressPercentage, context)
                        } catch (e: Exception) {
                            android.util.Log.e("WidgetProgress", "Error setting fasting progress", e)
                        }
                        
                        // Format duration for display
                        formatDuration(remainingDuration)
                    } else {
                        // Exceeded target fasting duration - show extra time as count up
                        val extraDuration = fastingDurationSoFar.minus(targetFastingDuration)
                        
                        // Progress is 100% once we've reached or exceeded target
                        try {
                            updateProgressDrawable(views, R.id.widget_fasting_progress, 100, context)
                        } catch (e: Exception) {
                            android.util.Log.e("WidgetProgress", "Error setting fasting progress", e)
                        }
                        
                        // Display with a plus sign to indicate counting up beyond target
                        "+" + formatDuration(extraDuration)
                    }
                } else {
                    // Get the timestamp of when eating started
                    val eatingStartTime = latestFastingRecord?.timestamp ?: now
                    
                    // Calculate and set the eating end time (red square area)
                    val sessionEndTime = eatingStartTime.plus(eatingWindowDuration)
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    views.setTextViewText(R.id.widget_fasting_end_time, "Ends: ${sessionEndTime.format(timeFormatter)}")
                    
                    // Countdown to fasting start - use the time component of eatingEndTime
                    val eatingEndLocalTime = sessionEndTime.toLocalTime()
                    val fastingStartDateTime = now.with(eatingEndLocalTime)
                    val duration = if (now.toLocalTime().isBefore(eatingEndLocalTime)) {
                        Duration.between(now, fastingStartDateTime)
                    } else {
                        Duration.between(now, fastingStartDateTime.plusDays(1))
                    }
                    
                    // Calculate progress percentage for logging purposes only
                    val totalEatingDuration = eatingWindowDuration.toMinutes()
                    val remainingEatingMinutes = duration.toMinutes()
                    val elapsedEatingMinutes = totalEatingDuration - remainingEatingMinutes
                    val eatingProgress = if (totalEatingDuration > 0) {
                        ((elapsedEatingMinutes * 100) / totalEatingDuration).toInt().coerceIn(0, 100)
                    } else 0
                    
                    // Set the progress level (0-10000 range for Android level-list drawables)
                    android.util.Log.d("WidgetProgress", "Eating progress: $eatingProgress%")
                    try {
                        updateProgressDrawable(views, R.id.widget_fasting_progress, eatingProgress, context)
                    } catch (e: Exception) {
                        android.util.Log.e("WidgetProgress", "Error setting eating progress", e)
                    }
                    
                    formatDuration(duration)
                }
                views.setTextViewText(R.id.widget_fasting_countdown, fastingCountdown)
                
                // Update fasting streak on the bottom left with emojis based on streak length
                val fastingEmoji = EmojiUtils.getEmoticonsByStreakLength(fastingStreak)
                views.setTextViewText(R.id.widget_fasting_streak, "Fast: ${fastingStreak} day${if (fastingStreak != 1) "s" else ""} $fastingEmoji")

                // Update bedtime information
                val bedTime = LocalTime.parse(settings.bedTime)
                val wakeUpTime = LocalTime.parse(settings.wakeUpTime)
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                
                // Determine if we should show bedtime or wake-up countdown
                val isShowingWakeUpCountdown = if (bedTime.isBefore(wakeUpTime)) {
                    // Normal case: bedtime before wake-up within the same day
                    now.toLocalTime().isAfter(bedTime) && now.toLocalTime().isBefore(wakeUpTime)
                } else {
                    // Edge case: bedtime after midnight (e.g., bed at 23:00, wake at 7:00)
                    now.toLocalTime().isAfter(bedTime) || now.toLocalTime().isBefore(wakeUpTime)
                }
                
                if (isShowingWakeUpCountdown) {
                    // Show countdown to wake-up
                    views.setTextViewText(R.id.widget_bedtime_label, "until wake-up")
                    
                    // Set the wake-up time in the yellow square area
                    views.setTextViewText(R.id.widget_bedtime_target_time, "Wake: ${wakeUpTime.format(timeFormatter)}")
                    
                    val wakeUpTimeTomorrow = if (now.toLocalTime().isBefore(wakeUpTime)) {
                        now.with(wakeUpTime)
                    } else {
                        now.with(wakeUpTime).plusDays(1)
                    }
                    val duration = Duration.between(now, wakeUpTimeTomorrow)
                    
                    // Calculate progress for sleep time
                    val totalSleepDuration = if (bedTime.isBefore(wakeUpTime)) {
                        Duration.between(bedTime, wakeUpTime)
                    } else {
                        Duration.between(bedTime, wakeUpTime.plus(Duration.ofHours(24)))
                    }.toMinutes()
                    
                    val elapsedSleepMinutes = if (now.toLocalTime().isBefore(wakeUpTime)) {
                        Duration.between(now, wakeUpTimeTomorrow).toMinutes()
                    } else {
                        0
                    }
                    
                    val sleepProgress = if (totalSleepDuration > 0) {
                        (((totalSleepDuration - elapsedSleepMinutes) * 100) / totalSleepDuration).toInt().coerceIn(0, 100)
                    } else 0
                    android.util.Log.d("WidgetProgress", "Sleep progress: $sleepProgress%")
                    try {
                        updateProgressDrawable(views, R.id.widget_bedtime_progress, sleepProgress, context)
                    } catch (e: Exception) {
                        android.util.Log.e("WidgetProgress", "Error setting sleep progress", e)
                    }
                    
                    views.setTextViewText(R.id.widget_bedtime_countdown, formatDuration(duration))
                } else {
                    // Show countdown to bedtime
                    views.setTextViewText(R.id.widget_bedtime_label, "until bedtime")
                    
                    // Set the bedtime in the yellow square area
                    views.setTextViewText(R.id.widget_bedtime_target_time, "Bed: ${bedTime.format(timeFormatter)}")
                    
                    val bedTimeTomorrow = now.with(bedTime).plusDays(if (now.toLocalTime().isBefore(bedTime)) 0 else 1)
                    val duration = Duration.between(now, bedTimeTomorrow)
                    
                    // Calculate progress for day time
                    val wakingHours = if (wakeUpTime.isBefore(bedTime)) {
                        Duration.between(wakeUpTime, bedTime)
                    } else {
                        Duration.between(wakeUpTime, bedTime.plus(Duration.ofHours(24)))
                    }.toMinutes()
                    
                    val remainingWakingMinutes = duration.toMinutes()
                    val elapsedWakingMinutes = if (now.toLocalTime().isAfter(wakeUpTime)) {
                        wakingHours - remainingWakingMinutes
                    } else {
                        0
                    }
                    
                    val dayProgress = if (wakingHours > 0) {
                        ((elapsedWakingMinutes * 100) / wakingHours).toInt().coerceIn(0, 100)
                    } else 0
                    android.util.Log.d("WidgetProgress", "Day progress: $dayProgress%")
                    try {
                        updateProgressDrawable(views, R.id.widget_bedtime_progress, dayProgress, context)
                    } catch (e: Exception) {
                        android.util.Log.e("WidgetProgress", "Error setting day progress", e)
                    }
                    
                    views.setTextViewText(R.id.widget_bedtime_countdown, formatDuration(duration))
                }
                
                // Update sleep streak on the bottom right with emoticons based on streak length
                val bedtimeEmoji = EmojiUtils.getEmoticonsByStreakLength(sleepStreak)
                views.setTextViewText(R.id.widget_bedtime_streak, "Sleep: ${sleepStreak} day${if (sleepStreak != 1) "s" else ""} $bedtimeEmoji")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Set fallback UI in case of any error
            views.setTextViewText(R.id.widget_header, "DAILY PROGRESS")
            views.setTextViewText(R.id.widget_fasting_countdown, "--:--")
            views.setTextViewText(R.id.widget_fasting_end_time, "Ends: --:--")
            views.setTextViewText(R.id.widget_bedtime_countdown, "--:--")
            views.setTextViewText(R.id.widget_bedtime_label, "tap to open app")
            views.setTextViewText(R.id.widget_bedtime_target_time, "Bed: --:--")
            views.setTextViewText(R.id.widget_fasting_streak, "Fast: - days")
            views.setTextViewText(R.id.widget_bedtime_streak, "Sleep: - days")
        }
    }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        return String.format("%d:%02d", hours, minutes)
    }
    
    /**
     * Updates the progress drawable by creating a new drawable with the level applied
     */
    private fun updateProgressDrawable(views: RemoteViews, viewId: Int, progress: Int, context: Context) {
        // Instead of creating bitmaps which can cause "Problem Loading Widget",
        // we'll directly set the level on the ImageView
        views.setInt(viewId, "setImageLevel", progress * 100)
    }

    companion object {
        private const val WIDGET_UPDATE_WORK_NAME = "widget_countdown_update_work"
        
        /**
         * Request widget update using broadcast
         */
        fun requestUpdate(context: Context) {
            try {
                android.util.Log.d("FastingBedtimeWidget", "Requesting widget update at ${LocalDateTime.now()}")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, FastingBedtimeWidget::class.java)
                )
                
                android.util.Log.d("FastingBedtimeWidget", "Found ${appWidgetIds.size} widgets to update")
                
                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    context.sendBroadcast(intent)
                    android.util.Log.d("FastingBedtimeWidget", "Update broadcast sent successfully")
                } else {
                    android.util.Log.d("FastingBedtimeWidget", "No widgets to update")
                }
            } catch (e: Exception) {
                android.util.Log.e("FastingBedtimeWidget", "Error requesting widget update", e)
            }
        }
        
        /**
         * Schedule periodic updates for the widget countdown using WorkManager
         * This ensures the widget is updated frequently for accurate countdown display
         */
        private fun schedulePeriodicUpdates(context: Context) {
            try {
                android.util.Log.d("FastingBedtimeWidget", "Scheduling periodic widget updates at ${LocalDateTime.now()}")
                
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, FastingBedtimeWidget::class.java)
                )
                
                // Only schedule updates if there are widgets
                if (appWidgetIds.isEmpty()) {
                    android.util.Log.d("FastingBedtimeWidget", "No widgets found, not scheduling updates")
                    return
                }
                
                android.util.Log.d("FastingBedtimeWidget", "Found ${appWidgetIds.size} widgets, scheduling updates")
                
                try {
                    // Cancel any existing work
                    WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK_NAME)
                    android.util.Log.d("FastingBedtimeWidget", "Cancelled existing work")
                } catch (e: Exception) {
                    android.util.Log.e("FastingBedtimeWidget", "Error cancelling existing work", e)
                }
                
                // Create a work request that repeats every minute
                val updateRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                    15, TimeUnit.MINUTES,  // Update every 15 minutes (system limitation)
                    5, TimeUnit.MINUTES    // Flex time of 5 minutes
                )
                    .setConstraints(Constraints.Builder()
                        .setRequiresBatteryNotLow(true)  // Don't update if battery is critically low
                        .build())
                    .build()
                
                // Log the request details
                android.util.Log.d("FastingBedtimeWidget", "Created periodic work request with ID: ${updateRequest.id}")
                
                // Enqueue the work request, replacing any existing one
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WIDGET_UPDATE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    updateRequest
                )
                
                android.util.Log.d("FastingBedtimeWidget", "Scheduled periodic widget updates successfully")
                
                // Also schedule a one-time immediate update to ensure the widget is updated right away
                val immediateUpdateRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .build()
                
                WorkManager.getInstance(context).enqueue(immediateUpdateRequest)
                android.util.Log.d("FastingBedtimeWidget", "Scheduled immediate update with ID: ${immediateUpdateRequest.id}")
                
            } catch (e: Exception) {
                android.util.Log.e("FastingBedtimeWidget", "Error scheduling periodic updates", e)
            }
        }
    }
}

/**
 * Worker class to perform periodic widget updates
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        try {
            // Log the update
            val now = LocalDateTime.now()
            android.util.Log.d("WidgetUpdateWorker", "Starting widget update at $now")
            
            // Request widget update
            FastingBedtimeWidget.requestUpdate(applicationContext)
            
            android.util.Log.d("WidgetUpdateWorker", "Widget update complete at ${LocalDateTime.now()}")
            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WidgetUpdateWorker", "Error updating widget", e)
            return Result.failure()
        }
    }
} 