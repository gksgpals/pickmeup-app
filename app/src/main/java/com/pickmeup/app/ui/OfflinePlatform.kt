package com.pickmeup.app.ui

import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.NaviController
import com.pickmeup.app.platform.NotifyResult
import com.pickmeup.app.platform.PassengerNotifier
import com.pickmeup.app.platform.Platform
import com.pickmeup.app.platform.SpeechInput
import com.pickmeup.app.platform.VehicleSignals
import com.pickmeup.app.platform.VoiceOutput
import com.pickmeup.core.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 최후 안전장치: PlatformFactory 생성 자체가 실패했을 때만 사용합니다.
 * 모든 연동을 "UNAVAILABLE"로 보고하므로 앱은 수동 확인·화면 입력·화면 안내 폴백으로 끝까지 동작합니다.
 */
internal object OfflinePlatform {
    fun create(): Platform = Platform(
        vehicle = object : VehicleSignals {
            override val status: StateFlow<LinkStatus> = MutableStateFlow(LinkStatus.UNAVAILABLE)
            override val parkingBrakeOn: StateFlow<Boolean?> = MutableStateFlow(null)
            override val anyDoorOpen: StateFlow<Boolean?> = MutableStateFlow(null)
            override fun start() {}
            override fun stop() {}
        },
        navi = object : NaviController {
            override val status: StateFlow<LinkStatus> = MutableStateFlow(LinkStatus.UNAVAILABLE)
            private val event = MutableStateFlow<String?>(null)
            override val lastEvent: StateFlow<String?> = event
            private val arrivedFlow = MutableStateFlow(false)
            override val arrived: StateFlow<Boolean> = arrivedFlow
            override fun start() {}
            override fun stop() {}
            override fun requestRoute(point: GeoPoint, poiName: String, address: String) {
                arrivedFlow.value = false
                event.value = "내비 연결 불가: '$poiName' 안내는 화면으로만 표시합니다"
            }
            override fun cancelRoute() {}
        },
        speech = object : SpeechInput {
            override val status: StateFlow<LinkStatus> = MutableStateFlow(LinkStatus.UNAVAILABLE)
            override val listening: StateFlow<Boolean> = MutableStateFlow(false)
            override fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
                onError("음성 인식을 사용할 수 없어 화면 입력으로 전환합니다")
            }
            override fun cancel() {}
            override fun release() {}
        },
        voice = object : VoiceOutput {
            override val status: StateFlow<LinkStatus> = MutableStateFlow(LinkStatus.UNAVAILABLE)
            override fun speak(text: String) {}
            override fun release() {}
        },
        notifier = object : PassengerNotifier {
            override suspend fun notify(phoneNumber: String?, message: String): NotifyResult =
                NotifyResult.Fallback("문자 전송 기능을 사용할 수 없습니다", message)
        },
    )
}
