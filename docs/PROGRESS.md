# PROGRESS (overwrite this file; keep under 40 lines)

Last updated: 2026-11-11
Current phase: Phase 2 build-only (owner chose to continue features; T07 GO/NO-GO still pending)
Last committed task: T32 (CSV/PDF export). Done: T01-T06, T08-T21, T23-T28, T31, T32. Open: T07, T22 (phone); Phase 3: T29, T30 (opus).
Everything since T05 is compiled + JVM-tested only, never run on a device.

## Next step
- Next unticked after T27: see docs/TASKS.md. Phone results (below) gate T07 GO/NO-GO and T22; NO-GO = stop and ask.

## Blockers / decisions needed from the owner
- No device connected (adb devices empty). Connect Pixel 8 Pro (USB debugging), then `./gradlew installDebug`.
- MANUAL TESTS (Pixel 8 Pro, Android 17), in priority order:
  1. T05/T11 camera+measure: feed ok after permission, rotate/split-screen ok, Home+return no crash, ~30 fps;
     dots/line track surface; labels legible; haptic on B; AR session shared in AppRoot, paused on tab leave:
     verify Measure<->Level<->Height switching + resume.
  2. T07 accuracy: 3 known-length objects (A4 29.7 cm, ~1 m ruler, ~2 m door). "+" for A, "+" for B, 5 readings each.
     `adb logcat -s OpenMeasureSpike` logs "dist=.. cm source=.. conf=..". Send true length, readings, source, oddities.
  3. T15 level (flat bubble / edge bar, green <0.5 deg, tone+haptic follow toggles). T17-T19 tabs, settings, disclaimer once, TalkBack.
  4. T16 capture: saved photo upright, no tint, overlay aligned, Share works (API 26-28 save gated).
  5. T20 emulator: tablet rail, split-screen, foldable posture, rotate mid-measure, cutout.
  6. T23 snap: table edge at 1 m (~7 mm expected), one haptic per acquire, no snap on flat wall, 24dp radius maybe too small.
  7. T24 rect: book at 0.5-1.5 m vs tape; flat wall -> hint; one Undo removes 4 corners.
  8. T25 height: skeleton aligned; stable readout (stableSpreadM may need raising); tape check 2/3/4 m (+-1.5 cm).
  9. T26 history: save line/rect/height, unit switch re-formats, note edit, delete+Undo, clear all.
  10. T27 guides: Level/Plumb chip, lock at 2 deg (release 4), placed point exactly level/plumb, chip not overlapping.
  11. T28 ruler ticks: appear on approach, thin out with distance, upright labels, none when confidence poor, toggle works, metric+imperial.
  12. T31 angle: A,V,B + arc + label; 90 deg corner ~90.0; arc takes smaller sweep near 180; drag/undo update; mode switch clears.
  13. T32 export: share CSV into spreadsheet, PDF into viewer (A4, multipage, thumbnails, missing photo ok); item share with/without photo.

## Notes that a new session must know
- Reference device: Pixel 8 Pro, Android 17. SDK via gitignored local.properties (C:/Users/idawud/AppData/Local/Android/Sdk).
- Versions: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00; core-measure pins jvmTarget 17.
- Deps (reasons): ARCore 1.57.0; Filament 1.77.3 (core-ar, unused); ML Kit pose-detection 17.0.0 (core-ar; height);
  Room 2.8.5 + KSP 2.3.13 (data); kotlinx-coroutines-android 1.10.2 (StateFlow). No other deps.
- Hit-test/confidence tunables are guesses (PLANE_HIGH_MIN_CONFIDENCE 0.9, snap thresholds): tune after T07/T22.
- Crash logs local-only (filesDir/crashes), gated by analytics opt-out. Lint leftovers in docs/RELEASE_CHECKLIST.md.
