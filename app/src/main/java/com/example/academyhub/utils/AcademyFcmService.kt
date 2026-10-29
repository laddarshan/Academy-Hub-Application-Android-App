package com.example.academyhub.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.academyhub.R
import com.example.academyhub.UserDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AcademyFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "Academy Hub"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val type  = message.data["type"] ?: "announcement"
        val announcementId = message.data["announcementId"] ?: ""
        val priority = message.data["priority"] ?: "normal"

        saveNotificationToFirestore(title, body, type, announcementId, priority)
        showNotification(title, body, priority)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    private fun saveNotificationToFirestore(
        title: String, body: String, type: String,
        announcementId: String, priority: String
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val notif = hashMapOf(
            "title"          to title,
            "body"           to body,
            "type"           to type,
            "announcementId" to announcementId,
            "priority"       to priority,
            "isRead"         to false,
            "createdAt"      to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("UserNotifications")
            .document(uid)
            .collection("notifications")
            .add(notif)
    }

    private fun showNotification(title: String, body: String, priority: String) {
        val channelId = "academy_hub_channel"
        val manager   = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Academy Hub",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Academy Hub Notifications" }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, UserDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifPriority = if (priority == "important")
            NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(notifPriority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}