(function () {
  if (window.__whatswebHostInstalled) return;
  window.__whatswebHostInstalled = true;

  var micAllowed = false;
  var camAllowed = false;

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

  // Firefox on a phone lists the microphone and speaker with blank ids.
  // Give a blank id the name "default", and drop that stand-in before capture
  // so Firefox still opens the real microphone.
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
    return Promise.resolve(getUserMedia.call(self, prepared)).then(function (stream) {
      var audioTracks = stream && stream.getAudioTracks ? stream.getAudioTracks() : [];
      var videoTracks = stream && stream.getVideoTracks ? stream.getVideoTracks() : [];
      if (audioTracks.length) micAllowed = true;
      if (videoTracks.length) camAllowed = true;
      return stream;
    }).catch(function (error) {
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

  // After a capture succeeds, Firefox on Android still answers the permission
  // query with "prompt". WhatsApp treats that as a failed unmute and leaves
  // the call muted. Report granted once this page has opened that device.
  function patchPermissionQuery(owner) {
    if (!owner || owner.__whatswebPerm || typeof owner.query !== "function") return;
    var query = owner.query;
    function wrapped(desc) {
      var name = desc && desc.name;
      var allow = (name === "microphone" && micAllowed) || (name === "camera" && camAllowed);
      return Promise.resolve(query.apply(this, arguments)).then(function (status) {
        var raw = status && status.state;
        if (!allow || !status || raw === "granted" || raw === "denied") return status;
        return new Proxy(status, {
          get: function (target, prop) {
            if (prop === "state") return "granted";
            var value = target[prop];
            return typeof value === "function" ? value.bind(target) : value;
          }
        });
      }, function (error) {
        if (!allow) return Promise.reject(error);
        return { state: "granted", onchange: null };
      });
    }
    try {
      Object.defineProperty(owner, "query", { value: wrapped, configurable: true, writable: true });
    } catch (error) {
      try { owner.query = wrapped; } catch (ignored) { return; }
    }
    try { Object.defineProperty(owner, "__whatswebPerm", { value: true }); } catch (error) {}
  }
  if (window.Permissions) patchPermissionQuery(Permissions.prototype);
  if (navigator.permissions) patchPermissionQuery(navigator.permissions);

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

  function patchTrackSettings() {
    var proto = window.MediaStreamTrack && MediaStreamTrack.prototype;
    if (!proto || proto.__whatswebSettings || typeof proto.getSettings !== "function") return;
    var getSettings = proto.getSettings;
    var wrapped = function () {
      var settings = getSettings.call(this) || {};
      if (this.kind !== "audio" || settings.deviceId) return settings;
      var copy = {};
      for (var key in settings) {
        if (Object.prototype.hasOwnProperty.call(settings, key)) copy[key] = settings[key];
      }
      copy.deviceId = "default";
      return copy;
    };
    try {
      Object.defineProperty(proto, "getSettings", { value: wrapped, configurable: true, writable: true });
    } catch (error) {
      try { proto.getSettings = wrapped; } catch (ignored) { return; }
    }
    proto.__whatswebSettings = true;
  }
  patchTrackSettings();

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
      var message = { whatsweb: "host", payload: String(payload) };
      window.postMessage(message, window.location.origin);
    }
  };

  // The call screen is the signal. Page peer connections stay open in the
  // chat list and are not the call, so counting them leaves the controls up.
  var domCall = false;
  var lastCall = null;
  function publishCall() {
    var active = domCall;
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

  window.addEventListener("message", function (event) {
    if (event.source !== window || !event.data) return;
    if (event.data.whatsweb !== "call") return;
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
