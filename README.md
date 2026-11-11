# OpenMeasure — Claude Code build kit

Copy everything in this folder into an empty project folder, then:

1. Install Claude Code and Android Studio (with the Android 17 / API 37 SDK).
2. Open a terminal in the folder and run `claude --model sonnet`.
3. Paste the **KICKOFF** prompt from `PROMPTS.md`.
4. When you hit your usage limit: wait for it to reset, open a fresh session the same way, and paste the **RESUME** prompt.

## What each file does

| File | Purpose |
|---|---|
| `CLAUDE.md` | Rules Claude Code loads automatically every session: resume protocol, model routing, tech baseline |
| `PROMPTS.md` | Kickoff, Resume, Checkpoint and Escalate prompts |
| `docs/SPEC.md` | Condensed product spec, read one section at a time |
| `docs/TASKS.md` | 32 small tasks, each tagged [haiku], [sonnet] or [opus] |
| `docs/PROGRESS.md` | Short "where we are" note, overwritten after each task — this is what makes resuming cheap |
| `.claude/agents/*.md` | Subagents pinned to cheaper or stronger models |

## How the token saving works

- The main session runs on Sonnet and hands off by tag:
  - Haiku: `scaffolder` for boilerplate and `test-runner` for build/test/lint (returns only failures).
  - Sonnet: `implementer` for normal feature work.
  - Opus: `architect`, only for AR math and depth logic.
- Each task ends with a commit and a fresh PROGRESS.md, so `/clear` after each task keeps the context small.
- Resuming reads ~40 lines of PROGRESS.md instead of reloading a long chat.

## Notes

- Check the subagents with `/agents` inside Claude Code.
- If `CLAUDE_CODE_SUBAGENT_MODEL` is set in your shell, it overrides the `model:` line in every subagent; leave it unset.
- Manual tests on your Pixel 8 Pro (T07, T22) will show up under "Blockers" in PROGRESS.md with exact steps.
