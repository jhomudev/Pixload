# Publish Pixload v0.7.0
Branch: develop; promote verified release to main. Delivery: exception-ok, single requested release, no PR.
Runner: shared/app JVM tests, debug/androidTest/release builds, APK signature/version/checksum verification, published asset readback.

## Specs
S1. "Bien, funciona bien. ahora haz push tanto a develop como a master y luego un despliegue nuevo."
S2. Assumption: the repository has no `master` branch; `main` is the production and default branch, so "master" maps to `main` as in the v0.6.0 release.
S3. Assumption: next minor version v0.7.0, Android versionCode 7, following the v0.6.0 release process.
S4. Device validation of the two viewer fixes is the user's own evidence ("funciona bien"); no new agent-run device tests are required for this release.

## Tasks
T1 [S1-S4] inline, done: version metadata 7/0.7.0 and release record. Commit 4ea0ebe.
T2 [S1-S4] inline, done: host tests, signed release APK build, artifact verification.
T3 [S1-S4] inline, pending: push develop, fast-forward main, annotated tag v0.7.0, GitHub release with asset verification.

## Log
L1. Original request verbatim: "Bien, funciona bien. ahora haz push tanto a develop como a master y luego un despliegue nuevo."
L2. Starting state: develop at 2a01f5c, two commits ahead of origin/develop (18c710f); main at 8069398 (v0.6.0); `git merge-base --is-ancestor origin/main develop` succeeds, so promotion to main is a fast-forward. There is no local or remote `master` branch. Working tree clean.
L3. Release candidate is the 33 commits between main and develop (47 files, 4417 insertions, 104 deletions), covering gallery rendering, GIF performance and palettes, MP4 MediaCodec encoding, and the fullscreen viewer fixes validated by the user on the device.
L4. Verification: `:shared:testAndroidHostTest` 24 tests and `:app:testDebugUnitTest` 142 tests, all with 0 failures and 0 errors; `:shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` reported BUILD SUCCESSFUL.
L5. Signed release artifact: `app/build/outputs/apk/release/app-release.apk`, 3375667 bytes, versionCode 7, versionName 0.7.0, SHA-256 46910205036483d4e33fa9defdba63931e632cd44b54bf6937ceffae24598b91. APK Signature Scheme v2 verifies; signer certificate SHA-256 7ccc0f876b4295dac0eaa5de572500decc1c6e2a44610ebd8286a3a6a45d2127 matches v0.6.0.
L6. Review gate: assess over the unreviewed unit 18c710f..HEAD (viewer fixes, evidence, release metadata) reported medium risk and `review_due: false` with reason `under_budget` (7 paths, 278 lines), so no native review ran. Assess over the whole branch origin/main..HEAD reported high only because of the versioned `opencode.json` process boundary; that tooling file was reviewed during v0.6.0 and no review transaction was created for this release.
