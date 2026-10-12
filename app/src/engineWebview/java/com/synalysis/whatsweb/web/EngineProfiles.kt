package com.synalysis.whatsweb.web

import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.synalysis.whatsweb.prefs.LinkRecords
import java.io.File

/**
 * One system-WebView data directory per WhatsApp account. The original
 * account keeps the unsuffixed directory, so an update does not sign it out.
 * [WebView.setDataDirectorySuffix] has to run before any WebView exists in
 * the process, and it cannot be changed afterward.
 */
object EngineProfiles {
    var installed: String? = null
        private set

    fun canSeparate(): Boolean = Build.VERSION.SDK_INT >= 28

    fun install(context: Context, id: String) {
        if (installed != null) return
        if (!LinkRecords.isId(id)) return
        // Before Android 9 every account would share one directory. Refuse that.
        if (id != LinkRecords.MAIN && !canSeparate()) return
        if (id != LinkRecords.MAIN) WebView.setDataDirectorySuffix(id)
        installed = id
    }

    fun delete(context: Context, id: String): Boolean {
        if (!LinkRecords.isId(id)) return false
        val name = if (id == LinkRecords.MAIN) "app_webview" else "app_webview_$id"
        return deleteInside(context.dataDir, File(context.dataDir, name))
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
