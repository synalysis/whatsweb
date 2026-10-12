// Leave SharedArrayBuffer absent. A stand-in makes WhatsApp try to start a call,
// then disable the voice and video buttons with an error. WebView cannot provide
// the real buffer those calls need.
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
