// Compares on-device PrayerCalc against api.aladhan.com method 16 (Dubai, 18.2°/18.2°).
// Run: kotlinc ../../app/src/main/java/ae/majlis/tv/Prayer.kt PrayerCheck.kt -include-runtime -d pc.jar && java -jar pc.jar
import ae.majlis.tv.*
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
fun main() {
    val fmt = SimpleDateFormat("HH:mm").apply { timeZone = TimeZone.getTimeZone("Asia/Dubai") }
    val keys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    var worst = 0
    for (c in Cities.all) for ((y, m, d) in listOf(Triple(2026, 10, 24), Triple(2026, 6, 21), Triple(2026, 12, 21), Triple(2027, 3, 20))) {
        val t = PrayerCalc.compute(c, y, m, d).map { fmt.format(Date(it)) }
        val js = URL("https://api.aladhan.com/v1/timings/%02d-%02d-%d?latitude=${c.lat}&longitude=${c.lng}&method=16".format(d, m, y)).readText()
        val ref = keys.map { Regex("\"$it\":\"(\\d\\d:\\d\\d)").find(js)!!.groupValues[1] }
        val diff = t.indices.map { i -> val (a, b) = t[i].split(":").map(String::toInt); val (p, q) = ref[i].split(":").map(String::toInt); (a * 60 + b) - (p * 60 + q) }
        worst = maxOf(worst, diff.maxOf { Math.abs(it) })
        println("${c.id} $y-$m-$d  ours=$t  aladhan=$ref  diff=$diff")
    }
    println("MAX ABS DIFF (min) = $worst")
}
