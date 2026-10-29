# Selected Pixload improvements
Branch: `develop` (agent 1); agent 2 owns EPUB in an isolated worktree. Delivery strategy: feature-branch-chain (user selected); forecast >400 authored lines across independent work units.
Runner: `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug`; Android instrumentation separately.

## Specs

S1. User: "Desarrollar: 1,2,6,8". Improvement 1 from the preceding list: "Buscar, filtrar y ordenar la galería por título, formato y fecha". Acceptance assumptions from PLAN.md: search by title case-insensitively, filter available types, sort by date; no mutation of stored gallery history, localized empty state.
S2. Improvement 2: "Reintentar descargas fallidas o interrumpidas desde la cola". User confirmed verbatim: "Bien, \"si\" en ambas decisiones a cerrar." The first question was "si «Reintentar» debe conservar exactamente las páginas y opciones originales". Preserve selected pages, format, folder and options; retry creates a new transfer, never resumes bytes.
S3. Improvement 6: "Más formatos para novelas, empezando por EPUB además de TXT". Agent 2 owns this in its separate worktree; agent 1 owns integration only once delivered. No internal EPUB reader.
S4. Improvement 8: "Control de descargas según la conexión —por ejemplo, «solo Wi‑Fi»—". User confirmed verbatim: "Bien, \"si\" en ambas decisiones a cerrar." The second question was "si «solo Wi‑Fi» debe aceptar cualquier red Wi‑Fi aunque tenga límites de datos". Default off; explicit waiting state; no automatic restart after reboot.
S5. User: "Ignorar: 3,4,5,7,9,10". Exclude favorites, backup/import history, internal reading options, automatic resume and iOS.
S6. User: "Muy bien, usemos worktrees. crea un PLAN.md (temporal) donde figure el plan y dejes en claro que tareas le corresponden a ambos agentes (agente 1 y agente 2). Tu eres el agente 1, yo indicaré al agente 2 que siga el plan.Avisame antes de que inicies tus tareas." Agent 1 informed the user before beginning and owns stages 1, 2, 4; agent 2 owns EPUB.

## Tasks

T1 [S1,S5] delegated (context backstop): done; gallery query/filter/order, RED/GREEN host tests and docs; commit 85f7e28; native assessment medium, reviewDue=false (defer to slice).
T2 [S2,S5] delegated (context backstop) after T1: in_progress; retry from saved selections with tests and documentation; commit pending.
T3 [S4,S5] inline after T2: pending; Wi-Fi policy with tests and documentation; commit pending.
T4 [S3,S6] integration: pending; integrate agent 2 EPUB worktree delivery, run combined checks; commit reference pending.
T5 [S1-S6] inline: pending; final checks, device proof where authorized/available, record review and delivery boundaries.

## Log

L1. User original request verbatim: "te paso lo que si desarrollaremos e lo que ignoraremos segun el numero de la prioridad que diste. Genera un plan para lo que si se desarrollará.\n- Desarrollar: 1,2,6,8\n- Ignorar: 3,4,5,7,9,10"
L2. User correction verbatim: "Bien, \"si\" en ambas decisiones a cerrar. antes de iniciar dime que etapas se pueden realizar en paralelo para que otro agente apoye tambien."
L3. User authorization verbatim: "ok, inicia con lo tuyo".
L4. Planning: worktree principal is clean on develop at 4f5c590, ahead of origin by 3. `PLAN.md` ignored, provides separate agent assignments. Current gallery cached listing in MainActivity.kt; queue persists card metadata only; DownloadService receives full JSON in start intent and is START_NOT_STICKY. Agent 1 informed user before implementation. T1 delegated because runtime context backstop fired.
L5. T1: writer observed RED compilation before GalleryQuery existed, GREEN focused GalleryQueryTest with JDK 21/SDK 35, full `:shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug` exit 0, git diff --check exit 0. Parent read diff/test and committed 85f7e28 (202 additions, 8 deletions including task document). Assessment 4f5c590..85f7e28 medium, due=false, no independent verifier; device interaction still untested. Risk: none (gallery history unchanged; presentation-only query).
L6. User selected feature-branch-chain via the oversized-delivery menu. This sets delivery structure, not push/PR authorization. Previous commits on develop predate this feature; feature slice 1 is gallery commit 85f7e28. Next slice is retry, separate from Wi-Fi and agent 2 EPUB. T2 delegated due to runtime context backstop.
