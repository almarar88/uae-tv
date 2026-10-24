package ae.majlis.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Liwaiptv / Xtream-Codes subscription. Credentials are entered once in Majlis settings and stored
 * AES-256-GCM encrypted with a hardware-backed Android Keystore key. They never reach the web layer:
 * JS only receives catalogue JSON, and playback URLs are built here.
 */
object Iptv {
    const val LIWA_PKG = "com.liwaiptv.app"
    private const val ALIAS = "majlis.iptv"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }
    fun save(c: Context, server: String, user: String, pass: String) {
        if (server.isBlank()) { Store.set(c, "iptv.enc", ""); return }
        val o = JSONObject().put("s", server.trim().trimEnd('/')).put("u", user.trim()).put("p", pass)
        val ci = Cipher.getInstance("AES/GCM/NoPadding"); ci.init(Cipher.ENCRYPT_MODE, key())
        val enc = ci.doFinal(o.toString().toByteArray())
        Store.set(c, "iptv.enc", Base64.encodeToString(ci.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP))
    }
    private fun load(c: Context): JSONObject? = try {
        val v = Store.get(c, "iptv.enc"); if (v.isEmpty()) null else {
            val (iv, d) = v.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val ci = Cipher.getInstance("AES/GCM/NoPadding"); ci.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            JSONObject(String(ci.doFinal(d)))
        }
    } catch (_: Exception) { null }
    fun has(c: Context) = load(c) != null
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    /** Allowed read-only Xtream actions: get_live_categories, get_live_streams, get_vod_streams, get_series, get_series_info … */
    fun api(c: Context, action: String, extra: String): String {
        require(Regex("get_[a-z_]+").matches(action)) { "bad action" }
        require(Regex("[A-Za-z0-9_=&]*").matches(extra)) { "bad params" }
        val o = load(c) ?: error("no subscription")
        val (code, body) = Http.request("${o.getString("s")}/player_api.php?username=${enc(o.getString("u"))}&password=${enc(o.getString("p"))}&action=$action" + (if (extra.isNotEmpty()) "&$extra" else ""), timeout = 45000)
        require(code in 200..299) { "HTTP $code" }
        return body
    }
    /** kind: live|movie|series ; plays in Liwaiptv when installed, otherwise any installed video player. */
    fun play(c: Context, kind: String, id: String, ext: String, title: String): Boolean {
        val o = load(c) ?: return false
        require(Regex("[0-9]+").matches(id) && Regex("[a-z0-9]{0,5}").matches(ext))
        val path = when (kind) { "live" -> "live"; "movie" -> "movie"; else -> "series" }
        val url = "${o.getString("s")}/$path/${o.getString("u")}/${o.getString("p")}/$id.${ext.ifEmpty { if (kind == "live") "ts" else "mp4" }}"
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val liwa = Intent(i).setPackage(LIWA_PKG)
        return try {
            if (liwa.resolveActivity(c.packageManager) != null) c.startActivity(liwa) else c.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: Exception) { false }
    }
}
