# Migrate Pixload toward Android and iOS
Branch: develop (repository policy). Incremental delivery; no push or release authorized.
Verification: Android unit/build; iOS builds and device checks require macOS/Xcode.

## Specs
S1. "Bien. Elabora un plan para migrar la app a Kotlin para hacerla multiplataforma."
S2. "Si, será para Android y iOS. El objetivo global de ahora en adelante será tener la app disponible para esos 2 OS."
S3. "Autorizado, comienza."

## Tasks
T1 [S1-S3] inline: done; commit pending. KMP module with Android+iOS targets, shared Pixiv link extraction wired into Android; Android host tests and full unit/APK build passed. iOS simulator tests not run on Linux.
T2 [S1-S3] blocked pending iOS first-release scope decision; commit pending. Extract shareable domain/network rules without changing Android behavior.
T3 [S1-S3] blocked pending iOS first-release scope decision and macOS/Xcode access; commit pending. Implement iOS shell and platform-specific features.
T4 [S1-S3] blocked pending macOS/Xcode + iOS device proof; commit pending. Verify parity and release readiness on both platforms.

## Log
L1. Original request verbatim: "Bien. Elabora un plan para migrar la app a Kotlin para hacerla multiplataforma."
L2. User confirmation verbatim: "Si, será para Android y iOS. El objetivo global de ahora en adelante será tener la app disponible para esos 2 OS."
L3. Authorization verbatim: "Autorizado, comienza."
L4. Existing build: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21; Kotlin official KMP compatibility guide lists AGP through 8.11.1 for Kotlin 2.2.21, and 8.2.2–9.0.0 for Kotlin 2.3.20–2.3.21. Host is Linux; iOS compilation requires macOS/Xcode. No work outside repo or release authorized.
L5. T1 first RED: :shared:allTests exit 1, unresolved PixivLinks (after correcting initial Gradle DSL error); GREEN :shared:allTests + focused PixivLinkTest exit 0 but iOS simulator test SKIPPED. Actual :shared:testAndroidHostTest plus full Android unit/APK exit 0. Independent verifier found Kotlin Unicode case folding changed Java CASE_INSENSITIVE semantics; added lookalike domain test RED exit 1, then ASCII-only matching GREEN :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug exit 0. No macOS/iOS runtime proof; no push/release. Verifier reported creating /tmp scratch without parent authorization; parent did not read/delete it.
L6. Scope question pending: whether first iOS release must match the Android feature set or may ship a smaller functional slice. This affects what is extracted into shared code next. Later iOS verification needs macOS/Xcode and a device or simulator.
