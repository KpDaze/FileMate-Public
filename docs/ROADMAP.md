# FileMate Version 1 completion roadmap

This file records the current native source state. Historical emulator evidence remains in VERIFICATION.md and RUNTIME-VERIFICATION.md.

## Implemented in native source

- AI Hub launches selected installed AI apps after event-driven monitoring is ready.
- Live Downloads/Documents monitoring, inactivity stop, manual Stop and reopen catch-up.
- User-created Projects, Recent, Needs Sorting, batch assignment and deliberate phone cleanup.
- Fingerprint-verified file moves/renames with collision protection, Activity history and Undo.
- Recovered core behaviour: a live AI download is automatically organised only when both AI-source evidence and one existing project match are High confidence. Ambiguous files stay untouched.
- Passive local learning from reviewed file/Gallery assignments. Learned clues are Medium-confidence suggestions only and never cause an automatic move by themselves.
- Local Gallery, screenshots, project assignment, exact/similar/version comparison, recoverable Android Trash/restore, Favourites and Albums.
- Phone-only default and per-project Phone + Drive rule storage. Changing the rule never retroactively moves existing files.

## Still required for full Version 1

### Google Drive connection and operations

The approved product target remains optional Google Drive in the same app: account connection, browse/search, folders, move/rename, selected upload, project organisation, existing-Drive review and safe retry/offline behaviour. Drive-after-upload may remove a local copy only after successful upload is independently verified.

No Google OAuth client configuration or Drive credential setup exists in either FileMate repository as of this recovery pass. The app must not pretend Drive is connected or upload/remove files until a real Google client is configured. Core local FileMate operation remains independent of Google.

### Verification and delivery

Run non-emulator build/JVM/lint checks before any Android runtime work. Emulator runs remain manual-only and require explicit approval; do not repeatedly dispatch them. Physical-phone acceptance uses disposable files first. A final phone update must use the preserved personal signing identity and a version code above the installed build.

## Not required for Version 1

Google Play publishing, web/iPhone ports, multi-user support, a paid/cloud backend, paid AI APIs, access to other apps' private data, 24/7 monitoring, recurring Downloads polling, automatic camera-photo organisation, face/people/pet recognition, or a full Google Photos replacement.
