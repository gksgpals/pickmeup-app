package com.pickmeup.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pickmeup.core.model.TrafficLevel

/**
 * 픽미업 색 체계.
 * - 배경: 짙은 남색 계열
 * - 행동(버튼·선택): 청록(Teal) 한 가지
 * - 주의(순위 조정·대기 필요): 호박색(Amber)
 * - 빨강은 정체 표시에만 사용
 */
object Pmu {
    val Bg = Color(0xFF080E1C)
    val Surface = Color(0xFF0F182B)
    val Card = Color(0xFF15213A)
    val CardHigh = Color(0xFF1C2A47)
    val Outline = Color(0xFF2A3B5E)
    val OutlineSoft = Color(0xFF203050)

    val Accent = Color(0xFF2DD4BF)
    val AccentDim = Color(0xFF134E4A)
    val AccentSoft = Color(0x332DD4BF)
    val OnAccent = Color(0xFF032420)

    val Attention = Color(0xFFF5B83D)
    val AttentionSoft = Color(0x33F5B83D)

    val Smooth = Color(0xFF4ADE80)
    val Slow = Color(0xFFF5B83D)
    val Congested = Color(0xFFF26B6B)
    val Unknown = Color(0xFF8A97AD)

    val Text = Color(0xFFEAF0FA)
    val TextMuted = Color(0xFF9AA8C0)
    val TextFaint = Color(0xFF66748C)

    val Scrim = Color(0xCC03070F)
}

fun trafficColor(level: TrafficLevel): Color = when (level) {
    TrafficLevel.SMOOTH -> Pmu.Smooth
    TrafficLevel.SLOW -> Pmu.Slow
    TrafficLevel.CONGESTED -> Pmu.Congested
    TrafficLevel.UNKNOWN -> Pmu.Unknown
}

private val colors = darkColorScheme(
    primary = Pmu.Accent,
    onPrimary = Pmu.OnAccent,
    primaryContainer = Pmu.AccentDim,
    onPrimaryContainer = Pmu.Text,
    secondary = Pmu.Attention,
    onSecondary = Color(0xFF2A1C00),
    tertiary = Pmu.Smooth,
    background = Pmu.Bg,
    onBackground = Pmu.Text,
    surface = Pmu.Surface,
    onSurface = Pmu.Text,
    surfaceVariant = Pmu.Card,
    onSurfaceVariant = Pmu.TextMuted,
    outline = Pmu.Outline,
    outlineVariant = Pmu.OutlineSoft,
    error = Pmu.Congested,
    onError = Color(0xFF2A0606),
)

/** 차량 화면용 큰 글자 체계(팔 길이 거리에서 읽기). */
private val typography = Typography(
    displayLarge = TextStyle(fontSize = 96.sp, lineHeight = 100.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 64.sp, lineHeight = 70.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 19.sp, lineHeight = 27.sp),
    bodySmall = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun PickMeUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
