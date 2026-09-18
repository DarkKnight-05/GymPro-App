package com.gymmanager.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.gymmanager.app.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.app.PendingIntent
import android.content.Intent
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "fee_reminders"

class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val user = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        val gymId = user.getString("gymId") ?: return Result.success()
        val branchRemoteId = user.getString("branchRemoteId")
        val threshold = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000
        val query = FirebaseFirestore.getInstance().collection("gyms").document(gymId).collection("members")
        // Only fetch members whose due date has actually reached the reminder window.
        // This avoids downloading the entire member collection every day.
        val dueQuery = if (branchRemoteId != null) {
            query.whereEqualTo("branchRemoteId", branchRemoteId)
                .whereLessThanOrEqualTo("nextDueDateMillis", threshold)
        } else {
            query.whereLessThanOrEqualTo("nextDueDateMillis", threshold)
        }
        val snap = dueQuery.get().await()
        val dueMembers = snap.documents.filter { d ->
            !(d.getBoolean("isArchived") ?: false)
        }

        if (dueMembers.isNotEmpty()) {
            createChannelIfNeeded()
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Fee reminders")
                .setContentText("${dueMembers.size} member(s) have fees due or overdue")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        dueMembers.joinToString("\n") { "• ${it.getString("name") ?: "Member"}" }
                    )
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(1001, notification)
        }
        return Result.success()
    }

    private fun createChannelIfNeeded() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Fee Reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "fee_reminder_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
