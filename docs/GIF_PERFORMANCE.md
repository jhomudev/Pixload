# GIF: diagnóstico y optimización (corpus sintético)

Latest measurement-only follow-up: [QUALITY GIF with 1080p/4K synthetic sources](GIF_QUALITY_PROFILE.md). Its instrumented processes reported a 256MiB heap limit; older heap figures below are historical, not current device guarantees.

Concurrency follow-up: [closed partial profile and sequential palette-variation control](GIF_CONCURRENT_PROFILE.md). Exact decoded parity failed; the follow-up does not establish a concurrency regression or authorize an app change.

Equipo: motorola edge 30 pro, heap máximo Android 512 MiB. Las pruebas instrumentadas generan JPEG a partir de gradientes RGB; **no** son descargas de Pixiv ni una evaluación visual de una obra real. El ZIP y los GIF se escriben en caché durante los benchmarks; la aplicación sigue guardando descargas en la carpeta SAF elegida. El codificador corre en el ejecutor de `DownloadService`, no en el hilo principal. Se usó `System.nanoTime()` para medir cada fase de una copia *solo de prueba* del código fuente de Square gifencoder 0.10.1 (`app/src/androidTest/java/com/pixload/bench/square/`).

## Deterministic palettes — Android functional subproof (2026-11-06)

**17/17 synthetic functional tests passed; final acceptance remains pending.** One filtered instrumentation invocation executed 13 GIF methods from `UgoiraConverterTest` and all four `IndexedGifWriterTest` cases. No MP4-only test, benchmark, baseline reinstall or retry ran. ADB exited 0; parsed per-test completion codes were all 0, with zero failures/errors/skips, runner summary `OK (17 tests)` and final `INSTRUMENTATION_CODE: -1`. Runner duration was 4.204 s, not a paired performance measurement.

Parent spot-check: after independently confirming package-scoped services still reported `(nothing)`, the same 17-test filter ran once more and passed 17/17, exit 0, without source/APK changes or reinstall. Raw output and parsed results are retained as `parent-functional-spotcheck.log` / `.json` in the evidence root below. This was a verification run, not a retry of a failure or a performance comparison.

The sole authorized wireless binding was `adb-ZY22H65ZBW-ja0hNt._adb-tls-connect._tcp`. Read-only identity checks returned Motorola edge 30 pro, product `hiphi_g`, device `hiphi`, SDK 34. Existing and post-install app identity was `com.pixload.app`, versionCode 6 / versionName 0.6.0; the installed runner was `com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner`, targeting that app.

`dumpsys activity services com.pixload.app` reported `(nothing)` initially, immediately before each install, and after the tests. Source inspection confirmed that `DownloadService` owns the job executor, enters foreground for pending work and stops when empty. Both in-place installs exited 0 / `Success`; `-r` preserved existing app data, with `-t` for the test APK. No uninstall, clear, downgrade, permission grant, force-stop, global setting change, reconnection or user-content inspection was used.

| APK verified before installation | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `e2ea42cb7bd50d76da6796befc303c6b41aa3495291249a623a3dab26c94fd76` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `91856343854b31895376ff3667ffb42a224bde706681f1a805df58be3195975d` |

Exact commands, with the filter quoted as one argument:

```sh
ADB=/home/jhonan/Android/Sdk/platform-tools/adb
DEVICE=adb-ZY22H65ZBW-ja0hNt._adb-tls-connect._tcp
"$ADB" -s "$DEVICE" shell dumpsys activity services com.pixload.app
"$ADB" -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$DEVICE" shell dumpsys activity services com.pixload.app
"$ADB" -s "$DEVICE" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
FILTER='com.pixload.app.UgoiraConverterTest#convertsToAnimatedGif,com.pixload.app.UgoiraConverterTest#decodesAnimatedGifWithChangingColors,com.pixload.app.UgoiraConverterTest#gifPreservesColorsInsideCoarsePaletteBuckets,com.pixload.app.UgoiraConverterTest#convertsGradientWithMoreThan256Colors,com.pixload.app.UgoiraConverterTest#qualityPreservesNearbyPaletteColors,com.pixload.app.UgoiraConverterTest#fastAndBalancedEncodingRemainStable,com.pixload.app.UgoiraConverterTest#balancedGifIsRepeatableAcrossIndependentConversions,com.pixload.app.UgoiraConverterTest#qualityGifIsRepeatableAcrossIndependentConversions,com.pixload.app.UgoiraConverterTest#convertsFullSizeColorfulGif,com.pixload.app.UgoiraConverterTest#gifPreservesResolutionNearSizeLimit,com.pixload.app.UgoiraConverterTest#gifAccumulatesVariableDelayRounding,com.pixload.app.UgoiraConverterTest#gifCapsFrameRateWithoutChangingTotalDuration,com.pixload.app.UgoiraConverterTest#gifCancellationIsCooperativeBeforeSampling,com.pixload.app.IndexedGifWriterTest'
"$ADB" -s "$DEVICE" shell am instrument -w -r -e class "$FILTER" com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
```

Both `balancedGifIsRepeatableAcrossIndependentConversions` and `qualityGifIsRepeatableAcrossIndependentConversions` passed their sequential/two-worker byte comparisons, small/>256-color synthetic fixtures, 64×64/200 ms decode/frame-change assertions and exact progress sequence. The unchanged `fastAndBalancedEncodingRemainStable` passed both original decoded SHA-256 baselines: FAST `4b11c2ecb49a080386a5165ea8ebc45ededab9a37dee07cb963bf7a9cd3d3c6a`, BALANCED `4f150c6c9249e8de8998e544792126882b01b4279e512c2f776e4dcd69896357`. Nearby-color, gradient, scaling, timing and cooperative cancellation cases passed. All writer cases passed fixed-palette Square pixel/timing parity, dictionary growth/reset decoding, rejected-input/interruption and caller-stream failure/ownership contracts.

Evidence retained for parent cleanup: `/tmp/opencode/palette-android-k6OLhK/`, including exact command/exit metadata, raw runner output, `functional-results.json`, APK/source hashes and service/package checks. No production/test source, assertions, dependencies or APKs were changed or rebuilt; only this report and the task status/log were edited. Installed skills resolved through native `skill://` identifiers (`ponytail`, `find-docs`, `context7-mcp`, `cognitive-doc-design`); `mem_save` is not mounted. Current official [ADB](https://developer.android.com/tools/adb) and [runner](https://developer.android.com/reference/kotlin/androidx/test/runner/AndroidJUnitRunner) documentation was queried through Context7.

**User-directed omission (2026-11-06):** the user asked to skip the real-artwork comparison and move to the next work. That comparison was not performed and has no quality verdict. Paired baseline/current conversion time and memory, and cross-device behavior, remain unmeasured; further measurement needs an explicit scope. Synthetic `Movie` assertions establish only their explicit contracts. The earlier closed PARTIAL decoded-parity investigation remains unchanged.

## Deterministic BALANCED/QUALITY palettes — host proof (2026-11-05)

**PC implementation verified; Android acceptance PENDING.** BALANCED and QUALITY now use an app-local adaptation of Square gifencoder 0.10.1 median cut, retaining its Apache-2.0 copyright/license header and existing license asset. Both presets canonically order the <=256-color bypass too. FAST still uses the original UniformQuantizer and original output order.

Local implementation commit: `c7199de`. The user-consented native risk/readability/reliability review reported no findings and its exact acknowledgement completed. This review does not establish the pending Android, visual or paired-performance acceptance.

The largest channel spread still wins, with R/G/B channel tie priority. Split sorting compares the exact selected component, then exact R/G/B; the median is floor(total occurrences / 2), including repeated colors crossing the boundary. Equal-spread clusters use invocation-local creation order (root, then left/right). Centroids accumulate canonically sorted distinct colors with their original counts using Square's unchanged `Color.scaled`, `plus` and final reciprocal scaling. Exact duplicate centroids are removed and the final palette is lexicographically ordered without rounding through RGB bytes. There is no shared mutable tie state or lock.

Some selected colors and nearest-color tie outcomes may deliberately change. Sampling, presets, dither arithmetic/LUT/cache, encoder, timing, progress, cancellation and stream ownership are unchanged. The earlier closed partial decoded-parity investigation remains partial; these host results neither prove its Android cause nor reclassify it as fixed.

| PC evidence | Observed result |
| --- | --- |
| Existing focused compatibility before seam / after fix | 10 / 10 tests passed; no failures, errors or skips |
| Behavioral RED on the old selection seam | 10 tests ran; 8 assertion failures, including repeatability/concurrency, exact ordering, weighted/tie oracles and a one-bit centroid difference |
| Initial GREEN / precision / durable independent regressions | 10 / 12 / 15 tests passed |
| Final full app host suite after regression persistence | 142 tests, no failures/errors/skips |
| Shared host results | 24 tests, no failures/errors/skips; task up-to-date |
| Debug application and androidTest APKs | Both compiled successfully; no installation/execution |

All required commands ran foreground with existing JDK 21 / SDK 35 and cached dependencies:

```sh
./gradlew :app:testDebugUnitTest --tests com.pixload.app.DeterministicMedianCutQuantizerTest --offline --console=plain
./gradlew :app:testDebugUnitTest --tests com.pixload.app.PaletteDithererTest --tests com.pixload.app.PaletteLookupDithererTest --tests com.pixload.app.IndependentIndexCacheTest --tests com.pixload.app.FastGifEncoderTest --offline --console=plain
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

An isolated JVM runtime probe compared baseline `f25a44e` selection (with a verified behavior-preserving seam) against current production classes. Five FAST palettes matched exact ordered component bits, and their actual indexed-dither/writer GIF bytes matched. Null rejection outcomes matched; missing/unknown IDs still select BALANCED. Old >256-color median cut is not assumed repeatable when checking default dispatch. Candidate BALANCED/QUALITY palette-to-dither-to-writer smoke passed for 1/2/255/256/257 colors, each with three reverse-insertion/equal-new-instance repeats. This exercises host GIF construction, not Android decoding.

Independent HOST verification reported no blockers: 139 app / 24 shared tests, both APKs, 16 FAST baseline/current exact-palette and GIF differentials, and three independent probes passed. The parent reported that the first two probes failed on baseline and passed on the candidate; the expanded three-probe suite passed on the candidate. These are separate verifier observations, not a new writer RED run.

Those probes are now durable Kotlin regressions in `DeterministicMedianCutQuantizerTest`, preserving all twelve earlier cases. An expanded-occurrence/index-cut reference remains structurally independent of production's counted splits; it checks exact centroid bits, creation-order ties, shuffled equal-valued colors and original counts/references across 24 seeded trials. Explicit weighted-boundary and RGB-cube expectations validate the reference too. The integration matrix covers 0/1/2/255/256/257/513 colors, FAST/default selection and six concurrent palette-to-dither-to-two-frame-writer byte comparisons per nonempty affected preset/input. Empty palettes are checked only at selection, not passed to dithering. Writer observations after this test-only port: focused 15 tests and full 142 app tests passed, shared 24 results up-to-date, both APK tasks successful; no new RED, required failure or production change.

Evidence/scratch retained outside the worktree: `/tmp/opencode/deterministic-palettes-50qkj0qn/` (command logs, RED/final XML, baseline source, compiler/probe sources and outputs). Regression persistence added `durable-regression-focused.log`, `durable-regression-full.log` and `regression-port-*` XML directories there; the independent verifier root `/tmp/opencode/palette-host-verifier-xg9awc/` is untouched. An initial isolated compiler attempt lacked Kotlin friend visibility; an initial probe incorrectly required old >256-color quantization to repeat. Both earlier harness issues were corrected and retained in logs; no required Gradle command had an unexpected failure. No separate production refactor followed GREEN.

**Phone proof update:** the two additive BALANCED/QUALITY repeatability cases and unchanged Android assertions/digests passed in the dated functional subproof above. Real-artwork comparison is omitted at the user's request; paired conversion time/memory is unmeasured. No visual-quality, performance, memory or cross-device guarantee is claimed.

## Indexed LUT index cache — accepted measurement (2026-11-04)

**Accepted in `60b2ee8`:** cache the existing palette index once per non-precise LUT cell instead of looking it up by `Color` identity for every output pixel. The selected colors, diffusion arithmetic, QUALITY precise path and GIF encoder are unchanged. Empty palettes retain the old fallback behavior. This adds a 32 KiB byte table for non-precise nonempty palettes; it is not a memory-saving change.

The parent authored the four-line diff; oh-my-pi performed the bounded measurement/functional action without editing production. Three alternating baseline/candidate pairs used the saved, hash-verified baseline application APK and a fixed common diagnostic test APK. The synthetic input and output hashes match the phase-profile corpus below.

| Pair | Baseline conversion (ms) | Candidate conversion (ms) | Time reduction |
| --- | ---: | ---: | ---: |
| 1 | 2821 | 2196 | 22.16% |
| 2 | 2804 | 2177 | 22.36% |
| 3 | 2794 | 2199 | 21.30% |

Medians: **2804 / 2196 ms**, a **21.68% time reduction** (1.277x baseline/candidate ratio). Within-series max/min drift: 0.97% / 1.01%, below the observed paired improvement. This is not a universal speed guarantee: one Motorola edge 30 pro, three pairs and a native-size 480x270 BALANCED synthetic corpus, without controlled thermal/JIT/CPU state. FAST, QUALITY, downscaled inputs and real artwork have no performance claim.

All six canonical inputs matched `392e4327a7eb65a74c798ae3dee039d36605a7b3a089e9daf53ec625df3c5416`. All six GIFs were byte-identical, SHA-256 `64181c2ab5d299b1bc100f9c5b972c41e58e2f61a4d5a56defe3e0fc16c28475`, 5,654,550 bytes, 480x270, 100 images, 10 seconds, 10cs delays and infinite looping. Progress was exactly `(0,100)` through `(100,100)`. Baseline/candidate full decodes and frame/timestamp digests matched. Sampled PSS ranges overlapped (56,078-56,098 / 56,051-56,190 KiB); no memory improvement is claimed.

The baseline app APK hash was `15e7563663e34f44645a960ae09dab69b35d5f30233e394877bd047c8847300b`; the candidate/final app was `f2abc3bc3784cf54327f9346d64966c2ac87fc79f01a4824963c539f2c1c6871`. Temporary benchmark diagnostics were restored byte-for-byte before final checks. The final full Gradle command below passed in 3s (7 executed / 78 up-to-date), with 125 app JVM tests executed and shared tests cached. The restored device contract command passed 16 tests in 3.3s. The parent separately repeated the eight focused JVM tests successfully in 2s. No deterministic wall-clock RED test is claimed for a performance-only change with unchanged output expectations.

The phone has the diagnostic-free candidate and test APK installed. Nine installs all succeeded with inactive-service guards. Host evidence remains in `/tmp/opencode/gif-index-cache-40c8pm/`, including baseline/candidate APKs, common diagnostics, six ZIP/GIF pairs, command logs, fixture/output validation and exact synthetic device paths in `measurements.json`. Risk: item 3, because the changed helper also serves non-indexed dithering. No additional optimization or tuning loop was performed.

Independent verification compared baseline `c8f64cc` and candidate in fresh isolated scratch copies, with offline dependencies and isolated HOME/cache/temp directories. All eight existing focused JVM tests and two independent probes passed in both copies. The probes cover the three dither entry points, diffuse/precise combinations, palette sizes 1/2/16/256, fractional colors, ties, all 32768 cells, unsigned index 255, two-frame writer integration, input immutability, rejected shapes and interruption. Both implementations produced matrix digest `e0dd60af9fa23e5938d2a04fc37081a3e71662fcf36101b3ecba97ecb77e8565`; the ported regression test asserts this captured digest without reading Git history. Empty-palette rejection already occurred on the baseline and is preserved, not repaired. No change-caused runtime blocker was reproduced. The verifier's concurrent-document attribution note was resolved: the parent added the candidate measurement section during verification; the runtime patch stayed identical. The parent ported the probes and ran all ten focused tests successfully. Verifier scratch: `/tmp/opencode/gif-index-verifier-crSR5Q/`. Native candidate review remains separate.

After porting the probes, the parent full Gradle command passed in 3s (2 executed / 83 up-to-date), with 127 app JVM results and no failures/errors/skips; shared host tests remained cached. The parent repeated the 16 device contract tests successfully in 2.935s. APK hashes still match the measured candidate and diagnostic-free test APK. No production source changed after the benchmark or independent runtime comparison.

The user granted candidate-scoped review. Risk, readability and reliability lenses approved without findings; exact native acknowledgement returned `action: acknowledged`, `authority: burned` for lineage `review-a741781bffb72519` and target `sha256:672aaebf67580ae9740cc91ef04d6517dbad892e80dc0280c5a4d671ec32b6d5`. Commit `60b2ee8` has exactly the reviewed tree `fef757e98ff355d6496c0b5a584899e172907bcd`. No runtime correction was needed. This approval does not establish performance on other devices or artwork.

Evidence paths below and above are historical: after acceptance the parent removed the three task-owned host scratch roots and the 24 exact synthetic device ZIP/GIF paths from both measurement manifests, without clearing app data/cache or touching downloads. Durable evidence is the committed report, captured hashes and regression tests. The installed application hash was independently confirmed to match the local candidate APK `f2abc3bc3784cf54327f9346d64966c2ac87fc79f01a4824963c539f2c1c6871`. All changes remain local, with no push or pull request.

## Current indexed pipeline — phase attribution (2026-11-04)

**Decision:** investigate one exact-output indexed-dither optimization next, not another encoder rewrite. On the corpus below, `ditherIndexed` accounts for 59.48% of measured conversion time. This is phase attribution, **not a delivered speedup** or proof that a particular statement inside the dither loop dominates.

Baseline: `446b652`, branch `feature/gif-indexed-phase-profile`, Motorola edge 30 pro. The earlier Fase 0/1 tables below describe historical Square paths, not the current indexed writer. The accepted writer's earlier 100-frame totals were 2840/2797/2823 ms (`odd/tasks/indexed-gif-writer.md`); this run establishes current phase evidence.

### Comparable inputs and outputs

Only `GifPhase1BenchmarkTest#benchmarkHundredJpegFrames` ran: 100 synthetic JPEGs, 480 × 270, JPEG quality 88, changing gradients, 100 ms per frame, BALANCED. Six separate instrumentation processes ran in order: three with unchanged production source, then three with temporary timers inside the existing `GifConversion.convert`. Both series used identical temporary benchmark diagnostics **outside** the conversion timer for input hashing, output hashing and decoded dimensions/duration. No copied conversion implementation, algorithm, preset, callback or exception changes were used.

Every run produced input identity `392e4327a7eb65a74c798ae3dee039d36605a7b3a089e9daf53ec625df3c5416`, defined as SHA-256 of UTF-8 `frames.toString()` followed by the ordered 32-byte SHA-256 digest of each JPEG. All six ZIPs were retained and independently rehashed on the host with reconstructed `frames.json`; filenames alone were not used as evidence. ZIP container hashes differ, but ordered JPEG content and delays match.

All six output GIFs have SHA-256 `64181c2ab5d299b1bc100f9c5b972c41e58e2f61a4d5a56defe3e0fc16c28475`, 5,654,550 bytes, final progress 100, decoded dimensions 480 × 270 and duration 10,000 ms. Host ffprobe counted 100 decoded frames; full FFmpeg decoding succeeded. A GIF block inspection required 100 images, every delay 10 cs and a NETSCAPE infinite-loop extension. Byte equality extends those checks to all six retained outputs. It does not establish artwork fidelity. Historical Square palette ordering may differ between processes; it did **not** vary in this series.

| Run | Process PID | Benchmark elapsed (ms) | Sampled peak PSS (KiB) | Sampled peak heap capacity (KiB) |
| --- | ---: | ---: | ---: | ---: |
| Unprobed 1 | 19855 | 2802 | 57707 | 27508 |
| Unprobed 2 | 20136 | 2853 | 57102 | 27508 |
| Unprobed 3 | 20278 | 2785 | 56933 | 27508 |
| Probed 1 | 20789 | 2812 | 56735 | 27508 |
| Probed 2 | 20908 | 2785 | 56714 | 27508 |
| Probed 3 | 20973 | 2814 | 56696 | 27508 |

Unprobed/probed medians: 2802/2812 ms, a +0.36% observed difference. Within-series max/min drift: 2.44%/1.04%. The difference is smaller than observed variation; ordered, non-interleaved series and uncontrolled thermal/JIT/GC state do not isolate timer overhead. No performance or memory improvement is claimed from the probes.

### Current phase boundaries

Nanosecond totals were accumulated around existing statements, then converted to milliseconds below. Shares use summed phase time divided by summed inner conversion time across the three probed runs.

| Boundary | Probed 1 (ms) | Probed 2 (ms) | Probed 3 (ms) | Share |
| --- | ---: | ---: | ---: | ---: |
| Sample selection, JPEG decode, pixel sampling and multiset | 67.040 | 63.004 | 63.144 | 2.33% |
| Quantizer selection and palette construction | 367.975 | 371.496 | 372.337 | 13.39% |
| `PaletteLookupDitherer` / LUT construction | 211.250 | 212.067 | 211.560 | 7.65% |
| First + loop decode / resize / `getPixels` | 141.797 | 137.354 | 143.832 | 5.09% |
| `ditherIndexed` | 1653.954 | 1633.297 | 1650.877 | 59.48% |
| Encoder/palette setup + indexed frames + finish/flush | 323.869 | 322.218 | 325.278 | 11.70% |
| Inner residual | 10.309 | 9.621 | 10.561 | 0.37% |
| Inner conversion total | 2776.195 | 2749.057 | 2777.588 | 100% |

The inner residual is total minus the six measured boundaries; it includes ZIP setup/close, buffer allocation, frame bookkeeping, bitmap recycling, progress and timer bookkeeping. Benchmark elapsed additionally includes dispatch/stream handling, the phase log and joining the existing memory sampler; its 35.8–36.4 ms difference from the inner total is not another identified conversion phase. Writer time includes LZW and buffered output, not pure compression CPU. No allocation profiler or nested dither profile was run.

**Single next bounded experiment, requiring parent authorization:** precompute the palette index of each LUT cell and use it instead of the per-pixel `IdentityHashMap<Color, Int>` lookup for non-precise indexed dithering. Keep the selected `Color`, palette order, floating-point arithmetic, diffusion order, QUALITY nearest-color path, timing and cancellation unchanged. Narrow proposed production surface: `PaletteLookupDitherer` in `GifConversion.kt`; use the existing dither/writer contracts plus paired production benchmark and decoded-output comparisons. The map's share of the measured dither phase is **unknown**; this is a testable small candidate, not a promised gain. Reject it if output changes or the win is indistinguishable from run variation. No optimization was implemented in this unit.

Limits: one phone and one native-size BALANCED corpus; no QUALITY/FAST phase attribution, large-source ImageDecoder/downscale path, real Pixiv work, provider/SAF I/O or vendor comparison. Input hashing warms file access before both series. PSS is sampled every 20 ms and may miss peaks; `peakHeapKb` is `Runtime.totalMemory()` heap capacity, not live objects, allocation volume or guaranteed peak memory. Existing JVM/device contracts remain the behavior evidence outside this fixture.

### Commands, artifacts and restoration

Toolchain: `JAVA_HOME=/home/jhonan/.jdks/jbr-21.0.11`, `ANDROID_HOME=/home/jhonan/Android/Sdk`; no local paths were added to Gradle. Commands ran foreground; every APK install was preceded by `dumpsys activity services com.pixload.app` reporting `(nothing)`. All six installs reported `Success`; no clear/uninstall/force-stop was used.

```sh
export JAVA_HOME=/home/jhonan/.jdks/jbr-21.0.11
export ANDROID_HOME=/home/jhonan/Android/Sdk
ADB="$ANDROID_HOME/platform-tools/adb"
DEVICE=adb-ZY22H65ZBW-ja0hNt._adb-tls-connect._tcp
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
# Repeat this guard immediately before EACH install; stop unless it reports (nothing).
"$ADB" -s "$DEVICE" shell dumpsys activity services com.pixload.app
"$ADB" -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$DEVICE" shell dumpsys activity services com.pixload.app
"$ADB" -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# One invocation per process; three unchanged-production and three phase-probed runs.
"$ADB" -s "$DEVICE" shell am instrument -w -r -e class com.pixload.app.GifPhase1BenchmarkTest#benchmarkHundredJpegFrames com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
"$ADB" -s "$DEVICE" logcat -d -v threadtime -t 2000 -s PixloadProfile:I '*:S'
# After exact source restoration:
./gradlew :app:testDebugUnitTest --tests com.pixload.app.PaletteDithererTest --tests com.pixload.app.PaletteLookupDithererTest --tests com.pixload.app.FastGifEncoderTest --console=plain
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
# Reinstall BOTH restored APKs with the guards above, then:
"$ADB" -s "$DEVICE" shell am instrument -w -r -e class com.pixload.app.UgoiraConverterTest,com.pixload.app.IndexedGifWriterTest com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
```

Initial full build: `BUILD SUCCESSFUL in 17s`, 1 executed / 84 up-to-date; shared and app test tasks were cached. Temporary identity-test build: 8s; phase-probed APK build: 9s. Every benchmark reported `OK (1 test)` (runner times 3.504/3.551/3.482 and 3.824/3.480/3.521 s). Restored focused JVM: `BUILD SUCCESSFUL in 4s`, 8 tests executed, zero failures/errors/skips. Final full build: `BUILD SUCCESSFUL in 3s`, 10 executed / 75 up-to-date; app JVM ran 125 tests with zero failures/errors/skips, shared host tests remained up-to-date. Restored device contracts: `OK (16 tests)` in 3.255 s. No command failed and no correction batch was needed. Diagnostic-only work has no behavioral RED/GREEN claim.

Both source files were restored with surgical edits after comparison against saved diagnostic snapshots. `cmp` succeeded, SHA-256 matched their pre-edit snapshots, and `git diff --exit-code --` on both paths was empty **before** the final checks. `GifConversion.kt`: `7a15e4d06e74ac07b30cd5721ca1d92171b28dd1ca09d774e79985a999cc1339`; `GifPhase1BenchmarkTest.java`: `352237a05555be3804d99dea83750a150ac2b064303ad17ee61f1ba4a42d019a`. The final installed app and test APKs were rebuilt from restored source, not the probed source.

Retained host scratch: `/tmp/opencode/gif-indexed-profile-X085t8/`. It contains `diagnostic.patch`, original/probed source snapshots, build/install/service logs, six runner/logcat logs, `baseline-{1,2,3}.{zip,gif}`, `probed-{1,2,3}.{zip,gif}`, reconstructed `frames.json`, `measurements.json`, `artifact-validation.json`, focused test XML, source/APK hashes and ffprobe/full-decode evidence. `measurements.json` maps every run to its exact retained device ZIP/GIF under `/data/user/0/com.pixload.app/cache/pixload-benchmark-*`; these synthetic files and host scratch were not deleted. Probe timing reproduction requires the retained patch; no diagnostic source ships. `mem_save` is unavailable; evidence is recorded here and in `odd/tasks/gif-indexed-phase-profile.md`.

## Fase 0 — Square original, JPEG 960 × 540, un fotograma

| Fase | Tiempo observado |
| --- | ---: |
| Descompresión ZIP y decodificación JPEG | 4–24 ms |
| Escalado (la fuente ya es 960 × 540) | 0 ms |
| `Bitmap.getPixels()` | ~1 ms |
| `int[]` → objetos `Color[][]` de Square | 69–258 ms |
| Histograma de colores | 228–279 ms |
| Cuantización median-cut | 3.40–3.48 s |
| Dithering Floyd–Steinberg | **25.8–29.9 s** |
| Índices de paleta | 113–143 ms |
| LZW | 544–656 ms |
| Escritura en `OutputStream` | ~1 ms |
| Codificación total | **30.4–34.5 s** |

**Dominante: dithering (~85–87 %)**; en Square el dither busca linealmente entre hasta 256 colores por píxel y crea numerosos objetos. Medida de asignaciones del runtime ART (`art.gc.bytes-allocated`): **4 590 854 144 bytes (~4,28 GiB) en un fotograma de 960 × 540**. El contador `Debug.getThreadAllocSize()` es un `int` que se desborda y NO sirve por sí solo para esta cifra. ΔPSS de una ejecución: ~55–89 MiB; **no** es memoria pico. GIF resultante: 175 613 bytes. Medidas de ejecuciones distintas, afectadas por calentamiento y GC.

## Fase 1 — comparación de cambios aislados

Un fotograma JPEG con gradiente; MAE = error absoluto RGB medio de píxeles muestreados, medido contra el JPEG decodificado a la misma resolución. Menor MAE no garantiza mejor calidad perceptual ni menos parpadeo. Tiempos de ejecución única/variación por calentamiento; comparar con prudencia.

| Cambio respecto a referencia | Tiempo de codificación (+ preparación) | GIF | MAE | Resultado |
| --- | ---: | ---: | ---: | --- |
| Square median-cut + Floyd, 960 × 540 | 30.4 s | 175 613 B | 4,78 | Referencia |
| **Solo reducir a 480 × 270** antes del encoder (misma cuantización/dithering) | 8,93 s (otra corrida 9,37 s), **~3,4×** | 54 372 B | 4,72 | Menos píxeles que procesar; ImageDecoder usa tamaño objetivo, sin bitmap a resolución completa |
| **Solo** cambiar a UniformQuantizer a 960, mantener Floyd | 26,49 s (**~13 %** menos) | 207 702 B | 15,97 | Descartado para modo equilibrado/calidad por pérdida cromática |
| **Solo** desactivar difusión usando `NearestColorDitherer` de Square a 960 | 33,73 s (**11 % más**) | 41 530 B | 3,61 | Descartado: sigue buscando linealmente en 256 colores y el MAE no mide banding |
| Igual pero a 480 | 9,93 s vs 8,93 s (**11 % más**) | 19 533 B | 3,66 | Descartado |
| 480 + **paleta global muestreada median-cut + LUT 5 bits/canal**, sin difusión | 0,53 s paleta + 0,69 s encoder (otra corrida ~0,11 + 0,33 s) | 19 840 B | 4,15 | Preset rápido usa Uniform + LUT sin difusión; los resultados indicados son con median-cut |
| 480 + **paleta global median-cut + LUT y Floyd en buffers por filas** | 0,11–0,30 s paleta + 0,33–0,72 s encoder; **~9–20×** frente a 8,93 s | 56–57 KB | ~5,36 | Adoptado en Equilibrado; elimina la búsqueda lineal dominante |

La paleta global se construye de muestras de **3 frames** (primero, central, último; 5 en Calidad), no se cargan todos los frames. Su reutilización evita cuantización por fotograma y puede reducir parpadeo, pero esto último requiere inspección visual. Se reutiliza `int[]` para `Bitmap.getPixels` y se reciclan `Bitmap` tras usarlos; el proyecto anterior ya reutilizaba parte de estos buffers, así que **no se atribuye una ganancia temporal aislada a esa medida**. En el fotograma 480 × 270, ART contabilizó ~1 165 881 344 B asignados con Square/Floyd original y ~29 679 616 B con paleta+LUT/Floyd (~39× menos, efecto combinado, no solo reutilización de `int[]`).

### Antes vs después, mismo corpus de 10 JPEG cambiantes de 480 × 270

| Métrica | Antes (Square median-cut/Floyd por frame) | Después (Equilibrado, paleta+LUT) |
| --- | ---: | ---: |
| Tiempo total (incluye JPEG/ZIP/GIF) | 86,60 s | 3,35 s (**~25,9×**) |
| Tamaño GIF | 531 506 B | 590 306 B (**+11,1 %**) |
| Pico PSS muestreado cada 20 ms | 138 440 KiB | 116 397 KiB (**−15,9 %**) |
| Pico de tamaño de heap Java muestreado | 57 824 KiB | 67 474 KiB (**+16,7 %; no mejora garantizada**) |

Estas dos mediciones comparables se ejecutaron en procesos de instrumentación separados; una corrida anterior en el mismo proceso tras otros benchmarks dio 122,00 s/3,47 s y PSS 140 359/101 730 KiB respectivamente. La memoria varía con el GC, la inicialización de Kotlin/ART y el orden de pruebas: **no** se garantiza menor heap en cada ejecución. Los picos son muestras a intervalos y pueden omitir picos más breves. Se midió el corpus completo de **100 JPEG** solo con la versión nueva: **30,18 s** (otra corrida 22,14 s), GIF **5 706 288 B**, PSS máximo muestreado **162 455–173 762 KiB**, heap máximo **73 250–82 322 KiB**. No se extrapolan tamaño ni memoria de la versión vieja a 100 fotogramas: no se ejecutó ese experimento completo. Criterio acordado: ≤3 min para 100 frames a 480 px; **cumplido**. Por eso no se activa Fase 2 (encoder propio/NDK). No se ha evaluado ni verificado `android-ndk-gif` para AGP/NDK ni páginas de 16 KB porque la condición de entrada a Fase 2 no se cumplió.

## Presets disponibles en la vista de ugoira

| Preset | Ancho máximo | Paleta/quantizer | Dithering | FPS máximo |
| --- | ---: | --- | --- | ---: |
| Rápido | 360 px | Uniform, compartida por todos los frames | Sin difusión; LUT 5 bits/canal | 15 |
| Equilibrado (por defecto) | 480 px | Median-cut de 3 frames muestreados, compartida | Floyd–Steinberg con LUT 5 bits/canal | 24 |
| Calidad | 720 px | Median-cut de 5 frames muestreados, compartida | Floyd–Steinberg con LUT 5 bits/canal | 30 |

El límite de fps descarta imágenes intermedias cuando es necesario, pero el retardo GIF se calcula a partir del **tiempo acumulado original** y se redondea a centésimas en cada imagen escrita (el bucle es infinito). El progreso reporta frames originales procesados / total; cancelar se comprueba entre frames, leyendo JPEG y por fila en la difusión.

## Riesgos y casos límite

- **Many frames**: ZIP reads, indexed dithering and GIF size still scale with kept frames. The current production path reuses ARGB/index buffers and does not build a Square `Color` grid per frame; Square remains in sampled-palette construction.
- **Poca RAM**: JPEG comprimido se lee en un array acotado a 40 MiB para `ImageDecoder` (Android 28+), más bitmap, buffer de píxeles, tabla y estructuras de Square; el PSS del equipo de prueba no garantiza caber en otros teléfonos. En Android 26–27 se usa `BitmapFactory.inSampleSize` (a veces reduce más de lo solicitado para no decodificar primero a tamaño completo).
- **Delays bajos**: GIF ofrece centésimas, no milisegundos. Los frames que exceden el límite FPS se descartan manteniendo duración acumulada; si la duración total es <10 ms, un GIF con al menos una imagen necesita un retardo mínimo y puede existir desfase inevitable. Un único frame con delay >655 350 ms excede el campo GIF de 16 bits y se trunca.
- **Calidad**: RGB con 256 colores por frame, sin transparencia en Square; una paleta global puede perder colores de escenas no presentes en los frames muestreados. LUT de 5 bits/canal aproxima vecino más cercano y puede crear contornos en gradientes. MAE sintético no sustituye la comparación visual de obras Pixiv en el teléfono del usuario.
- **Cancellation**: current indexed dithering checks interruption per row and primitive LZW checks every 4096 symbols; sampling/decoding has its existing checks. Incomplete SAF output is not recorded as a completed download; process interruption can still leave residues.

Reproducción: `./gradlew :app:assembleDebugAndroidTest`, instalar ambas APK solo si `DownloadService` está inactivo y ejecutar `adb shell am instrument -w -r com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner`. Para pruebas aisladas: `-e class com.pixload.app.GifBaselineProfileTest` y `-e class com.pixload.app.GifPhase1BenchmarkTest`.
