package com.thelightphone.sanntid

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.thelightphone.sdk.PermissionRequestLauncher
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.getOrNull
import com.thelightphone.sdk.ui.LightBottomBarItem
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Tool Library build. Everything that differs from the sideloaded build (the `phone` branch)
// lives in this file, so the two branches differ by this file alone.

data class SanntidCoordinates(val latitude: Double, val longitude: Double)

/** Whether a missing location grant can only be fixed over adb. */
const val LOCATION_NEEDS_ADB = false

private const val FIX_ATTEMPTS = 20
private const val FIX_POLL_MS = 500L
private const val MAX_FIX_AGE_MS = 2 * 60 * 1000L

class NearMeLocation internal constructor(
    private val requestLauncher: PermissionRequestLauncher?,
) {
    suspend fun hasPermission(): Boolean =
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION).getOrNull()?.permissionResult ==
            LightServiceMethod.GetPermission.Result.Granted

    fun requestPermission() {
        requestLauncher?.launch()
    }

    /** Asks LightOS to listen briefly and waits for a recent fix. */
    suspend fun coordinates(): SanntidCoordinates? {
        callRemoteServiceMethod(LightServiceMethod.RequestLocationUpdates, Unit)
        try {
            repeat(FIX_ATTEMPTS) {
                val fix = callRemoteServiceMethod(LightServiceMethod.GetCurrentLocation, Unit).getOrNull()
                val latitude = fix?.latitude
                val longitude = fix?.longitude
                val age = fix?.timestampMs?.let { System.currentTimeMillis() - it }
                if (latitude != null && longitude != null && (age == null || age < MAX_FIX_AGE_MS)) {
                    return SanntidCoordinates(latitude, longitude)
                }
                delay(FIX_POLL_MS)
            }
            return null
        } finally {
            withContext(NonCancellable) {
                callRemoteServiceMethod(LightServiceMethod.ReleaseLocationUpdates, Unit)
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberNearMeLocation(context: SealedLightContext): NearMeLocation {
    val launcher = rememberPermissionRequestLauncher(Manifest.permission.ACCESS_FINE_LOCATION)
    return remember(launcher) { NearMeLocation(launcher) }
}

/** Light's stock keyboard has one layout, so there is nothing to toggle. */
@Composable
fun rememberKeyboardToggleItem(): LightBottomBarItem? = null
