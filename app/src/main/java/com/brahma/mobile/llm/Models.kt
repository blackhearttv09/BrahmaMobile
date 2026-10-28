package com.brahma.mobile.llm

import org.json.JSONObject

data class Call(val id: String, val name: String, val args: JSONObject)

/** Provider-neutral conversation. Each provider converts this to its own wire format. */
sealed class Turn {
    data class User(val text: String) : Turn()
    data class Model(val text: String, val calls: List<Call>) : Turn()
    data class Tool(val results: List<Pair<Call, String>>) : Turn()
}

data class LlmReply(val text: String, val calls: List<Call>)

/** A tool the model may call. [schema] is a JSON-schema "object" or null for no parameters. */
class ToolDef(
    val name: String,
    val description: String,
    val schema: JSONObject?,
    val confirm: Boolean = false,
    val untrusted: Boolean = false,
    val run: suspend (JSONObject) -> String
)

interface LlmProvider {
    val name: String
    suspend fun generate(system: String, turns: List<Turn>, tools: List<ToolDef>): LlmReply
}
