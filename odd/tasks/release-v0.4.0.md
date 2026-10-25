# Publish Pixload v0.4.0
Branch: develop; main reserved for official production releases. Delivery strategy: one release unit, no PR requested.
Runner: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` with signing env loaded from project .env.

## Specs
S1. "Yo lo testeé, todo ok,si funciona." Evidence: user's manual confirmation; do not claim agent manual test.
S2. "Pushea los cambios haciendo un nuevo despliegue de la app." Check: changes pushed, new signed release APK published, tag and asset verified remotely. Assumption: next minor v0.4.0 from latest v0.3.0, following previous releases targeted at main.

## Tasks
T1 [S1-S2] inline: done; commit none. Inspect remote release convention and signing setup.
T2 [S2] inline: done; commit a63cfc0. Version 0.4.0, unit/debug and release build exit 0; APK verified with same signing certificate as v0.3.0.
T3 [S2] inline: done; commit a63cfc0. Pushed develop/main, v0.4.0 tag, release; corrected asset to Pixload-v0.4.0.apk.
T4 [S2] inline: done; commit none (verification only). Remote asset downloaded and checksum, tag, branches and release metadata verified.

## Log
L1. Original request verbatim: "Yo lo testeé, todo ok,si funciona. Pushea los cambios haciendo un nuevo despliegue de la app."
L2. Latest v0.3.0 release targets main with Pixload-v0.3.0.apk; local develop is 5 commits ahead; main/develop remote both at v0.3.0. User's pre-existing .gitignore edit stays out of commits. Project .env contains signing variable names but values not printed; keystore path resolves outside project to /home/jhonan/.local/share/pixload/release.jks.
L3. User selected verbatim: "Sí, usar ese keystore para firmar v0.4.0". Grant scoped to that exact file, not other external paths. app/build.gradle bumped versionCode 4/versionName 0.4.0. JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/home/jhonan/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon exit 0; :app:assembleRelease exit 0 with signing vars loaded without printing them.
L4. `apksigner verify --print-certs` on v0.3.0 and v0.4.0 passed; certificate SHA-256 equal (7ccc0f876b4295dac0eaa5de572500decc1c6e2a44610ebd8286a3a6a45d2127); `aapt dump badging` confirmed versionCode 4/versionName 0.4.0. New APK SHA-256 dd63018ad3610ffe48c53abe004407fced7b0572dd1d114120c321be09e748c8. Initial apksigner invocation failed because java absent from PATH; diagnosed and reran with JBR PATH; no artifact verification was claimed for the failed attempt.
L5. Commit a63cfc0 contains release metadata. RDD assess vs 680d745 nativeRisk medium reviewDue=false, escalated high item 5; independent verifier found no pre-publication blocker. Remote origin/develop and origin/main fast-forwarded from 25682b2 to a63cfc0; annotated tag v0.4.0 pushed and dereferences to a63cfc0. GitHub release created: https://github.com/jhomudev/Pixload/releases/tag/v0.4.0.
L6. First remote verification failed: `gh release download --pattern Pixload-v0.4.0.apk` found no match. `gh release view` showed upload name app-release.apk despite CLI #label; digest matched expected. Correctly named Pixload-v0.4.0.apk uploaded with same digest; redundant app-release.apk removed. This was a packaging-name incident, not a signing failure.
L7. Remote download of Pixload-v0.4.0.apk succeeded; SHA-256 dd63018ad3610ffe48c53abe004407fced7b0572dd1d114120c321be09e748c8 equals local signed APK. GitHub release is public (not draft/prerelease), target main, one asset of 1,925,720 bytes with matching digest. Remote refs develop/main and dereferenced tag v0.4.0 all point to release commit a63cfc0. No missing proof or pending checks. Only pre-existing .gitignore edit remains out of scope.
