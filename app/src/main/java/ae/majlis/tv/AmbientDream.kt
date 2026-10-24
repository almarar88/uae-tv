package ae.majlis.tv

import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ae.majlis.tv.data.*
import ae.majlis.tv.ui.*
import kotlinx.coroutines.delay

/** System screensaver in Compose: credited UAE photo (changes every 2 min, slow crossfade), clock, both dates, next prayer, verse of the day. */
class AmbientDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    override fun onCreate() { super.onCreate(); ssc.performRestore(null); reg.currentState = Lifecycle.State.CREATED }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true; isScreenBright = false
        val v = ComposeView(this)
        window.decorView.setViewTreeLifecycleOwner(this); window.decorView.setViewTreeSavedStateRegistryOwner(this)
        v.setViewTreeLifecycleOwner(this); v.setViewTreeSavedStateRegistryOwner(this)
        v.setContent { Ambient() }
        setContentView(v)
    }
    override fun onDreamingStarted() { super.onDreamingStarted(); reg.currentState = Lifecycle.State.RESUMED }
    override fun onDreamingStopped() { reg.currentState = Lifecycle.State.CREATED; super.onDreamingStopped() }
    override fun onDetachedFromWindow() { reg.currentState = Lifecycle.State.DESTROYED; super.onDetachedFromWindow() }

    @Composable
    private fun Ambient() {
        val c = this
        val photos = remember { Photos.all(c) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L + 50); now = System.currentTimeMillis() } }
        val idx = ((now / 120_000L) % photos.size).toInt()
        val city = Cities.byId(Store.get(c, "city", "abudhabi"))
        val np = remember(now) { try { val p = Fmt.parts(now); val d = PrayerCalc.compute(city, p.y, p.m, p.d)
            (0 until 6).firstOrNull { it != 1 && d[it] > now }?.let { it to d[it] } ?: run { val t = Fmt.parts(now + 86_400_000L); 0 to PrayerCalc.compute(city, t.y, t.m, t.d)[0] } } catch (_: Exception) { null } }
        var vod by remember { mutableStateOf<Pair<Poet, VerseOfDay>?>(null) }
        LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { val p = PoetryRepo.load(c); PoetryRepo.verseOfDay(p)?.let { vod = p[it.poet] to it } } }
        AlcodeTheme {
            Box(Modifier.fillMaxSize().background(C.Bg)) {
                Crossfade(photos[idx], animationSpec = tween(2500), label = "photo") { ph -> Img(ph.uri, 960.dp, 540.dp, Modifier.fillMaxSize()) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.Bg.copy(alpha = .1f), .55f to C.Bg.copy(alpha = .25f), 1f to C.Bg.copy(alpha = .92f))))
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = SafeH, vertical = SafeV)) {
                    Text(Fmt.time(c, now, ampm = false), style = MaterialTheme.typography.displayLarge, color = C.Text)
                    Text("${Fmt.gregLine(now)} · ${Fmt.hijriLine(now)}", style = MaterialTheme.typography.titleMedium, color = C.Sand)
                    np?.let { (i, t) -> Text("${Fmt.PNAMES[i]} ${Fmt.time(c, t)} — ${Fmt.until(t - now)}", style = MaterialTheme.typography.titleSmall, color = C.Gold) }
                    vod?.let { (poet, v) -> Spacer(Modifier.height(14.dp)); Sadu(); Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(620.dp)) { Column { v.lines.forEach { Bayt(it, MaterialTheme.typography.bodyLarge) }; Text(poet.name, style = MaterialTheme.typography.labelMedium, color = C.Dim) } } }
                }
                Text(photos[idx].let { "${it.ar} — ${it.artist} · ${it.license}" }, style = MaterialTheme.typography.labelSmall, color = C.Dim,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = SafeH, vertical = SafeV))
            }
        }
    }
}
