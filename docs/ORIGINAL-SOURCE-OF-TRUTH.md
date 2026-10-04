# Original behavioural source of truth recovered

The original complete handoff supplied by Kel on 4 October 2026 is the baseline product specification. Later deliberate decisions supersede only the specific behaviour they changed.

## Non-negotiable cost rule

FileMate must not add, connect to, depend on, or design around any service that can create ongoing, subscription, credit, metered, pay-per-request, hosting, or future rollover charges unless Kel explicitly approves it first.

A free tier is **not** automatically acceptable. Before any potentially chargeable external service is introduced, work must stop and explain the service, reason, cost model, local/free alternative and functionality lost without it.

Prefer Android/on-device facilities, local storage/databases and local/open-source libraries. Core FileMate must work without Google or any paid hosted dependency.

## Original Version 1 target

- AI Hub and event-based monitoring with catch-up.
- High-confidence automatic organisation; Medium suggestions; Low untouched in Needs Sorting.
- Passive learning from patterns, prior assignments and corrections.
- Project-centred organisation across providers.
- Phone cleanup, screenshots, exact/near duplicate review and strong local Gallery.
- Local metadata/history and safe Undo.
- Optional Drive integration, never foundational.
- Settings for AI apps, monitoring timeout, naming/project rules, Drive behaviour, screenshot behaviour and permissions.

## Later decisions preserved

- Projects remain manually created.
- Gallery assignment is metadata-only; personal camera media is never silently moved.
- Comparison Trash uses Android's recoverable confirmation flow.
- Favourites/albums are local metadata.
- Drive project behaviour was simplified to global Phone-only default plus per-project override.
- Emulator workflows are manual-only and must not be repeatedly run.
- Active development is in FileMate-Public; the private repository is a historical/recovery copy.
- Under Kel's clarified cost rule, do not use a Drive/API architecture that can become metered. Investigate Android's user-granted storage-provider/folder access instead.

## Recovery warning

Do not mark Version 1 complete merely because Stages 1–3D exist. Compare the app against the original target, especially automatic organisation, learning, Settings, screenshot grouping/classification, Gallery file actions, metadata depth and the optional no-metered-cost Drive route.


## Recovery implementation notes

- Monitoring timeout is now configurable locally with a safe 30-minute default.
- Reviewed file naming now has a local preference; automatic high-confidence moves preserve downloaded names.
- Screenshot groups may show review-only project suggestions when repeated local assignment patterns agree.
- No OCR dependency has been added. OCR remains optional and must be fully on-device/no-metered-cost if introduced.
- Optional external storage now uses Android user-granted document-provider folders, with verified copies and no developer API/OAuth/network permission. A provider-only option removes a local file only after the external copy is read back and hash-verified.
- Reviewed Gallery move/rename uses the existing verified transfer journal and Activity/Undo safeguards; camera media is never automatically selected.
