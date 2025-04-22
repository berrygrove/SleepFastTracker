package nl.berrygrove.sft.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import nl.berrygrove.sft.MainActivity
import nl.berrygrove.sft.R
import nl.berrygrove.sft.data.model.UserSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "sleepfasttracker_notifications"
    private val workManager = WorkManager.getInstance(context)
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SleepFastTracker Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for sleep and fasting tracking"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    
    private suspend fun getUserName(): String {
        val app = context.applicationContext as? nl.berrygrove.sft.SleepFastTrackerApplication
        val userSettings = app?.userSettingsRepository?.getUserSettings()
        val name = userSettings?.name ?: ""
        return if (name.isNotEmpty()) name else "there"
    }
    
    suspend fun showBedtimeCheckNotification() {
        val userName = getUserName()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sleep Check")
            .setContentText("Hey $userName, did you go to bed on time? 🛏️")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
            
        notificationManager.notify(1, notification)
    }
    
    suspend fun showBedtimeReminderNotification() {
        val userName = getUserName()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bedtime Reminder")
            .setContentText("Hey $userName, your bedtime starts soon. Keep up the streak! 😴")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
            
        notificationManager.notify(2, notification)
    }
    
    suspend fun showWeightUpdateNotification() {
        val userName = getUserName()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Weekly Weight Update")
            .setContentText("$userName, it's time to update your weight progress ⚖️")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
            
        notificationManager.notify(3, notification)
    }
    
    suspend fun showFastingEndNotification() {
        val userName = getUserName()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fasting Complete")
            .setContentText("Great job, $userName! You can eat now 😁")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
            
        notificationManager.notify(4, notification)
    }
    
    suspend fun showEatingEndNotification() {
        val userName = getUserName()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Eating Window Closed")
            .setContentText("$userName, it's time to start fasting again 💪")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
            
        notificationManager.notify(5, notification)
    }
    
    fun scheduleNotifications(settings: UserSettings) {
        // Cancel any existing notifications
        workManager.cancelAllWork()
        
        // If notifications are disabled globally, don't schedule any
        if (!settings.notificationsEnabled) {
            return
        }
        
        // Schedule bedtime check (1 hour after wake up)
        if (settings.bedtimeCheckNotificationEnabled) {
            val wakeUpTime = LocalTime.parse(settings.wakeUpTime, DateTimeFormatter.ofPattern("HH:mm"))
            val bedtimeCheckTime = wakeUpTime.plusHours(1)
            scheduleNotification(bedtimeCheckTime, 1)
        }
        
        // Schedule bedtime reminder (15 minutes before bedtime)
        if (settings.bedtimeReminderNotificationEnabled) {
            val bedTime = LocalTime.parse(settings.bedTime, DateTimeFormatter.ofPattern("HH:mm"))
            val bedtimeReminderTime = bedTime.minusMinutes(15)
            scheduleNotification(bedtimeReminderTime, 2)
        }
        
        // Schedule weight update (every Sunday 30 minutes after wake up)
        if (settings.weightUpdateNotificationEnabled) {
            val weightUpdateTime = LocalTime.parse(settings.wakeUpTime, DateTimeFormatter.ofPattern("HH:mm"))
                .plusMinutes(30)
            scheduleWeeklyNotification(weightUpdateTime, 3)
        }
        
        // Schedule fasting notifications based on eating window
        val eatingStartTime = LocalTime.parse(settings.eatingStartTime, DateTimeFormatter.ofPattern("HH:mm"))
        val eatingEndTime = eatingStartTime.plusHours(settings.eatingWindowHours.toLong())
        
        if (settings.fastingEndNotificationEnabled) {
            scheduleNotification(eatingStartTime, 4)
        }
        
        if (settings.eatingEndNotificationEnabled) {
            scheduleNotification(eatingEndTime, 5)
        }
    }
    
    private fun scheduleNotification(time: LocalTime, notificationId: Int) {
        val now = LocalDateTime.now()
        var scheduledTime = LocalDateTime.of(now.toLocalDate(), time)
        
        // If the time has already passed today, schedule for tomorrow
        if (scheduledTime.isBefore(now)) {
            scheduledTime = scheduledTime.plusDays(1)
        }
        
        val delay = Duration.between(now, scheduledTime)
        
        val notificationWork = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf("notification_type" to notificationId))
            .build()
            
        workManager.enqueue(notificationWork)
    }
    
    private fun scheduleWeeklyNotification(time: LocalTime, notificationId: Int) {
        val now = LocalDateTime.now()
        var scheduledTime = LocalDateTime.of(now.toLocalDate(), time)
        
        // If the time has already passed today, schedule for next week
        if (scheduledTime.isBefore(now)) {
            scheduledTime = scheduledTime.plusDays(7)
        }
        
        val delay = Duration.between(now, scheduledTime)
        
        val notificationWork = PeriodicWorkRequestBuilder<NotificationWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf("notification_type" to notificationId))
            .build()
            
        workManager.enqueue(notificationWork)
    }
} 