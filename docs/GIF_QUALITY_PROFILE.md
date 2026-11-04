# QUALITY GIF: large-source measurements

**Result:** the unchanged QUALITY converter completed both synthetic corpora and all controlled cancellations on the test phone. This establishes a measured operating point, not a speedup, artwork-quality verdict, memory requirement or cancellation SLA. No optimization is proposed from these measurements alone.

Baseline: `01e0b25`; Motorola edge 30 pro, Android 14 / API 34. QUALITY remains 720px maximum width, 30fps ceiling, diffusion and precise palette selection. The diagnostic harness called the public `UgoiraConverter.gif` without modifying production or workspace test/build source.

## Conversion time and sampled memory

Each corpus contains 30 synthetic changing-gradient JPEGs, JPEG quality 88, with 100ms delays. Fixtures were generated once in a separate preparation process; conversion processes only read the persisted ZIP/JSON. Three runs per corpus alternated 1080p / 4K, each in a fresh instrumentation process. Input hashing and GC preceded timing; output hashing/decoding and sampler shutdown followed it.

| Source | Conversion times (ms) | Median (ms) | Max/min variation | Sampled peak PSS (KiB) | Sampled peak Java heap capacity (KiB) |
| --- | --- | ---: | ---: | --- | --- |
| 1920x1080 | 3759.498 / 3781.429 / 3768.754 | 3768.754 | 0.58% | 80456 / 73691 / 73061 | 37650.25 / 38162.25 / 37522.25 |
| 3840x2160 | 4482.799 / 4468.148 / 4931.956 | 4482.799 | 10.38% | 73005 / 77116 / 73336 | 66434.25 / 67554.25 / 66530.25 |

The slower third 4K result is retained. Baseline PSS was 43,899-44,308KiB for 1080p and 43,553-43,678KiB for 4K; baseline Java heap capacity was 26,964.56KiB. These are different-resolution inputs, not a paired before/after implementation comparison. They do not establish phase attribution or monotonic memory scaling.

PSS was sampled every 20ms on a best-effort basis and includes the process, runtime, sampler and native allocations; transient peaks may be missed. `Runtime.totalMemory()` is Java heap capacity, not live objects or allocation volume. The instrumented processes reported `Runtime.maxMemory()` of **256MiB**, rather than the 512MiB recorded in older profiling. No live-heap/native allocation profile or minimum-memory-device guarantee was measured.

## Output and input controls

All six complete outputs decoded as **720x405, 30 images, 3 seconds**, with 10cs per image and a NETSCAPE infinite-loop extension. Progress was exactly `(0,30)` through `(30,30)`, 31 callbacks. The converter left its caller's stream open. Input ZIP hashes were unchanged after every conversion and cancellation; final device copies matched host copies.

Input identity is SHA-256 of the serialized frame/delay JSON followed by the ordered 32-byte SHA-256 digest of each JPEG. Repeated outputs were byte-identical **within** each corpus; no pixel equality between source resolutions is asserted.

| Corpus | Canonical input SHA-256 | Complete GIF SHA-256 | Bytes |
| --- | --- | --- | ---: |
| 1080p | `f5dea295d621d143f1f3d6872d32890e9542ed9999a97cef41f1e4fdbefd3e3a` | `0da5d1621b57f7cc696e0b8801450b11f1ddc5091a569e6902bff8e9b8e4e94b` | 4391984 |
| 4K | `88f2eebbff965f43334ffd3cd346ee6b48a8554c0d2ca0d8fc8ecf25071f6eef` | `cf7f692fd14578497c9c1c5fa015a2e267e264c52b6fa63736151ff0aa8fbab9` | 4766440 |

Host ffprobe/full FFmpeg decoding succeeded for a representative of each byte-identical group. Every GIF also passed an independent control-block parse. Decoded frame/timestamp signature hashes were `dc8f1162140d850da9b39a530828304310ca48aef6b0392046dbcc0299a1ad24` and `ff84cc839644591253f356c2b5594b8122d8f686464516747dfa43288d51dcb2`, respectively. These checks are not a visual or palette-fidelity assessment of real artwork.

## Controlled cancellation response

On the 4K corpus, a controller observed progress 0 or 15, recorded a monotonic request timestamp, set a cancellation flag and interrupted the conversion worker. Early cases added a deliberate 50ms delay after observing progress 0. The callback also checked cancellation like the service. No production service, queue, Wi-Fi state, UI or SAF provider was exercised.

| Request schedule | Request-to-converter-exit times (ms) | Last progress | Output bytes |
| --- | --- | ---: | ---: |
| Early, after observed start + 50ms | 0.706563 / 0.790052 / 2.953854 | 0/30 | 0 |
| Midpoint, after observed 15/30 | 0.382917 / 0.510208 / 0.388073 | 15/30 | 2357476 |

The end timestamp was taken on entering the worker's `finally` after the public converter returned/threw. All six requests were real, the worker threw `IOException("Conversión interrumpida")`, its interrupt flag remained set on exit, and it subsequently joined with no live worker left. The converter did not close the stream and the input stayed unchanged. Callback exception identity was not applicable: the converter detected interruption before that callback exception was needed. The active conversion phase at the request was not traced.

These sub-3ms samples are **not end-user/UI/service latency** or an SLA. Partial GIF validity was neither required nor claimed; cancellation does not call GIF finalization. One midpoint runner took 10.162s overall while its request-to-exit interval was 0.388073ms; setup, hashing, waiting and validation lie outside that interval, and the extra wall time was not attributed. Cancellation sampled peak PSS ranged 43,796-44,891KiB early and 78,515-82,813KiB at midpoint.

## Execution, restoration and acceptance

The only diagnostic Java source was in an external `git archive 01e0b25` copy. It was not added to the workspace. Build form, with the existing JDK 21 / SDK 35 environment, from that external copy:

```sh
./gradlew :app:assembleDebugAndroidTest --offline --console=plain
adb -s "$DEVICE" shell am instrument -w -r \
  -e class "com.pixload.app.GifQualityLargeProfileTest#METHOD" \
  -e qualityFixtureDir "$OWN_SYNTHETIC_FIXTURE_ROOT" \
  com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
```

The external build passed in 11s, 59 tasks executed. `prepareFixtures` ran once; `profileFullHd`, `profileUltraHd`, `cancelEarly` and `cancelMidpoint` each ran three times: 13 invocations, each `OK (1 test)`. No command failed or diagnostic correction was needed. These are diagnostic assertions, not behavioral RED/GREEN for a code change.

Every install and measurement was preceded by an inactive `DownloadService` check. Only the test APK was replaced and restored, two successful installs; the application APK was never reinstalled. No clear, uninstall, force-stop, account/Pixiv access, user file, SAF or remote operation was performed.

Installed app SHA-256 remained `f2abc3bc3784cf54327f9346d64966c2ac87fc79f01a4824963c539f2c1c6871`; restored original test APK was `840f549a119e92cb4dc41cdc5dd26acf8ef83e2ffe1b8a282d532d771559ecf1`. All 214 original tracked files matched before/after. The parent independently checked the empty source/test/build diff and inspected the harness's timing/cancellation boundaries. Restored device contracts passed 16 tests in 2.981s; the parent's repeat passed **16 tests in 2.929s**.

Historical external evidence root: `/tmp/opencode/gif-quality-large-PYOQuP/`; owned device root: `cache/gif-quality-large-PYOQuP`. It held the report, JSON manifests/results, command logs, APK/source identities, fixtures, six complete GIFs and six partial artifacts. Diagnostic source SHA-256: `d8b81966de22c2a08c71431d756868140ce250cf0e0b82079c17e77952111da4`; diagnostic test APK: `05687d52996abcdfc761ad806cf27c6cb51168473b4d2e5cb5ecb268b3c803bc`. This is a one-off external measurement, not a new shipped benchmark; recreating it requires an external harness with the documented controls.

After checking restoration and accepting the evidence, the parent removed only that task-owned host root and synthetic device cache subdirectory. No application data/cache clear or download removal occurred. Both installed APK hashes were independently read back and matched the originals. The committed report and task log retain the measurements; the diagnostic harness is not shipped.

## Limits and next decision

One phone, two smooth synthetic-gradient corpora, three full runs per group and six controlled cancellation requests cannot establish artwork quality, other-device behavior or worst-case response/memory. Thermal, CPU, JIT and GC state were uncontrolled. Source conditions imply the API 34 downscale path uses ImageDecoder, but no decoder trace was collected. No result justifies silently changing quality, resolution, FPS, cancellation behavior or dependencies.

Keep QUALITY unchanged. A future optimization needs a named bottleneck and separate authorization; a real-artwork comparison needs suitable user-supplied material and an explicit quality criterion. Risk: none for this documentation-only delivery.
