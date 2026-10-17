# Image pipeline changes and validation

Base: `integration/avix` at `f1ef8013a0f7e50c3cf94440669e03fc65a4af7a`.
The reference branch HEADs were checked: yesnt10 `enhanced` at
`894499115fb1a69cd4bb34d6969a74832f0e5636`, upstream `cmp-rewrite` at
`229c083cf3db7bab6fda284484f87439eb4e0eb0`.

## Ownership and scheduling

* Home composes one explicitly sized Coil painter per distinct visible/adjacent artwork URL.
  Visual layers retain independent page keys (including the two-item case). A shared resource
  can be drawn by more than one layer without starting another decode. The current artwork
  starts first; adjacent resources and logos wait for its success/error. There is no separate
  ORIGINAL-size enqueue or all-Hero palette prefetch. Existing pager, styles, gradients,
  parallax, indicators and trailer controls remain.
* A bounded last-good image underlays a pending transition, including a pending neighbour
  before `currentPage` changes. Success replaces it, failure releases it, and the 15-second
  stale-retention bound prevents an unresponsive server leaving another title's art forever.
  This is a retention upper bound, not a delay before displaying a completed request. A loaded
  current image remains eligible to underlay later transitions even after a long idle period.
* Details owns its sized painter above the LazyColumn. Mobile and tablet/cinematic layers
  draw it; disposal of the mobile LazyColumn item does not dispose its image request.
  One last-good result is retained during an artwork URL upgrade within the same content.
  Ownership ends with the details screen and is keyed by content identity.
* Palettes use the visible bitmap/painter. Only colors are kept globally (256 entries),
  never bitmaps. Optional standalone 96px samples have a separate memory key. Light/dark
  blending and existing swatch selection remain unchanged.
* Selecting a catalog preview seeds display-only metadata before navigation. A preview never
  satisfies a completed metadata fetch. Full provider data and configured customization
  still replace it. Invalid primary artwork can retry one alternate through the existing
  interceptor; retry routing is kept out of the new requests' memory-cache identity.

## Metadata and persistence

* The repository shares core metadata work by generation/content/cache-read policy. Consumers
  cancel independently; the producer is cancelled when the last consumer leaves. Errors are
  delivered to consumers without cancelling other keys. Profile/TMDB-setting changes invalidate
  generations. UI publications require the active identity and generation.
* Addon basic metadata is published immediately. TMDB artwork is published as soon as details
  arrive, before logos, credits, English fallback names, recommendations and collections finish.
  Existing cancellable transport timeouts remain; an outer enrichment timeout no longer
  discards every secondary result together. Existing MDBList/OMDb and provider settings remain.
* The visible details path enriches episodes progressively, with a selected initial season
  first, then regular seasons, then specials. At most two season fetches run concurrently.
  Every provider episode remains present, with its original identity, progress and fields;
  successful batches merge rather than replace the list. Other metadata consumers retain
  the API's fully enriched episode behavior; Hero consumers request core data only.
* Public TMDB season responses and resolved artwork reuse `AppFileCache`, including its atomic
  writes. One bounded JSON document covers all profiles: 96 responses, maximum 4 MiB, maximum
  1 MiB per response. Keys include profile, language, content/type and season. Artwork TTL is
  six hours, seasons 24 hours. Failures are not cached; corrupt/expired data is a cache miss.
  No API keys, signed provider URLs, account credentials or user viewing data are persisted
  by this TMDB response cache.
* Home's existing profile snapshots remain. Hero-only collection snapshots now save/restore;
  collection completion schedules a save. Initial publication retains the restored collection
  Hero until its refresh has started, rather than clearing it in the bootstrap gap. Snapshots expire after seven days, retain at most
  40 sections × 100 items and eight Hero items, and writes larger than 8 MiB preserve the last
  valid snapshot. Obsolete profile restoration is ignored, and async file I/O runs off Main.
* Critical startup still waits for selected profile and profile-dependent Home/playback state.
  Live TV navigation preferences remain critical. Notification initialization runs after
  content setup on Main, serializing publication with profile changes. The preloader is preserved.

## Coil and trailers

Coil 3.6.3, cache capacities and HTTP policies are unchanged. The singleton factories use
`DeDupeConcurrentRequestStrategy` to coordinate overlapping network fetches by disk key.
Waiters still validate/read through `CacheControlCacheStrategy`; this does not promise one
transfer for `no-store`, uncacheable responses or revalidation. Different required dimensions
may still need different decodes. Oversized ORIGINAL speculative requests are removed.

The Android trailer preview reveals its surface and reports readiness on `onRenderedFirstFrame`,
not `STATE_READY`. It gates alpha internally by its own player instance, invalidates readiness
on detach/disposal, installs the listener before prepare and ignores obsolete callbacks.
The static artwork remains behind the video. No main playback engines, mpvRx or subtitles change.
The iOS preview resets ownership/readiness per source; its existing bridge exposes no actual
first-frame callback, so dimensions plus playback remain a readiness approximation requiring
validation on iOS. No claim of exact first-frame detection is made for that bridge.

## Developer diagnostics

`ImagePerformance.enabled` is `false` by default in every build. Enable it explicitly from a
local debugger when investigating, then disable it again. No user-facing settings or credentials
are required. `Images/Performance` logs only opaque hashes for URLs, identities and cache keys.

Events include `app.start`, `app.content`, `home.compose`, `home.snapshot`, `home.artworkUrl`,
`details.click`, `details.compose`, `details.basicArtwork`, `coil.start`, `coil.size`, `coil.key`,
`coil.fetch`, `coil.decode`, `coil.success`, `coil.error`, `coil.cancel`, `http.response` and
`artwork.drawSubmitted`. Coil success records actual `MEMORY_CACHE`, `MEMORY`, `DISK` or `NETWORK`,
decoded dimensions and hashed keys. HTTP records status (including 200/304) and declared
Content-Length, not a measured wire byte count. Starts record concurrent requests. Fetch/decode
record separate durations. Draw submission is a CPU drawing marker, **not proof of scanout or
of the first hardware-presented frame**; correlate it with a screen recording/FrameTimeline.
A NETWORK result may be a 304 with a reused body. A new request is not proof of a new download.

## Reproduction and acceptance

Use the same device, providers, profile/language, visual style, artwork URLs and connectivity for
both builds. Keep cold-install, warm-process and process-restart runs separate. Repeat with
trailers off/on, slow images, errors, offline mode and memory eviction. Run movie, multi-season
series and anime (including Sword Art Online), one/two/three/eight Hero items, quick swipes,
autorotation, style/theme/profile changes and deep scroll returning to the top.

Record click/app start → composition → artwork URL → Coil start → fetch → decode → draw submission,
and independently the first visible frame. Warm same-size requests should use memory when
eligible; after process restart, fresh cacheable files should use disk. Conditional 304 is valid
reuse. Offline availability depends on HTTP freshness/validation requirements. Expired no-store
images are not expected to be available offline. After LazyColumn recycling, the same details
screen must draw its already loaded background without another request/decode. Neither an
outgoing title's artwork nor a previous player's black surface may persist after completion/error.

Automated coverage includes shared-request errors/cancellation/retry, cache persistence/expiry/
capacity/scope isolation, season ordering and existing episode merge tests, Hero page identities and long-idle/pending/error
continuity timing,
profile snapshots including collection-only Hero bootstrap publication, actual Coil memory/disk/restart/304/no-store/
concurrent-size requests and Compose scroll ownership. The restart unit tests rebuild the ImageLoader/cache object with the same files; they do not
launch and kill an Android application process. Full pixel continuity, trailer first frames,
device memory pressure and a performance comparison remain device acceptance tests.

## Changed entry points

| Area | Functions / ownership |
| --- | --- |
| Hero | `HomeHeroSection`, `HeroContentBlock`, `heroVisiblePages`, `heroArtworkUrl`, `rememberHeroArtworkContinuityAllowed`; `HomeScreen` profile ownership |
| Details | `MetaDetailsScreen`, `DetailHero`, `TabletDetailHero`, `BackdropImage`; catalog preview seeding in `CatalogDestination` |
| Metadata | `MetaDetailsRepository.load/fetch/peek/seedPreview/publishLoadedMeta`, `SharedRequests.await` |
| TMDB | `enrichMeta`, `enrichEpisodes`, `fetchEnrichment`, `fetchEpisodeEnrichment`, `visitArtworkSeasonBatches` |
| Persistence | `MetadataResponseCache.read/write`, `HomeSnapshotStore.load/save`, `HomeRepository` restoration/save scheduling, Android `AppFileCache.read/write` |
| Startup | `StartupPreloader.awaitReady/start/startSecondary`, `MainActivity` content setup |
| Image pipeline | singleton factories in `NuvioApplication` and `AppEnvironment`, `CustomPosterFallbackInterceptor.intercept`, `rememberArtworkDominantColor` |
| Trailer previews | `HeroTrailerPlayerSurface` common declaration and Android/iOS distribution actuals |
| Diagnostics | `ImagePerformance.Trace`, `ObservedImageCacheStrategy`, `artworkDrawMarker`, platform event-listener actuals |

Complete local file inventory (including regression tests):

* [composeApp/src/androidFull/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.android.kt](../composeApp/src/androidFull/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.android.kt)
* [composeApp/src/androidHostTest/kotlin/com/nuvio/app/core/ui/BackdropLifetimeTest.kt](../composeApp/src/androidHostTest/kotlin/com/nuvio/app/core/ui/BackdropLifetimeTest.kt)
* [composeApp/src/androidHostTest/kotlin/com/nuvio/app/core/ui/ImageCacheIntegrationTest.kt](../composeApp/src/androidHostTest/kotlin/com/nuvio/app/core/ui/ImageCacheIntegrationTest.kt)
* [composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/details/MetaDetailsPosterNavigationTest.kt](../composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/details/MetaDetailsPosterNavigationTest.kt)
* [composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeCatalogSettingsRepositoryTest.kt](../composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeCatalogSettingsRepositoryTest.kt)
* [composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeHeroPagerTest.kt](../composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeHeroPagerTest.kt)
* [composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeSnapshotTest.kt](../composeApp/src/androidHostTest/kotlin/com/nuvio/app/features/home/HomeSnapshotTest.kt)
* [composeApp/src/androidMain/kotlin/com/nuvio/app/MainActivity.kt](../composeApp/src/androidMain/kotlin/com/nuvio/app/MainActivity.kt)
* [composeApp/src/androidMain/kotlin/com/nuvio/app/NuvioApplication.kt](../composeApp/src/androidMain/kotlin/com/nuvio/app/NuvioApplication.kt)
* [composeApp/src/androidMain/kotlin/com/nuvio/app/core/storage/AppFileCache.android.kt](../composeApp/src/androidMain/kotlin/com/nuvio/app/core/storage/AppFileCache.android.kt)
* [composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/ImagePerformanceListener.kt](../composeApp/src/androidMain/kotlin/com/nuvio/app/core/ui/ImagePerformanceListener.kt)
* [composeApp/src/androidPlaystore/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.android.kt](../composeApp/src/androidPlaystore/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.android.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/CatalogDestination.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/CatalogDestination.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/concurrency/SharedRequests.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/concurrency/SharedRequests.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/poster/CustomPosterFallbackInterceptor.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/poster/CustomPosterFallbackInterceptor.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/startup/StartupPreloader.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/startup/StartupPreloader.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/storage/MetadataDiskCache.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/storage/MetadataDiskCache.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ArtworkDominantColor.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ArtworkDominantColor.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/BackdropImage.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/BackdropImage.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ImagePerformance.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ImagePerformance.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsRepository.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsRepository.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsScreen.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsScreen.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailHero.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailHero.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/TabletDetailHero.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/TabletDetailHero.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeRepository.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeRepository.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeScreen.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeScreen.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeSnapshot.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeSnapshot.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeHeroSection.kt)
* [composeApp/src/commonMain/kotlin/com/nuvio/app/features/tmdb/TmdbMetadataService.kt](../composeApp/src/commonMain/kotlin/com/nuvio/app/features/tmdb/TmdbMetadataService.kt)
* [composeApp/src/commonTest/kotlin/com/nuvio/app/core/concurrency/SharedRequestsTest.kt](../composeApp/src/commonTest/kotlin/com/nuvio/app/core/concurrency/SharedRequestsTest.kt)
* [composeApp/src/commonTest/kotlin/com/nuvio/app/core/storage/MetadataCachePolicyTest.kt](../composeApp/src/commonTest/kotlin/com/nuvio/app/core/storage/MetadataCachePolicyTest.kt)
* [composeApp/src/commonTest/kotlin/com/nuvio/app/core/storage/MetadataResponseCacheTest.kt](../composeApp/src/commonTest/kotlin/com/nuvio/app/core/storage/MetadataResponseCacheTest.kt)
* [composeApp/src/commonTest/kotlin/com/nuvio/app/features/home/components/HomeHeroSectionTest.kt](../composeApp/src/commonTest/kotlin/com/nuvio/app/features/home/components/HomeHeroSectionTest.kt)
* [composeApp/src/commonTest/kotlin/com/nuvio/app/features/tmdb/ProgressiveArtworkSeasonsTest.kt](../composeApp/src/commonTest/kotlin/com/nuvio/app/features/tmdb/ProgressiveArtworkSeasonsTest.kt)
* [composeApp/src/commonTest/kotlin/com/nuvio/app/features/tmdb/TmdbMetadataServiceTest.kt](../composeApp/src/commonTest/kotlin/com/nuvio/app/features/tmdb/TmdbMetadataServiceTest.kt)
* [composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/ImagePerformanceListener.kt](../composeApp/src/desktopMain/kotlin/com/nuvio/app/core/ui/ImagePerformanceListener.kt)
* [composeApp/src/iosAppStore/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.ios.kt](../composeApp/src/iosAppStore/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.ios.kt)
* [composeApp/src/iosFull/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.ios.kt](../composeApp/src/iosFull/kotlin/com/nuvio/app/features/details/components/HeroTrailerPlayerSurface.ios.kt)
* [composeApp/src/iosMain/kotlin/com/nuvio/app/core/ui/ImagePerformanceListener.kt](../composeApp/src/iosMain/kotlin/com/nuvio/app/core/ui/ImagePerformanceListener.kt)
* [docs/IMAGE_PIPELINE.md](../docs/IMAGE_PIPELINE.md)

## Validation commands and limits

Environment activation: `. /workspace/.setup/activate.sh` (JDK 21, Android SDK 37).
Robolectric requires a writable Java home/cache location in this cloud workspace:

```sh
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Duser.home=/workspace -Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2"
bash ./gradlew :composeApp:testAndroidHostTest \
  :androidApp:compileFullReleaseKotlin :androidApp:mergeFullReleaseNativeLibs \
  :androidApp:minifyFullReleaseWithR8 \
  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore \
  -Pavix.abis=arm64-v8a --max-workers=3 --console=plain
```

The independent `:composeApp:compileCommonMainKotlinMetadata` check remains blocked by two
unchanged errors already present at the base commit: JVM-only `toSortedMap` at
`core/avix/AvixSettingsBackup.kt:49` and `putIfAbsent` at
`features/player/SubtitleFontFile.kt:101`. These unrelated files were not modified.
Android compilation accepts these existing JVM calls. Native iOS compilation requires macOS
and its Apple toolchain; it was not validated in this Linux environment.

No device/emulator was available. There are no controlled before/after startup, Sword Art Online,
frame-time, memory-pressure or playback measurements. The automated checks are not a substitute
for the manual matrix above, and no two-second performance target is claimed as achieved.
No APK packaging, signing, upload, version change, dependency update or remote mutation is part
of these validation commands.

## Final executed results

* Android host suite: **1,828 tests, zero failures/errors/skipped**, 279 XML suites.
  This includes the seven Hero Compose tests, screen-owned backdrop scroll test, HTTP/image-cache
  integration tests, repository bootstrap publication and metadata persistence/concurrency tests.
* The command above completed **BUILD SUCCESSFUL** on the final source revision. Android FullRelease
  Kotlin compilation, native library merge and R8 were validated. The final invocation reused
  unchanged Release outputs built in the preceding run and reran the full host suite.
* All 20 merged native libraries are ELF64 AArch64 (`e_machine = 183`). No APK was generated.
* `git diff --check` passed. No build/dependency/version configuration files or main player/subtitle
  engine sources changed. HEAD remains the base commit; all 43 changed/added files are local.
* Raw final build log: `/workspace/.setup/images-acceptance.log`.
  Host report: `composeApp/build/reports/tests/testAndroidHostTest/index.html`.
* The common-metadata errors and unavailable native/device validations listed above remain limits;
  the successful Android checks do not establish full multiplatform or visual acceptance.
