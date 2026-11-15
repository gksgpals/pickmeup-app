# 픽미업 앱 구조

## 모듈

| 모듈 | 내용 |
| --- | --- |
| `core` (순수 Kotlin) | 거점·승차 지점 데이터, 4단계 우선순위 추천, 음성 문장 해석, 픽업 상태 머신, 합류 안내 문구 |
| `app` (Android, Jetpack Compose) | 차량 화면 UI, Pleos Connect SDK 연동(Vehicle, NaviHelper, SpeechToText, TextToSpeech), 동승자 알림 |

## 패키지와 담당 파일

```
core/src/main/kotlin/com/pickmeup/core/
  model/Models.kt            공용 모델 (계약 — 수정 금지)
  domain/Contracts.kt        TrafficProvider, HubRepository, PickupPhase/Event/State (계약 — 수정 금지)
  domain/UtteranceParser.kt  음성 문장 → 거점·출입구·준비 시간·동승자 조건
  domain/SpotRecommender.kt  4단계 우선순위 + 교통 반영 재정렬 + 대기 장소 판단
  domain/SimulatedTraffic.kt 시연용 교통정보 제공자
  domain/PickupReducer.kt    픽업 상태 머신
  domain/MessageComposer.kt  동승자 합류 안내 문구
  data/SampleHubs.kt         시연용 샘플 거점 데이터 (SampleHubRepository)

app/src/main/java/com/pickmeup/app/
  platform/Ports.kt                 플랫폼 포트 인터페이스 + Platform (계약 — 수정 금지)
  platform/PlatformFactory.kt       Platform 생성
  platform/AndroidPassengerNotifier.kt  차량 문자 앱 / 서버 문자 / 화면 폴백
  platform/pleos/*.kt               Pleos SDK 어댑터
  MainActivity.kt, ui/**            Compose 화면, PickupViewModel
```

## core 공개 API (구현 담당과 UI 담당이 함께 지키는 시그니처)

```kotlin
package com.pickmeup.core.data
class SampleHubRepository : HubRepository            // all(), byId(id)

package com.pickmeup.core.domain
const val WAIT_THRESHOLD_MINUTES = 10                 // 준비 시간이 이 값 이상이면 대기 장소 제안

class UtteranceParser(private val hubs: HubRepository) {
    fun parse(text: String): ParsedUtterance
}

class SpotRecommender(private val traffic: TrafficProvider) {
    /** 승차 지점 후보 정렬(boardingAllowed && 이용 시간 내 && 동승자 조건 충족). minuteOfDay = 0..1439 */
    fun rankBoarding(request: PickupRequest, minuteOfDay: Int): List<RankedSpot>
    /** 대기 장소 판단. readyInMinutes < WAIT_THRESHOLD_MINUTES 이면 shouldWait=false */
    fun adviseWaiting(request: PickupRequest, minuteOfDay: Int): WaitAdvice
}

class SimulatedTraffic(initial: Map<String, TrafficLevel> = emptyMap()) : TrafficProvider {
    fun set(linkId: String, level: TrafficLevel)
    fun snapshot(): Map<String, TrafficLevel>
}

object PickupReducer {
    fun reduce(state: PickupState, event: PickupEvent): PickupState
}

object MessageComposer {
    /** 예: "[픽미업] 친구님, 한빛상가 동문 바깥 파란 약국 간판 앞에서 만나요. 약 2분 뒤 도착 예정입니다." */
    fun meetingMessage(request: PickupRequest, boarding: PickupSpot, etaMinutes: Int): String
}
```

## 픽업 흐름

```
IDLE ─(음성/터치 입력)→ PLANNING ─┬─ 준비 시간 ≥ 10분 → TO_WAITING → WAITING(대기 장소 도착 + EPB/각각 수동 확인) ─(동승자 준비)→ TO_BOARDING
                                 └─ 준비 시간 < 10분 또는 이미 나와 있음 ───────────────────────────→ TO_BOARDING
TO_BOARDING ─(도착)→ BOARDING ─(동승자 문 개폐 또는 수동 확인 요청 → 운전자 확정)→ COMPLETED
```
