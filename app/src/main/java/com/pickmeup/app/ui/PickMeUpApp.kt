package com.pickmeup.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pickmeup.app.ui.components.BigButton
import com.pickmeup.app.ui.components.BoardingSpotCard
import com.pickmeup.app.ui.components.ButtonTone
import com.pickmeup.app.ui.screens.BoardingScreen
import com.pickmeup.app.ui.screens.CompletedScreen
import com.pickmeup.app.ui.screens.DemoPanel
import com.pickmeup.app.ui.screens.HomeScreen
import com.pickmeup.app.ui.screens.PlanningScreen
import com.pickmeup.app.ui.screens.ToBoardingScreen
import com.pickmeup.app.ui.screens.ToWaitingScreen
import com.pickmeup.app.ui.screens.TopStatusBar
import com.pickmeup.app.ui.screens.WaitingScreen
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.core.domain.PickupPhase

/** 앱 루트: 상단 상태 바 + 단계별 화면 + 배너 + 시연 패널 + 대화상자. */
@Composable
fun PickMeUpApp(vm: PickupViewModel) {
    val state by vm.ui.collectAsState()
    var confirmCancel by remember { mutableStateOf(false) }
    var changeSpot by remember { mutableStateOf(false) }
    val onMic = vm::startListening

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Pmu.Bg),
    ) {
        val widthDp = maxWidth.value.toInt()
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize()) {
            TopStatusBar(state, widthDp, onCancel = { confirmCancel = true }, onToggleDemo = vm::toggleDemoPanel)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.phase) {
                    PickupPhase.IDLE -> HomeScreen(state, vm, wide, onMic)
                    PickupPhase.PLANNING -> PlanningScreen(state, vm, wide, onMic)
                    PickupPhase.TO_WAITING -> ToWaitingScreen(state, vm, wide)
                    PickupPhase.WAITING -> WaitingScreen(state, vm, wide) { changeSpot = true }
                    PickupPhase.TO_BOARDING -> ToBoardingScreen(state, vm, wide) { changeSpot = true }
                    PickupPhase.BOARDING -> BoardingScreen(state, vm, wide) { changeSpot = true }
                    PickupPhase.COMPLETED -> CompletedScreen(state, vm)
                }
            }
        }

        // 배너(토스트 대용)
        AnimatedVisibility(
            visible = state.banner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
            state.banner?.let { BannerView(it, vm::dismissBanner) }
        }

        // 음성 인식 중
        if (state.listening) ListeningOverlay(onCancel = vm::cancelListening)

        // 시연 패널
        if (state.demoPanelOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Pmu.Scrim.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = vm::closeDemoPanel),
            )
        }
        AnimatedVisibility(
            visible = state.demoPanelOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            DemoPanel(state, vm, width = if (maxWidth < 600.dp) maxWidth else 440.dp)
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "픽업을 취소할까요?",
            body = "진행 중인 내비 안내와 대기 모드를 모두 종료합니다.",
            confirm = "픽업 취소",
            dismiss = "계속 진행",
            onConfirm = { confirmCancel = false; vm.cancel() },
            onDismiss = { confirmCancel = false },
        )
    }
    if (changeSpot) {
        ChangeSpotDialog(state, onSelect = { id -> changeSpot = false; vm.selectBoarding(id) }, onDismiss = { changeSpot = false })
    }
}

@Composable
private fun BannerView(banner: Banner, onDismiss: () -> Unit) {
    val color = when (banner.kind) {
        BannerKind.INFO -> Pmu.Accent
        BannerKind.WARN -> Pmu.Attention
        BannerKind.ERROR -> Pmu.Congested
    }
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(22.dp),
        color = Pmu.CardHigh,
        border = BorderStroke(2.dp, color),
        shadowElevation = 12.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 900.dp)
            .heightIn(min = 76.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (banner.kind == BannerKind.INFO) Icons.Filled.Info else Icons.Filled.WarningAmber, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Text(banner.text, style = MaterialTheme.typography.titleMedium, color = Pmu.Text, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Filled.Close, "닫기", tint = Pmu.TextMuted, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ListeningOverlay(onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Pmu.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(180.dp)
                    .background(Pmu.AccentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(132.dp).background(Pmu.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, null, tint = Pmu.OnAccent, modifier = Modifier.size(72.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("듣고 있어요", style = MaterialTheme.typography.headlineLarge, color = Pmu.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                "“○○상가 동문에서 친구 태울 거야. 10분 정도 걸린대”처럼 말해 주세요",
                style = MaterialTheme.typography.bodyLarge,
                color = Pmu.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(28.dp))
            BigButton("취소", onClick = onCancel, tone = ButtonTone.Quiet, icon = Icons.Filled.Close, modifier = Modifier.width(260.dp))
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Pmu.Surface, border = BorderStroke(1.dp, Pmu.Outline)) {
            Column(Modifier.padding(28.dp).widthIn(max = 560.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Pmu.Text)
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = Pmu.TextMuted)
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BigButton(dismiss, onClick = onDismiss, tone = ButtonTone.Quiet, modifier = Modifier.weight(1f))
                    BigButton(confirm, onClick = onConfirm, tone = ButtonTone.Danger, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChangeSpotDialog(state: UiState, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Pmu.Surface,
            border = BorderStroke(1.dp, Pmu.Outline),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 980.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("승차 지점 변경", style = MaterialTheme.typography.headlineMedium, color = Pmu.Text)
                        Text("바꾸면 동승자에게 새 합류 위치를 알립니다", style = MaterialTheme.typography.bodyMedium, color = Pmu.TextMuted)
                    }
                    BigButton("닫기", onClick = onDismiss, tone = ButtonTone.Quiet, icon = Icons.Filled.Close, minHeight = 76.dp)
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.ranked.isEmpty()) {
                        Text("선택할 수 있는 다른 승차 지점이 없습니다", style = MaterialTheme.typography.titleMedium, color = Pmu.TextMuted)
                    }
                    val current = state.pickup.boardingSpot?.id
                    state.ranked.forEach { r ->
                        BoardingSpotCard(r, selected = r.spot.id == current, onClick = { onSelect(r.spot.id) }, compact = true)
                    }
                }
            }
        }
    }
}
