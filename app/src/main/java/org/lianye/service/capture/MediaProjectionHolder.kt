package org.lianye.service.capture

import android.media.projection.MediaProjection

/**
 * Thread-safe holder for the active MediaProjection instance.
 * Maintained by LianyeMediaProjectionService and consumed by MediaProjectionFrameCapturer.
 */
object MediaProjectionHolder {
    @Volatile
    private var mediaProjection: MediaProjection? = null

    fun set(projection: MediaProjection?) {
        mediaProjection = projection
    }

    fun get(): MediaProjection? = mediaProjection

    fun clear() {
        mediaProjection = null
    }

    fun isReady(): Boolean = mediaProjection != null
}
