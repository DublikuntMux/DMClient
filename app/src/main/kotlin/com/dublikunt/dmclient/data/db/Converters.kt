package com.dublikunt.dmclient.data.db

import androidx.room.TypeConverter
import com.dublikunt.dmclient.data.repository.DownloadState

class Converters {
    @TypeConverter
    fun downloadState(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter
    fun downloadState(value: DownloadState): String = value.name
}
