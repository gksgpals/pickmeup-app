package com.pickmeup.core

import com.pickmeup.core.data.SampleHubRepository
import com.pickmeup.core.domain.SimulatedTraffic
import com.pickmeup.core.domain.SpotRecommender
import com.pickmeup.core.domain.WAIT_THRESHOLD_MINUTES
import com.pickmeup.core.model.Entrance
import com.pickmeup.core.model.GeoPoint
import com.pickmeup.core.model.Hub
import com.pickmeup.core.model.PassengerNeeds
import com.pickmeup.core.model.PickupRequest
import com.pickmeup.core.model.PickupSpot
import com.pickmeup.core.model.RoadSide
import com.pickmeup.core.model.SpotType
import com.pickmeup.core.model.TrafficLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotRecommenderTest {
    private val repo = SampleHubRepository()
    private val hanbit = repo.byId(SampleHubRepository.HANBIT_ID)!!
    private val saesol = repo.byId(SampleHubRepository.SAESOL_ID)!!
    private val nuri = repo.byId(SampleHubRepository.NURI_ID)!!
    private val east = hanbit.entrances.first { it.id == "hanbit-east" }
    private val tenAm = 10 * 60

    private fun req(hub: Hub, entrance: Entrance? = null, ready: Int = 3, needs: PassengerNeeds = PassengerNeeds()) =
        PickupRequest(hub = hub, entrance = entrance, readyInMinutes = ready, passengerName = "친구", needs = needs)

    @Test
    fun priorityOrder_forEastEntrance() {
        val ranked = SpotRecommender(SimulatedTraffic()).rankBoarding(req(hanbit, east), tenAm)
        assertEquals(listOf("hanbit-s1", "hanbit-s3", "hanbit-s4", "hanbit-s5"), ranked.map { it.spot.id })
        assertEquals(listOf(1, 2, 3, 4), ranked.map { it.rank })
        assertTrue(ranked.none { it.reordered })
        assertTrue(ranked.all { it.reasons.isNotEmpty() && it.spot.boardingAllowed })
        assertTrue(ranked.first().reasons.any { it.contains("주행차로 옆 승차 공간") })
        assertTrue(ranked.first().reasons.contains("출입구에서 도보 15m"))
    }

    @Test
    fun noEntrance_includesAllBoardingSpots() {
        val ranked = SpotRecommender(SimulatedTraffic()).rankBoarding(req(hanbit), tenAm)
        assertEquals(listOf("hanbit-s1", "hanbit-s2", "hanbit-s3", "hanbit-s4", "hanbit-s5"), ranked.map { it.spot.id })
    }

    @Test
    fun unknownTraffic_keepsBaseOrderWithReason() {
        val ranked = SpotRecommender(SimulatedTraffic()).rankBoarding(req(hanbit, east), tenAm)
        val alley = ranked.first { it.spot.id == "hanbit-s4" }
        assertEquals(TrafficLevel.UNKNOWN, alley.traffic)
        assertEquals(3, alley.rank)
        assertFalse(alley.reordered)
        assertTrue(alley.reasons.contains("교통정보 없음 — 기본 우선순위로 안내"))
        assertEquals(TrafficLevel.SMOOTH, ranked.first().traffic)
    }

    @Test
    fun congestion_demotesTopSpot_andExplains() {
        val traffic = SimulatedTraffic(mapOf("LINK-HB-01" to TrafficLevel.CONGESTED))
        val ranked = SpotRecommender(traffic).rankBoarding(req(hanbit, east), tenAm)
        assertEquals(listOf("hanbit-s3", "hanbit-s4", "hanbit-s5", "hanbit-s1"), ranked.map { it.spot.id })
        val promoted = ranked[0]
        assertTrue(promoted.reordered)
        assertTrue(promoted.reasons.contains("1순위 진입로가 정체되어 접근이 원활한 이 지점을 먼저 추천합니다"))
        val demoted = ranked.last()
        assertEquals(TrafficLevel.CONGESTED, demoted.traffic)
        assertEquals(4, demoted.rank)
        assertTrue(demoted.reordered)
        assertTrue(demoted.reasons.contains("진입로 정체로 순위를 낮췄습니다"))
    }

    @Test
    fun slowTraffic_demotesLess() {
        val traffic = SimulatedTraffic()
        traffic.set("LINK-HB-01", TrafficLevel.SLOW)
        val ranked = SpotRecommender(traffic).rankBoarding(req(hanbit, east), tenAm)
        assertEquals(listOf("hanbit-s3", "hanbit-s1", "hanbit-s4", "hanbit-s5"), ranked.map { it.spot.id })
        assertTrue(ranked[1].reasons.contains("진입로 서행으로 순위를 낮췄습니다"))
    }

    @Test
    fun mobility_excludesStepsOrCurb() {
        val needs = PassengerNeeds(mobilityImpaired = true)
        val ranked = SpotRecommender(SimulatedTraffic()).rankBoarding(req(saesol, needs = needs), tenAm)
        assertEquals(listOf("saesol-s1", "saesol-s2", "saesol-s5"), ranked.map { it.spot.id })
        assertTrue(ranked.none { it.spot.hasStepsOrCurb })
        assertTrue(ranked.all { it.reasons.contains("계단·턱 없음") })
    }

    @Test
    fun heavyLuggage_prefersShortWalk() {
        fun spot(id: String, type: SpotType, walk: Int) = PickupSpot(
            id = id, name = id, type = type, stopPoint = GeoPoint(37.5, 127.0), approach = "-",
            roadSide = RoadSide.RIGHT, meetingSign = "-", walkMeters = walk, hasStepsOrCurb = false,
            boardingAllowed = true, waitingAllowed = false, freeWaitMinutes = 0, approachLinkId = "L-$id",
            source = SampleHubRepository.SAMPLE_SOURCE,
        )
        val hub = Hub(
            id = "h", name = "테스트", aliases = emptyList(), roadAddress = "-", center = GeoPoint(37.5, 127.0),
            entrances = emptyList(),
            spots = listOf(spot("far-curb", SpotType.CURBSIDE_POCKET, 200), spot("near-drop", SpotType.BUILDING_DROPOFF, 20)),
        )
        val recommender = SpotRecommender(SimulatedTraffic())
        assertEquals("far-curb", recommender.rankBoarding(req(hub), tenAm).first().spot.id)
        val luggage = recommender.rankBoarding(req(hub, needs = PassengerNeeds(heavyLuggage = true)), tenAm)
        assertEquals("near-drop", luggage.first().spot.id)
        assertTrue(luggage.first().reordered)
        assertTrue(luggage.first().reasons.contains("짐이 많아 도보 거리가 짧은 이 지점을 앞당겼습니다"))
    }

    @Test
    fun timeWindow_filtersClosedSpots() {
        val recommender = SpotRecommender(SimulatedTraffic())
        assertTrue(recommender.rankBoarding(req(nuri), tenAm).any { it.spot.id == "nuri-s3" })
        val night = recommender.rankBoarding(req(nuri), 22 * 60)
        assertFalse(night.any { it.spot.id == "nuri-s3" })
        assertEquals(listOf("nuri-s1", "nuri-s2", "nuri-s4"), night.map { it.spot.id })
    }

    @Test
    fun waitThreshold_usesSlackRatherThanPassengerPreparation() {
        val recommender = SpotRecommender(SimulatedTraffic())
        val boarding = hanbit.spots.first { it.boardingAllowed }
        fun advice(remaining: Int) = recommender.adviseWaiting(req(hanbit, ready = remaining), tenAm, boarding, remaining, 5) { _, _ -> 1 }
        for (remaining in listOf(0, 5, 14)) {
            assertFalse(advice(remaining).shouldWait)
            assertTrue(advice(remaining).waitingCandidates.isEmpty())
        }
        val ten = advice(15)
        assertTrue(ten.shouldWait)
        assertTrue(ten.message.contains("시간 여유 10분"))
        assertTrue(ten.waitingCandidates.first().reasons.first().contains("실제 대기 가능 약 9분"))
    }

    @Test
    fun waiting_excludesDetoursWithoutWaitingTimeAndUsesActualWaitForFreeClass() {
        val recommender = SpotRecommender(SimulatedTraffic())
        val boarding = nuri.spots.first { it.boardingAllowed }
        val result = recommender.adviseWaiting(req(nuri, ready = 25), tenAm, boarding, 25, 5) { spot, _ ->
            when (spot.id) { "nuri-s3" -> 12; "nuri-s5" -> 20; else -> 1 }
        }
        assertTrue(result.shouldWait)
        assertFalse(result.waitingCandidates.any { it.spot.id == "nuri-s5" })
        val partial = result.waitingCandidates.first { it.spot.id == "nuri-s3" }
        assertTrue(partial.reasons[1].contains("예상 대기 8분"))
        val none = recommender.adviseWaiting(req(nuri), tenAm, boarding, 15, 5) { _, _ -> 10 }
        assertFalse(none.shouldWait)
        assertTrue(none.waitingCandidates.isEmpty())
    }

    @Test
    fun simulatedTraffic_defaults() {
        val t = SimulatedTraffic()
        assertEquals(TrafficLevel.UNKNOWN, t.levelOf(null))
        assertEquals(TrafficLevel.SMOOTH, t.levelOf("ANY"))
        t.set("ANY", TrafficLevel.CONGESTED)
        assertEquals(TrafficLevel.CONGESTED, t.levelOf("ANY"))
        assertEquals(mapOf("ANY" to TrafficLevel.CONGESTED), t.snapshot())
    }

    @Test
    fun sampleData_isLabelledAsUnverifiedAndCoversAllTypes() {
        for (hub in repo.all()) {
            assertEquals(SpotType.values().toSet(), hub.spots.map { it.type }.toSet())
            assertTrue(hub.spots.all { it.source.contains("시연용 샘플 데이터 (현장 확인 전)") })
            val spotIds = hub.spots.map { it.id }.toSet()
            val entranceIds = hub.entrances.map { it.id }.toSet()
            hub.entranceIds.forEach { (spotId, ids) ->
                assertTrue(spotId in spotIds)
                assertTrue(entranceIds.containsAll(ids))
            }
        }
        assertEquals(3, repo.all().size)
    }
}
