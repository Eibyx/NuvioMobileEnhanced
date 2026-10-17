package com.nuvio.app.features.home

import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.getBoolean
import com.nuvio.app.core.avix.putBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Avix (Yesnt10 port): Home and catalog layout options. Device-local only (DECISIONS D-5):
 * the synced home catalog settings of the Nuvio account are not touched.
 */
data class AvixHomeLayout(
    /** Poster rows per Home catalog shelf (1–4). */
    val shelfRows: Int = 1,
    /** Fixed column count for catalog grids (2–6); 0 = automatic from screen width. */
    val catalogColumns: Int = 0,
    val hideReleaseDates: Boolean = false,
    val heroAutoScroll: Boolean = true,
    val hero: AvixHeroOptions = AvixHeroOptions(),
    val pullToRefresh: Boolean = false,
)

/** Yesnt10 hero display modes. BALANCED is the existing Avix/Luqman look. */
enum class AvixHeroDisplayMode(val storageValue: String) {
    CINEMATIC("cinematic"),
    BALANCED("balanced"),
    INFO_RICH("info_rich"),
    ;

    companion object {
        fun fromStorageValue(value: String?): AvixHeroDisplayMode =
            entries.firstOrNull { it.storageValue == value } ?: BALANCED
    }
}

data class AvixHeroOptions(
    val displayMode: AvixHeroDisplayMode = AvixHeroDisplayMode.BALANCED,
    /** Only rating, year and type in the metadata row (no genre or age rating). */
    val compactMetadata: Boolean = false,
    val showRatings: Boolean = true,
    val showOverview: Boolean = true,
    /** Full-bleed hero uses the portrait poster instead of the backdrop on portrait phones. */
    val posterArtwork: Boolean = false,
    val motionPreview: Boolean = true,
)

/** Alpha stops of the full-bleed hero's darkening overlay (top to bottom). */
internal fun heroOverlayAlphas(mode: AvixHeroDisplayMode): List<Float> = when (mode) {
    AvixHeroDisplayMode.CINEMATIC -> listOf(0f, 0.04f, 0.2f, 0.62f)
    AvixHeroDisplayMode.BALANCED -> listOf(0.02f, 0.12f, 0.34f, 0.78f)
    AvixHeroDisplayMode.INFO_RICH -> listOf(0.06f, 0.2f, 0.46f, 0.86f)
}

/** Synopsis lines in the hero; 0 hides it. */
internal fun heroOverviewMaxLines(options: AvixHeroOptions): Int = when {
    !options.showOverview -> 0
    options.displayMode == AvixHeroDisplayMode.CINEMATIC -> 1
    options.displayMode == AvixHeroDisplayMode.INFO_RICH -> 4
    else -> 2
}

object AvixHomeLayoutPreferences {
    const val MIN_SHELF_ROWS = 1
    const val MAX_SHELF_ROWS = 4
    const val MIN_CATALOG_COLUMNS = 2
    const val MAX_CATALOG_COLUMNS = 6

    private const val KEY_SHELF_ROWS = "ui.home.shelf_rows"
    private const val KEY_CATALOG_COLUMNS = "ui.catalog.columns"
    private const val KEY_HIDE_RELEASE_DATES = "ui.home.hide_release_dates"
    private const val KEY_HERO_AUTO_SCROLL = "ui.home.hero_auto_scroll"
    private const val KEY_PULL_TO_REFRESH = "ui.home.pull_to_refresh"
    private const val KEY_HERO_MODE = "ui.home.hero_mode"
    private const val KEY_HERO_COMPACT = "ui.home.hero_compact_metadata"
    private const val KEY_HERO_RATINGS = "ui.home.hero_show_ratings"
    private const val KEY_HERO_OVERVIEW = "ui.home.hero_show_overview"
    private const val KEY_HERO_MOTION = "ui.home.hero_motion"
    private const val KEY_HERO_POSTER = "ui.home.hero_poster_artwork"

    private val _layout = MutableStateFlow(AvixHomeLayout())
    val layout: StateFlow<AvixHomeLayout> = _layout.asStateFlow()
    private var loaded = false

    init {
        com.nuvio.app.core.avix.AvixPreferencesReload.register {
            loaded = false
            ensureLoaded()
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _layout.value = AvixHomeLayout(
            shelfRows = normalizeShelfRows(AvixLocalStore.getString(KEY_SHELF_ROWS)?.toIntOrNull()),
            catalogColumns = normalizeCatalogColumns(AvixLocalStore.getString(KEY_CATALOG_COLUMNS)?.toIntOrNull()),
            hideReleaseDates = AvixLocalStore.getBoolean(KEY_HIDE_RELEASE_DATES) ?: false,
            heroAutoScroll = AvixLocalStore.getBoolean(KEY_HERO_AUTO_SCROLL) ?: true,
            pullToRefresh = AvixLocalStore.getBoolean(KEY_PULL_TO_REFRESH) ?: false,
            hero = AvixHeroOptions(
                displayMode = AvixHeroDisplayMode.fromStorageValue(AvixLocalStore.getString(KEY_HERO_MODE)),
                compactMetadata = AvixLocalStore.getBoolean(KEY_HERO_COMPACT) ?: false,
                showRatings = AvixLocalStore.getBoolean(KEY_HERO_RATINGS) ?: true,
                showOverview = AvixLocalStore.getBoolean(KEY_HERO_OVERVIEW) ?: true,
                motionPreview = AvixLocalStore.getBoolean(KEY_HERO_MOTION) ?: true,
                posterArtwork = AvixLocalStore.getBoolean(KEY_HERO_POSTER) ?: false,
            ),
        )
    }

    fun setShelfRows(rows: Int) = update(KEY_SHELF_ROWS, normalizeShelfRows(rows).toString()) {
        it.copy(shelfRows = normalizeShelfRows(rows))
    }

    fun setCatalogColumns(columns: Int) =
        update(KEY_CATALOG_COLUMNS, normalizeCatalogColumns(columns).toString()) {
            it.copy(catalogColumns = normalizeCatalogColumns(columns))
        }

    fun setHideReleaseDates(hide: Boolean) {
        ensureLoaded()
        _layout.value = _layout.value.copy(hideReleaseDates = hide)
        AvixLocalStore.putBoolean(KEY_HIDE_RELEASE_DATES, hide)
    }

    fun setHeroAutoScroll(enabled: Boolean) {
        ensureLoaded()
        _layout.value = _layout.value.copy(heroAutoScroll = enabled)
        AvixLocalStore.putBoolean(KEY_HERO_AUTO_SCROLL, enabled)
    }

    fun setPullToRefresh(value: Boolean) =
        update(KEY_PULL_TO_REFRESH, value.toString()) { it.copy(pullToRefresh = value) }

    fun setHeroDisplayMode(mode: AvixHeroDisplayMode) =
        update(KEY_HERO_MODE, mode.storageValue) { it.copy(hero = it.hero.copy(displayMode = mode)) }

    fun setHeroCompactMetadata(value: Boolean) =
        update(KEY_HERO_COMPACT, value.toString()) { it.copy(hero = it.hero.copy(compactMetadata = value)) }

    fun setHeroShowRatings(value: Boolean) =
        update(KEY_HERO_RATINGS, value.toString()) { it.copy(hero = it.hero.copy(showRatings = value)) }

    fun setHeroShowOverview(value: Boolean) =
        update(KEY_HERO_OVERVIEW, value.toString()) { it.copy(hero = it.hero.copy(showOverview = value)) }

    fun setHeroMotionPreview(value: Boolean) =
        update(KEY_HERO_MOTION, value.toString()) { it.copy(hero = it.hero.copy(motionPreview = value)) }

    fun setHeroPosterArtwork(value: Boolean) =
        update(KEY_HERO_POSTER, value.toString()) { it.copy(hero = it.hero.copy(posterArtwork = value)) }

    private inline fun update(key: String, stored: String, transform: (AvixHomeLayout) -> AvixHomeLayout) {
        ensureLoaded()
        _layout.value = transform(_layout.value)
        AvixLocalStore.putString(key, stored)
    }
}

internal fun normalizeShelfRows(rows: Int?): Int =
    (rows ?: AvixHomeLayoutPreferences.MIN_SHELF_ROWS)
        .coerceIn(AvixHomeLayoutPreferences.MIN_SHELF_ROWS, AvixHomeLayoutPreferences.MAX_SHELF_ROWS)

/** 0 (automatic) or a value within 2–6. */
internal fun normalizeCatalogColumns(columns: Int?): Int = when {
    columns == null || columns <= 0 -> 0
    else -> columns.coerceIn(AvixHomeLayoutPreferences.MIN_CATALOG_COLUMNS, AvixHomeLayoutPreferences.MAX_CATALOG_COLUMNS)
}

/** Columns for a catalog grid: the fixed choice, or [automatic] when none is set. */
internal fun resolveCatalogColumns(preferred: Int, automatic: Int): Int =
    if (preferred > 0) normalizeCatalogColumns(preferred) else automatic

/** Number of items loaded into a Home shelf with [rows] rows. */
internal fun homeShelfPreviewLimit(baseLimit: Int, rows: Int): Int = baseLimit * normalizeShelfRows(rows)
