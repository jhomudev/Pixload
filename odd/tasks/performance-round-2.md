# Performance round 2
Branch: feature/performance-round-2 (from develop; main reserved for production). Delivery: ask-on-risk; no push/PR authorized. Runner: JDK 21, SDK 35; `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :shared:testAndroidHostTest` plus isolated AndroidJUnitRunner tests on the connected Android 14 device.

## Specs
S1. User: "no hay otras mejoras posibles mas respecto al rendimiento?"; survey of remaining performance work, grounded in code and measurements, not generic advice.
S2. User: "inicia tal como tu recomendacion."; implement in the recommended order: (1) batched gallery validation, (2) cover thumbnail variant, (3) overlapped GIF pipeline. MediaCodec MP4 remains a separate project and is not implemented here.
S3. Batched gallery validation keeps identical availability semantics: a deleted entry is hidden, a permission or provider failure never hides an entry, and an entry without a stored tree URI keeps the per-entry query. One child listing per tree replaces one SAF query per entry, with a per-entry fallback only for entries missing from the listing.
S4. Cover thumbnail prefers Pixiv's `small` URL for the work cover (the card is 190 dp and page thumbnails already use `small`), falling back to `regular`, then to the novel `coverUrl`. No visual quality claim without a device check.
S5. GIF pipeline overlaps decoding and encoding without changing output bytes, delays, frame selection or cancellation. Measured against the recorded baseline on the same phone: 10 frames 2755 ms, 100 frames 21164 ms, PSS peak 170 MB (`docs/PERFORMANCE_MEASUREMENT.md`).

## Tasks
T1 [S2-S3] Batched gallery availability with per-tree listing and exact fallback semantics. Route: parent inline. Status: done. Risk: none.
T2 [S2,S4] Cover thumbnail from the `small` variant with fallbacks. Route: parent inline. Status: done. Risk: none.
T3 [S2,S5] Overlapped GIF decode/encode pipeline with device benchmark and independent verifier. Route: parent inline + verifier. Status: pending.

## Log
L1. "no hay otras mejoras posibles mas respecto al rendimiento?"
L2. "inicia tal como tu recomendacion."
L3. Exploration: `GalleryStore.Entry` already stores `treeUri` (added for duplicate detection), so batching needs no persisted-format change; `GalleryDocuments.isAvailable` queries one document per entry; `MainActivity.validateGallery` calls it per stored entry off the main thread; `PixivClient` builds the work cover from `urls.regular` while page thumbnails already prefer `urls.small`; `GifConversion.convert` decodes, converts to pixels and encodes strictly serially.
L4. T1 implemented: `GalleryDocuments.available` groups entries by `treeUri`, runs one child listing per tree, keeps every entry missing from that listing on the per-entry `isAvailable` path (deleted → hidden, provider or permission failure → kept), and queries entries without a stored tree URI or with a malformed URI individually. `MainActivity.validateGallery` now filters with `available[it.uri] != false`. Tests: 5 new instrumented cases in `GalleryDocumentsTest` (one listing per tree + fallback only for missing entries, listing miss that still resolves, listing failure falling back per entry, entries without treeUri, malformed URI not breaking the batch). RED observed (unresolved symbol), GREEN `OK (12 tests)` on device, JVM 119 + shared 24, 0 failures.
L5. T2 implemented: `coverThumbnail(small, regular)` prefers Pixiv's `small` variant for `work.thumbnail` and falls back to `regular`; the R-18 guard still reads `regular`, so its trigger is unchanged, and the novel `coverUrl` branch is untouched. Consumers of `work.thumbnail` are the 190 dp download card and the 56 dp queue row, so `small` (540 px) is decoded near native size instead of downsampling a 1200 px regular. Tests: `PixivCoverTest` (small preferred, regular fallback, both missing). RED observed (unresolved `coverThumbnail`), GREEN 121 app tests, 0 failures. Visual quality of a live cover on the phone was not checked: it needs a Pixiv work link, so no quality claim is made.
