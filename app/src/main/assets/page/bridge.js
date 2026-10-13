(function () {
  function parentNode() {
    return document.documentElement || document.head;
  }

  function inject(file) {
    var parent = parentNode();
    if (!parent) return;
    var script = document.createElement("script");
    script.src = browser.runtime.getURL(file);
    script.onload = function () { script.remove(); };
    parent.appendChild(script);
  }

  inject("host.js");
  inject("desktop_identity.js");
  if (window.top !== window) return;

  var port = browser.runtime.connectNative("whatsweb");

  window.addEventListener("message", function (event) {
    if (event.source !== window || !event.data || event.origin !== window.location.origin) return;
    if (event.data.whatsweb === "host") {
      port.postMessage({ host: String(event.data.payload) });
    } else if (event.data.whatsweb === "result") {
      port.postMessage({ id: event.data.id, result: String(event.data.result) });
    }
  });

  port.onMessage.addListener(function (message) {
    if (!message || !message.op) return;
    if (message.op === "layout") {
      var root = document.documentElement;
      if (!root) return;
      root.dataset.whatswebConfig = "1";
      root.dataset.whatswebPhone = message.phone ? "true" : "false";
      root.dataset.whatswebQuiet = message.quiet ? "true" : "false";
      root.dataset.whatswebHideAi = message.hideAi ? "true" : "false";
      root.dataset.whatswebHideCalls = message.hideCalls ? "true" : "false";
      root.dataset.whatswebTabChats = message.tabChats || "";
      root.dataset.whatswebTabUpdates = message.tabUpdates || "";
      root.dataset.whatswebTabCalls = message.tabCalls || "";
      if (message.cameraFlip != null) {
        var cameraFlip = message.cameraFlip ? "1" : "0";
        root.dataset.whatswebCameraFlip = cameraFlip;
        try { sessionStorage.setItem("whatswebCameraFlip", cameraFlip); } catch (error) {}
      }
      inject("phone_layout.js");
      return;
    }
    window.postMessage({
      whatsweb: "call",
      op: message.op,
      id: message.id,
      value: message.value
    }, "*");
  });
  port.postMessage({ ready: true });
})();
