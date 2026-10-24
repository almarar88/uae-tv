package ae.majlis.tv

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.util.Calendar
import java.util.TimeZone

object Adhan {
    const val CHANNEL = "adhan"
    private val UAE = TimeZone.getTimeZone("Asia/Dubai")

    fun enabled(c: Context) = Store.get(c, "adhan", "on") == "on"

    /** Schedules the next upcoming prayer (excluding sunrise). Re-scheduled after each fire. */
    fun scheduleNext(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 7, Intent(c, AdhanReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        if (!enabled(c)) return
        val next = nextPrayer(c) ?: return
        val i = Intent(c, AdhanReceiver::class.java).putExtra("idx", next.first).putExtra("at", next.second)
        val p2 = PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, p2)
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, p2)
    }

    fun nextPrayer(c: Context): Pair<Int, Long>? {
        val city = Cities.byId(Store.get(c, "city", "abudhabi"))
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(UAE)
        for (day in 0..1) {
            val t = PrayerCalc.compute(city, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            for (i in t.indices) if (i != 1 && t[i] > now + 5_000) return i to t[i]
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, c.getString(R.string.adhan_channel), NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        nm.createNotificationChannel(ch)
    }

    fun notify(c: Context, idx: Int) {
        ensureChannel(c)
        val city = Cities.byId(Store.get(c, "city", "abudhabi"))
        val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(c, CHANNEL) else @Suppress("DEPRECATION") android.app.Notification.Builder(c)
        val n = b.setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("حان الآن موعد أذان ${PrayerCalc.NAMES_AR[idx]}")
            .setContentText("حسب توقيت ${city.ar}")
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setContentIntent(open).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify(100 + idx, n)
    }
}

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val idx = i.getIntExtra("idx", -1)
        val at = i.getLongExtra("at", 0)
        // ignore stale alarms (e.g. delivered >15 min late after the device slept)
        if (idx >= 0 && Math.abs(System.currentTimeMillis() - at) < 15 * 60_000) {
            Adhan.notify(c, idx)
            MainActivity.instance?.runOnUiThread { MainActivity.instance?.js("window.onAdhan&&onAdhan($idx)") }
        }
        Adhan.scheduleNext(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Adhan.scheduleNext(c)
}
