package com.flashpick.app.recorder.buffer

import android.media.MediaCodec
import java.util.ArrayDeque

class CircularBuffer(
    private val maxDurationUs: Long
) {

    private val frames = ArrayDeque<EncodedFrame>()
    private var totalDurationUs: Long = 0L

    fun add(frame: EncodedFrame) {
        frames.addLast(frame)
        recomputeDuration()
        trim()
    }

    fun snapshot(): List<EncodedFrame> = frames.toList()

    fun snapshot(durationUs: Long): List<EncodedFrame> {
        if (frames.isEmpty()) return emptyList()
        val result = ArrayDeque<EncodedFrame>()
        val iterator = frames.descendingIterator()
        val lastTime = frames.last().info.presentationTimeUs
        var covered = 0L
        while (iterator.hasNext()) {
            val frame = iterator.next()
            result.addFirst(frame)
            covered = lastTime - frame.info.presentationTimeUs
            if (covered >= durationUs) break
        }
        return result.toList()
    }

    fun clear() {
        frames.clear()
        totalDurationUs = 0L
    }

    fun size(): Int = frames.size

    fun durationSeconds(): Long = totalDurationUs / 1_000_000L

    private fun trim() {
        while (totalDurationUs > maxDurationUs && frames.isNotEmpty()) {
            removeHead()
        }
        // Ensure the first frame is a keyframe
        while (frames.isNotEmpty() && frames.first().info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME == 0) {
            removeHead()
        }
    }

    private fun removeHead() {
        frames.removeFirst()
        recomputeDuration()
    }

    private fun recomputeDuration() {
        totalDurationUs = if (frames.size >= 2) {
            frames.last().info.presentationTimeUs - frames.first().info.presentationTimeUs
        } else {
            0L
        }
    }
}

