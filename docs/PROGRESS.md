# PROGRESS (overwrite this file; keep under 40 lines)

Last updated: 2026-11-11
Current phase: Phase 0
Last committed task: T03 (manifest + ArAvailability stub, assembleDebug passes)

## In progress
- Task: T04 (ARCore session lifecycle in core-ar) — delegated to implementer
- Uncommitted changes: none

## Next step
- T04 via `implementer` (Sonnet): install check, create/resume/pause, camera permission flow, Depth API when supported.

## Blockers / decisions needed from the owner
- none

## Notes that a new session must know
- Reference device: Pixel 8 Pro, Android 17.
- Versions: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00.
- SDK at C:/Users/idawud/AppData/Local/Android/Sdk via gitignored local.properties.
- core-measure pins Kotlin jvmTarget 17 (Kotlin defaulted to 25, mismatching Java).
- Dependencies (reasons): ARCore 1.57.0 (AR session/tracking); Filament 1.77.3 in core-ar (rendering);
  ML Kit pose-detection 17.0.0 (person height; accurate variant is separate artifact);
  Room 2.8.5 + KSP 2.3.13 in data (history DB; KSP has no 2.4.x, works with Kotlin 2.4.21).
- ArAvailability.check maps UNKNOWN_* and NOT_CAPABLE to Unsupported; APK_TOO_OLD/NOT_INSTALLED to NeedsInstall.
