package com.pickmeup.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.VoiceOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TextToSpeech SDK 어댑터: 짧은 한국어 안내 음성. 사용할 수 없으면 조용히 무시합니다.
 *
 * - Client ID/Secret이 있으면 HYBRID + registerApp, 없으면 앱 등록이 필요 없는 ON_DEVICE 모드.
 */
class PleosVoiceOutput(
    context: Context,
    private val clientId: String,
    private val clientSecret: String,
) : VoiceOutput {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(LinkStatus.CONNECTING)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    @Volatile private var tts: TextToSpeech? = null

    private val eventListener = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TTS 오류: $errorType")
        }
    }

    init {
        setup()
    }

    private fun setup() {
        val useServer = clientId.isNotBlank() && clientSecret.isNotBlank()
        val mode = if (useServer) Mode.HYBRID else Mode.ON_DEVICE
        try {
            val t = TextToSpeech(appContext, mode)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    try {
                        t.addEventListener(eventListener)
                        t.setLocale(Locale.KOREA)
                        if (useServer) {
                            t.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
                                override fun onConnected() {
                                    _status.value = LinkStatus.CONNECTED
                                }

                                override fun onFailed(errorType: ErrorType) {
                                    // HYBRID는 서버 연결 실패 시 On-device 엔진으로 전환됩니다.
                                    Log.w(TAG, "TTS 앱 등록 실패: $errorType (On-device 합성으로 시도)")
                                    _status.value = LinkStatus.CONNECTED
                                }
                            })
                        } else {
                            _status.value = LinkStatus.CONNECTED
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "TTS 설정 실패", e)
                        _status.value = LinkStatus.UNAVAILABLE
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "TTS 초기화 실패: $errorType")
                    _status.value = LinkStatus.UNAVAILABLE
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "TTS SDK 사용 불가", e)
            tts = null
            _status.value = LinkStatus.UNAVAILABLE
        }
    }

    override fun speak(text: String) {
        val t = tts
        if (t == null || _status.value != LinkStatus.CONNECTED || text.isBlank()) return
        try {
            t.speak(text, true)
        } catch (e: Throwable) {
            Log.w(TAG, "TTS speak 실패", e)
        }
    }

    override fun release() {
        val t = tts ?: return
        tts = null
        runCatching { t.stop() }
        runCatching { t.removeEventListener(eventListener) }
        runCatching { t.release() }.onFailure { Log.w(TAG, "TTS 해제 실패", it) }
        _status.value = LinkStatus.UNAVAILABLE
    }

    private companion object {
        const val TAG = "PickMeUp"
    }
}
