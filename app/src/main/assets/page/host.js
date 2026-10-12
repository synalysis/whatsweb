(function () {
  if (window.__whatswebHostInstalled) return;
  window.__whatswebHostInstalled = true;

  // WhatsApp Web asks a desktop camera for a landscape frame. On a portrait
  // phone that comes out sideways. Ask for the tall frame instead.
  function longEdge(value) {
    if (typeof value === "number") return value;
    if (!value || typeof value !== "object") return null;
    if (typeof value.ideal === "number") return value.ideal;
    if (typeof value.exact === "number") return value.exact;
    if (typeof value.max === "number") return value.max;
    if (typeof value.min === "number") return value.min;
    return null;
  }

  function portraitVideo(constraints) {
    if (!constraints || !constraints.video || window.innerHeight <= window.innerWidth) return constraints;
    var video = constraints.video;
    if (video === true) {
      return Object.assign({}, constraints, {
        video: { facingMode: "user", width: { ideal: 720 }, height: { ideal: 1280 } },
      });
    }
    if (typeof video !== "object" || video.mediaSource || video.chromeMediaSource || video.displaySurface) {
      return constraints;
    }
    var next = Object.assign({}, video);
    var width = longEdge(video.width);
    var height = longEdge(video.height);
    if (width !== null && height !== null && width > height) {
      next.width = video.height;
      next.height = video.width;
    } else if (width === null && height === null) {
      next.width = { ideal: 720 };
      next.height = { ideal: 1280 };
    }
    var ratio = video.aspectRatio;
    if (typeof ratio === "number" && ratio > 1) next.aspectRatio = 1 / ratio;
    else if (ratio && typeof ratio === "object" && typeof ratio.ideal === "number" && ratio.ideal > 1) {
      next.aspectRatio = Object.assign({}, ratio, { ideal: 1 / ratio.ideal });
    }
    return Object.assign({}, constraints, { video: next });
  }

  var devices = navigator.mediaDevices;
  if (devices && !devices.__whatswebPortrait && typeof devices.getUserMedia === "function") {
    var getUserMedia = devices.getUserMedia.bind(devices);
    devices.getUserMedia = function (constraints) {
      return getUserMedia(portraitVideo(constraints));
    };
    devices.__whatswebPortrait = true;
  }

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
