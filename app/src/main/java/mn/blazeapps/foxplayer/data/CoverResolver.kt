package mn.blazeapps.foxplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class CoverResolver(
    private val context: Context,
    private val metadata: AudioMetadataReader = AudioMetadataReader(context),
) {

    fun resolve(
        bookId: Long,
        folderCover: Uri?,
        embeddedCoverUri: Uri?,
        chapterUris: List<Uri> = emptyList(),
    ): String? {
        val dest = File(coversDir(), "$bookId.jpg")
        folderCover?.let { uri ->
            if (copyUriToFile(uri, dest)) return dest.absolutePath
        }
        val candidates = buildList {
            if (embeddedCoverUri != null) add(embeddedCoverUri)
            addAll(chapterUris)
        }.distinct()
        for (uri in candidates) {
            if (writeEmbedded(uri, dest)) return dest.absolutePath
        }
        dest.delete()
        return null
    }

    fun deleteCover(bookId: Long) {
        File(coversDir(), "$bookId.jpg").delete()
    }

    suspend fun saveCoverFromWeb(bookId: Long, imageUrl: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dest = File(coversDir(), "$bookId.jpg")
        try {
            val url = java.net.URL(imageUrl)
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.0 (Android Audiobook Player)")
            }
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext null
            }
            val bitmap = connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: run {
                connection.disconnect()
                return@withContext null
            }
            connection.disconnect()

            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (dest.length() > 0) dest.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    private fun coversDir(): File {
        val dir = File(context.filesDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun copyUriToFile(uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun writeEmbedded(uri: Uri, dest: File): Boolean {
        val art = metadata.readEmbeddedCover(uri) ?: return false
        return try {
            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size) ?: return false
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            dest.length() > 0
        } catch (_: Exception) {
            false
        }
    }
}
