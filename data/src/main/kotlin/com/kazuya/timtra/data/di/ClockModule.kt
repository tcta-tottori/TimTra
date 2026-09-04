package com.kazuya.timtra.data.di

import com.kazuya.timtra.core.TimTraConstants
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** 現在時刻の供給口。スマホ・Wear 共通。テストで差し替えられるようにする。 */
interface AppClock {
    fun now(): LocalDateTime
}

@Singleton
class SystemClock
    @Inject
    constructor() : AppClock {
        override fun now(): LocalDateTime = LocalDateTime.now(TimTraConstants.ZONE)
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    abstract fun bindClock(impl: SystemClock): AppClock
}
