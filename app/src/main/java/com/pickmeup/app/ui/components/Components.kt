package com.pickmeup.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.ui.theme.Pmu
import com.pickmeup.app.ui.theme.trafficColor
import com.pickmeup.core.model.TrafficLevel

/** 차량 화면 최소 터치 높이. */
val TouchMin: Dp = 76.dp

/** 둥근 정보 카드. [accent]가 있으면 테두리로 강조합니다. */
@Composable
fun PmuCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    color: Color = Pmu.Card,
    padding: PaddingValues = PaddingValues(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val border = accent?.let { BorderStroke(3.dp, it) } ?: BorderStroke(1.dp, Pmu.OutlineSoft)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

enum class ButtonTone { Primary, Secondary, Quiet, Danger }

/** 큰 행동 버튼(최소 높이 88dp). */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
    subtitle: String? = null,
    minHeight: Dp = 88.dp,
) {
    val (bg, fg, border) = when (tone) {
        ButtonTone.Primary -> Triple(Pmu.Accent, Pmu.OnAccent, null)
        ButtonTone.Secondary -> Triple(Pmu.CardHigh, Pmu.Text, BorderStroke(2.dp, Pmu.Accent.copy(alpha = 0.6f)))
        ButtonTone.Quiet -> Triple(Pmu.Card, Pmu.Text, BorderStroke(1.dp, Pmu.Outline))
        ButtonTone.Danger -> Triple(Pmu.Card, Pmu.Congested, BorderStroke(2.dp, Pmu.Congested.copy(alpha = 0.7f)))
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) bg else Pmu.Card,
        contentColor = if (enabled) fg else Pmu.TextFaint,
        border = border,
    ) {
        Row(
            Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = (if (enabled) fg else Pmu.TextFaint).copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 선택 칩(최소 76dp). */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 112.dp, minHeight = TouchMin),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Pmu.AccentSoft else Pmu.CardHigh,
        contentColor = if (selected) Pmu.Accent else Pmu.Text,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Pmu.Accent else Pmu.Outline),
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
            } else if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 작은 상태 표시(배지). */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Pmu.Bg else color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) Pmu.Bg else color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun TrafficTag(level: TrafficLevel, modifier: Modifier = Modifier) {
    Tag(
        text = if (level == TrafficLevel.UNKNOWN) "교통정보 없음" else "진입로 ${level.label}",
        color = trafficColor(level),
        icon = Icons.Filled.Traffic,
        modifier = modifier,
    )
}

@Composable
fun RankBadge(rank: Int, highlight: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(if (highlight) Pmu.Accent else Pmu.CardHigh)
            .border(2.dp, if (highlight) Pmu.Accent else Pmu.Outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$rank",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlight) Pmu.OnAccent else Pmu.Text,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = Pmu.Accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, color = Pmu.Text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun InfoLine(icon: ImageVector, text: String, modifier: Modifier = Modifier, color: Color = Pmu.TextMuted, iconTint: Color = Pmu.TextMuted) {
    Row(modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** 연동 상태 알약. */
@Composable
fun LinkPill(label: String, status: LinkStatus, icon: ImageVector, simulated: Boolean = false, compact: Boolean = false) {
    val (color, word) = when (status) {
        LinkStatus.CONNECTED -> Pmu.Smooth to "연결됨"
        LinkStatus.CONNECTING -> Pmu.Attention to "연결 중"
        LinkStatus.UNAVAILABLE -> Pmu.Unknown to "수동"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Pmu.Card)
            .border(1.dp, Pmu.OutlineSoft, RoundedCornerShape(30.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Pmu.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        if (!compact) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Pmu.TextMuted)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(if (simulated) "시연" else word, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}
