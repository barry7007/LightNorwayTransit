# Sanntid

**Departure times for public transport anywhere in Norway, on the Light Phone III.**

*Sanntid* is the word on the departure boards — "real time". The app opens straight onto the stop
you used last, so checking the next tram is a glance rather than a task. Star the stops you use and
they sit at the top of the chooser.

Built with the [Light SDK](https://github.com/lightphone/light-sdk) over
[Entur's](https://developer.entur.org) national APIs, so it covers every stop in Norway — bus,
tram, metro, train, coach and ferry — not just Oslo.

| Departures | Change stop | Near me |
|---|---|---|
| ![Departures](docs/screenshots/departures.png) | ![Change stop](docs/screenshots/change-stop.png) | ![Near me](docs/screenshots/near-me.png) |

## What it does

- **Opens on your last stop.** Departures is the home screen — no navigation to reach the thing you
  opened the app for.
- **Favourite stops.** The star on the departures bar adds or removes the stop you are looking at;
  favourites appear first under **BYTT STED**.
- **Nær meg** lists the nearest stops with distances so you can pick — see the caveat below.
- **Search any stop in Norway**, with the place shown ("Torshov, Oslo" vs "Torshov, Hamar") so
  same-named stops can be told apart.
- **Realtime**: delays (`+3 min forsinket`), cancellations, and `⚠` for service messages.
  Timetabled-only departures are prefixed `~`.
- **Norwegian or English**, switchable in-app under **BYTT STED → INNSTILLINGER → Språk**.
  Norwegian is the default. Stop and destination names come from Entur and stay Norwegian in both —
  translating a place name helps nobody.

## Install

Grab the APK from [Releases](../../releases) and sideload it:

```sh
adb install -r Sanntid-release.apk
```

It appears in the LightOS menu as **Sanntid**. If it does not show up straight away, restart the
phone — the launcher builds its app list at boot.

## Nær meg needs one adb command

**LightOS does not currently grant location to a Light SDK tool at all**, so the nearby-stops
feature needs the permission granted from outside the app, once:

```sh
adb shell pm grant com.thelightphone.sanntid android.permission.ACCESS_FINE_LOCATION
```

This survives app updates. **Everything except Nær meg works without it** — favourites, search and
departures are unaffected.

This is not a limitation of the app. The SDK's build-time policy explicitly permits both location
permissions, but the runtime permission server's grantable set is camera and audio only, so its
prompt refuses with `is not grantable by this server`. Its `GetPermission` call also reports
`BlockedByServer` *even once the OS has genuinely granted the permission*, which is why the app asks
the platform directly instead. If a future LightOS widens that set, Nær meg starts working with no
change here.

**Why there is no automatic fallback:** IP-based geolocation was measured and rejected. On both home
broadband and Norwegian mobile data the providers returned an identical Oslo city centroid about
2.8 km from the real position — six city-centre stops with confident-looking metre distances and no
overlap with the actual nearest ones. A wrong answer that looks precise is worse than no answer, so
when location is unavailable the app falls back to what it already does well: your favourites and
search.

## Building

Requires **JDK 17** and a GitHub token with `read:packages`, since the SDK's libraries are hosted on
GitHub Packages — see [the upstream SDK README](docs/LIGHT_SDK_README.md) for that setup.

```sh
./gradlew :examples:Sanntid:assembleDebug
```

If your default JDK is newer than 17, point Gradle at a 17 install rather than committing a path:

```sh
./gradlew -Dorg.gradle.java.home=/path/to/jdk-17 :examples:Sanntid:assembleDebug
```

## If you fork this: change the Entur client name

Entur asks every API consumer to identify itself with an `ET-Client-Name` header of the form
`<company>-<application>`, and **reserves the right to block unidentified consumers**. There is no
registration and no API key — you just pick a name.

`CLIENT_NAME` in
[`SanntidApi.kt`](examples/Sanntid/src/main/kotlin/com/thelightphone/sanntid/SanntidApi.kt) defaults
to `lightnorwaytransit-sanntid`, which is shared by everyone running an unmodified build. All that
traffic is rate-limited as a single consumer, so one heavy user can get every other user throttled —
and a block would take the whole default identity with it. **Set your own before redistributing.**

## Other countries

Norway only, because it is built on Entur. Every network call lives in one file,
[`SanntidApi.kt`](examples/Sanntid/src/main/kotlin/com/thelightphone/sanntid/SanntidApi.kt), behind
three functions — `searchStopPlaces`, `nearbyStopPlaces` and `fetchDepartures`. Replacing those with
another country's API is the whole job; nothing else in the app knows where the data came from.

PRs welcome if you do. There is deliberately no configurable "add your own transit API" setting:
transit APIs differ far too much for a generic adapter to earn its indirection.

## Repository layout

This is a fork of [`lightphone/light-sdk`](https://github.com/lightphone/light-sdk), which is how
Light SDK tools are distributed — the app cannot be built outside the SDK tree. Two things differ
from upstream:

- **`examples/Sanntid/`** — the app.
- **`sdk/client/src/main/kotlin/com/thelightphone/sdk/LightLocation.kt`** — a small addition giving
  tools a way to read a position at all. `getSystemService()` is a blocked API in tool modules, so
  this has to live in an SDK module. It is a standalone file extending `SealedLightContext`,
  mirroring upstream's own `LightDb.kt`, so it adds no merge-conflict surface when tracking
  upstream.

Everything else is upstream, unmodified.

## Credits and licence

- Departure and stop data from [Entur](https://developer.entur.org), licensed
  [NLOD](https://data.norge.no/nlod/en/2.0).
- Built on the [Light SDK](https://github.com/lightphone/light-sdk) by The Light Phone.
- MIT, matching upstream. See [LICENSE](LICENSE).

Not affiliated with The Light Phone, Entur, or Ruter.
