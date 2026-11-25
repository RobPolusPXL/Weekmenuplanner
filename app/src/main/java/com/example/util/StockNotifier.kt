package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.PantryItem

/**
 * Toont een Android-melding zodra een voorraaditem bijna op raakt.
 * Per item maar één keer, tot het weer aangevuld is (bijgehouden in SharedPreferences).
 * Werkt terwijl de app draait (ook op de achtergrond zolang Android ze niet afsluit).
 */
object StockNotifier {
    private const val CHANNEL_ID = "voorraad_bijna_op"
    private const val PREFS = "stock_notifier"
    private const val KEY_NOTIFIED = "notified_low_ids"
    private const val NOTIFICATION_ID = 4201

    fun update(context: Context, items: List<PantryItem>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val already = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        val low = items.filter { it.isLow }
        val newlyLow = low.filter { it.id !in already }
        prefs.edit().putStringSet(KEY_NOTIFIED, low.map { it.id }.toSet()).apply()
        if (newlyLow.isEmpty()) return
        if (!canNotify(context)) return
        ensureChannel(context)
        show(context, newlyLow)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Voorraad bijna op", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Melding wanneer een product in de voorraad bijna op is"
            }
        )
    }

    @SuppressLint("MissingPermission") // gecontroleerd in canNotify()
    private fun show(context: Context, items: List<PantryItem>) {
        val names = items.joinToString(", ") { it.name.replaceFirstChar { c -> c.uppercase() } }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pantry)
            .setContentTitle(if (items.size == 1) "Bijna op: $names" else "Bijna op in de voorraad")
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
