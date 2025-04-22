package nl.berrygrove.sft.notification

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import nl.berrygrove.sft.SleepFastTrackerApplication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val notificationHelper = NotificationHelper(applicationContext)
        val notificationType = inputData.getInt("notification_type", 0)
        val app = applicationContext as SleepFastTrackerApplication

        return runBlocking {
            when (notificationType) {
                1 -> {
                    // Bedtime check notification - only show if no sleep record for today
                    val sleepRepository = app.sleepRepository
                    val hasAnsweredToday = sleepRepository.hasAnsweredBedtimeToday()
                    
                    if (!hasAnsweredToday) {
                        notificationHelper.showBedtimeCheckNotification()
                    }
                }
                2 -> {
                    // Bedtime reminder notification - always show
                    notificationHelper.showBedtimeReminderNotification()
                }
                3 -> {
                    // Weight update notification - only show if last weight record is more than 5 days old
                    val weightRepository = app.weightRecordRepository
                    val latestWeightRecord = weightRepository.getLatestWeightRecord()
                    
                    if (latestWeightRecord == null) {
                        // No weight records yet, show notification
                        notificationHelper.showWeightUpdateNotification()
                    } else {
                        val daysSinceLastRecord = ChronoUnit.DAYS.between(
                            latestWeightRecord.timestamp.toLocalDate(),
                            LocalDate.now()
                        )
                        
                        if (daysSinceLastRecord > 5) {
                            notificationHelper.showWeightUpdateNotification()
                        }
                    }
                }
                4 -> {
                    // Fasting end notification (can eat) - only show if currently fasting
                    val fastingRepository = app.fastingRepository
                    val latestFastingRecord = fastingRepository.getLatestFastingRecord()
                    
                    if (latestFastingRecord == null || latestFastingRecord.state) {
                        // No record or in fasting state, show notification
                        notificationHelper.showFastingEndNotification()
                    }
                }
                5 -> {
                    // Eating end notification (start fasting) - only show if currently eating
                    val fastingRepository = app.fastingRepository
                    val latestFastingRecord = fastingRepository.getLatestFastingRecord()
                    
                    if (latestFastingRecord == null || !latestFastingRecord.state) {
                        // No record or in eating state, show notification
                        notificationHelper.showEatingEndNotification()
                    }
                }
            }
            
            Result.success()
        }
    }
} 