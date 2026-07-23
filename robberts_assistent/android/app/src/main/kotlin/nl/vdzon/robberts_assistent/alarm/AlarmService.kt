package nl.vdzon.robberts_assistent.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import nl.vdzon.robberts_assistent.R

/**
 * Foreground-service die een afgegaan alarm "laat bellen": speelt eenmalig een 2 minuten durend,
 * oplopend piepgeluid ([R.raw.alarm_beep]), trilt (pas na [VIBRATION_DELAY_MS], zodat het geluid
 * eerst even de kans krijgt om te wekken), en toont een high-importance full-screen notificatie
 * waarvan de full-screen-intent de [AlarmActivity] over het lockscreen opent. Blijft draaien tot
 * de gebruiker Sluit of Snooze kiest, ook nadat het geluid vanzelf is afgelopen.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentId: Int = 0
    private var currentMessage: String = "Alarm"
    private val vibrationHandler = Handler(Looper.getMainLooper())
    private val startVibrationRunnable = Runnable { startVibration() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentId = intent.getIntExtra(AlarmScheduling.EXTRA_ID, 0)
                currentMessage = intent.getStringExtra(AlarmScheduling.EXTRA_MESSAGE) ?: "Alarm"
                startForegroundWithNotification()
                acquireWakeLock()
                startAlarmSound()
                vibrationHandler.postDelayed(startVibrationRunnable, VIBRATION_DELAY_MS)
                launchAlarmActivity()
            }
            ACTION_DISMISS -> stopEverything()
            ACTION_SNOOZE -> {
                AlarmScheduling.snooze(this, currentId, currentMessage)
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val fullScreen = activityPendingIntent()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Alarm")
            .setContentText(currentMessage)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Snooze", servicePendingIntent(ACTION_SNOOZE, 1))
            .addAction(0, "Sluit", servicePendingIntent(ACTION_DISMISS, 2))
            .build()
    }

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduling.EXTRA_ID, currentId)
            putExtra(AlarmScheduling.EXTRA_MESSAGE, currentMessage)
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AlarmService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Backup naast de full-screen-intent: activiteit direct proberen te starten. */
    private fun launchAlarmActivity() {
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AlarmScheduling.EXTRA_ID, currentId)
                    putExtra(AlarmScheduling.EXTRA_MESSAGE, currentMessage)
                },
            )
        }
    }

    private fun startAlarmSound() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            player = MediaPlayer.create(
                this,
                R.raw.alarm_beep,
                audioAttributes,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            ).apply {
                isLooping = false
                start()
            }
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 800, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "robbertsassistent:alarm",
        ).apply { acquire(5 * 60_000L) }
    }

    private fun stopEverything() {
        vibrationHandler.removeCallbacks(startVibrationRunnable)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        if (wakeLock?.isHeld == true) runCatching { wakeLock?.release() }
        wakeLock = null
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // Geen kanaal-geluid: wij spelen zelf het alarmgeluid via MediaPlayer.
        val channel = NotificationChannel(CHANNEL_ID, "Wekker", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wekkers/alarms van je assistent"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "nl.vdzon.robberts_assistent.ALARM_SERVICE_START"
        const val ACTION_DISMISS = AlarmScheduling.ACTION_DISMISS
        const val ACTION_SNOOZE = AlarmScheduling.ACTION_SNOOZE
        private const val CHANNEL_ID = "assistent_wekker"
        private const val NOTIF_ID = 424242
        private const val VIBRATION_DELAY_MS = 30_000L
    }
}
