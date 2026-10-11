// Android WebView never becomes cross-origin isolated, so SharedArrayBuffer is missing
// and WhatsApp disables the chat call menu. The constructor only has to exist for that
// check; calling itself still needs the real buffer, which WebView cannot provide.
if (typeof SharedArrayBuffer !== "function") {
  window.SharedArrayBuffer = function SharedArrayBuffer(length) {
    return new ArrayBuffer(length >>> 0);
  };
}
(function () {
  var version = "__CHROME_VERSION__";
  var major = version.split(".")[0];
  try {
    Object.defineProperty(navigator, "platform", {
      get: function () { return "Win32"; }
    });
  } catch (error) {}
  try {
    Object.defineProperty(navigator, "maxTouchPoints", {
      get: function () { return 0; }
    });
  } catch (error) {}
  try {
    var brands = [
      { brand: "Chromium", version: major },
      { brand: "Google Chrome", version: major },
      { brand: "Not_A Brand", version: "24" }
    ];
    var data = {
      brands: brands,
      mobile: false,
      platform: "Windows",
      getHighEntropyValues: function () {
        return Promise.resolve({
          architecture: "x86",
          bitness: "64",
          brands: brands,
          mobile: false,
          model: "",
          platform: "Windows",
          platformVersion: "15.0.0",
          uaFullVersion: version,
          fullVersionList: brands
        });
      },
      toJSON: function () {
        return { brands: brands, mobile: false, platform: "Windows" };
      }
    };
    Object.defineProperty(navigator, "userAgentData", {
      get: function () { return data; }
    });
  } catch (error) {}
})();
