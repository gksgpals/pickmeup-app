package com.pickmeup.app.platform

import android.content.Context
import com.pickmeup.app.BuildConfig
import com.pickmeup.app.platform.pleos.PleosNaviController
import com.pickmeup.app.platform.pleos.PleosSpeechInput
import com.pickmeup.app.platform.pleos.PleosVehicleSignals
import com.pickmeup.app.platform.pleos.PleosVoiceOutput

/**
 * Pleos Connect SDK 어댑터로 Platform을 만듭니다.
 * 각 어댑터는 SDK 서비스가 없는 기기에서도 예외를 삼키고 UNAVAILABLE 상태로 동작합니다.
 * VehicleSignals·NaviController는 UI가 start()/stop()을 호출하고,
 * SpeechInput·VoiceOutput은 생성 시 초기화되며 release()로 해제합니다.
 */
object PlatformFactory {
    fun create(context: Context): Platform {
        val app = context.applicationContext
        val vehicle = PleosVehicleSignals(app)
        return Platform(
            vehicle = vehicle,
            navi = PleosNaviController(app),
            speech = PleosSpeechInput(app, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET),
            voice = PleosVoiceOutput(app, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET),
            notifier = AndroidPassengerNotifier(app, BuildConfig.SMS_SERVER_URL, vehicle.parkingBrakeOn),
        )
    }
}
