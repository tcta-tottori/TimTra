package com.kazuya.timtra.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
 * 1 回の測位結果。「勤務先にいるか」の判定は、いつ・どれくらいの精度で取れた位置かで
 * 信頼度が変わるので、座標だけでなく古さと誤差半径も返す。
 */
data class LocationFix(
    val point: GeoPoint,
    /** 測位からの経過時間（ミリ秒）。端末が返さない場合は 0。 */
    val ageMillis: Long,
    /** 誤差半径（メートル）。端末が返さない場合は [UNKNOWN_ACCURACY]。 */
    val accuracyMeters: Float,
) {
    /** 古すぎて「いまどこにいるか」の判断には使えない位置。 */
    val isStale: Boolean get() = ageMillis > STALE_MILLIS

    companion object {
        const val UNKNOWN_ACCURACY = 1_000f

        /** これより古い位置は「現在地」として扱わない。 */
        const val STALE_MILLIS = 15 * 60_000L
    }
}

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
            val fix: LocationFix,
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
        ): GeoPoint? = currentFix(maxCacheMillis, timeoutMillis)?.point

        /** [current] と同じだが、古さと誤差半径も返す。勤務先判定はこちらを使う。 */
        suspend fun currentFix(
            maxCacheMillis: Long = CACHE_MILLIS,
            timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
        ): LocationFix? {
            if (!hasPermission) return null
            cached?.let { hit ->
                val sinceCached = SystemClock.elapsedRealtime() - hit.atElapsedMillis
                if (sinceCached < maxCacheMillis) return hit.fix.copy(ageMillis = hit.fix.ageMillis + sinceCached)
            }
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            val location = fetch(manager, timeoutMillis) ?: return null
            val fix =
                LocationFix(
                    point = GeoPoint(location.latitude, location.longitude),
                    ageMillis = (System.currentTimeMillis() - location.time).coerceAtLeast(0L),
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else LocationFix.UNKNOWN_ACCURACY,
                )
            cached = Cached(fix, SystemClock.elapsedRealtime())
            return fix
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
            const val CACHE_MILLIS = 5 * 60_000L
            private const val FRESH_MILLIS = 10 * 60_000L
            const val REQUEST_TIMEOUT_MILLIS = 8_000L
        }
    }
