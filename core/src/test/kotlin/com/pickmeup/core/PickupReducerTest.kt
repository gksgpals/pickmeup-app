package com.pickmeup.core

import com.pickmeup.core.data.SampleHubRepository
import com.pickmeup.core.domain.PickupEvent
import com.pickmeup.core.domain.PickupPhase
import com.pickmeup.core.domain.PickupReducer
import com.pickmeup.core.domain.PickupState
import com.pickmeup.core.model.PickupRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PickupReducerTest {
    private val hub = SampleHubRepository().byId(SampleHubRepository.HANBIT_ID)!!
    private val boarding = hub.spots.first { it.id == "hanbit-s1" }
    private val waiting = hub.spots.first { it.id == "hanbit-s6" }
    private fun request(ready: Int) = PickupRequest(hub, hub.entrances.first(), ready, "친구")

    private fun run(vararg events: PickupEvent): PickupState =
        events.fold(PickupState()) { s, e -> PickupReducer.reduce(s, e) }

    @Test
    fun waitingFlow_happyPath() {
        var s = PickupReducer.reduce(PickupState(), PickupEvent.Planned(request(15)))
        assertEquals(PickupPhase.PLANNING, s.phase)
        s = PickupReducer.reduce(s, PickupEvent.StartToWaiting(waiting))
        assertEquals(PickupPhase.TO_WAITING, s.phase)
        assertEquals(waiting, s.waitingSpot)
        s = PickupReducer.reduce(s, PickupEvent.Parked(bySensor = true))
        assertEquals(PickupPhase.WAITING, s.phase)
        assertEquals("주차 브레이크 체결을 감지해 대기 모드로 전환했습니다.", s.notice)
        s = PickupReducer.reduce(s, PickupEvent.StartToBoarding(boarding))
        assertEquals(PickupPhase.TO_BOARDING, s.phase)
        assertEquals(boarding, s.boardingSpot)
        s = PickupReducer.reduce(s, PickupEvent.ArrivedAtBoarding)
        assertEquals(PickupPhase.BOARDING, s.phase)
        s = PickupReducer.reduce(s, PickupEvent.Boarded(bySensor = true))
        assertEquals(PickupPhase.COMPLETED, s.phase)
        assertNotNull(s.notice)
    }

    @Test
    fun waitingFlow_passengerReadyNeedsBoardingSpot() {
        val waitingState = run(
            PickupEvent.Planned(request(15)),
            PickupEvent.StartToWaiting(waiting),
            PickupEvent.Parked(bySensor = false),
        )
        assertEquals("정차를 확인해 대기 모드로 전환했습니다.", waitingState.notice)
        val noSpot = PickupReducer.reduce(waitingState, PickupEvent.PassengerReady(byPassengerSignal = true))
        assertEquals(PickupPhase.WAITING, noSpot.phase)
        assertEquals("동승자가 준비되었습니다. 승차 지점을 선택해 주세요.", noSpot.notice)

        // 승차 지점을 미리 정해 둔 경우(대기 중 선택) → 준비 신호로 자동 전환
        val withSpot = waitingState.copy(boardingSpot = boarding)
        val switched = PickupReducer.reduce(withSpot, PickupEvent.PassengerReady(byPassengerSignal = true))
        assertEquals(PickupPhase.TO_BOARDING, switched.phase)
        assertEquals(boarding, switched.boardingSpot)
    }

    @Test
    fun passengerReadyWhileDrivingToWaiting_goesToBoardingIfSpotKnown() {
        val toWaiting = run(
            PickupEvent.Planned(request(15)),
            PickupEvent.StartToBoarding(boarding),
            PickupEvent.StartToWaiting(waiting), // 동승자가 늦어 대기 장소로 변경
        )
        assertEquals(PickupPhase.TO_WAITING, toWaiting.phase)
        assertEquals(boarding, toWaiting.boardingSpot)
        val s = PickupReducer.reduce(toWaiting, PickupEvent.PassengerReady(byPassengerSignal = false))
        assertEquals(PickupPhase.TO_BOARDING, s.phase)
    }

    @Test
    fun directFlow_happyPath() {
        val s = run(
            PickupEvent.Planned(request(0)),
            PickupEvent.StartToBoarding(boarding),
            PickupEvent.ArrivedAtBoarding,
            PickupEvent.Boarded(bySensor = false),
        )
        assertEquals(PickupPhase.COMPLETED, s.phase)
        assertEquals(boarding, s.boardingSpot)
        assertNull(s.waitingSpot)
        // 완료 후 새 픽업 입력 가능
        val again = PickupReducer.reduce(s, PickupEvent.Planned(request(3)))
        assertEquals(PickupPhase.PLANNING, again.phase)
        assertNull(again.boardingSpot)
    }

    @Test
    fun cancel_returnsToIdleFromAnyPhase() {
        val mid = run(PickupEvent.Planned(request(15)), PickupEvent.StartToWaiting(waiting))
        val s = PickupReducer.reduce(mid, PickupEvent.Cancel)
        assertEquals(PickupPhase.IDLE, s.phase)
        assertNull(s.request)
        assertNull(s.waitingSpot)
        assertEquals("픽업을 취소했습니다.", s.notice)
        val idle = PickupState()
        assertSame(idle, PickupReducer.reduce(idle, PickupEvent.Cancel))
    }

    @Test
    fun invalidTransitions_leaveStateUnchanged() {
        val idle = PickupState()
        assertSame(idle, PickupReducer.reduce(idle, PickupEvent.ArrivedAtBoarding))
        assertSame(idle, PickupReducer.reduce(idle, PickupEvent.StartToBoarding(boarding)))
        assertSame(idle, PickupReducer.reduce(idle, PickupEvent.Boarded(true)))

        val planning = run(PickupEvent.Planned(request(3)))
        assertSame(planning, PickupReducer.reduce(planning, PickupEvent.Parked(true)))
        assertSame(planning, PickupReducer.reduce(planning, PickupEvent.ArrivedAtBoarding))

        val toBoarding = PickupReducer.reduce(planning, PickupEvent.StartToBoarding(boarding))
        assertSame(toBoarding, PickupReducer.reduce(toBoarding, PickupEvent.Boarded(true)))
        assertSame(toBoarding, PickupReducer.reduce(toBoarding, PickupEvent.Planned(request(5))))
        assertSame(toBoarding, PickupReducer.reduce(toBoarding, PickupEvent.Parked(true)))
    }
}
