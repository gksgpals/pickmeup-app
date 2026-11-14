package com.pickmeup.core.domain

import com.pickmeup.core.model.TrafficLevel
import java.util.concurrent.ConcurrentHashMap

/**
 * 시연용 교통정보 제공자. 실시간 소통 정보 API가 없을 때 화면의 시연 패널에서 구간별 상태를 바꿉니다.
 *
 * - linkId가 null(이면도로·골목) → [TrafficLevel.UNKNOWN]
 * - linkId가 있고 값을 따로 지정하지 않은 구간 → [TrafficLevel.SMOOTH]
 * - [set]으로 지정한 구간 → 지정한 값 (UNKNOWN으로 지정하면 조회 불가 상태를 흉내 냄)
 *
 * 여러 스레드(UI·센서 콜백)에서 동시에 읽고 써도 안전합니다.
 */
class SimulatedTraffic(initial: Map<String, TrafficLevel> = emptyMap()) : TrafficProvider {

    private val levels = ConcurrentHashMap<String, TrafficLevel>(initial)

    override fun levelOf(linkId: String?): TrafficLevel {
        if (linkId.isNullOrBlank()) return TrafficLevel.UNKNOWN
        return levels[linkId] ?: TrafficLevel.SMOOTH
    }

    fun set(linkId: String, level: TrafficLevel) {
        levels[linkId] = level
    }

    /** 현재 지정된 구간 상태의 복사본. */
    fun snapshot(): Map<String, TrafficLevel> = HashMap(levels)

    /** 지정한 값을 모두 지워 기본 상태(연결 구간은 원활)로 되돌립니다. */
    fun clear() {
        levels.clear()
    }
}
