package com.flashpick.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flashpick.app.data.model.AppUsageStat
import com.flashpick.app.data.model.VideoRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoRecordDao {
    @Query("SELECT * FROM video_records ORDER BY created_at DESC")
    fun getAllRecords(): Flow<List<VideoRecord>>

    @Query("SELECT * FROM video_records WHERE created_at BETWEEN :start AND :end ORDER BY created_at DESC")
    fun getRecordsByDateRange(start: Long, end: Long): Flow<List<VideoRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: VideoRecord): Long

    @Query("UPDATE video_records SET title = :title, summary = :summary, keywords = :keywords, entities = :entities, key_frames = :keyFrames, app_name = :appName, ai_processed = 1 WHERE id = :id")
    suspend fun updateAiResult(id: Long, title: String, summary: String, keywords: String, entities: String, keyFrames: String, appName: String)

    @Query("SELECT * FROM video_records WHERE file_path = :path LIMIT 1")
    suspend fun getByPath(path: String): VideoRecord?
    
    @Query("SELECT * FROM video_records WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%' OR app_name LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun searchRecords(query: String): Flow<List<VideoRecord>>

    @Query("UPDATE video_records SET title = :title, summary = :summary, url = :url WHERE id = :id")
    suspend fun updateRecordDetails(id: Long, title: String, summary: String, url: String?)

    @Query("SELECT * FROM video_records ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestRecord(): VideoRecord?

    @Query("UPDATE video_records SET thumbnail_path = :thumbnailPath, key_frames = :keyFrames WHERE id = :id")
    suspend fun updateThumbnailAndKeyframes(id: Long, thumbnailPath: String, keyFrames: String)

    @Query("SELECT * FROM video_records WHERE ai_processed = 0")
    suspend fun getUnprocessedRecords(): List<VideoRecord>
    
    @Query("SELECT app_name, count(*) as count FROM video_records WHERE created_at > :startTime GROUP BY app_name ORDER BY count DESC")
    fun getAppUsageStats(startTime: Long): Flow<List<AppUsageStat>>
}
