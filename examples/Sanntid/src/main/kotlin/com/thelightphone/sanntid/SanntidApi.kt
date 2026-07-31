package com.thelightphone.sanntid

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.text.Charsets.UTF_8

// ── Client identity ────────────────────────────────────────────────────────────
// Entur requires every API consumer to identify itself with an ET-Client-Name header of the form
// "<company>-<application>", and "reserves the right to block unidentified consumers".
//
// CHANGE THIS IF YOU FORK OR REDISTRIBUTE. The default below is shared by everyone running an
// unmodified build, so all of that traffic is rate-limited as a single consumer: one heavy user
// can get every other user throttled, and a block would take the whole default identity with it.
// Your own value costs nothing — there is no registration and no API key.
private const val CLIENT_NAME = "lightnorwaytransit-sanntid"

private const val GEOCODER_URL = "https://api.entur.io/geocoder/v1/autocomplete"
private const val REVERSE_GEOCODER_URL = "https://api.entur.io/geocoder/v1/reverse"
private const val JOURNEY_PLANNER_URL = "https://api.entur.io/journey-planner/v3/graphql"

private const val STOP_PLACE_PREFIX = "NSR:StopPlace:"

private val OSLO_ZONE: ZoneId = ZoneId.of("Europe/Oslo")
private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ── Geocoder response models ────────────────────────────────────────────────────

@Serializable
internal data class GeocoderResponse(
    val features: List<GeocoderFeature> = emptyList(),
)

@Serializable
internal data class GeocoderFeature(
    val properties: GeocoderProperties = GeocoderProperties(),
)

@Serializable
internal data class GeocoderProperties(
    val id: String = "",
    val name: String = "",
    val label: String = "",
    /** Reverse-geocoder only, and in kilometres despite the small values it usually carries. */
    val distance: Double? = null,
)

// ── Journey Planner (GraphQL) request + response models ──────────────────────────

@Serializable
internal data class GraphQlRequest(val query: String)

@Serializable
internal data class DeparturesResponse(val data: DeparturesData? = null)

@Serializable
internal data class DeparturesData(val stopPlace: StopPlaceNode? = null)

@Serializable
internal data class StopPlaceNode(
    val estimatedCalls: List<EstimatedCall> = emptyList(),
)

@Serializable
internal data class EstimatedCall(
    val aimedDepartureTime: String? = null,
    val expectedDepartureTime: String? = null,
    val realtime: Boolean = true,
    val cancellation: Boolean = false,
    val situations: List<Situation> = emptyList(),
    val quay: Quay? = null,
    val destinationDisplay: DestinationDisplay? = null,
    val serviceJourney: ServiceJourney? = null,
)

@Serializable
internal data class Situation(
    val summary: List<MultilingualString> = emptyList(),
)

@Serializable
internal data class MultilingualString(val value: String = "")

@Serializable
internal data class Quay(
    val id: String = "",
    val name: String = "",
)

@Serializable
internal data class DestinationDisplay(val frontText: String = "")

@Serializable
internal data class ServiceJourney(val line: Line? = null)

@Serializable
internal data class Line(
    val publicCode: String? = null,
    val transportMode: String? = null,
)

// ── API client ────────────────────────────────────────────────────────────────

internal class SanntidApi {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        // Without this, OkHttp's 10 s default read timeout applies. Entur's geocoder and
        // journey planner are public endpoints reached over mobile data, where a stalled
        // connection is normal rather than exceptional.
        // Same failure mode the Ask tool hit: once it reaches the UI, a timeout is
        // indistinguishable from a network outage.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            socketTimeoutMillis = 30_000L
            connectTimeoutMillis = 15_000L
        }
    }

    /**
     * Resolves a search string to stop places via the Entur geocoder. [size]
     * controls how many candidates come back — the UI uses 5 for a picker list,
     * where the Pi SMS version only needed the single best match. Only features
     * whose id starts with `NSR:StopPlace:` count as valid stop places.
     */
    suspend fun searchStopPlaces(text: String, size: Int = 5): Result<List<StopPlace>> = runCatching {
        val encoded = URLEncoder.encode(text.trim(), UTF_8.name())
        val response = client.get("$GEOCODER_URL?text=$encoded&layers=venue&size=$size") {
            header("ET-Client-Name", CLIENT_NAME)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Geocoder HTTP ${response.status.value}: ${response.bodyAsText().take(500)}")
        }
        val geo: GeocoderResponse = response.body()
        val stops = geo.features.mapNotNull { feature ->
            val id = feature.properties.id
            val name = feature.properties.name
            if (id.startsWith(STOP_PLACE_PREFIX) && name.isNotBlank()) {
                StopPlace(
                    id = id,
                    name = name,
                    label = feature.properties.label.ifBlank { name },
                )
            } else {
                null
            }
        }
        if (stops.isEmpty()) throw StopPlaceNotFoundException(text.trim())
        stops
    }

    /**
     * Stop places near a coordinate, nearest first. Entur's reverse geocoder already sorts by
     * distance and reports it in `properties.distance` (kilometres), so no client-side maths is
     * needed. Same `layers=venue` + `NSR:StopPlace:` filtering as [searchStopPlaces], since the
     * geocoder also returns addresses and localities that have no departures.
     *
     * An empty result is returned as an empty list rather than an error: standing somewhere with
     * no stop within range is a normal outcome, not a failure.
     */
    suspend fun nearbyStopPlaces(
        latitude: Double,
        longitude: Double,
        size: Int = 6,
    ): Result<List<NearbyStop>> = runCatching {
        val response = client.get(
            "$REVERSE_GEOCODER_URL?point.lat=$latitude&point.lon=$longitude&size=$size&layers=venue",
        ) {
            header("ET-Client-Name", CLIENT_NAME)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                "Reverse geocoder HTTP ${response.status.value}: ${response.bodyAsText().take(500)}",
            )
        }
        val geo: GeocoderResponse = response.body()
        geo.features.mapNotNull { feature ->
            val props = feature.properties
            if (!props.id.startsWith(STOP_PLACE_PREFIX) || props.name.isBlank()) return@mapNotNull null
            NearbyStop(
                stop = StopPlace(
                    id = props.id,
                    name = props.name,
                    label = props.label.ifBlank { props.name },
                ),
                distanceKm = props.distance ?: 0.0,
            )
        }
    }

    /**
     * Fetches departures for a stop place. Queries broadly (150 departures over a
     * 7200s window) and does all filtering/sorting client-side, per spec.
     */
    suspend fun fetchDepartures(stopPlaceId: String): Result<List<Departure>> = runCatching {
        val response = client.post(JOURNEY_PLANNER_URL) {
            header("ET-Client-Name", CLIENT_NAME)
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(departuresQuery(stopPlaceId)))
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Journey Planner HTTP ${response.status.value}: ${response.bodyAsText().take(500)}")
        }
        val parsed: DeparturesResponse = response.body()
        val calls = parsed.data?.stopPlace?.estimatedCalls ?: emptyList()
        val now = Instant.now()
        calls.mapNotNull { it.toDeparture(now) }.sortedBy { it.minutesToDeparture }
    }

    fun close() = client.close()
}

internal class StopPlaceNotFoundException(val query: String) :
    Exception("Fant ikke holdeplass: $query")

// ── EstimatedCall → Departure ─────────────────────────────────────────────────
// All time maths is done on Instants (UTC); the wall clock is derived only for
// display, by converting to Europe/Oslo — never trusting the device's local zone.

private fun EstimatedCall.toDeparture(now: Instant): Departure? {
    val expected = expectedDepartureTime?.let { parseInstant(it) } ?: return null
    val minutes = Duration.between(now, expected).toMinutes()
    if (minutes < 0) return null

    val mode = TransportMode.fromEntur(serviceJourney?.line?.transportMode) ?: return null

    val aimed = aimedDepartureTime?.let { parseInstant(it) }
    val delay = if (aimed != null) Duration.between(aimed, expected).toMinutes() else 0L

    return Departure(
        mode = mode,
        lineCode = serviceJourney?.line?.publicCode?.trim().orEmpty(),
        destination = destinationDisplay?.frontText?.trim().orEmpty(),
        clock = expected.atZone(OSLO_ZONE).format(CLOCK_FORMAT),
        minutesToDeparture = minutes,
        realtime = realtime,
        cancelled = cancellation,
        hasSituations = situations.isNotEmpty(),
        delayMinutes = delay,
    )
}

private fun parseInstant(iso: String): Instant? =
    runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()

private fun departuresQuery(stopPlaceId: String): String = """
{
  stopPlace(id: "$stopPlaceId") {
    estimatedCalls(timeRange: 7200, numberOfDepartures: 150) {
      aimedDepartureTime
      expectedDepartureTime
      realtime
      cancellation
      situations { summary { value } }
      quay { id name }
      destinationDisplay { frontText }
      serviceJourney {
        line { publicCode transportMode }
      }
    }
  }
}
""".trimIndent()
