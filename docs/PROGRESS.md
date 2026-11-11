# PROGRESS (overwrite this file; keep under 40 lines)

Last updated: 2026-11-11
Current phase: Phase 0 (spike)
Last committed task: T14 (units formatter, tests pass); T07 code committed, NOT ticked (awaiting phone test + GO/NO-GO)

## In progress
- Task: T07 — waiting on owner's manual accuracy test on the Pixel 8 Pro

## Next step
- Owner reports T07 results -> write docs/ACCURACY.md, record GO/NO-GO, tick T07. Phase 1 work is provisional on GO. Done T08, T09, T14 (phone-independent). Next: T10-T13 UI (build-only, unverified on device).
- GO: continue with T08. NO-GO: stop and discuss with owner.

## Blockers / decisions needed from the owner
- MANUAL TEST T05 + T07 (Pixel 8 Pro, Android 17):
  1. Build/install: `./gradlew installDebug` (phone USB debugging on) or open in Android Studio and Run.
  2. T05 checks: feed shows after camera permission, not stretched/flipped; rotate 0/90/180/270 and
     split-screen OK; Home then return = no black screen/crash; smooth ~30 fps.
  3. T07 accuracy: pick 3 reference objects with a known length (e.g. A4 long side 29.7 cm,
     a 1 m ruler/door-frame width, a ~2 m wall/door height). Move slowly to build tracking first.
  4. For each: tap point A, then B (reticle on each end), read distance. Repeat 5 times per object.
  5. Watch logs: `adb logcat -s OpenMeasureSpike` (lines "dist=.. cm source=.. conf=..").
  6. Send Claude per object: true length, the 5 readings, source (DEPTH/PLANE), and any oddities.
  Go criterion: see docs/SPEC.md accuracy target (about 1 cm on short, ~1-2% on longer).

## Notes that a new session must know
- Reference device: Pixel 8 Pro, Android 17.
- Versions: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00.
- SDK at C:/Users/idawud/AppData/Local/Android/Sdk via gitignored local.properties.
- core-measure pins Kotlin jvmTarget 17 (Kotlin defaulted to 25, mismatching Java).
- Deps (reasons): ARCore 1.57.0 (api in core-ar); Filament 1.77.3 (core-ar, unused so far);
  ML Kit pose-detection 17.0.0 (person height); Room 2.8.5 + KSP 2.3.13 (data);
  kotlinx-coroutines-android 1.10.2 (StateFlow in core-ar).
- Hit-test: core-measure geometry/ + hit/; core-ar HitTestService. Weights/thresholds are guesses: tune after T07.
- Spike points are plain Vec3, not anchors (drift); confidence shown as raw number, no badge label yet.
- Engine: core-measure model/Path.kt, session/MeasurementSession.kt. Wire: listener -> StateFlow; endMove() on drag end; updatePoints(anchors) per frame; after undo/remove re-sync anchors.
- Confidence: core-measure confidence/ConfidenceScorer.score(...) -> level/score/reason; map ARCore TrackingState to TrackingStatus in core-ar; hide number when !canDisplayNumber. Tunable: PLANE_HIGH_MIN_CONFIDENCE (0.9).
- Units: core-measure units/LengthFormatter.format(m, UnitSystem), defaultSystemForRegion(country), UnitSystem.toggled().
