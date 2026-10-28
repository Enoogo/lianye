package org.lianye.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Build
import org.lianye.data.draft.DraftStore
import org.lianye.domain.model.CaptureCompletion
import org.lianye.domain.model.CaptureMode
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualPhase
import org.lianye.domain.model.Draft
import org.lianye.domain.model.EditSnapshot
import org.lianye.domain.model.LianyeAction
import org.lianye.domain.model.WeavingState
import org.lianye.engine.model.TileMetadata
import java.util.UUID
import java.io.IOException

/** Persistence methods are synchronous and should be invoked on the IO dispatcher. */
class LianyeRepository(private val draftStore: DraftStore? = null) {
    private val _draft = MutableStateFlow(draftStore?.load())
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    private val _lastPersistenceError = MutableStateFlow<String?>(null)
    val lastPersistenceError: StateFlow<String?> = _lastPersistenceError.asStateFlow()
    private val _weavingState = MutableStateFlow<WeavingState>(
        _draft.value?.let { WeavingState.Preview(it.tiles, it.height) } ?: WeavingState.Idle(false)
    )
    val weavingState: StateFlow<WeavingState> = _weavingState.asStateFlow()

    private val _isProjectionGranted = MutableStateFlow(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    val isProjectionGranted: StateFlow<Boolean> = _isProjectionGranted.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()
    private val _captureMode = MutableStateFlow(CaptureMode.AUTO)
    val captureMode: StateFlow<CaptureMode> = _captureMode.asStateFlow()
    private val _manualSession = MutableStateFlow(ManualCaptureState())
    val manualSession: StateFlow<ManualCaptureState> = _manualSession.asStateFlow()
    private var captureId: String? = null
    private val persistenceLock = Any()
    private var isDiscarding = false

    fun updateProjectionGranted(granted: Boolean) {
        _isProjectionGranted.value = granted
    }

    fun setOverlayVisible(visible: Boolean) { _isOverlayVisible.value = visible }

    @Synchronized
    fun setCaptureMode(mode: CaptureMode): Boolean {
        if (_weavingState.value is WeavingState.Weaving || _manualSession.value.phase != ManualPhase.IDLE) return false
        _captureMode.value = mode
        return true
    }

    fun updateManualSession(state: ManualCaptureState) { _manualSession.value = state }

    @Synchronized
    fun updateServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
        if (!connected && _captureMode.value == CaptureMode.AUTO && _manualSession.value.phase == ManualPhase.IDLE) _isOverlayVisible.value = false
        if (_weavingState.value is WeavingState.Idle) {
            _weavingState.value = WeavingState.Idle(connected)
        }
        // The capture owner finalizes interrupted work. Service loss never discards the result.
    }

    @Synchronized
    fun startWeaving(): Boolean {
        if (isDiscarding || _draft.value != null || _weavingState.value is WeavingState.Weaving) return false
        captureId = UUID.randomUUID().toString()
        _weavingState.value = WeavingState.Weaving()
        return true
    }

    fun updateProgress(frameCount: Int, currentHeight: Int, viewportHeightPx: Int = 0) {
        if (_weavingState.value is WeavingState.Weaving) {
            _weavingState.value = WeavingState.Weaving(frameCount, currentHeight, viewportHeightPx)
        }
    }

    fun checkpointDraft(tiles: List<TileMetadata>) {
        if (tiles.isEmpty()) return
        synchronized(this) {
            val id = captureId ?: return
            val previous = _draft.value
            if (previous != null && previous.id != id) return
            publishDraft((previous ?: Draft(id, tiles)).copy(tiles = tiles, completion = CaptureCompletion.INTERRUPTED))
        }
        persistCurrentDraft()
    }

    fun finishWeaving(tiles: List<TileMetadata>, completion: CaptureCompletion = CaptureCompletion.COMPLETED) {
        synchronized(this) {
            if (tiles.isNotEmpty()) {
                val current = _draft.value
                publishDraft((current ?: Draft(captureId ?: UUID.randomUUID().toString(), tiles)).copy(tiles = tiles, completion = completion))
            }
            captureId = null
            _weavingState.value = WeavingState.Preview(tiles, tiles.sumOf { it.height })
        }
        persistCurrentDraft()
    }

    fun failWeaving(message: String, partialTiles: List<TileMetadata> = emptyList(), completion: CaptureCompletion = CaptureCompletion.INTERRUPTED) {
        synchronized(this) {
            val tiles = partialTiles.ifEmpty { _draft.value?.tiles ?: emptyList() }
            if (tiles.isNotEmpty()) {
                val current = _draft.value
                publishDraft((current ?: Draft(captureId ?: UUID.randomUUID().toString(), tiles)).copy(tiles = tiles, completion = completion))
            }
            captureId = null
            _weavingState.value = WeavingState.Error(message, tiles)
        }
        persistCurrentDraft()
    }

    /** Updates immediately on the caller thread without doing disk work. */
    @Synchronized
    fun adoptDraft(updated: Draft): Boolean {
        if (isDiscarding) return false
        val current = _draft.value ?: return false
        // Activity callbacks may arrive after recreation, discard, or a newer edit.
        if (updated.id != current.id || updated.tiles != current.tiles || updated.revision <= current.revision) return false
        val savedSnapshot = current.savedSnapshot ?: current.canonicalSnapshot().takeIf { current.isSaved }
        publishDraft(updated.copy(savedRevision = current.savedRevision, savedSnapshot = savedSnapshot))
        return true
    }

    /** Binds delayed viewport callbacks to the editor which produced them. */
    @Synchronized
    fun adoptViewport(draftId: String, offsetY: Float, scale: Float): Boolean {
        if (!offsetY.isFinite() || !scale.isFinite() || scale <= 0f) return false
        if (isDiscarding) return false
        val current = _draft.value ?: return false
        if (draftId != current.id) return false
        val safeOffset = offsetY.coerceAtLeast(0f)
        if (current.viewOffsetY == safeOffset && current.viewScale == scale) return false
        publishDraft(current.copy(viewOffsetY = safeOffset, viewScale = scale))
        return true
    }

    fun updateDraft(updated: Draft) {
        if (adoptDraft(updated)) persistCurrentDraft()
    }

    fun markDraftSaved(revision: Int) {
        synchronized(this) {
            val current = _draft.value ?: return
            if (revision !in 0..current.revision || revision < current.savedRevision) return
            // Legacy callers lack an export identity/snapshot. Only the current version
            // (or immutable initial version) can be identified without guessing history.
            val snapshot = when (revision) {
                current.revision -> current.canonicalSnapshot()
                0 -> current.canonicalSnapshot(current.history.first())
                else -> null
            }
            publishDraft(current.copy(savedRevision = revision, savedSnapshot = snapshot))
        }
        persistCurrentDraft()
    }

    /** Binds an asynchronous gallery export to its original draft and actual output. */
    fun markDraftSaved(draftId: String, revision: Int, snapshot: EditSnapshot): Boolean {
        synchronized(this) {
            val current = _draft.value ?: return false
            if (current.id != draftId || revision !in 0..current.revision || revision < current.savedRevision) return false
            val canonical = current.canonicalSnapshot(snapshot)
            if (revision == current.revision && canonical != current.canonicalSnapshot()) return false
            publishDraft(current.copy(savedRevision = revision, savedSnapshot = canonical))
        }
        persistCurrentDraft()
        return true
    }

    fun saveViewport(offsetY: Float, scale: Float, draftId: String? = null) {
        val id = draftId ?: synchronized(this) { _draft.value?.id } ?: return
        if (adoptViewport(id, offsetY, scale)) persistCurrentDraft()
    }

    /** IO only. Memory adoption remains available while a manifest is written. */
    fun persistCurrentDraft() {
        synchronized(persistenceLock) {
            val snapshot = synchronized(this) { _draft.value } ?: return
            try {
                draftStore?.save(snapshot)
                synchronized(this) {
                    if (_draft.value?.id == snapshot.id) _lastPersistenceError.value = null
                }
            } catch (_: IOException) {
                synchronized(this) {
                    if (_draft.value?.id == snapshot.id) _lastPersistenceError.value = "draft_storage_failed"
                }
            }
        }
    }

    fun discardDraft(): Boolean {
        synchronized(persistenceLock) {
            synchronized(this) {
                check(_weavingState.value !is WeavingState.Weaving) { "Stop capture before discarding its draft" }
                isDiscarding = true
            }
            try {
                draftStore?.clear()
                synchronized(this) {
                    _lastPersistenceError.value = null
                    _draft.value = null
                    captureId = null
                    reset()
                }
                return true
            } catch (_: IOException) {
                _lastPersistenceError.value = "draft_storage_failed"
                return false
            } finally {
                synchronized(this) { isDiscarding = false }
            }
        }
    }

    /** Resets task presentation only; navigation and lifecycle events never delete a draft. */
    fun reset() { _weavingState.value = WeavingState.Idle(_isServiceConnected.value) }

    fun triggerAction(action: LianyeAction) {
        when (action) {
            is LianyeAction.Start -> {
                if (!_isServiceConnected.value) {
                    _weavingState.value = WeavingState.Error("Accessibility service is not enabled. Turn it on first.")
                } else {
                    startWeaving()
                }
            }
            is LianyeAction.Stop -> {
                val current = _weavingState.value
                if (current is WeavingState.Weaving) {
                    val tiles = _draft.value?.tiles ?: emptyList()
                    _weavingState.value = WeavingState.Preview(tiles, tiles.sumOf { it.height })
                }
            }
            is LianyeAction.Reset -> reset()
            is LianyeAction.ReportError -> failWeaving(action.message)
        }
    }

    private fun publishDraft(value: Draft) {
        _draft.value = value
    }
}
