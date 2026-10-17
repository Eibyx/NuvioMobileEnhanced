package com.nuvio.app.features.home

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.details.MetaLookupOutcome
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.servers.ServerItemRef
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbPreviewArtwork
import com.nuvio.app.features.tmdb.TmdbSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull

/** A Home session owns a bounded set of shared requests, scoped by profile and language.
 * Artwork is published before addon metadata, ratings or trailers. Episode enrichment is
 * deliberately absent: the Details repository retains its complete episode/tracking contract.
 */
internal class HeroMetadataSession(
    private val scope: CoroutineScope,
    private val settings: TmdbSettings,
    private val profileKey: String,
) {
    private val requests = HeroRequestCoordinator<MetaDetails>(scope)

    fun close() = scope.cancel()

    suspend fun load(item: MetaPreview, onBaseMeta: (MetaDetails) -> Unit): MetaDetails? =
        requests.get(item.stableKey()) {
            val artwork = heroAttempt(4_000L) {
                TmdbMetadataService.resolveHeroArtwork(item, settings, profileKey)
            }
            val preview = artwork?.applyTo(item, settings.useBasicInfo) ?: item
            val previewMeta = preview.toHeroMeta(artwork)
            onBaseMeta(previewMeta)
            if (artwork != null) HomeRepository.recordHeroArtwork(preview, profileKey, settings.language)

            val addonMeta = heroAttempt(7_000L) {
                if (ServerItemRef.parse(item.id) != null) {
                    MetaDetailsRepository.fetch(item.type, item.id)
                } else {
                    (MetaDetailsRepository.fetchLightweight(item.type, item.id, includeTmdbFallback = false)
                        as? MetaLookupOutcome.Loaded)?.meta
                }
            }
            var meta = mergeHeroMetadata(previewMeta, addonMeta, settings)
            onBaseMeta(meta)
            // Only the currently selected item's metadata starts before its neighbours.
            if (meta.trailers.isEmpty()) {
                val trailers = heroAttempt(4_000L) { TmdbMetadataService.fetchHeroTrailers(item, settings) }
                if (!trailers.isNullOrEmpty()) {
                    meta = meta.copy(trailers = trailers)
                    onBaseMeta(meta)
                }
            }
            val enriched = heroAttempt(5_000L) {
                MdbListMetadataService.enrichMeta(meta, item.id, MdbListSettingsRepository.snapshot())
            }
            enriched?.copy(externalRatings = enriched.externalRatings + meta.externalRatings.filter { rating ->
                enriched.externalRatings.none { it.source.equals(rating.source, ignoreCase = true) }
            }) ?: meta
        }
}

internal fun TmdbPreviewArtwork.applyTo(item: MetaPreview, includeBasicInfo: Boolean = true): MetaPreview = item.copy(
    name = if (includeBasicInfo) localizedTitle ?: item.name else item.name,
    // Preserve explicit user artwork and its fallback contract.
    poster = if (item.rawPosterUrl != null) item.poster else poster ?: item.poster,
    rawPosterUrl = if (item.rawPosterUrl != null) poster ?: item.rawPosterUrl else null,
    banner = backdrop ?: item.banner,
    logo = logo ?: item.logo,
    description = if (includeBasicInfo) description ?: item.description else item.description,
    releaseInfo = if (includeBasicInfo) releaseInfo ?: item.releaseInfo else item.releaseInfo,
)

private fun MetaPreview.toHeroMeta(artwork: TmdbPreviewArtwork?) = MetaDetails(
    id = id, type = type, name = name, poster = poster,
    background = landscapePoster?.takeIf(String::isNotBlank) ?: banner,
    logo = logo, description = description, releaseInfo = releaseInfo,
    imdbRating = imdbRating, genres = genres, runtime = artwork?.runtime,
    externalRatings = artwork?.tmdbRating?.let { listOf(MetaExternalRating("tmdb", it)) }.orEmpty(),
)

internal fun mergeHeroMetadata(preview: MetaDetails, addon: MetaDetails?, settings: TmdbSettings): MetaDetails {
    if (addon == null) return preview
    return addon.copy(
        // Never allow a provider to change the navigation identity of the selected preview.
        id = preview.id,
        type = preview.type,
        name = if (settings.enabled && settings.useBasicInfo) preview.name else addon.name,
        background = preview.background ?: addon.background,
        poster = preview.poster ?: addon.poster,
        logo = preview.logo ?: addon.logo,
        description = if (settings.enabled && settings.useBasicInfo) preview.description ?: addon.description
            else addon.description ?: preview.description,
        releaseInfo = addon.releaseInfo ?: preview.releaseInfo,
        imdbRating = addon.imdbRating ?: preview.imdbRating,
        runtime = addon.runtime ?: preview.runtime,
        externalRatings = addon.externalRatings + preview.externalRatings.filter { rating ->
            addon.externalRatings.none { it.source.equals(rating.source, ignoreCase = true) }
        },
        genres = addon.genres.ifEmpty { preview.genres },
    )
}
