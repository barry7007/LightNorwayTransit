package com.thelightphone.sanntid

/** Flag appended to a departure that has active `situations` (avvik). */
const val SITUATION_FLAG = "⚠"

/**
 * The time cell for a departure: the cancellation notice when cancelled, otherwise
 * the Oslo clock — prefixed with `~` when the time is timetabled only (not realtime).
 */
fun formatDepartureTime(departure: Departure, strings: SanntidStrings): String = when {
    departure.cancelled -> strings.cancelled
    !departure.realtime -> "~${departure.clock}"
    else -> departure.clock
}

/** Human "minutes until" label; blank for cancelled departures. */
fun formatMinutes(departure: Departure, strings: SanntidStrings): String = when {
    departure.cancelled -> ""
    departure.minutesToDeparture <= 0L -> strings.now
    else -> strings.minutes(departure.minutesToDeparture)
}

/** Delay suffix (e.g. "+3 min forsinket") when a realtime departure runs late. */
fun formatDelay(departure: Departure, strings: SanntidStrings): String? =
    if (!departure.cancelled && departure.realtime && departure.delayMinutes > 0L) {
        strings.delaySuffix(departure.delayMinutes)
    } else {
        null
    }

/** Line + full (untruncated) destination, e.g. "31 Tonsenhagen". */
fun formatLineDestination(departure: Departure): String {
    val code = departure.lineCode.trim()
    val destination = departure.destination.trim().ifBlank { "—" }
    return if (code.isBlank()) destination else "$code  $destination"
}

/**
 * The secondary line under each departure: time · minutes · delay · avviksflagg,
 * omitting empty parts.
 */
fun formatDepartureDetail(departure: Departure, strings: SanntidStrings): String {
    val parts = buildList {
        add(formatDepartureTime(departure, strings))
        formatMinutes(departure, strings).takeIf { it.isNotBlank() }?.let { add(it) }
        formatDelay(departure, strings)?.let { add(it) }
        if (departure.hasSituations) add(SITUATION_FLAG)
    }
    return parts.joinToString("  ·  ")
}
