(function () {
  if (window.__whatswebHostInstalled) return;
  window.__whatswebHostInstalled = true;

  window.WhatswebHost = {
    postMessage: function (payload) {
      window.postMessage({ whatsweb: "host", payload: String(payload) }, window.location.origin);
    }
  };

  window.addEventListener("message", function (event) {
    if (event.source !== window || !event.data || event.data.whatsweb !== "call") return;
    var data = event.data;
    var value = null;
    try {
      if (data.op === "setPhone" && window.whatswebSetPhone) window.whatswebSetPhone(!!data.value);
      else if (data.op === "setHideAi" && window.whatswebSetHideAi) window.whatswebSetHideAi(!!data.value);
      else if (data.op === "setQuiet" && window.whatswebSetQuiet) window.whatswebSetQuiet(!!data.value);
      else if (data.op === "back") value = window.whatswebBack ? window.whatswebBack() : false;
    } catch (error) {}
    var encoded = "null";
    try {
      encoded = JSON.stringify(value === undefined ? null : value);
    } catch (error) {}
    window.postMessage({ whatsweb: "result", id: data.id, result: encoded }, window.location.origin);
  });
})();
