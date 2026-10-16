package com.nuvio.app.features.details

import com.nuvio.app.core.avix.AvixSettingsBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritePeopleRepositoryTest {
    private fun person(id: Int, addedAt: Long, name: String = "Person $id") =
        FavoritePerson(tmdbId = id, name = name, photo = "https://img/$id.jpg", knownFor = "Acting", addedAtEpochMs = addedAt)

    @Test
    fun toggleAddsNewestFirstAndRemovesWithItsPin() {
        var payload = FavoritePeoplePayload()
            .toggle(person(1, addedAt = 10))
            .toggle(person(2, addedAt = 20))
            .toggle(person(3, addedAt = 30))
        assertEquals(listOf(3, 2, 1), payload.people.map { it.tmdbId })

        payload = payload.togglePinned(1)
        assertTrue(payload.isFavorite(1))
        payload = payload.toggle(person(1, addedAt = 99))
        assertFalse(payload.isFavorite(1))
        assertEquals(emptyList(), payload.pinnedPersonIds)
        assertEquals(listOf(3, 2), payload.people.map { it.tmdbId })
    }

    @Test
    fun pinnedPeopleComeFirstInPinOrder() {
        var payload = FavoritePeoplePayload(people = listOf(person(1, 10), person(2, 20), person(3, 30), person(4, 40)))
            .normalized()
        payload = payload.togglePinned(1).togglePinned(3)
        assertEquals(listOf(3, 1), payload.pinnedPersonIds)
        assertEquals(listOf(3, 1, 4, 2), payload.toUiState().displayPeople.map { it.tmdbId })

        payload = payload.togglePinned(3)
        assertEquals(listOf(1), payload.pinnedPersonIds)
        assertEquals(listOf(1, 4, 3, 2), payload.toUiState().displayPeople.map { it.tmdbId })

        // Pinning someone who is not a favorite is ignored.
        assertEquals(payload, payload.togglePinned(42))
    }

    @Test
    fun removeDropsPersonAndPin() {
        val payload = FavoritePeoplePayload(people = listOf(person(1, 10), person(2, 20)))
            .normalized()
            .togglePinned(2)
            .remove(2)
        assertEquals(listOf(1), payload.people.map { it.tmdbId })
        assertEquals(emptyList(), payload.pinnedPersonIds)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val payload = FavoritePeoplePayload()
            .toggle(person(1, 10))
            .toggle(person(2, 20).copy(photo = null, knownFor = null))
            .togglePinned(1)
        assertEquals(payload, FavoritePeoplePayload.decode(FavoritePeoplePayload.encode(payload)))
    }

    @Test
    fun decodeIsLenient() {
        assertEquals(FavoritePeoplePayload(), FavoritePeoplePayload.decode(null))
        assertEquals(FavoritePeoplePayload(), FavoritePeoplePayload.decode(""))
        assertEquals(FavoritePeoplePayload(), FavoritePeoplePayload.decode("not json"))
        assertEquals(FavoritePeoplePayload(), FavoritePeoplePayload.decode("[1,2,3]"))
        assertEquals(FavoritePeoplePayload(), FavoritePeoplePayload.decode("""{"people":"oops"}"""))

        val decoded = FavoritePeoplePayload.decode(
            """
            {"future":true,
             "people":[
               {"tmdbId":1,"name":"Ana","addedAtEpochMs":10},
               {"tmdbId":2,"name":"   ","addedAtEpochMs":20},
               {"tmdbId":0,"name":"Zero","addedAtEpochMs":30},
               {"name":"No id","addedAtEpochMs":40},
               "garbage",
               {"tmdbId":3,"name":"Bo","addedAtEpochMs":50,"extra":"x"},
               {"tmdbId":1,"name":"Ana (older dup)","addedAtEpochMs":5}
             ],
             "pinnedPersonIds":[1,1,2,99,"3",3]}
            """.trimIndent(),
        )
        assertEquals(listOf(3, 1), decoded.people.map { it.tmdbId })
        assertEquals("Ana", decoded.people.last().name)
        assertEquals(listOf(1, 3), decoded.pinnedPersonIds)
    }

    @Test
    fun storageKeysArePerProfileAndIncludedInLocalBackup() {
        assertEquals("favorites.people.1", FavoritePeopleRepository.storageKey(1))
        assertEquals("favorites.people.3", FavoritePeopleRepository.storageKey(3))
        val key = FavoritePeopleRepository.storageKey(2)
        assertTrue(AvixSettingsBackup.isAllowedKey(key))

        // A long favorites list must survive a backup round trip.
        val many = (1..200).fold(FavoritePeoplePayload()) { acc, id -> acc.toggle(person(id, id.toLong())) }
        val value = FavoritePeoplePayload.encode(many)
        assertTrue(value.length > 4_096)
        val restored = AvixSettingsBackup.decode(AvixSettingsBackup.encode(mapOf(key to value), nowEpochMs = 1L))
        assertEquals(many, FavoritePeoplePayload.decode(restored[key]))
    }
}
