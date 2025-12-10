package com.flashpick.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_records")
data class VideoRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // File Info
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,

    // Context Info
    @ColumnInfo(name = "source_package") val sourcePackage: String = "",
    @ColumnInfo(name = "app_name") val appName: String = "",

    // AI Analysis
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "summary") val summary: String? = null,
    @ColumnInfo(name = "keywords") val keywords: String? = null, // JSON string
    @ColumnInfo(name = "entities") val entities: String? = null, // JSON string
    @ColumnInfo(name = "key_frames") val keyFrames: String? = null, // JSON list of paths
    @ColumnInfo(name = "url") val url: String? = null, // Source URL if available
    @ColumnInfo(name = "user_question") val userQuestion: String? = null,
    @ColumnInfo(name = "ai_answer") val aiAnswer: String? = null,

    // Status
    @ColumnInfo(name = "ai_processed") val aiProcessed: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false
)

data class AppUsageStat(
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "count") val count: Int
)
