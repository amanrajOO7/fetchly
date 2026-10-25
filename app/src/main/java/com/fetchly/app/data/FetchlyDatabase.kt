package com.fetchly.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [DownloadEntity::class, SegmentEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(FetchlyConverters::class)
abstract class FetchlyDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "fetchly.db"
    }
}

class FetchlyConverters {
    @TypeConverter
    fun statusToString(status: DownloadStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
