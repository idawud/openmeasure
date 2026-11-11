# OpenMeasure — product spec (build copy)

Condensed from the full spec doc. Section numbers are referenced by docs/TASKS.md.

## §1 Overview
Free Android AR app that turns the phone camera into a tape measure, level and height meter, filling the gap
left when Google retired Measure (June 2021). AR, not VR: lines are drawn over the live camera view.
Core tools are free forever, no sign-in, no ads over the camera.

## §2 Goals and metrics
- Length, area or person height in under 10 s from app open.
- Accuracy for everyday jobs; every number shows a confidence badge.
- Targets: median error ≤ 1 cm on 1 m lines (depth phones, Pixel 8 Pro reference), ≤ 2 cm camera-only;
  time to first measurement < 15 s; crash-free sessions ≥ 99.5%.
- Non-goals v1: pro surveying, full 3D scanning, VR headsets, iOS.

## §3 Users
Primary: home owner checking if something fits. Also DIY, movers, online sellers, parents (height), students.

## §4 Features (P0 = v1, P1 = v1.x, P2 = later)
| ID | Feature | Behaviour | P |
|---|---|---|---|
| F1 | Point-to-point length | Tap + for start, move, tap + for end; live label follows the line | P0 |
| F2 | Surface detection + coaching | "Move your phone slowly" until plane/depth found; reticle snaps to surfaces | P0 |
| F3 | Multi-segment path | Chain segments; per-segment + running total | P0 |
| F4 | Edit points | Drag endpoints; undo; clear | P0 |
| F5 | Units | cm/m and in/ft-in; locale default; one-tap switch | P0 |
| F6 | Level | Bubble (flat) and edge modes; degrees; green at 0° | P0 |
| F7 | Capture + share | Photo with lines/labels burned in; gallery; share sheet | P0 |
| F18 | Confidence badge | High/Medium/Low from tracking + depth confidence; hint when low | P0 |
| F8 | Edge/corner snapping | Snap with haptics | P1 |
| F9 | Rectangle area | Auto-detect rectangles; W × H and area; manual fallback | P1 |
| F10 | Person height | Pose model + depth; floor to top of head | P1 |
| F11 | History | Local list of captures with notes | P1 |
| F12 | Guide lines | Snap to true vertical/horizontal | P1 |
| F13 | Ruler view | Tick marks when close to a line | P1 |
| F14 | Box volume | W × D × H | P2 |
| F15 | Room scan → floor plan | 2D plan with dimensions; export | P2 |
| F16 | Angle tool | Angle between two lines | P2 |
| F17 | Export | CSV, PDF | P2 |

## §5 UX
- Opens straight into the camera with Measure live. Flow: open → scan coaching → surface found → tap start →
  tap end → live length + badge → drag/undo/chain → capture and share.
- Screens: Camera (home, bottom tabs Measure · Level · Height), Level, Photo review, History, Settings.
- One-handed: controls in the bottom third. Labels always face the user. Nothing ever covers the camera.

## §6 Technical architecture
- Kotlin + Jetpack Compose; compileSdk/targetSdk 37 (Android 17); minSdk 26.
- Reference device: Pixel 8 Pro on Android 17 (Depth API supported, no ToF sensor).
- Layers: App UI → Measurement engine (hit-test + anchors, snapping, confidence, units) →
  Perception (ARCore tracking, Depth API, ML Kit pose, IMU) → Storage (Room, MediaStore). No server.
- Rendering: Filament or OpenGL ES (Sceneform is archived).
- Length = distance between two ARCore anchors' world positions; anchors update as tracking refines.
- Confidence = tracking state + depth confidence at both ends + distance from camera.
- Level uses the rotation-vector sensor, low-pass filtered; no AR session.
- Android 17 rule: apps targeting API 37 can't opt out of rotation/resizing on screens > 600 dp.

## §7 Quality
- Accuracy: round to 1 cm / 0.5 in; never mm. Test rig: 10 objects (0.3–5 m), 3 light levels; Pixel 8 Pro gates releases.
  One-time disclaimer: estimates only.
- Privacy: all on device; camera permission only; local history; anonymous analytics with opt-out.
- Accessibility: TalkBack readouts, large labels, haptics, audio tone for Level.
- Performance: ≥ 30 fps; cold start to camera < 2 s; APK < 30 MB (ML models on demand).

## §8 Business and roadmap
- Free core; optional one-time Pro unlock for P2 features; tip jar; never ads over the camera.
- Phase 0 spike (4 wks) → Phase 1 MVP P0 (10–12 wks) → Phase 2 P1 → Phase 3 P2.
