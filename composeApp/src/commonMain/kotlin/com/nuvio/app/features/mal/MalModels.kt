// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import io.ktor.http.formUrlEncode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val malJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
}

/** MyAnimeList `my_list_status.status` values. */
object MalListStatus {
    const val WATCHING = "watching"
    const val COMPLETED = "completed"
    const val ON_HOLD = "on_hold"
    const val DROPPED = "dropped"
    const val PLAN_TO_WATCH = "plan_to_watch"
}

data class MalAuthScope(val profileId: Int, val generation: Long)

enum class MalAuthError {
    MISSING_CLIENT_ID,
    ACCESS_DENIED,
    INVALID_CALLBACK,
    TOKEN_EXCHANGE_FAILED,
    AUTHORIZATION_EXPIRED,
    AUTHORIZATION_REVOKED,
}

@Serializable
data class MalViewer(
    val id: Long? = null,
    val name: String? = null,
)

data class MalAuthState(
    val scope: MalAuthScope = MalAuthScope(1, 0),
    val isAuthenticated: Boolean = false,
    val viewer: MalViewer? = null,
    val awaitingApproval: Boolean = false,
    val isBusy: Boolean = false,
    val error: MalAuthError? = null,
)

@Serializable
internal data class MalStoredAuth(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtEpochMs: Long? = null,
    val viewer: MalViewer? = null,
    val pendingState: String? = null,
    val pendingVerifier: String? = null,
    val pendingSinceEpochMs: Long? = null,
) {
    override fun toString(): String = "MalStoredAuth()"
}

internal data class MalCredentials(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long?,
) {
    override fun toString(): String = "MalCredentials()"
}

internal data class MalPendingAuthorization(
    val state: String,
    val verifier: String,
) {
    override fun toString(): String = "MalPendingAuthorization()"
}

@Serializable
internal data class MalTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
) {
    override fun toString(): String = "MalTokenResponse()"
}

@Serializable
internal data class MalMyListStatus(
    val status: String? = null,
    @SerialName("num_episodes_watched") val numEpisodesWatched: Int = 0,
    @SerialName("is_rewatching") val isRewatching: Boolean = false,
    val score: Int = 0,
)

@Serializable
internal data class MalAnime(
    val id: Long,
    val title: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int = 0,
    @SerialName("my_list_status") val myListStatus: MalMyListStatus? = null,
)

/** A progress write for `PATCH /anime/{id}/my_list_status`. A null [status] leaves the list status unchanged. */
internal data class MalProgressUpdate(
    val numWatchedEpisodes: Int,
    val status: String?,
)

class MalAuthException(val error: MalAuthError) : Exception(error.name)

open class MalApiException(
    val status: Int,
    message: String? = null,
) : Exception(message ?: "MyAnimeList request failed ($status)")

internal fun parseMalAnime(body: String): MalAnime? =
    runCatching { malJson.decodeFromString(MalAnime.serializer(), body) }.getOrNull()

internal fun parseMalMyListStatus(body: String): MalMyListStatus? =
    runCatching { malJson.decodeFromString(MalMyListStatus.serializer(), body) }.getOrNull()

internal fun parseMalViewer(body: String): MalViewer? =
    runCatching { malJson.decodeFromString(MalViewer.serializer(), body) }.getOrNull()

internal fun parseMalTokenResponse(body: String): MalTokenResponse? =
    runCatching { malJson.decodeFromString(MalTokenResponse.serializer(), body) }.getOrNull()
        ?.takeIf { !it.accessToken.isNullOrBlank() }

/** `completed` once the watched count reaches a known episode total, otherwise `watching`. */
internal fun malStatusForProgress(numWatchedEpisodes: Int, numEpisodes: Int): String =
    if (numEpisodes > 0 && numWatchedEpisodes >= numEpisodes) MalListStatus.COMPLETED else MalListStatus.WATCHING

/**
 * Decides the write for a watched [episode]. Returns null when MyAnimeList already has the same or a
 * higher `num_episodes_watched`, so tracking never lowers progress.
 */
internal fun malProgressUpdateFor(anime: MalAnime, episode: Int): MalProgressUpdate? {
    if (episode <= 0) return null
    val current = anime.myListStatus?.numEpisodesWatched ?: 0
    if (anime.myListStatus != null && current >= episode) return null
    val total = anime.numEpisodes
    val progress = if (total > 0 && episode > total) total else episode
    if (anime.myListStatus != null && current >= progress) return null
    return MalProgressUpdate(progress, malStatusForProgress(progress, total))
}

/** Mirrors AniList's history removal: rolls progress back to before [episode] when it is already counted. */
internal fun malProgressRemovalFor(anime: MalAnime, episode: Int): MalProgressUpdate? {
    val current = anime.myListStatus ?: return null
    if (episode <= 0 || current.numEpisodesWatched < episode) return null
    val next = (episode - 1).coerceAtLeast(0)
    val status = when {
        current.status == MalListStatus.COMPLETED && next > 0 -> MalListStatus.WATCHING
        current.status == MalListStatus.COMPLETED -> MalListStatus.PLAN_TO_WATCH
        else -> null
    }
    return MalProgressUpdate(next, status)
}

/** Form body for the list-status PATCH. Note writes use `num_watched_episodes` while reads use `num_episodes_watched`. */
internal fun buildMalProgressForm(update: MalProgressUpdate): String = buildList {
    add("num_watched_episodes" to update.numWatchedEpisodes.coerceAtLeast(0).toString())
    update.status?.let { add("status" to it) }
}.formUrlEncode()

internal fun buildMalTokenExchangeForm(
    clientId: String,
    code: String,
    verifier: String,
    redirectUri: String,
): String = listOf(
    "client_id" to clientId,
    "code" to code,
    "code_verifier" to verifier,
    "grant_type" to "authorization_code",
    "redirect_uri" to redirectUri,
).formUrlEncode()

internal fun buildMalRefreshForm(clientId: String, refreshToken: String): String = listOf(
    "client_id" to clientId,
    "grant_type" to "refresh_token",
    "refresh_token" to refreshToken,
).formUrlEncode()
