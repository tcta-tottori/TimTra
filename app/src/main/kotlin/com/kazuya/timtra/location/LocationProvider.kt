package com.kazuya.timtra.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.kazuya.timtra.core.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 現在地を 1 回だけ取る。往路/復路の判定にしか使わないので粗い精度で十分。
 * 常時取得はしない（CLAUDE.md 3-4）: ホーム画面が表示されている間だけ呼ばれ、結果は数分キャッシュする。
 */
@Singleton
class LocationProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private data class Cached(
            val point: GeoPoint,
            val atElapsedMillis: Long,
        )

        @Volatile
        private var cached: Cached? = null

        /** 粗い位置でも細かい位置でも、どちらかの許可があれば取れる。 */
        val hasPermission: Boolean
            get() = PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        /** 許可が無い・取れないときは null。 */
        suspend fun current(): GeoPoint? {
            if (!hasPermission) return null
            cached?.let { if (SystemClock.elapsedRealtime() - it.atElapsedMillis < CACHE_MILLIS) return it.point }
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            val location = fetch(manager) ?: return null
            return GeoPoint(location.latitude, location.longitude).also {
                cached = Cached(it, SystemClock.elapsedRealtime())
            }
        }

        @SuppressLint("MissingPermission")
        private suspend fun fetch(manager: LocationManager): Location? {
            val enabled = PROVIDER_PREFERENCE.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            val lastKnown =
                enabled
                    .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                    .maxByOrNull { it.time }
            if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_MILLIS) return lastKnown
            val provider = enabled.firstOrNull() ?: return lastKnown
            val fresh =
                withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            LocationManagerCompat.getCurrentLocation(
                                manager,
                                provider,
                                signal,
                                ContextCompat.getMainExecutor(context),
                            ) { location -> if (continuation.isActive) continuation.resume(location) }
                        }.onFailure { if (continuation.isActive) continuation.resume(null) }
                    }
                }
            return fresh ?: lastKnown
        }

        companion object {
            val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

            /** 基地局・Wi-Fi 測位を優先する。屋内でも取れて電池も食わない。 */
            private val PROVIDER_PREFERENCE =
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            private const val CACHE_MILLIS = 5 * 60_000L
            private const val FRESH_MILLIS = 10 * 60_000L
            private const val REQUEST_TIMEOUT_MILLIS = 8_000L
        }
    }
