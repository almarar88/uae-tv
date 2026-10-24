package ae.majlis.tv.data

import android.content.Context
import android.util.Xml
import ae.majlis.tv.BuildConfig
import ae.majlis.tv.Http
import ae.majlis.tv.Store
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

private fun cacheFile(c: Context, name: String) = File(c.filesDir, "cache-$name.json")
private fun httpsGet(url: String): String { val (code, t) = Http.request(url); if (code !in 200..299) throw RuntimeException("HTTP $code"); return t }

// ───────────────────────── News (verified feeds) ─────────────────────────
data class NewsItem(val title: String, val link: String, val t: Long, val src: String, val color: Long, val en: Boolean, val desc: String, val img: String, val cat: String)

object News {
    private data class Feed(val src: String, val url: String, val color: Long, val cat: String? = null, val en: Boolean = false)
    private val FEEDS = listOf(
        Feed("الإمارات اليوم", "https://www.emaratalyoum.com/1.533091?ot=ot.AjaxPageLayout", 0xFF9B1C24, "محلي"),
        Feed("سكاي نيوز عربية (أبوظبي)", "https://www.skynewsarabia.com/rss.xml", 0xFF0B4A8B),
        Feed("The National", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml", 0xFF1D1D1D, en = true),
    )
    val CATS = listOf("الكل", "محلي", "اقتصاد", "رياضة", "طقس", "حكومة", "عالمي")
    fun guessCat(t: String): String = when {
        Regex("مباراة|دوري|كأس|منتخب|لاعب|نادي|football|league|match", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "رياضة"
        Regex("طقس|أمطار|حرارة|غبار|ضباب|المركز الوطني للأرصاد|weather|rain|fog", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "طقس"
        Regex("اقتصاد|أسهم|نفط|استثمار|درهم|سوق|بنك|economy|market|oil|stocks", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "اقتصاد"
        Regex("رئيس الدولة|ولي عهد|مجلس الوزراء|وزارة|حكومة|مرسوم|government|ministry|sheikh", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "حكومة"
        Regex("الإمارات|أبوظبي|دبي|الشارقة|عجمان|الفجيرة|رأس الخيمة|العين|UAE|Dubai|Abu Dhabi", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "محلي"
        else -> "عالمي"
    }
    /** publisher thumbnail → large rendition (same rules as 1.3.1 news.js). */
    fun bigImg(u: String): String = when {
        u.isEmpty() -> ""
        u.contains("emaratalyoum.com") -> u.replace(Regex("derivatives/[a-z]+_\\d+/"), "derivatives/rectangular_960/")
        u.contains("images.skynewsarabia.com") -> u.replace(Regex("/\\d+/\\d+/([^/]+)$"), "/1000/563/$1")
        u.contains("thenationalnews.com/resizer") -> u.replace(Regex("([?&])width=\\d+"), "$1width=1280").replace(Regex("([?&])height=\\d+"), "$1height=720")
        else -> u
    }
    private val dateFmts = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z")
    private fun parseDate(s: String): Long? { for (f in dateFmts) try { return SimpleDateFormat(f, Locale.US).parse(s.trim())?.time } catch (_: Exception) {}; return null }

    private fun parse(xml: String, f: Feed): List<NewsItem> {
        val out = ArrayList<NewsItem>(); val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); p.setInput(StringReader(xml))
        var inItem = false; var tag = ""; val sb = HashMap<String, StringBuilder>(); var img = ""
        while (true) {
            val ev = try { p.next() } catch (_: Exception) { break }
            if (ev == XmlPullParser.END_DOCUMENT) break
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; sb.clear(); img = "" }
                    else if (inItem && img.isEmpty() && (tag == "enclosure" || tag == "media:content" || tag == "media:thumbnail")) p.getAttributeValue(null, "url")?.let { img = it }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) sb.getOrPut(tag) { StringBuilder() }.append(p.text)
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" && inItem) {
                        inItem = false
                        val title = sb["title"]?.toString()?.trim().orEmpty()
                        if (title.isNotEmpty() && out.size < 25) {
                            val desc = (sb["description"]?.toString() ?: "").replace(Regex("<[^>]+>"), " ").replace(Regex("&nbsp;|&#160;"), " ").replace(Regex("\\s+"), " ").trim().take(600)
                            out += NewsItem(title, sb["link"]?.toString()?.trim().orEmpty(), parseDate(sb["pubDate"]?.toString() ?: "") ?: System.currentTimeMillis(),
                                f.src, f.color, f.en, desc, img, f.cat ?: guessCat("$title $desc"))
                        }
                    }
                    tag = ""
                }
            }
        }
        return out
    }
    fun load(c: Context): List<NewsItem> {
        val all = java.util.concurrent.ConcurrentLinkedQueue<NewsItem>()
        FEEDS.map { f -> Thread { try { all += parse(httpsGet(f.url), f) } catch (_: Exception) {} }.apply { start() } }.forEach { it.join(30000) }
        val list = all.distinctBy { it.title }.sortedByDescending { it.t }.take(60)
        val cf = cacheFile(c, "news")
        if (list.isNotEmpty()) { cf.writeText(JSONArray(list.map { JSONObject().put("title", it.title).put("link", it.link).put("t", it.t).put("src", it.src).put("c", it.color).put("en", it.en).put("desc", it.desc).put("img", it.img).put("cat", it.cat) }).toString()); return list }
        return try { val a = JSONArray(cf.readText()); (0 until a.length()).map { a.getJSONObject(it).let { o -> NewsItem(o.getString("title"), o.optString("link"), o.getLong("t"), o.getString("src"), o.getLong("c"), o.optBoolean("en"), o.optString("desc"), o.optString("img"), o.optString("cat")) } } } catch (_: Exception) { emptyList() }
    }
}

// ───────────────────────── Weather (Open-Meteo, CC BY 4.0) ─────────────────────────
data class WxDay(val date: String, val code: Int, val max: Double, val min: Double, val uv: Double, val rain: Double, val gust: Double)
data class Weather(val city: String, val temp: Double, val feels: Double, val hum: Int, val code: Int, val wind: Double, val days: List<WxDay>, val at: Long)

object WeatherRepo {
    private val WMO = mapOf(0 to ("صحو" to "☀"), 1 to ("صحو غالباً" to "🌤"), 2 to ("غائم جزئياً" to "⛅"), 3 to ("غائم" to "☁"), 45 to ("ضباب" to "🌫"), 48 to ("ضباب متجمد" to "🌫"),
        51 to ("رذاذ خفيف" to "🌦"), 53 to ("رذاذ" to "🌦"), 55 to ("رذاذ كثيف" to "🌧"), 61 to ("مطر خفيف" to "🌦"), 63 to ("مطر" to "🌧"), 65 to ("مطر غزير" to "🌧"),
        80 to ("زخات مطر" to "🌦"), 81 to ("زخات مطر" to "🌧"), 82 to ("زخات غزيرة" to "⛈"), 95 to ("عاصفة رعدية" to "⛈"), 96 to ("عاصفة رعدية مع برد" to "⛈"), 99 to ("عاصفة رعدية مع برد" to "⛈"))
    fun wmo(c: Int) = WMO[c] ?: ("—" to "•")
    fun load(c: Context, cityId: String): Weather? {
        val city = ae.majlis.tv.Cities.byId(cityId); val cf = cacheFile(c, "wx")
        val txt = try {
            httpsGet("https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lng}&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,wind_gusts_10m_max,uv_index_max,precipitation_sum&timezone=Asia%2FDubai&forecast_days=7")
                .also { cf.writeText(JSONObject().put("city", city.id).put("at", System.currentTimeMillis()).put("wx", it).toString()) }
        } catch (_: Exception) { try { JSONObject(cf.readText()).takeIf { it.getString("city") == city.id }?.getString("wx") } catch (_: Exception) { null } } ?: return null
        return try {
            val o = JSONObject(txt); val cu = o.getJSONObject("current"); val d = o.getJSONObject("daily")
            val time = d.getJSONArray("time")
            Weather(city.ar, cu.getDouble("temperature_2m"), cu.getDouble("apparent_temperature"), cu.getInt("relative_humidity_2m"), cu.getInt("weather_code"), cu.getDouble("wind_speed_10m"),
                (0 until time.length()).map { i -> WxDay(time.getString(i), d.getJSONArray("weather_code").optInt(i), d.getJSONArray("temperature_2m_max").optDouble(i), d.getJSONArray("temperature_2m_min").optDouble(i),
                    d.getJSONArray("uv_index_max").optDouble(i), d.getJSONArray("precipitation_sum").optDouble(i), d.getJSONArray("wind_gusts_10m_max").optDouble(i)) }, System.currentTimeMillis())
        } catch (_: Exception) { null }
    }
}

// ───────────────────────── Football (TheSportsDB free API, UAE Pro League) ─────────────────────────
data class Match(val home: String, val away: String, val hs: String?, val aws: String?, val date: String, val t: Long?, val venue: String)
data class TableRow(val rank: String, val team: String, val played: String, val gd: String, val pts: String)
data class Football(val season: String, val table: List<TableRow>, val today: List<Match>, val next: List<Match>, val past: List<Match>, val updated: String)

object FootballRepo {
    private const val LEAGUE = "4678"; private const val B = "https://www.thesportsdb.com/api/v1/json/123"
    private val TEAM_AR = mapOf("Al Ain" to "العين", "Al Jazira" to "الجزيرة", "Al Wahda" to "الوحدة", "Al Wasl" to "الوصل", "Shabab Al-Ahli Dubai" to "شباب الأهلي", "Shabab Al-Ahli" to "شباب الأهلي",
        "Sharjah" to "الشارقة", "Al Sharjah" to "الشارقة", "Al Nasr" to "النصر", "Al-Nasr" to "النصر", "Ajman" to "عجمان", "Baniyas" to "بني ياس", "Bani Yas" to "بني ياس", "Khor Fakkan" to "خورفكان",
        "Kalba" to "كلباء", "Ittihad Kalba" to "اتحاد كلباء", "Al Bataeh" to "البطائح", "Al Dhafra" to "الظفرة", "Dibba Al Fujairah" to "دبا الفجيرة", "Dibba" to "دبا", "Al Urooba" to "العروبة",
        "Hatta" to "حتا", "Emirates Club" to "الإمارات", "Al Orooba" to "العروبة")
    fun tn(n: String) = TEAM_AR[n] ?: n
    private fun j(url: String): JSONObject? = try { JSONObject(httpsGet(url)) } catch (_: Exception) { null }
    private fun ev(a: JSONArray?): List<Match> = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("idLeague") == LEAGUE }.map { e ->
        val ts = e.optString("strTimestamp").takeIf { it.isNotEmpty() && it != "null" }?.let { s -> try { java.time.Instant.parse(s.replace(' ', 'T').let { if (it.endsWith("Z") || it.contains('+')) it.replace("+00:00", "Z") else it + "Z" }).toEpochMilli() } catch (_: Exception) { null } }
        fun s(k: String) = e.optString(k).takeIf { it.isNotEmpty() && it != "null" }
        Match(tn(e.optString("strHomeTeam")), tn(e.optString("strAwayTeam")), s("intHomeScore"), s("intAwayScore"), e.optString("dateEvent"), ts, s("strVenue") ?: "")
    }
    fun load(): Football? {
        val season = j("$B/lookupleague.php?id=$LEAGUE")?.optJSONArray("leagues")?.optJSONObject(0)?.optString("strCurrentSeason") ?: ""
        val p = Fmt.parts(); val today = "%d-%02d-%02d".format(p.y, p.m, p.d)
        val tb = if (season.isNotEmpty()) j("$B/lookuptable.php?l=$LEAGUE&s=$season")?.optJSONArray("table") else null
        val table = if (tb == null) emptyList() else (0 until tb.length()).map { tb.getJSONObject(it) }.map { TableRow(it.optString("intRank"), tn(it.optString("strTeam")), it.optString("intPlayed"), it.optString("intGoalDifference"), it.optString("intPoints")) }
        val f = Football(season, table, ev(j("$B/eventsday.php?d=$today&l=$LEAGUE")?.optJSONArray("events")), ev(j("$B/eventsnextleague.php?id=$LEAGUE")?.optJSONArray("events")),
            ev(j("$B/eventspastleague.php?id=$LEAGUE")?.optJSONArray("events")), tb?.optJSONObject(0)?.optString("dateUpdated") ?: "")
        return if (f.table.isEmpty() && f.today.isEmpty() && f.next.isEmpty() && f.past.isEmpty()) null else f
    }
}

// ───────────────────────── Quran (bundled Tanzil Uthmani text) ─────────────────────────
data class Sura(val n: Int, val ayas: Int, val name: String, val type: String)
data class Tafsir(val name: String, val text: String, val url: String)

object Quran {
    @Volatile private var lines: List<String>? = null
    fun suras(c: Context): List<Sura> { val a = JSONArray(c.assets.open("data/suras.json").bufferedReader().use { it.readText() }); return (0 until a.length()).map { a.getJSONObject(it).let { o -> Sura(o.getInt("n"), o.getInt("ayas"), o.getString("name"), o.getString("type")) } } }
    /** Verses of one sura, verbatim from the bundled Tanzil text (never generated). */
    fun verses(c: Context, n: Int): List<String> {
        val l = lines ?: c.assets.open("quran-uthmani.txt").bufferedReader(Charsets.UTF_8).use { r -> r.readLines().filter { it.isNotBlank() && !it.startsWith("#") } }.also { lines = it }
        val pre = "$n|"; return l.filter { it.startsWith(pre) }.map { it.substring(it.indexOf('|', pre.length) + 1) }
    }
    fun tafsir(sura: Int, aya: Int): List<Tafsir> = listOf("ar.muyassar" to "التفسير الميسر — مجمع الملك فهد لطباعة المصحف الشريف", "ar.jalalayn" to "تفسير الجلالين").mapNotNull { (ed, name) ->
        val url = "https://api.alquran.cloud/v1/ayah/$sura:$aya/$ed"
        try { JSONObject(httpsGet(url)).optJSONObject("data")?.optString("text")?.takeIf { it.isNotBlank() }?.let { Tafsir(name, it, url) } } catch (_: Exception) { null }
    }
}

// ───────────────────────── Poetry (sourced, verbatim; remote refresh ≤ 1/day) ─────────────────────────
data class Poem(val title: String, val source: String, val url: String, val verses: List<List<String>>, val wisdom: List<List<Int>>)
data class Poet(val id: String, val name: String, val short: String, val bio: String, val bioSource: String, val bioUrl: String, val poems: List<Poem>)
data class VerseOfDay(val poet: Int, val poem: Int, val lines: List<List<String>>)

object PoetryRepo {
    const val REMOTE = "https://raw.githubusercontent.com/almarar88/uae-tv/main/app/src/main/assets/data/poetry.json"
    private fun ok(s: Any?) = s is String && s.isNotBlank() && s.length < 300
    fun valid(d: JSONObject?): Boolean = try {
        val ps = d!!.getJSONArray("poets"); ps.length() in 1..99 && (0 until ps.length()).all { i ->
            val p = ps.getJSONObject(i); val qs = p.getJSONArray("poems")
            ok(p.opt("name")) && ok(p.opt("short")) && qs.length() > 0 && (0 until qs.length()).all { k ->
                val q = qs.getJSONObject(k); val vs = q.getJSONArray("verses")
                ok(q.opt("title")) && ok(q.opt("source")) && vs.length() > 0 && (0 until vs.length()).all { v -> val a = vs.getJSONArray(v); (a.length() == 1 || a.length() == 2) && (0 until a.length()).all { ok(a.opt(it)) } } &&
                    (q.optJSONArray("wisdom")?.let { w -> (0 until w.length()).all { x -> val a = w.getJSONArray(x); (0 until a.length()).all { a.optInt(it, -1) in 0 until vs.length() } } } ?: true)
            }
        }
    } catch (_: Exception) { false }
    private fun newer(a: JSONObject, b: JSONObject?) = b == null || a.optString("built") > b.optString("built") || (a.optString("built") == b.optString("built") && a.optInt("version") > b.optInt("version"))
    fun load(c: Context): List<Poet> {
        val bundled = try { JSONObject(c.assets.open("data/poetry.json").bufferedReader().use { it.readText() }) } catch (_: Exception) { null }
        val cf = File(c.filesDir, "poetry.remote.json")
        val cached = try { JSONObject(cf.readText()).takeIf { valid(it) } } catch (_: Exception) { null }
        val d = if (cached != null && (!valid(bundled) || newer(cached, bundled))) cached else bundled
        return if (valid(d)) parse(d!!) else emptyList()
    }
    /** At most one attempt per day; validated before it is cached. */
    fun refresh(c: Context) {
        val last = Store.get(c, "poetry.ts", "0").toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < 86400000L) return
        Store.set(c, "poetry.ts", System.currentTimeMillis().toString())
        try { val t = httpsGet(REMOTE); val d = JSONObject(t); if (valid(d)) File(c.filesDir, "poetry.remote.json").writeText(t) } catch (_: Exception) {}
    }
    private fun parse(d: JSONObject): List<Poet> { val ps = d.getJSONArray("poets"); return (0 until ps.length()).map { i -> val p = ps.getJSONObject(i); val qs = p.getJSONArray("poems")
        Poet(p.optString("id"), p.getString("name"), p.getString("short"), p.optString("bio"), p.optString("bioSource"), p.optString("bioUrl"), (0 until qs.length()).map { k -> val q = qs.getJSONObject(k); val vs = q.getJSONArray("verses")
            Poem(q.getString("title"), q.getString("source"), q.optString("url"), (0 until vs.length()).map { v -> val a = vs.getJSONArray(v); (0 until a.length()).map { a.getString(it) } },
                q.optJSONArray("wisdom")?.let { w -> (0 until w.length()).map { x -> val a = w.getJSONArray(x); (0 until a.length()).map { a.getInt(it) } } } ?: emptyList()) }) } }
    /** Deterministic per UAE calendar day, cycling through the sourced wisdom picks. */
    fun verseOfDay(p: List<Poet>): VerseOfDay? {
        val picks = ArrayList<Triple<Int, Int, List<Int>>>()
        p.forEachIndexed { pi, poet -> poet.poems.forEachIndexed { qi, q -> q.wisdom.forEach { picks += Triple(pi, qi, it) } } }
        if (picks.isEmpty()) return null
        val day = ((System.currentTimeMillis() + 4 * 3600_000L) / 86400_000L).toInt()
        val k = picks[(day * 7) % picks.size]
        return VerseOfDay(k.first, k.second, k.third.take(1).map { p[k.first].poems[k.second].verses[it] })
    }
    fun host(u: String) = try { java.net.URI(u).host.removePrefix("www.") } catch (_: Exception) { "" }
}

// ───────────────────────── Photos (Wikimedia Commons, credited) ─────────────────────────
data class Photo(val file: String, val ar: String, val artist: String, val license: String, val page: String) { val uri get() = "file:///android_asset/bg/$file" }
object Photos {
    fun all(c: Context): List<Photo> { val a = JSONArray(c.assets.open("data/photos.json").bufferedReader().use { it.readText() }); return (0 until a.length()).map { a.getJSONObject(it).let { o -> Photo(o.getString("file"), o.getString("ar"), o.optString("artist"), o.optString("license"), o.optString("page")) } } }
    fun today(c: Context, all: List<Photo>): Photo { val pref = Store.get(c, "bg", "auto"); return all.firstOrNull { "bg/" + it.file == pref || it.file == pref } ?: all[(((System.currentTimeMillis() + 4 * 3600_000L) / 86400_000L) % all.size).toInt()] }
}

// ───────────────────────── Self-update (GitHub Releases) ─────────────────────────
data class UpdateInfo(val latest: String, val newer: Boolean, val apk: String, val notes: String)
object Updater {
    fun check(): UpdateInfo {
        val (code, txt) = Http.request("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", headers = mapOf("Accept" to "application/vnd.github+json"))
        if (code != 200) throw RuntimeException("GitHub HTTP $code")
        val o = JSONObject(txt); val tag = o.getString("tag_name").trimStart('v', 'V'); val assets = o.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url") ?: ""
        return UpdateInfo(tag, newer(tag, BuildConfig.VERSION_NAME) && apk.isNotEmpty(), apk, o.optString("body"))
    }
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }
    /** Downloads the APK (trusted hosts only) and hands it to the system installer. Returns a status word. */
    fun install(c: Context, url: String): String {
        require(url.startsWith("https://github.com/${BuildConfig.UPDATE_REPO}/") || url.startsWith("https://objects.githubusercontent.com/")) { "رابط غير موثوق" }
        val f = File(File(c.cacheDir, "updates").apply { mkdirs() }, "update.apk")
        java.net.URL(url).openStream().use { inp -> f.outputStream().use { inp.copyTo(it) } }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            c.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + c.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return "needs-permission"
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".files", f)
        c.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return "started"
    }
}

// ───────────────────────── Liwaiptv / Xtream catalogue ─────────────────────────
data class IpCat(val id: String, val name: String)
data class IpItem(val kind: String, val id: String, val name: String, val img: String, val ext: String, val plot: String = "")
object IptvRepo {
    private fun arr(c: Context, action: String, extra: String = ""): JSONArray = try { JSONArray(ae.majlis.tv.Iptv.api(c, action, extra)) } catch (_: Exception) { JSONArray() }
    fun cats(c: Context, kind: String): List<IpCat> { val a = arr(c, when (kind) { "live" -> "get_live_categories"; "movie" -> "get_vod_categories"; else -> "get_series_categories" })
        return (0 until a.length()).map { a.getJSONObject(it) }.map { IpCat(it.optString("category_id"), it.optString("category_name")) }.filter { it.id.matches(Regex("[0-9]+")) } }
    fun items(c: Context, kind: String, cat: String): List<IpItem> {
        require(cat.matches(Regex("[0-9]+")))
        val a = arr(c, when (kind) { "live" -> "get_live_streams"; "movie" -> "get_vod_streams"; else -> "get_series" }, "category_id=$cat")
        return (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o -> when (kind) {
            "series" -> IpItem("series", o.optString("series_id"), o.optString("name"), o.optString("cover"), "", o.optString("plot"))
            "movie" -> IpItem("movie", o.optString("stream_id"), o.optString("name"), o.optString("stream_icon"), o.optString("container_extension"))
            else -> IpItem("live", o.optString("stream_id"), o.optString("name"), o.optString("stream_icon"), "ts")
        }.takeIf { it.id.matches(Regex("[0-9]+")) } }
    }
    fun episodes(c: Context, seriesId: String): List<IpItem> {
        require(seriesId.matches(Regex("[0-9]+")))
        val o = try { JSONObject(ae.majlis.tv.Iptv.api(c, "get_series_info", "series_id=$seriesId")) } catch (_: Exception) { return emptyList() }
        val eps = o.optJSONObject("episodes") ?: return emptyList(); val out = ArrayList<IpItem>()
        eps.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }.forEach { s -> val a = eps.getJSONArray(s); for (i in 0 until a.length()) { val e = a.getJSONObject(i)
            out += IpItem("series", e.optString("id"), "م$s · ح${e.optString("episode_num")} — ${e.optString("title")}", e.optJSONObject("info")?.optString("movie_image") ?: "", e.optString("container_extension")) } }
        return out.filter { it.id.matches(Regex("[0-9]+")) }
    }
    fun recent(c: Context): List<IpItem> = try { val a = JSONArray(Store.get(c, "iptv.recent", "[]")); (0 until a.length()).map { a.getJSONObject(it).let { o -> IpItem(o.getString("k"), o.getString("i"), o.getString("n"), o.optString("img"), o.optString("e")) } } } catch (_: Exception) { emptyList() }
    fun play(c: Context, it: IpItem): Boolean {
        val ok = ae.majlis.tv.Iptv.play(c, it.kind, it.id, it.ext.lowercase().take(5).filter { ch -> ch.isLetterOrDigit() }, it.name)
        if (ok) { val l = (listOf(it) + recent(c).filterNot { r -> r.kind == it.kind && r.id == it.id }).take(12)
            Store.set(c, "iptv.recent", JSONArray(l.map { r -> JSONObject().put("k", r.kind).put("i", r.id).put("n", r.name).put("img", r.img).put("e", r.ext) }).toString()) }
        return ok
    }
}
