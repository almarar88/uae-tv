@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
package ae.majlis.tv.ui

import android.view.KeyEvent as AKey
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import ae.majlis.tv.R
import ae.majlis.tv.data.*

@Composable
fun AlcodeApp(s: AppState, onVoice: () -> Unit) {
    AlcodeTheme {
        val photo = remember(s.prefsRev, s.now / 86_400_000L) { Photos.today(s.c, s.photos) }
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            // calm backdrop: one photo per day, sized to the screen, darkened by static scrims (no motion)
            Img(photo.uri, 960.dp, 540.dp, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.Bg.copy(alpha = .55f), .35f to C.Bg.copy(alpha = .35f), .62f to C.Bg.copy(alpha = .9f), 1f to C.Bg)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Transparent, 1f to C.Bg.copy(alpha = .55f))))
            if (s.tab != 0) Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = .9f)))
            Column(Modifier.fillMaxSize()) {
                TopBar(s)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (s.tab) {
                        0 -> HomeTab(s, photo)
                        1 -> NewsTab(s)
                        2 -> PrayerTab(s)
                        3 -> QuranTab(s)
                        4 -> PoetryTab(s)
                        5 -> WeatherTab(s)
                        6 -> FootballTab(s)
                        7 -> AskTab(s, onVoice)
                        else -> SettingsTab(s)
                    }
                }
            }
            s.toast?.let { t ->
                Text(t, style = MaterialTheme.typography.titleSmall, color = C.Bg, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SafeV)
                    .clip(RoundedCornerShape(24.dp)).background(C.Sand).padding(horizontal = 24.dp, vertical = 10.dp))
            }
        }
        s.routes.toList().forEach { r -> key(r) { RouteHost(s, r) } }
    }
}

@Composable
private fun TopBar(s: AppState) {
    val frs = remember { List(s.tabs.size) { FocusRequester() } }
    // Focus starts on the selected tab (Home), and returns there when Back/HOME resets the tab.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(s.homeReset) {
        ready = false; s.tab = 0
        // the framework's default initial focus may land on any tab; ignore it until we've placed focus on Home
        repeat(10) { kotlinx.coroutines.delay(60); try { frs[0].requestFocus() } catch (_: Exception) {} }
        ready = true
    }
    Row(Modifier.fillMaxWidth().padding(start = SafeH, end = SafeH, top = SafeV - 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.splash_logo), null, Modifier.size(40.dp, 34.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(18.dp))
        TabRow(
            selectedTabIndex = s.tab, modifier = Modifier.focusRestorer(),
            separator = { Spacer(Modifier.width(4.dp)) },
            indicator = { pos, focused -> TabRowDefaults.PillIndicator(currentTabPosition = pos[s.tab], doesTabRowHaveFocus = focused, activeColor = C.Text, inactiveColor = C.Gold.copy(alpha = .25f)) },
        ) {
            s.tabs.forEachIndexed { i, t ->
                Tab(modifier = Modifier.focusRequester(frs[i]), selected = i == s.tab, onFocus = { if (ready) s.tab = i }, onClick = { s.tab = i },
                    colors = TabDefaults.pillIndicatorTabColors(contentColor = C.Dim, selectedContentColor = C.Text, focusedContentColor = C.Bg, focusedSelectedContentColor = C.Bg)) {
                    Text(t, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** Home: a calm hero (no button) then rows. The hero is visual only; focus goes tabs → first row. */
@Composable
private fun HomeTab(s: AppState, photo: Photo) {
    Column(Modifier.fillMaxSize()) {
        Hero(s, photo)
        val vSpec = rememberPivot(44.dp, rtlAxis = false)
        val picks = remember(s.now, s.apps, s.favs, s.football, s.prefsRev) { s.picks() }
        val vod = remember(s.poets) { PoetryRepo.verseOfDay(s.poets) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides vSpec) {
            LazyColumn(Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(bottom = 140.dp)) {
                item("fav") { FavRow(s) }
                if (picks.isNotEmpty()) item("picks") { PicksRow(picks) }
                if (s.iptv) item("iptv") { IptvRow(s) }
                if (vod != null) item("vod") { VerseRow(s, vod) }
                item("allTitle") { Text("كل التطبيقات", style = MaterialTheme.typography.titleMedium, color = C.Sand, modifier = Modifier.padding(start = SafeH, top = 6.dp)) }
                val rows = s.apps.chunked(5)
                items(rows.size, key = { "all$it" }) { r -> AllAppsRow(s, rows[r]) }
            }
        }
    }
}

@Composable
private fun Hero(s: AppState, photo: Photo) {
    val np = remember(s.now, s.prefsRev) { s.nextPrayer() }
    Column(Modifier.fillMaxWidth().padding(horizontal = SafeH).padding(top = 6.dp, bottom = 10.dp)) {
        Text(Fmt.greeting(s.now), style = MaterialTheme.typography.titleMedium, color = C.Sand)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.time(s.c, s.now, ampm = false), style = MaterialTheme.typography.displayLarge.copy(lineHeight = 78.sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)), color = C.Text)
            Spacer(Modifier.width(22.dp))
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(Fmt.gregLine(s.now), style = MaterialTheme.typography.titleSmall, color = C.Text)
                Text(Fmt.hijriLine(s.now), style = MaterialTheme.typography.titleSmall, color = C.Sand)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            np?.let { (i, t) ->
                Text("${Fmt.PNAMES[i]} ${Fmt.time(s.c, t)} · ${Fmt.until(t - s.now)}", style = MaterialTheme.typography.labelLarge, color = C.Gold)
                Spacer(Modifier.width(20.dp))
            }
            s.weather?.let { w -> Text("${WeatherRepo.wmo(w.code).second} ${Math.round(w.temp)}° ${w.city}", style = MaterialTheme.typography.labelLarge, color = C.Sand) }
            Spacer(Modifier.weight(1f))
            Text(photo.ar, style = MaterialTheme.typography.labelSmall, color = C.Dim.copy(alpha = .8f), maxLines = 1)
        }
    }
}

@Composable
fun AppArt(a: AppTile, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val full = a.banner ?: a.art
    if (full != null) { Img(full, w, h, Modifier.fillMaxSize()); return }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(a.c1), Color(a.c2)))), contentAlignment = Alignment.Center) {
        val ic = a.play ?: a.icon
        if (ic != null) Img(ic, h * .62f, h * .62f, Modifier.size(h * .62f).clip(RoundedCornerShape(14.dp)), ContentScale.Fit)
        else Text(a.label.take(2), style = MaterialTheme.typography.headlineMedium, color = C.Text)
    }
}

@Composable
private fun FavRow(s: AppState) {
    var menu by remember { mutableStateOf<AppTile?>(null) }
    val favApps = s.favs.mapNotNull { p -> s.apps.firstOrNull { it.pkg == p } }
    Shelf(if (s.moving != null) "المفضلة — حرّك بالأسهم ثم اضغط موافق" else "المفضلة") {
        if (favApps.isEmpty()) item("hint") {
            CaptionedCard("اضغط مطولاً على أي تطبيق لإضافته", onClick = { s.say("اضغط مطولاً على تطبيق في «كل التطبيقات» ثم اختر «إضافة إلى المفضلة»") }) {
                Text("＋", style = MaterialTheme.typography.displayMedium, color = C.Gold, modifier = Modifier.align(Alignment.Center))
            }
        }
        items(favApps.size, key = { favApps[it].pkg }) { i -> val a = favApps[i]
            val mv = s.moving == a.pkg
            CaptionedCard(a.label, highlight = mv,
                modifier = Modifier.animateItem().onPreviewKeyEvent { e ->
                    if (!mv) return@onPreviewKeyEvent false
                    val down = e.type == KeyEventType.KeyDown
                    when (e.nativeKeyEvent.keyCode) {
                        AKey.KEYCODE_DPAD_LEFT -> { if (down) s.moveFav(a.pkg, +1); true }   // RTL: left = later
                        AKey.KEYCODE_DPAD_RIGHT -> { if (down) s.moveFav(a.pkg, -1); true }
                        AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_DPAD_DOWN -> true
                        AKey.KEYCODE_BACK -> { if (!down) s.moving = null; true }
                        else -> false
                    }
                },
                onClick = { if (mv) s.moving = null else s.launch(a.pkg) }, onLongClick = { menu = a }) { AppArt(a, 196.dp, 110.dp) }
        }
    }
    menu?.let { AppMenu(s, it) { menu = null } }
}

@Composable
private fun AllAppsRow(s: AppState, row: List<AppTile>) {
    var menu by remember { mutableStateOf<AppTile?>(null) }
    Row(Modifier.fillMaxWidth().padding(horizontal = SafeH).padding(top = 14.dp).focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        row.forEach { a ->
            CaptionedCard(a.label, width = 156.dp, height = 88.dp, onClick = { s.launch(a.pkg) }, onLongClick = { menu = a }) { AppArt(a, 156.dp, 88.dp) }
        }
    }
    menu?.let { AppMenu(s, it) { menu = null } }
}

@Composable
private fun AppMenu(s: AppState, a: AppTile, close: () -> Unit) {
    val fav = a.pkg in s.favs
    Panel(a.label, close) {
        val fr = remember { FocusRequester() }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("فتح", { close(); s.launch(a.pkg) }, Modifier.focusRequester(fr))
            Pill(if (fav) "إزالة من المفضلة" else "إضافة إلى المفضلة", { s.toggleFav(a.pkg); close(); s.say(if (fav) "أُزيل من المفضلة" else "أُضيف إلى المفضلة") })
            if (fav && s.favs.size > 1) Pill("تحريك داخل المفضلة", { s.moving = a.pkg; close() })
            Pill("معلومات التطبيق", { close(); ae.majlis.tv.data.Apps.appInfo(s.c, a.pkg) })
        }
        LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
    }
}

@Composable
private fun PicksRow(picks: List<Pick>) {
    Shelf("مختاراتي") {
        items(picks.size, key = { picks[it].key }) { i -> val p = picks[i]
            CaptionedCard(p.sub, onClick = p.act) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(C.Raised2, Color(0xFF3A2E1C)))).padding(14.dp)) {
                    Text(p.glyph, color = C.Gold, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopStart))
                    Text(p.title, style = MaterialTheme.typography.titleSmall, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }
}

@Composable
private fun IptvRow(s: AppState) {
    data class T(val key: String, val title: String, val sub: String, val img: String?, val act: () -> Unit)
    val tiles = buildList {
        add(T("live", "البث المباشر", "قنوات اشتراكك", null) { s.routes += Route.IptvR("live") })
        add(T("movie", "الأفلام", "مكتبة الأفلام", null) { s.routes += Route.IptvR("movie") })
        add(T("series", "المسلسلات", "مكتبة المسلسلات", null) { s.routes += Route.IptvR("series") })
        s.recent.take(6).forEach { r -> add(T("r" + r.kind + r.id, r.name, "شاهدته مؤخراً", r.img.takeIf { it.startsWith("https://") }) { s.play(r) }) }
        if (s.liwaInstalled()) add(T("app", "Liwaiptv", "فتح التطبيق", null) { s.launch(ae.majlis.tv.Iptv.LIWA_PKG) })
    }
    Shelf("Liwaiptv") {
        items(tiles.size, key = { tiles[it].key }) { i -> val t = tiles[i]
            CaptionedCard(t.sub, onClick = t.act) {
                if (t.img != null) Img(t.img, 196.dp, 110.dp, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg.copy(alpha = if (t.img != null) .85f else .0f)))))
                Box(Modifier.fillMaxSize().then(if (t.img == null) Modifier.background(Brush.linearGradient(listOf(Color(0xFF2B2216), C.Raised))) else Modifier).padding(14.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = C.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }
}

@Composable
private fun VerseRow(s: AppState, v: VerseOfDay) {
    val poet = s.poets[v.poet]; val poem = poet.poems[v.poem]
    Shelf("بيت اليوم") {
        item("vod") {
            CaptionedCard("${poet.name} — «${poem.title}» · المصدر: ${poem.source}", width = 560.dp, height = 110.dp, onClick = { s.routes += Route.PoemR(v.poet, v.poem) }) {
                Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B2216), C.Raised))).padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
                    v.lines.forEach { l -> Bayt(l, MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

/** One bayt: two hemistichs, the first at the start, the second at the end; a single-line verse is centred. */
@Composable
fun Bayt(v: List<String>, style: androidx.compose.ui.text.TextStyle, color: Color = C.Text) {
    if (v.size == 2) Row(Modifier.fillMaxWidth()) {
        Text(v[0], style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 2)
        Spacer(Modifier.width(24.dp))
        Text(v[1], style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 2)
    } else Text(v[0], style = style, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}
