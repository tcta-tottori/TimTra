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
 * 下地に使うタイルの種類。どちらも元データは OpenStreetMap。
 *
 * 通勤で見たいのは「道の形」だけなので、建物や店の絵が無い、道が主役の地図を既定にする。
 */
enum class MapTileStyle {
    /**
     * CARTO Dark（ラベル無し）。建物や店を描かず、道と水だけの黒地の地図。
     * アプリの地（黒〜濃紺）にそのまま馴染むので、色は少し持ち上げるだけでよい。
     */
    CARTO_DARK,

    /**
     * OpenStreetMap 標準。上が使えないときの控え。白地なので描くときに色を反転する。
     */
    OSM,
    ;

    /** キャッシュを置く場所。種類ごとに分けて、混ざらないようにする。 */
    val cacheDir: String get() = if (this == CARTO_DARK) "carto_dark" else "osm"

    fun url(tile: TileSpec): String =
        when (this) {
            CARTO_DARK -> {
                val host = CARTO_HOSTS[(tile.x + tile.y) % CARTO_HOSTS.size]
                "https://$host/dark_nolabels/${tile.zoom}/${tile.x}/${tile.y}@2x.png"
            }
            OSM -> "https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png"
        }

    private companion object {
        val CARTO_HOSTS =
            listOf(
                "a.basemaps.cartocdn.com",
                "b.basemaps.cartocdn.com",
                "c.basemaps.cartocdn.com",
                "d.basemaps.cartocdn.com",
            )
    }
}

/**
 * ホームの地図の下地（ラスタタイル）を取得する。
 *
 * - 取得は地図が画面に出ている間だけ（呼び出し側の責務）。常駐やプリフェッチはしない（CLAUDE.md 3-4）。
 * - 端末内にキャッシュし（メモリ + ファイル）、通信できないときはキャッシュ済みの範囲だけ描く。
 * - 取得先の規約に従い、アプリを示す User-Agent を付け、同時接続は 2 本まで。
 *   出典表示「© OpenStreetMap contributors © CARTO」は地図の隅と About 画面に出す。
 * - 既定の下地が配信側の都合で取れなくなったときは OSM 標準に切り替える（[style]）。
 *   圏外（通信そのものの失敗）では切り替えない。地図の色はこの [style] に合わせて選ぶ。
 */
class MapTileLoader private constructor(
    context: Context,
) {
    private val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
    private val memory = LruCache<String, ImageBitmap>(MEMORY_TILES)
    private val permits = Semaphore(MAX_PARALLEL)

    /** 今使っている下地。描くときの色はこれで決める。 */
    @Volatile
    var style: MapTileStyle = MapTileStyle.CARTO_DARK
        private set

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
                    // 取りに行くあいだに下地が切り替わることがあるので、置き場は取れてから決める
                    download(tile)?.let { bytes -> store(fileFor(tile), bytes) }?.let(::decode) ?: onDisk
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
                        .url(style.url(tile))
                        .header("User-Agent", USER_AGENT)
                        .build()
                client.newCall(request).execute().use { response ->
                    // 返事は来たのに貰えない = 配信が変わった。控えの下地に切り替える
                    if (!response.isSuccessful) {
                        fallBack()
                        throw IOException("HTTP ${response.code}")
                    }
                    response.body.bytes()
                }
            }.onFailure {
                synchronized(failedAt) { failedAt[tile.key] = System.currentTimeMillis() }
            }.getOrNull()
        }
    }

    /** 既定の下地が使えないと分かったときだけ呼ぶ。以後は OSM 標準で描く。 */
    private fun fallBack() {
        if (style == MapTileStyle.CARTO_DARK) {
            style = MapTileStyle.OSM
            memory.evictAll()
            synchronized(failedAt) { failedAt.clear() }
        }
    }

    /** 取れたタイルを置き場に書く。書けなくても描画は続ける。 */
    private fun store(
        file: File,
        bytes: ByteArray,
    ): ByteArray {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        return bytes
    }

    private fun decode(bytes: ByteArray): ImageBitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

    private fun fileFor(tile: TileSpec): File = File(dir, "${style.cacheDir}/${tile.zoom}/${tile.x}/${tile.y}.png")

    /** キャッシュが上限を超えていたら古いものから消す。起動時に 1 回、バックグラウンドで呼ぶ。 */
    suspend fun trim() =
        withContext(Dispatchers.IO) {
            runCatching { File(dir.parentFile, LEGACY_CACHE_DIR).deleteRecursively() }
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
        /**
         * 出典と規約:
         * - CARTO Basemaps（非商用は無償・要出典）https://carto.com/basemaps
         * - OSM 標準タイル https://operations.osmfoundation.org/policies/tiles/
         */
        private const val USER_AGENT = "TimTra/0.1 (personal commute app; https://github.com/tcta-tottori/TimTra)"
        private const val CACHE_DIR = "map_tiles"

        /** 種類を分ける前に使っていた置き場。見つけたら消す。 */
        private const val LEGACY_CACHE_DIR = "osm_tiles"
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
