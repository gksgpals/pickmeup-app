package com.pickmeup.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hail
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.material.icons.filled.SwitchLeft
import androidx.compose.material.icons.filled.SwitchRight
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.NotifyResult
import com.pickmeup.app.ui.NotifyKind
import com.pickmeup.app.ui.NotifyUi
import com.pickmeup.app.ui.PickupViewModel
import com.pickmeup.app.ui.UiState
import com.pickmeup.app.ui.components.BigButton
import com.pickmeup.app.ui.components.ButtonTone
import com.pickmeup.app.ui.components.InfoLine
import com.pickmeup.app.ui.components.PmuCard
import com.pickmeup.app.ui.components.Tag
import com.pickmeup.app.ui.formatClock
import com.pickmeup.app.ui.freeWaitLabel
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.app.ui.walkLabel
import com.pickmeup.core.model.PickupSpot
import com.pickmeup.core.model.RoadSide

// ───────────────────────────── 공통 레이아웃 ─────────────────────────────

/** 넓은 화면: 좌(주 정보) · 우(상태·행동). 좁은 화면: 위아래로 쌓고 행동 버튼은 하단 고정. */
@Composable
private fun TwoPane(
    wide: Boolean,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = left,
            )
            Column(Modifier.weight(0.45f).fillMaxHeight()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = right,
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = actions)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                left()
                right()
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}

@Composable
private fun PhaseHeader(icon: ImageVector, label: String, title: String, notice: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Pmu.AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Pmu.Accent, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Pmu.Accent)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Pmu.Text)
        }
    }
    if (!notice.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(notice, style = MaterialTheme.typography.bodyLarge, color = Pmu.TextMuted)
    }
}

/** 내비 목적지 카드. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DestinationCard(spot: PickupSpot, kindLabel: String, naviEvent: String?, naviStatus: LinkStatus, showMeeting: Boolean) {
    PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Navigation, null, tint = Pmu.Accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(kindLabel, style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted, modifier = Modifier.weight(1f))
            Tag(
                when (naviStatus) {
                    LinkStatus.CONNECTED -> "Pleos 내비 안내 중"
                    LinkStatus.CONNECTING -> "내비 연결 중"
                    LinkStatus.UNAVAILABLE -> "내비 미연결 · 화면 안내"
                },
                if (naviStatus == LinkStatus.CONNECTED) Pmu.Smooth else Pmu.Unknown,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(spot.name, style = MaterialTheme.typography.displaySmall, color = Pmu.Text)
        Spacer(Modifier.height(12.dp))
        if (showMeeting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = Pmu.Accent, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text(spot.meetingSign, style = MaterialTheme.typography.headlineSmall, color = Pmu.Accent)
            }
            Spacer(Modifier.height(10.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InfoLine(if (spot.roadSide == RoadSide.RIGHT) Icons.Filled.SwitchRight else Icons.Filled.SwitchLeft, spot.roadSide.label, color = Pmu.Text)
            InfoLine(Icons.Filled.LocalParking, spot.type.label, color = Pmu.Text)
        }
        InfoLine(Icons.AutoMirrored.Filled.AltRoute, spot.approach, color = Pmu.Text)
        if (!naviEvent.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(naviEvent, style = MaterialTheme.typography.bodySmall, color = Pmu.TextFaint)
        }
    }
}

@Composable
private fun SensorCard(icon: ImageVector, title: String, status: LinkStatus, simulated: Boolean, value: String?, hint: String) {
    PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Pmu.Accent, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Pmu.Text)
                if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = Pmu.TextMuted)
            }
            val (text, color) = when {
                simulated -> "시연 신호" to Pmu.Attention
                status == LinkStatus.CONNECTED -> "센서 연결됨" to Pmu.Smooth
                status == LinkStatus.CONNECTING -> "센서 연결 중" to Pmu.Attention
                else -> "수동 확인" to Pmu.Unknown
            }
            Tag(text, color, icon = Icons.Filled.Sensors)
        }
        Spacer(Modifier.height(10.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Pmu.TextMuted)
    }
}

// ───────────────────────────── TO_WAITING ─────────────────────────────

@Composable
fun ToWaitingScreen(state: UiState, vm: PickupViewModel, wide: Boolean) {
    val spot = state.pickup.waitingSpot ?: return MissingSpot(vm)
    TwoPane(
        wide = wide,
        left = {
            PhaseHeader(Icons.Filled.LocalParking, "1단계 · 대기", if (state.arrivedAtWaiting) "대기 장소 도착" else "대기 장소로 이동 중", state.pickup.notice)
            DestinationCard(spot, "대기 장소", state.naviEvent, state.links.navi, showMeeting = false)
        },
        right = {
            PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(freeWaitLabel(spot), if (spot.freeWaitMinutes != 0) Pmu.Smooth else Pmu.Unknown, filled = spot.freeWaitMinutes != 0)
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Filled.HourglassTop, null, tint = Pmu.TextMuted, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "동승자 준비까지 ${formatClock(state.remainingSec ?: 0)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Pmu.Text,
                    )
                }
            }
            SensorCard(
                icon = Icons.Filled.LocalParking,
                title = "주차 브레이크(EPB)",
                status = state.links.vehicle,
                simulated = state.brakeSimulated,
                value = when (state.parkingBrakeOn) { true -> "체결됨"; false -> "해제됨"; null -> "신호 없음" },
                hint = "도착 후 주차 브레이크를 체결하면 자동으로 대기 모드로 바뀝니다. 센서가 없으면 아래 버튼을 눌러 주세요.",
            )
        },
        actions = {
            if (!state.pickup.arrivedAtWaiting) BigButton(
                "대기 장소 도착 확인(수동)",
                onClick = vm::manualArrivedAtWaiting,
                icon = Icons.Filled.Flag,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.pickup.arrivedAtWaiting) BigButton(
                "주차 브레이크 체결 확인(수동)",
                onClick = vm::manualParked,
                icon = Icons.Filled.TouchApp,
                subtitle = "대기 모드 시작 · 동승자에게 위치 알림",
                modifier = Modifier.fillMaxWidth(),
                minHeight = 100.dp,
            )
            BigButton(
                "동승자가 벌써 나왔어요",
                onClick = { vm.passengerReady(true) },
                tone = ButtonTone.Quiet,
                icon = Icons.Filled.Hail,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ───────────────────────────── WAITING ─────────────────────────────

@Composable
fun WaitingScreen(state: UiState, vm: PickupViewModel, wide: Boolean, onChangeSpot: () -> Unit) {
    val boarding = state.pickup.boardingSpot
    TwoPane(
        wide = wide,
        left = {
            PhaseHeader(Icons.Filled.HourglassTop, "1단계 · 대기 모드", "동승자를 기다리는 중", state.pickup.notice)
            PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountdownRing(
                        remaining = state.remainingSec ?: 0,
                        total = state.countdownTotalSec ?: 1,
                        size = if (wide) 260.dp else 220.dp,
                    )
                    Spacer(Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text("승차 지점까지", style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted)
                        Text("약 ${state.travelMinutesToBoarding ?: 1}분", style = MaterialTheme.typography.displayMedium, color = Pmu.Text)
                        Spacer(Modifier.height(8.dp))
                        state.pickup.waitingSpot?.let {
                            InfoLine(Icons.Filled.LocalParking, "현재 ${it.name}", color = Pmu.TextMuted)
                        }
                        Text(
                            "카운트다운이 끝나면 승차 지점으로 자동 안내합니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Pmu.TextFaint,
                        )
                    }
                }
            }
            if (boarding != null) {
                PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("승차 지점", style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted)
                            Text(boarding.name, style = MaterialTheme.typography.headlineSmall, color = Pmu.Text)
                            InfoLine(Icons.Filled.Place, boarding.meetingSign, color = Pmu.Accent, iconTint = Pmu.Accent)
                            InfoLine(if (boarding.roadSide == RoadSide.RIGHT) Icons.Filled.SwitchRight else Icons.Filled.SwitchLeft, "${boarding.roadSide.label} · ${walkLabel(boarding.walkMeters)}")
                        }
                        Spacer(Modifier.width(12.dp))
                        BigButton("변경", onClick = onChangeSpot, tone = ButtonTone.Quiet, icon = Icons.Filled.EditLocationAlt, minHeight = 76.dp)
                    }
                }
            }
        },
        right = {
            NotifyCard(state.notify, parked = true, onResend = vm::resendMessage)
        },
        actions = {
            BigButton(
                "동승자 준비 완료",
                subtitle = "승차 지점으로 안내 전환",
                onClick = { vm.passengerReady(true) },
                icon = Icons.Filled.Hail,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 104.dp,
            )
        },
    )
}

@Composable
private fun CountdownRing(remaining: Int, total: Int, size: Dp) {
    val progress = if (total <= 0) 0f else (remaining.toFloat() / total).coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Pmu.OutlineSoft, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                if (remaining <= 60) Pmu.Attention else Pmu.Accent,
                -90f,
                360f * progress,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("동승자 준비까지", style = MaterialTheme.typography.titleSmall, color = Pmu.TextMuted)
            Text(formatClock(remaining), style = MaterialTheme.typography.displayMedium, color = Pmu.Text, fontWeight = FontWeight.Bold)
        }
    }
}

/** 동승자 알림 결과 카드. 폴백이면 정차 중 직접 전달할 수 있게 문구를 크게 보여 줍니다. */
@Composable
fun NotifyCard(notify: NotifyUi?, parked: Boolean, onResend: () -> Unit) {
    if (notify == null) return
    val result = notify.result
    val fallback = result as? NotifyResult.Fallback
    val prefilled = result is NotifyResult.Sent && PickupViewModel.isPrefilledSmsApp(result)
    PmuCard(
        Modifier.fillMaxWidth(),
        accent = if (fallback != null) Pmu.Attention else null,
        padding = PaddingValues(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                notify.sending -> CircularProgressIndicator(color = Pmu.Accent, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                fallback != null -> Icon(Icons.Filled.SmsFailed, null, tint = Pmu.Attention, modifier = Modifier.size(36.dp))
                else -> Icon(Icons.Filled.Sms, null, tint = Pmu.Smooth, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        notify.sending -> "동승자에게 알리는 중…"
                        fallback != null -> "문자를 보내지 못했어요"
                        prefilled -> "문자 앱에 합류 안내를 채워 두었어요"
                        result is NotifyResult.Sent -> "동승자에게 보냈어요"
                        else -> "동승자 알림"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Pmu.Text,
                )
                val sub = when {
                    fallback != null -> fallback.reason
                    prefilled -> "정차 중에 문자 앱에서 전송을 눌러 주세요"
                    result is NotifyResult.Sent -> "${result.channel} · ${if (notify.kind == NotifyKind.WAITING) "대기 안내" else "합류 위치·도착 예정"}"
                    else -> null
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Pmu.TextMuted)
            }
        }
        if (fallback != null && parked) {
            Spacer(Modifier.height(16.dp))
            Text("정차 중 동승자에게 이 문구를 전해 주세요", style = MaterialTheme.typography.titleMedium, color = Pmu.Attention)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pmu.Bg)
                    .padding(22.dp),
            ) {
                Text(fallback.message.ifBlank { notify.message }, style = MaterialTheme.typography.headlineSmall, color = Pmu.Text)
            }
            Spacer(Modifier.height(14.dp))
            BigButton("다시 보내기", onClick = onResend, tone = ButtonTone.Quiet, icon = Icons.Filled.Refresh, minHeight = 76.dp, modifier = Modifier.fillMaxWidth())
        } else if (fallback != null) {
            Spacer(Modifier.height(8.dp))
            Text("정차하면 안내 문구를 크게 보여 드립니다", style = MaterialTheme.typography.bodyMedium, color = Pmu.TextFaint)
        } else if (!notify.sending && parked) {
            Spacer(Modifier.height(12.dp))
            Text("“${notify.message}”", style = MaterialTheme.typography.bodyMedium, color = Pmu.TextMuted)
        }
    }
}

// ───────────────────────────── TO_BOARDING ─────────────────────────────

@Composable
fun ToBoardingScreen(state: UiState, vm: PickupViewModel, wide: Boolean, onChangeSpot: () -> Unit) {
    val spot = state.pickup.boardingSpot ?: return MissingSpot(vm)
    TwoPane(
        wide = wide,
        left = {
            PhaseHeader(Icons.Filled.DirectionsCar, "2단계 · 전환", "승차 지점으로 이동 중", state.pickup.notice)
            DestinationCard(spot, "승차 지점", state.naviEvent, state.links.navi, showMeeting = true)
        },
        right = {
            NotifyCard(state.notify, parked = false, onResend = vm::resendMessage)
        },
        actions = {
            BigButton(
                "도착(수동)",
                subtitle = "내비 안내가 끝나면 자동으로 넘어갑니다",
                onClick = vm::arrivedAtBoarding,
                icon = Icons.Filled.Flag,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 104.dp,
            )
            BigButton(
                "다른 승차 지점",
                onClick = onChangeSpot,
                tone = ButtonTone.Quiet,
                icon = Icons.Filled.EditLocationAlt,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ───────────────────────────── BOARDING ─────────────────────────────

@Composable
fun BoardingScreen(state: UiState, vm: PickupViewModel, wide: Boolean, onChangeSpot: () -> Unit) {
    val spot = state.pickup.boardingSpot
    TwoPane(
        wide = wide,
        left = {
            PhaseHeader(Icons.Filled.SensorDoor, "3단계 · 완료", "동승자 탑승 대기", state.pickup.notice)
            if (spot != null) {
                PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(26.dp)) {
                    Text("만나는 곳", style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, null, tint = Pmu.Accent, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(spot.meetingSign, style = MaterialTheme.typography.headlineMedium, color = Pmu.Accent)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(spot.name, style = MaterialTheme.typography.titleLarge, color = Pmu.Text)
                }
            }
            SensorCard(
                icon = Icons.Filled.SensorDoor,
                title = "도어 개폐 감지",
                status = state.links.vehicle,
                simulated = state.doorSimulated,
                value = when (state.anyDoorOpen) { true -> "도어 열림"; false -> "도어 닫힘"; null -> "신호 없음" },
                hint = "앞 동승자석 또는 뒷문이 열렸다 닫히면 탑승 확인을 요청합니다. 센서가 없으면 직접 확인해 주세요.",
            )
        },
        right = {
            NotifyCard(state.notify, parked = true, onResend = vm::resendMessage)
        },
        actions = {
            if (!state.pickup.boardingConfirmation) BigButton(
                "탑승 여부 직접 확인",
                onClick = vm::requestManualBoarding,
                icon = Icons.Filled.TouchApp,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.pickup.boardingConfirmation) BigButton(
                "동승자 탑승 완료 확정",
                subtitle = if (state.pickup.boardingDetected) "도어 개폐가 감지되었습니다. 탑승을 확인해 주세요." else "동승자 탑승을 직접 확인해 주세요.",
                onClick = vm::boarded,
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 104.dp,
            )
            BigButton(
                "이 지점을 쓸 수 없어요",
                subtitle = "대체 승차 지점으로 변경",
                onClick = onChangeSpot,
                tone = ButtonTone.Quiet,
                icon = Icons.Filled.EditLocationAlt,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ───────────────────────────── COMPLETED ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompletedScreen(state: UiState, vm: PickupViewModel) {
    val req = state.pickup.request
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(132.dp).clip(CircleShape).background(Pmu.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, null, tint = Pmu.Accent, modifier = Modifier.size(96.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("픽업 완료", style = MaterialTheme.typography.displaySmall, color = Pmu.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                state.pickup.notice ?: "동승자 탑승을 확인했습니다",
                style = MaterialTheme.typography.bodyLarge,
                color = Pmu.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PmuCard(Modifier.fillMaxWidth()) {
                SummaryRow("거점", listOfNotNull(req?.hub?.name, req?.entrance?.name).joinToString(" "))
                SummaryRow("대기 장소", state.pickup.waitingSpot?.name ?: "대기 없이 바로 승차")
                SummaryRow("승차 지점", state.pickup.boardingSpot?.let { "${it.name} · ${it.meetingSign}" } ?: "-")
                SummaryRow(
                    "탑승 확인",
                    when (state.completedBySensor) { true -> "도어 개폐 감지 후 운전자 확인"; false -> "운전자 확인"; null -> "-" },
                )
                SummaryRow(
                    "동승자 알림",
                    when (val r = state.notify?.result) {
                        is NotifyResult.Sent -> if (PickupViewModel.isPrefilledSmsApp(r)) "문자 앱에 채워 둠" else r.channel
                        is NotifyResult.Fallback -> "화면 안내(${r.reason})"
                        null -> "-"
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
            BigButton("처음으로", onClick = vm::restart, icon = Icons.AutoMirrored.Filled.ArrowForward, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted, modifier = Modifier.width(160.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Pmu.Text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MissingSpot(vm: PickupViewModel) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("안내할 지점 정보가 없습니다", style = MaterialTheme.typography.headlineMedium, color = Pmu.Text)
            Spacer(Modifier.height(20.dp))
            BigButton("처음으로", onClick = vm::restart)
        }
    }
}
