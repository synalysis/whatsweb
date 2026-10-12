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

  // Firefox on a phone names the real mic and speaker, then leaves their ids
  // blank. WhatsApp ignores a blank id, so the unmute half of the call button
  // returns without opening the microphone. Call that device "default", and
  // drop that stand-in id before capture so Firefox still opens the real mic.
  function listedIds(value) {
    var ids = [];
    function add(item) {
      if (typeof item === "string") ids.push(item);
      else if (item && typeof item.length === "number") {
        for (var i = 0; i < item.length; i++) if (typeof item[i] === "string") ids.push(item[i]);
      }
    }
    if (typeof value === "string" || (value && typeof value.length === "number")) add(value);
    else if (value && typeof value === "object") {
      add(value.exact);
      add(value.ideal);
    }
    return ids;
  }

  function withoutDefaultMic(audio) {
    if (!audio || audio === true || typeof audio !== "object") return audio;
    var ids = listedIds(audio.deviceId);
    if (!ids.length) return audio;
    for (var i = 0; i < ids.length; i++) if (ids[i] !== "default") return audio;
    var cleaned = {};
    var kept = false;
    for (var key in audio) {
      if (key === "deviceId" || !Object.prototype.hasOwnProperty.call(audio, key)) continue;
      cleaned[key] = audio[key];
      kept = true;
    }
    return kept ? cleaned : true;
  }

  function prepareConstraints(constraints) {
    var next = portraitVideo(constraints);
    if (!next || !next.audio) return next;
    var audio = withoutDefaultMic(next.audio);
    if (audio === next.audio) return next;
    var copy = {};
    for (var key in next) {
      if (Object.prototype.hasOwnProperty.call(next, key)) copy[key] = next[key];
    }
    copy.audio = audio;
    return copy;
  }

  function requestMedia(getUserMedia, self, constraints, retried) {
    var prepared = retried ? constraints : prepareConstraints(constraints);
    return Promise.resolve(getUserMedia.call(self, prepared)).catch(function (error) {
      if (retried || !prepared || !prepared.audio || typeof prepared.audio !== "object") {
        return Promise.reject(error);
      }
      var constraint = error && error.constraint;
      var named = constraint === "deviceId" || constraint === "groupId";
      var unnamed = !constraint && (prepared.audio.deviceId || prepared.audio.groupId);
      if (!named && !unnamed) return Promise.reject(error);
      var audio = {};
      var kept = false;
      for (var key in prepared.audio) {
        if (key === "deviceId" || key === "groupId" || !Object.prototype.hasOwnProperty.call(prepared.audio, key)) continue;
        audio[key] = prepared.audio[key];
        kept = true;
      }
      var retry = {};
      for (var name in prepared) {
        if (Object.prototype.hasOwnProperty.call(prepared, name)) retry[name] = prepared[name];
      }
      retry.audio = kept ? audio : true;
      return requestMedia(getUserMedia, self, retry, true);
    });
  }

  function patchGetUserMedia(owner) {
    if (!owner || typeof owner.getUserMedia !== "function" || owner.getUserMedia.__whatswebGum) return;
    var getUserMedia = owner.getUserMedia;
    var wrapped = function (constraints) {
      return requestMedia(getUserMedia, this, constraints, false);
    };
    wrapped.__whatswebGum = true;
    try {
      Object.defineProperty(owner, "getUserMedia", { value: wrapped, configurable: true, writable: true });
    } catch (error) {
      try { owner.getUserMedia = wrapped; } catch (ignored) { return; }
    }
  }

  patchGetUserMedia(window.MediaDevices && MediaDevices.prototype);
  patchGetUserMedia(navigator.mediaDevices);

  // Firefox on a phone lists the microphone and no speakers until speaker
  // selection is switched on, and even then the ids are blank. Offer the
  // phone's own speaker when none is listed, and give a blank id a name.
  function audioDevice(kind, deviceId, label, groupId) {
    var base = window.MediaDeviceInfo && MediaDeviceInfo.prototype;
    var device = base ? Object.create(base) : {};
    function prop(name, value) {
      try {
        Object.defineProperty(device, name, { value: value, enumerable: true, configurable: true });
      } catch (error) {
        device[name] = value;
      }
    }
    prop("deviceId", deviceId);
    prop("kind", kind);
    prop("label", label || "");
    prop("groupId", groupId || "");
    device.toJSON = function () {
      return { deviceId: deviceId, kind: kind, label: label || "", groupId: groupId || "" };
    };
    return device;
  }

  function withSpeaker(list) {
    var found = list || [];
    var copy = [];
    var sawOutput = false;
    for (var i = 0; i < found.length; i++) {
      var device = found[i];
      if (device && (device.kind === "audioinput" || device.kind === "audiooutput") && !device.deviceId) {
        device = audioDevice(device.kind, "default", device.label, device.groupId);
      }
      if (device && device.kind === "audiooutput") sawOutput = true;
      copy.push(device);
    }
    if (!sawOutput) copy.push(audioDevice("audiooutput", "default", "Default speaker", "whatsweb"));
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

  // A finger on the mute half produces a touch, and WhatsApp's toggle listens
  // for a click. If that click never arrives, send one. A click that did
  // arrive cancels this, so a working toggle still runs once.
  var micTap = 0;
  function callMicButton(target) {
    if (!target || !target.closest) return null;
    if (target.closest("[aria-haspopup]")) return null;
    var button = target.closest("button, [role='button']");
    if (!button || button.closest("#whatsweb-tabbar")) return null;
    if (button.closest("[role='menu'], [role='listbox']")) return null;
    var parent = button.parentElement;
    if (!parent || !parent.querySelector("[aria-haspopup]")) return null;
    var icon = button.querySelector("[data-icon]");
    var name = (icon && icon.getAttribute("data-icon") || "").toLowerCase();
    var label = (button.getAttribute("aria-label") || "").toLowerCase();
    var titled = button.querySelector("svg title");
    var title = titled && titled.textContent ? titled.textContent.toLowerCase() : "";
    var mic = name.indexOf("mic") !== -1 || label.indexOf("mic") !== -1 || label.indexOf("mute") !== -1 ||
      title.indexOf("mic") !== -1 || title.indexOf("mute") !== -1;
    return mic ? button : null;
  }
  document.addEventListener("pointercancel", function () {
    micTap += 1;
  }, true);
  document.addEventListener("pointerup", function (event) {
    if (!event || event.pointerType === "mouse") return;
    var button = callMicButton(event.target);
    if (!button || button.disabled || button.getAttribute("aria-disabled") === "true") return;
    var token = micTap + 1;
    micTap = token;
    window.setTimeout(function () {
      if (micTap !== token) return;
      micTap += 1;
      button.dispatchEvent(new MouseEvent("click", {
        bubbles: true,
        cancelable: true,
        view: window,
        detail: 1,
        button: 0,
        clientX: event.clientX,
        clientY: event.clientY,
      }));
    }, 280);
  }, true);
  document.addEventListener("click", function (event) {
    if (callMicButton(event.target)) micTap += 1;
  }, false);

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
