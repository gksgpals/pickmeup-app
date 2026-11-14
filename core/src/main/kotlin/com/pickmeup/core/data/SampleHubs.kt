package com.pickmeup.core.data

import com.pickmeup.core.domain.HubRepository
import com.pickmeup.core.model.Entrance
import com.pickmeup.core.model.GeoPoint
import com.pickmeup.core.model.Hub
import com.pickmeup.core.model.PickupSpot
import com.pickmeup.core.model.RoadSide
import com.pickmeup.core.model.SpotType
import com.pickmeup.core.model.TimeWindow

/**
 * 시연용 샘플 거점 저장소.
 *
 * 모든 거점·지점은 기능 시연을 위한 **가상 데이터**입니다. 이름은 실존 시설이 아니며,
 * 좌표는 서울 역삼·대치 일대의 대략적인 위치를 빌려 왔을 뿐 실제 승차 가능 여부·운영 조건을
 * 현장에서 확인한 자료가 아닙니다. 각 지점의 [PickupSpot.source]에 그 사실을 명시합니다.
 */
class SampleHubRepository : HubRepository {

    override fun all(): List<Hub> = HUBS

    override fun byId(id: String): Hub? = HUBS.firstOrNull { it.id == id }

    companion object {
        /** 모든 샘플 지점의 출처 표기. 실제 검증 자료처럼 보이지 않도록 고정 문구를 씁니다. */
        const val SAMPLE_SOURCE = "시연용 샘플 데이터 (현장 확인 전) · 가상 거점, 운영 조건 미검증"

        const val HANBIT_ID = "hanbit-plaza"
        const val SAESOL_ID = "saesol-hospital"
        const val NURI_ID = "nuri-academy"

        // ───────────────────────── 1. 한빛상가 (역삼 일대, 가상) ─────────────────────────
        private val HANBIT_EAST = Entrance(
            id = "hanbit-east",
            name = "동문",
            aliases = listOf("동문", "동쪽 문", "동쪽 출입구", "동쪽 입구", "약국 쪽 출입구"),
        )
        private val HANBIT_MAIN = Entrance(
            id = "hanbit-main",
            name = "정문",
            aliases = listOf("정문", "앞문", "정면 출입구", "메인 출입구"),
        )
        private val HANBIT_PARKING = Entrance(
            id = "hanbit-parking",
            name = "주차장 입구",
            aliases = listOf("주차장 입구", "주차장 출입구", "지하주차장 입구", "주차장 쪽"),
        )

        private val HANBIT_SPOTS = listOf(
            PickupSpot(
                id = "hanbit-s1",
                name = "동문 앞 승차 포켓",
                type = SpotType.CURBSIDE_POCKET,
                stopPoint = GeoPoint(37.50112, 127.03795),
                approach = "상가 앞 큰길에서 오른쪽 끝 차로를 유지하다가 동문 앞 승차 포켓으로 진입",
                roadSide = RoadSide.RIGHT,
                meetingSign = "동문 바깥 파란 약국 간판 앞",
                walkMeters = 15,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-HB-01",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "hanbit-s2",
                name = "정문 회차 승하차장",
                type = SpotType.BUILDING_DROPOFF,
                stopPoint = GeoPoint(37.50071, 127.03702),
                approach = "상가 정면 진입로로 들어와 차단기 앞 회차 구역에서 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "정문 회전문 앞 차양 아래",
                walkMeters = 25,
                hasStepsOrCurb = true,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-HB-02",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "hanbit-s3",
                name = "동문 옆 외부 승하차 구역",
                type = SpotType.BUILDING_DROPOFF,
                stopPoint = GeoPoint(37.50131, 127.03842),
                approach = "상가 동쪽 이면 2차로로 우회전 진입 후 꽃집 앞 승하차 구역에 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "동문 나와서 오른쪽, 노란 꽃집 차양 앞",
                walkMeters = 40,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-HB-05",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "hanbit-s4",
                name = "뒷골목 노상 주차 구역",
                type = SpotType.ON_STREET_PARKING,
                stopPoint = GeoPoint(37.50165, 127.03810),
                approach = "상가 뒤편 일방통행 골목으로 진입, 노상 주차 구획에 정차",
                roadSide = RoadSide.LEFT,
                meetingSign = "상가 뒤편 편의점 앞 가로등 옆",
                walkMeters = 120,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = true,
                freeWaitMinutes = 0,
                approachLinkId = null, // 골목: 교통정보 없음
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "hanbit-s5",
                name = "한빛상가 지하주차장 (무료 회차 30분)",
                type = SpotType.PARKING_LOT,
                stopPoint = GeoPoint(37.50052, 127.03760),
                approach = "주차장 입구로 진입해 지하 1층 엘리베이터 앞 구역에 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "지하 1층 엘리베이터 홀 앞",
                walkMeters = 90,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = true,
                freeWaitMinutes = 30,
                available = TimeWindow(7 * 60, 23 * 60),
                approachLinkId = "LINK-HB-04",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "hanbit-s6",
                name = "근린공원 옆 노상 주차 구역 (무료)",
                type = SpotType.ON_STREET_PARKING,
                stopPoint = GeoPoint(37.50230, 127.03905),
                approach = "상가 동쪽 이면도로를 따라 공원 방향으로 직진, 공원 담장 옆 구획에 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "근린공원 정문 앞 벤치",
                walkMeters = 260,
                hasStepsOrCurb = false,
                boardingAllowed = false,
                waitingAllowed = true,
                freeWaitMinutes = null,
                approachLinkId = "LINK-HB-06",
                source = SAMPLE_SOURCE,
            ),
        )

        private val HANBIT = Hub(
            id = HANBIT_ID,
            name = "한빛상가",
            aliases = listOf("한빛 상가", "한빛플라자", "한빛 플라자"),
            roadAddress = "서울 강남구 역삼동 일대 (시연용 가상 거점)",
            center = GeoPoint(37.50090, 127.03770),
            entrances = listOf(HANBIT_EAST, HANBIT_MAIN, HANBIT_PARKING),
            spots = HANBIT_SPOTS,
            entranceIds = mapOf(
                "hanbit-s1" to listOf(HANBIT_EAST.id),
                "hanbit-s2" to listOf(HANBIT_MAIN.id),
                "hanbit-s3" to listOf(HANBIT_EAST.id),
                "hanbit-s4" to listOf(HANBIT_EAST.id, HANBIT_PARKING.id),
                // hanbit-s5, hanbit-s6: 모든 출입구
            ),
        )

        // ───────────────────────── 2. 새솔병원 (선릉 일대, 가상) ─────────────────────────
        private val SAESOL_MAIN = Entrance(
            id = "saesol-main",
            name = "본관 정문",
            aliases = listOf("본관 정문", "정문", "본관", "앞문"),
        )
        private val SAESOL_BACK = Entrance(
            id = "saesol-back",
            name = "후문",
            aliases = listOf("후문", "뒷문", "별관 쪽", "별관 출입구"),
        )

        private val SAESOL_SPOTS = listOf(
            PickupSpot(
                id = "saesol-s1",
                name = "본관 정문 앞 승하차 베이",
                type = SpotType.CURBSIDE_POCKET,
                stopPoint = GeoPoint(37.50438, 127.05205),
                approach = "병원 앞 큰길 오른쪽 차로에서 정문 앞 승하차 베이로 진입",
                roadSide = RoadSide.RIGHT,
                meetingSign = "본관 정문 경사로 끝, 초록 십자 간판 아래",
                walkMeters = 20,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-SS-01",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "saesol-s2",
                name = "본관 캐노피 승하차장",
                type = SpotType.BUILDING_DROPOFF,
                stopPoint = GeoPoint(37.50460, 127.05180),
                approach = "병원 구내 진입로로 들어와 주차장 차단기 앞 캐노피 아래에 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "본관 로비 자동문 앞 캐노피 아래",
                walkMeters = 10,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-SS-02",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "saesol-s3",
                name = "후문 승하차 구역",
                type = SpotType.BUILDING_DROPOFF,
                stopPoint = GeoPoint(37.50495, 127.05232),
                approach = "병원 뒤편 골목으로 진입해 후문 앞 흰색 구획에 정차",
                roadSide = RoadSide.LEFT,
                meetingSign = "후문 계단 아래 자판기 옆",
                walkMeters = 35,
                hasStepsOrCurb = true,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = null, // 골목: 교통정보 없음
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "saesol-s4",
                name = "병원 앞 노상 주차 구역",
                type = SpotType.ON_STREET_PARKING,
                stopPoint = GeoPoint(37.50402, 127.05255),
                approach = "병원 앞 큰길에서 첫 번째 골목 직전 노상 주차 구획에 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "횡단보도 건너 버스정류장 뒤 화단 앞",
                walkMeters = 150,
                hasStepsOrCurb = true,
                boardingAllowed = true,
                waitingAllowed = true,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-SS-03",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "saesol-s5",
                name = "새솔병원 주차장 (외래 무료 회차 30분)",
                type = SpotType.PARKING_LOT,
                stopPoint = GeoPoint(37.50475, 127.05150),
                approach = "구내 진입로 끝 주차장 차단기로 진입, 지상 1층 엘리베이터 앞에 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "주차동 1층 엘리베이터 앞",
                walkMeters = 70,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = true,
                freeWaitMinutes = 30,
                approachLinkId = "LINK-SS-04",
                source = SAMPLE_SOURCE,
            ),
        )

        private val SAESOL = Hub(
            id = SAESOL_ID,
            name = "새솔병원",
            aliases = listOf("새솔 병원", "새솔메디컬", "새솔 메디컬센터"),
            roadAddress = "서울 강남구 대치동 일대 (시연용 가상 거점)",
            center = GeoPoint(37.50450, 127.05200),
            entrances = listOf(SAESOL_MAIN, SAESOL_BACK),
            spots = SAESOL_SPOTS,
            entranceIds = mapOf(
                "saesol-s1" to listOf(SAESOL_MAIN.id),
                "saesol-s2" to listOf(SAESOL_MAIN.id),
                "saesol-s3" to listOf(SAESOL_BACK.id),
            ),
        )

        // ───────────────────────── 3. 누리학원가 (대치 일대, 가상) ─────────────────────────
        private val NURI_MAIN = Entrance(
            id = "nuri-main",
            name = "누리빌딩 정문",
            aliases = listOf("누리빌딩 정문", "빌딩 정문", "정문", "앞문"),
        )
        private val NURI_BACK = Entrance(
            id = "nuri-back",
            name = "후문",
            aliases = listOf("후문", "뒷문", "골목 쪽 출입구"),
        )
        private val NURI_PARKING = Entrance(
            id = "nuri-parking",
            name = "주차장 입구",
            aliases = listOf("주차장 입구", "주차장 출입구", "주차장 쪽"),
        )

        private val NURI_SPOTS = listOf(
            PickupSpot(
                id = "nuri-s1",
                name = "정문 앞 승차 포켓",
                type = SpotType.CURBSIDE_POCKET,
                stopPoint = GeoPoint(37.49925, 127.06095),
                approach = "학원가 큰길 오른쪽 차로에서 누리빌딩 정문 앞 승차 포켓으로 진입",
                roadSide = RoadSide.RIGHT,
                meetingSign = "누리빌딩 정문 앞 빨간 우체통 옆",
                walkMeters = 15,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                available = TimeWindow(6 * 60, 24 * 60),
                approachLinkId = "LINK-NR-01",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "nuri-s2",
                name = "빌딩 앞 외부 회차로",
                type = SpotType.BUILDING_DROPOFF,
                stopPoint = GeoPoint(37.49940, 127.06060),
                approach = "누리빌딩 옆 진입로로 들어와 주차장 차단기 앞 회차로에서 정차",
                roadSide = RoadSide.RIGHT,
                meetingSign = "빌딩 1층 카페 앞 보도",
                walkMeters = 30,
                hasStepsOrCurb = true,
                boardingAllowed = true,
                waitingAllowed = false,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-NR-02",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "nuri-s3",
                name = "후문 골목 노상 주차 구역",
                type = SpotType.ON_STREET_PARKING,
                stopPoint = GeoPoint(37.49970, 127.06120),
                approach = "학원가 뒤편 골목으로 진입, 후문 맞은편 노상 주차 구획에 정차",
                roadSide = RoadSide.LEFT,
                meetingSign = "후문 나와서 왼쪽, 문구점 간판 앞",
                walkMeters = 60,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = true,
                freeWaitMinutes = 10,
                available = TimeWindow(7 * 60, 21 * 60),
                approachLinkId = null, // 골목: 교통정보 없음
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "nuri-s4",
                name = "누리공영주차장 (무료 회차 30분)",
                type = SpotType.PARKING_LOT,
                stopPoint = GeoPoint(37.49870, 127.06150),
                approach = "학원가 큰길에서 한 블록 지나 우회전, 공영주차장 입구로 진입",
                roadSide = RoadSide.RIGHT,
                meetingSign = "공영주차장 1층 정산소 앞",
                walkMeters = 180,
                hasStepsOrCurb = false,
                boardingAllowed = true,
                waitingAllowed = true,
                freeWaitMinutes = 30,
                approachLinkId = "LINK-NR-03",
                source = SAMPLE_SOURCE,
            ),
            PickupSpot(
                id = "nuri-s5",
                name = "학원가 민영주차장 (유료)",
                type = SpotType.PARKING_LOT,
                stopPoint = GeoPoint(37.49900, 127.06010),
                approach = "누리빌딩 맞은편 골목 첫 건물 지하주차장으로 진입",
                roadSide = RoadSide.LEFT,
                meetingSign = "민영주차장 출구 앞",
                walkMeters = 120,
                hasStepsOrCurb = true,
                boardingAllowed = false,
                waitingAllowed = true,
                freeWaitMinutes = 0,
                approachLinkId = "LINK-NR-04",
                source = SAMPLE_SOURCE,
            ),
        )

        private val NURI = Hub(
            id = NURI_ID,
            name = "누리학원가",
            aliases = listOf("누리 학원가", "누리학원", "누리빌딩", "누리상가", "누리 학원 상가"),
            roadAddress = "서울 강남구 대치동 일대 (시연용 가상 거점)",
            center = GeoPoint(37.49930, 127.06090),
            entrances = listOf(NURI_MAIN, NURI_BACK, NURI_PARKING),
            spots = NURI_SPOTS,
            entranceIds = mapOf(
                "nuri-s1" to listOf(NURI_MAIN.id),
                "nuri-s2" to listOf(NURI_MAIN.id, NURI_PARKING.id),
                "nuri-s3" to listOf(NURI_BACK.id),
            ),
        )

        private val HUBS: List<Hub> = listOf(HANBIT, SAESOL, NURI)
    }
}
