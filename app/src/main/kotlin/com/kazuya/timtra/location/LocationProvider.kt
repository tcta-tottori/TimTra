package com.kazuya.timtra.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.os.CancellationSignal
import com.kazuya.timtra.core.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 現在地の取得。
 *
 * - [current]: 1 回だけ取る（通知の直前の判定など）。結果は数分キャッシュする。
 * - [updates]: ホーム画面が表示されている間だけ数秒おきに受け取る（地図と「駅まであと何分」用）。
 *   購読が切れれば止まる。バックグラウンドや常駐では使わない（CLAUDE.md 3-4）。
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

        /**
         * バックグラウンド（通知が鳴る瞬間の BroadcastReceiver）でも位置を取れるか。
         * Android 10 以降は ACCESS_BACKGROUND_LOCATION（「常に許可」）が別途必要。
         */
        val hasBackgroundPermission: Boolean
            get() =
                hasPermission &&
                    (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                    )

        /**
         * 許可が無い・取れないときは null。
         * @param maxCacheMillis この時間内に取った位置があればそれを返す（0 で必ず取り直す）。
         * @param timeoutMillis 新規取得の待ち時間。BroadcastReceiver からは短くする。
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

        /**
         * 位置の連続更新。collect している間だけ GPS / 基地局測位を [intervalMillis] 間隔で受け取る。
         * 許可が無い、または測位が使えないときは何も流さずに終わる。
         */
        @SuppressLint("MissingPermission")
        fun updates(intervalMillis: Long = LIVE_INTERVAL_MILLIS): Flow<GeoPoint> =
            callbackFlow {
                val manager = context.getSystemService(LocationManager::class.java)
                if (!hasPermission || manager == null) {
                    close()
                    return@callbackFlow
                }
                val listener =
                    LocationListenerCompat { location ->
                        val point = GeoPoint(location.latitude, location.longitude)
                        cached = Cached(point, SystemClock.elapsedRealtime())
                        trySend(point)
                    }
                val request =
                    LocationRequestCompat
                        .Builder(intervalMillis)
                        .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                        .setMinUpdateIntervalMillis(intervalMillis)
                        .build()
                val providers = LIVE_PROVIDERS.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                providers.forEach { provider ->
                    runCatching {
                        LocationManagerCompat.requestLocationUpdates(
                            manager,
                            provider,
                            request,
                            listener,
                            Looper.getMainLooper(),
                        )
                    }
                }
                awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
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

            /** 「常に許可」。前景の許可を得た後に別途要求する（Android 11 以降は設定画面に飛ぶ）。 */
            const val BACKGROUND_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

            /** 基地局・Wi-Fi 測位を優先する。屋内でも取れて電池も食わない。 */
            private val PROVIDER_PREFERENCE =
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)

            /** 連続更新で使う測位。屋外は GPS、屋内は基地局・Wi-Fi で補う。 */
            private val LIVE_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

            /** 連続更新の間隔。「数秒単位」で追いつつ電池を使いすぎない値。 */
            const val LIVE_INTERVAL_MILLIS = 3_000L
            const val CACHE_MILLIS = 5 * 60_000L
            private const val FRESH_MILLIS = 10 * 60_000L
            const val REQUEST_TIMEOUT_MILLIS = 8_000L
        }
    }
