package com.pickmeup.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Hail
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pickmeup.app.ui.PickupViewModel
import com.pickmeup.app.ui.UiState
import com.pickmeup.app.ui.components.BigButton
import com.pickmeup.app.ui.components.ButtonTone
import com.pickmeup.app.ui.components.BoardingSpotCard
import com.pickmeup.app.ui.components.InfoLine
import com.pickmeup.app.ui.components.PmuCard
import com.pickmeup.app.ui.components.SectionTitle
import com.pickmeup.app.ui.components.SelectChip
import com.pickmeup.app.ui.components.WaitingSpotCard
import com.pickmeup.app.ui.readyLabel
import com.pickmeup.app.ui.theme.Pmu

/** 픽업 정보 확인 · 승차 지점 후보 선택(PLANNING). */
@Composable
fun PlanningScreen(state: UiState, vm: PickupViewModel, wide: Boolean, onMic: () -> Unit) {
    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                RequestSummary(state, vm, onMic)
            }
            Column(Modifier.weight(0.62f).fillMaxHeight()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Recommendations(state, vm)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryActions(state, vm)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RequestSummary(state, vm, onMic)
                Recommendations(state, vm)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { PrimaryActions(state, vm) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestSummary(state: UiState, vm: PickupViewModel, onMic: () -> Unit) {
    val d = state.draft
    val hub = state.selectedHub
    PmuCard(Modifier.fillMaxWidth()) {
        SectionTitle("픽업 정보", icon = Icons.Filled.Hail, trailing = {
            SelectChip("다시 말하기", selected = false, onClick = onMic, icon = Icons.Filled.Mic)
        })
        if (d.rawUtterance != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.FormatQuote, null, tint = Pmu.TextFaint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(d.rawUtterance, style = MaterialTheme.typography.bodyLarge, color = Pmu.TextMuted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            val candidates = d.hubCandidateIds.mapNotNull { id -> state.hubs.firstOrNull { it.id == id } }
            if (candidates.size > 1) {
                StepBlock("!", "거점 후보가 여러 곳입니다 · 선택해 주세요") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        candidates.forEach { h ->
                            SelectChip(h.name, selected = h.id == d.hubId, onClick = { vm.selectHub(h.id) }, icon = hubIcon(h))
                        }
                    }
                }
            } else {
                StepBlock("1", "거점") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.hubs.forEach { h ->
                            SelectChip(h.name, selected = h.id == d.hubId, onClick = { vm.selectHub(h.id) }, icon = hubIcon(h))
                        }
                    }
                }
            }
            if (hub != null) {
                StepBlock("2", "출입구") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hub.entrances.forEach { e ->
                            SelectChip(e.name, selected = e.id == d.entranceId, onClick = { vm.selectEntrance(e.id) })
                        }
                        SelectChip("잘 모름", selected = d.entranceId == null, onClick = { vm.selectEntrance(null) })
                    }
                }
            }
            StepBlock("3", "준비 시간 · ${readyLabel(d.readyInMinutes)}") {
                if (d.readyInMinutes == null) {
                    InfoLine(
                        Icons.Filled.WarningAmber,
                        "준비 시간을 알아듣지 못해 ${PickupViewModel.DEFAULT_READY_MINUTES}분으로 계산했어요",
                        color = Pmu.Attention,
                        iconTint = Pmu.Attention,
                    )
                }
                ReadyChips(d.readyInMinutes, vm::selectReady)
            }
            StepBlock("4", "동승자 · ${d.passengerLabel ?: "미지정"}") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val labels = (PickupViewModel.PASSENGER_LABELS + listOfNotNull(d.passengerLabel)).distinct()
                    labels.forEach { l ->
                        SelectChip(l, selected = d.passengerLabel == l, onClick = {
                            vm.selectPassengerLabel(if (d.passengerLabel == l) null else l)
                        })
                    }
                }
                NeedsChips(state, vm)
            }
        }
    }
}

@Composable
private fun ColumnScope.Recommendations(state: UiState, vm: PickupViewModel) {
    val advice = state.waitAdvice
    if (advice != null && advice.shouldWait && advice.waitingCandidates.isNotEmpty()) {
        PmuCard(Modifier.fillMaxWidth(), accent = Pmu.Attention.copy(alpha = 0.55f), padding = PaddingValues(22.dp)) {
            SectionTitle("먼저 대기 장소에서 기다리세요", icon = Icons.Filled.LocalParking)
            Spacer(Modifier.height(8.dp))
            Text(advice.message, style = MaterialTheme.typography.bodyMedium, color = Pmu.TextMuted)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                advice.waitingCandidates.forEach { r ->
                    WaitingSpotCard(r, selected = r.spot.id == state.selectedWaitingId, onClick = { vm.selectWaiting(r.spot.id) })
                }
            }
        }
    } else if (advice != null && advice.message.isNotBlank()) {
        PmuCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
            InfoLine(Icons.Filled.Info, advice.message, color = Pmu.Text, iconTint = Pmu.Accent)
        }
    }

    SectionTitle(
        if (advice?.shouldWait == true) "승차 지점 · 동승자 준비 후 이동" else "승차 지점 추천",
        icon = Icons.Filled.DirectionsCar,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (state.ranked.any { it.reordered }) {
        PmuCard(Modifier.fillMaxWidth(), color = Pmu.AttentionSoft, accent = Pmu.Attention.copy(alpha = 0.5f), padding = PaddingValues(18.dp)) {
            val promoted = state.ranked.first { it.reordered }
            InfoLine(
                Icons.Filled.SwapVert,
                "접근 도로 소통 상태를 반영해 순위를 조정했습니다 · ${promoted.reasons.firstOrNull() ?: promoted.spot.name}",
                color = Pmu.Attention,
                iconTint = Pmu.Attention,
            )
        }
    }
    if (state.ranked.isEmpty()) {
        PmuCard(Modifier.fillMaxWidth()) {
            InfoLine(
                Icons.Filled.WarningAmber,
                "조건에 맞는 승차 지점이 없습니다. 출입구나 동승자 조건을 바꿔 보세요.",
                color = Pmu.Attention,
                iconTint = Pmu.Attention,
            )
        }
    }
    state.ranked.forEach { r ->
        BoardingSpotCard(r, selected = r.spot.id == state.selectedBoardingId, onClick = { vm.selectBoarding(r.spot.id) })
    }
}

@Composable
private fun PrimaryActions(state: UiState, vm: PickupViewModel) {
    val shouldWait = state.waitAdvice?.let { it.shouldWait && it.waitingCandidates.isNotEmpty() } == true
    val boarding = state.selectedBoarding
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (shouldWait) {
            BigButton(
                text = "바로 승차 지점",
                subtitle = "동승자가 더 빨리 나올 때",
                onClick = vm::startToBoarding,
                enabled = boarding != null,
                tone = ButtonTone.Secondary,
                modifier = Modifier.weight(0.4f),
            )
            BigButton(
                text = "대기 장소로 안내",
                subtitle = state.selectedWaiting?.spot?.name,
                onClick = vm::startToWaiting,
                enabled = boarding != null && state.selectedWaiting != null,
                icon = Icons.Filled.LocalParking,
                modifier = Modifier.weight(0.6f),
            )
        } else {
            BigButton(
                text = "승차 지점으로 바로 안내",
                subtitle = boarding?.spot?.name ?: "승차 지점을 선택하세요",
                onClick = vm::startToBoarding,
                enabled = boarding != null,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
