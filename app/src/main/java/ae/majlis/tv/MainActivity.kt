package ae.majlis.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import ae.majlis.tv.ui.AlcodeApp
import ae.majlis.tv.ui.AppState

/** Native launcher host (Compose for TV). No WebView. */
class MainActivity : ComponentActivity() {
    companion object { @Volatile var onAdhan: ((Int) -> Unit)? = null }
    private val s: AppState by viewModels()
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) { s.tab = 7; s.ask(t) } else s.say("لم يتم التعرف على الصوت")
    }
    private val pkgReceiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { s.reloadApps() } }

    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-AE").putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلّم الآن…")
        try { voice.launch(i) } catch (_: Exception) { s.say("البحث الصوتي غير متاح على هذا الجهاز") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {
                when {
                    s.moving != null -> s.moving = null
                    s.tab != 0 -> s.goHome()
                    else -> {} // launcher: Back on Home stays Home
                }
            }
            AlcodeApp(s) { listen() }
        }
        Adhan.ensureChannel(this); Adhan.scheduleNext(this)
        onAdhan = { i -> runOnUiThread { s.say("حان الآن موعد أذان ${ae.majlis.tv.data.Fmt.PNAMES.getOrElse(i) { "" }}"); s.now = System.currentTimeMillis() } }
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        })
    }
    /** HOME pressed while we are the launcher → back to the Home tab with overlays closed. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) s.goHome()
    }
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if ((e.keyCode == KeyEvent.KEYCODE_SEARCH || e.keyCode == KeyEvent.KEYCODE_ASSIST) && e.action == KeyEvent.ACTION_UP) { s.routes.clear(); s.tab = 7; listen(); return true }
        if (e.keyCode == KeyEvent.KEYCODE_SEARCH || e.keyCode == KeyEvent.KEYCODE_ASSIST) return true
        return super.dispatchKeyEvent(e)
    }
    override fun onResume() { super.onResume(); s.now = System.currentTimeMillis() }
    override fun onDestroy() { onAdhan = null; try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}; super.onDestroy() }
}
