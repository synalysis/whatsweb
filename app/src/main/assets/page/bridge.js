(function () {
  function inject(file) {
    var parent = document.documentElement || document.head;
    if (!parent) return;
    var script = document.createElement("script");
    script.src = browser.runtime.getURL(file);
    script.onload = function () { script.remove(); };
    parent.appendChild(script);
  }

  // Page-world copies. The manifest also requests the main world; this still
  // runs if that request is ignored, and a second run is harmless.
  inject("host.js");
  inject("desktop_identity.js");

  var port = browser.runtime.connectNative("whatsweb");

  window.addEventListener("message", function (event) {
    if (event.source !== window || !event.data) return;
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
