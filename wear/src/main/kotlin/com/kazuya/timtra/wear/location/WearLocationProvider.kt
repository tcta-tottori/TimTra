package com.kazuya.timtra.wear.location

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
 * 時計の現在地を 1 回だけ取る。発車標でどの停留所・駅を出すかを決めるのにしか使わないので、
 * 数百 m の精度で十分。常時取得はしない（CLAUDE.md 3-4）: 画面かタイルが描かれるときだけ呼ばれ、
 * 結果は数分キャッシュする。
 *
 * 取れないとき（許可が無い・測位が切られている・屋内）は null を返し、呼び出し側は手動選択に倒す。
 */
@Singleton
class WearLocationProvider
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

        val hasPermission: Boolean
            get() = PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

        /** 直前に取った位置（[CACHE_MILLIS] 以内）。取り直しはしない。 */
        fun cached(): GeoPoint? = cached?.takeIf { SystemClock.elapsedRealtime() - it.atElapsedMillis < CACHE_MILLIS }?.point

        /**
         * @param maxCacheMillis この時間内に取った位置があればそれを返す（0 で必ず取り直す）。
         * @param timeoutMillis 新規取得の待ち時間。タイルからは短くする。
         */
        suspend fun current(
            maxCacheMillis: Long = CACHE_MILLIS,
            timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
        ): GeoPoint? {
            if (!hasPermission) return null
            cached?.let { if (SystemClock.elapsedRealtime() - it.atElapsedMillis < maxCacheMillis) return it.point }
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            val location = fetch(manager, timeoutMillis) ?: return null
            return GeoPoint(location.latitude, location.longitude).also {
                cached = Cached(it, SystemClock.elapsedRealtime())
            }
        }

        @SuppressLint("MissingPermission")
        private suspend fun fetch(
            manager: LocationManager,
            timeoutMillis: Long,
        ): Location? {
            val enabled = PROVIDER_PREFERENCE.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            val lastKnown =
                enabled
                    .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                    .maxByOrNull { it.time }
            // 通勤経路の地点は 1.7 km 以上離れているので、十数分前の位置でも取り違えない
            if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_MILLIS) return lastKnown
            val provider = enabled.firstOrNull() ?: return lastKnown
            val fresh =
                withTimeoutOrNull(timeoutMillis) {
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

            /** 時計は基地局測位が弱いので GPS も見る。 */
            private val PROVIDER_PREFERENCE =
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            const val CACHE_MILLIS = 5 * 60_000L
            private const val FRESH_MILLIS = 15 * 60_000L
            const val REQUEST_TIMEOUT_MILLIS = 8_000L

            /** タイルから呼ぶときの待ち時間。描画を待たせないよう短くする。 */
            const val TILE_TIMEOUT_MILLIS = 4_000L
        }
    }
