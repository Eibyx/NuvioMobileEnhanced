package com.nuvio.app.features.details

import com.nuvio.app.core.avix.AvixLocalStore
import com.nuvio.app.core.avix.AvixPreferencesReload
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Clock

/** Avix (ported from Yesnt10): a person the active profile marked as favorite. */
@Serializable
data class FavoritePerson(
    val tmdbId: Int,
    val name: String,
    val photo: String? = null,
    val knownFor: String? = null,
    val addedAtEpochMs: Long,
)

data class FavoritePeopleUiState(
    /** Newest first. */
    val people: List<FavoritePerson> = emptyList(),
    /** Most recently pinned first; always a subset of [people]. */
    val pinnedPersonIds: List<Int> = emptyList(),
    val isLoaded: Boolean = false,
) {
    /** Pinned people first (in pin order), then the rest newest first. */
    val displayPeople: List<FavoritePerson>
        get() {
            if (people.isEmpty() || pinnedPersonIds.isEmpty()) return people
            val peopleById = people.associateBy(FavoritePerson::tmdbId)
            val pinned = pinnedPersonIds.mapNotNull(peopleById::get)
            val pinnedIds = pinned.mapTo(mutableSetOf(), FavoritePerson::tmdbId)
            return pinned + people.filterNot { it.tmdbId in pinnedIds }
        }
}

/** Pure, normalized favorites state (what is stored per profile). */
@Serializable
internal data class FavoritePeoplePayload(
    val people: List<FavoritePerson> = emptyList(),
    val pinnedPersonIds: List<Int> = emptyList(),
) {
    fun isFavorite(tmdbId: Int): Boolean = people.any { it.tmdbId == tmdbId }

    /** Adds [person] (as newest) or removes it (and its pin) if already a favorite. */
    fun toggle(person: FavoritePerson): FavoritePeoplePayload =
        if (isFavorite(person.tmdbId)) remove(person.tmdbId) else copy(people = listOf(person) + people).normalized()

    fun remove(tmdbId: Int): FavoritePeoplePayload =
        copy(
            people = people.filterNot { it.tmdbId == tmdbId },
            pinnedPersonIds = pinnedPersonIds.filterNot { it == tmdbId },
        )

    /** Pins (to the front) or unpins a favorite; ignores ids that are not favorites. */
    fun togglePinned(tmdbId: Int): FavoritePeoplePayload {
        if (!isFavorite(tmdbId)) return this
        val pinned = if (tmdbId in pinnedPersonIds) {
            pinnedPersonIds.filterNot { it == tmdbId }
        } else {
            listOf(tmdbId) + pinnedPersonIds
        }
        return copy(pinnedPersonIds = pinned).normalized()
    }

    /** Drops invalid/duplicate people, sorts newest first and keeps only valid, distinct pins. */
    fun normalized(): FavoritePeoplePayload {
        val validPeople = people
            .filter { it.tmdbId > 0 && it.name.isNotBlank() }
            .sortedByDescending(FavoritePerson::addedAtEpochMs)
            .distinctBy(FavoritePerson::tmdbId)
        val ids = validPeople.mapTo(mutableSetOf(), FavoritePerson::tmdbId)
        return FavoritePeoplePayload(
            people = validPeople,
            pinnedPersonIds = pinnedPersonIds.distinct().filter { it in ids },
        )
    }

    fun toUiState(): FavoritePeopleUiState =
        FavoritePeopleUiState(people = people, pinnedPersonIds = pinnedPersonIds, isLoaded = true)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

        fun encode(payload: FavoritePeoplePayload): String =
            json.encodeToString(serializer(), payload.normalized())

        /**
         * Lenient decode: malformed JSON yields an empty payload, and a malformed entry only
         * drops that entry instead of the whole list.
         */
        fun decode(text: String?): FavoritePeoplePayload {
            val root = text?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return FavoritePeoplePayload()
            val people = (root["people"] as? JsonArray).orEmpty().mapNotNull { element ->
                runCatching { json.decodeFromJsonElement(FavoritePerson.serializer(), element) }.getOrNull()
            }
            val pinned = (root["pinnedPersonIds"] as? JsonArray).orEmpty().mapNotNull { element ->
                (element as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            }
            return FavoritePeoplePayload(people = people, pinnedPersonIds = pinned).normalized()
        }
    }
}

internal fun PersonDetail.toFavoritePerson(addedAtEpochMs: Long): FavoritePerson =
    FavoritePerson(
        tmdbId = tmdbId,
        name = name.trim(),
        photo = profilePhoto?.trim()?.takeIf { it.isNotBlank() },
        knownFor = knownFor?.trim()?.takeIf { it.isNotBlank() },
        addedAtEpochMs = addedAtEpochMs,
    )

/**
 * Avix (ported from Yesnt10): favorite people per profile.
 *
 * Device-local (docs/integration/DECISIONS.md D-5): stored as JSON in [AvixLocalStore] under
 * `favorites.people.<profileId>`, never in the synced Nuvio profile settings blob. Included in
 * the local Avix settings backup.
 */
object FavoritePeopleRepository {
    internal const val KEY_PREFIX = "favorites.people."

    internal fun storageKey(profileId: Int): String = "$KEY_PREFIX$profileId"

    private val _uiState = MutableStateFlow(FavoritePeopleUiState())
    val uiState: StateFlow<FavoritePeopleUiState> = _uiState.asStateFlow()

    private var currentProfileId: Int? = null
    private var payload = FavoritePeoplePayload()

    init {
        AvixPreferencesReload.register {
            currentProfileId?.let(::load)
        }
    }

    /** Loads the active profile's favorites (again if the active profile changed). */
    fun ensureLoaded() {
        val activeProfileId = ProfileRepository.activeProfileId
        if (currentProfileId == activeProfileId) return
        load(activeProfileId)
    }

    fun onProfileChanged(profileId: Int) {
        load(profileId)
    }

    fun isFavorite(tmdbId: Int): Boolean {
        ensureLoaded()
        return payload.isFavorite(tmdbId)
    }

    fun toggle(person: PersonDetail) =
        update { it.toggle(person.toFavoritePerson(Clock.System.now().toEpochMilliseconds())) }

    fun remove(tmdbId: Int) = update { it.remove(tmdbId) }

    fun togglePinned(tmdbId: Int) = update { it.togglePinned(tmdbId) }

    private fun update(transform: (FavoritePeoplePayload) -> FavoritePeoplePayload) {
        ensureLoaded()
        val profileId = currentProfileId ?: return
        val next = transform(payload)
        if (next == payload) return
        payload = next
        _uiState.value = next.toUiState()
        AvixLocalStore.putString(
            storageKey(profileId),
            next.takeIf { it.people.isNotEmpty() }?.let(FavoritePeoplePayload::encode),
        )
    }

    private fun load(profileId: Int) {
        currentProfileId = profileId
        payload = FavoritePeoplePayload.decode(AvixLocalStore.getString(storageKey(profileId)))
        _uiState.value = payload.toUiState()
    }
}
