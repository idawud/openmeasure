# OpenMeasure release checklist

Status: pre-release (v0.1.0, versionCode 1). Tick items before each Play upload.

## 1. Build and versioning
- [ ] `app/build.gradle.kts`: bump `versionCode` (monotonic integer) and `versionName` (current: 0.1.0 / 1).
- [ ] compileSdk and targetSdk are 37; minSdk is 26.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` passes (lint: 0 errors).
- [ ] Known lint warnings to re-check: NewerVersionAvailable (kotlinx-coroutines 1.10.2 vs 1.11.0),
      Aligned16KB (`mediapipe-internal` 16.0.0 native lib), ObsoleteSdkInt (`mipmap-anydpi-v26`, see section 2).

## 2. Signing and release build
- [ ] Create upload keystore; keep it and passwords out of git (use `keystore.properties` or CI secrets).
- [ ] Add a `signingConfigs.release` block and reference it from `buildTypes.release`.
- [ ] `isMinifyEnabled` is currently `false`. Decide: enable R8 (`true`) and run a release build,
      or keep off and record the reason here. If enabled, smoke-test every screen on the release APK.
- [ ] ARCore keep rules: ARCore and ML Kit ship consumer ProGuard rules; `app/proguard-rules.pro` is
      empty. Verify no ARCore class is stripped in a minified release build (check the logcat for
      `ClassNotFound` or session-create failures).
- [ ] Build with `./gradlew bundleRelease`; confirm AAB size and that the app launches from the bundle.
- [ ] Launcher icon is a placeholder (`res/drawable/ic_launcher_foreground.xml`): replace with final art.

## 3. Tests
- [ ] JVM: `./gradlew :core-measure:test` (133 tests at last run, all pass).
- [ ] Unit: `./gradlew testDebugUnitTest` (no Android unit tests yet in app, core-ar, data).
- [ ] Lint: `./gradlew lintDebug` and `lintRelease`; review HTML report before upload.

## 4. Manual device checklist (Pixel 8 Pro, Android 17)
- [ ] Camera feed starts; permission prompt shown and denial handled with a settings link.
- [ ] Rotation: portrait/landscape keep overlay aligned; no session restart loop.
- [ ] Accuracy per `docs/ACCURACY.md` (file not yet present; create before release).
      Measure a known-length object and record error; no number shown when tracking is poor.
- [ ] Level screen reads sensible angles and reports "unavailable" without a sensor.
- [ ] Capture: photo saved to Pictures/OpenMeasure; Share action opens the chooser.
- [ ] Settings: units and haptics toggles persist across app restarts.
- [ ] Disclaimer visible before first measurement.
- [ ] TalkBack: all buttons announce; measurement labels read distance and confidence.

## 5. Tablet and foldable emulator checks
- [ ] Pixel Tablet / 10" tablet AVD: rail navigation, insets, no clipped controls.
- [ ] Foldable AVD: fold/unfold keeps measurement state; no crash on resize.
- [ ] Non-AR device (no ARCore): app opens, shows the unsupported message, no crash.

## 6. Permissions and Play data safety
- [ ] Only permission declared: CAMERA. `android.hardware.camera.ar` is optional (`required="false"`).
- [ ] ARCore is optional (`com.google.ar.core` meta-data `optional`); the app must run without it.
- [ ] Data safety form: no data collected or shared; all processing on device; no accounts.
- [ ] Photos are written only to the user's MediaStore folder when they tap capture.
- [ ] Measurement history (Room) is local only.
- [ ] Crash logs, if any, stay local on the device; nothing uploaded.

## 7. Store listing
- [ ] Describe as a measurement aid, not a precision tool; include the accuracy disclaimer text.
- [ ] State that AR features need a supported device; ARCore is optional, so the app installs on any device.
- [ ] Screenshots: phone, 7" and 10" tablet; include one showing the confidence badge.
- [ ] Privacy policy URL: state "no data leaves the device".
- [ ] Content rating questionnaire completed.
