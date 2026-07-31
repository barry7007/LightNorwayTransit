package com.thelightphone.sanntid

/**
 * A resolved Entur stop place: a validated `NSR:StopPlace:*` id, a short [name]
 * ("Torshov") used for the departures header, and a fuller [label]
 * ("Torshov, Oslo") used in the search picker to tell same-named places apart.
 */
data class StopPlace(
    val id: String,
    val name: String,
    val label: String = name,
)

/**
 * A [StopPlace] paired with how far away it is, as returned by the reverse geocoder. Kept
 * separate from [StopPlace] rather than added as a nullable field so that a stop is still equal
 * to itself whether it arrived from a search, a favourite or a nearby lookup - favourites are
 * matched by identity in several places, and a distance riding along would silently break that.
 */
data class NearbyStop(
    val stop: StopPlace,
    val distanceKm: Double,
) {
    /** Formatted for display; the decimal separator follows the selected language. */
    fun distanceLabel(strings: SanntidStrings): String = strings.distance(distanceKm)
}

/**
 * Transport modes we group departures by, declared in the fixed display order
 * required by the spec (metro → tram → bus → coach → rail → ferry). [enturCode]
 * is the raw `transportMode` string from Entur that maps to each bucket; per
 * Entur's API, ferries are always reported as `water`, never `ferry`.
 */
enum class TransportMode(val enturCode: String) {
    Metro("metro"),
    Tram("tram"),
    Bus("bus"),
    Coach("coach"),
    Rail("rail"),
    Ferry("water"),
    ;

    companion object {
        fun fromEntur(code: String?): TransportMode? {
            val normalized = code?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.enturCode == normalized }
        }
    }
}

/** A single processed departure, ready for display. */
data class Departure(
    val mode: TransportMode,
    val lineCode: String,
    val destination: String,
    /** Departure clock in Europe/Oslo, HH:MM. */
    val clock: String,
    /** Whole minutes from "now" (computed at fetch time); always >= 0. */
    val minutesToDeparture: Long,
    /** false → aimed/timetabled only, shown with a ~ prefix. */
    val realtime: Boolean,
    val cancelled: Boolean,
    val hasSituations: Boolean,
    /** expected − aimed, in whole minutes (0 when aimed is unknown). */
    val delayMinutes: Long,
)

/** Departures for one [TransportMode], already sorted and capped. */
data class ModeGroup(
    val mode: TransportMode,
    val departures: List<Departure>,
)

/** Max departures shown per mode — the screen scrolls, so we can show more than SMS. */
const val MAX_DEPARTURES_PER_MODE = 8

/**
 * Buckets departures into [ModeGroup]s in the fixed [TransportMode] order, each
 * sorted by time and capped at [maxPerMode]. Empty modes are dropped.
 */
fun List<Departure>.groupedByMode(maxPerMode: Int = MAX_DEPARTURES_PER_MODE): List<ModeGroup> =
    TransportMode.entries.mapNotNull { mode ->
        val deps = filter { it.mode == mode }
            .sortedBy { it.minutesToDeparture }
            .take(maxPerMode)
        if (deps.isEmpty()) null else ModeGroup(mode, deps)
    }
