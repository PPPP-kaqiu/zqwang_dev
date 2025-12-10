package com.flashpick.app.recorder.buffer

import android.media.MediaCodec

data class EncodedFrame(
    val data: ByteArray,
    val info: MediaCodec.BufferInfo
)

