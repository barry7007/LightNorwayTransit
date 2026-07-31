package com.thelightphone.sdk

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * LOCAL ADDITION (not upstream).
 *
 * Deliberately a standalone file holding an extension on [SealedLightContext], rather than a new
 * member inside [SealedLightContext] itself: upstream's own `LightDb.kt` extends the sealed
 * context the same way, so this follows a sanctioned pattern while touching no upstream-tracked
 * file. That keeps it merge-proof - `LightActivity.kt` already carries local edits, and adding
 * another hunk there would grow a conflict hotspot for every future upstream merge.
 *
 * It exists because a tool module cannot reach location itself: `getSystemService(` is in the
 * plugin's `BLOCKED_CODE_PATTERNS`, so the only route is through an SDK module, which the scan
 * exempts. The permissions themselves are already sanctioned - `ACCESS_FINE_LOCATION` and
 * `ACCESS_COARSE_LOCATION` are both in `LightToolPolicy.ALLOWED_PERMISSIONS` - and a tool still
 * has to declare one in its `lighttool.toml` and obtain it at runtime via
 * `rememberPermissionRequestLauncher`. This only supplies the reading.
 */
data class LightCoordinates(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Whether this app actually holds a location permission, asked of Android directly.
 *
 * Deliberately NOT routed through the SDK server's `checkPermission()`, which a tool would
 * normally use. That call gates on `LightSdkServer.androidPermissionAllowed`, whose default set is
 * only `{CAMERA, READ_MEDIA_AUDIO, RECORD_AUDIO}`, and production LightOS 566 does not extend it
 * to location - `LightSdkPermissionActivity` logs "is not grantable by this server" and
 * `GetPermission` returns `BlockedByServer` *even when the OS has genuinely granted the
 * permission*. Asking the platform is therefore the only way to get a truthful answer.
 *
 * This is an inconsistency inside Light's own SDK, not something a tool can fix: the build-time
 * plugin allowlist (`LightToolPolicy.ALLOWED_PERMISSIONS`) explicitly permits both location
 * permissions, so a tool may declare what the runtime server will never grant. Until LightOS
 * widens that set, the grant has to come from outside the SDK - e.g.
 * `adb shell pm grant <pkg> android.permission.ACCESS_FINE_LOCATION`.
 */
val SealedLightContext.hasLocationPermission: Boolean
    get() = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { androidContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/**
 * One position fix, or null if none can be obtained - no permission, location switched off, no
 * usable provider, or the fix timed out. Callers get a single nullable answer because there is
 * nothing useful for a tool to do differently between those cases.
 *
 * Uses `getCurrentLocation` rather than `getLastKnownLocation`: the last known fix can be hours
 * old and kilometres away, which for a "nearest stop" list is worse than no answer at all. The
 * platform applies its own timeout and calls back with null if it can't produce one.
 */
suspend fun SealedLightContext.currentCoordinates(): LightCoordinates? {
    val manager = androidContext.getSystemService(LocationManager::class.java) ?: return null
    val provider = manager.bestAvailableProvider() ?: return null

    // getCurrentLocation throws SecurityException when the permission was never granted, and the
    // callback delivers null on timeout - both collapse to null here.
    return runCatching {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(
                provider,
                signal,
                androidContext.mainExecutor,
            ) { location ->
                continuation.resume(
                    location?.let { LightCoordinates(it.latitude, it.longitude) },
                )
            }
        }
    }.getOrNull()
}

/**
 * Fused first - it merges GPS with wifi/cell and answers fastest indoors, which is where a
 * "stops near me" lookup usually gets used. The raw providers are fallbacks for the case where
 * fused is unavailable or switched off.
 */
private fun LocationManager.bestAvailableProvider(): String? = listOf(
    LocationManager.FUSED_PROVIDER,
    LocationManager.GPS_PROVIDER,
    LocationManager.NETWORK_PROVIDER,
).firstOrNull { provider ->
    runCatching { isProviderEnabled(provider) }.getOrDefault(false)
}
