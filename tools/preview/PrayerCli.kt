// CLI wrapper around the app's PrayerCalc, used by the desktop preview server.
import ae.majlis.tv.*
fun main(a: Array<String>) {
    val t = PrayerCalc.compute(Cities.byId(a[0]), a[1].toInt(), a[2].toInt(), a[3].toInt())
    println(t.joinToString(",", "[", "]"))
}
