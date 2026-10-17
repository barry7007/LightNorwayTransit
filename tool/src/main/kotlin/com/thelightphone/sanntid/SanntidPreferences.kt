package com.thelightphone.sanntid

import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal object SanntidPreferences {
    /** Last searched/selected stop place, so the app jumps straight to departures. */
    val STOP_ID = stringPreferencesKey("stop_id")
    val STOP_NAME = stringPreferencesKey("stop_name")

    /**
     * Favourite stops as a JSON array. Stored as one string rather than a preferences string-set
     * because the display order is the order they were added, and a Set would lose it.
     */
    val FAVOURITES = stringPreferencesKey("favourites")

    /** [AppLanguage.code]; absent means [AppLanguage.Default]. */
    val LANGUAGE = stringPreferencesKey("language")
}

@Serializable
internal data class StoredFavourite(
    val id: String,
    val name: String,
    val label: String = name,
)

private val favouritesJson = Json { ignoreUnknownKeys = true }
private val favouritesSerializer = ListSerializer(StoredFavourite.serializer())

/** Never throws: a corrupt or half-written value degrades to "no favourites", which the picker
 *  renders as an empty section, rather than taking the whole screen down on launch. */
internal fun decodeFavourites(raw: String?): List<StopPlace> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        favouritesJson.decodeFromString(favouritesSerializer, raw)
            .map { StopPlace(id = it.id, name = it.name, label = it.label) }
    }.getOrDefault(emptyList())
}

internal fun encodeFavourites(stops: List<StopPlace>): String =
    favouritesJson.encodeToString(
        favouritesSerializer,
        stops.map { StoredFavourite(id = it.id, name = it.name, label = it.label) },
    )
