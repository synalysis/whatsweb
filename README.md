# WhatsWeb

<a href="https://github.com/synalysis/whatsweb/releases/latest"><img src="docs/badges/download.png" alt="Direct download" height="48"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22com.synalysis.whatsweb%22%2C%22name%22%3A%22WhatsWeb%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsynalysis%2Fwhatsweb%22%2C%22author%22%3A%22synalysis%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EWhatsWeb-%5B0-9%5D.%2A%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22autoApkFilterByArch%5C%22%3Afalse%7D%22%7D"><img src="docs/badges/obtainium.png" alt="Get WhatsWeb on Obtainium" height="48"></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22com.synalysis.whatsweb.gecko%22%2C%22name%22%3A%22WhatsWeb%20Calls%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsynalysis%2Fwhatsweb%22%2C%22author%22%3A%22synalysis%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EWhatsWeb-Calls-.%2A%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%7D"><img src="docs/badges/obtainium.png" alt="Get WhatsWeb Calls on Obtainium" height="48"></a>

WhatsWeb is a free Android app that shows the [WhatsApp Web](https://web.whatsapp.com/) companion in a layout meant for a phone. You link it from the WhatsApp app you already use, the same way you would link a desktop browser: **Linked devices → Link a device**, then scan the code.

The page you see is the official companion site. WhatsWeb does not speak WhatsApp's private protocol, and it is not affiliated with WhatsApp LLC. "WhatsApp" is used here only to name that service.

## What the app adds

- A one-column phone layout, so the chat list and the open conversation each use the screen. Turn it off on a larger display and both stay visible.
- Adjustable text size, with settings and reload kept over the page so the companion stays full screen.
- Downloads into the system Downloads collection, including files the page exposes as blobs.
- Share-in: text is kept ready to paste, and shared files are saved so you can attach them from a chat.
- Optional app lock, using this device's own biometric or PIN prompt.
- An option to blank the recent-apps preview and block screenshots.
- Optional handling of `wa.me`, `api.whatsapp.com`, and group invite links. This stays off until you turn it on.
- More than one WhatsApp account. Each account keeps its own linked session. Switching reloads the app into that session. A long-press on the icon opens an account once two or more are saved. Android limits how many of those shortcuts it shows; the rest stay in settings.

There is no account, no advertising, and no analytics. Background push is not included, because that would mean running WhatsApp's proprietary push service. See [PRIVACY.md](PRIVACY.md).

## Build

The project uses Android Gradle Plugin 9.0.1 and compile SDK 36. Install a full JDK, not a JRE: the compiler is required. CI uses Temurin 21, and JDK 17 or newer works locally.

```sh
./gradlew test assembleRelease
```

Install a debug build with `./gradlew installDebug`.

## Install

Tagged releases on the [releases](https://github.com/synalysis/whatsweb/releases) page include signed APKs of the same version.

- [WhatsWeb](https://github.com/synalysis/whatsweb/releases/latest): `WhatsWeb-<version>.apk`
- [WhatsWeb Calls](https://github.com/synalysis/whatsweb/releases/latest), a separate install with voice and video calls, alerts while the app is open, and screen sharing. Download the file that matches the device:
  - `WhatsWeb-Calls-<version>-arm64-v8a.apk` for current phones
  - `WhatsWeb-Calls-<version>-armeabi-v7a.apk` for older 32-bit phones
  - `WhatsWeb-Calls-<version>-x86_64.apk` for Chromebooks and emulators

[Obtainium](https://github.com/ImranR98/Obtainium) can track each app from the same GitHub repository. [Add WhatsWeb](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22com.synalysis.whatsweb%22%2C%22name%22%3A%22WhatsWeb%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsynalysis%2Fwhatsweb%22%2C%22author%22%3A%22synalysis%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EWhatsWeb-%5B0-9%5D.%2A%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22autoApkFilterByArch%5C%22%3Afalse%7D%22%7D). [Add WhatsWeb Calls](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22com.synalysis.whatsweb.gecko%22%2C%22name%22%3A%22WhatsWeb%20Calls%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsynalysis%2Fwhatsweb%22%2C%22author%22%3A%22synalysis%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5EWhatsWeb-Calls-.%2A%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%7D). The Calls link turns on architecture filtering, so Obtainium keeps the APK whose name matches the device.

The GitHub APK and an F-Droid build are signed by different keys, so Android will not replace one with the other. WhatsWeb Calls is not an F-Droid build.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
