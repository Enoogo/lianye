package org.lianye.ui.floating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.lianye.R
import org.lianye.domain.model.WeavingState
import org.lianye.domain.model.ManualCaptureState
import org.lianye.domain.model.ManualGuide
import org.lianye.domain.model.ManualPhase
import org.lianye.ui.common.uiText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LianyeFloatingBubble(
    state: WeavingState,
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onCloseRequest: () -> Unit = {},
    hasDraft: Boolean = false,
    isStopping: Boolean = false,
    manualSession: ManualCaptureState? = null
) {
    val colors = MaterialTheme.colorScheme
    val capturing = if (manualSession != null) manualSession.phase in listOf(ManualPhase.TAKING_FIRST, ManualPhase.RECORDING, ManualPhase.GAP, ManualPhase.FINISHING)
        else state is WeavingState.Weaving
    val finishing = isStopping || manualSession?.phase == ManualPhase.FINISHING
    // Idle: one round icon only. Tap starts a capture, long-press retires the control.
    val idle = !capturing && manualSession?.phase != ManualPhase.FIRST_FAILED &&
        manualSession?.message != "temporarily_unmatched"
    if (idle) {
        val openDraft = manualSession == null && hasDraft
        // Tap starts a capture, long-press retires the control. The halo is drawn as a
        // circle instead of Surface elevation: windowed elevation shadows render as a
        // translucent square on some devices.
        Box(
            modifier = modifier
                .size(56.dp)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // No ripple: the windowed ripple is drawn as a rectangle and reads as
                    // a square shadow around the round icon.
                    indication = null,
                    onClick = { if (openDraft) onPreviewClick() else onStartClick() },
                    onLongClick = onCloseRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(colors.scrim.copy(alpha = 0.14f), CircleShape)
            )
            Box(
                Modifier
                    .size(52.dp)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (openDraft) R.drawable.ic_bubble_finished else R.drawable.ic_main_header_shuttle),
                    uiText(if (openDraft) "继续编辑长图" else "开始长截图", if (openDraft) "Continue draft" else "Start long screenshot"),
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        return
    }
    if (capturing) {
        Surface(modifier = modifier, shape = RoundedCornerShape(20.dp),
            color = colors.primary, contentColor = colors.onPrimary, shadowElevation = 3.dp) {
            Column {
                val failure = when {
                    manualSession?.message == "temporarily_unmatched" ->
                        uiText("未接上", "Not joined") to uiText("向下滑回一点，稍停", "Swipe down a little; pause")
                    manualSession?.phase == ManualPhase.GAP ->
                        uiText("未接上", "Not joined") to uiText("已保留前段", "Previous content kept")
                    else -> null
                }
                if (failure != null && !finishing) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(failure.first, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        // Recovery instructions live beside the on-screen gesture endpoints.
                        if (manualSession?.guide != ManualGuide.RECOVERY)
                            Text(failure.second, fontSize = 12.sp, color = colors.onPrimary.copy(alpha = 0.8f))
                    }
                }
                TextButton(onClick = onStopClick, enabled = !finishing,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimary,
                        disabledContentColor = colors.onPrimary.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 18.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Box(Modifier.size(7.dp).background(colors.onPrimary, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(9.dp))
                    Text(uiText("结束", "Finish"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = colors.surface,
            contentColor = colors.onSurface, border = BorderStroke(0.5.dp, colors.outlineVariant), shadowElevation = 3.dp) {
            val openDraft = manualSession == null && hasDraft
            Column {
                if (manualSession?.phase == ManualPhase.FIRST_FAILED) {
                    Text(uiText("未截到画面", "No image captured"), fontSize = 12.sp,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp), color = colors.onSurfaceVariant)
                }
                TextButton(onClick = if (openDraft) onPreviewClick else onStartClick,
                    enabled = manualSession == null || manualSession.phase in listOf(ManualPhase.READY, ManualPhase.FIRST_FAILED),
                    modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
                    Icon(painterResource(if (openDraft) R.drawable.ic_bubble_finished else R.drawable.ic_bubble_idle), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (openDraft) uiText("继续编辑", "Continue draft") else if (manualSession?.phase == ManualPhase.FIRST_FAILED || state is WeavingState.Error) uiText("重新截图", "Try again") else uiText("开始截图", "Capture"),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
