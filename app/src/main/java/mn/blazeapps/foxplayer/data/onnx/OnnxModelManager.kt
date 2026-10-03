package mn.blazeapps.foxplayer.data.onnx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface ModelDownloadState {
    object Idle : ModelDownloadState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState
    object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}

class OnnxModelManager(private val context: Context) {

    companion object {
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/onnx-community/SmolLM2-360M-Instruct-ONNX/resolve/main/onnx/model_q4f16.onnx"
        private const val MODEL_SUBDIR = "models/smollm2_360m"
        private const val MODEL_FILENAME = "model.onnx"
    }

    private val _downloadState = MutableStateFlow<ModelDownloadState>(
        if (isModelDownloaded()) ModelDownloadState.Ready else ModelDownloadState.Idle
    )
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private fun modelDir(): File {
        // Prefer app-specific external storage (no permissions needed) with internal fallback
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, MODEL_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(): File {
        return File(modelDir(), MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        // Quantized SmolLM2-360M is ~270 MB
        return file.exists() && file.length() > 50 * 1024 * 1024
    }

    fun getModelSizeBytes(): Long {
        val file = getModelFile()
        return if (file.exists()) file.length() else 0L
    }

    fun getFreeSpaceBytes(): Long {
        return modelDir().usableSpace
    }

    fun deleteModel(): Boolean {
        val file = getModelFile()
        val deleted = if (file.exists()) file.delete() else true
        if (deleted) {
            _downloadState.value = ModelDownloadState.Idle
        }
        return deleted
    }

    suspend fun downloadModel(urlStr: String = DEFAULT_MODEL_URL): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getModelFile()
        val targetDir = modelDir()
        val tempFile = File(targetDir, "$MODEL_FILENAME.tmp")

        try {
            _downloadState.value = ModelDownloadState.Downloading(0f, 0L, 0L)

            // Check storage space
            val freeBytes = targetDir.usableSpace
            if (freeBytes > 0 && freeBytes < 350L * 1024 * 1024) {
                _downloadState.value = ModelDownloadState.Error("Insufficient storage: ${freeBytes / (1024 * 1024)}MB free, ~350MB needed")
                return@withContext false
            }

            // Follow HTTP redirects across hosts (Hugging Face redirects to AWS/Cloudfront CDN)
            var currentUrl = urlStr
            var redirectCount = 0
            val maxRedirects = 10
            var connection: HttpURLConnection? = null

            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; FoxPlayer/1.1.0)")
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        throw java.io.IOException("HTTP $code redirect without Location header")
                    }
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(url, location).toString()
                    }
                    redirectCount++
                } else if (code in 200..299) {
                    connection = conn
                    break
                } else {
                    conn.disconnect()
                    throw java.io.IOException("HTTP Error $code: ${conn.responseMessage}")
                }
            }

            val finalConnection = connection ?: throw java.io.IOException("Failed to connect after $redirectCount redirects")

            val totalBytes = finalConnection.contentLengthLong.takeIf { it > 0 } ?: (272L * 1024 * 1024)
            var bytesRead = 0L

            finalConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastUpdate = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250) {
                            val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                            _downloadState.value = ModelDownloadState.Downloading(progress, bytesRead, totalBytes)
                            lastUpdate = now
                        }
                    }
                    output.flush()
                }
            }
            finalConnection.disconnect()

            if (tempFile.length() < 1024 * 1024) {
                tempFile.delete()
                _downloadState.value = ModelDownloadState.Error("Downloaded file too small, connection may have been interrupted")
                return@withContext false
            }

            // Move temp file to target file safely
            if (targetFile.exists()) targetFile.delete()
            val moved = try {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                true
            } catch (_: Exception) {
                tempFile.renameTo(targetFile)
            }

            if (moved && targetFile.exists() && targetFile.length() > 0) {
                _downloadState.value = ModelDownloadState.Ready
                true
            } else {
                _downloadState.value = ModelDownloadState.Error("Failed to save model to storage")
                false
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            _downloadState.value = ModelDownloadState.Error(e.message ?: "Download failed")
            false
        }
    }
}
