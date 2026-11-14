package com.pickmeup.app.platform

import com.pickmeup.core.model.GeoPoint
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 플랫폼 연동 포트 (공용 계약).
 * - Pleos Connect SDK 구현: com.pickmeup.app.platform.pleos 패키지
 * - SDK 연결 실패·미지원 시에도 앱이 멈추지 않도록 각 구현은 예외를 삼키고 상태로 알려야 합니다.
 * UI(ViewModel)는 이 인터페이스만 사용합니다. 시그니처를 바꾸지 마세요.
 */

/** 연동 상태. UI에 "센서 연결됨 / 수동 확인" 등을 표시하는 데 씁니다. */
enum class LinkStatus { CONNECTING, CONNECTED, UNAVAILABLE }

/** Vehicle SDK: 주차 브레이크(EPB)와 도어 개폐 신호. */
interface VehicleSignals {
    val status: StateFlow<LinkStatus>
    /** 주차 브레이크 체결 여부. 모르면 null. */
    val parkingBrakeOn: StateFlow<Boolean?>
    /** 도어(운전석 제외 포함 아무 도어)가 열려 있는지. 모르면 null. */
    val anyDoorOpen: StateFlow<Boolean?>
    fun start()
    fun stop()
}

/** NaviHelper SDK: Pleos 기본 내비에 목적지 전달. */
interface NaviController {
    val status: StateFlow<LinkStatus>
    /** 마지막 요청 결과 메시지(예: "경로 탐색 완료", "내비 연결 실패: …"). */
    val lastEvent: StateFlow<String?>
    /** 목적지 안내가 끝났을 때(도착) true로 바뀝니다. requestRoute 호출 시 false로 초기화. */
    val arrived: StateFlow<Boolean>
    fun start()
    fun stop()
    fun requestRoute(point: GeoPoint, poiName: String, address: String)
    fun cancelRoute()
}

/** SpeechToText SDK: 운전자가 마이크 버튼을 눌렀을 때만 1회 인식. */
interface SpeechInput {
    val status: StateFlow<LinkStatus>
    /** 인식 중인지. */
    val listening: StateFlow<Boolean>
    /**
     * 1회 인식 시작. 결과(최종 문장)는 onResult, 실패는 onError(사용자용 한국어 메시지).
     * 사용할 수 없으면 즉시 onError를 호출합니다(UI는 화면 입력으로 폴백).
     */
    fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun cancel()
    fun release()
}

/** TextToSpeech SDK: 짧은 안내 음성. 사용할 수 없으면 조용히 무시. */
interface VoiceOutput {
    val status: StateFlow<LinkStatus>
    fun speak(text: String)
    fun release()
}

/** 동승자 알림 전송 결과. */
sealed interface NotifyResult {
    /** channel: "차량 문자 앱", "픽미업 서버 문자" 등 */
    data class Sent(val channel: String) : NotifyResult
    /** 전송 불가 → 화면에 합류 안내 문구를 띄워 운전자가 정차 중 직접 전달 */
    data class Fallback(val reason: String, val message: String) : NotifyResult
}

/** 동승자 알림: ① 차량 문자 앱 연동 ② 자체 서버 문자 ③ 화면 안내 폴백. */
interface PassengerNotifier {
    suspend fun notify(phoneNumber: String?, message: String): NotifyResult
}

/** 앱이 쓰는 플랫폼 연동 묶음. 생성은 PlatformFactory.create(context) (platform 패키지). */
data class Platform(
    val vehicle: VehicleSignals,
    val navi: NaviController,
    val speech: SpeechInput,
    val voice: VoiceOutput,
    val notifier: PassengerNotifier,
)
