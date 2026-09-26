package com.termux.devcenter.data.attachments

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

enum class AttachmentKind { IMAGE, TEXT, ARCHIVE, BINARY }

data class Attachment(
    val name: String,
    val mime: String,
    val size: Long,
    val kind: AttachmentKind,
    /** Contenu texte (fichier texte ou résumé d'archive) transmis au modèle. */
    val text: String? = null,
    /** Image redimensionnée en data URL JPEG pour les modèles multimodaux. */
    val imageDataUrl: String? = null,
    /** Copie dans Téléchargements, lisible par Termux/Omni-Exec. */
    val devicePath: String? = null
) {
    val icon: String
        get() = when (kind) {
            AttachmentKind.IMAGE -> "🖼"
            AttachmentKind.TEXT -> "📄"
            AttachmentKind.ARCHIVE -> "🗜"
            AttachmentKind.BINARY -> "📎"
        }

    val label: String get() = "$icon $name"

    /** Description textuelle ajoutée au message (l'image elle-même part dans une partie séparée). */
    fun toPromptText(imagesSupported: Boolean = true): String = buildString {
        append("\n\n")
        append(if (kind == AttachmentKind.IMAGE) "🖼 Image jointe" else "📎 Fichier joint")
        append(" : « $name » ($mime, ${formatSize(size)})")
        if (kind == AttachmentKind.IMAGE && !imagesSupported) append(" — image non transmise à ce modèle.")
        devicePath?.let {
            append("\nCopie sur le téléphone : $it")
            append("\n(dans Termux : ~/storage/downloads/TermuxDevCenter/${it.substringAfterLast('/')}, après termux-setup-storage)")
        }
        text?.let { append("\n```\n").append(it).append("\n```") }
    }

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes < 0 -> "taille inconnue"
            bytes < 1024 -> "$bytes o"
            bytes < 1024 * 1024 -> "${bytes / 1024} Ko"
            else -> String.format(java.util.Locale.FRANCE, "%.1f Mo", bytes / 1048576.0)
        }
    }
}

/** Prépare un fichier choisi par l'utilisateur pour l'envoyer à un modèle. */
class AttachmentProcessor(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun process(uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val (name, size) = queryMeta(uri)
            val ext = name.substringAfterLast('.', "").lowercase()
            val mime = resolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"

            when {
                mime.startsWith("image/") && mime != "image/svg+xml" ->
                    encodeImage(uri)?.let { Attachment(name, mime, size, AttachmentKind.IMAGE, imageDataUrl = it) }
                        ?: binary(uri, name, mime, size)
                isArchive(mime, ext) -> {
                    val summary = openStream(uri).use { summarizeZip(it) }
                    Attachment(name, mime, size, AttachmentKind.ARCHIVE, text = summary, devicePath = saveToDownloads(uri, name, mime))
                }
                size in 0..MAX_TEXT_FILE_BYTES && (isTextLike(mime, ext) || looksLikeText(uri)) ->
                    Attachment(name, mime, size, AttachmentKind.TEXT, text = openStream(uri).use { readText(it, MAX_TEXT_CHARS) })
                else -> binary(uri, name, mime, size)
            }
        }
    }

    private fun binary(uri: Uri, name: String, mime: String, size: Long) =
        Attachment(name, mime, size, AttachmentKind.BINARY, devicePath = saveToDownloads(uri, name, mime))

    private fun openStream(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: error("Impossible d'ouvrir le fichier")

    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        return (name ?: uri.lastPathSegment ?: "fichier") to size
    }

    private fun looksLikeText(uri: Uri): Boolean = runCatching {
        openStream(uri).use { input ->
            val buf = ByteArray(4096)
            val n = input.read(buf).coerceAtLeast(0)
            isProbablyText(buf.copyOf(n))
        }
    }.getOrDefault(false)

    private fun encodeImage(uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = openStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Copie dans Téléchargements/TermuxDevCenter pour que Claude puisse le traiter via Omni-Exec. */
    private fun saveToDownloads(uri: Uri, name: String, mime: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            openStream(uri).use { input ->
                (resolver.openOutputStream(target) ?: error("écriture impossible")).use { input.copyTo(it) }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            // Android peut renommer le fichier en cas de doublon : on relit le nom final.
            val finalName = resolver.query(target, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
            "/storage/emulated/0/${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_DIR/$finalName"
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            null
        }
    }

    companion object {
        const val DOWNLOAD_DIR = "TermuxDevCenter"
        private const val MAX_IMAGE_SIDE = 1568
        private const val MAX_TEXT_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_TEXT_CHARS = 100_000
        private const val MAX_ZIP_ENTRIES_LISTED = 300
        private const val MAX_ZIP_TEXT_CHARS = 80_000
        private const val MAX_ZIP_ENTRY_BYTES = 100 * 1024

        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "csv", "tsv", "log", "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "cfg",
            "conf", "properties", "env", "gradle", "kts", "kt", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
            "html", "htm", "css", "scss", "sh", "bash", "zsh", "c", "h", "cpp", "hpp", "cc", "rs", "go", "rb", "php",
            "swift", "sql", "lua", "dart", "vue", "svelte", "svg", "gitignore", "dockerfile", "makefile", "pro", "bat"
        )
        private val ARCHIVE_EXTENSIONS = setOf("zip", "jar", "apk", "aar")

        fun isArchive(mime: String, ext: String): Boolean =
            mime == "application/zip" || mime == "application/x-zip-compressed" ||
                mime == "application/vnd.android.package-archive" || ext in ARCHIVE_EXTENSIONS

        fun isTextLike(mime: String, ext: String): Boolean =
            mime.startsWith("text/") || ext in TEXT_EXTENSIONS ||
                mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh", "image/svg+xml")

        /** Pas d'octet nul et UTF-8 valide (un caractère coupé en fin de tampon est toléré). */
        fun isProbablyText(bytes: ByteArray): Boolean {
            if (bytes.isEmpty()) return true
            if (bytes.any { it == 0.toByte() }) return false
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val trimmed = if (bytes.size >= 4096) bytes.copyOf(bytes.size - 4) else bytes
            return try {
                decoder.decode(ByteBuffer.wrap(trimmed))
                true
            } catch (e: CharacterCodingException) {
                false
            }
        }

        fun readText(input: InputStream, maxChars: Int): String {
            val bytes = input.readNBytesCompat(maxChars * 4L + 4)
            val text = String(bytes, Charsets.UTF_8)
            return if (text.length > maxChars) text.take(maxChars) + "\n… (tronqué)" else text
        }

        /** Liste le contenu d'une archive ZIP et inclut les petits fichiers texte. */
        fun summarizeZip(input: InputStream): String {
            val listing = StringBuilder()
            val contents = StringBuilder()
            var count = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    if (count <= MAX_ZIP_ENTRIES_LISTED) {
                        listing.append(entry.name)
                        if (!entry.isDirectory && entry.size >= 0) listing.append(" (").append(Attachment.formatSize(entry.size)).append(')')
                        listing.append('\n')
                    }
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val base = entry.name.substringAfterLast('/').lowercase()
                    if (!entry.isDirectory && contents.length < MAX_ZIP_TEXT_CHARS &&
                        (ext in TEXT_EXTENSIONS || base in TEXT_EXTENSIONS) && entry.size in -1..MAX_ZIP_ENTRY_BYTES.toLong()
                    ) {
                        val bytes = zip.readNBytesCompat(MAX_ZIP_ENTRY_BYTES + 1L)
                        if (bytes.size <= MAX_ZIP_ENTRY_BYTES && isProbablyText(bytes)) {
                            val room = MAX_ZIP_TEXT_CHARS - contents.length
                            contents.append("\n----- ").append(entry.name).append(" -----\n")
                                .append(String(bytes, Charsets.UTF_8).take(room)).append('\n')
                        }
                    }
                }
            }
            return buildString {
                append("Contenu de l'archive ($count entrée(s)) :\n").append(listing)
                if (count > MAX_ZIP_ENTRIES_LISTED) append("… et ${count - MAX_ZIP_ENTRIES_LISTED} autre(s)\n")
                if (contents.isNotEmpty()) append("\nFichiers texte inclus :").append(contents)
                if (contents.length >= MAX_ZIP_TEXT_CHARS) append("\n… (contenu tronqué)")
            }.trimEnd()
        }

        private fun InputStream.readNBytesCompat(limit: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0L
            while (total < limit) {
                val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }
    }
}
