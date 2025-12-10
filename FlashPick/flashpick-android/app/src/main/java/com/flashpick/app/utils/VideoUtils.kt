package com.flashpick.app.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

object VideoUtils {
    /**
     * Extracts a keyframe from the video at the specified time (or middle if not specified).
     * Saves it as a JPEG file with the same base name + "_thumb.jpg".
     */
    fun extractAndSaveThumbnail(videoFile: File, atTimeMs: Long? = null): File? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            
            val timeToExtract = if (atTimeMs != null) {
                atTimeMs * 1000 // to micros
            } else {
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                (durationMs / 2) * 1000 // Middle
            }

            // OPTION_CLOSEST_SYNC for performance
            val bitmap = retriever.getFrameAtTime(timeToExtract, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) 
                ?: return null

            val thumbFile = File(videoFile.parent, "${videoFile.nameWithoutExtension}_thumb.jpg")
            FileOutputStream(thumbFile).use { out ->
                // Compress to JPEG, 80% quality
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            return thumbFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            retriever.release()
        }
    }

    fun extractKeyFrames(videoFile: File, count: Int = 5, focusTimeMs: Long? = null): List<File> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<File>()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs == 0L) return emptyList()

            val rawTimestamps = mutableListOf<Long>()

            // 1. Uniform Sampling (Background Context)
            val uniformCount = count
            val step = durationMs / (uniformCount + 1)
            for (i in 1..uniformCount) {
                rawTimestamps.add(i * step)
            }

            // 2. Focused Sampling (Trigger Moment)
            if (focusTimeMs != null && focusTimeMs > 0) {
                // T - 0.5s, T, T + 0.5s
                rawTimestamps.add((focusTimeMs - 500).coerceAtLeast(0))
                rawTimestamps.add(focusTimeMs.coerceAtMost(durationMs))
                rawTimestamps.add((focusTimeMs + 500).coerceAtMost(durationMs))
            } else if (focusTimeMs == null) {
                // If no focus time, maybe add start and end? 
                // Currently covered by uniform.
            }

            // 3. Deduplicate and Sort
            // Filter timestamps that are too close (e.g. within 300ms)
            rawTimestamps.sort()
            val mergedTimestamps = mutableListOf<Long>()
            
            rawTimestamps.forEach { ts ->
                if (mergedTimestamps.isEmpty() || (ts - mergedTimestamps.last() > 300)) {
                    mergedTimestamps.add(ts)
                }
            }
            
            mergedTimestamps.forEachIndexed { index, timeMs ->
                val timeUs = timeMs * 1000
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val frameFile = File(videoFile.parent, "${videoFile.nameWithoutExtension}_frame_$index.jpg")
                    FileOutputStream(frameFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out) // Lower quality for multi-frame
                    }
                    frames.add(frameFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        return frames
    }
}
