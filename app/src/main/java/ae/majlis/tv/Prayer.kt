package ae.majlis.tv

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/** A UAE city with coordinates (decimal degrees). */
data class City(val id: String, val ar: String, val lat: Double, val lng: Double)

object Cities {
    val all = listOf(
        City("abudhabi", "أبوظبي", 24.4539, 54.3773),
        City("alain", "العين", 24.2075, 55.7447),
        City("dubai", "دبي", 25.2048, 55.2708),
        City("sharjah", "الشارقة", 25.3463, 55.4209),
        City("ajman", "عجمان", 25.4052, 55.5136),
        City("uaq", "أم القيوين", 25.5647, 55.5552),
        City("rak", "رأس الخيمة", 25.8007, 55.9762),
        City("fujairah", "الفجيرة", 25.1288, 56.3265),
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: all[0]
}

/**
 * On-device prayer-time calculation (original implementation of the standard
 * solar-position formulas). UAE method: Fajr 18.2°, Isha 18.2°, Asr = shadow factor 1,
 * with minute adjustments matching the UAE ("Dubai") convention used by common calculators.
 */
object PrayerCalc {
    const val FAJR_ANGLE = 18.2
    const val ISHA_ANGLE = 18.2
    // minutes: fajr, sunrise, dhuhr, asr, maghrib, isha
    val ADJ = intArrayOf(0, 0, 3, 0, 3, 0)
    val NAMES_AR = arrayOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    val KEYS = arrayOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")

    private fun d2r(d: Double) = d * PI / 180
    private fun r2d(r: Double) = r * 180 / PI
    private fun fix(a: Double, b: Double): Double { val x = a - b * floor(a / b); return if (x < 0) x + b else x }

    private fun julian(y: Int, m: Int, d: Int): Double {
        var yy = y; var mm = m
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = floor(yy / 100.0); val b = 2 - a + floor(a / 4)
        return floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    /** returns (declination deg, equation of time hours) */
    private fun sun(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(d2r(g)) + 0.020 * sin(d2r(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = fix(r2d(atan2(cos(d2r(e)) * sin(d2r(l)), cos(d2r(l)))) / 15, 24.0)
        val eqt = q / 15 - ra
        val decl = r2d(asin(sin(d2r(e)) * sin(d2r(l))))
        return decl to eqt
    }

    /**
     * Times for the given local calendar day, as epoch millis.
     * tzHours: UTC offset of the city (UAE = +4, no DST).
     */
    fun compute(city: City, y: Int, m: Int, d: Int, tzHours: Double = 4.0): LongArray {
        val jd = julian(y, m, d) - city.lng / (15 * 24)
        val lat = city.lat
        fun noon(t: Double): Double { val (_, eqt) = sun(jd + t); return fix(12 - eqt, 24.0) }
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val (decl, _) = sun(jd + t)
            val n = noon(t)
            val c = (-sin(d2r(angle)) - sin(d2r(decl)) * sin(d2r(lat))) / (cos(d2r(decl)) * cos(d2r(lat)))
            val tt = r2d(acos(c.coerceIn(-1.0, 1.0))) / 15
            return n + if (ccw) -tt else tt
        }
        fun asr(factor: Double, t: Double): Double {
            val (decl, _) = sun(jd + t)
            val angle = -r2d(atan(1 / (factor + tan(d2r(abs(lat - decl))))))
            return angleTime(angle, t, false)
        }
        // two passes for accuracy
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val p = t.map { it / 24 }
            t = doubleArrayOf(
                angleTime(FAJR_ANGLE, p[0], true),
                angleTime(0.833, p[1], true),
                noon(p[2]),
                asr(1.0, p[3]),
                angleTime(0.833, p[4], false),
                angleTime(ISHA_ANGLE, p[5], false),
            )
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear(); cal.set(y, m - 1, d, 0, 0, 0)
        val base = cal.timeInMillis
        return LongArray(6) { i ->
            val localHours = t[i] + tzHours - city.lng / 15
            val utcHours = localHours - tzHours
            // round to nearest minute
            val mins = Math.round(utcHours * 60) + ADJ[i]
            base + mins * 60_000L
        }
    }
}
