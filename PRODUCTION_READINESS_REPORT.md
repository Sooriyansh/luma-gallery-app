# Luma Gallery production-readiness report

Audit date: 2026-10-23

## Architecture

Luma Gallery is an offline-first Java/XML Android application (`minSdk 24`, `targetSdk 36`,
`compileSdk 36`). `MediaRepository` owns MediaStore queries; `WorkManager` schedules local
analysis; Room persists analysis results; activities/fragments render the gallery, viewer,
private vault, recycle bin, and settings flows. The bundled LiteRT/TensorFlow Lite model is
stored uncompressed and invoked from the background analysis pipeline.

## Resolved high-priority findings

* Fixed the Private Vault lookup for current opaque UUID vault filenames. Previously a new
  encrypted item could appear in the vault but not resolve for restore or recycle-bin actions.
  Legacy filename lookup remains supported.
* A successful vault restore now removes its encrypted metadata sidecar as well as the encrypted
  media file.
* Vault IV reads now handle partial `InputStream` reads correctly.
* App-lock and vault-code verifiers are PBKDF2-HMAC-SHA256 salted verifiers (210,000 iterations),
  use constant-time comparison, and now clear temporary password, salt, and hash byte arrays in
  `finally` blocks.
* Corrected the `TypedArray` package in the privacy-policy screen, which was preventing Java
  compilation.
* Added Android-version-safe confirmation haptics (API 24 through current devices) and regression
  coverage for current and legacy vault item lookup.

## Security assessment

The manifest exposes only the launcher activity; all other listed activities are non-exported.
Cleartext network traffic is disabled by `network_security_config`. Media and vault metadata are
excluded from backup, vault contents use AES-256-GCM with an Android Keystore key, and release
minification/resource shrinking are enabled. Signing credentials are loaded from the ignored
`keystore.properties` file rather than source control.

No network API or hard-coded credential was found in the inspected source. The application makes
privacy-sensitive claims about on-device analysis and must keep its public privacy policy and
Google Play Data safety declaration aligned with future SDK additions.

## AI/model assessment

The bundled model is kept intact and uncompressed. Model load validates the expected float input
tensor (`[1,128,128,3]`) and scalar float output before inference. Decode/inference happens off
the UI path via the existing worker pipeline; invalid images and model-load failures return an
explicit failure result rather than a fabricated classification. The app documents that results
are probabilistic and avoids claiming cryptographic provenance validation when no verifier is
present.

An instrumented model inference test on representative real-device media was not run because no
Android device/emulator was supplied for this audit.

## Verification actually run

Executed successfully:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lint assembleRelease bundleRelease
```

Results:

* 21 JVM tests passed: image-evidence fusion (14), Android compatibility policy (4), and vault
  lookup regression tests (3).
* Debug lint: 0 errors, 220 warnings. The remaining warnings are chiefly localization,
  dependency-update, deprecated-API, overdraw, and resource-cleanup suggestions; none block the
  configured build.
* R8/resource shrinking, release signing validation, release APK packaging, and release AAB
  packaging all completed successfully.

Not performed: device UI/instrumentation, network-failure, low-RAM/battery profiling, permission
matrix, offline regression, and manual Android-version testing. These need a device/emulator lab
before a production rollout.

## Release artifacts

* `app/build/outputs/apk/release/app-release.apk` — version 1.0 (code 1), SHA-256
  `D6821545284741890B112AC8F9565EB182B4D0A51D2FBF2D62F8A8E219BDD53B`
* `app/build/outputs/bundle/release/app-release.aab` — SHA-256
  `D65FE146618902D70AB5DC3A36CF1CA723B118E50213106339A96D4824CE64D9`

Build these again with the command above. Keep the keystore outside source control and increment
`versionCode` for every Play Console upload.

## Google Play preparation

`targetSdk 36` meets the current requirement for new mobile apps and updates, which target Android
16/API 36 or higher. See Google's current target-API policy:
https://support.google.com/googleplay/android-developer/answer/11926878

Suggested listing copy (review against the final feature set):

* App name: **Luma Gallery**
* Short description: **Private, on-device photo gallery with local media analysis.**
* Full description: **Luma Gallery helps you browse, organize, edit, share, and protect photos and
  videos stored on your Android device. Its optional Private Vault encrypts selected media in
  app-private storage. Media analysis runs on-device and provides evidence-based, probabilistic
  insights; it is not proof of authenticity. Luma Gallery does not upload your media.**

Before submission, the developer must provide a publicly accessible privacy-policy URL, developer
contact details, final category/content rating, Data safety answers, login/app-access instructions
if applicable, screenshots for supported form factors, a 1024x500 feature graphic, and the final
signed AAB. Begin with Internal Testing and run the manual test matrix above before Closed or
Production rollout.

## Remaining release blockers

1. No public privacy-policy URL or developer contact was supplied; the in-app policy is not a
   replacement for the Play Console URL.
2. Device/emulator validation has not been performed, including Android 7.0 through Android 16,
   selected-photo access, denied permissions, large media, rotation/foldable layouts, and low-RAM
   behavior.
3. Play Console declarations, screenshots, feature graphic, content rating, and Data safety data
   require the publisher's account and factual business details.

