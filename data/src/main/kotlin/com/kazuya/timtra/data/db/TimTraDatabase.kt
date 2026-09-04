package com.kazuya.timtra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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

        /** 端末内のファイル名。assets を差し替えたときに古いコピーを確実に捨てるため、schema 版を含める。 */
        const val DB_NAME = "timtra_gtfs_v1.db"

        fun build(context: Context): TimTraDatabase =
            Room
                .databaseBuilder(context.applicationContext, TimTraDatabase::class.java, DB_NAME)
                .createFromAsset(ASSET_NAME)
                // ダイヤ改正で assets を差し替えたときは、端末内のコピーを作り直す
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
