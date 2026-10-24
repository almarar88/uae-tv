@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
package ae.majlis.tv.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ae.majlis.tv.Adhan
import ae.majlis.tv.Ai
import ae.majlis.tv.BuildConfig
import ae.majlis.tv.Cities
import ae.majlis.tv.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Pad = Modifier.padding(horizontal = SafeH)

@Composable private fun H(t: String, m: Modifier = Modifier) = Text(t, style = MaterialTheme.typography.headlineSmall, color = C.Text, modifier = m)
@Composable private fun Small(t: String, m: Modifier = Modifier, color: Color = C.Dim) = Text(t, style = MaterialTheme.typography.bodySmall, color = color, modifier = m)
@Composable private fun Loading(t: String = "جارٍ التحميل…") = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t, style = MaterialTheme.typography.titleMedium, color = C.Dim) }

/** Vertical list with a fixed top pivot (focused item keeps its place; content slides). */
@Composable
fun PivotColumn(modifier: Modifier = Modifier, top: androidx.compose.ui.unit.Dp = 60.dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(top, rtlAxis = false)) {
        LazyColumn(modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(top = 10.dp, bottom = 200.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

// ═════════════════════════ الأخبار ═════════════════════════
@Composable
fun NewsTab(s: AppState) {
    LaunchedEffect(Unit) { s.loadNews() }
    val all = s.news ?: return Loading()
    if (all.isEmpty()) return Column(Pad.padding(top = 30.dp)) { H("تعذّر جلب الأخبار"); Spacer(Modifier.height(16.dp)); Pill("إعادة المحاولة", { s.news = null; s.loadNews(true) }) }
    val list = if (s.newsCat == "الكل") all else all.filter { it.cat == s.newsCat }
    val urgent = all.firstOrNull { System.currentTimeMillis() - it.t < 2 * 3600_000L }
    PivotColumn {
        item("cats") {
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = SafeH, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(News.CATS.filter { c -> c == "الكل" || all.any { it.cat == c } }) { c -> Pill(c, { s.newsCat = c }, selected = c == s.newsCat) }
            }
        }
        if (urgent != null) item("urgent") {
            Row(Pad.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("عاجل", style = MaterialTheme.typography.labelLarge, color = C.Text, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF9B1C24)).padding(horizontal = 10.dp, vertical = 2.dp))
                Spacer(Modifier.width(12.dp)); Text(urgent.title, style = MaterialTheme.typography.titleSmall, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (list.isEmpty()) item("none") { Small("لا أخبار في هذا التصنيف الآن", Pad) }
        items(list, key = { it.link + it.title }) { n -> NewsRow(s, n) }
    }
}

@Composable
private fun NewsPic(n: NewsItem, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    var bad by remember(n.img) { mutableStateOf(n.img.isEmpty()) }
    Box(Modifier.size(w, h).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(n.color), C.Raised))), contentAlignment = Alignment.Center) {
        if (bad) Text(n.src, style = MaterialTheme.typography.labelMedium, color = C.Text.copy(alpha = .85f), textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
        // only real ≥600px photos (requested at 640px; anything smaller is a thumbnail → placeholder, never upscaled/blurred)
        if (!bad) Img(News.bigImg(n.img), 640.dp.coerceAtMost(w * 3), 360.dp.coerceAtMost(h * 3), Modifier.fillMaxSize(), onLowRes = { bad = it }, minPx = 600)
    }
}

@Composable
private fun NewsRow(s: AppState, n: NewsItem) {
    Surface(onClick = { s.routes += Route.NewsR(n) }, modifier = Pad.fillMaxWidth().height(112.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.Raised.copy(alpha = .7f), focusedContainerColor = C.Raised2, contentColor = C.Text, focusedContentColor = C.Text),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, C.Ring), shape = RoundedCornerShape(12.dp)))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NewsPic(n, 164.dp, 92.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Small("${n.src} · ${n.cat} · ${Fmt.ago(n.t)}")
            }
        }
    }
}

@Composable
private fun NewsDetail(s: AppState, n: NewsItem) {
    val ctx = s.c
    val view = remember(n.link) { Intent(Intent.ACTION_VIEW, Uri.parse(n.link)).takeIf { n.link.startsWith("http") && it.resolveActivity(ctx.packageManager) != null } }
    val fr = remember { FocusRequester() }
    PivotColumn(Modifier.padding(top = SafeV)) {
        item { Row(Pad) {
            NewsPic(n, 400.dp, 225.dp)
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content), color = C.Text)
                Spacer(Modifier.height(10.dp)); Small("${n.src} · ${n.cat} · ${Fmt.ago(n.t)}", color = C.Sand)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (view != null) Pill("فتح الخبر في المتصفح", { ctx.startActivity(view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.focusRequester(fr))
                    Pill("رجوع", { s.routes.removeLastOrNull() }, if (view == null) Modifier.focusRequester(fr) else Modifier)
                }
            }
        } }
        item { ReadRow(Pad) { Text(n.desc.ifEmpty { "لا يوجد ملخص في الخلاصة." }, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)) } }
        item { Small("المصدر: ${n.src}", Pad) }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

// ═════════════════════════ الصلاة ═════════════════════════
@Composable
fun PrayerTab(s: AppState) {
    val d = remember(s.now, s.prefsRev) { s.prayerDay() }
    val np = remember(s.now, s.prefsRev) { s.nextPrayer() }
    var cityPick by remember { mutableStateOf(false) }
    Column(Pad.padding(top = 18.dp)) {
        np?.let { (i, t) ->
            Text("الصلاة القادمة", style = MaterialTheme.typography.titleSmall, color = C.Sand)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.PNAMES[i], style = MaterialTheme.typography.displayMedium, color = C.Text)
                Spacer(Modifier.width(18.dp))
                Text("${Fmt.time(s.c, t)} · ${Fmt.until(t - s.now)}", style = MaterialTheme.typography.headlineSmall, color = C.Gold, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (0 until 6).forEach { i ->
                val next = np?.let { it.first == i && it.second == d[i] } == true
                Column(Modifier.width(128.dp).clip(RoundedCornerShape(12.dp)).background(if (next) C.Palm else C.Raised).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.PNAMES[i], style = MaterialTheme.typography.titleSmall, color = if (next) C.Text else C.Sand)
                    Text(Fmt.time(s.c, d[i], ampm = false), style = MaterialTheme.typography.headlineSmall, color = C.Text)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Small("${s.city().ar} · ${Fmt.gregLine(s.now)} · ${Fmt.hijriLine(s.now)} — المواقيت محسوبة على الجهاز (طريقة الإمارات)")
        Spacer(Modifier.height(22.dp))
        Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("المدينة: ${s.city().ar}", { cityPick = true })
            val on = s.pref("adhan", "on") == "on"
            Pill(if (on) "تنبيه الأذان: مفعّل" else "تنبيه الأذان: متوقف", { s.setPref("adhan", if (on) "off" else "on") }, selected = on)
            Pill("تجربة التنبيه", { Adhan.notify(s.c, 2); s.say("أُرسل تنبيه تجريبي") })
        }
    }
    if (cityPick) ChoicePanel("المدينة", Cities.all.map { it.id to it.ar }, s.city().id, { s.setPref("city", it) }) { cityPick = false }
}

// ═════════════════════════ القرآن ═════════════════════════
@Composable
fun QuranTab(s: AppState) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(40.dp, rtlAxis = false)) {
        LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(start = SafeH, end = SafeH, top = 16.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(s.suras.size, key = { it }) { i -> val su = s.suras[i]
                TvCard({ s.routes += Route.Reader(su.n) }, width = 152.dp, height = 74.dp) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                        Text("${Fmt.arNum(su.n)}. ${su.name}", style = MaterialTheme.typography.titleSmall, color = C.Text, maxLines = 1)
                        Text("${su.type} · ${Fmt.arCount(su.ayas, "آية واحدة", "آيتان", "آيات", "آية")}", style = MaterialTheme.typography.labelSmall, color = C.Dim, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Reader(s: AppState, n: Int) {
    val su = s.suras[n - 1]
    var verses by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(n) { verses = withContext(Dispatchers.IO) { Quran.verses(s.c, n) } }
    val v = verses ?: return Loading()
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = SafeV)) {
        Row(Pad, verticalAlignment = Alignment.CenterVertically) { H("سورة ${su.name}"); Spacer(Modifier.width(16.dp)); Small("${su.type} · اضغط على آية لعرض تفسيرها · النص: مشروع تنزيل tanzil.net") }
        Sadu(Pad.padding(top = 6.dp, bottom = 6.dp))
        PivotColumn(top = 80.dp) {
            items(v.size, key = { it }) { i ->
                ReadRow(Pad.then(if (i == 0) Modifier.focusRequester(fr) else Modifier), onClick = { s.routes += Route.TafsirR(n, i + 1) }) {
                    Text(v[i] + " ﴿" + Fmt.arNum(i + 1) + "﴾", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 52.sp), color = C.Text)
                }
            }
        }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun TafsirView(s: AppState, sura: Int, aya: Int) {
    val su = s.suras[sura - 1]
    var text by remember { mutableStateOf<String?>(null) }
    var srcs by remember { mutableStateOf<List<Tafsir>?>(null) }
    var ai by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sura, aya) { withContext(Dispatchers.IO) { text = Quran.verses(s.c, sura).getOrNull(aya - 1); srcs = Quran.tafsir(sura, aya) } }
    val fr = remember { FocusRequester() }
    PivotColumn(Modifier.padding(top = SafeV)) {
        item { Column(Pad) { H("تفسير ${su.name}: ${Fmt.arNum(aya)}"); Sadu(Modifier.padding(vertical = 8.dp)) } }
        item { ReadRow(Pad.focusRequester(fr)) { Text((text ?: "") + " ﴿" + Fmt.arNum(aya) + "﴾", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 52.sp)) } }
        val sr = srcs
        when {
            sr == null -> item { Small("جارٍ جلب التفسير…", Pad) }
            sr.isEmpty() -> item { Small("تعذّر جلب نص التفسير (تحقق من الاتصال). لن نعرض شرحاً بلا مصدر.", Pad) }
            else -> {
                if (Ai.key(s.c).isNotBlank()) item {
                    Column(Pad) {
                        if (ai == null) Pill("✦ اشرح بلغة مبسطة (من نص التفسير فقط)", {
                            ai = "…"
                            s.io {
                                val r = try { "✦ ذكاء اصطناعي — " + Ai.chat(s.c, "أنت مساعد يشرح معاني آيات القرآن الكريم لعامة الناس بالعربية المبسطة. اعتمد فقط على نصوص التفاسير المرفقة ولا تضف أحكاماً أو أقوالاً من خارجها. لا تكتب نص الآية ولا أي آية أخرى. اكتب شرحاً من 3-5 جمل.",
                                    org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("text", "الآية (${su.name} $aya)\n\n" + sr.mapIndexed { i, x -> "[${i + 1}] ${x.name}:\n${x.text}" }.joinToString("\n\n")))) } catch (e: Throwable) { e.message ?: "خطأ" }
                                withContext(Dispatchers.Main) { ai = r }
                            }
                        }) else ReadRow { Text(ai!!, style = MaterialTheme.typography.bodyLarge, color = C.Sand) }
                    }
                }
                items(sr.size) { i -> ReadRow(Pad) { Column { Text("[${i + 1}] ${sr[i].name}", style = MaterialTheme.typography.titleSmall, color = C.Gold); Text(sr[i].text, style = MaterialTheme.typography.bodyLarge) } } }
                item { Small("المصادر: " + sr.joinToString(" · ") { it.name } + " — عبر alquran.cloud ($sura:$aya) · نص الآية: مشروع تنزيل tanzil.net", Pad) }
            }
        }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

// ═════════════════════════ الشعر ═════════════════════════
@Composable
fun PoetryTab(s: AppState) {
    if (s.poets.isEmpty()) return Loading()
    var sel by rememberSaveable { mutableIntStateOf(0) }
    Row(Modifier.fillMaxSize().padding(start = SafeH, end = SafeH, top = 12.dp)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(40.dp, rtlAxis = false)) {
            LazyColumn(Modifier.width(230.dp).fillMaxHeight().focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(s.poets.size) { i -> ListItem(selected = i == sel, onClick = { sel = i }, headlineContent = { Text(s.poets[i].short, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(Fmt.arCount(s.poets[i].poems.size, "قصيدة واحدة", "قصيدتان", "قصائد", "قصيدة"), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.onFocusChangedSelect { if (it) sel = i }) }
            }
        }
        Spacer(Modifier.width(28.dp))
        val p = s.poets[sel.coerceIn(s.poets.indices)]
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.headlineSmall, color = C.Text)
            Text(p.bio, style = MaterialTheme.typography.bodyMedium, color = C.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (p.bioSource.isNotEmpty()) Small("المصدر: ${p.bioSource}")
            Spacer(Modifier.height(12.dp))
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(20.dp, rtlAxis = false)) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().focusRestorer(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)) {
                    items(p.poems.size, key = { "${sel}_$it" }) { k -> val q = p.poems[k]
                        TvCard({ s.routes += Route.PoemR(sel, k) }, width = 196.dp, height = 96.dp) {
                            Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B2216), C.Raised))).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(q.title, style = MaterialTheme.typography.titleSmall, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(Fmt.arCount(q.verses.size, "بيت واحد", "بيتان", "أبيات", "بيتاً"), style = MaterialTheme.typography.labelSmall, color = C.Dim)
                            }
                        }
                    }
                }
            }
        }
    }
}
fun Modifier.onFocusChangedSelect(f: (Boolean) -> Unit) = this.then(Modifier.onFocusChanged { f(it.isFocused) })

@Composable
private fun PoemView(s: AppState, pi: Int, qi: Int) {
    val p = s.poets[pi]; val q = p.poems[qi]
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = SafeV)) {
        Column(Pad) { H("«${q.title}»"); Text(p.name, style = MaterialTheme.typography.titleSmall, color = C.Sand); Sadu(Modifier.padding(vertical = 8.dp)) }
        PivotColumn(top = 80.dp) {
            items(q.verses.size) { i -> ReadRow(Modifier.padding(horizontal = 90.dp).then(if (i == 0) Modifier.focusRequester(fr) else Modifier)) { Bayt(q.verses[i], MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 38.sp)) } }
            item { Small("المصدر: ${q.source}${PoetryRepo.host(q.url).let { if (it.isNotEmpty()) " — $it" else "" }} · منقول حرفياً من المصدر", Pad.padding(top = 10.dp)) }
        }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

// ═════════════════════════ الطقس ═════════════════════════
@Composable
fun WeatherTab(s: AppState) {
    LaunchedEffect(Unit) { s.loadWeather() }
    var cityPick by remember { mutableStateOf(false) }
    Column(Pad.padding(top = 18.dp)) {
        val w = s.weather
        if (w == null) { H(if (s.weatherLoaded) "الطقس غير متاح الآن" else "جارٍ التحميل…") } else {
            val (d, ic) = WeatherRepo.wmo(w.code)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$ic ${Math.round(w.temp)}°", style = MaterialTheme.typography.displayMedium, color = C.Text)
                Spacer(Modifier.width(22.dp))
                Column { Text("${w.city} — $d", style = MaterialTheme.typography.titleLarge, color = C.Text)
                    Small("الإحساس ${Math.round(w.feels)}° · الرطوبة ${w.hum}% · الرياح ${Math.round(w.wind)} كم/س", color = C.Sand) }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                w.days.forEachIndexed { i, day -> val (ds, di) = WeatherRepo.wmo(day.code)
                    val wd = try { java.time.LocalDate.parse(day.date).dayOfWeek.value % 7 } catch (_: Exception) { 0 }
                    Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised).padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "اليوم" else Fmt.DAYS[wd], style = MaterialTheme.typography.titleSmall, color = C.Sand)
                        Text(di, fontSize = 26.sp)
                        Text("${Math.round(day.max)}° / ${Math.round(day.min)}°", style = MaterialTheme.typography.titleSmall, color = C.Text)
                        Text(ds, style = MaterialTheme.typography.labelSmall, color = C.Dim, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); Small("المصدر: Open-Meteo.com (CC BY 4.0)")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("المدينة: ${s.city().ar}", { cityPick = true })
            Pill("تحديث", { s.loadWeather(true); s.say("جارٍ التحديث…") })
        }
    }
    if (cityPick) ChoicePanel("المدينة", Cities.all.map { it.id to it.ar }, s.city().id, { s.setPref("city", it) }) { cityPick = false }
}

// ═════════════════════════ الدوري ═════════════════════════
@Composable
fun FootballTab(s: AppState) {
    LaunchedEffect(Unit) { s.loadFootball() }
    val f = s.football
    if (f == null) return Column(Pad.padding(top = 30.dp)) { H(if (s.footballLoaded) "لا توجد بيانات كرة قدم متاحة الآن" else "جارٍ التحميل…"); Spacer(Modifier.height(16.dp)); Pill("إعادة المحاولة", { s.loadFootball(true) }) }
    @Composable fun line(m: Match, score: Boolean) = ReadRow(Pad) {
        Column { Text("${m.home}${if (score && m.hs != null) "  ${m.hs} - ${m.aws}  " else "  ×  "}${m.away}", style = MaterialTheme.typography.titleSmall)
            Small(listOfNotNull(m.date, m.t?.let { Fmt.time(s.c, it) }, m.venue.takeIf { it.isNotEmpty() }).joinToString(" · ")) }
    }
    PivotColumn {
        item { Row(Pad, verticalAlignment = Alignment.CenterVertically) { H("دوري أدنوك للمحترفين"); Spacer(Modifier.width(14.dp)); if (f.season.isNotEmpty()) Small("موسم ${f.season}") } }
        item { Text("اليوم", style = MaterialTheme.typography.titleMedium, color = C.Sand, modifier = Pad) }
        if (f.today.isEmpty()) item { Small("لا مباريات اليوم", Pad) } else items(f.today.size) { line(f.today[it], true) }
        item { Text("القادمة", style = MaterialTheme.typography.titleMedium, color = C.Sand, modifier = Pad) }
        if (f.next.isEmpty()) item { Small("—", Pad) } else items(f.next.size) { line(f.next[it], false) }
        item { Text("آخر النتائج", style = MaterialTheme.typography.titleMedium, color = C.Sand, modifier = Pad) }
        if (f.past.isEmpty()) item { Small("—", Pad) } else items(f.past.size) { line(f.past[it], true) }
        if (f.table.isNotEmpty()) {
            item { Text("الترتيب", style = MaterialTheme.typography.titleMedium, color = C.Sand, modifier = Pad) }
            items(f.table.size) { i -> val r = f.table[i]
                ReadRow(Pad) { Row { Text(r.rank, Modifier.width(40.dp)); Text(r.team, Modifier.weight(1f)); Text("لعب ${r.played}", Modifier.width(90.dp)); Text("فارق ${r.gd}", Modifier.width(90.dp)); Text("${r.pts} نقطة", Modifier.width(90.dp), color = C.Gold) } }
            }
        }
        item { Small("المصدر: TheSportsDB.com (واجهة مجانية عامة)${if (f.updated.isNotEmpty()) " · آخر تحديث للجدول ${f.updated} UTC" else ""}. الواجهة المجانية تعرض أعلى ${f.table.size} فرق فقط ومباراة قادمة واحدة.", Pad) }
        item { Box(Pad) { Pill("تحديث", { s.loadFootball(true); s.say("جارٍ التحديث…") }) } }
    }
}

// ═════════════════════════ اسأل ═════════════════════════
@Composable
fun AskTab(s: AppState, onVoice: () -> Unit) {
    var typing by remember { mutableStateOf(false) }
    val st = rememberLazyListState()
    LaunchedEffect(s.chat.size) { if (s.chat.isNotEmpty()) st.animateScrollToItem(s.chat.size - 1) }
    val hasVoice = remember { s.c.packageManager.queryIntentActivities(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty() }
    val info = remember(s.prefsRev) { Ai.info(s.c) }
    Column(Pad.fillMaxSize().padding(top = 14.dp)) {
        Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("اكتب سؤالك", { typing = true })
            if (hasVoice) Pill("تحدّث", onVoice)
            listOf("أعطني موجز اليوم", "متى الصلاة القادمة؟", "كيف الطقس؟").forEach { q -> Pill(q, { s.ask(q) }, enabled = !s.asking) }
            if (s.chat.isNotEmpty()) Pill("مسح", { s.chat.clear() })
        }
        Spacer(Modifier.height(8.dp))
        Small(if (info.optBoolean("hasKey")) "✦ ${info.optString("provider")} · ${info.optString("model")} — يعتمد على بيانات الشاشة فقط ولا يخترع المعلومات" else "لا يوجد مفتاح ذكاء اصطناعي — الأوامر البسيطة تعمل («افتح يوتيوب»، «الطقس»). أضف مفتاحاً من الإعدادات.")
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxSize(), state = st, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
            items(s.chat.size) { i -> val m = s.chat[i]
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.user) Alignment.CenterStart else Alignment.CenterEnd) {
                    Text((if (m.ai) "✦ " else "") + m.text, style = MaterialTheme.typography.bodyLarge, color = if (m.err) Color(0xFFE8A39B) else C.Text,
                        modifier = Modifier.widthIn(max = 640.dp).clip(RoundedCornerShape(14.dp)).background(if (m.user) C.Raised2 else C.Raised).padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
            if (s.asking) item { Small("…") }
        }
    }
    if (typing) TextPanel("اسأل الكود", listOf(Triple("سؤالك أو أمرك", "", false)), { s.ask(it[0]) }, { typing = false })
}

// ═════════════════════════ الإعدادات ═════════════════════════
@Composable
fun SettingsTab(s: AppState) {
    var dlg by remember { mutableStateOf("") }
    var upd by remember { mutableStateOf<String?>(null) }
    var updInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val rev = s.prefsRev
    @Composable fun row(title: String, value: String, act: () -> Unit) = ListItem(selected = false, onClick = act, modifier = Pad,
        colors = androidx.tv.material3.ListItemDefaults.colors(contentColor = C.Text, focusedContentColor = C.Bg, focusedContainerColor = C.Sand),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) }, trailingContent = { Text(value, style = MaterialTheme.typography.labelLarge) })
    PivotColumn {
        item { row("المدينة", s.city().ar) { dlg = "city" } }
        item { val on = s.pref("adhan", "on") == "on"; row("تنبيه الأذان", if (on) "مفعّل" else "متوقف") { s.setPref("adhan", if (on) "off" else "on") } }
        item { val c24 = s.pref("clock", "12") == "24"; row("نظام الساعة", if (c24) "24 ساعة" else "12 ساعة") { s.setPref("clock", if (c24) "12" else "24") } }
        item { val b = s.pref("bg", "auto"); row("صورة الخلفية", s.photos.firstOrNull { "bg/" + it.file == b || it.file == b }?.ar?.substringBefore("—")?.trim() ?: "تلقائي (صورة لكل يوم)") { dlg = "bg" } }
        item { val i = Ai.info(s.c); row("مفتاح الذكاء الاصطناعي", if (i.optBoolean("hasKey")) "${i.optString("provider")}${if (i.optBoolean("override")) " · مفتاح بديل" else " · المفتاح المدمج"}" else "غير موجود") { dlg = "ai" } }
        item { row("نموذج الذكاء الاصطناعي", s.pref("ai.model").ifEmpty { "الافتراضي" }) { dlg = "model" } }
        item { row("اشتراك Liwaiptv", if (s.iptv) "مُعدّ (مشفّر على الجهاز)" else "غير مُعدّ") { dlg = "iptv" } }
        item { row("التحقق من التحديثات", upd ?: "الإصدار الحالي ${BuildConfig.VERSION_NAME}") {
            upd = "جارٍ التحقق…"
            s.io { val r = try { Updater.check() } catch (e: Throwable) { null }
                withContext(Dispatchers.Main) { updInfo = r; upd = when { r == null -> "تعذّر التحقق"; r.newer -> "يتوفر ${r.latest} — اضغط «تثبيت التحديث»"; else -> "لديك أحدث إصدار (${BuildConfig.VERSION_NAME})" } } }
        } }
        if (updInfo?.newer == true) item { row("تثبيت التحديث", updInfo!!.latest) {
            upd = "جارٍ التنزيل…"; val u = updInfo!!.apk
            s.io { val r = try { Updater.install(s.c, u) } catch (e: Throwable) { e.message ?: "خطأ" }
                withContext(Dispatchers.Main) { upd = when (r) { "needs-permission" -> "اسمح بتثبيت التطبيقات ثم أعد المحاولة"; "started" -> "بدأ التثبيت"; else -> r } } }
        } }
        item { row("شاشة التوقف", "إعدادات النظام") { try { s.c.startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { s.c.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
        item { row("إعدادات الجهاز", "") { try { s.c.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { s.say("تعذّر فتح الإعدادات") } } }
        item { row("الحقوق والمصادر", "") { s.routes += Route.Credits } }
        item { Small("الإصدار ${BuildConfig.VERSION_NAME}", Pad.padding(top = 8.dp)) }
    }
    when (dlg) {
        "city" -> ChoicePanel("المدينة", Cities.all.map { it.id to it.ar }, s.city().id, { s.setPref("city", it) }) { dlg = "" }
        "bg" -> ChoicePanel("صورة الخلفية", listOf("auto" to "تلقائي (صورة لكل يوم)") + s.photos.map { "bg/" + it.file to it.ar }, s.pref("bg", "auto"), { s.setPref("bg", it) }) { dlg = "" }
        "ai" -> TextPanel("مفتاح الذكاء الاصطناعي", listOf(Triple("المفتاح (اتركه فارغاً لاستخدام المفتاح المدمج)", "", true)), { ae.majlis.tv.Store.set(s.c, "ai.key", it[0].trim()); s.prefsRev++; s.say("تم الحفظ") }, { dlg = "" },
            note = "يُكتشف المزوّد من بادئة المفتاح: Gemini أو xAI أو OpenAI أو Anthropic أو Groq أو OpenRouter. يُحفظ على الجهاز فقط.")
        "model" -> TextPanel("نموذج الذكاء الاصطناعي", listOf(Triple("اسم النموذج (فارغ = الافتراضي)", s.pref("ai.model"), false)), { s.setPref("ai.model", it[0].trim()); s.say("تم الحفظ") }, { dlg = "" })
        "iptv" -> TextPanel("اشتراك Liwaiptv (Xtream)", listOf(Triple("الخادم (https://…)", "", false), Triple("اسم المستخدم", "", false), Triple("كلمة المرور", "", true)),
            { v -> if (v[0].isNotBlank() && !v[0].trim().startsWith("http")) s.say("أدخل عنوان الخادم كاملاً") else { s.saveIptv(v[0], v[1], v[2]); s.say(if (v[0].isBlank()) "أُزيل الاشتراك" else "تم حفظ الاشتراك مشفّراً") } }, { dlg = "" },
            note = "تُشفّر البيانات بمفتاح من مخزن مفاتيح أندرويد ولا تغادر الجهاز إلا إلى خادمك. اترك الخادم فارغاً لإزالة الاشتراك.")
    }
    @Suppress("UNUSED_EXPRESSION") rev
}

@Composable
private fun Credits(s: AppState) {
    val fr = remember { FocusRequester() }
    PivotColumn(Modifier.padding(top = SafeV)) {
        item { Column(Pad) { H("الحقوق والمصادر"); Sadu(Modifier.padding(vertical = 8.dp)) } }
        item { ReadRow(Pad.focusRequester(fr)) { Text("من مؤسسة الكود. مفتوح المصدر (MIT).", style = MaterialTheme.typography.bodyLarge) } }
        items(s.photos) { p -> ReadRow(Pad) { Column { Text(p.ar, style = MaterialTheme.typography.titleSmall); Small("${p.artist} · ${p.license} · Wikimedia Commons") } } }
        item { ReadRow(Pad) { Text("القرآن الكريم: نص مشروع تنزيل (tanzil.net). التفسير: alquran.cloud. الطقس: Open-Meteo.com (CC BY 4.0). كرة القدم: TheSportsDB.com. الأخبار: ${s.news?.map { it.src }?.distinct()?.joinToString("، ")?.ifEmpty { null } ?: "الإمارات اليوم، The National"} (تُخفى المصادر غير المتاحة). الخطوط: Noto Kufi Arabic وNoto Naskh Arabic وAmiri Quran (SIL OFL).", style = MaterialTheme.typography.bodyMedium) } }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

// ═════════════════════════ Liwaiptv hub ═════════════════════════
@Composable
private fun IptvHub(s: AppState, kind: String) {
    var cats by remember { mutableStateOf<List<IpCat>?>(null) }
    var cat by remember { mutableStateOf<IpCat?>(null) }
    var items by remember { mutableStateOf<List<IpItem>?>(null) }
    LaunchedEffect(kind) { cats = withContext(Dispatchers.IO) { IptvRepo.cats(s.c, kind) }; cat = cats?.firstOrNull() }
    LaunchedEffect(cat) { val c = cat ?: return@LaunchedEffect; items = null; items = withContext(Dispatchers.IO) { IptvRepo.items(s.c, kind, c.id) } }
    val title = when (kind) { "live" -> "البث المباشر"; "movie" -> "الأفلام"; else -> "المسلسلات" }
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = SafeV)) {
        Row(Pad, verticalAlignment = Alignment.CenterVertically) { H(title); Spacer(Modifier.width(14.dp)); Small("Liwaiptv · ${cat?.name ?: ""}") }
        Spacer(Modifier.height(10.dp))
        val cs = cats
        if (cs == null) return Loading()
        if (cs.isEmpty()) return Column(Pad) { Small("تعذّر جلب القائمة من الخادم. تحقّق من الاشتراك في الإعدادات."); Spacer(Modifier.height(12.dp)); Pill("رجوع", { s.routes.removeLastOrNull() }, Modifier.focusRequester(fr)); LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} } }
        Row(Modifier.fillMaxSize().padding(start = SafeH, end = SafeH)) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(40.dp, rtlAxis = false)) {
                LazyColumn(Modifier.width(240.dp).fillMaxHeight().focusRestorer(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(cs.size) { i -> ListItem(selected = cs[i] == cat, onClick = { cat = cs[i] }, headlineContent = { Text(cs[i].name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier) }
                }
            }
            Spacer(Modifier.width(24.dp))
            val its = items
            if (its == null) Loading() else if (its.isEmpty()) Small("لا عناصر في هذا التصنيف") else
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(20.dp, rtlAxis = false)) {
                val poster = kind != "live"
                LazyVerticalGrid(GridCells.Fixed(if (poster) 5 else 4), Modifier.fillMaxSize().focusRestorer(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)) {
                    items(its.size, key = { its[it].kind + its[it].id }) { i -> val it = its[i]
                        val w = if (poster) 110.dp else 140.dp; val h = if (poster) 160.dp else 80.dp
                        CaptionedCard(it.name, width = w, height = h, onClick = { if (it.kind == "series") s.routes += Route.SeriesR(it) else s.play(it) }) {
                            Box(Modifier.fillMaxSize().background(C.Raised2), contentAlignment = Alignment.Center) {
                                Text(it.name.take(2), style = MaterialTheme.typography.titleMedium, color = C.Dim)
                                if (it.img.startsWith("http")) Img(it.img, w, h, Modifier.fillMaxSize(), if (poster) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit)
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(cats) { if (cats?.isNotEmpty() == true) try { fr.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun SeriesView(s: AppState, it: IpItem) {
    var eps by remember { mutableStateOf<List<IpItem>?>(null) }
    LaunchedEffect(it.id) { eps = withContext(Dispatchers.IO) { IptvRepo.episodes(s.c, it.id) } }
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = SafeV)) {
        Column(Pad) { H(it.name); if (it.plot.isNotEmpty()) Text(it.plot, style = MaterialTheme.typography.bodyMedium, color = C.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        val e = eps ?: return Loading()
        if (e.isEmpty()) return Column(Pad) { Small("لا حلقات متاحة"); Pill("رجوع", { s.routes.removeLastOrNull() }, Modifier.focusRequester(fr)); LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} } }
        PivotColumn(top = 60.dp) { items(e.size) { i -> ReadRow(Pad.then(if (i == 0) Modifier.focusRequester(fr) else Modifier), onClick = { s.play(e[i]) }) { Text("▶  " + e[i].name, style = MaterialTheme.typography.titleSmall) } } }
        LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
    }
}

// ═════════════════════════ route host ═════════════════════════
@Composable
fun RouteHost(s: AppState, r: Route) {
    Overlay({ s.routes.removeLastOrNull() }) {
        when (r) {
            is Route.Reader -> Reader(s, r.sura)
            is Route.TafsirR -> TafsirView(s, r.sura, r.aya)
            is Route.PoemR -> PoemView(s, r.poet, r.poem)
            is Route.NewsR -> NewsDetail(s, r.item)
            is Route.IptvR -> IptvHub(s, r.kind)
            is Route.SeriesR -> SeriesView(s, r.item)
            Route.Credits -> Credits(s)
        }
    }
}
