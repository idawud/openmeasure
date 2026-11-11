# Prompts to paste into Claude Code

Start Claude Code in the project folder on Sonnet:

```
claude --model sonnet
```

---

## 1. KICKOFF (first session only)

```
You are building OpenMeasure, a free Android AR measuring app. Read CLAUDE.md and follow it exactly,
especially §1 Resume protocol and §2 Model routing.

Then:
1. Read docs/PROGRESS.md and docs/TASKS.md (not SPEC.md in full).
2. Initialise git if needed and make a first commit of these planning files.
3. Start with the first unticked task. Delegate by its tag:
   [haiku] → scaffolder or test-runner subagent, [sonnet] → do it yourself or use implementer,
   [opus] → architect subagent. Give each subagent a short brief, never the whole spec.
4. After each task: test-runner check → tick TASKS.md → overwrite PROGRESS.md → commit "T<id>: …".
5. Keep going task by task. Stop and ask me only for: a NO-GO at T07, a new paid dependency,
   or anything that needs my phone (manual tests on the Pixel 8 Pro). For manual tests, write the
   exact steps for me in PROGRESS.md under "Blockers" and move on to the next task that doesn't depend on it.

Be token-thrifty: read only the files a task needs, don't re-read files you just wrote,
and summarise subagent results in a line or two.
```

---

## 2. RESUME (after a usage limit, crash or new day)

Start a FRESH session (cheaper than reloading the old chat):

```
claude --model sonnet
```

Then paste:

```
Resume OpenMeasure. Follow CLAUDE.md §1 Resume protocol:
read docs/PROGRESS.md, run `git status` and `git log --oneline -5`, then look at the one task it points to.
If there are uncommitted changes, check them with the test-runner subagent: keep and finish them if they
build, otherwise `git stash` them and restart that task. Then continue task by task as before,
using the model routing in CLAUDE.md §2.
```

Tip: if you were in the middle of a reasoning-heavy step and the chat is short, `claude --continue`
also works, but a fresh session + PROGRESS.md usually costs far fewer tokens.

---

## 3. CHECKPOINT (send this when you see you're close to your limit)

```
Pause now. Finish or revert the current edit so the project builds, overwrite docs/PROGRESS.md with
exactly where you are and the next concrete step, and commit as "WIP T<id>: <state>". Then stop.
```

---

## 4. ESCALATE (when a cheaper model keeps failing)

```
Task T<id> has failed twice. Hand it to the architect subagent with: the task text, the files involved,
the error/test output (max 20 lines) and what was tried. Then wire in its result.
```
