# PROGRESS (overwrite this file; keep under 40 lines)

Last updated: 2026-11-11
Current phase: Phase 0
Last committed task: T06 (hit-test: 24 JVM tests pass, assembleDebug passes; unverified on device)

## In progress
- Task: T07 (spike screen: code via implementer, then manual accuracy test on phone)
- Uncommitted changes: none at start

## Next step
- T07 code first; manual test + GO/NO-GO needs owner (steps in Blockers). Hit-test weights/thresholds are guesses: tune on Pixel 8 Pro.

## Blockers / decisions needed from the owner
- MANUAL TEST for T05 (Pixel 8 Pro; not blocking other tasks): install debug APK, then check:
  1. Grant camera permission: live feed shows, not stretched/rotated/flipped.
  2. Rotate 0/90/180/270 and use split-screen/resize: feed stays correct.
  3. Press Home, return: no black screen or crash (riskiest: session pause/resume with kept GL context).
  4. Feed feels smooth (~30 fps+).

## Notes that a new session must know
- Reference device: Pixel 8 Pro, Android 17.
- Versions: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00.
- SDK at C:/Users/idawud/AppData/Local/Android/Sdk via gitignored local.properties.
- core-measure pins Kotlin jvmTarget 17 (Kotlin defaulted to 25, mismatching Java).
- Deps (reasons): ARCore 1.57.0 (api in core-ar); Filament 1.77.3 (core-ar, unused so far; GL renderer used);
  ML Kit pose-detection 17.0.0 (person height); Room 2.8.5 + KSP 2.3.13 (data; no KSP 2.4.x, works);
  kotlinx-coroutines-android 1.10.2 (StateFlow in core-ar).
- core-ar exposes ArCameraView: trackingState StateFlow, latestFrame, onFrame callback (GL thread).
- Hit-test: core-measure geometry/ + hit/ (DepthHitTester, HitResolver); core-ar HitTestService.hitTest(frame,x,y). Call on GL thread from ArCameraView.onFrame.
