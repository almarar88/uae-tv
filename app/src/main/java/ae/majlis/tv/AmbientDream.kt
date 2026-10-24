package ae.majlis.tv

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.webkit.WebView

/** System screensaver: shows the ambient page (photo slideshow, clock, dates, next prayer). */
class AmbientDream : DreamService() {
    private var web: WebView? = null
    @SuppressLint("SetJavaScriptEnabled")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true; isScreenBright = false
        val w = WebView(this)
        w.setBackgroundColor(0xFF000000.toInt())
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = true
        w.addJavascriptInterface(DreamBridge(this), "Android")
        w.loadUrl("file:///android_asset/web/index.html#ambient")
        setContentView(w); web = w
    }
    override fun onDetachedFromWindow() { web?.destroy(); web = null; super.onDetachedFromWindow() }
}

/** Minimal read-only bridge for the dream (no app launching or AI). */
class DreamBridge(private val s: DreamService) {
    @android.webkit.JavascriptInterface fun prefs(): String = Store.all(s).toString()
    @android.webkit.JavascriptInterface fun prayer(cityId: String, y: Int, m: Int, d: Int): String =
        org.json.JSONArray(PrayerCalc.compute(Cities.byId(cityId), y, m, d).toList()).toString()
    @android.webkit.JavascriptInterface fun cities(): String = org.json.JSONArray(Cities.all.map { org.json.JSONObject().put("id", it.id).put("ar", it.ar).put("lat", it.lat).put("lng", it.lng) }).toString()
    @android.webkit.JavascriptInterface fun hijri(epoch: Double): String {
        val cal = android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Dubai"), android.icu.util.ULocale("ar"))
        cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = epoch.toLong()
        return org.json.JSONObject().put("d", cal.get(android.icu.util.Calendar.DAY_OF_MONTH)).put("m", cal.get(android.icu.util.Calendar.MONTH) + 1).put("y", cal.get(android.icu.util.Calendar.YEAR)).toString()
    }
    @android.webkit.JavascriptInterface fun isDream(): Boolean = true
}
