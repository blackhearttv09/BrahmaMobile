package com.brahma.mobile.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Speech-to-text + text-to-speech. Starting to listen instantly stops speech (barge-in). */
class VoiceEngine(context: Context) {
    private val ctx = context.applicationContext
    var language = "bn-BD"
    val level = MutableStateFlow(0f)
    val listening = MutableStateFlow(false)
    val speaking = MutableStateFlow(false)

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking.value = true }
                    override fun onDone(utteranceId: String?) { speaking.value = false }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { speaking.value = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking.value = false }
                })
            }
        }
    }

    fun startListening(cbResult: (String) -> Unit, cbError: (String) -> Unit) {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            cbError("Speech recognition is not available on this phone (install Google app / speech services).")
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening.value = false; level.value = 0f }
            override fun onError(error: Int) {
                listening.value = false
                level.value = 0f
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    cbError("Speech error code $error")
                }
            }
            override fun onResults(results: Bundle?) {
                listening.value = false
                level.value = 0f
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) cbResult(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        listening.value = true
        r.startListening(i)
    }

    fun stopListening() {
        recognizer?.stopListening()
        listening.value = false
        level.value = 0f
    }

    fun speak(text: String) {
        val t = tts ?: return
        if (!ttsReady) return
        val res = t.setLanguage(Locale.forLanguageTag(language))
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.getDefault())
        }
        val clean = text.replace(Regex("[*#`_>|]"), "").take(3900)
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "brahma")
    }

    fun stopSpeaking() {
        tts?.stop()
        speaking.value = false
    }

    fun release() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
