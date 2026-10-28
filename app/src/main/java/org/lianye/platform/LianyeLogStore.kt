package org.lianye.platform

import android.content.Context
import android.util.Log
import org.lianye.domain.model.CaptureCompletion
import org.lianye.engine.model.LianyeState
import org.lianye.engine.model.MatchResult
import org.lianye.service.gesture.model.ControlledScrollCommand
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-app ring buffer that mirrors the engine/gesture/capture logs so the user can copy a
 * diagnosis to the clipboard without adb. Writes both to a file (so a long session survives
 * process restarts) and to logcat (unchanged visibility for existing tooling).
 */
object LianyeLogStore {
    private const val TAG = "LianyeLog"
    private const val MAX_MEMORY_LINES = 2000
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024

    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    @Volatile private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                logFile = File(context.filesDir, "lianye-diagnostics.log")
                loadFromFileLocked()
            }
        }
    }

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = timeFormat.format(Date())
        val line = buildString {
            append(timestamp).append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (throwable != null) {
                append(" | ").append(throwable::class.java.simpleName)
                throwable.message?.let { append(": ").append(it) }
            }
        }
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_MEMORY_LINES) buffer.removeFirst()
            appendToFileLocked(line)
        }
        when (level) {
            "E" -> Log.e(tag, message, throwable)
            "W" -> Log.w(tag, message, throwable)
            "I" -> Log.i(tag, message, throwable)
            else -> Log.d(tag, message, throwable)
        }
    }

    /** One-call convenience for the hot capture path. */
    fun captureEvent(event: String, detail: String = "") {
        log("I", TAG, if (detail.isEmpty()) event else "$event | $detail")
    }

    fun frameMatch(frameIndex: Int, deltaY: Int, isBottomReached: Boolean, isStationary: Boolean, isDynamic: Boolean, sadScore: Float, ambiguityRatio: Float, isDeformed: Boolean = false) {
        log("D", TAG, "Frame $frameIndex match deltaY=$deltaY bottom=$isBottomReached stationary=$isStationary dynamic=$isDynamic deformed=$isDeformed sad=%.2f amb=%.2f".format(sadScore, ambiguityRatio))
    }

    fun stateChanged(from: LianyeState, to: LianyeState, reason: String) {
        log("I", TAG, "State $from -> $to | $reason")
    }

    fun gesture(command: ControlledScrollCommand) {
        log("D", TAG, "Gesture (${command.startX}, ${command.startY}) -> (${command.endX}, ${command.endY}) move=${command.moveDurationMs}ms hold=${command.holdDurationMs}ms")
    }

    fun completion(reason: CaptureCompletion, frames: Int, height: Int) {
        log("I", TAG, "Completion reason=$reason frames=$frames height=$height")
    }

    fun completionWithSad(reason: CaptureCompletion, frames: Int, height: Int, lastSad: Float) {
        log("I", TAG, "Completion reason=$reason frames=$frames height=$height lastSad=%.2f".format(lastSad))
    }

    fun dump(): String = synchronized(lock) {
        if (buffer.isEmpty()) return@synchronized "(no log entries yet)"
        buffer.joinToString("\n")
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            logFile?.let { file -> runCatching { file.delete() } }
        }
    }

    private fun loadFromFileLocked() {
        val file = logFile ?: return
        if (!file.exists()) return
        runCatching {
            file.readLines().takeLast(MAX_MEMORY_LINES).forEach { buffer.addLast(it) }
        }
    }

    private fun appendToFileLocked(line: String) {
        val file = logFile ?: return
        runCatching {
            if (file.length() > MAX_FILE_BYTES) {
                val trimmed = file.readLines().takeLast(MAX_MEMORY_LINES / 2)
                file.writeText(trimmed.joinToString("\n") + "\n")
            }
            file.appendText(line + "\n")
        }
    }
}
