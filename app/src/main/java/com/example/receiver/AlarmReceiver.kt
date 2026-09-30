package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.GameNetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val stationId = intent.getIntExtra("station_id", -1)
        val reservationId = intent.getLongExtra("reservation_id", -1L)
        val minutesBefore = intent.getIntExtra("minutes_before", -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repository = GameNetRepository(db)
                val enabledSetting = repository.getSetting("notifications_enabled") ?: "true"
                val notificationsEnabled = enabledSetting.toBoolean()
                if (notificationsEnabled) {
                    if (stationId != -1) {
                        showNotification(context, stationId)
                        triggerVibration(context)
                    } else if (reservationId != -1L) {
                        val reservation = repository.getReservationById(reservationId)
                        if (reservation != null) {
                            val lang = repository.getSetting("language") ?: "fa"
                            showReservationNotification(context, reservation, minutesBefore, lang)
                            triggerVibration(context)
                            triggerVibration(context) // Double vibration for reservations as they require higher sensitivity!
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showReservationNotification(context: Context, reservation: com.example.data.Reservation, minutesBefore: Int, lang: String) {
        val channelId = "gamenet_reservations"
        val notificationId = 20000 + (reservation.id * 10 + minutesBefore).toInt()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reservation Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High sensitivity alerts for upcoming reservations"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (lang == "fa") {
            "یادآوری مهم رزرو ($minutesBefore دقیقه مانده)"
        } else {
            "Important Reservation Alert ($minutesBefore min remaining)"
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        val formattedTime = sdf.format(Date(reservation.reservationTimeMillis))

        val text = if (lang == "fa") {
            "رزرو ${reservation.fullName} (${reservation.durationMinutes} دقیقه) برای ساعت $formattedTime"
        } else {
            "Reservation for ${reservation.fullName} (${reservation.durationMinutes} mins) scheduled at $formattedTime"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.ic_notification_gn)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showNotification(context: Context, stationId: Int) {
        val channelId = "gamenet_alarms"
        val notificationId = 1000 + stationId

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Station Time Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a station has 1 minute remaining"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.ic_notification_gn)
            .setContentTitle("Station $stationId - Time Warning")
            .setContentText("One minute remaining for Station $stationId.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun triggerVibration(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 400, 200, 400), -1)
        }
    }
}
