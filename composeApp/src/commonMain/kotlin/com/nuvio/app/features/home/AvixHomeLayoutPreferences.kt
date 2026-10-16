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
)

object AvixHomeLayoutPreferences {
    const val MIN_SHELF_ROWS = 1
    const val MAX_SHELF_ROWS = 4
    const val MIN_CATALOG_COLUMNS = 2
    const val MAX_CATALOG_COLUMNS = 6

    private const val KEY_SHELF_ROWS = "ui.home.shelf_rows"
    private const val KEY_CATALOG_COLUMNS = "ui.catalog.columns"
    private const val KEY_HIDE_RELEASE_DATES = "ui.home.hide_release_dates"
    private const val KEY_HERO_AUTO_SCROLL = "ui.home.hero_auto_scroll"

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
