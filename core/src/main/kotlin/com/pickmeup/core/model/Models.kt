package com.pickmeup.core.model

/**
 * 픽미업 도메인 모델 (공용 계약).
 * core 모듈과 app 모듈이 함께 사용하므로 필드 이름·의미를 바꾸지 마세요.
 */

/** 위경도 좌표 (WGS84). */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 승차·대기 공간 유형. [priority]가 낮을수록 기본 추천 순위가 높습니다(기획서 4단계 우선순위).
 */
enum class SpotType(val priority: Int, val label: String) {
    CURBSIDE_POCKET(1, "주행차로 옆 승차 공간"),
    BUILDING_DROPOFF(2, "건물 앞 외부 승하차 공간"),
    ON_STREET_PARKING(3, "노상 주차 구역"),
    PARKING_LOT(4, "주차장"),
}

/** 도로 측면: 진행 방향 기준 오른쪽/왼쪽. */
enum class RoadSide(val label: String) { RIGHT("진행 방향 오른쪽"), LEFT("진행 방향 왼쪽") }

/** 접근 도로의 소통 상태. UNKNOWN = 교통정보를 조회할 수 없는 이면도로/골목. */
enum class TrafficLevel(val label: String) {
    SMOOTH("원활"),
    SLOW("서행"),
    CONGESTED("정체"),
    UNKNOWN("교통정보 없음"),
}

/** 동승자 특화 조건. */
data class PassengerNeeds(
    /** 보행 약자(고령자·유아 동반·휠체어 등): 계단·턱 없는 지점만, 도보 거리 짧은 순. */
    val mobilityImpaired: Boolean = false,
    /** 짐이 많음: 도보 거리 짧은 지점 우선, 트렁크 접근 여유 공간 선호. */
    val heavyLuggage: Boolean = false,
)

/** 이용 가능 시간대(요일 무관 단순화). 분 단위, 0..1440. 예: 07:00~22:00 → 420..1320 */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {
    fun contains(minuteOfDay: Int): Boolean =
        if (startMinute <= endMinute) minuteOfDay in startMinute until endMinute
        else minuteOfDay >= startMinute || minuteOfDay < endMinute
    companion object { val ALL_DAY = TimeWindow(0, 1440) }
}

/** 거점에 등록된 승차 또는 대기 후보 지점. */
data class PickupSpot(
    val id: String,
    val name: String,
    val type: SpotType,
    /** 실제로 차를 대는 위치(내비 전달 목적지). */
    val stopPoint: GeoPoint,
    /** 진입 방향 설명. 예: "테헤란로에서 우회전 진입" */
    val approach: String,
    val roadSide: RoadSide,
    /** 동승자 합류 위치·현장 표지. 예: "상가 동문 바깥, 파란 약국 간판 앞" */
    val meetingSign: String,
    /** 동승자 출입구에서 정차 위치까지 도보 거리(m). */
    val walkMeters: Int,
    /** 도보 경로에 계단·턱이 있는지. */
    val hasStepsOrCurb: Boolean,
    /** 승차(잠깐 정차) 가능 여부. */
    val boardingAllowed: Boolean,
    /** 대기(수 분 이상 정차) 가능 여부. */
    val waitingAllowed: Boolean,
    /** 무료 대기 가능 시간(분). 0이면 무료 대기 불가, null이면 제한 없음. */
    val freeWaitMinutes: Int?,
    val available: TimeWindow = TimeWindow.ALL_DAY,
    /** 접근 도로 구간 식별자. null이면 교통정보 연결 불가(→ TrafficLevel.UNKNOWN). */
    val approachLinkId: String?,
    /** 운영 근거·자료 기준일 등. */
    val source: String,
)

/** 출입구(동승자가 나오는 곳). */
data class Entrance(
    val id: String,
    val name: String,
    /** 음성 인식용 별칭. 예: ["동문", "동쪽 입구"] */
    val aliases: List<String>,
)

/** 픽업 거점(건물·시설). */
data class Hub(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val roadAddress: String,
    val center: GeoPoint,
    val entrances: List<Entrance>,
    /** 승차·대기 후보. 출입구별로 다를 수 있어 [entranceIds]로 연결합니다. */
    val spots: List<PickupSpot>,
    /** spotId → 해당 지점을 쓰는 출입구 id 목록. 비어 있으면 모든 출입구에 해당. */
    val entranceIds: Map<String, List<String>> = emptyMap(),
)

/** 운전자의 픽업 요청(음성 또는 터치 입력 결과). */
data class PickupRequest(
    val hub: Hub,
    val entrance: Entrance?,
    /** 동승자 예상 준비 시간(분). 0이면 이미 나와 있음. */
    val readyInMinutes: Int,
    val passengerName: String? = null,
    val needs: PassengerNeeds = PassengerNeeds(),
)

/** 추천 결과 한 건. */
data class RankedSpot(
    val spot: PickupSpot,
    val rank: Int,
    val traffic: TrafficLevel,
    /** 화면에 보여줄 추천/순위 변경 이유. 예: "1순위 진입로 정체로 대체 지점을 위로 올렸습니다" */
    val reasons: List<String>,
    /** 기본 우선순위에서 순위가 조정되었는지. */
    val reordered: Boolean,
)

/** 대기 장소 제안이 필요한지 판단한 결과. */
data class WaitAdvice(
    val shouldWait: Boolean,
    /** 대기 후보(무료 우선). shouldWait=false면 비어 있음. */
    val waitingCandidates: List<RankedSpot>,
    val message: String,
)

/** 음성 문장 해석 결과. */
data class ParsedUtterance(
    val raw: String,
    /** 이름 매칭으로 찾은 거점 후보(점수 높은 순). 여러 개면 운전자가 선택. */
    val hubCandidates: List<Hub>,
    val entrance: Entrance?,
    val readyInMinutes: Int?,
    val passengerLabel: String?,
    val needs: PassengerNeeds,
)
