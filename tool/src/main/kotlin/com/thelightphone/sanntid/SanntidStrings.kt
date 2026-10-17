package com.thelightphone.sanntid

/**
 * Every user-facing string in the app, in one place per language.
 *
 * Deliberately plain Kotlin objects rather than Android string resources. Resource lookup follows
 * the *system* locale, and overriding that in-app is normally done with
 * `AppCompatDelegate.setApplicationLocales` — `androidx.appcompat` is a blocked import for tool
 * modules, so that route is closed. Selecting the language ourselves is both simpler and the only
 * thing that actually works inside the sandbox.
 *
 * Norwegian is the default: the app only covers Norway (Entur), so every user is here.
 */
enum class AppLanguage(val code: String) {
    NB("nb"),
    EN("en"),
    ;

    /** Label shown in the language row — always in its own language, never translated. */
    val ownLabel: String get() = when (this) {
        NB -> "Norsk"
        EN -> "English"
    }

    companion object {
        val Default: AppLanguage = NB

        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: Default
    }
}

fun stringsFor(language: AppLanguage): SanntidStrings = when (language) {
    AppLanguage.NB -> NbStrings
    AppLanguage.EN -> EnStrings
}

interface SanntidStrings {
    val appTitle: String
    val loading: String
    val searchStopTitle: String
    val changePlace: String
    val changePlaceBar: String
    val favouritesHeading: String
    val findStopHeading: String
    val nearMe: String
    val settingsHeading: String
    val language: String
    val version: String
    val data: String
    val noDepartures: String
    val emptySearch: String
    val refresh: String
    val backToSearch: String
    val backToDepartures: String
    val back: String
    val networkError: String
    val locationPermission: String
    val locationUnavailable: String
    val noStopsNearby: String
    val cancelled: String
    val now: String

    fun stopNotFound(query: String): String
    fun addFavourite(stop: String): String
    fun removeFavourite(stop: String): String
    fun minutes(count: Long): String
    fun delaySuffix(minutes: Long): String
    fun modeLabel(mode: TransportMode): String

    /** "120 m" below a kilometre, "1,4 km" above — decimal separator follows the language. */
    fun distance(distanceKm: Double): String
}

private const val PACKAGE_NAME = "com.sanntid.lp3"

internal object NbStrings : SanntidStrings {
    override val appTitle = "Sanntid"
    override val loading = "Laster…"
    override val searchStopTitle = "Søk holdeplass"
    override val changePlace = "Bytt sted"
    override val changePlaceBar = "BYTT STED"
    override val favouritesHeading = "FAVORITTER"
    override val findStopHeading = "FINN HOLDEPLASS"
    override val nearMe = "Nær meg"
    override val settingsHeading = "INNSTILLINGER"
    override val language = "Språk"
    override val version = "Versjon"
    override val data = "Data"
    override val noDepartures = "Ingen avganger nå."
    override val emptySearch = "Skriv inn et stoppested."
    override val refresh = "Oppdater"
    override val backToSearch = "Tilbake til søk"
    override val backToDepartures = "Tilbake til avganger"
    override val back = "Tilbake"
    override val networkError =
        "Sanntid trenger nettforbindelse. Koble til wi-fi eller sett inn et data-SIM for å hente avganger."
    override val locationPermission =
        if (LOCATION_NEEDS_ADB) {
            "Sanntid mangler tilgang til posisjon. LightOS gir ikke denne tilgangen til app-er selv; " +
                "den må gis via adb:\npm grant $PACKAGE_NAME android.permission.ACCESS_FINE_LOCATION"
        } else {
            "Sanntid trenger tilgang til posisjon. Gi tilgang, og trykk Nær meg igjen."
        }
    override val locationUnavailable =
        "Fant ikke posisjonen din. Sjekk at posisjon er slått på, og prøv igjen."
    override val noStopsNearby = "Fant ingen holdeplasser nær deg."
    override val cancelled = "avlyst"
    override val now = "nå"

    override fun stopNotFound(query: String) = "Fant ikke holdeplass: $query"
    override fun addFavourite(stop: String) = "Legg $stop til favoritter"
    override fun removeFavourite(stop: String) = "Fjern $stop fra favoritter"
    override fun minutes(count: Long) = if (count == 1L) "1 min" else "$count min"
    override fun delaySuffix(minutes: Long) = "+$minutes min forsinket"

    override fun modeLabel(mode: TransportMode) = when (mode) {
        TransportMode.Metro -> "T-bane"
        TransportMode.Tram -> "Trikk"
        TransportMode.Bus -> "Buss"
        TransportMode.Coach -> "Ekspressbuss"
        TransportMode.Rail -> "Tog"
        TransportMode.Ferry -> "Ferge"
    }

    override fun distance(distanceKm: Double) = if (distanceKm < 1.0) {
        "${(distanceKm * 1000).toInt()} m"
    } else {
        "%.1f km".format(distanceKm).replace('.', ',')
    }
}

internal object EnStrings : SanntidStrings {
    override val appTitle = "Sanntid"
    override val loading = "Loading…"
    override val searchStopTitle = "Search stop"
    override val changePlace = "Change stop"
    override val changePlaceBar = "CHANGE STOP"
    override val favouritesHeading = "FAVOURITES"
    override val findStopHeading = "FIND A STOP"
    override val nearMe = "Near me"
    override val settingsHeading = "SETTINGS"
    override val language = "Language"
    override val version = "Version"
    override val data = "Data"
    override val noDepartures = "No departures right now."
    override val emptySearch = "Enter a stop name."
    override val refresh = "Refresh"
    override val backToSearch = "Back to search"
    override val backToDepartures = "Back to departures"
    override val back = "Back"
    override val networkError =
        "Sanntid needs a network connection. Join wi-fi or insert a data SIM to fetch departures."
    override val locationPermission =
        if (LOCATION_NEEDS_ADB) {
            "Sanntid has no location permission. LightOS does not grant this to apps itself; " +
                "it has to be given over adb:\npm grant $PACKAGE_NAME android.permission.ACCESS_FINE_LOCATION"
        } else {
            "Sanntid needs location access. Allow it, then tap Near me again."
        }
    override val locationUnavailable =
        "Couldn't get your position. Check that location is switched on, and try again."
    override val noStopsNearby = "No stops found near you."
    override val cancelled = "cancelled"
    override val now = "now"

    override fun stopNotFound(query: String) = "No stop found: $query"
    override fun addFavourite(stop: String) = "Add $stop to favourites"
    override fun removeFavourite(stop: String) = "Remove $stop from favourites"
    override fun minutes(count: Long) = if (count == 1L) "1 min" else "$count min"
    override fun delaySuffix(minutes: Long) = "+$minutes min late"

    // Stop and destination names come from Entur and stay Norwegian whichever language is set —
    // translating a mode label is useful, "inventing" a Norwegian place name in English is not.
    override fun modeLabel(mode: TransportMode) = when (mode) {
        TransportMode.Metro -> "Metro"
        TransportMode.Tram -> "Tram"
        TransportMode.Bus -> "Bus"
        TransportMode.Coach -> "Coach"
        TransportMode.Rail -> "Train"
        TransportMode.Ferry -> "Ferry"
    }

    override fun distance(distanceKm: Double) = if (distanceKm < 1.0) {
        "${(distanceKm * 1000).toInt()} m"
    } else {
        "%.1f km".format(distanceKm)
    }
}
