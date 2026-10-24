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
    companion object { const val IMG_BASE = "https://alcode.local/tile/" }
    @Volatile var lastBeat = System.currentTimeMillis()
    /** Watchdog heartbeat from the page (see MainActivity). */
    @JavascriptInterface fun beat() { lastBeat = System.currentTimeMillis() }
    @JavascriptInterface fun lowEnd(): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.isLowRamDevice || am.memoryClass <= 256 || Runtime.getRuntime().availableProcessors() <= 4
    }
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
                // 1.3.1: images go to disk once per app version and reach the page as small URLs
                // (1.3.0 sent multi-MB base64 PNGs for every app through evaluateJavascript on the UI thread → freeze).
                val ver = try { pm.getPackageInfo(pkg, 0).lastUpdateTime } catch (_: Exception) { 0L }
                val key = "$pkg-$ver"; val meta = File(tileDir(), "$key.json")
                val m = if (meta.exists()) JSONObject(meta.readText()) else {
                    val ic = hiResIcon(pm, ri) ?: ri.loadIcon(pm)
                    val banner = bannerFor(pm, ri, pkg)?.takeIf { goodBanner(it) }
                    val (c1, c2) = tint(ic)
                    val adaptive = if (banner == null) adaptiveArt(ic) else null
                    tileDir().listFiles { f -> f.name.startsWith("$pkg-") }?.forEach { it.delete() }
                    JSONObject().put("c1", c1).put("c2", c2)
                        .put("icon", save("$key-i.png", draw(ic, 192, 192), true))
                        .put("banner", banner?.let { save("$key-b.webp", draw(it, 640, 360), false) } ?: "")
                        .put("art", adaptive?.let { save("$key-a.webp", it, false) } ?: "")
                        .also { meta.writeText(it.toString()) }
                }
                out.put(JSONObject().put("pkg", pkg).put("label", ri.loadLabel(pm).toString())
                    .put("icon", m.optString("icon")).put("c1", m.optString("c1")).put("c2", m.optString("c2"))
                    .put("banner", m.optString("banner"))
                    .put("art", m.optString("art"))
                    .put("sys", (ri.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                    .put("tv", cat == Intent.CATEGORY_LEANBACK_LAUNCHER))
            }
        }
        add(Intent.CATEGORY_LEANBACK_LAUNCHER); add(Intent.CATEGORY_LAUNCHER)
        out.toString()
    }
    /** The app's own TV banner: activity → application → leanback launch activity. */
    private fun bannerFor(pm: PackageManager, ri: android.content.pm.ResolveInfo, pkg: String): Drawable? = try {
        ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            ?: pm.getApplicationBanner(pkg)
            ?: pm.getLeanbackLaunchIntentForPackage(pkg)?.let { pm.getActivityBanner(it) }
    } catch (_: Exception) { null }
    /** XXXHDPI icon straight from the app's resources, so nothing is upscaled. */
    private fun hiResIcon(pm: PackageManager, ri: android.content.pm.ResolveInfo): Drawable? = try {
        val ai = ri.activityInfo; val res = pm.getResourcesForApplication(ai.applicationInfo)
        val id = if (ai.icon != 0) ai.icon else ai.applicationInfo.icon
        if (id == 0) null else res.getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXXHIGH, null)
    } catch (_: Exception) { null }
    /** A banner counts only if it is real 16:9 artwork (>= 320x180) and not an icon pasted on flat grey. */
    private fun goodBanner(d: Drawable): Boolean = try {
        if (d.intrinsicWidth in 1..319 || d.intrinsicHeight in 1..179) false else {
            val b = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, 96, 54); d.draw(Canvas(b))
            var grey = 0; val hsv = FloatArray(3)
            for (y in 0 until 54) for (x in 0 until 96) { android.graphics.Color.colorToHSV(b.getPixel(x, y), hsv); if (hsv[1] < .08f && hsv[2] in 0.25f..0.85f) grey++ }
            grey < 96 * 54 * 0.6
        }
    } catch (_: Exception) { true }
    /** Adaptive icon → 16:9 tile at 960x540: solid brand background, foreground logo ~55% of the width, no box-in-a-box. */
    fun tileDir() = File(c.cacheDir, "tiles").apply { mkdirs() }
    private fun draw(d: Drawable, w: Int, h: Int): Bitmap { val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, w, h); d.draw(Canvas(b)); return b }
    @Suppress("DEPRECATION")
    private fun save(name: String, b: Bitmap, png: Boolean): String {
        File(tileDir(), name).outputStream().use { b.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.WEBP, if (png) 100 else 90, it) }
        b.recycle(); return "$IMG_BASE$name"
    }
    private fun adaptiveArt(d: Drawable): Bitmap? {
        if (Build.VERSION.SDK_INT < 26 || d !is android.graphics.drawable.AdaptiveIconDrawable) return null
        return try {
            val w = 640; val h = 360
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(b)
            d.background?.let { bg ->
                val sq = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888); bg.setBounds(0, 0, 216, 216); bg.draw(Canvas(sq))
                cv.drawBitmap(sq, null, android.graphics.Rect(0, 0, w, h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            // the logo lives in the central 72/108 of the layer; size the layer so that zone ≈ 55% of the tile width
            d.foreground?.let { fg -> val s = (w * 0.55 * 108 / 72).toInt(); val l = (w - s) / 2; val t = (h - s) / 2; fg.setBounds(l, t, l + s, t + s); fg.draw(cv) }
            b
        } catch (_: Exception) { null }
    }
    /** Official hi-res icon from the app's Google Play listing (og:image, requested at 1024px), cached on disk.
     *  Returns the raw icon as a data URL; the web layer composes the tvOS tile from it. */
    @JavascriptInterface fun playIcon(id: String, pkg: String) = async(id) {
        require(Regex("^[A-Za-z0-9_.]+$").matches(pkg))
        val dir = tileDir(); val f = File(dir, "play-$pkg.png")
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 30L * 86400000) {
            // TV builds often have their own id with no listing; try the phone listing of the same app too
            val ids = linkedSetOf(pkg, pkg.removeSuffix(".tv"), pkg.removeSuffix(".androidtv"), pkg.removeSuffix(".tv.android"), pkg.removeSuffix(".livingroom"))
            val html = ids.asSequence().map { Http.request("https://play.google.com/store/apps/details?id=$it&hl=en", headers = mapOf("Accept" to "text/html")) }
                .firstOrNull { it.first == 200 }?.second ?: throw RuntimeException("no listing")
            val m = Regex("<meta property=\"og:image\" content=\"(https://play-lh\\.googleusercontent\\.com/[^\"=]+)").find(html) ?: throw RuntimeException("no icon")
            val con = java.net.URL(m.groupValues[1] + "=s1024").openConnection() as java.net.HttpURLConnection
            val bytes = con.inputStream.use { it.readBytes() }; con.disconnect()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw RuntimeException("bad image")
            if (bmp.width < 512) throw RuntimeException("low-res")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        "$IMG_BASE${f.name}"
    }
    /** Dominant / vibrant colours of the icon (AndroidX Palette) → tvOS-style tile gradient. */
    private fun tint(d: Drawable): Pair<String, String> = try {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 96, 96); d.draw(Canvas(b))
        val p = androidx.palette.graphics.Palette.from(b).maximumColorCount(16).generate()
        val dom = p.getDominantColor(0xFF3A2E1C.toInt())
        val alt = p.getDarkVibrantColor(p.getDarkMutedColor(dom))
        Pair(String.format("#%06X", dom and 0xFFFFFF), String.format("#%06X", alt and 0xFFFFFF))
    } catch (_: Exception) { Pair("", "") }
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
    /** proactive prayer pause: sends a standard media PAUSE key to whatever holds media focus (no permission needed). */
    @JavascriptInterface fun mediaPause() {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        for (a in intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) am.dispatchMediaKeyEvent(android.view.KeyEvent(a, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
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

    // ── Liwaiptv / Xtream subscription (credentials stay native + encrypted) ──
    @JavascriptInterface fun iptvSet(server: String, user: String, pass: String) { Iptv.save(c, server, user, pass) }
    @JavascriptInterface fun iptvHas(): Boolean = Iptv.has(c)
    @JavascriptInterface fun iptvApi(id: String, action: String, extra: String) = async(id) { Iptv.api(c, action, extra) }
    @JavascriptInterface fun iptvPlay(kind: String, sid: String, ext: String, title: String): Boolean = Iptv.play(c, kind, sid, ext, title)
    @JavascriptInterface fun liwaInstalled(): Boolean = c.packageManager.getLaunchIntentForPackage(Iptv.LIWA_PKG) != null || c.packageManager.getLeanbackLaunchIntentForPackage(Iptv.LIWA_PKG) != null

    fun shutdown() { tts?.shutdown(); pool.shutdownNow() }
}
