package app.whatsweb.web

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream

object MediaStoreSaver {
    sealed interface Result {
        data object PublicDownloads : Result
        data object AppFolder : Result
        data object Failed : Result
    }

    fun save(context: Context, displayName: String, mimeType: String, bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Failed
        val name = safeName(displayName, mimeType)
        val mime = mimeType.substringBefore(';').trim().ifBlank { "application/octet-stream" }
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                savePublic(context, name, mime, bytes)
            } else {
                saveLegacy(context, name, bytes)
            }
        } catch (_: Exception) {
            Result.Failed
        }
    }

    fun safeName(raw: String, mimeType: String): String {
        val cleaned = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .trim('_', '.')
            .take(80)
        val base = cleaned.ifBlank { "whatsweb-${System.currentTimeMillis()}" }
        if ('.' in base.trimStart('.')) return base
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType.substringBefore(';').trim())
            ?: "bin"
        return "$base.$extension"
    }

    @RequiresApi(29)
    private fun savePublic(context: Context, name: String, mime: String, bytes: ByteArray): Result {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return Result.Failed
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return Result.Failed
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return Result.PublicDownloads
    }

    private fun saveLegacy(context: Context, name: String, bytes: ByteArray): Result {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val wrotePublic = try {
            if (publicDir != null && (publicDir.isDirectory || publicDir.mkdirs())) {
                FileOutputStream(File(publicDir, name)).use { it.write(bytes) }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
        if (wrotePublic) return Result.PublicDownloads
        val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return Result.Failed
        if (!privateDir.isDirectory && !privateDir.mkdirs()) return Result.Failed
        FileOutputStream(File(privateDir, name)).use { it.write(bytes) }
        return Result.AppFolder
    }
}
