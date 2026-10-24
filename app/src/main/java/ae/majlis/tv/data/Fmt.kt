package ae.majlis.tv.data

import android.content.Context
import ae.majlis.tv.Store
import java.util.Calendar
import java.util.TimeZone

/** UAE time & Arabic wording helpers (ported from app.js). UAE = UTC+4, no DST. */
object Fmt {
    val TZ: TimeZone = TimeZone.getTimeZone("Asia/Dubai")
    val HIJRI_M = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    val GREG_M = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val DAYS = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    val PNAMES = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")

    data class Parts(val y: Int, val m: Int, val d: Int, val h: Int, val mi: Int, val wd: Int)
    fun parts(t: Long = System.currentTimeMillis()): Parts {
        val c = Calendar.getInstance(TZ).apply { timeInMillis = t }
        return Parts(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.DAY_OF_WEEK) - 1)
    }
    private fun pad(n: Int) = n.toString().padStart(2, '0')
    fun time(c: Context, t: Long, ampm: Boolean = true): String {
        val p = parts(t)
        if (Store.get(c, "clock", "12") == "24") return pad(p.h) + ":" + pad(p.mi)
        val h = p.h % 12; return (if (h == 0) 12 else h).toString() + ":" + pad(p.mi) + if (ampm) (if (p.h < 12) " ص" else " م") else ""
    }
    fun hijri(t: Long): Triple<Int, Int, Int> {
        val cal = android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Dubai"), android.icu.util.ULocale("ar"))
        cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = t
        return Triple(cal.get(android.icu.util.Calendar.DAY_OF_MONTH), cal.get(android.icu.util.Calendar.MONTH) + 1, cal.get(android.icu.util.Calendar.YEAR))
    }
    fun gregLine(t: Long = System.currentTimeMillis()): String { val p = parts(t); return "${DAYS[p.wd]} ${p.d} ${GREG_M[p.m - 1]} ${p.y}" }
    fun hijriLine(t: Long = System.currentTimeMillis()): String = try { val (d, m, y) = hijri(t); "$d ${HIJRI_M[m - 1]} $y هـ" } catch (_: Exception) { "" }

    fun arCount(n: Int, one: String, two: String, few: String, many: String) = when { n == 1 -> one; n == 2 -> two; n in 3..10 -> "$n $few"; else -> "$n $many" }
    fun minW(n: Int) = arCount(n, "دقيقة", "دقيقتين", "دقائق", "دقيقة")
    fun hrW(n: Int) = arCount(n, "ساعة", "ساعتين", "ساعات", "ساعة")
    fun until(ms: Long): String {
        val m = maxOf(0, Math.round(ms / 60000.0).toInt()); val h = m / 60; val r = m % 60
        return when { h > 0 && r > 0 -> "بعد $h س و$r د"; h > 0 -> "بعد ${hrW(h)}"; r > 0 -> "بعد ${minW(r)}"; else -> "الآن" }
    }
    fun ago(t: Long): String {
        val m = Math.round((System.currentTimeMillis() - t) / 60000.0).toInt()
        return when { m < 60 -> "قبل ${minW(maxOf(1, m))}"; m < 1440 -> "قبل ${hrW(Math.round(m / 60.0).toInt())}"; else -> gregLine(t) }
    }
    fun arNum(n: Int) = n.toString().map { if (it.isDigit()) "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")

    /** Fixed, pre-written greetings (no generated text). */
    fun greeting(t: Long = System.currentTimeMillis()): String {
        val h = parts(t).h
        val set = when { h in 4..11 -> listOf("صباح الخير", "صبّحكم الله بالخير"); h in 12..14 -> listOf("يومكم سعيد"); h in 15..21 -> listOf("مساكم الله بالخير", "مساء الخير"); else -> listOf("تصبحون على خير") }
        return set[(parts(t).d) % set.size]
    }
}
