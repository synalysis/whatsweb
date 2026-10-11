package app.whatsweb.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import app.whatsweb.R
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Saves files the companion page offers.
 * Blob URLs are read in the page and handed over in pieces, because a single
 * JavascriptInterface argument cannot cross the Binder size limit.
 */
class DownloadHandler(
    private val context: Context,
    private val messages: (String) -> Unit,
) {
    lateinit var webView: WebView

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val incoming = HashMap<String, Array<String?>>()

    fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        when {
            url.startsWith("blob:") -> {
                if (!LinkRouter.isCompanionPage(webView.url)) return
                webView.evaluateJavascript(blobScript(JSONObject.quote(url)), null)
            }
            url.startsWith("https://") || url.startsWith("http://") -> {
                enqueue(url, userAgent, contentDisposition, mimeType)
            }
            else -> say(R.string.download_failed)
        }
    }

    @JavascriptInterface
    fun chunk(id: String, index: Int, total: Int, mime: String, part: String) {
        if (id.length > 64 || total !in 1..MAX_CHUNKS || index !in 0 until total) return
        if (part.length > CHUNK_CHARS) return
        val complete = synchronized(incoming) {
            val parts = incoming.getOrPut(id) { arrayOfNulls(total) }
            if (parts.size != total) return
            parts[index] = part
            if (parts.any { it == null }) {
                null
            } else {
                incoming.remove(id)
                parts.joinToString(separator = "")
            }
        } ?: return
        main.post {
            if (!LinkRouter.isCompanionPage(webView.url)) return@post
            executor.execute { savePayload(mime, complete) }
        }
    }

    @JavascriptInterface
    fun fail() {
        say(R.string.download_failed)
    }

    private fun savePayload(mime: String, payload: String) {
        if (payload.length > MAX_CHUNKS * CHUNK_CHARS) {
            say(R.string.download_too_large)
            return
        }
        val bytes = try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            say(R.string.download_failed)
            return
        }
        val name = "whatsweb-${System.currentTimeMillis()}"
        val result = MediaStoreSaver.save(context, name, mime, bytes)
        say(messageFor(result))
    }

    private fun enqueue(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, MediaStoreSaver.safeName(name, mimeType ?: "application/octet-stream"))
            .setMimeType(mimeType ?: "application/octet-stream")
            .addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
            request.addRequestHeader("Cookie", cookie)
        }
        val manager = context.getSystemService(android.app.DownloadManager::class.java)
        if (manager == null) {
            say(R.string.download_failed)
            return
        }
        try {
            manager.enqueue(request)
            say(R.string.download_started)
        } catch (_: Exception) {
            say(R.string.download_failed)
        }
    }

    private fun say(resId: Int) {
        val text = context.getString(resId)
        main.post { messages(text) }
    }

    private fun messageFor(result: MediaStoreSaver.Result): Int {
        return when (result) {
            MediaStoreSaver.Result.PublicDownloads -> R.string.download_saved
            MediaStoreSaver.Result.AppFolder -> R.string.download_saved_private
            MediaStoreSaver.Result.Failed -> R.string.download_failed
        }
    }

    private fun blobScript(quotedUrl: String): String {
        return """
            (function () {
              fetch($quotedUrl).then(function (response) {
                return response.blob();
              }).then(function (blob) {
                var reader = new FileReader();
                reader.onloadend = function () {
                  var result = String(reader.result || "");
                  var comma = result.indexOf(",");
                  var payload = comma >= 0 ? result.substring(comma + 1) : "";
                  var size = 400000;
                  var total = Math.max(1, Math.ceil(payload.length / size));
                  if (total > $MAX_CHUNKS) {
                    WhatswebDownloads.fail();
                    return;
                  }
                  var id = String(Date.now());
                  var mime = blob.type || "application/octet-stream";
                  for (var i = 0; i < total; i++) {
                    WhatswebDownloads.chunk(id, i, total, mime, payload.substring(i * size, (i + 1) * size));
                  }
                };
                reader.readAsDataURL(blob);
              }).catch(function () {
                WhatswebDownloads.fail();
              });
            })();
        """.trimIndent()
    }

    private companion object {
        const val CHUNK_CHARS = 500_000
        const val MAX_CHUNKS = 160
    }
}
