package app.whatsweb.web

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.webkit.WebViewFeature
import app.whatsweb.R
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saves files the companion page offers.
 * Blob bytes are read in bounded slices so the page never builds one giant data URL.
 */
class DownloadHandler(
    context: Context,
    private val messages: (String) -> Unit,
    private val requestLegacyStorage: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
) {
    lateinit var webView: WebView

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val transfers = BlobTransfer(File(appContext.cacheDir, "blob-downloads"))

    fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        if (closed.get()) return
        if (!::webView.isInitialized || !LinkRouter.isCompanionPage(webView.url)) {
            say(R.string.download_failed)
            return
        }
        when {
            url.startsWith("blob:") -> {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    say(R.string.download_failed)
                    return
                }
                webView.evaluateJavascript(blobScript(JSONObject.quote(url)), null)
            }
            url.startsWith("https://") -> enqueue(url, userAgent, contentDisposition, mimeType)
            else -> say(R.string.download_failed)
        }
    }

    fun onMessage(payload: String, reply: (String) -> Unit) {
        if (closed.get()) {
            reply("err")
            return
        }
        worker.execute {
            val status = if (closed.get()) "err" else process(payload)
            reply(status)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.shutdownNow()
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        transfers.close()
    }

    private fun process(payload: String): String {
        return when (val message = HostMessages.parse(payload)) {
            HostMessages.Message.Fail -> {
                say(R.string.download_failed)
                "err"
            }
            is HostMessages.Message.Chunk -> {
                val accepted = "${message.id}:ok"
                val rejected = "${message.id}:err"
                when (
                    val offer = transfers.offer(
                        BlobTransfer.Piece(
                            id = message.id,
                            index = message.index,
                            total = message.total,
                            mime = message.mime,
                            part = message.part,
                        ),
                        System.currentTimeMillis(),
                    )
                ) {
                    BlobTransfer.Offer.Pending -> accepted
                    BlobTransfer.Offer.Rejected -> {
                        say(R.string.download_failed)
                        rejected
                    }
                    is BlobTransfer.Offer.Ready -> {
                        val result = try {
                            offer.file.inputStream().buffered().use { input ->
                                MediaStoreSaver.save(
                                    appContext,
                                    "whatsweb-${System.currentTimeMillis()}",
                                    offer.mime,
                                    input,
                                    offer.file.length(),
                                )
                            }
                        } catch (_: Exception) {
                            MediaStoreSaver.Result.Failed
                        } finally {
                            offer.file.parentFile?.deleteRecursively()
                        }
                        say(messageFor(result))
                        if (result == MediaStoreSaver.Result.Failed || result == MediaStoreSaver.Result.TooLarge) {
                            rejected
                        } else {
                            accepted
                        }
                    }
                }
            }
            else -> "err"
        }
    }

    private fun enqueue(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val start = {
            if (!closed.get()) enqueueNow(url, userAgent, contentDisposition, mimeType)
        }
        if (Build.VERSION.SDK_INT <= 28) {
            requestLegacyStorage(start) { say(R.string.download_failed) }
        } else {
            start()
        }
    }

    private fun enqueueNow(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                MediaStoreSaver.safeName(name, mimeType ?: "application/octet-stream"),
            )
            .setMimeType(mimeType ?: "application/octet-stream")
            .addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
            request.addRequestHeader("Cookie", cookie)
        }
        val manager = appContext.getSystemService(DownloadManager::class.java)
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
        if (closed.get()) return
        val text = appContext.getString(resId)
        main.post {
            if (!closed.get()) messages(text)
        }
    }

    private fun messageFor(result: MediaStoreSaver.Result): Int {
        return when (result) {
            MediaStoreSaver.Result.PublicDownloads -> R.string.download_saved
            MediaStoreSaver.Result.AppFolder -> R.string.download_saved_private
            MediaStoreSaver.Result.TooLarge -> R.string.download_too_large
            MediaStoreSaver.Result.Failed -> R.string.download_failed
        }
    }

    private fun blobScript(quotedUrl: String): String {
        return """
            (function () {
              var host = window.WhatswebDownloads;
              if (!host || !host.postMessage) return;
              var maxBytes = ${BlobTransfer.MAX_BYTES};
              var maxChunks = ${BlobTransfer.MAX_CHUNKS};
              var sliceBytes = ${BlobTransfer.SLICE_BYTES};
              function fail() {
                host.postMessage(JSON.stringify({type:"fail"}));
              }
              fetch($quotedUrl).then(function (response) {
                return response.blob();
              }).then(function (blob) {
                if (!blob || blob.size <= 0 || blob.size > maxBytes) {
                  fail();
                  return;
                }
                var total = Math.ceil(blob.size / sliceBytes);
                if (total > maxChunks) {
                  fail();
                  return;
                }
                var id = String(Date.now()) + Math.random().toString(16).slice(2, 10);
                var mime = blob.type || "application/octet-stream";
                var index = 0;
                var stopped = false;
                var transfers = window.__whatswebTransfers || (window.__whatswebTransfers = {});
                function send() {
                  if (stopped || index >= total) return;
                  var start = index * sliceBytes;
                  var slice = blob.slice(start, Math.min(blob.size, start + sliceBytes));
                  slice.arrayBuffer().then(function (buffer) {
                    if (stopped) return;
                    var bytes = new Uint8Array(buffer);
                    var binary = "";
                    var step = 1024;
                    for (var offset = 0; offset < bytes.length; offset += step) {
                      var end = Math.min(bytes.length, offset + step);
                      binary += String.fromCharCode.apply(null, bytes.subarray(offset, end));
                    }
                    host.postMessage(JSON.stringify({
                      type: "chunk",
                      id: id,
                      index: index,
                      total: total,
                      mime: mime,
                      part: btoa(binary)
                    }));
                  }).catch(function () {
                    stopped = true;
                    delete transfers[id];
                    fail();
                  });
                }
                transfers[id] = function (status) {
                  if (stopped) return;
                  if (status !== "ok") {
                    stopped = true;
                    delete transfers[id];
                    return;
                  }
                  index += 1;
                  if (index >= total) delete transfers[id];
                  else send();
                };
                if (!host.__whatswebBound) {
                  host.__whatswebBound = true;
                  host.onmessage = function (event) {
                    var status = event && event.data !== undefined ? String(event.data) : String(event || "");
                    var split = status.indexOf(":");
                    if (split <= 0) return;
                    var callback = transfers[status.substring(0, split)];
                    if (callback) callback(status.substring(split + 1));
                  };
                }
                send();
              }).catch(fail);
            })();
        """.trimIndent()
    }
}
