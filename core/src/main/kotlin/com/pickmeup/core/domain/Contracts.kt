package com.pickmeup.core.domain

import com.pickmeup.core.model.*

/**
 * core 모듈의 공용 계약. 구현은 같은 패키지의 별도 파일에 둡니다.
 * app 모듈은 이 시그니처만 보고 개발하므로 바꾸지 마세요.
 */

/** 접근 도로 소통 정보 제공자. 실시간 소스가 없으면 시연용 구현을 씁니다. */
fun interface TrafficProvider {
    /** linkId가 null이거나 조회 불가하면 TrafficLevel.UNKNOWN */
    fun levelOf(linkId: String?): TrafficLevel
}

/** 거점 데이터 저장소. */
interface HubRepository {
    fun all(): List<Hub>
    fun byId(id: String): Hub?
}

/** 픽업 상태 머신의 단계. */
enum class PickupPhase {
    IDLE,              // 입력 대기
    PLANNING,          // 픽업 정보 확인·승차 지점 후보 선택
    TO_WAITING,        // 대기 장소로 이동 중
    WAITING,           // 대기 모드(주차 브레이크 또는 수동 정차 확인)
    TO_BOARDING,       // 승차 지점으로 이동 중
    BOARDING,          // 승차 지점 도착, 탑승 대기
    COMPLETED,         // 탑승 완료
}

/** 상태 머신 입력 이벤트. */
sealed interface PickupEvent {
    data class Planned(val request: PickupRequest) : PickupEvent
    data class StartToWaiting(val spot: PickupSpot) : PickupEvent
    data class StartToBoarding(val spot: PickupSpot) : PickupEvent
    /** 주차 브레이크 체결(EPB) 또는 운전자 "정차 완료" 터치. */
    data class Parked(val bySensor: Boolean, val engaged: Boolean = true) : PickupEvent
    data object ArrivedAtWaiting : PickupEvent
    data class DoorStates(val doors: Map<String, Boolean?>) : PickupEvent
    data object RequestManualBoarding : PickupEvent
    /** 동승자 준비 완료 신호 또는 카운트다운 종료. */
    data class PassengerReady(val byPassengerSignal: Boolean) : PickupEvent
    /** 승차 지점 도착(내비 안내 종료 또는 운전자 터치). */
    data object ArrivedAtBoarding : PickupEvent
    /** 탑승 확인 요청 후 운전자 확정. */
    data class Boarded(val bySensor: Boolean) : PickupEvent
    data object Cancel : PickupEvent
}

data class PickupState(
    val phase: PickupPhase = PickupPhase.IDLE,
    val request: PickupRequest? = null,
    val waitingSpot: PickupSpot? = null,
    val boardingSpot: PickupSpot? = null,
    /** 사용자에게 보여줄 최근 상태 메시지. */
    val notice: String? = null,
    val arrivedAtWaiting: Boolean = false,
    val brakeConfirmed: Boolean = false,
    val doorBaseline: Map<String, Boolean?> = emptyMap(),
    val openedDoors: Set<String> = emptySet(),
    val boardingConfirmation: Boolean = false,
    val boardingDetected: Boolean = false,
)
