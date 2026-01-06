package com.dublikunt.dmclient.database

import androidx.room.TypeConverter
import com.dublikunt.dmclient.scrapper.ImageType
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromImageTypeList(value: List<ImageType>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toImageTypeList(value: String): List<ImageType> {
        return Json.decodeFromString(value)
    }
}
