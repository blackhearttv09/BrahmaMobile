package com.brahma.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brahma.mobile.agent.Agent
import com.brahma.mobile.agent.Toolbox
import com.brahma.mobile.data.Memory
import com.brahma.mobile.data.Prefs
import com.brahma.mobile.llm.Turn
import com.brahma.mobile.voice.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class UiMsg(val id: Long, val role: String, val text: String) // role: user | assistant | step | error
data class ConfirmReq(val text: String, val deferred: CompletableDeferred<Boolean>)

class BrahmaViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val memory = Memory(app)
    val voice = VoiceEngine(app).also { it.language = prefs.language }

    private val agent = Agent(app, prefs, memory, Toolbox(app, memory).all())
    private val history = mutableListOf<Turn>()
    private val ids = AtomicLong(0)
    private var job: Job? = null

    val messages = MutableStateFlow<List<UiMsg>>(emptyList())
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val confirm = MutableStateFlow<ConfirmReq?>(null)

    private fun add(role: String, text: String) {
        messages.value = (messages.value + UiMsg(ids.incrementAndGet(), role, text)).takeLast(200)
    }

    fun setLanguage(tag: String) {
        prefs.language = tag
        voice.language = tag
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isBlank() || busy.value) return
        add("user", t)
        busy.value = true
        job = viewModelScope.launch {
            try {
                val reply = agent.run(
                    t, history,
                    onStep = { s -> status.value = s; add("step", s) },
                    confirm = { ask(it) }
                )
                add("assistant", reply)
                if (prefs.speakReplies) voice.speak(reply)
            } catch (e: CancellationException) {
                add("step", "■ " + "Stopped")
                throw e
            } catch (e: Exception) {
                add("error", e.message ?: e.javaClass.simpleName)
            } finally {
                busy.value = false
                status.value = ""
            }
        }
    }

    private suspend fun ask(text: String): Boolean {
        if (!prefs.askConfirm) return true
        val d = CompletableDeferred<Boolean>()
        confirm.value = ConfirmReq(text, d)
        return try { d.await() } finally { confirm.value = null }
    }

    fun answer(ok: Boolean) { confirm.value?.deferred?.complete(ok) }

    fun stop() {
        job?.cancel()
        confirm.value?.deferred?.complete(false)
        voice.stopSpeaking()
        voice.stopListening()
    }

    fun listen() {
        voice.startListening(cbResult = { send(it) }, cbError = { add("error", it) })
    }

    fun clearChat() {
        messages.value = emptyList()
        history.clear()
    }

    override fun onCleared() {
        voice.release()
        super.onCleared()
    }
}
