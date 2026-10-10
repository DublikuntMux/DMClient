package com.dublikunt.dmclient.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dublikunt.dmclient.data.db.AppDatabase
import com.dublikunt.dmclient.data.db.MIGRATION_8_9
import com.dublikunt.dmclient.data.db.historicalMigrations
import com.dublikunt.dmclient.data.db.seedDefaultStatuses
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "main_database")
            .addMigrations(*historicalMigrations, MIGRATION_8_9)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    seedDefaultStatuses(db)
                }
            })
            .fallbackToDestructiveMigration(false).build()
}
