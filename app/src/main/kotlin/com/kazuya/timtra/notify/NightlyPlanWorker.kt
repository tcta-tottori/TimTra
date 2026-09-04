package com.kazuya.timtra.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** 前夜（および再計算要求時）に通知を計算して予約するジョブ。 */
@HiltWorker
class NightlyPlanWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val scheduler: NotificationScheduler,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            runCatching { scheduler.replan() }
                .fold(
                    onSuccess = { Result.success() },
                    onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() },
                )

        private companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
