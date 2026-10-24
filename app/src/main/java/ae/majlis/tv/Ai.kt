package ae.majlis.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-provider AI client. The provider is detected from the key prefix:
 *   xai- → xAI, gsk_ → Groq, sk-ant- → Anthropic, sk-or- → OpenRouter, sk- → OpenAI,
 *   AIza / AQ. → Google Gemini.
 * Build-time key comes from BuildConfig.AI_KEY; Settings can override key and model.
 */
object Ai {
    data class Provider(val id: String, val label: String, val defaultModel: String)

    fun detect(key: String): Provider = when {
        key.startsWith("xai-") -> Provider("xai", "xAI", "grok-3-mini")
        key.startsWith("gsk_") -> Provider("groq", "Groq", "llama-3.3-70b-versatile")
        key.startsWith("sk-ant-") -> Provider("anthropic", "Anthropic", "claude-3-5-haiku-latest")
        key.startsWith("sk-or-") -> Provider("openrouter", "OpenRouter", "openrouter/auto")
        key.startsWith("sk-") -> Provider("openai", "OpenAI", "gpt-4o-mini")
        else -> Provider("gemini", "Google Gemini", "gemini-flash-lite-latest")
    }

    fun key(c: Context): String = Store.get(c, "ai.key").ifBlank { BuildConfig.AI_KEY }
    fun info(c: Context): JSONObject {
        val k = key(c); val p = detect(k)
        return JSONObject().put("hasKey", k.isNotBlank()).put("provider", p.label)
            .put("model", Store.get(c, "ai.model").ifBlank { p.defaultModel })
            .put("override", Store.get(c, "ai.key").isNotBlank())
    }

    /** messages: [{role:"user"|"assistant", text:"..."}]. Returns reply text or throws. */
    fun chat(c: Context, system: String, messages: JSONArray): String {
        val k = key(c); require(k.isNotBlank()) { "لا يوجد مفتاح ذكاء اصطناعي. أضِفه من الإعدادات." }
        val p = detect(k); val model = Store.get(c, "ai.model").ifBlank { p.defaultModel }
        return when (p.id) {
            "gemini" -> gemini(k, model, system, messages)
            "anthropic" -> anthropic(k, model, system, messages)
            else -> openAiCompat(p.id, k, model, system, messages)
        }
    }

    private fun gemini(k: String, model: String, system: String, msgs: JSONArray): String {
        val contents = JSONArray()
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            contents.put(JSONObject().put("role", if (m.getString("role") == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", m.getString("text")))))
        }
        val body = JSONObject().put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 1200))
        val (code, txt) = Http.request("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", "POST", body.toString(), mapOf("x-goog-api-key" to k), 60000)
        if (code != 200) throw RuntimeException(err(code, txt))
        val parts = JSONObject(txt).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
    }

    private fun anthropic(k: String, model: String, system: String, msgs: JSONArray): String {
        val arr = JSONArray()
        for (i in 0 until msgs.length()) { val m = msgs.getJSONObject(i); arr.put(JSONObject().put("role", m.getString("role")).put("content", m.getString("text"))) }
        val body = JSONObject().put("model", model).put("max_tokens", 1200).put("system", system).put("messages", arr)
        val (code, txt) = Http.request("https://api.anthropic.com/v1/messages", "POST", body.toString(), mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"), 60000)
        if (code != 200) throw RuntimeException(err(code, txt))
        return JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text").trim()
    }

    private fun openAiCompat(id: String, k: String, model: String, system: String, msgs: JSONArray): String {
        val base = when (id) { "xai" -> "https://api.x.ai/v1"; "groq" -> "https://api.groq.com/openai/v1"; "openrouter" -> "https://openrouter.ai/api/v1"; else -> "https://api.openai.com/v1" }
        val arr = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (i in 0 until msgs.length()) { val m = msgs.getJSONObject(i); arr.put(JSONObject().put("role", m.getString("role")).put("content", m.getString("text"))) }
        val body = JSONObject().put("model", model).put("messages", arr).put("temperature", 0.6)
        val (code, txt) = Http.request("$base/chat/completions", "POST", body.toString(), mapOf("Authorization" to "Bearer $k"), 60000)
        if (code != 200) throw RuntimeException(err(code, txt))
        return JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }

    private fun err(code: Int, txt: String): String {
        val msg = try { JSONObject(txt).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
        return when (code) {
            401, 403 -> "المفتاح غير صالح أو لا يملك صلاحية ($code)."
            429 -> "تم تجاوز حد الاستخدام لمفتاح الذكاء الاصطناعي. حاول لاحقاً."
            else -> "خطأ من خدمة الذكاء الاصطناعي ($code)" + (msg?.let { ": " + it.take(160) } ?: "")
        }
    }
}
