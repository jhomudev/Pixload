# Concurrent QUALITY GIF profile — closed as partial

**Outcome: PARTIAL, not approved for exact decoded parity.** Three converters overlapped and selected cancellation left the other two converters running to completion. However, all 15 non-solo complete GIFs differed from the solo controls in decoded pixels. Their dimensions, timestamps, durations, progress and stream ownership passed the recorded controls. No production regression, cross-converter write or visual severity was established.

A separately authorized sequential quantizer experiment then produced eight different RGB palette sets from identical unchanged samples, without concurrent quantizer calls. This demonstrates variability in that desktop JVM/dataset, **not the cause of the retained Android GIF differences**. The user chose to close the report as partial, preserve both evidence roots and leave the app unchanged. No implementation, dependency, preset or job-limit change was made.

## Android component workload

Baseline `d92b9d4`, Motorola edge 30 pro, unchanged QUALITY preset. One persisted corpus contains 30 JPEGs at 3840x2160, quality 88, with 100ms delays. Preparation ran in a separate process. Three fresh solo controls, three groups of three complete converters and three groups with one selected cancellation each ran in a distinct instrumentation process.

Canonical input identity was `88f2eebbff965f43334ffd3cd346ee6b48a8554c0d2ca0d8fc8ecf25071f6eef`, defined over frame/delay JSON and ordered JPEG digests. All 21 calls preserved the ZIP before/after. Each worker had its own frame JSON, cancellation flag and stream; only the immutable ZIP was shared. Start/end intervals prove overlap rather than inferring it from thread launches.

| Run | Worker call times, indices 0/1/2 (s) | Batch span (s) | Three-worker overlap (s) | Sampled process PSS max (KiB) | Sampled heap capacity max (KiB) |
| --- | --- | ---: | ---: | ---: | ---: |
| Solo 1 | 4.409852 | 4.409852 | N/A | 83262 | 67503 |
| Solo 2 | 4.398257 | 4.398257 | N/A | 93556 | 52991 |
| Solo 3 | 4.405402 | 4.405402 | N/A | 91506 | 67631 |
| Full group 1 | 4.422174 / 4.419455 / 4.419222 | 4.422321 | 4.419130 | 225752 | 135115 |
| Full group 2 | 4.484071 / 4.483858 / 4.484657 | 4.484720 | 4.483750 | 126553 | 137751 |
| Full group 3 | 4.401170 / 4.402686 / 4.401485 | 4.402958 | 4.400898 | 154197 | 127079 |
| Cancel worker 0 | 3.191003 / 4.404373 / 4.404647 | 4.405189 | 3.190391 | 161232 | 135291 |
| Cancel worker 1 | 4.469575 / 3.252360 / 4.472217 | 4.472446 | 3.252131 | 150142 | 135679 |
| Cancel worker 2 | 4.359810 / 4.288002 / 3.148167 | 4.359810 | 3.148109 | 122309 | 107647 |

These are component-load observations, not a throughput/speedup guarantee or a recommendation to change the three-job limit. Process PSS includes runtime/instrumentation/native allocations and is not divided by worker count. Requested sampling sleep was 20ms; observed median gaps were 36-77ms, with a maximum of 98ms. Peaks may be missed. Heap capacity is not live allocation or comparable directly with PSS; the reported Java maximum heap was 262144KiB.

## Controls that passed and the control that failed

All 18 complete GIFs passed 720x405, 30 images, 3 seconds, 10cs delays, infinite looping, exact progress `(0,30)` through `(30,30)` and caller-owned stream checks. The three solo GIFs were mutually byte/pixel-identical, SHA-256 `cf7f692fd14578497c9c1c5fa015a2e267e264c52b6fa63736151ff0aa8fbab9`.

**The separate host decoded comparison failed, exit 1.** FFmpeg RGBA frame hashes differed from every solo in all 30 frames for all 15 non-solo complete outputs, while timestamps and frame sizes matched. This is not merely different GIF headers, palette indices or compressed bytes. Android instrumentation's `OK` covered its own metadata/progress/completion assertions, not this later host check. The failed assertion and all output/framehash artifacts remain unchanged.

Both peers were active when each selected worker reached 15/30 and received its own flag-plus-interrupt request. Only the selected worker stopped; both peers completed with exact progress, but their required decoded equivalence still failed.

| Target | Progress snapshot, workers 0/1/2 | Request-to-public-call-exit (ms) | Partial bytes |
| --- | --- | ---: | ---: |
| 0 | 15 / 14 / 14 | 0.218021 | 2357211 |
| 1 | 15 / 15 / 15 | 0.220052 | 2357460 |
| 2 | 14 / 15 / 15 | 0.248594 | 2357424 |

Targets threw `IOException("Conversión interrumpida")` through an internal interruption check. Callback exception identity was not exercised. Partial GIF validity was not required. These intervals do not measure full UI, service, network, queue or SAF cancellation; the exact active conversion phase was not traced.

## Static attribution review

A bounded read-only explorer found no concrete harness/verifier flaw invalidating the retained comparison and no demonstrated cross-converter write. Converter buffers/encoders are local to calls or instances. Exact cross-call determinism was not established as a library guarantee, but that does not waive the explicit failed measurement control.

The parent inspected the actual cached `com.squareup:gifencoder:0.10.1` JAR using `javap -p -c`. Both median-cut tie comparators delegate to `ArbitraryComparator`, which uses `System.identityHashCode`. Its fallback shared `WeakHashMap` is synchronized. This identifies a plausible source of variation, not a proof of an unsynchronized race or current artifact causality.

## Authorized sequential quantizer experiment

One desktop JBR 21.0.11 JVM (PID 23311), one calling thread (`main`, ID 1), one input constructed once: all 32768 RGB cell centers `(8*r+4, 8*g+4, 8*b+4)` for indices 0..31, each with weight 1. The same production `HashMultiset<Color>` and original Color references were reused for eight sequential calls to `MedianCutQuantizer.INSTANCE.quantize(samples, 256)`.

All 17 input snapshots preserved total/distinct counts, unit weights, references and component/count/iteration signatures. No sample canonicalization before the library, forced GC, identity seeding, state reset, executor or concurrent quantizer call occurred. A negative variation result would have been accepted; no input tuning or rerun was used.

| Observation | Result |
| --- | --- |
| Calls / palette size | 8 / 256 distinct RGB colors each |
| Unique exact component-bit sets / orders | 8 / 8 |
| Unique RGB sets / orders | 8 / 8 |
| All 28 pair comparisons | Different RGB sets; intersections 58-106 of 256 colors |
| Shared RGB entries | Identical component bits in every pair |
| Quantizer call times (ms) | 245.584402 / 162.809905 / 165.600796 / 160.613250 / 159.611890 / 158.960862 / 160.435283 / 161.090706 |

This is actual RGB-set variation, not only a permutation or floating-bit drift. Full input/palette rows permit independent recomputation. Input component/count SHA-256 was `8d00cc3fc18cce695175975c35bddfa8abb2b905c6e59c39ea64e93146be2b47`; iteration signature was `9db0d2ed725140102bce196e102fdfb33a9b15b9c47bfba9f3a084860a4e6b8f`.

The exact read-only JAR SHA-256 remained `ee1a938b897ea68b9cfa6813edf361f0aa69ed363eed96f91bf3811eacd2053e`. One compile and one experiment process completed without quantizer exceptions or probe corrections. This synthetic grid does not reconstruct JPEG decoding/sampling, and desktop JBR is not Android ART. Sequential variability does not retrospectively establish the cause or visual impact of the Android results.

## Failed attempts, restoration and verification boundaries

- Initial device access failed with a trailing-period transport argument and empty inventories. A later parent check of the exact authorized transport succeeded; availability may also have changed. No pairing, reconnect loop or alternate device was used.
- Initial fixture preparation rejected the shell-alias cache path before creating fixtures. One argument-only correction to Java's canonical `/data/data` path passed preparation; no Java rebuild or parity-failure rerun occurred. Eleven diagnostic invocations include that rejection, successful preparation and nine measurement groups; ten reported `OK (1 test)`.
- The external Android diagnostic build passed in 40s, 59 tasks. Only the test APK was installed and restored, with inactive-service guards. The app APK was never reinstalled. Restored original contracts passed 16 tests; no further device tests were run for the sequential scope or closure.
- One auxiliary sequential host assertion compared full-width SHA-1 with a cache-directory name missing its leading zero and failed. Parent recomputation confirmed zero-padded equality and unchanged SHA-256; this was not a library-byte mismatch or probe rerun.
- Worker manifests reported 216 tracked live/archive files unchanged after Android execution; sequential manifests reported 216 tracked workspace files and 437 protected original files unchanged. The parent confirmed the empty application/test/build diff against `d92b9d4`, inspected the comparison/probe evidence and independently checked the exact JAR hashes. No application-source change or behavioral RED/GREEN is claimed.

Recorded installed app/test hashes after restoration were `f2abc3bc3784cf54327f9346d64966c2ac87fc79f01a4824963c539f2c1c6871` / `840f549a119e92cb4dc41cdc5dd26acf8ef83e2ffe1b8a282d532d771559ecf1`. The sequential experiment intentionally did not access the device again.

## Preserved evidence and closure

Both external evidence roots are retained by explicit user choice, not deleted at closure:

- `/tmp/opencode/gif-concurrent-yezviy8M/`: original blocked attempt, `resumed/REPORT.md`, `commands.json`, `output-verification.json`, timing/memory/cancellation summaries, harness/patch, all original ZIP/GIF/framehash artifacts and APK backups. Its owned device cache root is `/data/data/com.pixload.app/cache/gif-concurrent-yezviy8M`; no user files or SAF paths were used.
- `/tmp/opencode/gif-quantizer-sequential-6ATDgeJj/`: `REPORT.md`, `evidence.json`, `commands.json`, `host-verification.json`, `input.tsv`, `inputs.tsv`, `calls.tsv`, eight full palette TSVs, probe source/classes, runtime/JAR/source identities and logs.

These are external temporary locations, not files included in the repository; system/manual temporary-file cleanup may remove them. The committed report and task log retain the outcome and identities. No further experiment, repair or cleanup is authorized by this closure. No push or pull request was requested.

The user selected **"Cerrar informe parcial (Recomendado)"**. T1/T2 are closed as partial, not converted to parity passes. S3/T4 completed its bounded experiment. Current Android causality, visual severity, deterministic-rendering guarantees, broader-device behavior and full service/UI/SAF isolation remain unproven. Any proposal or implementation for deterministic palettes requires separate authorization and explicit pixel/performance tradeoffs. Risk: none for this documentation-only delivery.
