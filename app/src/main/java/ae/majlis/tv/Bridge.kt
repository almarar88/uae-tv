package ae.majlis.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/** JavaScript ⇄ Android bridge. Every async call answers through window.__cb(id, ok, payload). */
class Bridge(private val act: Activity, private val js: (String) -> Unit) {
    private val c: Context = act.applicationContext
    private val pool = Executors.newFixedThreadPool(4)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun reply(id: String, ok: Boolean, payload: String) {
        val q = JSONObject.quote(payload)
        act.runOnUiThread { js("window.__cb&&__cb(${JSONObject.quote(id)},$ok,$q)") }
    }
    private fun async(id: String, block: () -> String) = pool.execute {
        try { reply(id, true, block()) } catch (e: Throwable) { reply(id, false, e.message ?: e.toString()) }
    }

    // ── apps ──────────────────────────────────────────────────────────────
    @JavascriptInterface fun apps(id: String) = async(id) {
        val pm = c.packageManager
        val seen = HashSet<String>(); val out = JSONArray()
        fun add(cat: String) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == c.packageName || !seen.add(pkg)) continue
                val banner = if (cat == Intent.CATEGORY_LEANBACK_LAUNCHER) ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm) else null
                out.put(JSONObject().put("pkg", pkg).put("label", ri.loadLabel(pm).toString())
                    .put("icon", png(ri.loadIcon(pm), 160, 160))
                    .put("banner", banner?.let { png(it, 320, 180) } ?: "")
                    .put("tv", cat == Intent.CATEGORY_LEANBACK_LAUNCHER))
            }
        }
        add(Intent.CATEGORY_LEANBACK_LAUNCHER); add(Intent.CATEGORY_LAUNCHER)
        out.toString()
    }
    private fun png(d: Drawable, w: Int, h: Int): String {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h); d.draw(Canvas(b))
        val bo = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, bo)
        return "data:image/png;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
    }
    @JavascriptInterface fun launch(pkg: String): Boolean {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        Store.recordLaunch(c, pkg)
        act.runOnUiThread { act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }
    @JavascriptInterface fun usage(): String = Store.usage(c).toString()
    @JavascriptInterface fun openSystemSettings() { act.runOnUiThread { try { act.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} } }
    @JavascriptInterface fun openDreamSettings() { act.runOnUiThread { try { act.startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { openSystemSettings() } } }

    // ── prefs ─────────────────────────────────────────────────────────────
    @JavascriptInterface fun prefs(): String = Store.all(c).toString()
    @JavascriptInterface fun setPref(k: String, v: String) {
        Store.set(c, k, v)
        if (k == "city" || k == "adhan") Adhan.scheduleNext(c)
    }
    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME

    // ── prayer ────────────────────────────────────────────────────────────
    @JavascriptInterface fun cities(): String = JSONArray(Cities.all.map { JSONObject().put("id", it.id).put("ar", it.ar).put("lat", it.lat).put("lng", it.lng) }).toString()
    /** y/m/d in UAE calendar; returns epoch-millis array for fajr…isha */
    @JavascriptInterface fun prayer(cityId: String, y: Int, m: Int, d: Int): String =
        JSONArray(PrayerCalc.compute(Cities.byId(cityId), y, m, d).toList()).toString()
    @JavascriptInterface fun hijri(epoch: Double): String {
        // Umm al-Qura calendar via ICU (API 24+)
        val cal = android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Dubai"), android.icu.util.ULocale("ar"))
        cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = epoch.toLong()
        return JSONObject().put("d", cal.get(android.icu.util.Calendar.DAY_OF_MONTH)).put("m", cal.get(android.icu.util.Calendar.MONTH) + 1).put("y", cal.get(android.icu.util.Calendar.YEAR)).toString()
    }
    @JavascriptInterface fun testAdhan() { Adhan.notify(c, 2) }

    // ── network ───────────────────────────────────────────────────────────
    @JavascriptInterface fun get(id: String, url: String) = async(id) {
        require(url.startsWith("https://")) { "https only" }
        val (code, txt) = Http.request(url)
        if (code !in 200..299) throw RuntimeException("HTTP $code"); txt
    }
    private var quranLines: List<String>? = null
    /** Verses of one sura from the bundled Tanzil Uthmani text: JSON array of strings. */
    @JavascriptInterface fun sura(id: String, n: Int) = async(id) {
        val lines = quranLines ?: c.assets.open("quran-uthmani.txt").bufferedReader(Charsets.UTF_8).use { r -> r.readLines().filter { it.isNotBlank() && !it.startsWith("#") } }.also { quranLines = it }
        val pre = "$n|"
        JSONArray(lines.filter { it.startsWith(pre) }.map { it.substring(it.indexOf('|', pre.length) + 1) }).toString()
    }

    // ── AI ────────────────────────────────────────────────────────────────
    @JavascriptInterface fun aiInfo(): String = Ai.info(c).toString()
    @JavascriptInterface fun ai(id: String, system: String, messagesJson: String) = async(id) { Ai.chat(c, system, JSONArray(messagesJson)) }
    @JavascriptInterface fun setAiKey(k: String) { Store.set(c, "ai.key", k.trim()) }

    // ── voice ─────────────────────────────────────────────────────────────
    @JavascriptInterface fun hasVoice(): Boolean =
        c.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    @JavascriptInterface fun listen() {
        act.runOnUiThread {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-AE")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلّم الآن…")
            try { act.startActivityForResult(i, MainActivity.REQ_VOICE) } catch (e: Exception) { js("window.onVoice&&onVoice(null)") }
        }
    }
    @JavascriptInterface fun speak(text: String) {
        act.runOnUiThread {
            if (tts == null) tts = TextToSpeech(c) { s -> ttsReady = s == TextToSpeech.SUCCESS; if (ttsReady) { tts?.language = Locale("ar"); tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "b") } }
            else if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "b")
        }
    }
    @JavascriptInterface fun stopSpeak() { tts?.stop() }

    // ── self-update from GitHub Releases ─────────────────────────────────
    @JavascriptInterface fun checkUpdate(id: String) = async(id) {
        val (code, txt) = Http.request("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", headers = mapOf("Accept" to "application/vnd.github+json"))
        if (code != 200) throw RuntimeException("GitHub HTTP $code")
        val o = JSONObject(txt); val tag = o.getString("tag_name").trimStart('v', 'V')
        var apk = ""
        val assets = o.getJSONArray("assets")
        for (i in 0 until assets.length()) { val a = assets.getJSONObject(i); if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break } }
        JSONObject().put("latest", tag).put("current", BuildConfig.VERSION_NAME)
            .put("newer", newer(tag, BuildConfig.VERSION_NAME) && apk.isNotEmpty()).put("apk", apk)
            .put("notes", o.optString("body")).toString()
    }
    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }
    @JavascriptInterface fun installUpdate(id: String, url: String) = async(id) {
        require(url.startsWith("https://github.com/${BuildConfig.UPDATE_REPO}/") || url.startsWith("https://objects.githubusercontent.com/")) { "رابط غير موثوق" }
        val dir = File(c.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "update.apk")
        java.net.URL(url).openStream().use { inp -> f.outputStream().use { inp.copyTo(it) } }
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            act.runOnUiThread { act.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return@async "needs-permission"
        }
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        act.runOnUiThread { act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
        "started"
    }

    fun shutdown() { tts?.shutdown(); pool.shutdownNow() }
}
