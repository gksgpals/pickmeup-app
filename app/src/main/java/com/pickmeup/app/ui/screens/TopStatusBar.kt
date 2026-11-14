package com.pickmeup.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hail
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.ui.UiState
import com.pickmeup.app.ui.components.ButtonTone
import com.pickmeup.app.ui.components.LinkPill
import com.pickmeup.app.ui.components.SelectChip
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.core.domain.PickupPhase

private data class Step(val label: String)

private val STEPS = listOf(Step("입력"), Step("확인"), Step("대기"), Step("승차"), Step("완료"))

private fun stepIndex(phase: PickupPhase): Int = when (phase) {
    PickupPhase.IDLE -> 0
    PickupPhase.PLANNING -> 1
    PickupPhase.TO_WAITING, PickupPhase.WAITING -> 2
    PickupPhase.TO_BOARDING, PickupPhase.BOARDING -> 3
    PickupPhase.COMPLETED -> 4
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopStatusBar(
    state: UiState,
    widthDp: Int,
    onCancel: () -> Unit,
    onToggleDemo: () -> Unit,
) {
    val active = state.phase != PickupPhase.IDLE && state.phase != PickupPhase.COMPLETED
    val wide = widthDp >= 1000
    val roomy = widthDp >= 1600
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pmu.Surface)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        val pills: @Composable () -> Unit = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkPill("차량 센서", state.links.vehicle, Icons.Filled.DirectionsCar, simulated = state.brakeSimulated || state.doorSimulated, compact = !roomy)
                LinkPill("내비", state.links.navi, Icons.Filled.Navigation, compact = !roomy)
                LinkPill("음성", speechStatus(state), Icons.Filled.RecordVoiceOver, compact = !roomy)
            }
        }
        val actions: @Composable () -> Unit = {
            if (active) {
                TopAction(if (roomy) "픽업 취소" else "취소", Icons.Filled.Close, ButtonTone.Danger, onCancel)
                Spacer(Modifier.width(10.dp))
            }
            SelectChip(text = "시연", selected = state.demoPanelOpen, onClick = onToggleDemo, icon = Icons.Filled.Tune)
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Spacer(Modifier.width(24.dp))
                Stepper(state, Modifier.weight(1f), labels = roomy)
                Spacer(Modifier.width(16.dp))
                pills()
                Spacer(Modifier.width(12.dp))
                actions()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Spacer(Modifier.weight(1f))
                pills()
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(state, Modifier.weight(1f), labels = false)
                Spacer(Modifier.width(12.dp))
                actions()
            }
        }
    }
}

private fun speechStatus(state: UiState): LinkStatus {
    // 음성 입력·출력 중 하나라도 연결되면 연결로 표시
    val s = state.links.speech
    val v = state.links.voice
    return when {
        s == LinkStatus.CONNECTED || v == LinkStatus.CONNECTED -> LinkStatus.CONNECTED
        s == LinkStatus.CONNECTING || v == LinkStatus.CONNECTING -> LinkStatus.CONNECTING
        else -> LinkStatus.UNAVAILABLE
    }
}

@Composable
private fun TopAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: ButtonTone, onClick: () -> Unit) {
    com.pickmeup.app.ui.components.BigButton(
        text = text,
        onClick = onClick,
        icon = icon,
        tone = tone,
        minHeight = 76.dp,
    )
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Pmu.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Hail, null, tint = Pmu.OnAccent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("픽미업", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Pmu.Text)
            Text("Pick me up", style = MaterialTheme.typography.labelSmall, color = Pmu.TextMuted)
        }
    }
}

@Composable
private fun Stepper(state: UiState, modifier: Modifier = Modifier, labels: Boolean = true) {
    val current = stepIndex(state.phase)
    val skippedWaiting = state.waitingSkipped ||
        (current >= 3 && state.pickup.waitingSpot == null)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        STEPS.forEachIndexed { i, step ->
            val done = i < current || state.phase == PickupPhase.COMPLETED
            val now = i == current && state.phase != PickupPhase.COMPLETED
            val skipped = i == 2 && skippedWaiting && current > 2
            val color = when {
                now -> Pmu.Accent
                skipped -> Pmu.TextFaint
                done -> Pmu.Accent.copy(alpha = 0.75f)
                else -> Pmu.TextFaint
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (now) Pmu.Accent else if (done && !skipped) Pmu.AccentSoft else Pmu.Card)
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done && !skipped) {
                        Icon(Icons.Filled.Check, null, tint = Pmu.Accent, modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (now) Pmu.OnAccent else color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (labels || now) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (skipped) "${step.label}(생략)" else step.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (now) Pmu.Text else color,
                        fontWeight = if (now) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            if (i < STEPS.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < current) Pmu.Accent.copy(alpha = 0.6f) else Pmu.OutlineSoft),
                )
            }
        }
    }
}
