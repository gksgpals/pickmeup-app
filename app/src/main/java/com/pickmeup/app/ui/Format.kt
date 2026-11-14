package com.pickmeup.app.ui

import com.pickmeup.core.model.GeoPoint
import com.pickmeup.core.model.PickupSpot
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 두 좌표 사이 직선 거리(m). */
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** 도심 저속 주행(20 km/h) 기준 이동 시간 추정(분, 최소 1분). */
fun estimateDriveMinutes(from: GeoPoint, to: GeoPoint): Int {
    val metersPerMinute = 20_000.0 / 60.0
    val min = ceil(haversineMeters(from, to) / metersPerMinute).toInt()
    return min.coerceAtLeast(1)
}

fun freeWaitLabel(spot: PickupSpot): String = when (val m = spot.freeWaitMinutes) {
    null -> "무료 · 시간 제한 없음"
    0 -> "유료 대기"
    else -> "무료 ${m}분"
}

fun readyLabel(minutes: Int?): String = when (minutes) {
    null -> "확인 필요"
    0 -> "지금 나와 있음"
    else -> "${minutes}분 뒤"
}

fun formatClock(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun walkLabel(meters: Int): String = if (meters >= 1000) "도보 %.1fkm".format(meters / 1000.0) else "도보 ${meters}m"
