# Deferred large-gallery rendering

Gallery rows now defer card construction until they approach the viewport. The existing two-column cards, `galleryQuery`, thumbnail loader, metadata updates, navigation, share and confirmed-delete handlers remain in use. GIF/MP4 conversion, download handling, persisted history and SAF deletion code are unchanged. No dependencies or visual redesign were introduced.

## Decision and retention cost

`MainActivity.galleryEntries` supplies the existing card-building closure to `GalleryRenderWindow`. Lightweight row slots reserve scroll space. The first measured row estimates unknown heights; measured heights survive renders for the same ordered entry snapshot and width, preserving mixed-height scroll geometry. Changing the ordered entries or width invalidates those measurements. Newly realized rows notify the existing `GalleryImages.loadVisible` path.

**Rows are deferred, not recycled or capped.** Realized rows retain their original focus and action bindings. After visiting every row, all 500 cards are realized, just as with the old eager implementation. The measured total view count then reaches **8,003 versus 7,502 originally: +501 slot/container views**. This is an initial-render optimization, not a bounded-memory implementation. Heap size and frame-time percentiles were not profiled.

Accessibility services and physical-keyboard configurations use eager construction to preserve the complete native focus tree. Placeholder heights for never-visited rows remain estimates until those rows are measured; scroll extent can adjust while discovering heterogeneous rows. Previously measured geometry is retained on return within the same activity and ordered snapshot, not persisted across process death or activity recreation.

## Paired phone measurements

Measured on the authorized local phone `adb-ZY22H65ZBW-ja0hNt._adb-tls-connect._tcp`. The fresh baseline preceded production edits. `GalleryRenderBenchmarkTest.measure500Entries` constructs, measures and lays out 500 synthetic PNG entries with empty covers in a 1080×1600-pixel `ScrollView`, six times on the main thread. Timing excludes fixture setup, provider access, image decoding, app startup and display composition. Candidate timing uses the same entry data, dimensions and timed boundary. The harness uses isolated preferences and never adds fixtures to history.

| Sample | Eager baseline, ms | Final candidate, ms |
| --- | ---: | ---: |
| 0, warm-up | 749.577 | 103.825 |
| 1 | 727.285 | 43.147 |
| 2 | 739.530 | 36.363 |
| 3 | 720.926 | 37.829 |
| 4 | 716.679 | 36.967 |
| 5 | 722.870 | 37.056 |
| Median, samples 1–5 | 722.870 | 37.056 |

| Initial hierarchy metric | Eager baseline | Final candidate |
| --- | ---: | ---: |
| Realized cards | 500 | 14 |
| Total instantiated views | 7,502 | 470 |

These are synthetic native-render measurements, not a promise about real gallery startup or scrolling performance. The historical ~317ms result is not used as the baseline.

## Regression and surface evidence

- **Initial-render RED → GREEN:** before production edits, `initialRenderingDefersOffscreenCards` failed with 500 cards against the `<40` bound. The final candidate passes with 14 initial cards.
- **Mixed-height RED → GREEN:** a narrow 500-pixel fixture containing 100 single-file/folder entries initially lost scroll geometry on return: expected 37,358px, observed 35,468px. Retaining measured row heights makes `returningToMixedHeightRowsPreservesTheMeasuredScrollExtent` pass.
- **Full traversal:** all 500 cards become available; returning to the start retains the original first-row view rather than rebinding it. The test logs the 8,003-view retention cost.
- **Native surface:** an isolated in-memory cache of 501 synthetic entries proves decoded PNG thumbnails on both the first card and the initially uncached deferred final card (each entry has a unique thumbnail cache key), deferred initial construction, deep scrolling to the odd final card, navigation to that exact entry, exact pixel-scroll restoration on return, and a delete dialog for that card. The dialog is canceled; no delete operation runs. Activating accessibility through the native dialog harness exposes all cards.
- **Filters/counts:** the native surface renders exactly the 11 `Fixture 49` search matches in oldest-first order while retaining the 501-entry history count/cache; existing `GalleryQueryTest` covers stable dates, ties, type combinations and presentation-only ordering.
- **Share target:** a deferred final row invokes the real asynchronous share handler against an in-process synthetic provider. The intercepted chooser contains the exact `/500` stream/clip URI and read grant. This passes in a fresh instrumentation process, not only after another test. UI widgets keep the real Android Settings resolver; gallery IO uses the synthetic provider.

The PNG thumbnail fixture and screenshot are app-cache files, not user downloads. No real SAF grant, real provider deletion, real share receiver, Pixiv request or account credential was used for proof. The card's metadata-update code remains unchanged; unknown-folder provider updates were not exercised end to end.

## Reproduce

Use JDK 21 and Android SDK 35. Run focused contracts first:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.pixload.app.GalleryQueryTest' \
  --tests 'com.pixload.app.GalleryImagesApiTest' \
  --tests 'com.pixload.app.GalleryStoreEntryTest' \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

Then run the required foreground verification:

```sh
./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Both commands passed. Before every app reinstall, `adb -s "$DEVICE" shell dumpsys activity services com.pixload.app` returned `(nothing)`; do not reinstall if download/conversion work is active. Both APK installations succeeded. With `DEVICE` set to the authorized phone, the final runs were:

```sh
adb -s "$DEVICE" shell am instrument -w -r \
  -e class com.pixload.app.GalleryRenderBenchmarkTest \
  com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
# OK (4 tests)

adb -s "$DEVICE" shell am instrument -w -r \
  -e class 'com.pixload.app.GalleryRenderingTest#deferredRowSharesItsOwnUriThroughTheExistingHandler' \
  com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
# OK (1 test), fresh process

adb -s "$DEVICE" shell am instrument -w -r \
  -e class 'com.pixload.app.GalleryRenderingTest#nativeSurfacePreservesDeepScrollActionsFilteringAndThumbnails' \
  com.pixload.app.test/androidx.test.runner.AndroidJUnitRunner
# OK (1 test)
```

The final tested candidate remains installed. Run timing before the native UI automation test: enabling accessibility intentionally selects eager construction.

## Review boundaries and retained evidence

Review `GalleryRenderWindow` geometry/lifecycle first, then the gallery-only `MainActivity` integration and the instrumentation assertions. Risk: the shared activity now retains presentation-only row measurements; observer attachment/detachment, entry-snapshot invalidation and scroll restoration are the important boundaries. Card handlers and the shared shell were not refactored. Parent independent verification/native review remains pending under T3; worker checks are not delivery approval.

Local evidence is retained at `/tmp/pixload-gallery.H6jui7/`: `baseline.txt`, `candidate-metrics.txt`, `candidate-benchmark.txt`, `candidate-share-standalone.txt`, `candidate-native.txt`, `candidate-native-uncached.txt`, `verification.txt`, and `gallery-render-surface.png`. Device cache files: `/data/user/0/com.pixload.app/cache/gallery-render-test.png` and `gallery-render-surface.png`. The parent owns cleanup.

Skill resolution: `ponytail`, `impeccable`, and `cognitive-doc-design` loaded through installed `skill://` identifiers. Impeccable context ran against the existing activity; native appearance was preserved. CodeGraph is not exposed, the Kotlin LSP reports no server, and `mem_save` is unavailable; reusable findings are recorded here and in the task log instead.
