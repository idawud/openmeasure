# OpenMeasure — task list

One task = one commit. Tag = which model does it (see CLAUDE.md §2). Spec refs point to `docs/SPEC.md`.
Tick with `- [x]` when committed. Do not reorder without noting it in PROGRESS.md.

## Phase 0 — Setup and accuracy spike

- [x] T01 [haiku] Create Android project: Kotlin, Compose, modules `app`, `core-measure`, `core-ar`, `data`; compileSdk/targetSdk 37, minSdk 26; `.gitignore`; builds empty screen. (Spec §6)
- [ ] T02 [haiku] Add dependencies: ARCore, Filament (or GL helper), CameraX not needed, ML Kit pose (on-demand), Room, Compose Material 3. Build passes.
- [ ] T03 [haiku] Manifest: camera permission, `com.google.ar.core` meta-data `optional`, ARCore availability check stub. Support rotation/resizing.
- [ ] T04 [sonnet] ARCore session lifecycle in `core-ar`: install check, create/resume/pause, camera permission flow, Depth API mode when supported.
- [ ] T05 [sonnet] Camera background rendering in Compose (GLSurfaceView or Filament view) showing live AR feed at 30 fps+.
- [ ] T06 [opus] Hit-test service: reticle ray → depth map hit, fallback to plane hit; returns world pose + depth confidence. Unit-test the math in `core-measure`.
- [ ] T07 [sonnet] Spike screen: tap twice, log distance between anchors. Manual test on Pixel 8 Pro with 3 reference objects; record results in `docs/ACCURACY.md`. GO/NO-GO note in PROGRESS.md.

## Phase 1 — MVP (all P0 features)

- [ ] T08 [opus] Measurement engine (`core-measure`): Point, Segment, Path models; length in metres; live update from anchors; unit tests.
- [ ] T09 [opus] Confidence scorer (F18): combine tracking state, depth confidence at both ends, camera distance → High/Medium/Low; unit tests.
- [ ] T10 [sonnet] Coaching overlay (F2): "Move your phone slowly" until surface/depth found; reticle appears when ready.
- [ ] T11 [sonnet] Point-to-point UI (F1): + button drops start/end; line + live label rendered in AR; label always faces user.
- [ ] T12 [sonnet] Multi-segment path (F3) with per-segment and total labels.
- [ ] T13 [sonnet] Edit points (F4): drag endpoints, undo, clear.
- [ ] T14 [haiku] Units (F5): metric/imperial formatter + locale default + one-tap toggle; unit tests for formatting and rounding (1 cm / 0.5 in).
- [ ] T15 [sonnet] Level tool (F6): rotation-vector sensor, low-pass filter, flat (bubble) and edge modes, green at 0°, optional rising tone.
- [ ] T16 [sonnet] Capture and share (F7): render overlay into photo, save via MediaStore, share sheet.
- [ ] T17 [haiku] Bottom tab switcher Measure · Level · Height (Height disabled placeholder), settings screen (units, haptics, sound, analytics opt-out, about).
- [ ] T18 [haiku] Accessibility pass: TalkBack readouts ("Line, 84 centimetres"), haptics on finish, contrast check.
- [ ] T19 [haiku] One-time accuracy disclaimer dialog and crash reporting with opt-out.
- [ ] T20 [sonnet] Large-screen and rotation handling (Android 17 rule): test on foldable/tablet emulator.
- [ ] T21 [haiku] Run full test suite + lint, fix warnings; write `docs/RELEASE_CHECKLIST.md`.
- [ ] T22 [sonnet] Accuracy test run on Pixel 8 Pro (10 objects, 3 light levels) → `docs/ACCURACY.md`; must meet ≤ 1 cm median error at 1 m or open fix tasks.

## Phase 2 — v1.x (P1)

- [ ] T23 [opus] Edge and corner snapping (F8) with haptic feedback.
- [ ] T24 [opus] Rectangle detection + area (F9) with manual 4-corner fallback.
- [ ] T25 [opus] Person height (F10): ML Kit pose + depth floor distance; depth-capable phones only.
- [ ] T26 [sonnet] Measurement history (F11): Room entities, list screen, notes, delete.
- [ ] T27 [sonnet] Vertical/horizontal guide lines (F12).
- [ ] T28 [sonnet] Ruler view ticks (F13).

## Phase 3 — Later (P2)

- [ ] T29 [opus] Box volume (F14).
- [ ] T30 [opus] Room scan to floor plan (F15).
- [ ] T31 [sonnet] Angle tool (F16).
- [ ] T32 [haiku] Export CSV/PDF (F17).
