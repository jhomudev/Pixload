# Publish Pixload v0.6.0
Branch: develop; promote verified release to main. Delivery: exception-ok, single requested release, no PR; feature work delivered in reviewable slices across 31 existing commits plus one interactive gallery unit.
Runner: shared/app JVM tests, debug/androidTest/release builds, APK signature/version/checksum verification, published asset readback.

## Specs
S1. "Agregamos nuevas funcionalidades a la app desde el ultimo despliegue. Quiero que hagas push y haz un nuevo despliegue de la app."
S2. "Publicar todo" — selected authorization to include local changes, use the configured GitHub session, existing signing and main promotion.
S3. "Yo hice todas las pruevas en mi telefono. Ahora quita los worktrees si es que ya no los necesitamos y luego haz push de los commits y un nuevo despliegue." Device runtime validation is the user's own evidence; it is not an agent-run instrumentation result.
Assumption: next minor version v0.6.0, Android versionCode 6, following the v0.5.0 release process.

## Tasks
T1 [S1-S3] inline, done: remove the merged EPUB worktree; commit the pending interactive gallery work. Commit 1b472f3.
T2 [S1-S3] inline, pending: version metadata and signed APK; independent artifact verification. Commit: pending.
T3 [S1-S3] inline, pending: push develop, fast-forward main, annotated tag, GitHub release; verify published asset digest and refs. Commit: pending.

## Log
L1. Original request verbatim: "Yo hice todas las pruevas en mi telefono. Ahora quita los worktrees si es que ya no los necesitamos y luego haz push de los commits y un nuevo despliegue."
L2. Starting state: develop at 9228f51, 30 commits ahead of origin/develop (c33259a); main at 30490ee (v0.5.0); versionCode 5/0.5.0. One linked worktree agent-2-epub at 197e57d, clean, and `git merge-base --is-ancestor agent-2/epub develop` confirmed it was already integrated. Worktree removed and branch deleted; only the primary worktree remains.
L3. Pending work was two behaviours in one file: gallery filter popup with multi-select types, and follow-finger fullscreen swiping with a stable shell. Splitting them would produce an intermediate commit that does not compile because render(), dispatchTouchEvent and fullscreenGallery share hunks; committed as one unit 1b472f3 (910 authored lines including 499 test lines). Overages reported rather than met by compressing code or dropping tests.
L4. No agent-run instrumentation in this release: the connected device ZY22H65ZBW carries user data and DownloadService state, and installing a new APK was not authorized for this release. The user's own device validation is recorded as S3, not as agent evidence.
