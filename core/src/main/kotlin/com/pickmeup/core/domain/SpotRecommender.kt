package com.pickmeup.core.domain

import com.pickmeup.core.model.Hub
import com.pickmeup.core.model.PickupRequest
import com.pickmeup.core.model.PickupSpot
import com.pickmeup.core.model.RankedSpot
import com.pickmeup.core.model.TrafficLevel
import com.pickmeup.core.model.WaitAdvice

/** 준비 시간이 이 값 이상이면 대기 장소를 제안합니다(분). */
const val WAIT_THRESHOLD_MINUTES = 10

/**
 * 승차 지점 추천(기획서 4단계 우선순위) + 접근 도로 소통 상태 반영 재정렬 + 대기 장소 판단.
 *
 * 기본 순서: [com.pickmeup.core.model.SpotType.priority] → 도보 거리.
 * 동승자 조건(보행 약자·짐 많음)이 있으면 도보 거리 가중치를 키웁니다.
 * 진입로가 정체(CONGESTED)인 지점은 정체가 아닌 후보 아래로, 서행(SLOW)은 한 단계 남짓 내립니다.
 * 교통정보 없음(UNKNOWN)은 순위를 바꾸지 않고 기본 우선순위로 안내합니다.
 */
class SpotRecommender(private val traffic: TrafficProvider) {

    fun rankBoarding(request: PickupRequest, minuteOfDay: Int): List<RankedSpot> {
        val hub = request.hub
        val needs = request.needs
        val open = hub.spots.filter { it.boardingAllowed && it.available.contains(minuteOfDay) }
        if (open.isEmpty()) return emptyList()

        var pool = open
        var entranceFallback = false
        val entrance = request.entrance
        if (entrance != null) {
            val byEntrance = open.filter { servesEntrance(hub, it, entrance.id) }
            if (byEntrance.isNotEmpty()) pool = byEntrance else entranceFallback = true
        }
        var stepFallback = false
        if (needs.mobilityImpaired) {
            val flat = pool.filter { !it.hasStepsOrCurb }
            if (flat.isNotEmpty()) pool = flat else stepFallback = true
        }

        val level = pool.associate { it.id to traffic.levelOf(it.approachLinkId) }
        val walkWeight = when {
            needs.mobilityImpaired -> 1.0
            needs.heavyLuggage -> 0.6
            else -> 0.0
        }
        fun baseScore(s: PickupSpot) = s.type.priority * 100.0 + s.walkMeters * walkWeight
        fun penalty(s: PickupSpot) = if (level[s.id] == TrafficLevel.SLOW) SLOW_PENALTY else 0.0
        fun congested(s: PickupSpot) = level[s.id] == TrafficLevel.CONGESTED

        val tieBreak = compareBy<PickupSpot>({ it.type.priority }, { it.walkMeters }, { it.id })
        // 1) 순수 4단계 우선순위 순서 (reordered 판정 기준)
        val pureOrder = pool.sortedWith(tieBreak)
        // 2) 동승자 조건 반영 순서
        val needsOrder = pool.sortedWith(compareBy<PickupSpot> { baseScore(it) }.then(tieBreak))
        // 3) 교통 반영 최종 순서
        val finalOrder = pool.sortedWith(
            compareBy<PickupSpot>({ if (congested(it)) 1 else 0 }, { baseScore(it) + penalty(it) }).then(tieBreak),
        )

        val pureIdx = pureOrder.withIndex().associate { it.value.id to it.index }
        val needsIdx = needsOrder.withIndex().associate { it.value.id to it.index }
        val needsTop = needsOrder.first()
        val needsTopLevel = level.getValue(needsTop.id)

        return finalOrder.mapIndexed { i, spot ->
            val lv = level.getValue(spot.id)
            val ni = needsIdx.getValue(spot.id)
            val pi = pureIdx.getValue(spot.id)
            val reasons = mutableListOf<String>()

            // 교통 반영 순위 변경
            if (i > ni && (lv == TrafficLevel.CONGESTED || lv == TrafficLevel.SLOW)) {
                reasons += if (lv == TrafficLevel.CONGESTED) "진입로 정체로 순위를 낮췄습니다"
                else "진입로 서행으로 순위를 낮췄습니다"
            } else if (i < ni) {
                reasons += if (i == 0 && needsTop.id != spot.id &&
                    (needsTopLevel == TrafficLevel.CONGESTED || needsTopLevel == TrafficLevel.SLOW)
                ) {
                    promotedTopReason(needsTopLevel, lv)
                } else {
                    "앞 순위 지점의 진입로 상황으로 순위를 올렸습니다"
                }
            }

            // 동승자 조건 반영 순위 변경
            if (ni < pi) {
                reasons += if (needs.mobilityImpaired) "보행 약자를 고려해 도보 거리가 짧은 이 지점을 앞당겼습니다"
                else "짐이 많아 도보 거리가 짧은 이 지점을 앞당겼습니다"
            } else if (ni > pi) {
                reasons += "도보 거리가 길어 순위를 낮췄습니다"
            }

            reasons += "${spot.type.label} (기본 ${spot.type.priority}순위 유형)"
            reasons += "출입구에서 도보 ${spot.walkMeters}m"
            reasons += if (spot.hasStepsOrCurb) "도보 경로에 계단·턱 있음" else "계단·턱 없음"
            if (stepFallback) reasons += "계단·턱 없는 지점이 없어 함께 표시합니다. 동승자 이동에 주의하세요"
            if (entranceFallback) reasons += "선택한 출입구 전용 지점이 없어 거점 전체 후보를 표시합니다"
            reasons += "${spot.roadSide.label} 정차"
            reasons += trafficReason(lv)

            RankedSpot(
                spot = spot,
                rank = i + 1,
                traffic = lv,
                reasons = reasons,
                reordered = i != pi,
            )
        }
    }

    /** 시연용 추정: 출발지→대기 장소는 직행 시간과 같다고 가정합니다. */
    fun adviseWaiting(
        request: PickupRequest,
        minuteOfDay: Int,
        boarding: PickupSpot,
        remainingMinutes: Int,
        directMinutes: Int,
        travelToBoarding: (PickupSpot, PickupSpot) -> Int,
    ): WaitAdvice {
        val slack = remainingMinutes - directMinutes
        val summary = "직행 약 ${directMinutes}분 · 시간 여유 ${slack}분 (시연용 추정)"
        if (slack < WAIT_THRESHOLD_MINUTES) {
            return WaitAdvice(false, emptyList(), "$summary. 대기 없이 바로 승차 지점으로 안내합니다.")
        }
        // 실제 차량 위치가 없어 출발지→대기 장소는 직행 시간과 같다고 가정합니다.
        val eligible = request.hub.spots.asSequence()
            .filter { it.waitingAllowed && it.available.contains(minuteOfDay) }
            .map { spot ->
                val detour = travelToBoarding(spot, boarding).coerceAtLeast(0)
                Triple(spot, detour, slack - detour)
            }
            .filter { it.third > 0 }
            .sortedWith(compareBy<Triple<PickupSpot, Int, Int>>(
                { freeClass(it.first, it.third) }, { it.first.type.priority },
                { it.first.walkMeters }, { it.first.id },
            )).toList()
        if (eligible.isEmpty()) {
            return WaitAdvice(false, emptyList(), "$summary. 이용 가능한 대기 후보가 없어 바로 승차 지점으로 안내합니다.")
        }
        val candidates = eligible.mapIndexed { index, (spot, detour, wait) ->
            val level = traffic.levelOf(spot.approachLinkId)
            RankedSpot(spot, index + 1, level, listOf(
                "우회 약 ${detour}분 · 실제 대기 가능 약 ${wait}분 (시연용 추정)",
                freeReason(spot, wait), trafficReason(level),
            ), false)
        }
        return WaitAdvice(true, candidates, "$summary. 대기 장소까지는 직행 시간과 같다고 가정합니다. 우회 시간과 실제 대기 가능 시간을 비교해 추천합니다.")
    }

    private fun servesEntrance(hub: Hub, spot: PickupSpot, entranceId: String): Boolean {
        val ids = hub.entranceIds[spot.id]
        return ids.isNullOrEmpty() || entranceId in ids
    }

    /** 0 = 예상 대기 시간 전체 무료, 1 = 일부만 무료, 2 = 유료. */
    private fun freeClass(spot: PickupSpot, ready: Int): Int {
        val free = spot.freeWaitMinutes ?: return 0
        return when {
            free >= ready -> 0
            free > 0 -> 1
            else -> 2
        }
    }

    private fun freeReason(spot: PickupSpot, ready: Int): String {
        val free = spot.freeWaitMinutes
        return when {
            free == null -> "무료 대기 가능 (시간 제한 없음)"
            free >= ready -> "무료 대기 ${free}분 — 예상 대기 ${ready}분 이내"
            free > 0 -> "무료 ${free}분 이후 유료 (예상 대기 ${ready}분)"
            else -> "유료 대기 — 무료 대기 불가"
        }
    }

    private fun promotedTopReason(topLevel: TrafficLevel, mine: TrafficLevel): String {
        val cause = if (topLevel == TrafficLevel.CONGESTED) "정체되어" else "서행 중이라"
        return when (mine) {
            TrafficLevel.SMOOTH -> "1순위 진입로가 $cause 접근이 원활한 이 지점을 먼저 추천합니다"
            TrafficLevel.UNKNOWN -> "1순위 진입로가 $cause 이 지점을 먼저 추천합니다 (이 지점은 교통정보 없음)"
            else -> "1순위 진입로가 $cause 상대적으로 접근이 나은 이 지점을 먼저 추천합니다"
        }
    }

    private fun trafficReason(level: TrafficLevel): String = when (level) {
        TrafficLevel.SMOOTH -> "진입로 소통 원활"
        TrafficLevel.SLOW -> "진입로 서행"
        TrafficLevel.CONGESTED -> "진입로 정체"
        TrafficLevel.UNKNOWN -> "교통정보 없음 — 기본 우선순위로 안내"
    }

    private companion object {
        /** 서행 구간 감점: 유형 한 단계(100)보다 조금 크게. */
        const val SLOW_PENALTY = 150.0
    }
}
