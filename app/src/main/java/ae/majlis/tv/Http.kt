package ae.majlis.tv

import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun request(url: String, method: String = "GET", body: String? = null, headers: Map<String, String> = emptyMap(), timeout: Int = 30000): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "MajlisTV/" + BuildConfig.VERSION_NAME + " (Android TV)")
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }
}
