package com.pickmeup.core.domain

import com.pickmeup.core.domain.PickupPhase.BOARDING
import com.pickmeup.core.domain.PickupPhase.COMPLETED
import com.pickmeup.core.domain.PickupPhase.IDLE
import com.pickmeup.core.domain.PickupPhase.PLANNING
import com.pickmeup.core.domain.PickupPhase.TO_BOARDING
import com.pickmeup.core.domain.PickupPhase.TO_WAITING
import com.pickmeup.core.domain.PickupPhase.WAITING

/**
 * 픽업 상태 머신 (순수 함수).
 *
 * ```
 * IDLE ─(입력)→ PLANNING ─┬─ 준비 ≥ 10분 → TO_WAITING ─(EPB/수동)→ WAITING ─(동승자 준비)→ TO_BOARDING
 *                         └─ 준비 < 10분 ────────────────────────────────────────────→ TO_BOARDING
 * TO_BOARDING ─(도착)→ BOARDING ─(도어 개폐/수동)→ COMPLETED
 * ```
 *
 * 허용되지 않는 전이는 상태를 그대로(같은 인스턴스) 돌려줍니다.
 * 단, 운전자에게 다음 행동을 알려야 하는 경우(예: 승차 지점 미선택)는 [PickupState.notice]만 갱신합니다.
 */
object PickupReducer {

    fun reduce(state: PickupState, event: PickupEvent): PickupState = when (event) {
        is PickupEvent.Planned -> onPlanned(state, event)
        is PickupEvent.StartToWaiting -> onStartToWaiting(state, event)
        is PickupEvent.StartToBoarding -> onStartToBoarding(state, event)
        is PickupEvent.Parked -> onParked(state, event)
        is PickupEvent.PassengerReady -> onPassengerReady(state, event)
        PickupEvent.ArrivedAtBoarding -> onArrived(state)
        is PickupEvent.Boarded -> onBoarded(state, event)
        PickupEvent.Cancel ->
            if (state.phase == IDLE) state else PickupState(notice = "픽업을 취소했습니다.")
    }

    private fun onPlanned(state: PickupState, e: PickupEvent.Planned): PickupState {
        if (state.phase !in setOf(IDLE, PLANNING, COMPLETED)) return state
        val r = e.request
        val where = listOfNotNull(r.hub.name, r.entrance?.name).joinToString(" ")
        val ready = if (r.readyInMinutes <= 0) "동승자가 이미 나와 있음" else "준비까지 약 ${r.readyInMinutes}분"
        return PickupState(
            phase = PLANNING,
            request = r,
            notice = "픽업 정보를 확인했습니다: $where, $ready",
        )
    }

    private fun onStartToWaiting(state: PickupState, e: PickupEvent.StartToWaiting): PickupState {
        if (state.request == null) return state
        return when (state.phase) {
            PLANNING, TO_WAITING, TO_BOARDING, BOARDING -> state.copy(
                phase = TO_WAITING,
                waitingSpot = e.spot,
                notice = if (state.phase == TO_WAITING) "대기 장소를 '${e.spot.name}'(으)로 변경했습니다."
                else "대기 장소 '${e.spot.name}'(으)로 안내를 시작합니다.",
            )
            else -> state
        }
    }

    private fun onStartToBoarding(state: PickupState, e: PickupEvent.StartToBoarding): PickupState {
        if (state.request == null) return state
        val notice = when (state.phase) {
            PLANNING -> "승차 지점 '${e.spot.name}'(으)로 안내를 시작합니다."
            WAITING, TO_WAITING -> "동승자 준비에 맞춰 승차 지점 '${e.spot.name}'(으)로 안내를 전환합니다."
            TO_BOARDING, BOARDING -> "승차 지점을 '${e.spot.name}'(으)로 변경했습니다."
            else -> return state
        }
        return state.copy(phase = TO_BOARDING, boardingSpot = e.spot, notice = notice)
    }

    private fun onParked(state: PickupState, e: PickupEvent.Parked): PickupState {
        if (state.phase != TO_WAITING) return state
        return state.copy(
            phase = WAITING,
            notice = if (e.bySensor) "주차 브레이크 체결을 감지해 대기 모드로 전환했습니다."
            else "정차를 확인해 대기 모드로 전환했습니다.",
        )
    }

    private fun onPassengerReady(state: PickupState, e: PickupEvent.PassengerReady): PickupState {
        return when (state.phase) {
            WAITING, TO_WAITING -> {
                val spot = state.boardingSpot
                if (spot == null) {
                    state.copy(notice = "동승자가 준비되었습니다. 승차 지점을 선택해 주세요.")
                } else {
                    val cause = if (e.byPassengerSignal) "동승자 준비 완료 신호를 받아" else "준비 시간이 끝나"
                    state.copy(
                        phase = TO_BOARDING,
                        notice = "$cause 승차 지점 '${spot.name}'(으)로 안내를 전환합니다.",
                    )
                }
            }
            PLANNING -> state.copy(notice = "동승자가 준비되었습니다. 승차 지점을 선택해 주세요.")
            TO_BOARDING -> state.copy(notice = "동승자가 준비되었습니다. 승차 지점으로 이동 중입니다.")
            else -> state
        }
    }

    private fun onArrived(state: PickupState): PickupState {
        if (state.phase != TO_BOARDING) return state
        val sign = state.boardingSpot?.meetingSign
        return state.copy(
            phase = BOARDING,
            notice = if (sign != null) "승차 지점에 도착했습니다. 동승자와 '$sign'에서 만나세요."
            else "승차 지점에 도착했습니다. 동승자 탑승을 기다립니다.",
        )
    }

    private fun onBoarded(state: PickupState, e: PickupEvent.Boarded): PickupState {
        if (state.phase != BOARDING) return state
        return state.copy(
            phase = COMPLETED,
            notice = if (e.bySensor) "도어 개폐를 감지해 탑승 완료로 처리했습니다. 픽업을 마칩니다."
            else "탑승 완료를 확인했습니다. 픽업을 마칩니다.",
        )
    }
}
