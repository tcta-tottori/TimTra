package com.kazuya.timtra.wear.sync

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.data.sync.SyncedSettings
import com.kazuya.timtra.data.sync.WearSyncPaths
import com.kazuya.timtra.wear.complication.LeaveCountdownComplicationService
import com.kazuya.timtra.wear.tile.CommuteTileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** スマホから同期された設定を受け取り、時計側の設定を置き換えてタイル・コンプリケーションを更新する。 */
@AndroidEntryPoint
class WearDataListenerService : WearableListenerService() {
    @Inject
    lateinit var settings: SettingsRepository

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var updated = false
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != WearSyncPaths.SETTINGS) return@forEach
            val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearSyncPaths.KEY_JSON) ?: return@forEach
            runCatching { SyncedSettings.parse(json).toAppSettings() }
                .onSuccess { synced ->
                    // WearableListenerService のコールバックはバックグラウンドスレッド。短い DataStore 書き込みなので同期的に待つ
                    runBlocking { settings.replaceAll(synced) }
                    updated = true
                }.onFailure { Log.w(TAG, "設定の同期データを解釈できません: ${it.message}") }
        }
        if (updated) requestRefresh()
    }

    private fun requestRefresh() {
        TileService.getUpdater(this).requestUpdate(CommuteTileService::class.java)
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, LeaveCountdownComplicationService::class.java))
            .requestUpdateAll()
    }

    private companion object {
        const val TAG = "WearDataListener"
    }
}
