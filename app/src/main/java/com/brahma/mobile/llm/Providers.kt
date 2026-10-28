package com.brahma.mobile.llm

import com.brahma.mobile.data.Prefs
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class GeminiProvider(private val key: String, private val model: String) : LlmProvider {
    override val name = "Gemini"

    override suspend fun generate(system: String, turns: List<Turn>, tools: List<ToolDef>): LlmReply {
        val contents = JSONArray()
        for (t in turns) {
            when (t) {
                is Turn.User -> contents.put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
                )
                is Turn.Model -> {
                    val parts = JSONArray()
                    if (t.text.isNotBlank()) parts.put(JSONObject().put("text", t.text))
                    for (c in t.calls) {
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject().put("name", c.name).put("args", c.args)
                            )
                        )
                    }
                    contents.put(JSONObject().put("role", "model").put("parts", parts))
                }
                is Turn.Tool -> {
                    val parts = JSONArray()
                    for ((c, r) in t.results) {
                        parts.put(
                            JSONObject().put(
                                "functionResponse",
                                JSONObject().put("name", c.name)
                                    .put("response", JSONObject().put("result", r))
                            )
                        )
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
            }
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        if (tools.isNotEmpty()) {
            val decls = JSONArray()
            for (tool in tools) {
                val d = JSONObject().put("name", tool.name).put("description", tool.description)
                if (tool.schema != null) d.put("parameters", tool.schema)
                decls.put(d)
            }
            body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(Http.jsonBody(body.toString()))
            .build()
        val res = JSONObject(Http.send(req))
        val cand = res.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw IOException("Gemini returned no answer: " + res.optJSONObject("promptFeedback"))
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        val calls = mutableListOf<Call>()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.has("text") && !p.optBoolean("thought", false)) text.append(p.optString("text"))
            p.optJSONObject("functionCall")?.let { fc ->
                calls.add(Call(UUID.randomUUID().toString(), fc.optString("name"), fc.optJSONObject("args") ?: JSONObject()))
            }
        }
        return LlmReply(text.toString().trim(), calls)
    }
}

/** OpenRouter (OpenAI-compatible chat completions with tool calling). */
class OpenRouterProvider(private val key: String, private val model: String) : LlmProvider {
    override val name = "OpenRouter"

    override suspend fun generate(system: String, turns: List<Turn>, tools: List<ToolDef>): LlmReply {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) {
            when (t) {
                is Turn.User -> msgs.put(JSONObject().put("role", "user").put("content", t.text))
                is Turn.Model -> {
                    val m = JSONObject().put("role", "assistant").put("content", t.text)
                    if (t.calls.isNotEmpty()) {
                        val arr = JSONArray()
                        for (c in t.calls) {
                            arr.put(
                                JSONObject().put("id", c.id).put("type", "function").put(
                                    "function",
                                    JSONObject().put("name", c.name).put("arguments", c.args.toString())
                                )
                            )
                        }
                        m.put("tool_calls", arr)
                    }
                    msgs.put(m)
                }
                is Turn.Tool -> for ((c, r) in t.results) {
                    msgs.put(JSONObject().put("role", "tool").put("tool_call_id", c.id).put("content", r))
                }
            }
        }
        val body = JSONObject().put("model", model).put("messages", msgs)
        if (tools.isNotEmpty()) {
            val arr = JSONArray()
            for (tool in tools) {
                val fn = JSONObject().put("name", tool.name).put("description", tool.description)
                fn.put("parameters", tool.schema ?: JSONObject().put("type", "object").put("properties", JSONObject()))
                arr.put(JSONObject().put("type", "function").put("function", fn))
            }
            body.put("tools", arr)
        }
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("X-Title", "Brahma Mobile")
            .post(Http.jsonBody(body.toString()))
            .build()
        val res = JSONObject(Http.send(req))
        val msg = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw IOException("OpenRouter returned no answer: " + res.optJSONObject("error"))
        val calls = mutableListOf<Call>()
        val tc = msg.optJSONArray("tool_calls")
        if (tc != null) {
            for (i in 0 until tc.length()) {
                val o = tc.optJSONObject(i) ?: continue
                val f = o.optJSONObject("function") ?: continue
                val args = try { JSONObject(f.optString("arguments", "{}").ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }
                calls.add(Call(o.optString("id", UUID.randomUUID().toString()), f.optString("name"), args))
            }
        }
        val content = if (msg.isNull("content")) "" else msg.optString("content", "")
        return LlmReply(content.trim(), calls)
    }
}

/** Tries the preferred provider first, then falls back to the other one if it fails. */
class FallbackRouter(private val prefs: Prefs) {
    suspend fun generate(
        system: String,
        turns: List<Turn>,
        tools: List<ToolDef>,
        note: (String) -> Unit
    ): LlmReply {
        val g = prefs.geminiKey.takeIf { it.isNotBlank() }?.let { GeminiProvider(it, prefs.geminiModel) }
        val o = prefs.openrouterKey.takeIf { it.isNotBlank() }?.let { OpenRouterProvider(it, prefs.openrouterModel) }
        val order = if (prefs.primary == "openrouter") listOfNotNull(o, g) else listOfNotNull(g, o)
        if (order.isEmpty()) throw IllegalStateException("No API key yet. Open Settings and add a Gemini or OpenRouter key.")
        var last: Exception? = null
        for (p in order) {
            try {
                return p.generate(system, turns, tools)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                note("${p.name} failed (${e.message?.take(90)})")
            }
        }
        throw last ?: IllegalStateException("All providers failed")
    }
}
