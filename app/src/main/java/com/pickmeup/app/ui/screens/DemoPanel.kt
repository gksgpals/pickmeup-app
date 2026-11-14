package com.pickmeup.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hail
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pickmeup.app.ui.DemoClock
import com.pickmeup.app.ui.PickupViewModel
import com.pickmeup.app.ui.TrafficLink
import com.pickmeup.app.ui.UiState
import com.pickmeup.app.ui.components.SelectChip
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.app.ui.theme.trafficColor
import com.pickmeup.core.domain.PickupPhase
import com.pickmeup.core.model.TrafficLevel

/**
 * 시연 패널(사이드 시트). SDK가 없어도 같은 ViewModel 경로(센서 값 덮어쓰기·이벤트)로 흐름을 재현합니다.
 */
@Composable
fun DemoPanel(state: UiState, vm: PickupViewModel, width: Dp) {
    Surface(
        color = Pmu.Surface,
        border = BorderStroke(1.dp, Pmu.Outline),
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        modifier = Modifier.width(width).fillMaxHeight(),
        shadowElevation = 16.dp,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = Pmu.Attention, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("시연 패널", style = MaterialTheme.typography.headlineSmall, color = Pmu.Text)
                    Text("센서·내비·교통 신호를 흉내 냅니다", style = MaterialTheme.typography.bodySmall, color = Pmu.TextMuted)
                }
                SelectChip("닫기", selected = false, onClick = vm::closeDemoPanel, icon = Icons.Filled.Close)
            }

            Group("차량 신호 · 현재 단계: ${phaseName(state.phase)}") {
                DemoButton(
                    Icons.Filled.LocalParking,
                    if (state.parkingBrakeOn == true) "주차 브레이크 해제" else "주차 브레이크 체결",
                    "대기 장소 이동 중 체결 → 대기 모드",
                    active = state.phase == PickupPhase.TO_WAITING,
                    onClick = vm::demoToggleBrake,
                )
                DemoButton(
                    Icons.Filled.SensorDoor,
                    "도어 열림",
                    "승차 지점 도착 후 → 탑승 완료",
                    active = state.phase == PickupPhase.BOARDING,
                    onClick = vm::demoDoorPulse,
                )
                DemoButton(
                    Icons.Filled.Flag,
                    "내비 목적지 도착",
                    "이동 중 단계에서 도착 처리",
                    active = state.phase == PickupPhase.TO_WAITING || state.phase == PickupPhase.TO_BOARDING,
                    onClick = vm::demoNaviArrived,
                )
            }

            Group("동승자") {
                DemoButton(
                    Icons.Filled.Hail,
                    "동승자 준비 완료 신호",
                    "대기 중 → 승차 지점으로 경로 전환",
                    active = state.phase == PickupPhase.WAITING || state.phase == PickupPhase.TO_WAITING,
                    onClick = vm::demoPassengerReady,
                )
                DemoButton(
                    Icons.Filled.FastForward,
                    "카운트다운 1분 앞당기기",
                    "0이 되면 자동으로 승차 지점 안내",
                    active = state.readyAtMillis != null && state.phase != PickupPhase.IDLE,
                    onClick = vm::demoFastForward,
                )
            }

            Group("접근 도로 교통 (눌러서 원활→서행→정체→정보 없음)") {
                if (state.trafficLinks.isEmpty()) {
                    Text("픽업 정보를 입력하면 후보 지점의 접근 도로가 표시됩니다", style = MaterialTheme.typography.bodySmall, color = Pmu.TextFaint)
                }
                state.trafficLinks.forEach { link -> TrafficRow(link) { vm.demoCycleTraffic(link.linkId) } }
                val alleys = (state.ranked + state.waitAdvice?.waitingCandidates.orEmpty())
                    .filter { it.spot.approachLinkId == null }.map { it.spot.name }.distinct()
                if (alleys.isNotEmpty()) {
                    Text(
                        "골목(교통정보 연결 없음): ${alleys.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Pmu.TextFaint,
                    )
                }
            }

            Group("추천 기준 시각") {
                ClockChips(state.clock, vm::demoSetClock)
            }

            Group("연동 상태") {
                Text("내비: ${state.naviEvent ?: "이벤트 없음"}", style = MaterialTheme.typography.bodySmall, color = Pmu.TextMuted)
                Text(
                    "EPB: ${state.parkingBrakeOn ?: "모름"}${if (state.brakeSimulated) " (시연)" else ""} · 도어: ${state.anyDoorOpen ?: "모름"}${if (state.doorSimulated) " (시연)" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pmu.TextMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockChips(current: DemoClock, onSelect: (DemoClock) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoClock.entries.forEach { c ->
            SelectChip(c.label, selected = c == current, onClick = { onSelect(c) }, icon = Icons.Filled.Schedule)
        }
    }
}

private fun phaseName(p: PickupPhase): String = when (p) {
    PickupPhase.IDLE -> "입력 대기"
    PickupPhase.PLANNING -> "확인"
    PickupPhase.TO_WAITING -> "대기 장소 이동"
    PickupPhase.WAITING -> "대기 모드"
    PickupPhase.TO_BOARDING -> "승차 지점 이동"
    PickupPhase.BOARDING -> "탑승 대기"
    PickupPhase.COMPLETED -> "완료"
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Pmu.TextMuted)
        content()
    }
}

@Composable
private fun DemoButton(icon: ImageVector, title: String, hint: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) Pmu.CardHigh else Pmu.Card,
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Pmu.Attention else Pmu.OutlineSoft),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) Pmu.Attention else Pmu.TextMuted, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (active) Pmu.Text else Pmu.TextMuted)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Pmu.TextFaint)
            }
        }
    }
}

@Composable
private fun TrafficRow(link: TrafficLink, onClick: () -> Unit) {
    val color = trafficColor(link.level)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Pmu.Card,
        border = BorderStroke(2.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Traffic, null, tint = color, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(link.linkId, style = MaterialTheme.typography.titleSmall, color = Pmu.Text, fontWeight = FontWeight.Bold)
                Text(link.spotNames.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Pmu.TextMuted, maxLines = 2)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (link.level == TrafficLevel.UNKNOWN) "정보 없음" else link.level.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
