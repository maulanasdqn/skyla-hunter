package dev.reforge.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds a markdown recon report from a session and saves it to Downloads/Reforge. */
object ReportExporter {

    // The Markdown assembly + tool extraction live in the Rust native core
    // (skyla_core::report); this object keeps only the Android-facing timestamp
    // and file-save. The timestamp is computed here and passed in, so the core
    // stays pure/deterministic.

    fun build(title: String, messages: List<ChatMessage>): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return uniffi.skyla_core.buildReport(title, now, messages.map { it.toReport() })
    }

    fun toolsUsed(messages: List<ChatMessage>): List<String> =
        uniffi.skyla_core.toolsUsed(messages.map { it.toReport() })

    private fun ChatMessage.toReport() = uniffi.skyla_core.ReportMessage(
        when (kind) {
            MsgKind.USER -> uniffi.skyla_core.ReportKind.USER
            MsgKind.THOUGHT -> uniffi.skyla_core.ReportKind.THOUGHT
            MsgKind.COMMAND -> uniffi.skyla_core.ReportKind.COMMAND
            MsgKind.OUTPUT -> uniffi.skyla_core.ReportKind.OUTPUT
            MsgKind.ANSWER -> uniffi.skyla_core.ReportKind.ANSWER
            MsgKind.ERROR -> uniffi.skyla_core.ReportKind.ERROR
            MsgKind.RUNINFO -> uniffi.skyla_core.ReportKind.RUN_INFO
        },
        text,
    )

    /** Saves the report to Downloads/Reforge. Returns a display path, or null on failure. */
    fun save(context: Context, title: String, messages: List<ChatMessage>): String? {
        val md = build(title, messages)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val name = "reforge-report-$stamp.md"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Reforge")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(md.toByteArray()) }
                "Download/Reforge/$name"
            } else {
                val dir = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Reforge"
                ).apply { mkdirs() }
                java.io.File(dir, name).writeText(md)
                "Download/Reforge/$name"
            }
        } catch (e: Exception) {
            null
        }
    }
}
