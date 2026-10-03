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
            "https://huggingface.co/onnx-community/SmolLM-135M-Instruct/resolve/main/onnx/model_q4f16.onnx"
        private const val MODEL_SUBDIR = "models/smollm_135m"
        private const val MODEL_FILENAME = "model.onnx"
    }

    private val _downloadState = MutableStateFlow<ModelDownloadState>(
        if (isModelDownloaded()) ModelDownloadState.Ready else ModelDownloadState.Idle
    )
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private fun modelDir(): File {
        val dir = File(context.filesDir, MODEL_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(): File {
        return File(modelDir(), MODEL_FILENAME)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        // INT4 quantized SmolLM-135M is ~70-85 MB
        return file.exists() && file.length() > 10 * 1024 * 1024
    }

    fun getModelSizeBytes(): Long {
        val file = getModelFile()
        return if (file.exists()) file.length() else 0L
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
        val tempFile = File(modelDir(), "$MODEL_FILENAME.tmp")

        try {
            _downloadState.value = ModelDownloadState.Downloading(0f, 0L, 0L)

            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.0 (Android Audiobook Player; ONNX Model Downloader)")
            }

            if (connection.responseCode !in 200..299) {
                _downloadState.value = ModelDownloadState.Error("HTTP Error: ${connection.responseCode} ${connection.responseMessage}")
                connection.disconnect()
                return@withContext false
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: (78L * 1024 * 1024)
            var bytesRead = 0L

            connection.inputStream.use { input ->
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
                }
            }
            connection.disconnect()

            if (tempFile.length() < 1024 * 1024) {
                tempFile.delete()
                _downloadState.value = ModelDownloadState.Error("Downloaded file too small, check connection or URL")
                return@withContext false
            }

            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            if (renamed) {
                _downloadState.value = ModelDownloadState.Ready
                true
            } else {
                _downloadState.value = ModelDownloadState.Error("Failed to rename temporary model file")
                false
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            _downloadState.value = ModelDownloadState.Error(e.message ?: "Download failed")
            false
        }
    }
}
