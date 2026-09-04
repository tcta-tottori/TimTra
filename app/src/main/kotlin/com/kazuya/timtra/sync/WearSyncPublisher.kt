package com.kazuya.timtra.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.sync.SyncedSettings
import com.kazuya.timtra.data.sync.WearSyncPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * スマホの設定を Wearable Data Layer で時計に配る（CLAUDE.md 7-4）。
 * 時計側は同梱データで自前計算するので、設定さえ一致すれば結果は同じになる。
 * 時計が無い／Play 開発者サービスが無い端末では失敗するが、無視してよい。
 */
@Singleton
class WearSyncPublisher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun publishSettings(settings: AppSettings) {
            val sentAt = System.currentTimeMillis()
            val request =
                PutDataMapRequest.create(WearSyncPaths.SETTINGS).apply {
                    dataMap.putString(WearSyncPaths.KEY_JSON, SyncedSettings.from(settings, sentAt).toJson())
                    dataMap.putLong(WearSyncPaths.KEY_SENT_AT, sentAt)
                }
            runCatching { Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await() }
                .onFailure { Log.w(TAG, "Wear への設定同期に失敗（時計未接続なら無視してよい）: ${it.message}") }
        }

        private companion object {
            const val TAG = "WearSyncPublisher"
        }
    }
