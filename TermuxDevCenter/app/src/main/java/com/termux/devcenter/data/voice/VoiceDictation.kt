package com.termux.devcenter.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Dictée continue : le moteur Android s'arrête après chaque silence, on le relance aussitôt
 * et on accumule le texte, jusqu'à ce que l'utilisateur termine. Les pauses ne coupent donc pas la dictée.
 * Toutes les méthodes publiques doivent être appelées sur le thread principal.
 */
class VoiceDictation(private val context: Context) {

    data class State(
        val active: Boolean = false,
        val committed: String = "",
        val partial: String = "",
        /** Niveau sonore normalisé 0..1 pour l'animation. */
        val level: Float = 0f,
        val startedAt: Long = 0L,
        val error: String? = null
    ) {
        val text: String get() = joinText(committed, partial)
    }

    sealed interface Action {
        data class Restart(val delayMs: Long, val recreate: Boolean) : Action
        data class Fail(val message: String) : Action
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var consecutiveErrors = 0

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (active) return
        if (!isAvailable()) {
            _state.value = State(
                error = "Reconnaissance vocale indisponible : activez l'application Google " +
                    "(Réglages Android › Gestion globale › Clavier › Saisie vocale)."
            )
            return
        }
        active = true
        consecutiveErrors = 0
        _state.value = State(active = true, startedAt = System.currentTimeMillis())
        listen()
    }

    /** Termine la dictée et renvoie le texte complet (y compris le fragment en cours). */
    fun finish(): String {
        val text = _state.value.text
        shutdown()
        _state.value = State()
        return text
    }

    fun cancel() {
        shutdown()
        _state.value = State()
    }

    fun release() = shutdown()

    private fun shutdown() {
        active = false
        main.removeCallbacksAndMessages(null)
        recognizer?.run {
            cancel()
            destroy()
        }
        recognizer = null
    }

    private fun listen() {
        if (!active) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        r.startListening(buildIntent())
    }

    private fun restart(action: Action.Restart) {
        if (action.recreate) {
            recognizer?.destroy()
            recognizer = null
        }
        main.postDelayed({ listen() }, action.delayMs)
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Indicatifs : certains moteurs les respectent et coupent moins souvent.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15_000L)
        if (Build.VERSION.SDK_INT >= 33) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
    }

    private fun commit(text: String?) {
        val s = _state.value
        _state.value = s.copy(committed = joinText(s.committed, text.orEmpty()), partial = "")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (active) _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (active && !text.isNullOrBlank()) _state.value = _state.value.copy(partial = text)
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            commit(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            consecutiveErrors = 0
            restart(Action.Restart(delayMs = 0, recreate = false))
        }

        override fun onError(error: Int) {
            if (!active) return
            // Un silence termine la session avec NO_MATCH/SPEECH_TIMEOUT : on garde le fragment en cours.
            commit(_state.value.partial)
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                consecutiveErrors++
            }
            when (val action = decide(error, consecutiveErrors)) {
                is Action.Restart -> restart(action)
                is Action.Fail -> {
                    val text = _state.value.text
                    shutdown()
                    _state.value = State(committed = text, error = action.message)
                }
            }
        }
    }

    companion object {
        private const val MAX_CONSECUTIVE_ERRORS = 6

        fun joinText(a: String, b: String): String = listOf(a.trim(), b.trim()).filter { it.isNotEmpty() }.joinToString(" ")

        /** Politique de relance selon le code d'erreur de SpeechRecognizer. */
        fun decide(error: Int, consecutiveErrors: Int): Action = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                Action.Restart(delayMs = 0, recreate = false)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                Action.Fail("Permission micro refusée : autorisez le micro pour Termux Dev Center.")
            SpeechRecognizer.ERROR_AUDIO -> Action.Fail("Le micro est indisponible (utilisé par une autre application ?).")
            12, 13 -> Action.Fail("Langue non prise en charge par la reconnaissance vocale de ce téléphone.")
            else -> if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
                Action.Fail("La reconnaissance vocale a échoué plusieurs fois (code $error). Vérifiez la connexion internet.")
            } else {
                // CLIENT/BUSY : recréer le moteur ; réseau/serveur : patienter un peu.
                val recreate = error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                Action.Restart(delayMs = if (recreate) 300 else 800, recreate = recreate)
            }
        }
    }
}
