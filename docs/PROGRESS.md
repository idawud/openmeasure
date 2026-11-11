# PROGRESS (overwrite this file; keep under 40 lines)

Last updated: 2026-11-11
Current phase: Phase 1 (provisional: T07 GO/NO-GO still pending)
Last committed task: T11 (point-to-point AR UI, build-only). Done: T01-T06, T08-T11, T14, T15, T17-T19. T07 code committed, NOT ticked.

## In progress
- Owner chose build-only continuation. Next: T12, T13, T16 AR UI (unverified on device).
- T07 results -> write docs/ACCURACY.md, record GO/NO-GO, tick T07. NO-GO = stop and ask owner.

## Blockers / decisions needed from the owner
- MANUAL TEST (Pixel 8 Pro, Android 17): `./gradlew installDebug`, then:
  1. T05 camera: feed shows after permission, not stretched/flipped; rotate 0/90/180/270 + split-screen OK;
     Home then return = no black screen/crash; smooth ~30 fps.
  2. T07 accuracy (spike screen replaced by T11 UI): 3 known-length objects (A4 29.7 cm, ~1 m ruler, ~2 m door height).
     Press "+" for A then "+" again for B (3rd press restarts), 5 readings each. Label shows number only if confidence allows. `adb logcat -s OpenMeasureSpike` gives "dist=.. cm source=.. conf=..".
     Send per object: true length, 5 readings, source (DEPTH/PLANE), oddities. Target ~1 cm short / 1-2% long (SPEC).
  3. T15 level: Level tab; phone flat shows bubble, on its side shows bar; green <0.5 deg; check with a known-level
     surface; tone/haptic follow Settings toggles; rotate = brief reset only.
  4. T10+T11: coaching hint, reticle only when ready; dots/line track surface after moving and after rotation; label legible; haptic on B.
  5. T17/T19/T18: tabs, gear icon, settings persist, disclaimer once; TalkBack readouts; level live region may be chatty.

## Notes that a new session must know
- Reference device: Pixel 8 Pro, Android 17. SDK via gitignored local.properties (C:/Users/idawud/AppData/Local/Android/Sdk).
- Versions: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00. core-measure pins Kotlin jvmTarget 17.
- Deps (reasons): ARCore 1.57.0 (api in core-ar); Filament 1.77.3 (core-ar, unused so far); ML Kit pose-detection
  17.0.0 (person height); Room 2.8.5 + KSP 2.3.13 (data); kotlinx-coroutines-android 1.10.2 (StateFlow in core-ar).
- core-measure: geometry/, hit/ (DepthHitTester, HitResolver), model/Path, session/MeasurementSession (wire: listener->
  StateFlow, endMove(), updatePoints per frame), confidence/ConfidenceScorer (hide number when !canDisplayNumber),
  units/LengthFormatter, level/LevelMath. core-ar: HitTestService, ArCameraView (onFrame on GL thread).
- Tunables guessed, need phone tuning: hit-test weights, PLANE_HIGH_MIN_CONFIDENCE (0.9).
- Spike points are plain Vec3 (no anchors). Settings haptics/sound wired only in Level; analytics opt-out also
  gates local-only crash logs (filesDir/crashes). Hand-drawn icons: eyeball on device.
