# Downloads queue — concurrent downloads (max 3 at once) with a queue screen

Branch `develop`; delivery strategy `ask-on-risk`; JVM runner `./gradlew :app:testDebugUnitTest`, full check `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.

## Specs

S1. Concurrent downloads: up to **3** jobs download at the same time; further accepted jobs wait in FIFO order. (User chose "Hasta 3 a la vez".)

S2. Queue screen listing every in-progress download; each item shows a **thumbnail image**, the **work title** and its **progress**. (User verbatim: "una vista de cola de descargas en la que se enlisten las descargas en curso (cada item debe tener miniatura imagen, titulo de la obra y avance)".)

S3. At most **5** downloads may be in the queue at any time. (User verbatim: "solo permitir un maximo de 5 descargas en la cola".) The limit counts jobs that are queued or active; finished jobs leave the list and free a slot.

S4. The button that opens the queue screen uses an **icon only, no text**, and sits **next to the "Pixiv activo" badge in the header**, not in the bottom navigation. (User verbatim: "El boton que me lleve a la vista de la cola (boton con icono representativo, no texto), debe estar al lado del texto "Pixiv acivo", no en el menu en la parte inferior.")

S5. Each queue item can be cancelled individually; cancelling one item must not affect the others. (User chose "Ver y cancelar individual".)

S6. Accepted assumptions, stated to the user and not contested: a single summary notification replaces per-work notifications ("2 de 3 descargas" style with aggregate progress); queued or active jobs left by a process death are shown as interrupted on the next launch and are not resumed automatically; the thumbnail is the work cover, with a generic icon fallback when there is none.

S7. Existing behaviour that must not change: SAF folder grant, duplicate-work confirmation and `(2)` suffix naming, `grouped` decided by original page count, gallery import of the last completed download, the bottom progress card's "Abrir en galería" action for the last completed job, cancellation from the notification, and every existing user-visible string.

S8. The bottom card's "Abrir en galería" action must always open the newest finished download, and must never fail silently. It cannot depend on which job wrote the single last-status record last. (User verbatim: "el boton de ver descarga no funciona, tiene que funcionar respecto a la ultima descarga".)

S9. Queue item progress is the progress of the whole work, not of the page being transferred. Each item also shows how many pages/files it has and its type: novela, manga, ilustración or ugoira. (User verbatim: "en la vista de cola de descargas, el avance de cada item debe ser el general, no por imagen si es que es que tiene varias paginas. Y tambien debe verse la cantidad de paginas que tiene cada item y su tipo (novela, manga, ilustracion, ugoira)".)

S10. The queue screen gets a real empty state instead of one muted sentence. (User verbatim: "Dale una mejor UI a la vista de la cola cuando esta vacia".)

S11. The queue button in the header uses a vertical transfer icon with a ghost style: no outline, no filled box. (User verbatim: "Cambia el icono de ver cola a un icono transfer-vertical, y que sea estilo ghost, no outline".)

## Tasks

T1 [S1,S3,S5,S6] inline: `DownloadQueue` store with per-job state, FIFO order, 5-slot cap counting queued+active, complete/error/cancel release, aggregate progress, JSON persistence on state transitions only, interrupted recovery on load; pure state book covered by JVM tests. Commit 3f2b808. Evidence: `DownloadQueueBookTest` green.

T2 [S1,S5,S6] inline: `DownloadService` three-thread pool, per-job cancellation token, aggregated summary notification, queue store updates, unchanged SAF/naming/gallery behaviour. Commit 6627c6f. Evidence: JVM suite green, `assembleDebug` green.

T3 [S2,S4,S6] inline: header icon button next to "Pixiv activo", queue screen (thumbnail, title, per-item progress and cancel), "Descargar selección" blocked only when the queue is full. Commit ac688af. Evidence: `assembleDebug` and `assembleDebugAndroidTest` green.

T5 [S7] inline: independent verification blocker — the same work could be enqueued twice while pending, because the old global `isActive` guard was replaced by the queue-full guard. The work id is now the queue key, a duplicate in-flight work is rejected with a message, the download button shows "Descargando..." for the loaded work, a retry replaces a stale failed row, the cancellation path no longer shuts the pool down, and failed rows stay static on screen. Commit 8f12dde. Evidence: verifier probe suite committed as `QueueProbeTest`; 55 JVM tests green.

T4 [S2,S3] pending: device verification by the user (visual queue screen, 3 concurrent jobs, per-item cancel, summary notification, interrupted recovery). Requires explicit install authorization.

T6 [S8] inline: commit decba12. "Abrir en galería" resolves the newest finished download from the persisted last-completion record and then from the newest gallery entry, shows a toast instead of returning silently, and stops depending on the transient `download_status` write order. Red before the fix: the action returned early whenever the last status write was not a completed job.

T7 [S9] inline: commit decba12. per-item progress becomes the whole work's progress (`DownloadQueueBook.overall`), and each queue row shows type plus page/file count. Queue rows also stop labelling a waiting job as "En curso".

T8 [S10,S11] inline: commit decba12. queue empty state card with icon, explanation and a "Buscar una obra" action; header queue button becomes a ghost `transfer-vertical` icon and the same glyph is used by the panel action and the "Cola llena" state.

T9 [S8,S9,S10,S11] pending: device verification of the four observations after install.

## Log

L1. User request verbatim: "Bien. Ahora quiero que desarrolles la funcionalidad de descargas simultaneas, una vista de cola de descargas en la que se enlisten las descargas en curso (cada item debe tener miniatura imagen, titulo de la obra y avance), solo permitir un maximo de 5 descargas en la cola. El boton que me lleve a la vista de la cola (boton con icono representativo, no texto), debe estar al lado del texto "Pixiv acivo", no en el menu en la parte inferior. Si tienes dudas, preguntame."

L2. Concurrency answered with "Hasta 3 a la vez": 3 workers, FIFO beyond that. The current service already serialises everything on a single-thread executor and the UI aborts any new download while `DownloadStatus.isActive`, so both the executor and the guard must change.

L3. Design decisions derived from the specs. `DownloadStatus` stays as the single record for the last finished job (gallery import and "Abrir en galería" keep working); the new `DownloadQueue` owns per-job state and is authoritative while any job is pending. The bottom card shows the aggregate queue while jobs are pending and falls back to the old rendering otherwise. Because a queue of 5 only makes sense if you can enqueue a second work, "Descargar selección" is disabled only when the queue is full ("Cola llena") instead of while anything is active. A variant of S6: the notification shows `N en curso · X %` rather than `N de M descargas` because finished jobs leave the queue and there is no stable total to divide by. A failed job stays in the queue in red with a dismiss action instead of vanishing, and stale failed entries are dropped on the next process start. Process death recovery relies on `START_NOT_STICKY` (was `START_REDELIVER_INTENT`) so an interrupted queue never silently re-downloads behind an "Interrumpida" row. S7 addition required by concurrency: SAF name resolution and `.part` creation are serialised under one lock, otherwise two workers could pick the same free filename in the same tree.

L4. Verification. JVM: `DownloadQueueBookTest` (limit, FIFO, slot release, duplicate id, aggregate percent, transitions) plus the whole existing suite green, including `DownloadServiceApiTest` updated to pin the new `jobs` map instead of the removed `cancelled`/`currentWorkTitle` fields. Build: `:shared:testAndroidHostTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest` all green. Not run: instrumented persistence round-trip for `DownloadQueue` (JSON prefs need a device) and any on-device visual or concurrency check; both remain pending in T4.

L5. Independent verification (fresh copy, probes, no workspace writes) found one blocker and four advisories. Blocker: replacing the global `DownloadStatus.isActive` guard with the queue-full guard dropped in-flight duplicate protection, so a double tap enqueued the same work twice with two different keys. Fixed by keying the queue on the work id, rejecting an already-pending work, re-arming "Descargando..." on the button, and replacing a stale failed row on retry. Advisories fixed: static rows (failed/interrupted) are no longer overwritten with "En curso" every tick; `cancelAll` marks and interrupts each job instead of shutting the pool down (a late start could otherwise throw `RejectedExecutionException` and leak the pending counter), and `cancelJob`/`cancelAll` stop the service when nothing is pending; the bottom "Ver cola" action is hidden while the queue screen is already open. Accepted as-is: `notificationDetail` is now only exercised by its tests (kept as public API), and a cancel landing after completion can still relabel that finished job. Verifier also observed the workspace `testDebugUnitTest` as up-to-date rather than executed; the suite was re-run here with `--rerun-tasks` (55 tests, 0 failures).

L6. User request verbatim after installing and testing on the device: "Si funciona,pero tengo observaciones: - el boton de ver descarga no funciona, tiene que funcionar respecto a la ultima descarga. - en la vista de cola de descargas, el avance de cada item debe ser el general, no por imagen si es que es que tiene varias paginas. Y tambien debe verse la cantidad de paginas que tiene cada item y su tipo (novela, manga, ilustracion, ugoira) - Dale una mejor UI a la vista de la cola cuando esta vacia. - Cambia el icono de ver cola a un icono transfer-vertical, y que sea estilo ghost, no outline." Asked which button was meant; the user answered "Abrir en galería (panel inferior)".

L7. Root cause found for the panel action. `DownloadStatus` is a single-slot record that every concurrent job overwrites, and `cancelJob`/`cancelAll` call `DownloadStatus.write`, which removes `document_uri`. With three workers, the record behind "Abrir en galería" is whichever job wrote last, so the action can early-return silently (`saved == null`, `state != "complete"` or an empty URI) or look up a URI the gallery never received. Fix: persist the last successful completion (`last_uri`) on `completed()`, resolve the card action through it and then through the newest gallery entry, and toast when nothing can be opened instead of returning silently.

L8. Progress was genuinely per page, not per work: `save()` notifies `(i + 1) / files.length()` when a page finishes while `download()` notifies the current file's byte percentage as `percent / 100`, so the row oscillated between two scales. Now every notification is mapped to the whole work with `DownloadQueueBook.overall(pageIndex, pageCount, fraction)`, and the queue row shows type plus page/file count. Type is decided at enqueue time: novela, ugoira, manga (2+ original pages) or ilustración.

L9. Verification of T6–T8 by a fresh independent verifier (scratch copies under `/tmp/opencode`, no workspace writes). Result: no blockers, six advisories. Fixed here in commit `25542e7`: the ugoira row jumped 100 % → 0 % at the conversion boundary once progress covered the whole work (archive now fills the first half and conversion the second), and the in-place queue refresh compared only row counts, so a finished job replaced by a new one in the same tick left a stale row (now compares ids). The empty-gallery toast keeps the shipped message and only the "never downloaded" case uses the new one. Accepted as-is: the header ghost button keeps its pre-existing 36 dp touch target (enlarging it would move the "Pixiv activo" badge, and the ask was the icon and style), `overall` overflows only at implausible page counts, and two simultaneous completions can resolve to either freshly finished work. Verified: 62 JVM tests (57 + the verifier's 5 probes, committed as `DownloadProgressProbeTest`), 24 shared tests, `assembleDebug` and `assembleDebugAndroidTest` green. Not run: any instrumented or on-device check, which stays pending in T4 and T9.
