package com.kazuya.timtra.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kazuya.timtra.core.geo.TileSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Duration

/**
 * ホームの地図の下地に使う OpenStreetMap の標準タイル（ラスタ）を取得する。
 *
 * - 取得は地図が画面に出ている間だけ（呼び出し側の責務）。常駐やプリフェッチはしない（CLAUDE.md 3-4）。
 * - 端末内にキャッシュし（メモリ + ファイル）、通信できないときはキャッシュ済みの範囲だけ描く。
 * - OSM のタイル利用規約に従い、アプリを示す User-Agent を付け、同時接続は 2 本まで。
 *   出典表示「© OpenStreetMap contributors」は地図の隅と About 画面に出す。
 */
class MapTileLoader private constructor(
    context: Context,
) {
    private val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
    private val memory = LruCache<String, ImageBitmap>(MEMORY_TILES)
    private val permits = Semaphore(MAX_PARALLEL)

    /** 直近に失敗したタイル。しばらく再試行しない（圏外で連打しないため）。 */
    private val failedAt = HashMap<String, Long>()

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT)
            .readTimeout(TIMEOUT)
            .build()

    /** メモリにあればすぐ返す（描画スレッドから呼んでよい）。 */
    fun cached(tile: TileSpec): ImageBitmap? = memory.get(tile.key)

    /**
     * メモリ → ファイル → ネットワークの順に探す。取れなければ null。
     * ファイルが古い（[STALE_DAYS] 日超）ときは取り直しを試み、失敗したら古いものを使う。
     */
    suspend fun load(tile: TileSpec): ImageBitmap? {
        memory.get(tile.key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = fileFor(tile)
            val onDisk = if (file.exists()) decode(file.readBytes()) else null
            val fresh = onDisk != null && System.currentTimeMillis() - file.lastModified() < STALE_MILLIS
            val bitmap =
                if (fresh) {
                    onDisk
                } else {
                    download(tile)
                        ?.also { bytes ->
                            runCatching {
                                file.parentFile?.mkdirs()
                                file.writeBytes(bytes)
                            }
                        }?.let(::decode)
                        ?: onDisk
                }
            bitmap?.also { memory.put(tile.key, it) }
        }
    }

    private suspend fun download(tile: TileSpec): ByteArray? {
        val now = System.currentTimeMillis()
        synchronized(failedAt) {
            failedAt[tile.key]?.let { if (now - it < RETRY_MILLIS) return null }
        }
        return permits.withPermit {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url("$TILE_BASE_URL/${tile.zoom}/${tile.x}/${tile.y}.png")
                        .header("User-Agent", USER_AGENT)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.bytes()
                }
            }.onFailure {
                synchronized(failedAt) { failedAt[tile.key] = System.currentTimeMillis() }
            }.getOrNull()
        }
    }

    private fun store(
        file: File,
        bytes: ByteArray,
    ) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    private fun fileFor(tile: TileSpec): File = File(dir, "${tile.zoom}/${tile.x}/${tile.y}.png")

    /** キャッシュが上限を超えていたら古いものから消す。起動時に 1 回、バックグラウンドで呼ぶ。 */
    suspend fun trim() =
        withContext(Dispatchers.IO) {
            val files = dir.walkTopDown().filter { it.isFile }.toList()
            var total = files.sumOf { it.length() }
            if (total <= MAX_DISK_BYTES) return@withContext
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_DISK_BYTES * TRIM_TO) break
                total -= f.length()
                f.delete()
            }
        }

    companion object {
        /** OSM 標準タイル。利用規約: https://operations.osmfoundation.org/policies/tiles/ */
        private const val TILE_BASE_URL = "https://tile.openstreetmap.org"
        private const val USER_AGENT = "TimTra/0.1 (personal commute app; https://github.com/tcta-tottori/TimTra)"
        private const val CACHE_DIR = "osm_tiles"
        private const val MEMORY_TILES = 64
        private const val MAX_PARALLEL = 2
        private const val MAX_DISK_BYTES = 60L * 1024 * 1024
        private const val TRIM_TO = 0.8
        private const val STALE_DAYS = 14L
        private const val STALE_MILLIS = STALE_DAYS * 24 * 60 * 60 * 1000
        private const val RETRY_MILLIS = 60_000L
        private val TIMEOUT: Duration = Duration.ofSeconds(8)

        @Volatile
        private var instance: MapTileLoader? = null

        fun get(context: Context): MapTileLoader =
            instance ?: synchronized(this) {
                instance ?: MapTileLoader(context.applicationContext).also { instance = it }
            }
    }
}
