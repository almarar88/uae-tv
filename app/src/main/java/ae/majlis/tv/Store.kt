package ae.majlis.tv

import android.content.Context
import org.json.JSONObject

/** Small prefs wrapper. Usage counts are stored per package and per 3-hour slot of the day. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("majlis", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, def: String = "") = p(c).getString(k, def) ?: def
    fun set(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun all(c: Context): JSONObject {
        val o = JSONObject(); p(c).all.forEach { (k, v) -> if (!k.startsWith("u.") && k != "ai.key" && k != "iptv.enc") o.put(k, v.toString()) }
        o.put("ai.key.set", get(c, "ai.key").isNotEmpty()); return o
    }
    fun recordLaunch(c: Context, pkg: String) {
        val slot = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) / 3
        val k = "u.$pkg.$slot"; val t = "u.$pkg.total"
        val sp = p(c); sp.edit().putInt(k, sp.getInt(k, 0) + 1).putInt(t, sp.getInt(t, 0) + 1)
            .putLong("u.$pkg.last", System.currentTimeMillis()).apply()
    }
    fun usage(c: Context): JSONObject {
        val o = JSONObject()
        p(c).all.forEach { (k, v) ->
            if (k.startsWith("u.")) {
                val rest = k.substring(2); val dot = rest.lastIndexOf('.')
                val pkg = rest.substring(0, dot); val f = rest.substring(dot + 1)
                val e = o.optJSONObject(pkg) ?: JSONObject().also { o.put(pkg, it) }
                e.put(f, v)
            }
        }
        return o
    }
}
