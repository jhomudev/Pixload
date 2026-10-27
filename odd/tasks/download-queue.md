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

## Tasks

T1 [S1,S3,S5,S6] inline: `DownloadQueue` store with per-job state, FIFO order, 5-slot cap counting queued+active, complete/error/cancel release, aggregate progress, JSON persistence on state transitions only, interrupted recovery on load; pure state book covered by JVM tests. Commit: pending.

T2 [S1,S5,S6] inline: `DownloadService` three-thread pool, per-job cancellation token, aggregated summary notification, queue store updates, unchanged SAF/naming/gallery behaviour. Commit: pending.

T3 [S2,S4,S6] inline: header icon button next to "Pixiv activo", queue screen (thumbnail, title, per-item progress and cancel), "Descargar selección" blocked only when the queue is full. Commit: pending.

T4 [S2,S3] inline: device verification by the user and logbook close. Commit: pending.

## Log

L1. User request verbatim: "Bien. Ahora quiero que desarrolles la funcionalidad de descargas simultaneas, una vista de cola de descargas en la que se enlisten las descargas en curso (cada item debe tener miniatura imagen, titulo de la obra y avance), solo permitir un maximo de 5 descargas en la cola. El boton que me lleve a la vista de la cola (boton con icono representativo, no texto), debe estar al lado del texto "Pixiv acivo", no en el menu en la parte inferior. Si tienes dudas, preguntame."

L2. Concurrency answered with "Hasta 3 a la vez": 3 workers, FIFO beyond that. The current service already serialises everything on a single-thread executor and the UI aborts any new download while `DownloadStatus.isActive`, so both the executor and the guard must change.
