package com.kazuya.timtra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * プリパッケージ DB。読み取り専用で、ダイヤ改正時は assets の DB を差し替えてアプリを更新する。
 * version は docs/db_schema.md の SCHEMA_VERSION と合わせる。
 */
@Database(
    entities = [
        MetaEntity::class,
        StopEntity::class,
        RouteEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        CalendarEntity::class,
        CalendarDateEntity::class,
        CommuteLegEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TimTraDatabase : RoomDatabase() {
    abstract fun gtfsDao(): GtfsDao

    companion object {
        /** assets 内のファイル名。tools/gtfs_import.py の --out と一致させる。 */
        const val ASSET_NAME = "timtra_gtfs.db"

        /** 端末内のファイル名。schema 版を含める（版を上げたら別のファイルになる）。 */
        const val DB_NAME = "timtra_gtfs_v1.db"

        /** 「いつのアプリで写したか」を覚えておく場所。 */
        private const val PREFS_NAME = "timtra_db"
        private const val KEY_COPIED_FOR = "copied_for"

        fun build(context: Context): TimTraDatabase {
            refreshIfReinstalled(context.applicationContext)
            return Room
                .databaseBuilder(context.applicationContext, TimTraDatabase::class.java, DB_NAME)
                .createFromAsset(ASSET_NAME)
                // schema 版を上げたときも、端末内のコピーを作り直す
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        /**
         * アプリを入れ直したら、端末内の DB のコピーを捨てて assets から写し直させる。
         *
         * `createFromAsset` が写すのは **端末にコピーが無いときだけ**。ダイヤ改正で assets の DB を
         * 差し替えても schema 版が同じなら写し直さないので、更新しても古いダイヤのまま動いてしまう
         * （バスの時刻が公式サイトと違う、という形で出る）。入れ替えた時刻が変われば捨てる。
         */
        private fun refreshIfReinstalled(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stamp = installStamp(context)
            if (prefs.getString(KEY_COPIED_FOR, null) == stamp) return
            val file = context.getDatabasePath(DB_NAME)
            listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).forEach { runCatching { it.delete() } }
            prefs.edit().putString(KEY_COPIED_FOR, stamp).apply()
        }

        /** アプリを入れ替えたことが分かる印。版と入れ替え時刻の組（再インストールでも変わる）。 */
        private fun installStamp(context: Context): String =
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.versionName}/${info.lastUpdateTime}"
            }.getOrElse { "unknown" }
    }
}
