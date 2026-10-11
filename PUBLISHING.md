# Publishing

These steps are for cutting a release. They are not part of using the app.

## Signed GitHub release

The release APK is unsigned unless `WHATSWEB_KEYSTORE`, `WHATSWEB_KEYSTORE_PASSWORD`, `WHATSWEB_KEY_ALIAS`, and `WHATSWEB_KEY_PASSWORD` are all set. A partial set fails the build instead of producing a mis-signed APK. The unsigned file is `app-release-unsigned.apk`. F-Droid signs its own build from that output.

Run `scripts/tag-release` after the version in `app/build.gradle.kts` is committed. It tags the current commit `v` plus `versionName`, which is the only form [`.github/workflows/release.yml`](.github/workflows/release.yml) accepts. Before that, raise `versionCode` so Android accepts the update. Pushing the tag starts the workflow. The workflow needs these repository secrets, taken from one keystore that is kept and reused:

- `WHATSWEB_KEYSTORE_BASE64` — `base64 -w 0 release.jks`
- `WHATSWEB_KEYSTORE_PASSWORD`
- `WHATSWEB_KEY_ALIAS`
- `WHATSWEB_KEY_PASSWORD`

That APK and an F-Droid build are signed by different keys, so Android will not replace one with the other.

## F-Droid

The recipe is [`fdroid/com.synalysis.whatsweb.yml`](fdroid/com.synalysis.whatsweb.yml). The filename matches the application id. It uses `gradle: yes`, so F-Droid runs the release build. No extra build script is required.

Store text, the icon, and the phone screenshots live under `fastlane/metadata/android/en-US/`. F-Droid reads the screenshots from `images/phoneScreenshots/`.

Inclusion is a review. The recipe builds the tag named in `commit` (currently `v1.0.1`, version name `1.0.1`, version code `2`), so that tag has to exist first. Open a request on [fdroiddata](https://gitlab.com/fdroid/fdroiddata) and attach the recipe. A first inclusion can instead go to the [Request for Packaging](https://gitlab.com/fdroid/rfp/-/issues) tracker. The request should mention the NonFreeNet anti-feature and the app-side features: the phone layout, downloads, share-in, lock, and link handling.
