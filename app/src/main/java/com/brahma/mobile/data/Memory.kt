package com.brahma.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Tiny on-device memory: durable facts + standing rules. Nothing leaves the phone except what the prompt needs. */
class Memory(ctx: Context) {
    private val sp = ctx.getSharedPreferences("brahma_memory", Context.MODE_PRIVATE)

    fun all(): Map<String, String> {
        val j = try { JSONObject(sp.getString("facts", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        val out = linkedMapOf<String, String>()
        j.keys().forEach { out[it] = j.optString(it) }
        return out
    }

    private fun save(m: Map<String, String>) {
        val j = JSONObject()
        m.forEach { (k, v) -> j.put(k, v) }
        sp.edit().putString("facts", j.toString()).apply()
    }

    fun put(key: String, value: String) {
        val m = LinkedHashMap(all())
        m[key.trim().lowercase().take(60)] = value.trim().take(300)
        while (m.size > 80) m.remove(m.keys.first())
        save(m)
    }

    fun remove(key: String) { save(all().filterKeys { it != key }) }

    fun clear() { sp.edit().remove("facts").remove("rules").apply() }

    fun rules(): List<String> {
        val a = try { JSONArray(sp.getString("rules", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).map { a.optString(it) }
    }

    fun addRule(rule: String) {
        val r = rules().toMutableList()
        if (rule.isNotBlank() && !r.contains(rule.trim())) r.add(rule.trim().take(300))
        sp.edit().putString("rules", JSONArray(r.takeLast(30)).toString()).apply()
    }

    fun clearRules() { sp.edit().remove("rules").apply() }

    fun search(q: String): String {
        val ql = q.lowercase()
        val hits = all().filter { (k, v) -> ql.isBlank() || k.contains(ql) || v.lowercase().contains(ql) }
        return if (hits.isEmpty()) "Nothing stored for '$q'." else hits.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    fun promptBlock(): String {
        val sb = StringBuilder()
        val facts = all()
        if (facts.isNotEmpty()) {
            sb.append("\nKNOWN FACTS ABOUT THE USER (from memory):\n")
            facts.entries.toList().takeLast(40).forEach { sb.append("- ${it.key}: ${it.value}\n") }
        }
        val rules = rules()
        if (rules.isNotEmpty()) {
            sb.append("\nSTANDING RULES FROM THE USER (always follow):\n")
            rules.forEach { sb.append("- $it\n") }
        }
        return sb.toString()
    }
}
