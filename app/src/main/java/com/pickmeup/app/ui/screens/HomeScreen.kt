package com.pickmeup.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pickmeup.app.ui.DemoPreset
import com.pickmeup.app.ui.PickupViewModel
import com.pickmeup.app.ui.PresetIcon
import com.pickmeup.app.ui.UiState
import com.pickmeup.app.ui.components.BigButton
import com.pickmeup.app.ui.components.PmuCard
import com.pickmeup.app.ui.components.SectionTitle
import com.pickmeup.app.ui.components.SelectChip
import com.pickmeup.app.ui.readyLabel
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.core.model.Hub

/** 입력 대기(IDLE): 마이크 · 시연 시나리오 · 화면 입력. */
@Composable
fun HomeScreen(state: UiState, vm: PickupViewModel, wide: Boolean, onMic: () -> Unit) {
    if (wide) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                VoiceCard(state, onMic, Modifier.fillMaxWidth())
                PresetList(state.presets, vm::runPreset)
            }
            Column(
                Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
            ) {
                TouchInputCard(state, vm, Modifier.fillMaxSize())
            }
        }
    } else {
        val scroll = rememberScrollState()
        var touchTop by remember { mutableIntStateOf(0) }
        LaunchedEffect(state.touchInputOpen) {
            if (state.touchInputOpen) scroll.animateScrollTo(touchTop)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            VoiceCard(state, onMic, Modifier.fillMaxWidth())
            PresetList(state.presets, vm::runPreset)
            TouchInputCard(
                state,
                vm,
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { touchTop = it.positionInParent().y.toInt() },
                scrollable = false,
            )
        }
    }
}

@Composable
private fun VoiceCard(state: UiState, onMic: () -> Unit, modifier: Modifier) {
    PmuCard(modifier = modifier, padding = PaddingValues(28.dp)) {
        Text("누구를 데리러 가세요?", style = MaterialTheme.typography.headlineMedium, color = Pmu.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            "마이크를 누르고 거점·출입구·준비 시간을 말해 주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = Pmu.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                onClick = onMic,
                shape = CircleShape,
                color = if (state.listening) Pmu.Attention else Pmu.Accent,
                contentColor = Pmu.OnAccent,
                modifier = Modifier.size(168.dp),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = "말로 픽업 요청", modifier = Modifier.size(84.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.listening) "듣고 있어요…" else "말로 픽업 요청",
            style = MaterialTheme.typography.headlineSmall,
            color = Pmu.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "예) “한빛상가 동문에서 친구 태울 거야. 10분 정도 걸린대”",
            style = MaterialTheme.typography.bodyMedium,
            color = Pmu.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PresetList(presets: List<DemoPreset>, onRun: (DemoPreset) -> Unit) {
    if (presets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("시연 시나리오 · 한 번 눌러 바로 시작", style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted)
        presets.forEach { p ->
            PmuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                onClick = { onRun(p) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .background(Pmu.AccentSoft, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(presetIcon(p.icon), null, tint = Pmu.Accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, color = Pmu.Text, fontWeight = FontWeight.Bold)
                        Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = Pmu.Accent)
                        Text(
                            "“${p.utterance}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = Pmu.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Pmu.TextMuted, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

private fun presetIcon(icon: PresetIcon): ImageVector = when (icon) {
    PresetIcon.RAIN -> Icons.Filled.Umbrella
    PresetIcon.TRAFFIC -> Icons.Filled.Traffic
    PresetIcon.HOSPITAL -> Icons.Filled.LocalHospital
    PresetIcon.SCHOOL -> Icons.Filled.School
}

fun hubIcon(hub: Hub): ImageVector = when {
    hub.name.contains("병원") -> Icons.Filled.LocalHospital
    hub.name.contains("학원") || hub.name.contains("학교") -> Icons.Filled.School
    hub.name.contains("상가") || hub.name.contains("마트") -> Icons.Filled.Storefront
    else -> Icons.Filled.Apartment
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TouchInputCard(state: UiState, vm: PickupViewModel, modifier: Modifier, scrollable: Boolean = true) {
    val d = state.draft
    val hub = state.selectedHub
    PmuCard(
        modifier = modifier,
        accent = if (state.touchInputOpen) Pmu.Attention else null,
        padding = PaddingValues(0.dp),
    ) {
        Column(
            (if (scrollable) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SectionTitle("화면으로 입력", icon = Icons.Filled.TouchApp)

            StepBlock("1", "픽업 거점") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.hubs.forEach { h -> HubCard(h, selected = h.id == d.hubId) { vm.selectHub(h.id) } }
                }
            }
            if (hub != null) {
                StepBlock("2", "출입구 (동승자가 나오는 곳)") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hub.entrances.forEach { e ->
                            SelectChip(e.name, selected = e.id == d.entranceId, onClick = { vm.selectEntrance(e.id) })
                        }
                        SelectChip("잘 모름", selected = d.entranceId == null, onClick = { vm.selectEntrance(null) })
                    }
                }
            }
            StepBlock("3", "동승자 준비 시간") {
                ReadyChips(d.readyInMinutes, vm::selectReady)
            }
            StepBlock("4", "동승자 조건") {
                NeedsChips(state, vm)
            }
            StepBlock("5", "동승자 연락처 (선택)") {
                PhoneField(state, vm)
            }
        }
        Box(Modifier.padding(start = 26.dp, end = 26.dp, bottom = 26.dp)) {
            BigButton(
                text = "안내 시작",
                subtitle = if (hub == null) "거점을 먼저 선택하세요" else "${hub.name} · ${readyLabel(d.readyInMinutes ?: PickupViewModel.DEFAULT_READY_MINUTES)}",
                onClick = vm::submitTouchInput,
                enabled = hub != null,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun StepBlock(number: String, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(Pmu.CardHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = Pmu.Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted)
        }
        content()
    }
}

@Composable
private fun HubCard(hub: Hub, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) Pmu.AccentSoft else Pmu.CardHigh,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Pmu.Accent else Pmu.Outline),
        modifier = Modifier
            .widthIn(min = 200.dp, max = 300.dp)
            .heightIn(min = 96.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(hubIcon(hub), null, tint = if (selected) Pmu.Accent else Pmu.TextMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(hub.name, style = MaterialTheme.typography.titleLarge, color = Pmu.Text, fontWeight = FontWeight.Bold)
                Text(
                    "출입구 ${hub.entrances.size} · 후보 ${hub.spots.size}곳",
                    style = MaterialTheme.typography.bodySmall,
                    color = Pmu.TextMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadyChips(selected: Int?, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickupViewModel.READY_OPTIONS.forEach { m ->
            SelectChip(
                text = if (m == 0) "지금 나와 있음" else "${m}분",
                selected = selected == m,
                onClick = { onSelect(m) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeedsChips(state: UiState, vm: PickupViewModel) {
    val n = state.draft.needs
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectChip("보행 약자", selected = n.mobilityImpaired, onClick = vm::toggleMobility, icon = Icons.AutoMirrored.Filled.Accessible)
        SelectChip("짐 많음", selected = n.heavyLuggage, onClick = vm::toggleLuggage, icon = Icons.Filled.Luggage)
    }
}

@Composable
private fun PhoneField(state: UiState, vm: PickupViewModel) {
    val allowed = state.keyboardAllowed
    OutlinedTextField(
        value = state.draft.phone,
        onValueChange = vm::updatePhone,
        enabled = allowed,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Phone, null, modifier = Modifier.size(28.dp)) },
        placeholder = { Text("010-0000-0000", style = MaterialTheme.typography.titleMedium) },
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = {
            Text(
                if (allowed) "정차 중에만 입력하세요. 비워 두면 차량 문자 앱 또는 화면 안내로 전달합니다."
                else "주행 중에는 입력할 수 없습니다. 정차 후 입력하세요.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pmu.Accent,
            unfocusedBorderColor = Pmu.Outline,
            focusedTextColor = Pmu.Text,
            unfocusedTextColor = Pmu.Text,
            cursorColor = Pmu.Accent,
            focusedLeadingIconColor = Pmu.Accent,
            unfocusedLeadingIconColor = Pmu.TextMuted,
            focusedSupportingTextColor = Pmu.TextMuted,
            unfocusedSupportingTextColor = Pmu.TextFaint,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
    )
}
