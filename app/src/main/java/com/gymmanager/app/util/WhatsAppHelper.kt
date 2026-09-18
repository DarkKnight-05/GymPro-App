package com.gymmanager.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.gymmanager.app.data.Member
import java.text.SimpleDateFormat
import java.util.*

object WhatsAppHelper {

    /**
     * Opens WhatsApp with a pre-filled fee-reminder message for the given member.
     * The gym owner still has to tap "Send" inside WhatsApp — this is the free,
     * no-approval-required way to do this (the paid WhatsApp Business API is the
     * only way to send fully automatically).
     */
    fun sendFeeReminder(context: Context, member: Member) {
        val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dueDateStr = df.format(Date(member.nextDueDateMillis))
        val isOverdue = member.nextDueDateMillis < System.currentTimeMillis()

        val message = if (isOverdue) {
            "Hi ${member.name}, this is a reminder from the gym that your membership fee " +
                "of ₹${"%.0f".format(member.feeAmount)} was due on $dueDateStr and is now overdue. " +
                "Please make the payment at your earliest convenience. Thank you!"
        } else {
            "Hi ${member.name}, this is a friendly reminder that your gym membership fee " +
                "of ₹${"%.0f".format(member.feeAmount)} is due on $dueDateStr. " +
                "Please renew on time to keep your membership active. Thank you!"
        }

        // Normalize phone: strip spaces/dashes, keep leading +
        val cleanPhone = member.phone.replace(Regex("[^+0-9]"), "")

        val uri = Uri.parse(
            "https://wa.me/${cleanPhone.removePrefix("+")}?text=${Uri.encode(message)}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_LONG).show()
        }
    }
}
