package com.pickmeup.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SwitchLeft
import androidx.compose.material.icons.filled.SwitchRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pickmeup.app.ui.freeWaitLabel
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.app.ui.walkLabel
import com.pickmeup.core.model.RankedSpot
import com.pickmeup.core.model.RoadSide

/** 승차 지점 후보 카드. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardingSpotCard(
    ranked: RankedSpot,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val spot = ranked.spot
    PmuCard(
        modifier = modifier.fillMaxWidth(),
        accent = when {
            selected -> Pmu.Accent
            ranked.reordered -> Pmu.Attention.copy(alpha = 0.7f)
            else -> null
        },
        color = if (selected) Pmu.CardHigh else Pmu.Card,
        padding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            RankBadge(ranked.rank, highlight = selected)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spot.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Pmu.Text,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        Icon(Icons.Filled.CheckCircle, "선택됨", tint = Pmu.Accent, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(spot.type.label, Pmu.Accent)
                    TrafficTag(ranked.traffic)
                    if (ranked.reordered) Tag("순위 조정", Pmu.Attention, icon = Icons.Filled.SwapVert)
                }
                Spacer(Modifier.height(10.dp))
                InfoLine(Icons.Filled.Place, spot.meetingSign, color = Pmu.Text, iconTint = Pmu.Accent)
                if (!compact) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        InfoLine(
                            if (spot.roadSide == RoadSide.RIGHT) Icons.Filled.SwitchRight else Icons.Filled.SwitchLeft,
                            spot.roadSide.label,
                        )
                        InfoLine(Icons.AutoMirrored.Filled.DirectionsWalk, walkLabel(spot.walkMeters))
                        InfoLine(
                            if (spot.hasStepsOrCurb) Icons.Filled.Stairs else Icons.AutoMirrored.Filled.Accessible,
                            if (spot.hasStepsOrCurb) "계단·턱 있음" else "계단·턱 없음",
                        )
                    }
                    InfoLine(Icons.AutoMirrored.Filled.AltRoute, spot.approach)
                    keyReasons(ranked).forEach { reason ->
                        InfoLine(
                            Icons.Filled.Info,
                            reason,
                            color = if (ranked.reordered) Pmu.Attention else Pmu.TextMuted,
                            iconTint = if (ranked.reordered) Pmu.Attention else Pmu.TextFaint,
                        )
                    }
                }
            }
        }
    }
}

/** 대기 장소 후보 카드. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaitingSpotCard(
    ranked: RankedSpot,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spot = ranked.spot
    val free = spot.freeWaitMinutes != 0
    PmuCard(
        modifier = modifier.fillMaxWidth(),
        accent = if (selected) Pmu.Accent else null,
        color = if (selected) Pmu.CardHigh else Pmu.Card,
        padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalParking, null, tint = Pmu.Accent, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(spot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Pmu.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(freeWaitLabel(spot), if (free) Pmu.Smooth else Pmu.Unknown, filled = free)
                    Tag(spot.type.label, Pmu.Accent)
                    TrafficTag(ranked.traffic)
                }
                Spacer(Modifier.height(6.dp))
                ranked.reasons.take(2).forEach { InfoLine(Icons.Filled.Info, it, color = Pmu.Text, iconTint = Pmu.Smooth) }
                InfoLine(Icons.AutoMirrored.Filled.AltRoute, spot.approach)
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.CheckCircle, "선택됨", tint = Pmu.Accent, modifier = Modifier.size(34.dp))
            }
        }
    }
}

/** 카드에 이미 아이콘으로 보이는 기본 사실(유형·도보·계단·도로 측면)은 빼고 판단 이유만 남깁니다. */
private fun keyReasons(ranked: RankedSpot): List<String> {
    val spot = ranked.spot
    return ranked.reasons.filterNot { r ->
        r.startsWith("${spot.type.label} (") ||
            r.startsWith("출입구에서 도보") ||
            r == "도보 경로에 계단·턱 있음" || r == "계단·턱 없음" ||
            r == "${spot.roadSide.label} 정차"
    }.take(3)
}
