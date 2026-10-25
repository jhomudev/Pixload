# Complete pending TODO items
Branch: develop (repository policy). Delivery strategy: auto-chain, chain_strategy=feature-branch-chain (user choice; no push/PR authorized). Forecast >400 authored lines across UI language, SAF naming and duplicate handling.
Test runner: `./gradlew :app:testDebugUnitTest :app:assembleDebug`; Android instrumented tests where available.

## Specs
S1. "Agregar ajuste de idioma de la app, en la vista Ajutes (English y Español)," Check: language switch persists and visible application copy changes in both directions.
S2. "No poner slug al final de las descargas, ya sean carpetas o archivos. Poner solo el titulo de la obra como nombre." Check: first download uses sanitized work title (and file extension), without slug.
S3. "Si una obra se descarga por 2da vez, mostrar aviso de que la obra ya esta descargada con opciones de continuar y cancelar. En caso de continuar con la descarga, la descarga debe poner el sufijo "(2)" al archivo/carpeta" Check: selected destination duplicate prompts before queueing, cancel has no effect, continue saves with (2) before extension for a file.
S4. "Solo en la carpeta elegida" (user's chosen option). Check: another destination does not trigger duplicate warning. Assumption: identify work by Pixiv ID, retain legacy entries without ID without a speculative match; later repeats use next free numbered suffix without overwriting.

## Tasks
T1 [S2] inline: done; commit pending naming+identity unit; RED two missing-symbol compile exits 1; GREEN TitleNameTest + full unit/APK exit 0. Replace random naming with deterministic first/next available names and tests.
T2 [S3-S4] delegated (context backstop): done; commit pending with T1 naming unit; RED unavailable (worker could not run build); GREEN Gradle unit+APK exit 0 and GalleryStoreTest 3/3 on device. Persist work/destination identity and confirm duplicate in selected destination.
T3 [S1] inline/delegated (context backstop): pending; commit pending; RED/GREEN pending. Add persisted English/Spanish selection and localize app UI.
T4 [S1-S4] inline: pending; commit pending. Complete cross-feature checks and mark TODO.

## Log
L1. User request verbatim: "analiza el todo.md y realiza las tareas pendientes."
L2. Original pending entries verbatim:
[ ] Agregar ajuste de idioma de la app, en la vista Ajutes (English y Español),
[ ] No poner slug al final de las descargas, ya sean carpetas o archivos. Poner solo el titulo de la obra como nombre.
[ ] Si una obra se descarga por 2da vez, mostrar aviso de que la obra ya esta descargada con opciones de continuar y cancelar. En caso de continuar con la descarga, la descarga debe poner el sufijo "(2)" al archivo/carpeta
L3. User chose verbatim: "Solo en la carpeta elegida". Existing `.gitignore` modification is pre-existing and out of scope; TODO.md is currently ignored by that modification.
L4. User chose verbatim: "Cadena en rama de funcionalidad (recomendado)". Work remains on develop per repository policy; no publication authorized.
L5. T2 writer produced identity and confirmation changes, but could not observe RED due its scoped edit surfaces. Parent: JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/jhonan/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon exit 0 (23s); :app:assembleDebugAndroidTest + install after confirming no running DownloadService, GalleryStoreTest OK (3 tests), instrumentation exit 0.
L6. T1: first RED `TitleNameTest` missing collisionName compile exit 1; GREEN focused exit 0; second RED missing startOrdinal compile exit 1; GREEN focused and full unit+APK exit 0. SAF real end-to-end download not run (requires Pixiv/account and selected provider). Numbering forces (2) on a confirmed repeat even when format changes. Diff check passed.
