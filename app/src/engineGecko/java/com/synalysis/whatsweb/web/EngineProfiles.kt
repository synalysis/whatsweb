package com.synalysis.whatsweb.web

import android.content.Context
import com.synalysis.whatsweb.prefs.LinkRecords
import java.io.File

/**
 * One Gecko profile per WhatsApp account. The original account keeps
 * GeckoView's default profile. Extra accounts pass `-profile` when the
 * runtime is created, which happens once per process.
 */
object EngineProfiles {
    var installed: String? = null
        private set

    fun canSeparate(): Boolean = true

    fun install(context: Context, id: String) {
        if (installed != null) return
        if (!LinkRecords.isId(id)) return
        installed = id
    }

    fun profileArguments(context: Context): Array<String>? {
        val id = installed ?: return null
        if (id == LinkRecords.MAIN) return null
        val dir = File(context.filesDir, "gecko-profiles/$id")
        dir.mkdirs()
        return arrayOf("-profile", dir.absolutePath)
    }

    fun delete(context: Context, id: String): Boolean {
        if (!LinkRecords.isId(id)) return false
        return if (id == LinkRecords.MAIN) {
            deleteInside(context.filesDir, File(context.filesDir, "mozilla")) &&
                deleteInside(context.dataDir, File(context.dataDir, "app_mozilla"))
        } else {
            deleteInside(context.filesDir, File(context.filesDir, "gecko-profiles/$id"))
        }
    }

    private fun deleteInside(root: File, target: File): Boolean {
        val base = try {
            root.canonicalFile
        } catch (_: Exception) {
            return false
        }
        val dir = try {
            target.canonicalFile
        } catch (_: Exception) {
            return false
        }
        if (!dir.path.startsWith(base.path + File.separator)) return false
        if (!dir.exists()) return true
        return if (dir.isDirectory) dir.deleteRecursively() else dir.delete()
    }
}
