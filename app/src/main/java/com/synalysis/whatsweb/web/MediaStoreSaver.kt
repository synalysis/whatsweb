package com.synalysis.whatsweb.web

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object MediaStoreSaver {
    sealed interface Result {
        data object PublicDownloads : Result
        data object AppFolder : Result
        data object TooLarge : Result
        data object Failed : Result
    }

    fun save(
        context: Context,
        displayName: String,
        mimeType: String,
        source: InputStream,
        maxBytes: Long,
    ): Result {
        if (maxBytes < 0) return Result.Failed
        val name = safeName(displayName, mimeType)
        val mime = mimeType.substringBefore(';').trim().ifBlank { "application/octet-stream" }
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                savePublic(context, name, mime, source, maxBytes)
            } else {
                saveLegacy(context, name, source, maxBytes)
            }
        } catch (_: ByteStreams.LimitExceeded) {
            Result.TooLarge
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
    private fun savePublic(
        context: Context,
        name: String,
        mime: String,
        source: InputStream,
        maxBytes: Long,
    ): Result {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return Result.Failed
        return try {
            val copied = context.contentResolver.openOutputStream(uri)?.use { output ->
                ByteStreams.copy(source, output, maxBytes)
            } ?: return deleteAnd(context, uri, Result.Failed)
            if (copied <= 0L) return deleteAnd(context, uri, Result.Failed)
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            Result.PublicDownloads
        } catch (limit: ByteStreams.LimitExceeded) {
            deleteQuietly(context, uri)
            throw limit
        } catch (_: Exception) {
            deleteQuietly(context, uri)
            Result.Failed
        }
    }

    private fun saveLegacy(
        context: Context,
        name: String,
        source: InputStream,
        maxBytes: Long,
    ): Result {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (publicDir != null && publicDir.canWrite() && (publicDir.isDirectory || publicDir.mkdirs())) {
            val dest = File(publicDir, name)
            try {
                val copied = FileOutputStream(dest).use { output ->
                    ByteStreams.copy(source, output, maxBytes)
                }
                if (copied <= 0L) {
                    dest.delete()
                    return Result.Failed
                }
                return Result.PublicDownloads
            } catch (limit: ByteStreams.LimitExceeded) {
                dest.delete()
                throw limit
            } catch (_: Exception) {
                dest.delete()
                return Result.Failed
            }
        }
        val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return Result.Failed
        if (!privateDir.isDirectory && !privateDir.mkdirs()) return Result.Failed
        val dest = File(privateDir, name)
        return try {
            val copied = FileOutputStream(dest).use { output ->
                ByteStreams.copy(source, output, maxBytes)
            }
            if (copied <= 0L) {
                dest.delete()
                Result.Failed
            } else {
                Result.AppFolder
            }
        } catch (limit: ByteStreams.LimitExceeded) {
            dest.delete()
            throw limit
        } catch (_: Exception) {
            dest.delete()
            Result.Failed
        }
    }

    private fun deleteAnd(context: Context, uri: android.net.Uri, result: Result): Result {
        deleteQuietly(context, uri)
        return result
    }

    private fun deleteQuietly(context: Context, uri: android.net.Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }
}
