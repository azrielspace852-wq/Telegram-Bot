package com.offlineai.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.offlineai.app.data.Attachment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {

    private val TEXT_EXTENSIONS = setOf("txt", "md", "json", "jsonl", "csv", "log", "xml", "html", "htm", "yaml", "yml")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov")

    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                getFileName(context, uri).substringAfterLast('.', "")
            ) ?: "application/octet-stream"
    }

    fun isSupported(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS ||
                ext in IMAGE_EXTENSIONS ||
                ext in AUDIO_EXTENSIONS ||
                ext in VIDEO_EXTENSIONS ||
                ext == "pdf" ||
                ext == "zip"
    }

    /**
     * Process attachment: extract text from supported formats.
     * For ZIP: recursively list + extract text from text files inside.
     */
    suspend fun processAttachment(context: Context, uri: Uri): Attachment {
        val name = getFileName(context, uri)
        val size = getFileSize(context, uri)
        val mime = getMimeType(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()

        val isImage = ext in IMAGE_EXTENSIONS
        val isAudio = ext in AUDIO_EXTENSIONS
        val isVideo = ext in VIDEO_EXTENSIONS
        val isZip = ext == "zip"

        var extracted: String? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when {
                    ext in TEXT_EXTENSIONS -> {
                        extracted = input.bufferedReader().use { it.readText() }
                    }
                    ext == "pdf" -> {
                        PDFBoxResourceLoader.init(context)
                        PDDocument.load(input).use { doc ->
                            val stripper = PDFTextStripper()
                            extracted = stripper.getText(doc)
                        }
                    }
                    isZip -> {
                        extracted = extractZipContent(input)
                    }
                    // images / audio / video: leave to model (multimodal)
                }
            }
        } catch (e: Exception) {
            extracted = "[Gagal membaca file: ${e.message}]"
        }

        return Attachment(
            uri = uri.toString(),
            name = name,
            mimeType = mime,
            sizeBytes = size,
            extractedText = extracted,
            isImage = isImage,
            isAudio = isAudio,
            isVideo = isVideo,
            isZip = isZip
        )
    }

    private fun extractZipContent(input: InputStream): String {
        val sb = StringBuilder()
        sb.appendLine("=== ISI ZIP ===")
        ZipArchiveInputStream(BufferedInputStream(input)).use { zis ->
            var entry: ZipArchiveEntry? = zis.nextEntry
            var count = 0
            while (entry != null && count < 50) { // limit 50 files
                if (!entry.isDirectory) {
                    val name = entry.name
                    val ext = name.substringAfterLast('.', "").lowercase()
                    sb.appendLine("📄 $name (${entry.size} bytes)")
                    if (ext in TEXT_EXTENSIONS && entry.size < 512_000) { // max 512KB text
                        try {
                            val text = zis.bufferedReader().readText()
                            sb.appendLine("---")
                            sb.appendLine(text.take(4000))
                            if (text.length > 4000) sb.appendLine("... (truncated)")
                            sb.appendLine("---")
                        } catch (_: Exception) {
                            sb.appendLine("[tidak bisa dibaca sebagai teks]")
                        }
                    }
                    count++
                }
                entry = zis.nextEntry
            }
            if (count >= 50) sb.appendLine("... (terlalu banyak file, dibatasi 50)")
        }
        return sb.toString()
    }

    /** Generate ZIP from list of (filename, content) pairs. Returns File in cache. */
    fun createZip(context: Context, files: Map<String, String>, zipName: String = "ai_output.zip"): File {
        val outFile = File(context.cacheDir, zipName)
        ZipArchiveOutputStream(FileOutputStream(outFile)).use { zos ->
            files.forEach { (name, content) ->
                val entry = ZipArchiveEntry(name)
                zos.putArchiveEntry(entry)
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeArchiveEntry()
            }
        }
        return outFile
    }

    fun copyUriToFile(context: Context, uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
