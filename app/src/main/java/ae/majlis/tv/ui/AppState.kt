package ae.majlis.tv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ae.majlis.tv.Adhan
import ae.majlis.tv.Ai
import ae.majlis.tv.Cities
import ae.majlis.tv.Iptv
import ae.majlis.tv.PrayerCalc
import ae.majlis.tv.Store
import ae.majlis.tv.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Overlay routes (shown as full-screen focus-trapping dialogs above the tabs, so Back returns focus to the opener). */
sealed interface Route {
    data class Reader(val sura: Int) : Route
    data class TafsirR(val sura: Int, val aya: Int) : Route
    data class PoemR(val poet: Int, val poem: Int) : Route
    data class NewsR(val item: NewsItem) : Route
    data class IptvR(val kind: String) : Route
    data class SeriesR(val item: IpItem) : Route
    data object Credits : Route
}

data class Pick(val key: String, val title: String, val sub: String, val glyph: String, val act: () -> Unit)
data class Msg(val user: Boolean, val text: String, val ai: Boolean = false, val err: Boolean = false)

class AppState(app: Application) : AndroidViewModel(app) {
    val c = app.applicationContext
    val tabs = listOf("الرئيسية", "الأخبار", "الصلاة", "القرآن", "الشعر", "الطقس", "الدوري", "اسأل", "الإعدادات")
    var tab by mutableIntStateOf(0)
    val routes = mutableStateListOf<Route>()
    var prefsRev by mutableIntStateOf(0)
    var now by mutableLongStateOf(System.currentTimeMillis())

    var apps by mutableStateOf<List<AppTile>>(emptyList())
    var favs by mutableStateOf<List<String>>(emptyList())
    var moving by mutableStateOf<String?>(null)
    var news by mutableStateOf<List<NewsItem>?>(null)
    var newsCat by mutableStateOf("الكل")
    var weather by mutableStateOf<Weather?>(null)
    var weatherLoaded by mutableStateOf(false)
    var football by mutableStateOf<Football?>(null)
    var footballLoaded by mutableStateOf(false)
    var poets by mutableStateOf<List<Poet>>(emptyList())
    val photos by lazy { Photos.all(c) }
    val suras by lazy { Quran.suras(c) }
    var iptv by mutableStateOf(Iptv.has(c))
    var recent by mutableStateOf(IptvRepo.recent(c))
    val chat = mutableStateListOf<Msg>()
    var asking by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)

    fun pref(k: String, d: String = "") = Store.get(c, k, d)
    fun setPref(k: String, v: String) { Store.set(c, k, v); if (k == "city" || k == "adhan") Adhan.scheduleNext(c); if (k == "city") loadWeather(true); prefsRev++ }
    fun city() = Cities.byId(pref("city", "abudhabi"))
    fun say(t: String) { toast = t; viewModelScope.launch { delay(3500); if (toast == t) toast = null } }
    fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { try { block() } catch (_: Throwable) {} }

    init {
        viewModelScope.launch { while (isActive) { now = System.currentTimeMillis(); delay(60_000L - now % 60_000L + 50) } }
        reloadApps(); loadWeather(); loadFootball()
        io { val p = PoetryRepo.load(c); withContext(Dispatchers.Main) { poets = p }; PoetryRepo.refresh(c) }
    }

    // ── apps & favourites ──
    fun reloadApps() = io {
        val list = Apps.load(c).sortedBy { it.label.lowercase() }
        withContext(Dispatchers.Main) { apps = list; favs = loadFavs(list) }
        // apps with no banner and no adaptive art: fetch the official Play icon in the background
        list.filter { it.banner == null && it.art == null && it.play == null }.forEach { a ->
            Apps.fetchPlayIcon(c, a.pkg)?.let { f -> withContext(Dispatchers.Main) { apps = apps.map { if (it.pkg == a.pkg) it.copy(play = f) else it } } }
        }
    }
    private fun loadFavs(list: List<AppTile>): List<String> {
        val have = list.map { it.pkg }.toSet()
        val saved = pref("fav", "").takeIf { it.isNotEmpty() }?.split(',')?.filter { it in have }
        if (saved != null) return saved
        val u = Store.usage(c)
        val byUse = list.filter { u.optJSONObject(it.pkg)?.optInt("total", 0) ?: 0 > 0 }.sortedByDescending { u.optJSONObject(it.pkg)!!.optInt("total") }
        return (byUse + list.filter { it.tv && !it.sys } + list.filter { it.tv }).map { it.pkg }.distinct().take(6)
    }
    private fun saveFavs(l: List<String>) { favs = l; Store.set(c, "fav", l.joinToString(",")) }
    fun toggleFav(pkg: String) = saveFavs(if (pkg in favs) favs - pkg else favs + pkg)
    fun moveFav(pkg: String, by: Int) { val i = favs.indexOf(pkg); val j = i + by; if (i < 0 || j !in favs.indices) return; val l = favs.toMutableList(); l.removeAt(i); l.add(j, pkg); saveFavs(l) }
    fun launch(pkg: String) { if (!Apps.launch(c, pkg)) say("تعذّر فتح التطبيق") }

    // ── picks «مختاراتي»: real, local signals only (no generated text) ──
    fun picks(): List<Pick> {
        val out = ArrayList<Pick>()
        nextPrayer()?.let { (i, t) -> if (t - now < 25 * 60_000L) out += Pick("prayer", "${Fmt.PNAMES[i]} ${Fmt.until(t - now)}", "الأذان قريب", "۞") { tab = 2 } }
        football?.today?.take(2)?.forEachIndexed { k, m -> out += Pick("match$k", "${m.home} × ${m.away}", "مباراة اليوم · الدوري", "⚽") { tab = 6 } }
        if (Ai.key(c).isNotBlank()) out += Pick("brief", "موجز اليوم", "الطقس والصلاة والدوري", "✦") { tab = 7; ask("أعطني موجز اليوم") }
        val slot = Fmt.parts(now).h / 3; val u = Store.usage(c)
        apps.filter { it.pkg !in favs }.map { it to (u.optJSONObject(it.pkg)?.optInt(slot.toString(), 0) ?: 0) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.take(3).forEach { (a, _) -> out += Pick("app:" + a.pkg, a.label, "تفتحه عادةً في هذا الوقت", "▶") { launch(a.pkg) } }
        return out
    }

    // ── prayer ──
    fun prayerDay(t: Long = now): LongArray { val p = Fmt.parts(t); return PrayerCalc.compute(city(), p.y, p.m, p.d) }
    fun nextPrayer(): Pair<Int, Long>? = try {
        val d = prayerDay(); (0 until 6).firstOrNull { it != 1 && d[it] > now }?.let { it to d[it] } ?: (0 to prayerDay(now + 86_400_000L)[0])
    } catch (_: Exception) { null }

    // ── data loads (each cached on disk inside its repo) ──
    fun loadNews(force: Boolean = false) { if (news != null && !force) return; io { val n = News.load(c); withContext(Dispatchers.Main) { news = n } } }
    fun loadWeather(force: Boolean = false) { if (weatherLoaded && !force) return; io { val w = WeatherRepo.load(c, city().id); withContext(Dispatchers.Main) { weather = w; weatherLoaded = true } } }
    fun loadFootball(force: Boolean = false) { if (footballLoaded && !force) return; io { val f = FootballRepo.load(); withContext(Dispatchers.Main) { football = f; footballLoaded = true } } }

    // ── IPTV ──
    fun play(it: IpItem) = io { val ok = IptvRepo.play(c, it); withContext(Dispatchers.Main) { if (ok) recent = IptvRepo.recent(c) else say("تعذّر التشغيل") } }
    fun liwaInstalled() = c.packageManager.getLaunchIntentForPackage(Iptv.LIWA_PKG) != null || c.packageManager.getLeanbackLaunchIntentForPackage(Iptv.LIWA_PKG) != null
    fun saveIptv(s: String, u: String, p: String) { Iptv.save(c, s, u, p); iptv = Iptv.has(c); prefsRev++ }

    // ── assistant ──
    fun contextBlock(): String {
        val np = nextPrayer(); val d = prayerDay()
        val pt = (0 until 6).joinToString("، ") { Fmt.PNAMES[it] + " " + Fmt.time(c, d[it]) }
        val wx = weather?.let { "${it.city}: ${Math.round(it.temp)}° ${WeatherRepo.wmo(it.code).first}، رطوبة ${it.hum}%" } ?: "غير متاح"
        val f = football; val ft = if (f == null) "لا توجد بيانات كرة قدم متاحة" else
            "مباريات اليوم: ${f.today.joinToString("، ") { it.home + " × " + it.away }.ifEmpty { "لا يوجد" }}؛ القادمة: ${f.next.joinToString("، ") { it.home + " × " + it.away + " " + it.date }.ifEmpty { "—" }}؛ المتصدرون: ${f.table.take(5).joinToString("، ") { it.rank + ". " + it.team + " " + it.pts }}"
        return "الوقت الآن ${Fmt.time(c, now)}، ${Fmt.gregLine(now)} الموافق ${Fmt.hijriLine(now)}. المدينة: ${city().ar}.\nمواقيت الصلاة اليوم: $pt.${np?.let { " الصلاة القادمة: ${Fmt.PNAMES[it.first]} ${Fmt.time(c, it.second)}." } ?: ""}\nالطقس: $wx.\nكرة القدم (دوري المحترفين الإماراتي): $ft."
    }
    private val SECTIONS = listOf("صلا|أذان|اذان" to 2, "طقس|جو" to 5, "قرآن|قران|سورة" to 3, "شعر|قصيد|قصائد" to 4, "أخبار|اخبار|عاجل" to 1, "دوري|مباراة|مباريات" to 6, "إعداد|اعداد" to 8, "الرئيسية" to 0)
    /** Offline / no-key fallback: rule-based Arabic intents. */
    private fun localIntent(q: String): Boolean {
        val t = q.trim(); val low = t.lowercase()
        val app = apps.firstOrNull { low.contains(it.label.lowercase()) && it.label.length > 2 }
        if (Regex("افتح|شغل|شغّل|open").containsMatchIn(t) && app != null) { chat += Msg(false, "جارٍ فتح ${app.label}"); launch(app.pkg); return true }
        SECTIONS.firstOrNull { Regex(it.first).containsMatchIn(t) }?.let { chat += Msg(false, "تم فتح «${tabs[it.second]}»"); tab = it.second; return true }
        return false
    }
    fun ask(q0: String) {
        val q = q0.trim(); if (q.isEmpty() || asking) return
        chat += Msg(true, q)
        if (Ai.key(c).isBlank()) { if (!localIntent(q)) chat += Msg(false, "الذكاء الاصطناعي غير متاح (لا يوجد مفتاح). جرّب أوامر مثل «افتح يوتيوب» أو «الطقس».", err = true); return }
        asking = true
        val sys = """أنت «الكود»، مساعد صوتي عربي على تلفزيون منزلي في الإمارات. أجب بالعربية الفصحى المبسطة وبإيجاز (3-5 جمل) لأن الإجابة تُعرض على شاشة التلفزيون.
لا تخترع معلومات: إذا لم تكن المعلومة في السياق أو في معرفتك المؤكدة فقل ذلك بوضوح. لا تختلق نتائج مباريات أو مواقيت. لا تكتب آيات قرآنية أو أبيات شعر من الذاكرة.
إذا طلب المستخدم فتح تطبيق من القائمة، أضف في آخر ردك السطر [[open:اسم_الحزمة]] بالضبط.
إذا طلب قسماً من أقسام الواجهة (${tabs.joinToString("، ")}) أضف [[tab:اسم_القسم]].
السياق الحالي:
${contextBlock()}
التطبيقات المثبتة:
${apps.take(50).joinToString("\n") { it.label + " = " + it.pkg }}"""
        val hist = JSONArray(chat.filter { !it.err }.takeLast(10).map { JSONObject().put("role", if (it.user) "user" else "assistant").put("text", it.text) })
        io {
            val r = try { Result.success(Ai.chat(c, sys, hist)) } catch (e: Throwable) { Result.failure(e) }
            withContext(Dispatchers.Main) {
                asking = false
                r.onSuccess { raw ->
                    val open = Regex("\\[\\[open:([\\w.]+)]]").find(raw)?.groupValues?.get(1)
                    val tb = Regex("\\[\\[tab:([^\\]]+)]]").find(raw)?.groupValues?.get(1)?.trim()
                    chat += Msg(false, raw.replace(Regex("\\[\\[(open|tab):[^\\]]+]]"), "").trim(), ai = true)
                    if (open != null && apps.any { it.pkg == open }) viewModelScope.launch { delay(900); launch(open) }
                    tabs.indexOf(tb).takeIf { it >= 0 }?.let { i -> viewModelScope.launch { delay(900); tab = i } }
                }.onFailure { e -> if (!localIntent(q)) chat += Msg(false, e.message ?: "خطأ", err = true) }
            }
        }
    }
}
