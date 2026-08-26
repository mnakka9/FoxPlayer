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
