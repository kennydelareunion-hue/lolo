package com.termux.devcenter.data.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDictationPolicyTest {
    @Test
    fun `un silence relance immediatement sans compter d'erreur`() {
        assertEquals(
            VoiceDictation.Action.Restart(0, recreate = false),
            VoiceDictation.decide(SpeechRecognizer.ERROR_NO_MATCH, 99)
        )
        assertEquals(
            VoiceDictation.Action.Restart(0, recreate = false),
            VoiceDictation.decide(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, 99)
        )
    }

    @Test
    fun `moteur occupe recree puis abandon apres trop d'echecs`() {
        assertEquals(
            VoiceDictation.Action.Restart(300, recreate = true),
            VoiceDictation.decide(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 1)
        )
        assertEquals(
            VoiceDictation.Action.Restart(800, recreate = false),
            VoiceDictation.decide(SpeechRecognizer.ERROR_NETWORK, 2)
        )
        assertTrue(VoiceDictation.decide(SpeechRecognizer.ERROR_NETWORK, 7) is VoiceDictation.Action.Fail)
        assertTrue(VoiceDictation.decide(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, 0) is VoiceDictation.Action.Fail)
    }

    @Test
    fun `assemblage du texte dicte`() {
        assertEquals("bonjour je voulais", VoiceDictation.joinText(" bonjour ", "je voulais"))
        assertEquals("seul", VoiceDictation.joinText("", "seul "))
    }
}
