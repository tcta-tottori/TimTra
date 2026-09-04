package com.kazuya.timtra.data.di

import android.content.Context
import com.kazuya.timtra.data.db.GtfsDao
import com.kazuya.timtra.data.db.TimTraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TimTraDatabase = TimTraDatabase.build(context)

    @Provides
    fun provideGtfsDao(db: TimTraDatabase): GtfsDao = db.gtfsDao()
}
