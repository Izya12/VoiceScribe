package com.example.data.repository

import android.content.Context
import com.example.data.model.VoiceModelConfig
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Рефакторинг загрузчика моделей с поддержкой мульти-файловых моделей (GigaAM v3 / Sherpa-ONNX),
 * плавной эмуляцией прогресса скачивания в оффлайн-среде и транзакционной откатки при сбоях.
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
    }

    /**
     * Скачивание всей группы файлов для заданной модели.
     * Возвращает директорию со всеми скачанными файлами.
     */
    suspend fun downloadModelGroup(
        config: VoiceModelConfig,
        onProgress: suspend (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "models/${config.id}").apply {
            if (!exists()) mkdirs()
        }

        val totalFiles = config.downloadUrls.size
        val downloadedFiles = mutableListOf<File>()

        AppLogger.i(TAG, "Старт скачивания группы из $totalFiles файлов для модели '${config.name}' (ID: ${config.id})", context)

        try {
            var totalBytesDownloadedAcrossGroup = 0L
            val fileSizes = LongArray(totalFiles) { 0L }

            config.downloadUrls.forEachIndexed { index, urlString ->
                val fileName = parseFileNameFromUrl(urlString)
                val targetFile = File(modelDir, fileName)

                AppLogger.d(TAG, "Скачивание файла [${index + 1}/$totalFiles]: $fileName из $urlString", context)

                downloadSingleFileWithProgress(
                    urlString = urlString,
                    targetFile = targetFile,
                    fileIndex = index,
                    totalFiles = totalFiles,
                    onFileProgress = { bytesRead, fileTotalBytes ->
                        fileSizes[index] = fileTotalBytes
                        val sumTotalBytes = fileSizes.sum().coerceAtLeast(config.sizeBytes.coerceAtLeast(1L))
                        val currentDownloaded = totalBytesDownloadedAcrossGroup + bytesRead
                        val groupProgress = (currentDownloaded.toFloat() / sumTotalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                        onProgress(groupProgress)
                    }
                )

                // Валидация наличия и непустого размера
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    throw IllegalStateException("Файл $fileName не существует или пуст после скачивания")
                }

                totalBytesDownloadedAcrossGroup += targetFile.length()
                downloadedFiles.add(targetFile)
            }

            onProgress(1.0f)
            AppLogger.i(TAG, "Группа файлов модели '${config.name}' успешно скачана и проверена (${downloadedFiles.size} файлов)", context)
            modelDir

        } catch (e: Exception) {
            AppLogger.e(TAG, "Сбой скачивания группы файлов модели '${config.name}'. Выполнение транзакционного отката (rollback)...", e, context)
            rollbackDownloadGroup(modelDir, downloadedFiles)
            throw IllegalStateException("Ошибка скачивания модели ${config.name}: ${e.localizedMessage}", e)
        }
    }

    private suspend fun downloadSingleFileWithProgress(
        urlString: String,
        targetFile: File,
        fileIndex: Int,
        totalFiles: Int,
        onFileProgress: suspend (bytesRead: Long, fileTotalBytes: Long) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = urlString
            var redirects = 0
            val maxRedirects = 10

            while (redirects < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "*/*")
                }
                val status = connection.responseCode
                if (status in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: throw java.io.IOException("HTTP redirect $status without Location header from $currentUrl")
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(URL(currentUrl), location).toString()
                    }
                    redirects++
                    connection.disconnect()
                } else {
                    break
                }
            }

            val finalConnection = connection ?: throw java.io.IOException("Не удалось установить соединение с $urlString")
            val status = finalConnection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP ошибка $status (${finalConnection.responseMessage}) при скачивании $urlString")
            }

            val contentType = finalConnection.contentType ?: ""
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw java.io.IOException("Получен HTML ответ вместо бинарных весов из $urlString.")
            }

            val fileTotalBytes = finalConnection.contentLengthLong.takeIf { it > 0 } ?: 1L
            var downloadedBytes = 0L

            finalConnection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        onFileProgress(downloadedBytes, fileTotalBytes)
                    }
                }
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw java.io.IOException("Скачанный файл $targetFile имеет нулевой размер")
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Сетевое скачивание файла $urlString завершилось ошибкой: ${e.message}", e, context)
            throw java.io.IOException(" Ошибка сети: не удалось скачать файл $urlString: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * ТРАНЗАКЦИОННОСТЬ: Удаляет ВСЕ ранее скачанные файлы модели при любой ошибке.
     */
    private fun rollbackDownloadGroup(modelDir: File, downloadedFiles: List<File>) {
        downloadedFiles.forEach { file ->
            if (file.exists()) {
                val deleted = file.delete()
                AppLogger.d(TAG, "Rollback: удален файл ${file.name} (success=$deleted)", context)
            }
        }
        if (modelDir.exists() && (modelDir.listFiles()?.isEmpty() == true)) {
            modelDir.delete()
        }
    }

    private fun parseFileNameFromUrl(url: String): String {
        return url.substringAfterLast('/').substringBefore('?')
    }
}

