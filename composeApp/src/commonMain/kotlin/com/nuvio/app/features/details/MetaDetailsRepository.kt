package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.features.addons.supportsResource
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.servers.ServerItemRef
import com.nuvio.app.features.servers.ServerUserStateProjection
import com.nuvio.app.features.servers.message
import com.nuvio.app.features.servers.serverFailure
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.TMDB_RECOMMENDATIONS_PAGE_SIZE
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktConnectionMode
import com.nuvio.app.features.trakt.TraktRelatedRepository
import com.nuvio.app.features.trakt.MoreLikeThisSourcePreference
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.trakt.shouldUseTraktMoreLikeThis
import com.nuvio.app.features.watched.WatchedClock
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

object MetaDetailsRepository {
    private val stateLock = SynchronizedObject()
    private data class CachedMetaEntry(
        val baseMeta: MetaDetails,
        val complete: Boolean = true,
        val seasonsComplete: Boolean = false,
        val seasonsCheckedAtMillis: Long = 0L,
        val metaScreenMeta: MetaDetails? = null,
        val metaScreenSettingsFingerprint: String? = null,
    )

    private val log = Logger.withTag("MetaDetailsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()
    private var activeRequestKey: String? = null
    private val metaEntries = linkedMapOf<String, CachedMetaEntry>()
    private val cachedMetaByRequestKey = object : MutableMap<String, CachedMetaEntry> by metaEntries {
        override fun get(key: String): CachedMetaEntry? = synchronized(stateLock) { metaEntries[key] }
        override fun clear() = synchronized(stateLock) { metaEntries.clear() }
        override fun put(key: String, value: CachedMetaEntry): CachedMetaEntry? = synchronized(stateLock) {
            val previous = metaEntries.put(key, value)
            if (metaEntries.size > 64) metaEntries.remove(metaEntries.keys.first())
            previous
        }
    }
    private inline fun withActive(epoch: Long, key: String, block: () -> Unit) {
        synchronized(stateLock) {
            ensureCacheContext()
            if (generation == epoch && activeRequestKey == key) block()
        }
    }
    private var generation = 0L
    private var cacheContext: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private val requests = com.nuvio.app.core.concurrency.SharedRequests<String, MetaDetails?>(
        CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun ensureCacheContext(): Unit = synchronized(stateLock) {
        val context = "${com.nuvio.app.features.home.HomeSnapshotStore.currentProfileKey()}:${TmdbSettingsRepository.snapshot()}"
        if (cacheContext != context) {
            clear()
            cacheContext = context
        }
    }

    /** A catalog preview is display-only, never a completed metadata response. */
    fun seedPreview(preview: com.nuvio.app.features.home.MetaPreview) {
        synchronized(stateLock) {
            ensureCacheContext()
            val key = "${preview.type}:${preview.id}"
            if (cachedMetaByRequestKey.containsKey(key)) return
            cachedMetaByRequestKey[key] = CachedMetaEntry(
                baseMeta = MetaDetails(id = preview.id, type = preview.type, name = preview.name,
                    poster = preview.poster, background = preview.banner?.takeIf { it.isNotBlank() } ?: preview.landscapePoster,
                    logo = preview.logo, description = preview.description, releaseInfo = preview.releaseInfo,
                    imdbRating = preview.imdbRating, genres = preview.genres), complete = false,
            )
        }
    }

    fun load(type: String, id: String, prioritySeason: Int? = null) {
        synchronized(stateLock) {
            ensureCacheContext()
            val key = "$type:$id"
            ServerItemRef.parse(id)?.let { loadServerMeta(key, it); return }
            if (activeRequestKey == key && loadJob?.isActive == true) return
            loadJob?.cancel()
            activeRequestKey = key
            val epoch = generation
            val settings = MdbListSettingsRepository.snapshot()
            val fingerprint = buildMetaScreenSettingsFingerprint(settings)
            val cached = cachedMetaByRequestKey[key]
            if (cached?.metaScreenSettingsFingerprint == fingerprint && cached.metaScreenMeta != null && cached.seasonsComplete && (cached.metaScreenMeta.videos.none { it.season != null } ||
                kotlin.time.Clock.System.now().toEpochMilliseconds() - cached.seasonsCheckedAtMillis in 0 until 60_000L)) {
                _uiState.value = MetaDetailsUiState(meta = cached.metaScreenMeta.withUnreleasedFilter())
                return
            }
            val previous = _uiState.value.meta?.takeIf { it.type == type && it.id == id }
            _uiState.value = MetaDetailsUiState(isLoading = true, meta = (cached?.baseMeta ?: previous)?.withUnreleasedFilter())
            loadJob = scope.launch {
                try {
                    val meta = cached?.metaScreenMeta?.takeIf { cached.metaScreenSettingsFingerprint == fingerprint }
                        ?: fetch(type, id, includeEpisodes = false)
                    if (generation != epoch || activeRequestKey != key) return@launch
                    if (meta == null) {
                        val message = getString(Res.string.details_load_failed_all_addons)
                        withActive(epoch, key) { _uiState.value = MetaDetailsUiState(meta = _uiState.value.meta, errorMessage = message) }
                        return@launch
                    }
                    if (cached?.metaScreenMeta != null && cached.metaScreenSettingsFingerprint == fingerprint) {
                        withActive(epoch, key) { _uiState.value = MetaDetailsUiState(meta = meta.withUnreleasedFilter()) }
                    } else {
                        publishLoadedMeta(key, meta, id, type, settings, fingerprint, epoch)
                    }
                    if (generation != epoch || activeRequestKey != key) return@launch
                    val completeCore = cachedMetaByRequestKey[key]?.metaScreenMeta ?: meta
                    TmdbMetadataService.enrichEpisodes(completeCore, id, TmdbSettingsRepository.snapshot(), prioritySeason = prioritySeason) { updated ->
                        withActive(epoch, key) {
                                cachedMetaByRequestKey[key]?.let { entry ->
                                    cachedMetaByRequestKey[key] = entry.copy(metaScreenMeta = updated, baseMeta = updated)
                                }
                                _uiState.value = MetaDetailsUiState(meta = updated.withUnreleasedFilter())
                        }
                    }
                    withActive(epoch, key) {
                        cachedMetaByRequestKey[key]?.let { cachedMetaByRequestKey[key] = it.copy(seasonsComplete = true, seasonsCheckedAtMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (generation == epoch && activeRequestKey == key) {
                        val message = getString(Res.string.details_load_failed_all_addons)
                        withActive(epoch, key) { _uiState.value = MetaDetailsUiState(meta = _uiState.value.meta, errorMessage = message) }
                    }
                }
            }
        }
    }

    fun peek(type: String, id: String): MetaDetails? {
        return synchronized(stateLock) {
            ensureCacheContext()
            val requestKey = "$type:$id"
            val currentMeta = _uiState.value.meta?.takeIf { it.type == type && it.id == id }
            if (currentMeta != null) return currentMeta

            val cachedEntry = cachedMetaByRequestKey[requestKey] ?: return null
            val cachedMeta = cachedEntry.metaScreenMeta
                ?.takeIf {
                    cachedEntry.metaScreenSettingsFingerprint ==
                        buildMetaScreenSettingsFingerprint(MdbListSettingsRepository.snapshot())
                }
                ?: cachedEntry.baseMeta
            return cachedMeta.withUnreleasedFilter()
        }
    }

    fun clearCachedMetadata(): Unit = synchronized(stateLock) {
        generation++
        loadJob?.cancel()
        activeRequestKey = null
        cachedMetaByRequestKey.clear()
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun clear(): Unit = synchronized(stateLock) {
        generation++
        loadJob?.cancel()
        activeRequestKey = null
        cachedMetaByRequestKey.clear()
        _uiState.value = MetaDetailsUiState()
    }

    suspend fun fetch(
        type: String, id: String, cacheResult: Boolean = true, useCache: Boolean = true,
        includeEpisodes: Boolean = true,
    ): MetaDetails? {
        val epoch = synchronized(stateLock) { ensureCacheContext(); generation }
        val core = requests.await("$epoch:$type:$id:$useCache") {
            fetchUncached(type, id, useCache, epoch)
        }
        synchronized(stateLock) {
            ensureCacheContext()
            if (generation != epoch) return null
            if (cacheResult && core != null) {
                cachedMetaByRequestKey["$type:$id"] = cachedMetaByRequestKey["$type:$id"]
                    ?.copy(baseMeta = core, complete = true) ?: CachedMetaEntry(core)
            }
        }
        val result = if (core != null && includeEpisodes && ServerItemRef.parse(id) == null)
            TmdbMetadataService.enrichEpisodes(core, id, TmdbSettingsRepository.snapshot()) else core
        return synchronized(stateLock) { ensureCacheContext(); result.takeIf { generation == epoch } }
    }

    private suspend fun fetchUncached(
        type: String, id: String, useCache: Boolean, epoch: Long,
    ): MetaDetails? {
        val requestKey = "$type:$id"
        if (useCache) {
            synchronized(stateLock) { cachedMetaByRequestKey[requestKey] }?.takeIf { it.complete }?.let { return it.baseMeta }
        }
        ServerItemRef.parse(id)?.let { ref ->
            return runCatching { ServerCatalog.details(ref).meta }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()

        }

        val metaLookupId = resolveMetaLookupId(itemId = id, itemType = type)
        val manifests = findReadyMetaManifests(type = type, id = metaLookupId)

        val publishPartial: suspend (MetaDetails) -> Unit = { partial ->
            synchronized(stateLock) {
                ensureCacheContext()
                if (epoch == generation && activeRequestKey == requestKey) {
                    val previous = _uiState.value.meta
                    val value = if (partial.name.startsWith("TMDB ") && previous?.id == id)
                        partial.copy(name = previous.name, description = partial.description ?: previous.description) else partial
                    com.nuvio.app.core.ui.ImagePerformance.mark("details.basicArtwork", requestKey)
                    _uiState.value = MetaDetailsUiState(isLoading = true, meta = value.withUnreleasedFilter())
                }
            }
        }
        for (manifest in manifests) {
            val result = tryFetchMeta(manifest, type, metaLookupId, includeMdbList = false, onProgress = publishPartial)
            if (result != null) {
                return result
            }
        }

        return tryFetchTmdbFallbackMeta(type = type, id = id, onProgress = publishPartial)
    }

    private val backgroundBackoffUntilByHost = mutableMapOf<String, Long>()

    internal suspend fun fetchLightweight(type: String, id: String): MetaLookupOutcome {
        val metaLookupId = resolveMetaLookupId(itemId = id, itemType = type)
        val manifests = findReadyMetaManifests(type = type, id = metaLookupId)
        if (manifests.isEmpty()) {
            return tryFetchTmdbFallbackMeta(type = type, id = id)
                ?.let { meta -> MetaLookupOutcome.Loaded(meta) }
                ?: MetaLookupOutcome.Unavailable
        }

        var throttledUntil: Long? = null
        for (manifest in manifests) {
            val host = manifest.transportUrl.addonHost()
            val now = WatchedClock.nowEpochMs()
            val blockedUntil = backgroundBackoffUntilByHost[host]
            if (blockedUntil != null && blockedUntil > now) {
                throttledUntil = minOf(throttledUntil ?: blockedUntil, blockedUntil)
                continue
            }
            val url = buildAddonResourceUrl(
                manifestUrl = manifest.transportUrl,
                resource = "meta",
                type = type,
                id = metaLookupId,
            )
            val response = try {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    httpRequestRaw(
                        method = "GET",
                        url = url,
                        headers = mapOf("Accept" to "application/json"),
                        body = "",
                        maxResponseBodyBytes = LIGHTWEIGHT_META_MAX_BYTES,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Lightweight meta request failed url=$url" }
                null
            }
            if (response == null) continue

            if (response.status == 429 || response.status == 503) {
                val until = now + response.retryAfterMs()
                backgroundBackoffUntilByHost[host] = until
                InAppLogger.warn(
                    "Metadata/AddonFetch",
                    "Background lookups paused host=$host status=${response.status} for ${(until - now) / 1000}s",
                )
                throttledUntil = minOf(throttledUntil ?: until, until)
                continue
            }
            if (response.status !in 200..299 || response.body.isBlank()) continue

            val meta = try {
                withContext(Dispatchers.Default) { MetaDetailsParser.parse(response.body) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Failed to parse lightweight meta url=$url" }
                null
            }
            if (meta != null) return MetaLookupOutcome.Loaded(meta)
        }

        return throttledUntil?.let { until -> MetaLookupOutcome.Throttled(until) } ?: MetaLookupOutcome.Failed
    }

    private fun String.addonHost(): String =
        substringAfter("://").substringBefore('/').substringBefore('?').lowercase()

    private fun com.nuvio.app.features.addons.RawHttpResponse.retryAfterMs(): Long {
        val seconds = headers["retry-after"]?.trim()?.toLongOrNull()
        val delayMs = seconds?.times(1_000L) ?: DEFAULT_BACKGROUND_BACKOFF_MS
        return delayMs.coerceIn(MIN_BACKGROUND_BACKOFF_MS, MAX_BACKGROUND_BACKOFF_MS)
    }

    private const val LIGHTWEIGHT_META_MAX_BYTES = 8 * 1024 * 1024
    private const val DEFAULT_BACKGROUND_BACKOFF_MS = 2L * 60_000L
    private const val MIN_BACKGROUND_BACKOFF_MS = 30_000L
    private const val MAX_BACKGROUND_BACKOFF_MS = 15L * 60_000L

    private fun loadServerMeta(requestKey: String, ref: ServerItemRef) {
        if (loadJob?.isActive == true && activeRequestKey == requestKey) return
        loadJob?.cancel()
        val epoch = generation
        activeRequestKey = requestKey
        _uiState.value = MetaDetailsUiState(
            isLoading = true,
            meta = cachedMetaByRequestKey[requestKey]?.baseMeta,
        )
        loadJob = scope.launch {
            val result = runCatching { ServerCatalog.details(ref) }
            if (activeRequestKey != requestKey || generation != epoch) return@launch
            result.fold(
                onSuccess = { details ->
                    val meta = details.meta
                    ServerUserStateProjection.apply(details)
                    cachedMetaByRequestKey[requestKey] = CachedMetaEntry(baseMeta = meta)
                    _uiState.value = MetaDetailsUiState(meta = meta)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    log.w { "Server details failed: ${error.serverFailure()}" }
                    val cached = cachedMetaByRequestKey[requestKey]?.baseMeta
                    _uiState.value = MetaDetailsUiState(
                        meta = cached,
                        errorMessage = if (cached == null) getString(error.serverFailure().message()) else null,
                    )
                    if (cached == null) activeRequestKey = null
                },
            )
        }
    }

    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val METADATA_PROVIDER_READY_TIMEOUT_MS = 10_000L
    private const val TMDB_ENRICH_TIMEOUT_MS = 5_000L
    private const val MDBLIST_ENRICH_TIMEOUT_MS = 5_000L

    private suspend fun tryFetchMeta(
        manifest: AddonManifest,
        type: String,
        id: String,
        includeMdbList: Boolean,
        onProgress: suspend (MetaDetails) -> Unit = {},
    ): MetaDetails? {
        val url = buildAddonResourceUrl(
            manifestUrl = manifest.transportUrl,
            resource = "meta",
            type = type,
            id = id,
        )

        return try {
            TmdbSettingsRepository.ensureLoaded()
            log.d { "Fetching meta resource=${url.hashCode().toUInt()} type=$type" }
            InAppLogger.info("Metadata/AddonFetch", "Fetching meta type=$type id=$id url=${InAppLogger.redactUrl(url)}")
            val payload = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchAddonResponseText(url) } ?: return null
            log.d { "Meta payload length=${payload.length}" }
            InAppLogger.debug("Metadata/AddonFetch", "Meta payload length=${payload.length} type=$type id=$id")
            val result = withContext(Dispatchers.Default) { MetaDetailsParser.parse(payload) }
            onProgress(result)
            val tmdbEnriched = TmdbMetadataService.enrichMeta(
                    meta = result,
                    fallbackItemId = id,
                    settings = TmdbSettingsRepository.snapshot(),
                    includeEpisodes = false,
                    onProgress = onProgress,
                )
            val enriched = if (includeMdbList) {
                MdbListSettingsRepository.ensureLoaded()
                withTimeoutOrNull(MDBLIST_ENRICH_TIMEOUT_MS) {
                    MdbListMetadataService.enrichMeta(
                        meta = tmdbEnriched,
                        fallbackItemId = id,
                        settings = MdbListSettingsRepository.snapshot(),
                    )
                } ?: tmdbEnriched
            } else {
                tmdbEnriched
            }
            log.d { "Parsed meta: type=${enriched.type}, name=${enriched.name}, videos=${enriched.videos.size}" }
            InAppLogger.info(
                "Metadata/AddonFetch",
                "Parsed meta source=${manifest.name} type=${enriched.type} id=${enriched.id} name=${enriched.name} videos=${enriched.videos.size}",
            )
            if (enriched.videos.isNotEmpty()) {
                val first = enriched.videos.first()
                log.d { "First video: id=${first.id} title=${first.title} s=${first.season} e=${first.episode} embeddedStreams=${first.streams.size}" }
                InAppLogger.debug("Metadata/AddonFetch", "First video id=${first.id} title=${first.title} s=${first.season} e=${first.episode} embeddedStreams=${first.streams.size}")
            }
            enriched
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.e { "Failed to fetch/parse meta resource=${url.hashCode().toUInt()} error=${e::class.simpleName}" }
            InAppLogger.warn("Metadata/AddonFetch", "Failed meta source=${manifest.name} url=${InAppLogger.redactUrl(url)} error=${InAppLogger.throwableSummary(e)}")
            null
        }
    }

    private suspend fun findReadyMetaManifests(type: String, id: String): List<AddonManifest> {
        AddonRepository.initialize()

        findMetaManifests(AddonRepository.uiState.value, type, id).takeIf { it.isNotEmpty() }?.let { return it }

        if (!AddonRepository.uiState.value.hasPendingEnabledAddonManifests()) {
            return emptyList()
        }

        val readyState = withTimeoutOrNull(METADATA_PROVIDER_READY_TIMEOUT_MS) {
            AddonRepository.uiState.first { state ->
                findMetaManifests(state, type, id).isNotEmpty() ||
                    !state.hasPendingEnabledAddonManifests()
            }
        } ?: AddonRepository.uiState.value

        return findMetaManifests(readyState, type, id)
    }

    private fun findMetaManifests(state: com.nuvio.app.features.addons.AddonsUiState, type: String, id: String): List<AddonManifest> =
        state.addons
            .enabledAddons()
            .mapNotNull { it.manifest }
            .filter { manifest -> manifest.supportsResource("meta", type, id) }

    private fun com.nuvio.app.features.addons.AddonsUiState.hasPendingEnabledAddonManifests(): Boolean =
        addons.enabledAddons().any { addon -> addon.manifest == null && addon.isRefreshing }

    private suspend fun resolveMetaLookupId(itemId: String, itemType: String): String {
        val tmdbId = itemId
            .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.substringBefore(':')
            ?.toIntOrNull()
            ?: return itemId

        val resolved = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            TmdbService.tmdbToImdb(tmdbId = tmdbId, mediaType = itemType)
        }
            ?.takeIf { it.isNotBlank() }
            ?: itemId
        InAppLogger.debug("Metadata/MetaDetailsRepository", "TMDB lookup tmdb:$tmdbId mediaType=$itemType resolved=$resolved")
        return resolved
    }

    private suspend fun tryFetchTmdbFallbackMeta(
        type: String, id: String, onProgress: suspend (MetaDetails) -> Unit = {},
    ): MetaDetails? {
        InAppLogger.info("Metadata/TMDB", "Fetching standalone fallback meta type=$type id=$id")
        val result = TmdbMetadataService.fetchStandaloneMeta(
                type = type,
                id = id,
                settings = TmdbSettingsRepository.snapshot(),
                onProgress = onProgress,
            )
        if (result == null) {
            InAppLogger.warn("Metadata/TMDB", "Standalone fallback returned empty type=$type id=$id")
        } else {
            InAppLogger.info("Metadata/TMDB", "Standalone fallback loaded type=${result.type} id=${result.id} name=${result.name}")
        }
        return result
    }

    private suspend fun publishLoadedMeta(
        requestKey: String,
        meta: MetaDetails,
        fallbackItemId: String,
        fallbackItemType: String,
        mdbListSettings: com.nuvio.app.features.mdblist.MdbListSettings,
        metaScreenSettingsFingerprint: String,
        epoch: Long,
    ) {
        val cachedEntry = CachedMetaEntry(baseMeta = meta)
        val shouldEnrich = shouldEnrichForMetaScreen(meta, fallbackItemId, mdbListSettings)
        withActive(epoch, requestKey) {
            cachedMetaByRequestKey[requestKey] = if (shouldEnrich) cachedEntry else cachedEntry.copy(
                metaScreenMeta = meta, metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
            )
            _uiState.value = MetaDetailsUiState(isLoading = shouldEnrich, meta = meta.withUnreleasedFilter())
        }
        if (!shouldEnrich) return
        val enrichedMeta = withContext(Dispatchers.Default) {
            enrichForMetaScreen(
                requestKey = requestKey,
                meta = meta,
                fallbackItemId = fallbackItemId,
                fallbackItemType = fallbackItemType,
                settings = mdbListSettings,
                settingsFingerprint = metaScreenSettingsFingerprint,
            )
        }
        withActive(epoch, requestKey) {
            cachedMetaByRequestKey[requestKey] = cachedEntry.copy(
                metaScreenMeta = enrichedMeta,
                metaScreenSettingsFingerprint = metaScreenSettingsFingerprint,
            )
            _uiState.value = MetaDetailsUiState(meta = enrichedMeta.withUnreleasedFilter())
        }
    }

    private suspend fun enrichForMetaScreen(
        requestKey: String,
        meta: MetaDetails,
        fallbackItemId: String,
        fallbackItemType: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
        settingsFingerprint: String,
    ): MetaDetails {
        val mdbListEnrichedMeta = withTimeoutOrNull(MDBLIST_ENRICH_TIMEOUT_MS) {
            MdbListMetadataService.enrichMeta(
                meta = meta,
                fallbackItemId = fallbackItemId,
                settings = settings,
            )
        } ?: meta
        val imdbRatedMeta = OmdbEpisodeRatingsService.applyTo(
            meta = mdbListEnrichedMeta,
            fallbackItemId = fallbackItemId,
            settings = TmdbSettingsRepository.snapshot(),
        )
        val enrichedMeta = applyMoreLikeThisSource(
            meta = imdbRatedMeta,
            fallbackItemId = fallbackItemId,
            fallbackItemType = fallbackItemType,
        )

        return enrichedMeta
    }

    private suspend fun applyMoreLikeThisSource(
        meta: MetaDetails,
        fallbackItemId: String,
        fallbackItemType: String,
    ): MetaDetails {
        TrackingSettingsRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()
        TmdbSettingsRepository.ensureLoaded()

        val trackingSettings = TrackingSettingsRepository.uiState.value
        val isTraktAuthenticated = TraktAuthRepository.uiState.value.mode == TraktConnectionMode.CONNECTED
        val tmdbSettings = TmdbSettingsRepository.snapshot()

        // Simkl source
        if (shouldUseSimklMoreLikeThis(trackingSettings.moreLikeThisSource) &&
            supportsMoreLikeThis(meta, fallbackItemType)
        ) {
            val items = runCatching {
                com.nuvio.app.features.simkl.SimklRelatedRepository.getRelated(
                    meta = meta,
                    fallbackItemId = fallbackItemId,
                    fallbackItemType = fallbackItemType,
                )
            }.onFailure { error ->
                log.w { "Failed to load Simkl related titles for ${meta.id}: ${error.message}" }
            }.getOrDefault(emptyList())

            return meta.copy(
                moreLikeThis = items,
                moreLikeThisSource = MoreLikeThisSource.SIMKL.takeIf { items.isNotEmpty() },
            )
        }

        // Trakt source
        val shouldUseTrakt = shouldUseTraktMoreLikeThis(
            isAuthenticated = isTraktAuthenticated,
            source = trackingSettings.moreLikeThisSource,
        ) && supportsMoreLikeThis(meta, fallbackItemType)

        if (shouldUseTrakt) {
            val page = runCatching {
                TraktRelatedRepository.getRelated(
                    itemId = meta.id,
                    itemType = meta.type,
                    fallbackItemId = fallbackItemId,
                    fallbackItemType = fallbackItemType,
                )
            }.onFailure { error ->
                log.w { "Failed to load Trakt related titles for ${meta.id}: ${error.message}" }
                InAppLogger.warn("Metadata/Trakt", "Failed related titles id=${meta.id} error=${InAppLogger.throwableSummary(error)}")
            }.getOrDefault(MoreLikeThisPage())
            InAppLogger.info("Metadata/Trakt", "Related titles id=${meta.id} count=${page.items.size} hasMore=${page.hasMore}")

            if (page.items.isNotEmpty()) {
                return meta.copy(
                    moreLikeThis = page.items,
                    moreLikeThisSource = MoreLikeThisSource.TRAKT,
                    moreLikeThisHasMore = page.hasMore,
                )
            }
        }

        if (!tmdbSettings.enabled || !tmdbSettings.useMoreLikeThis) {
            return meta.copy(
                moreLikeThis = emptyList(),
                moreLikeThisSource = null,
                moreLikeThisHasMore = false,
            )
        }

        InAppLogger.info(
            "Metadata/TMDB",
            "More Like This fallback id=${meta.id} count=${meta.moreLikeThis.size}",
        )
        return meta.copy(
            moreLikeThisSource = MoreLikeThisSource.TMDB.takeIf { meta.moreLikeThis.isNotEmpty() },
            moreLikeThisHasMore = meta.moreLikeThis.size >= TMDB_RECOMMENDATIONS_PAGE_SIZE,
        )
    }

    private fun shouldFetchMdbListOnMetaScreen(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): Boolean = MdbListMetadataService.shouldFetchForMeta(
        meta = meta,
        fallbackItemId = fallbackItemId,
        settings = settings,
    )

    private fun shouldEnrichForMetaScreen(
        meta: MetaDetails,
        fallbackItemId: String,
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): Boolean {
        if (shouldFetchMdbListOnMetaScreen(meta, fallbackItemId, settings)) return true
        TmdbSettingsRepository.ensureLoaded()
        if (OmdbEpisodeRatingsService.shouldApply(meta, TmdbSettingsRepository.snapshot())) return true
        return shouldApplyMoreLikeThisSource(meta)
    }

    private fun shouldApplyMoreLikeThisSource(meta: MetaDetails): Boolean {
        TrackingSettingsRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()
        TmdbSettingsRepository.ensureLoaded()

        val trackingSettings = TrackingSettingsRepository.uiState.value
        val isTraktAuthenticated = TraktAuthRepository.uiState.value.mode == TraktConnectionMode.CONNECTED
        val tmdbSettings = TmdbSettingsRepository.snapshot()
        return shouldUseTraktMoreLikeThis(
            isAuthenticated = isTraktAuthenticated,
            source = trackingSettings.moreLikeThisSource,
        ) || !tmdbSettings.enabled || !tmdbSettings.useMoreLikeThis || meta.moreLikeThisSource == null && meta.moreLikeThis.isNotEmpty()
    }

    private fun buildMetaScreenSettingsFingerprint(
        settings: com.nuvio.app.features.mdblist.MdbListSettings,
    ): String {
        TrackingSettingsRepository.ensureLoaded()
        TraktAuthRepository.ensureLoaded()
        TmdbSettingsRepository.ensureLoaded()
        val providers = settings.enabledProvidersInPriorityOrder().joinToString(",")
        val trackingSettings = TrackingSettingsRepository.uiState.value
        val traktAuthMode = TraktAuthRepository.uiState.value.mode
        val tmdbSettings = TmdbSettingsRepository.snapshot()
        return buildString {
            append("${settings.enabled}:${settings.apiKey.trim()}:$providers")
            append("|mdblist_account=${settings.accountScope.takeUnless { settings.hasApiKey }}")
            append("|more_like=${trackingSettings.moreLikeThisSource}:$traktAuthMode")
            append("|tmdb=${tmdbSettings.enabled}:${tmdbSettings.useMoreLikeThis}:${tmdbSettings.language}")
            append("|omdb=${OmdbEpisodeRatingsService.hasApiKey}:${tmdbSettings.useEpisodeRatings}")
        }
    }

    private fun supportsMoreLikeThis(meta: MetaDetails, fallbackItemType: String): Boolean =
        normalizeMoreLikeThisType(meta.type) != null || normalizeMoreLikeThisType(fallbackItemType) != null

    private fun shouldUseSimklMoreLikeThis(source: MoreLikeThisSourcePreference): Boolean {
        if (source != MoreLikeThisSourcePreference.SIMKL) return false
        com.nuvio.app.features.simkl.SimklAuthRepository.ensureLoaded()
        return com.nuvio.app.features.simkl.SimklAuthRepository.isAuthenticated.value
    }

    private fun normalizeMoreLikeThisType(value: String?): String? =
        when (value?.trim()?.lowercase()) {
            "movie", "film" -> "movie"
            "series", "show", "tv", "tvshow" -> "series"
            else -> null
        }

    private fun MetaDetails.withUnreleasedFilter(): MetaDetails {
        val posterPattern = com.nuvio.app.core.poster.CustomPosterUrlRepository.let {
            it.ensureLoaded()
            it.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.DETAILS)
        }
        val base = withCustomPosterUrls(posterPattern)
        if (!HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) return base
        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val releasedMoreLikeThis = base.moreLikeThis.filterReleasedItems(todayIsoDate)
        return base.copy(
            moreLikeThis = releasedMoreLikeThis,
            moreLikeThisSource = base.moreLikeThisSource.takeIf { releasedMoreLikeThis.isNotEmpty() },
            collectionItems = base.collectionItems.filterReleasedItems(todayIsoDate),
        )
    }

   
    fun findEmbeddedStreams(videoId: String): List<com.nuvio.app.features.streams.StreamItem> {
        val meta = _uiState.value.meta ?: return emptyList()
        val videosWithStreams = meta.videos.filter { it.streams.isNotEmpty() }
        if (videosWithStreams.isEmpty()) return emptyList()

        val directMatch = videosWithStreams.firstOrNull { it.id == videoId }
        if (directMatch != null) return directMatch.streams

        val parts = videoId.split(":")
        if (parts.size >= 3) {
            val season = parts[parts.size - 2].toIntOrNull()
            val episode = parts[parts.size - 1].toIntOrNull()
            if (season != null && episode != null) {
                val episodeMatch = videosWithStreams.firstOrNull { it.season == season && it.episode == episode }
                if (episodeMatch != null) return episodeMatch.streams
            }
        }

        val prefixMatch = videosWithStreams.firstOrNull { it.id.startsWith("$videoId:") }
        if (prefixMatch != null) return prefixMatch.streams

        if (videoId == meta.id && videosWithStreams.size == 1) {
            return videosWithStreams.first().streams
        }

        if (videoId == meta.id && videosWithStreams.isNotEmpty()) {
            return videosWithStreams.flatMap { it.streams }
        }

        return emptyList()
    }
}

internal sealed interface MetaLookupOutcome {
    class Loaded(val meta: MetaDetails) : MetaLookupOutcome

    class Throttled(val retryAtEpochMs: Long) : MetaLookupOutcome

    object Unavailable : MetaLookupOutcome

    object Failed : MetaLookupOutcome
}
