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

  // Firefox on a phone lists the microphone and no speakers. WhatsApp Web then
  // leaves the call muted, because the unmute control has no output to use.
  // The phone already plays through its own speaker, so offer that as the default.
  function defaultSpeaker() {
    var base = window.MediaDeviceInfo && MediaDeviceInfo.prototype;
    var device = base ? Object.create(base) : {};
    function prop(name, value) {
      try {
        Object.defineProperty(device, name, { value: value, enumerable: true, configurable: true });
      } catch (error) {
        device[name] = value;
      }
    }
    prop("deviceId", "default");
    prop("kind", "audiooutput");
    prop("label", "Default speaker");
    prop("groupId", "whatsweb");
    device.toJSON = function () {
      return { deviceId: "default", kind: "audiooutput", label: "Default speaker", groupId: "whatsweb" };
    };
    return device;
  }

  function withSpeaker(list) {
    var found = list || [];
    for (var i = 0; i < found.length; i++) {
      if (found[i] && found[i].kind === "audiooutput") return found;
    }
    var copy = [];
    for (var j = 0; j < found.length; j++) copy.push(found[j]);
    copy.push(defaultSpeaker());
    return copy;
  }

  function patchEnumerate(owner) {
    if (!owner || owner.__whatswebSpeakers || typeof owner.enumerateDevices !== "function") return;
    var enumerateDevices = owner.enumerateDevices;
    var wrapped = function () {
      return Promise.resolve(enumerateDevices.apply(this, arguments)).then(withSpeaker);
    };
    try {
      Object.defineProperty(owner, "enumerateDevices", { value: wrapped, configurable: true, writable: true });
    } catch (error) {
      try { owner.enumerateDevices = wrapped; } catch (ignored) { return; }
    }
    if (owner.enumerateDevices !== wrapped) return;
    try {
      Object.defineProperty(owner, "__whatswebSpeakers", { value: true });
    } catch (error) {
      owner.__whatswebSpeakers = true;
    }
  }

  patchEnumerate(window.MediaDevices && MediaDevices.prototype);
  patchEnumerate(navigator.mediaDevices);

  var mediaElement = window.HTMLMediaElement && HTMLMediaElement.prototype;
  if (mediaElement && !mediaElement.__whatswebSink) {
    var setSinkId = mediaElement.setSinkId;
    var assignSink = function (sinkId) {
      if (typeof setSinkId !== "function") return Promise.resolve();
      var accepted = sinkId === "default" || sinkId === "";
      try {
        return Promise.resolve(setSinkId.call(this, sinkId)).catch(function (error) {
          if (accepted) return undefined;
          return Promise.reject(error);
        });
      } catch (error) {
        if (accepted) return Promise.resolve();
        return Promise.reject(error);
      }
    };
    try {
      Object.defineProperty(mediaElement, "setSinkId", { value: assignSink, configurable: true, writable: true });
    } catch (error) {
      mediaElement.setSinkId = assignSink;
    }
    mediaElement.__whatswebSink = true;
  }

  window.WhatswebHost = {
    postMessage: function (payload) {
      window.postMessage({ whatsweb: "host", payload: String(payload) }, window.location.origin);
    }
  };

  // A live call, including one whose window is tucked away, should keep the
  // app lock from covering the page when the phone is switched away and back.
  var rtcLive = 0;
  var domCall = false;
  var lastCall = null;
  function publishCall() {
    var active = rtcLive > 0 || domCall;
    if (active === lastCall) return;
    lastCall = active;
    var host = window.WhatswebHost;
    if (!host || !host.postMessage) return;
    host.postMessage(JSON.stringify({ type: "call", active: active }));
  }

  window.__whatswebSetDomCall = function (active) {
    domCall = !!active;
    publishCall();
  };

  var OrigPeer = window.RTCPeerConnection;
  if (OrigPeer && !OrigPeer.__whatswebCall) {
    function Peer(config) {
      var pc = new OrigPeer(config);
      rtcLive += 1;
      publishCall();
      var counted = true;
      pc.addEventListener("connectionstatechange", function () {
        var state = pc.connectionState;
        if (!counted || (state !== "closed" && state !== "failed")) return;
        counted = false;
        rtcLive -= 1;
        if (rtcLive < 0) rtcLive = 0;
        publishCall();
      });
      return pc;
    }
    Peer.prototype = OrigPeer.prototype;
    if (typeof OrigPeer.generateCertificate === "function") {
      Peer.generateCertificate = function () {
        return OrigPeer.generateCertificate.apply(OrigPeer, arguments);
      };
    }
    try {
      Object.defineProperty(Peer, "__whatswebCall", { value: true });
    } catch (error) {}
    window.RTCPeerConnection = Peer;
  }

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
