# Publish Pixload v0.7.0
Branch: develop; promote verified release to main. Delivery: exception-ok, single requested release, no PR.
Runner: shared/app JVM tests, debug/androidTest/release builds, APK signature/version/checksum verification, published asset readback.

## Specs
S1. "Bien, funciona bien. ahora haz push tanto a develop como a master y luego un despliegue nuevo."
S2. Assumption: the repository has no `master` branch; `main` is the production and default branch, so "master" maps to `main` as in the v0.6.0 release.
S3. Assumption: next minor version v0.7.0, Android versionCode 7, following the v0.6.0 release process.
S4. Device validation of the two viewer fixes is the user's own evidence ("funciona bien"); no new agent-run device tests are required for this release.

## Tasks
T1 [S1-S4] inline, pending: version metadata 7/0.7.0 and release record.
T2 [S1-S4] inline, pending: host tests, signed release APK build, artifact verification.
T3 [S1-S4] inline, pending: push develop, fast-forward main, annotated tag v0.7.0, GitHub release with asset verification.

## Log
L1. Original request verbatim: "Bien, funciona bien. ahora haz push tanto a develop como a master y luego un despliegue nuevo."
L2. Starting state: develop at 2a01f5c, two commits ahead of origin/develop (18c710f); main at 8069398 (v0.6.0); `git merge-base --is-ancestor origin/main develop` succeeds, so promotion to main is a fast-forward. There is no local or remote `master` branch. Working tree clean.
L3. Release candidate is the 33 commits between main and develop (47 files, 4417 insertions, 104 deletions), covering gallery rendering, GIF performance and palettes, MP4 MediaCodec encoding, and the fullscreen viewer fixes validated by the user on the device.
