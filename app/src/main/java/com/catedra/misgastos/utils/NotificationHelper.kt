package com.catedra.misgastos.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.catedra.misgastos.R

object NotificationHelper {

    private const val CHANNEL_ID = "expense_alerts_channel"
    private const val CHANNEL_NAME = "Alertas de gastos"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando se supere el límite mensual de gastos"
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
            .setContentTitle("Límite mensual superado")
            .setContentText("Superaste tu límite mensual!!!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Superaste tu límite mensual de gastos. Total actual: $monthlyTotalFormatted. Límite configurado: $monthlyLimitFormatted."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}