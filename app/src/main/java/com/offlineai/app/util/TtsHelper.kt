package com.offlineai.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale("id", "ID") // prefer Indonesian, fallback to default
                if (tts?.isLanguageAvailable(Locale("id", "ID")) != TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = Locale.getDefault()
                }
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ready || tts == null) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "offline_ai_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
