# PROGRESS (overwrite this file; keep under 40 lines)

Last updated: 2026-11-18
Current phase: Phase 4 field fixes T33-T36 DONE (committed), all unverified on device. T07 GO/NO-GO and T22 still open.
Last commits: T33 solid line/no ruler; T34 SurfaceFuser (plane-fused depth hit, near-range falloff); T35 PlacementBuffer (pre-tap hit, speed gate, close-range snap); T36 debug log.

## Next step
- OWNER: install on Pixel 8 Pro (`./gradlew installDebug`) and retest: 1 m item at ~30 cm moving along it (compare iPhone), tap "+" 10x on same spot (flakiness),
  line is solid white. Send `adb logcat -s OpenMeasureMeasure OpenMeasureDrift` + true length if error persists.
- If still wrong: tune SurfaceFuser (alpha 0.3, gates), PlacementBuffer (lookback 120 ms, max 0.25 m/s), then consider [opus] architect. No ML model unless fusion fails.
- Known gaps: no "Hold steady" UI for refused taps (MeasureUiState.tooFastTick unused); held snaps ignore close-range radius cap.
- Then: next unticked in docs/TASKS.md; old manual-test list is in git history (commit e270e45).

## Notes that a new session must know
- Reference device: Pixel 8 Pro, Android 17. SDK via gitignored local.properties (C:/Users/idawud/AppData/Local/Android/Sdk).
- Versions: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00; core-measure pins jvmTarget 17.
- Deps (reasons): ARCore 1.57.0; ML Kit pose-detection 18.0.0-beta5 (core-ar; height; 17.0.0 had unaligned 16KB libxeno_native);
  Room 2.8.5 + KSP 2.3.13 (data); kotlinx-coroutines-android 1.10.2 (StateFlow). No other deps.
- Hit-test/confidence tunables are guesses (PLANE_HIGH_MIN_CONFIDENCE 0.9, snap thresholds): tune after T07/T22.
- Crash logs local-only (filesDir/crashes), gated by analytics opt-out. Lint leftovers in docs/RELEASE_CHECKLIST.md.
