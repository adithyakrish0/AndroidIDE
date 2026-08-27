# Jules Task Plan — AI Folder Agent

## Setup (do this first, one time)

1. Create an empty GitHub repo.
2. Commit `android-ai-folder-agent-prompt.md` into the repo root, renamed to `SPEC.md`.
3. Connect the repo to Jules (jules.google.com).

Every task below references `SPEC.md` so you don't have to re-paste the whole spec each time — Jules reads it from the repo.

---

## Task 1 — Project skeleton + folder access

```
Read SPEC.md in this repo for full context on the app we're building.

For this task, implement only:
- Basic Android (Kotlin) project skeleton, minSdk suitable for SAF (API 21+, target latest stable).
- SAF folder picker flow (ACTION_OPEN_DOCUMENT_TREE) per SPEC.md §1 and §2.1, with persisted URI permission.
- A basic Dashboard screen (§8.1) that lists files in the bound folder (plain list is fine for now, no markdown rendering yet).
- Follow the permissions constraint in §1 strictly — no accessibility service, overlay, or device admin.

Do not implement chat, AI providers, or markdown rendering yet — that's later tasks. Confirm your plan before writing code.
```

---

## Task 2 — Settings + encrypted API keys

```
Read SPEC.md in this repo.

Implement the Settings screen from §8.4 and §2.2:
- Fields for Gemini API key and Groq API key, stored via EncryptedSharedPreferences.
- Folder rebind option (re-trigger the SAF picker from Task 1).
- Leave the autonomous/confirm toggle as a placeholder for now (wire it up fully in a later task).

Confirm your plan before writing code.
```

---

## Task 3 — Chat + Gemini + one working tool

```
Read SPEC.md in this repo.

Implement a minimal end-to-end chat flow:
- Chat screen per §8.3 (single continuous thread, persisted locally per bound folder — §8.3).
- Gemini API integration only (no Groq yet).
- Exactly one working tool: write_file, per §4 tool definitions and the system-prompt tone rule in §4.
- Goal: I type a message, the model can call write_file, the file appears in the folder, and I see it on the Dashboard from Task 1.

Keep this minimal — do not add the other tools yet. Confirm your plan before writing code.
```

---

## Task 4 — Full tool set + agent rules

```
Read SPEC.md in this repo.

Extend the agent from Task 3:
- Add remaining tools from §4: list_dir, read_file, create_file, delete_file, rename_file.
- Implement the on-demand context strategy (§4 "Context strategy" — no full folder listing every message).
- Implement the agent loop cap (~15 chained tool calls, §4 "Agent loop cap").
- Implement the file-matching rule and freshness rule from §4 (check existing files before creating new ones; re-read files before editing).

Confirm your plan before writing code.
```

---

## Task 5 — Groq fallback

```
Read SPEC.md in this repo.

Implement §3 in full:
- Abstract the AI provider behind a common interface if not already done in Task 3.
- Add Groq as automatic fallback on Gemini quota/429 errors, with the in-chat notice described in §3.
- Handle the both-providers-exhausted case and the no-internet case per §3.

Confirm your plan before writing code.
```

---

## Later tonight, at home with Antigravity/Freebuff (not for Jules — needs real device testing)

- §7 Markdown renderer with interactive checkboxes + tables
- §5 Confirm-mode diff view + autonomous/confirm toggle wiring
- §6 `.history/` backup layer
- §9 Debug logging + log viewer
- §10 polish pass (offline/error states, empty states)

These involve a lot of visual/UI verification (does the checkbox actually toggle, does the diff view look right, does the folder picker behave correctly on your actual phone) that's much faster to iterate on with a real device in front of you than async PR review from a phone screen.
