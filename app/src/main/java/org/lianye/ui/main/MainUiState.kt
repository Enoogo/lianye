package org.lianye.ui.main

import android.os.Build
import androidx.annotation.StringRes
import org.lianye.domain.model.WeavingState

enum class RestrictedCardPhase {
    HIDDEN,               // 不展示（未尝试开启、或非受限设备、或服务已连接）
    BLOCKED_GUIDE,        // 放行前：直达系统应用详情页放行
    ALLOWED_READY_RETURN  // 状态无法确认时提示返回设置检查，不能宣称限制已解除
}

/**
 * 纯粹 UI 状态模型，集中计算系统版本逻辑，彻底隔离平台依赖与 Compose 渲染。
 */
data class MainUiState(
    val isServiceConnected: Boolean,
    val isProjectionGranted: Boolean,
    val showMediaProjectionCard: Boolean,
    val restrictedCardPhase: RestrictedCardPhase,
    @StringRes val preFlightHintRes: Int,
    val isCapturing: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        fun create(
            weavingState: WeavingState,
            isProjectionGranted: Boolean,
            hasAttemptedEnable: Boolean,
            hasEncounteredRestriction: Boolean,
            isRestrictedBlocked: Boolean,
            preFlightHintRes: Int
        ): MainUiState {
            val isConnected = (weavingState as? WeavingState.Idle)?.isServiceConnected
                ?: (weavingState is WeavingState.Weaving)

            val phase = when {
                isConnected -> RestrictedCardPhase.HIDDEN
                !hasAttemptedEnable -> RestrictedCardPhase.HIDDEN
                isRestrictedBlocked -> RestrictedCardPhase.BLOCKED_GUIDE
                hasEncounteredRestriction && !isRestrictedBlocked -> RestrictedCardPhase.ALLOWED_READY_RETURN
                else -> RestrictedCardPhase.HIDDEN
            }

            return MainUiState(
                isServiceConnected = isConnected,
                isProjectionGranted = isProjectionGranted,
                showMediaProjectionCard = (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) && !isProjectionGranted,
                restrictedCardPhase = phase,
                preFlightHintRes = preFlightHintRes,
                isCapturing = weavingState is WeavingState.Weaving,
                errorMessage = (weavingState as? WeavingState.Error)?.message
            )
        }
    }
}
