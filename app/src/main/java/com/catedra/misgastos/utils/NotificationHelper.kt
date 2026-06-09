package com.catedra.misgastos.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.catedra.misgastos.R

object NotificationHelper {

    private const val CHANNEL_ID = "expense_alerts_channel"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.expense_alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.expense_alerts_channel_description)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showLimitExceededNotification(
        context: Context,
        monthlyTotal: Double,
        monthlyLimit: Double
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monthlyTotalFormatted = "$ %.2f".format(monthlyTotal)
        val monthlyLimitFormatted = "$ %.2f".format(monthlyLimit)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.limit_exceeded_notification_title))
            .setContentText(context.getString(R.string.limit_exceeded_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.limit_exceeded_notification_detail,
                        monthlyTotalFormatted,
                        monthlyLimitFormatted
                    )
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}