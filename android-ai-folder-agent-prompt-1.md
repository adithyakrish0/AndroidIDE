# Build Prompt: AI Folder Agent (Android App)

## Concept

Build a native Android app (Kotlin) that lets me chat with an AI agent that has read/write access to a single folder on my phone. Instead of chatting with an AI and getting throwaway text answers, this agent maintains clean, structured **Markdown files** in that folder — shopping lists, trackers, notes, project docs — that I can view directly, like a lightweight Notion/Obsidian with an AI editor built in.

This is a personal, zero-budget hobby project. It must never require any paid service, and must be safe to run alongside banking apps (see Permissions section — this is a hard constraint, not a suggestion).

App name: **[placeholder — pick something, e.g. "FolderMind" or "Ledger"]**

---

## 1. Permissions — strict minimum, non-negotiable

Only request:
- `INTERNET` (for API calls)
- Storage Access Framework (SAF) folder access via `ACTION_OPEN_DOCUMENT_TREE` — scoped, revocable, user-picked. No `READ/WRITE_EXTERNAL_STORAGE` broad permission needed.

**Never implement, request, or reference:**
- Accessibility Service
- "Draw over other apps" / overlay permission
- Device Admin
- Any background service that isn't strictly needed for an active API call

Reasoning (keep this in mind during implementation): banking apps detect and block based on Accessibility Service, overlay permissions, device admin, and root — not on which unrelated apps are installed. Staying away from those permission categories entirely means this app coexists safely with banking/financial apps on the same device. Do not add any permission beyond the two listed above without flagging it to me first.

---

## 2. Onboarding flow

1. First launch → explain briefly what the app does → SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`) → persist the returned URI permission permanently (`takePersistableUriPermission`).
2. Settings screen where I enter:
   - Gemini API key
   - Groq API key
   Both stored in `EncryptedSharedPreferences` — never hardcoded, never stored in plaintext.
3. If either key is missing when I try to chat, block chat with a clear inline message ("Add your Gemini/Groq API key in Settings to continue") rather than a cryptic failure.
4. Internal design note: even though I'm binding one folder for now, structure the storage/permission layer as a **list of bound folders** (even if the UI currently only supports adding one). This makes multi-project support later a UI change, not a rewrite.

---

## 3. AI provider layer

- **Primary: Gemini API free tier.** Better reasoning quality, large context window, more reliable structured tool-calling.
- **Fallback: Groq API free tier.** Used automatically when Gemini's daily/rate quota is hit. Faster but lower-quality open models — acceptable as a degraded fallback, not primary.
- Provider layer must be abstracted behind a common interface (`AIProvider` with `sendMessage(...)`) so swapping/adding providers later doesn't require touching the agent loop logic.
- On a 429/quota error from Gemini, automatically retry the same request against Groq and surface a subtle in-chat notice ("Switched to backup model due to quota limit") — don't fail silently, don't require a manual toggle for this specific case.
- If **both** providers are exhausted for the day, show a clear message ("Daily limit reached on both providers — try again later") instead of a generic error or infinite retry.
- No internet connection → detect and show a clear offline message before attempting a request.

---

## 4. Agent tool set

Tools the model can call, executed against the single bound SAF folder only:

- `list_dir(path)` — list files/folders
- `read_file(path)` — read text content
- `create_file(path, content)`
- `write_file(path, content)` — overwrite existing file (triggers backup, see §6)
- `delete_file(path)` (triggers backup, see §6)
- `rename_file(old_path, new_path)`

**Scope restriction:** tools only operate on text-based files (`.md` primarily; allow `.txt`/`.csv` if the model creates them, but the app's own file-creation should default to `.md`). Binary files (images, PDFs, etc.) should be visible in listings but not readable/writable by the agent.

**Context strategy:** do NOT send a full folder listing on every message. The model should call `list_dir` on demand when it needs to know what's in the folder. This conserves free-tier token/request quota — most messages don't need folder context at all.

**Agent loop cap:** hard limit of ~15 chained tool calls per single user message, to prevent a confused model from looping and burning the whole day's quota in one turn. If the cap is hit, stop and surface a message to me rather than continuing silently.

**File-matching rule (important):** before creating a new file, the agent should always `list_dir` first and check whether an existing file already matches the intent of my request (e.g., "add milk to my shopping list" → find and update `shopping-list.md`, don't create `shopping-list-2.md`). Only create a new file when nothing existing fits. This rule must be explicit in the system prompt.

**Freshness rule:** before editing any existing file, the agent must re-read its current on-disk content first — never assume the in-memory/chat-history version is still accurate, since I might have edited the file manually outside the chat.

**System prompt tone rule:** the agent's chat replies must be terse — a one-line confirmation of what it did ("Added 3 items to shopping-list.md"), never a conversational essay. The content belongs in the file, not the chat bubble.

---

## 5. Autonomous vs. confirm mode

- Global settings toggle: **Autonomous** (tool calls execute immediately) vs. **Confirm** (each tool call shown to me with an Approve/Reject button before executing).
- **Regardless of toggle state**, `delete_file` and `write_file` (overwrite) always show a confirm step *or* rely on the automatic backup in §6 — pick one consistent behavior, but destructive/overwrite ops must never be silently unrecoverable.
- In Confirm mode, file edits should show a **diff view** (before/after) rather than just "confirm write?" — much easier to sanity-check at a glance.

---

## 6. Backup / history layer

- Before any `write_file` (overwrite) or `delete_file`, copy the current version into a hidden `.history/` folder inside the bound folder, timestamped (e.g. `.history/shopping-list.md.2026-08-27T14-30.bak`).
- Keep a bounded number of recent backups per file (last 5–10) — prune older ones automatically so `.history/` doesn't grow forever.
- No UI needed for browsing history in v1 — just make sure the safety net exists on disk.

---

## 7. Markdown rendering (core UI feature)

- Files are Markdown. The app needs a built-in renderer (use an existing lightweight Android markdown library — do not build a parser from scratch) supporting: headers, bold/italic, tables (`| col | col |` syntax), and checkbox list items (`- [ ] item`).
- **Checkboxes must be interactive** in the rendered preview — tapping one toggles it and writes the change back to the file directly (not via chat). This is a core feature, not optional polish — it's what makes shopping/todo lists actually usable.
- Tables render as proper bordered tables, not raw pipe text.
- Live-refresh of an open preview when the agent edits that file mid-chat is a nice-to-have — acceptable to defer to v2 if it adds complexity; at minimum, the preview must refresh on re-opening the file.

---

## 8. App structure / screens

1. **Home / Dashboard** — grid or list of files in the bound folder (auto-reads folder contents), each tappable to open its rendered preview. This is the primary screen — files are the product, chat is the input method.
2. **File preview** — rendered Markdown view (interactive checkboxes, tables) with a fallback raw-text view toggle.
3. **Chat** — one continuous conversation thread for the whole bound folder (not per-file; the agent infers which file I mean from context), accessible via a persistent floating action button or tab from the Dashboard. Chat history persists locally per folder across app restarts.
4. **Settings** — API keys (Gemini, Groq), autonomous/confirm toggle, folder rebind option.

---

## 9. Debug logging

- Every agent turn writes to a local, private (app-internal storage, not inside my bound folder) log file: timestamp, which provider handled the request (Gemini/Groq), tool calls made (name + params), tool results, any errors, and any fallback/provider-switch events.
- Add a simple "View Logs" screen in Settings so I can read this without pulling the file off-device.

---

## 10. Explicit non-goals for v1

- No `.xlsx` generation — Markdown tables only.
- No multi-project/multi-folder switching UI (internal architecture should support it later, per §2.4, but the UI stays single-folder for now).
- No on-device/local LLM — phone hardware isn't suitable, free API tiers only.
- No cloud sync/backend of any kind — everything is local to the phone and the two AI provider APIs.

---

## 11. Build order (please build in this sequence, not all at once)

1. SAF folder picker + persisted permission + basic file listing (Dashboard skeleton).
2. Settings screen with encrypted API key storage.
3. Chat screen wired to Gemini only, with a single working tool (`write_file`) — get one end-to-end flow working (chat → tool call → file written → visible in folder) before adding more.
4. Add remaining tools (`list_dir`, `read_file`, `create_file`, `delete_file`, `rename_file`) + agent loop cap + file-matching/freshness rules.
5. Add Groq fallback + provider abstraction.
6. Add Markdown renderer with interactive checkboxes + table rendering.
7. Add confirm-mode diff view + autonomous/confirm toggle.
8. Add `.history/` backup layer with pruning.
9. Add debug logging + Settings log viewer.
10. Polish: offline/error states, empty-folder empty-state, dashboard refresh handling.

Please confirm the plan/architecture back to me before writing large amounts of code, and build/test incrementally per the order above rather than generating the whole app in one pass.
