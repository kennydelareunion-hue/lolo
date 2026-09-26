package com.termux.devcenter.data.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AttachmentProcessorTest {

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `resume d'archive liste les fichiers et inclut le texte`() {
        val zip = zipOf(
            "projet/README.md" to "# Mon projet".toByteArray(),
            "projet/app/Main.kt" to "fun main() = println(\"hi\")".toByteArray(),
            "projet/logo.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0)
        )
        val summary = AttachmentProcessor.summarizeZip(ByteArrayInputStream(zip))
        assertTrue(summary.startsWith("Contenu de l'archive (3 entrée(s))"))
        assertTrue(summary.contains("projet/logo.png"))
        assertTrue(summary.contains("----- projet/README.md -----\n# Mon projet"))
        assertTrue(summary.contains("println(\"hi\")"))
        assertFalse(summary.contains("PNG"))
    }

    @Test
    fun `detection texte et binaire`() {
        assertTrue(AttachmentProcessor.isProbablyText("Élève à l'école ✓".toByteArray()))
        assertFalse(AttachmentProcessor.isProbablyText(byteArrayOf(1, 0, 2)))
        assertFalse(AttachmentProcessor.isProbablyText(byteArrayOf(0xC3.toByte(), 0x28)))
        assertTrue(AttachmentProcessor.isTextLike("application/octet-stream", "kt"))
        assertTrue(AttachmentProcessor.isArchive("application/octet-stream", "zip"))
        assertFalse(AttachmentProcessor.isTextLike("application/pdf", "pdf"))
    }

    @Test
    fun `texte long tronque`() {
        val text = AttachmentProcessor.readText(ByteArrayInputStream("a".repeat(50).toByteArray()), 10)
        assertEquals("a".repeat(10) + "\n… (tronqué)", text)
    }

    @Test
    fun `description envoyee au modele`() {
        val file = Attachment(
            "log.txt", "text/plain", 2048, AttachmentKind.TEXT, text = "erreur",
            devicePath = "/storage/emulated/0/Download/TermuxDevCenter/log.txt"
        )
        val prompt = file.toPromptText()
        assertTrue(prompt.contains("« log.txt » (text/plain, 2 Ko)"))
        assertTrue(prompt.contains("~/storage/downloads/TermuxDevCenter/log.txt"))
        assertTrue(prompt.endsWith("```\nerreur\n```"))

        val image = Attachment("capture.png", "image/png", 10, AttachmentKind.IMAGE, imageDataUrl = "data:x")
        assertTrue(image.toPromptText(imagesSupported = false).contains("image non transmise"))
    }
}
