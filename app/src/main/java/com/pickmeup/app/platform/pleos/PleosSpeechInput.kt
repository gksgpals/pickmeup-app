package com.pickmeup.app.platform.pleos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.SpeechInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * SpeechToText SDK 어댑터. 운전자가 마이크 버튼을 눌렀을 때(listenOnce)만 1회 인식합니다.
 *
 * - 차량 마이크 음성은 Pleos Speech Service로만 처리합니다(Android SpeechRecognizer·외부 클라우드 STT 사용 안 함).
 *   마이크는 Speech Service가 직접 열기 때문에 앱은 RECORD_AUDIO 권한이 필요 없습니다(SDK 문서상 STT_SERVICE만 필요).
 * - Client ID/Secret이 있으면 HYBRID 모드 + registerApp(서버 인식 우선, 실패 시 차량 On-device 엔진).
 *   없으면 앱 등록이 필요 없는 ON_DEVICE 모드(차량 내장 엔진)로 동작합니다.
 * - 초기화 실패 시 UNAVAILABLE → listenOnce는 즉시 onError로 화면 입력 폴백을 안내합니다.
 * - 결과·오류 콜백은 항상 메인 스레드에서 정확히 한 번 호출됩니다.
 */
class PleosSpeechInput(
    context: Context,
    private val clientId: String,
    private val clientSecret: String,
) : SpeechInput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(LinkStatus.CONNECTING)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private var stt: SpeechToText? = null

    // 아래 상태는 메인 스레드에서만 다룹니다.
    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var lastText: String = ""
    private val timeout = Runnable {
        Log.w(TAG, "STT 시간 초과")
        runCatching { stt?.stop() }
        finishError("음성을 인식하지 못했어요. 다시 눌러 말씀하시거나 화면에서 선택해 주세요")
    }

    private val resultListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            main.post {
                if (stt.isNotBlank()) lastText = stt
                if (completed && stt.isNotBlank()) {
                    finishResult(stt.trim())
                    runCatching { this@PleosSpeechInput.stt?.stop() }
                }
            }
        }

        override fun onEndedRecognition() {
            main.post {
                if (lastText.isNotBlank()) finishResult(lastText.trim())
                else finishError("음성을 인식하지 못했어요. 다시 눌러 말씀하시거나 화면에서 선택해 주세요")
            }
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "STT 오류: $errorType")
            main.post { finishError(describe(errorType)) }
        }
    }

    init {
        setup()
    }

    private fun setup() {
        val useServer = clientId.isNotBlank() && clientSecret.isNotBlank()
        val mode = if (useServer) Mode.HYBRID else Mode.ON_DEVICE
        try {
            val s = SpeechToText(appContext, mode)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    try {
                        s.addListener(resultListener)
                        s.setLocale(Locale.KOREA)
                        if (useServer) {
                            s.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
                                override fun onConnected() {
                                    _status.value = LinkStatus.CONNECTED
                                }

                                override fun onFailed(errorType: ErrorType) {
                                    // HYBRID는 서버 연결이 안 되면 On-device 엔진으로 전환되므로 사용 가능으로 둡니다.
                                    Log.w(TAG, "STT 앱 등록 실패: $errorType (On-device 인식으로 시도)")
                                    _status.value = LinkStatus.CONNECTED
                                }
                            })
                        } else {
                            _status.value = LinkStatus.CONNECTED
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "STT 설정 실패", t)
                        _status.value = LinkStatus.UNAVAILABLE
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "STT 초기화 실패: $errorType")
                    _status.value = LinkStatus.UNAVAILABLE
                }
            })
            Log.i(TAG, "STT 초기화 요청 (mode=$mode)")
        } catch (t: Throwable) {
            Log.w(TAG, "STT SDK 사용 불가", t)
            stt = null
            _status.value = LinkStatus.UNAVAILABLE
        }
    }

    override fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onMain {
            val s = stt
            when {
                s == null || _status.value == LinkStatus.UNAVAILABLE -> {
                    onError(MSG_UNAVAILABLE)
                    return@onMain
                }
                _status.value == LinkStatus.CONNECTING -> {
                    onError("음성 인식을 준비 중이에요. 잠시 후 다시 눌러 주세요")
                    return@onMain
                }
            }
            // 이전 인식이 남아 있으면 조용히 정리하고 새로 시작합니다.
            if (_listening.value) {
                clearPending()
                runCatching { s!!.stop() }
            }
            this.onResult = onResult
            this.onError = onError
            lastText = ""
            _listening.value = true
            main.postDelayed(timeout, LISTEN_TIMEOUT_MS)
            try {
                s!!.setLocale(Locale.KOREA)
                s.request()
            } catch (t: Throwable) {
                Log.w(TAG, "STT request 실패", t)
                finishError(MSG_UNAVAILABLE)
            }
        }
    }

    override fun cancel() {
        onMain {
            if (!_listening.value) return@onMain
            clearPending()
            runCatching { stt?.stop() }.onFailure { Log.w(TAG, "STT stop 실패", it) }
        }
    }

    override fun release() {
        onMain {
            clearPending()
            val s = stt ?: return@onMain
            stt = null
            runCatching { s.stop() }
            runCatching { s.removeListener(resultListener) }
            runCatching { s.release() }.onFailure { Log.w(TAG, "STT 해제 실패", it) }
            _status.value = LinkStatus.UNAVAILABLE
        }
    }

    /** 메인 스레드면 바로, 아니면 메인으로 넘겨 실행합니다. */
    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private fun finishResult(text: String) {
        val cb = onResult ?: return
        clearPending()
        cb(text)
    }

    private fun finishError(message: String) {
        val cb = onError ?: return
        clearPending()
        cb(message)
    }

    private fun clearPending() {
        main.removeCallbacks(timeout)
        onResult = null
        onError = null
        lastText = ""
        _listening.value = false
    }

    private fun describe(type: ErrorType): String = when (type) {
        ErrorType.MIC_ERROR, ErrorType.AUDIO_ERROR -> "마이크를 사용할 수 없어요. 화면에서 선택해 주세요"
        ErrorType.NETWORK_ERROR, ErrorType.TIMEOUT -> "음성 인식 연결이 불안정해요. 다시 눌러 주시거나 화면에서 선택해 주세요"
        ErrorType.PERMISSION_DENIED, ErrorType.UNAUTHENTICATED, ErrorType.NOT_SUPPORTED_API ->
            MSG_UNAVAILABLE
        else -> "음성을 인식하지 못했어요. 다시 눌러 말씀하시거나 화면에서 선택해 주세요"
    }

    private companion object {
        const val TAG = "PickMeUp"
        const val LISTEN_TIMEOUT_MS = 15_000L
        const val MSG_UNAVAILABLE = "음성 인식을 사용할 수 없어 화면 입력으로 진행합니다"
    }
}
