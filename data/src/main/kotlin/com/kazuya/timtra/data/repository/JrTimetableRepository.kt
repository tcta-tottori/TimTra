package com.kazuya.timtra.data.repository

import android.content.Context
import com.kazuya.timtra.core.data.JrTimetableParser
import com.kazuya.timtra.core.model.JrTimetable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** assets/jr_timetable.json（手動転記の JR 時刻表）を読む。CLAUDE.md 4-3 は app/src/main/assets を指定しているが、Wear と共有するため data に置く。 */
@Singleton
class JrTimetableRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var cached: JrTimetable? = null

        suspend fun timetable(): JrTimetable =
            cached ?: mutex.withLock {
                cached ?: load().also { cached = it }
            }

        private suspend fun load(): JrTimetable =
            withContext(Dispatchers.IO) {
                context.assets
                    .open(ASSET_NAME)
                    .bufferedReader()
                    .use { JrTimetableParser.parse(it.readText()) }
            }

        companion object {
            /** CLAUDE.md 4-3 は app/src/main/assets を指定しているが、Wear と共有するため data に置く。 */
            const val ASSET_NAME = "jr_timetable.json"
        }
    }
