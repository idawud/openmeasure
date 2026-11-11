# OpenMeasure — project rules for Claude Code

Free Android AR measuring app (like Apple Measure / the retired Google Measure).
Full product spec: `docs/SPEC.md`. Task list: `docs/TASKS.md`. Live state: `docs/PROGRESS.md`.

## 1. Resume protocol (read this first, every session)

Work can stop at any moment (usage limit, crash, closed laptop). So:

1. Start every session by reading ONLY `docs/PROGRESS.md`, then the one task in `docs/TASKS.md` it points to.
   Do not read `docs/SPEC.md` in full; open only the section the task names.
2. Run `git status` and `git log --oneline -5`. If there are uncommitted changes, they belong to the
   "In progress" task in PROGRESS.md: finish or discard them before starting anything new.
3. Work on ONE task at a time. A task must be small enough to finish and commit in about 30 minutes.
4. After each task:
   - build passes (`./gradlew assembleDebug`) and relevant tests pass,
   - tick it in `docs/TASKS.md` (`- [x]`),
   - update `docs/PROGRESS.md` (keep it under 40 lines — overwrite, don't append history),
   - commit: `git commit -m "T<id>: <summary>"`.
5. Before any long step (big refactor, new module), write the plan into PROGRESS.md "Next step" first,
   so a new session can pick it up mid-task.
6. Never leave the repo in a non-building state between commits.

## 2. Model routing (save tokens)

The main session runs on **Sonnet** and orchestrates. Delegate by task tag in TASKS.md:

| Tag | Use | Model | How |
|---|---|---|---|
| `[haiku]` | Boilerplate, Gradle/config edits, strings/resources, docs, renaming, running build/tests/lint, reading logs, git chores | Haiku | `scaffolder` or `test-runner` subagent |
| `[sonnet]` | Normal feature code, UI screens, Room DB, unit tests, bug fixes | Sonnet | Do it in the main session or the `implementer` subagent |
| `[opus]` | AR math, hit-testing, depth fusion, edge snapping, confidence scoring, tricky crashes, architecture choices | Opus | `architect` subagent: it designs and writes the hard core; main session wires it in |

Rules:
- Default down, not up: try the cheaper model first; escalate only when a task fails twice.
- Hand subagents a short brief (task ID, files to touch, acceptance criteria) — never the whole spec.
- Subagents return a summary under 15 lines, not file dumps.
- Use the `test-runner` (Haiku) for every build/test run; it returns only failures.
- Run `/clear` after each committed task; PROGRESS.md carries the state, not the chat.

## 3. Tech baseline

- Kotlin, Jetpack Compose, single-activity app, package `app.openmeasure`.
- compileSdk / targetSdk 37 (Android 17), minSdk 26. Reference device: Pixel 8 Pro on Android 17.
- ARCore (Depth API when supported, plane hit-test fallback), Filament or OpenGL ES rendering (no Sceneform).
- ML Kit Pose Detection (person height), Room (history), MediaStore (photos).
- Must handle rotation and resizing on large screens (Android 17 / API 37 rule).
- All processing on device. No backend, no sign-in.

## 4. Code rules

- Modules: `app` (UI), `core-measure` (engine, pure Kotlin, unit-tested), `core-ar` (ARCore wrapper), `data` (Room).
- The measurement engine stays free of Android types so it can be unit-tested on the JVM.
- Every length shows a confidence badge; never display a number when tracking is poor.
- Keep files under ~300 lines. No new dependency without a one-line reason in PROGRESS.md.
