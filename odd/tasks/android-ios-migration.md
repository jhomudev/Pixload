# Migrate Pixload toward Android and iOS
Branch: develop (repository policy). Incremental delivery; no push or release authorized.
Verification: Android unit/build; iOS builds and device checks require macOS/Xcode.

## Specs
S1. "Bien. Elabora un plan para migrar la app a Kotlin para hacerla multiplataforma."
S2. "Si, será para Android y iOS. El objetivo global de ahora en adelante será tener la app disponible para esos 2 OS."
S3. "Autorizado, comienza."
S4. "Paridad con Android"

## Tasks
T1 [S1-S3] inline: done; commit 4754f2d. KMP module with Android+iOS targets, shared Pixiv link extraction wired into Android; Android host tests and full unit/APK build passed. iOS simulator tests not run on Linux.
T2 [S1-S4] blocked awaiting macOS/Xcode runtime proof; commits a316ef4, 4aa3cce (partial). Shared GIF presets and filename policy are implemented; Pixiv networking and platform contracts remain.
T3 [S1-S4] blocked awaiting macOS/Xcode access; commit pending. Implement iOS shell and platform-specific login, download, gallery, GIF/MP4.
T4 [S1-S4] blocked awaiting macOS/Xcode + iOS simulator/device proof; commit pending. Verify full Android/iOS parity and release readiness.

## Log
L1. Original request verbatim: "Bien. Elabora un plan para migrar la app a Kotlin para hacerla multiplataforma."
L2. User confirmation verbatim: "Si, será para Android y iOS. El objetivo global de ahora en adelante será tener la app disponible para esos 2 OS."
L3. Authorization verbatim: "Autorizado, comienza."
L4. Existing build: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21; Kotlin official KMP compatibility guide lists AGP through 8.11.1 for Kotlin 2.2.21, and 8.2.2–9.0.0 for Kotlin 2.3.20–2.3.21. Host is Linux; iOS compilation requires macOS/Xcode. No work outside repo or release authorized.
L5. T1 first RED: :shared:allTests exit 1, unresolved PixivLinks (after correcting initial Gradle DSL error); GREEN :shared:allTests + focused PixivLinkTest exit 0 but iOS simulator test SKIPPED. Actual :shared:testAndroidHostTest plus full Android unit/APK exit 0. Independent verifier found Kotlin Unicode case folding changed Java CASE_INSENSITIVE semantics; added lookalike domain test RED exit 1, then ASCII-only matching GREEN :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug exit 0. No macOS/iOS runtime proof; no push/release. Verifier reported creating /tmp scratch without parent authorization; parent did not read/delete it.
L6. Work-unit commit 4754f2d; RDD assess vs 5577443: nativeRisk medium, reviewDue=false (under_budget), high item 5 escalation requires independent verifier (completed before commit); no native review started. Later iOS verification needs macOS/Xcode and a device or simulator.
L7. User selected verbatim: "Paridad con Android" for the first iOS version, meaning session, illustrations, novels, downloads, gallery and GIF/MP4. No smaller-feature launch is an acceptable completion of S4.
L8. T2 slice 1 in commit a316ef4: moved GIF preset IDs, dimensions, FPS, diffusion and fallback to shared Kotlin, rewired Android callers with no encoding change. Worker RED focused shared test exit 1 (unresolved GifPreset); GREEN focused exit 0, focused Android unit exit 0. Worker offline AndroidTest build exit 1 for uncached kotlin-stdlib-common:2.3.21; parent online :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest exit 0. Device GIF tests and iOS runtime unavailable. Native committed assess vs 842bbc4: medium, reviewDue=false; no independent verifier needed. T2 is not yet complete.
L9. T2 slice 2 in commit 4aa3cce: worker moved pure filename policy to :shared, preserved DownloadService wrappers; RED unresolved DownloadNames exit 1, GREEN shared test + Android TitleNameTest exit 0, full Android unit/APK exit 0. Parent attempted iOS klib compile: RED exit 1 because @JvmStatic in commonMain (also in committed GifPreset); removed annotations and used Java INSTANCE/Companion; GREEN iOS main/test compile + shared host/Android unit/APK exit 0. Independent verifier for high-risk item 1 found no confirmed Android behavior divergence by source inspection; iOS runtime naming parity remains unproven. KMP generated .kotlin/sessions, now ignored. Final suite with expanded Unicode cases :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest exit 0; no SAF provider or iOS device run. Risk: item 1 (persisted filename policy). Native committed assess against a316ef4: medium, reviewDue=false; independent verifier already ran by Task Size item 1.
L10. Source analysis: PixivClient uses Android Uri and WebView cookies, HttpURLConnection, and org.json; DownloadService writes SAF URIs, GalleryStore uses Android SharedPreferences/URI. Further full-parity extraction needs iOS-native runtime checks of login/session, network access and persisted filenames; Linux compiled iOS klib/test code but cannot execute simulator or Xcode app. No iOS app exists yet and T2-T4 remain incomplete.
L11. User correction verbatim: "pregunta de nuevo" after first selecting "Tengo un Mac con Xcode". On the repeated question, user selected verbatim: "Todavía no tengo acceso". The second answer supersedes the first: no macOS/Xcode validation environment is presently available. T2-T4 remain blocked; do not claim an iOS binary or full parity.
