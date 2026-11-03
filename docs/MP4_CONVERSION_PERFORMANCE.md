# MP4 hardware encoding with a JCodec fallback

`UgoiraConverter.mp4` now prefers Android hardware AVC encoding through `MediaCodec` and `MediaMuxer`, with the existing JCodec encoder retained for clean restarts. The authorized phone shows a material improvement on the synthetic fixture, well beyond observed drift. The original five MP4 contract tests, six hardware/fallback tests, twelve existing converter tests and two incorporated independent probe classes pass. Parent acceptance and native review are complete for implementation commit `3ce81de`, on the tested device only.

## Implementation and review boundary

| Concern | Decision |
|---|---|
| Public API | Keep `UgoiraConverter.mp4(archive, frames, output, progress)` unchanged. Its private decoder is unchanged, including the reflective benchmark entry point. |
| Timing and dimensions | Shared MP4 orchestration keeps cumulative delay rounding, minimum 20 ms, default 100 ms, 18000 samples, 30 fps, even dimensions and the 1280 px cap. No existing error string changed. |
| Hardware input | Query `MediaCodecList.REGULAR_CODECS` for a hardware AVC encoder supporting `COLOR_FormatYUV420Flexible`. Use `getInputImage` and each plane's row/pixel strides, rather than guessing vendor NV12/NV21 layouts. |
| Color | Explicit BT.601 limited-range RGB-to-YUV420, with averaged 2×2 chroma. Reuse one RGB array and one planar YUV array; convert once per source frame and copy the cached YUV for repeated samples. No Surface/EGL lifecycle or dependency. |
| Quality configuration | AVC Baseline, VBR target `max(1000000, width * height * 18)` bits/s: 16,588,800 bits/s at 1280×720, with a one-second keyframe interval. This is a conservative hardware quality setting, not a lower-resolution/frame-rate speed trade. JCodec settings are unchanged. Cross-encoder quantizers are not claimed equivalent; fixture fidelity is measured below. |
| Codec configuration | Supply output-format SPS/PPS through `MediaMuxer.addTrack` before media samples. Ignore standalone codec-config output as a media sample, as required by the [MediaMuxer API](https://developer.android.com/reference/android/media/MediaMuxer#addTrack(android.media.MediaFormat)). |
| Sample timestamps | Queue `sampleIndex * 1000000 / 30`; reject reordered/changed output PTS and missing samples rather than silently changing the contract. |
| Finalization | Queue codec EOS, drain through output EOS, then submit an empty muxer EOS sample with the exact end timestamp. Release codec/muxer in every completed or failed attempt. |
| Fallback | On hardware setup, encoding or finalization failure, release hardware, reopen the ZIP and restart from frame zero through JCodec's truncating channel. Suppress already-delivered callbacks. The software channel is explicitly owned and closed even if `finish` fails. Unfinalized encoder output is removed. |
| Cancellation | Preserve the exact callback throwable and stop callbacks. Finalize the completed prefix; if hardware finalization fails, silently rebuild only that prefix through JCodec before rethrowing the original cancellation. |

### Exact duration needs a header correction

The first real hardware test failed: eight samples produced `MediaExtractor.KEY_DURATION = 266700`, not the required `266666 ± 1` microseconds. An explicit muxer EOS sample alone did not fix it. The generated headers therefore need explicit duration normalization rather than relying on muxer rounding.

After `MediaMuxer.stop`, `Mp4HardwareEncoder.fixDuration` updates only timing fields in the generated single-video-track MP4: movie timescale/duration, track duration, media duration and any zero-offset, unit-rate edit duration. It leaves compressed samples, their offsets and sample tables untouched. It checks box bounds, header versions, a media timescale divisible by 30 and the expected three timing headers; an unsupported layout triggers the ordinary full JCodec restart. Both the eight-sample regression and a one-sample hardware video now pass the microsecond contract. This owned-output metadata correction is a review risk, not a general-purpose MP4 editor.

Changed production files are `UgoiraConverter.kt` (MP4 delegation only), `Mp4Conversion.kt` and `Mp4HardwareEncoder.kt`. Tests add `Mp4HardwareEncoderTest.java`, the paired method in `Mp4BenchmarkTest.java` and the independently supplied `IndependentMp4ProbeTest.java` / `IndependentFallbackProbeTest.java`. `Mp4ConversionTest.java` and every pre-existing benchmark assertion are unchanged. GIF, gallery, downloads, dependencies and build configuration are unchanged. The T6 probe continuation does not modify production or existing tests and performs no Git/staging operations. Rollback removes the MP4 helpers and restores the original MP4 body; no other subsystem needs migration.

## Device and retained fixture

- Authorized serial: `adb-ZY22H65ZBW-ja0hNt._adb-tls-connect._tcp`; Motorola edge 30 pro, Android 14.
- Selected hardware: `c2.qti.avc.encoder`. Advertised color formats: `[2130708361, 2135033992, 19, 21, 20, 39]`; selected flexible YUV420 is `2135033992` (`0x7f420888`). Selection is visible under logcat tag `PixloadMp4`.
- Original host fixture: `/tmp/pixload-mp4-oLNE0b/hundred-jpeg.zip`; device fixture: `/data/user/0/com.pixload.app/cache/mp4-oLNE0b/hundred-jpeg.zip`.
- SHA-256: `284d5d4ecfdc2c28cba42e9e16d84d79ae8a1680bd6568978968081cf9639c5d`, checked before work. The fixture remains 100 JPEGs at 1441×811, JPEG quality 88, 100 ms each, with `frames.json`; expected output is 300 samples at 1280×720, 30 fps, 10 seconds.
- New retained scratch: `/tmp/pixload-mp4-hardware-o9wy9z`. Logs, paired MP4s, `compare_quality.py`, `verify_mp4.py`, `preview.py` and `quality-preview.png` remain there; it was not deleted.
- Every reinstall was preceded by `dumpsys activity services com.pixload.app`, which reported `(nothing)`; both APK installs reported `Success`. Only synthetic app-cache files and the authorized local phone were used. No Pixiv requests, user files, real SAF grants, SSH or remote sessions.

## Paired measurements

Each series runs software/hardware/hardware/software in one instrumentation invocation on the same fixture and phone. Software uses the retained AndroidSequenceEncoder path through a deliberately unavailable hardware factory; hardware calls the public production entry point. Both receive exactly 100 ordered callbacks.

| Series | Execution order | Path | Total ms | MP4 bytes |
|---|---:|---|---:|---:|
| Initial prototype | 0 | JCodec | 99830 | 8751373 |
| Initial prototype | 1 | Hardware | 10498 | 2079687 |
| Initial prototype | 2 | Hardware | 10935 | 2079687 |
| Initial prototype | 3 | JCodec | 102668 | 8751373 |
| Duration correction | 0 | JCodec | 144778 | 8751373 |
| Duration correction | 1 | Hardware | 15706 | 2079687 |
| Duration correction | 2 | Hardware | 15560 | 2079687 |
| Duration correction | 3 | JCodec | 136106 | 8751373 |
| Full benchmark class | 0 | JCodec | 85117 | 8751373 |
| Full benchmark class | 1 | Hardware | 9349 | 2079687 |
| Full benchmark class | 2 | Hardware | 9258 | 2079687 |
| Full benchmark class | 3 | JCodec | 85266 | 8751373 |
| T6 independent repeat | 0 | JCodec | 87990 | 8751373 |
| T6 independent repeat | 1 | Hardware | 9490 | 2079687 |
| T6 independent repeat | 2 | Hardware | 9460 | 2079687 |
| T6 independent repeat | 3 | JCodec | 86345 | 8751373 |

Within-series max/min drift is 2.8%/4.2% for software/hardware initially, 6.4%/0.9% after the duration correction, 0.18%/0.98% in the full benchmark-class series, and 1.91%/0.32% in the T6 independent repeat. Across series max/min variation reaches about 70% for both paths; no causal timing claim is made about the tiny metadata edit. Paired ratios remain roughly 8.7–9.5×. Even comparing the slowest hardware run with the fastest software run still gives about 5.4×. Thermal state and warm-up are uncontrolled; the claim is a large consistent win on this fixture/phone, not a fixed universal multiplier.

The earlier full `Mp4BenchmarkTest` invocation reported **OK (3 tests)** in 589.694 seconds. Both original methods, including their unchanged 300-sample assertions, passed alongside the new paired method. A final failure-only guard also classifies codec/muxer release exceptions as hardware failures; that APK passed all 23 focused device tests again in 9.899 seconds. The first three paired series preceded that cleanup-error guard; the T6 independent repeat exercises the frozen production build including the guard.

### Quality and actual output smoke

`verify_mp4.py` ran `ffprobe -count_frames` against all four retained paired outputs: one H.264 track in MP4, 1280×720, 30 fps, all 300 decoded frames, every sample PTS within 1 µs of `index / 30`, and both track/container duration exactly 10 seconds.

`compare_quality.py` fully decodes each output and compares each of the 100 source-frame samples in RGB against the retained JPEG resized to 1280×720 with Pillow bilinear resampling. It is an independent approximate scaling reference, not pixel equality with Android's scaler.

| Path, both repeated outputs identical in this metric | Aggregate RGB PSNR dB | Lowest source-frame PSNR dB |
|---|---:|---:|
| JCodec | 21.7773 | 20.7004 |
| Hardware | 35.6617 | 34.8267 |

The hardware output does not trade away fixture fidelity for speed by this measurement. A source/software/hardware contact sheet at source frames 0/33/66/99 was rendered and inspected; the changing gradients decode correctly. The device's red/blue tolerance assertions also pass. This synthetic result does not establish quality parity for all Pixiv artwork.

## Contract and fallback verification

The earlier focused device invocation passed **23 tests** in 9.899 seconds: all five unchanged `Mp4ConversionTest` tests, all twelve unchanged `UgoiraConverterTest` tests, and six hardware/fallback tests. The preceding resource-ownership build also passed 23/23 in 14.356 seconds. The T6 continuation below adds two independent probe classes to the same run.

- Existing assertions retain container/H.264/30 fps, every PTS, exact duration, odd/max dimensions, cumulative/default/minimum delays, progress sequence, cancellation identity and partial output, rounded 18000-sample boundary, exact empty/missing-entry errors and color tolerance.
- New real hardware tests exercise supported 320×240 dimensions, cumulative timing/colors, cancellation with a valid three-sample prefix, and exact single-sample duration. A successful hardware finish is asserted so fallback cannot masquerade as hardware proof.
- Fallback tests cover an unavailable encoder, actual platform setup/start failure from an invalid color format, failure after the first hardware source frame without duplicate callbacks, and hardware-finalization failure after callback cancellation with the original exception and valid partial MP4 preserved.
- First hardware contract run: 9/10 passed; duration regression failed. The diagnostic observed `266700` µs. Corrected run: 22/22 passed. Final run adds the single-sample edge and passes 23/23. No assertion was weakened or deleted.
- Focused API JVM tests and the required foreground Gradle command reported `BUILD SUCCESSFUL`. Shared host results were up-to-date; app unit tests executed. No final Kotlin warning remains in the changed files; Gradle reports its existing Gradle-9 deprecation notice.

### T6 independent probe continuation

The parent reported that independent verification found no change-caused blocker, then authorized incorporating only `IndependentMp4ProbeTest.java` and `IndependentFallbackProbeTest.java` from `/tmp/opencode/mp4-verify.xPEDIn/candidate/app/src/androidTest/java/com/pixload/app/`. Both sources were read before adaptation. Neither depends on Git history or a fixed baseline: ZIP integrity uses a digest computed before conversion. Cleanup is limited to formatting/imports, the Android test runner, locale-independent mode matching, fixture bitmap cleanup and compression preconditions; the log-only GIF hash block was omitted. All supplied behavioral assertions remain.

The public probe retains immutable-ZIP checks across its input matrix, exact missing-entry behavior, runtime cancellation identity, completed-prefix progress and 30 fps PTS checks where output exists. The fallback probe asserts replay occurs only after release, completed-prefix sample counts, no duplicate callbacks, ZIP immutability and preservation of callback `IOException` or `AssertionError` identity across setup/encode/finish/close fault modes. Tiny-frame codec errors are observed without pinning a new encoder-specific exception contract.

New probes plus `Mp4HardwareEncoderTest`, `Mp4ConversionTest` and `UgoiraConverterTest`: **OK (25 tests)** in 16.376 seconds. Both service dumps before reinstall reported `(nothing)` and both APK installs succeeded. The retained device fixture SHA-256 remains `284d5d4ecfdc2c28cba42e9e16d84d79ae8a1680bd6568978968081cf9639c5d`.

The independent repeat selected only `Mp4BenchmarkTest#measureHardwareAgainstSoftware`: **OK (1 test)** in 193.306 seconds. The fourth series above gives 9.13–9.27× paired ratios, with software/hardware max/min drift 1.91%/0.32%. The full benchmark class was not rerun in this continuation. The final required Gradle command ran afterward in the foreground: **BUILD SUCCESSFUL in 629 ms**, 1 task executed / 84 up-to-date; shared/app JVM test and APK tasks were up-to-date. This is a successful cached verification, not a claim of fresh JVM test execution.

New scratch `/tmp/opencode/mp4-probes-tzg0Yi` retains `commands.sh`, full probe/benchmark/Gradle output, both service/install logs, fixture digest, four repeated MP4 outputs and `runtime-smoke.txt`. All four copied MP4s were checked with `ffprobe -count_frames`: one AVC track in MP4, 1280×720, 30 fps, 300 decoded samples, every PTS within 1 µs and exact 10 s track/container duration. Verifier scratch `/tmp/opencode/mp4-verify.xPEDIn` remains untouched; the parent owns cleanup.

Synthetic header-layout advisories from the independent investigation are **not reproduced public-entry-point blockers**. No production fix or diagnostic-only header probe is added in this unit. Parent spot-check, normalization if needed, native review and final T6 acceptance remain pending; passing these probes does not claim that review is complete.

### Exact commands

From the repository, with the supplied environment:

```sh
env JAVA_HOME=/home/jhonan/.jdks/jbr-21.0.11 ANDROID_HOME=/home/jhonan/Android/Sdk ./gradlew :app:testDebugUnitTest --tests com.pixload.app.UgoiraConverterApiTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
env JAVA_HOME=/home/jhonan/.jdks/jbr-21.0.11 ANDROID_HOME=/home/jhonan/Android/Sdk ./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

For these commands, `ADB=/home/jhonan/Android/Sdk/platform-tools/adb` and `DEVICE=adb-ZY22H65ZBW-ja0hNt._adb-tls-connect._tcp`:

```sh
$ADB -s "$DEVICE" shell dumpsys activity services com.pixload.app
$ADB -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s "$DEVICE" shell dumpsys activity services com.pixload.app
$ADB -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB -s "$DEVICE" shell am instrument -w -r -e class com.pixload.app.IndependentMp4ProbeTest,com.pixload.app.IndependentFallbackProbeTest,com.pixload.app.Mp4HardwareEncoderTest,com.pixload.app.Mp4ConversionTest,com.pixload.app.UgoiraConverterTest com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s "$DEVICE" shell am instrument -w -r -e class com.pixload.app.Mp4BenchmarkTest#measureHardwareAgainstSoftware -e mp4Fixture /data/user/0/com.pixload.app/cache/mp4-oLNE0b/hundred-jpeg.zip com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
# Historical T4-T5 invocation only; not rerun during the T6 continuation.
$ADB -s "$DEVICE" shell am instrument -w -r -e class com.pixload.app.Mp4BenchmarkTest -e mp4Fixture /data/user/0/com.pixload.app/cache/mp4-oLNE0b/hundred-jpeg.zip com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
$ADB -s "$DEVICE" exec-out run-as com.pixload.app tar -C cache/mp4-oLNE0b -cf - hardware-1.mp4 hardware-2.mp4 software-0.mp4 software-3.mp4 | tar -xf - -C /tmp/pixload-mp4-hardware-o9wy9z
python /tmp/pixload-mp4-hardware-o9wy9z/verify_mp4.py
python /tmp/pixload-mp4-hardware-o9wy9z/compare_quality.py
python /tmp/pixload-mp4-hardware-o9wy9z/preview.py
```

Instrumentation logs are retained as `contracts-first.txt`, `duration-diagnostic.txt`, `contracts-corrected.txt`, `contracts-final.txt`, `contracts-cleanup-final.txt`, `paired-first.txt`, `paired-final.txt` and `all-benchmarks-final.txt`; Gradle output is `gradle-final.txt`, and codec capabilities are in `selected-codec.txt`. Final benchmark MP4s were pulled again and the decode/PTS/duration/fidelity/preview harnesses reran successfully. The first quality harness invocation used removed FFmpeg 9 `-vsync`; it was corrected to documented `-fps_mode passthrough` and then completed successfully.

## Historical JCodec baseline (accepted T1–T3)

The prior measured-stop investigation made no production optimization. Its direct baseline was 93396/86417 ms; profiled totals were 90350/86620 ms, decode/scale 1391/1355 ms, bitmap-to-RGB 2289/2223 ms and native encode 86613/82985 ms. The nested run took 85748 ms with 74304 ms in the existing H.264/mux sink. Parent independent profiling took 126867 ms, including 121478 ms native encode and 109923 ms H.264/mux. That confirmed substantial drift and encode dominance, motivating the later user-authorized S5 hardware work rather than unmeasurable bitmap micro-optimizations. Earlier evidence remains under `/tmp/pixload-mp4-oLNE0b` and in the task log.

## Risk and ownership

- Risk: hardware/color-format compatibility is measured on one Android 14 phone, not an API-26-to-current device matrix. Unsupported dimensions/formats fall back to JCodec; other vendors' plane layouts have not been exercised.
- Risk: exact-duration correction patches generated MP4 timing headers. Supported zero-offset edits and both timing-header versions are handled, but unobserved layouts deliberately fall back. Parent review should prioritize this code and fallback/cancellation state.
- Risk: fallback restarts from frame zero and can therefore incur the full existing JCodec cost after work was already spent on hardware. No duplicate callbacks are emitted.
- Risk: synthetic gradients and solid colors do not establish quality/memory behavior for all real artwork. No phone memory profile or controlled thermal experiment was performed.
- Parent owns Git, independent verification, native review and T6 acceptance. The worker performed no Git operation, started no agents and did not invoke a review lifecycle.

`skill_resolution`: installed `ponytail`, `cognitive-doc-design` and `work-unit-commits` were loaded through native `skill://` reads before task work. `find-docs` and `context7-mcp` were also loaded; Context7 resolved official Android Developers documentation and confirmed EOS/configuration rules. `mem_save` is not mounted, so discoveries are recorded here and in `odd/tasks/mp4-conversion-performance.md`. No Kotlin language server is available; the references request reported that limitation and callers were located with a scoped search.

## Parent acceptance and evidence lifecycle

The parent repeated the full Gradle command successfully (cached JVM/APK tasks) and the five-class device suite: **25 tests passed in 16.608 s**. An independent verifier compared baseline `2ee2b0f` and candidate in isolated scratch trees and reproduced no change-caused functional blocker. The repeated paired benchmark measured JCodec/hardware/hardware/JCodec at **87990/9490/9460/86345 ms**, corresponding to paired ratios **9.27x and 9.13x** on this phone, not a universal speed guarantee.

All four native review lenses approved the frozen implementation; its exact acknowledgement burned authority for target `sha256:925e5548d66c77cf1bb0aad803ec370cd764a29ca6ef211a1759318b701063c5`. Commit `3ce81de` retains those exact reviewed bytes. Non-blocking advisories identify a shared FPS constant, a benchmark label that requests rather than proves hardware completion, incomplete deterministic alternate-header coverage, hardware test prerequisites and observational cases in the independent probe matrix. Existing assertion-based contract tests still pass; these advisories are not claims of compatibility on untested devices and do not reopen the review.

Scratch paths and script commands above are historical evidence locations, not shipped files. After acceptance the parent removes temporary host scratch, synthetic device MP4s and the deployment ZIP. Regenerating the described corpus requires re-uploading a synthetic fixture before the opt-in benchmark; the exact measured ZIP hash identifies the historical run. No runtime source or APK is changed by this documentation/cleanup. Other vendors/API levels, real-artwork quality and memory/controlled thermal profiling remain unverified.
