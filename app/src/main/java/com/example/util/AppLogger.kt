package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Автономный тредобезопасный логгер приложения VoiceScribe AI.
 * Пишет логи одновременно в Logcat и локальный файл app_debug.log с поддержкой экспорта через FileProvider.
 */
object AppLogger {
    private const val LOG_FILE_NAME = "app_debug.log"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    @Volatile
    var isDebugEnabled: Boolean = true

    fun getLogFile(context: Context): File {
        return File(context.cacheDir, LOG_FILE_NAME)
    }

    @Synchronized
    fun i(tag: String, message: String, context: Context? = null) {
        log("INFO", tag, message, context)
    }

    @Synchronized
    fun d(tag: String, message: String, context: Context? = null) {
        log("DEBUG", tag, message, context)
    }

    @Synchronized
    fun w(tag: String, message: String, context: Context? = null) {
        log("WARN", tag, message, context)
    }

    @Synchronized
    fun e(tag: String, message: String, throwable: Throwable? = null, context: Context? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log("ERROR", tag, fullMessage, context)
    }

    private fun log(level: String, tag: String, message: String, context: Context?) {
        when (level) {
            "INFO" -> Log.i(tag, message)
            "DEBUG" -> Log.d(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message)
        }

        if (!isDebugEnabled) return

        val ctx = context ?: return
        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp [$level] [$tag] $message\n"
            val file = getLogFile(ctx)
            file.appendText(logLine)
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to write log line to file", e)
        }
    }

    fun getLogSizeFormatted(context: Context): String {
        val file = getLogFile(context)
        if (!file.exists()) return "0 KB"
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.writeText("")
            }
            i("AppLogger", "Файл логов очищен по требованию пользователя", context)
        } catch (e: Exception) {
            Log.e("AppLogger", "Ошибка при очистке логов", e)
        }
    }

    fun exportLogs(context: Context) {
        try {
            val file = getLogFile(context)
            if (!file.exists() || file.length() == 0L) {
                i("AppLogger", "=== Новая отладочная сессия VoiceScribe AI ===", context)
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "VoiceScribe AI - Debug Logs")
                putExtra(Intent.EXTRA_TEXT, "Логи автономной отладки приложения VoiceScribe AI")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Экспорт логов диагностики").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("AppLogger", "Ошибка при запуске экспорта логов через FileProvider", e)
        }
    }
}
