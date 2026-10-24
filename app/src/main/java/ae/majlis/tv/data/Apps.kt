package ae.majlis.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import ae.majlis.tv.Http
import ae.majlis.tv.Store
import org.json.JSONObject
import java.io.File

/** Installed apps + tile art (ported from the 1.3.1 Bridge tile cache: files in cache/tiles, rendered once per app version). */
data class AppTile(
    val pkg: String, val label: String, val icon: File?, val banner: File?, val art: File?, val play: File?,
    val c1: Int, val c2: Int, val sys: Boolean, val tv: Boolean,
)

object Apps {
    private val SYS_SETTINGS = Regex("^com\\.(android|google\\.android)\\.(tv\\.)?settings$")
    fun tileDir(c: Context) = File(c.cacheDir, "tiles").apply { mkdirs() }

    fun load(c: Context): List<AppTile> {
        val pm = c.packageManager; val seen = HashSet<String>(); val out = ArrayList<AppTile>()
        fun add(cat: String) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == c.packageName || !seen.add(pkg)) continue
                if (SYS_SETTINGS.matches(pkg)) continue // settings live in the الإعدادات tab, never as a duplicate tile
                val ver = try { pm.getPackageInfo(pkg, 0).lastUpdateTime } catch (_: Exception) { 0L }
                val key = "$pkg-$ver"; val dir = tileDir(c); val meta = File(dir, "$key.n.json")
                val m = try { if (meta.exists()) JSONObject(meta.readText()) else null } catch (_: Exception) { null } ?: run {
                    val ic = hiResIcon(pm, ri) ?: ri.loadIcon(pm)
                    val banner = bannerFor(pm, ri, pkg)?.takeIf { goodBanner(it) }
                    val (c1, c2) = tint(ic)
                    val adaptive = if (banner == null) adaptiveArt(ic) else null
                    dir.listFiles { f -> f.name.startsWith("$pkg-") }?.forEach { it.delete() }
                    JSONObject().put("c1", c1).put("c2", c2)
                        .put("icon", save(dir, "$key-i.png", draw(ic, 192, 192), true))
                        .put("banner", banner?.let { save(dir, "$key-b.webp", draw(it, 640, 360), false) } ?: "")
                        .put("art", adaptive?.let { save(dir, "$key-a.webp", it, false) } ?: "")
                        .also { meta.writeText(it.toString()) }
                }
                fun f(k: String) = m.optString(k).takeIf { it.isNotEmpty() }?.let { File(dir, it) }?.takeIf { it.exists() }
                val play = File(dir, "play-$pkg.png").takeIf { it.exists() }
                out += AppTile(pkg, ri.loadLabel(pm).toString(), f("icon"), f("banner"), f("art"), play,
                    m.optInt("c1", 0xFF3A2E1C.toInt()), m.optInt("c2", 0xFF1A1917.toInt()),
                    (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0, cat == Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
        }
        add(Intent.CATEGORY_LEANBACK_LAUNCHER); add(Intent.CATEGORY_LAUNCHER)
        return out
    }

    /** Apps with neither a real banner nor adaptive art: try the official Play icon once (cached 30 days). */
    fun fetchPlayIcon(c: Context, pkg: String): File? {
        if (!Regex("^[A-Za-z0-9_.]+$").matches(pkg)) return null
        val f = File(tileDir(c), "play-$pkg.png")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 86400000) return f
        val miss = File(tileDir(c), "play-$pkg.miss")
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < 7L * 86400000) return null
        return try {
            val ids = linkedSetOf(pkg, pkg.removeSuffix(".tv"), pkg.removeSuffix(".androidtv"), pkg.removeSuffix(".tv.android"), pkg.removeSuffix(".livingroom"))
            val html = ids.asSequence().map { Http.request("https://play.google.com/store/apps/details?id=$it&hl=en", headers = mapOf("Accept" to "text/html")) }
                .firstOrNull { it.first == 200 }?.second ?: error("no listing")
            val mm = Regex("<meta property=\"og:image\" content=\"(https://play-lh\\.googleusercontent\\.com/[^\"=]+)").find(html) ?: error("no icon")
            val con = java.net.URL(mm.groupValues[1] + "=s512").openConnection() as java.net.HttpURLConnection
            val bytes = con.inputStream.use { it.readBytes() }; con.disconnect()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("bad image")
            if (bmp.width < 256) error("low-res")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; f
        } catch (_: Exception) { miss.writeText(""); null }
    }

    fun launch(c: Context, pkg: String): Boolean {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        Store.recordLaunch(c, pkg)
        return try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }
    fun appInfo(c: Context, pkg: String) = try {
        c.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    private fun bannerFor(pm: PackageManager, ri: android.content.pm.ResolveInfo, pkg: String): Drawable? = try {
        ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            ?: pm.getApplicationBanner(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg)?.let { pm.getActivityBanner(it) }
    } catch (_: Exception) { null }
    private fun hiResIcon(pm: PackageManager, ri: android.content.pm.ResolveInfo): Drawable? = try {
        val ai = ri.activityInfo; val res = pm.getResourcesForApplication(ai.applicationInfo)
        val id = if (ai.icon != 0) ai.icon else ai.applicationInfo.icon
        if (id == 0) null else res.getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXXHIGH, null)
    } catch (_: Exception) { null }
    /** Real 16:9 artwork (>= 320x180), not an icon pasted on flat grey. */
    private fun goodBanner(d: Drawable): Boolean = try {
        if (d.intrinsicWidth in 1..319 || d.intrinsicHeight in 1..179) false else {
            val b = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, 96, 54); d.draw(Canvas(b))
            var grey = 0; val hsv = FloatArray(3)
            for (y in 0 until 54) for (x in 0 until 96) { android.graphics.Color.colorToHSV(b.getPixel(x, y), hsv); if (hsv[1] < .08f && hsv[2] in 0.25f..0.85f) grey++ }
            b.recycle(); grey < 96 * 54 * 0.6
        }
    } catch (_: Exception) { true }
    private fun draw(d: Drawable, w: Int, h: Int): Bitmap { val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, w, h); d.draw(Canvas(b)); return b }
    @Suppress("DEPRECATION")
    private fun save(dir: File, name: String, b: Bitmap, png: Boolean): String {
        File(dir, name).outputStream().use { b.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.WEBP, if (png) 100 else 90, it) }
        b.recycle(); return name
    }
    private fun adaptiveArt(d: Drawable): Bitmap? {
        if (Build.VERSION.SDK_INT < 26 || d !is AdaptiveIconDrawable) return null
        return try {
            val w = 640; val h = 360
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(b)
            d.background?.let { bg ->
                val sq = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888); bg.setBounds(0, 0, 216, 216); bg.draw(Canvas(sq))
                cv.drawBitmap(sq, null, android.graphics.Rect(0, 0, w, h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            d.foreground?.let { fg -> val s = (w * 0.55 * 108 / 72).toInt(); val l = (w - s) / 2; val t = (h - s) / 2; fg.setBounds(l, t, l + s, t + s); fg.draw(cv) }
            b
        } catch (_: Exception) { null }
    }
    private fun tint(d: Drawable): Pair<Int, Int> = try {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, 96, 96); d.draw(Canvas(b))
        val p = androidx.palette.graphics.Palette.from(b).maximumColorCount(16).generate()
        val dom = p.getDominantColor(0xFF3A2E1C.toInt()); val alt = p.getDarkVibrantColor(p.getDarkMutedColor(dom))
        Pair(dom or 0xFF000000.toInt(), alt or 0xFF000000.toInt())
    } catch (_: Exception) { Pair(0xFF3A2E1C.toInt(), 0xFF1A1917.toInt()) }
}
