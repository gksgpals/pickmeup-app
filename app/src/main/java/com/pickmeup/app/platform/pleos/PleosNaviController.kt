package com.pickmeup.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.NaviController
import com.pickmeup.core.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NaviHelper SDK 어댑터: Pleos 기본 내비게이션에 목적지를 전달하고 경로 이벤트를 받습니다.
 *
 * - 연결: NaviHelper(context).initialize(onConnected, onFailed) → onConnected에서 addListener.
 * - 도착: NaviHelperEventListener.onDestinationArrived → arrived = true.
 *   (onRouteEnded는 SDK 3.0.5에서 deprecated라 사용하지 않습니다. 내비가 도착 이벤트를 주지 않는 환경에서는
 *   UI의 수동 "도착" 버튼으로 진행합니다.)
 * - 연결 전 requestRoute가 오면 보관했다가 연결 직후 전송합니다.
 */
class PleosNaviController(context: Context) : NaviController {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(LinkStatus.CONNECTING)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _lastEvent = MutableStateFlow<String?>(null)
    override val lastEvent: StateFlow<String?> = _lastEvent.asStateFlow()

    private val _arrived = MutableStateFlow(false)
    override val arrived: StateFlow<Boolean> = _arrived.asStateFlow()

    private var helper: NaviHelper? = null
    @Volatile private var pendingRoute: RouteInfo? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _lastEvent.value = "경로 탐색 시작"
        }

        override fun onRoutingCompleted() {
            _lastEvent.value = "경로 탐색 완료"
        }

        override fun onDestinationChanged() {
            _lastEvent.value = "목적지 변경됨"
        }

        override fun onReRouteEnded() {
            _lastEvent.value = "경로 재탐색 완료"
        }

        override fun onRouteCancelled() {
            _lastEvent.value = "경로 안내 취소됨"
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _arrived.value = true
            _lastEvent.value = "목적지 도착"
        }

        override fun onError(errorCode: NaviErrorCode) {
            _lastEvent.value = "내비 오류: ${describe(errorCode)}"
        }
    }

    override fun start() {
        if (helper != null) return
        _status.value = LinkStatus.CONNECTING
        try {
            val h = NaviHelper(appContext)
            helper = h
            h.initialize(
                {
                    try {
                        h.addListener(listener)
                        _status.value = LinkStatus.CONNECTED
                        pendingRoute?.let { route ->
                            pendingRoute = null
                            send(h, route)
                        }
                    } catch (t: Throwable) {
                        fail("리스너 등록 실패", t)
                    }
                },
                { t: Throwable -> fail("서비스 연결 실패", t) },
            )
        } catch (t: Throwable) {
            fail("초기화 실패", t)
        }
    }

    override fun stop() {
        val h = helper ?: return
        helper = null
        pendingRoute = null
        runCatching { h.removeListener(listener) }.onFailure { Log.w(TAG, "removeListener 실패", it) }
        runCatching { h.release() }.onFailure { Log.w(TAG, "NaviHelper 해제 실패", it) }
        _status.value = LinkStatus.CONNECTING
    }

    override fun requestRoute(point: GeoPoint, poiName: String, address: String) {
        _arrived.value = false
        val route = RouteInfo(
            point.latitude,
            point.longitude,
            null, // displayLatitude
            null, // displayLongitude
            poiName,
            "", // poiId
            address,
            "", // poiSubId
            RouteOption.RECOMMENDED,
            "", // poiProvider
            false, // isMobileRequest
        )
        val h = helper
        when {
            h != null && _status.value == LinkStatus.CONNECTED -> send(h, route)
            h != null && _status.value == LinkStatus.CONNECTING -> {
                pendingRoute = route
                _lastEvent.value = "내비 연결 중… 연결되면 목적지를 보냅니다"
            }
            else -> _lastEvent.value = "내비 연결 실패: 내비게이션 서비스를 사용할 수 없습니다"
        }
    }

    override fun cancelRoute() {
        pendingRoute = null
        val h = helper ?: return
        if (_status.value != LinkStatus.CONNECTED) return
        try {
            h.cancelRoute()
        } catch (t: Throwable) {
            Log.w(TAG, "cancelRoute 실패", t)
            _lastEvent.value = "내비 오류: 경로 취소 실패"
        }
    }

    private fun send(h: NaviHelper, route: RouteInfo) {
        try {
            h.requestRoute(route)
            _lastEvent.value = "목적지 전송: ${route.poiName}"
        } catch (t: Throwable) {
            Log.w(TAG, "requestRoute 실패", t)
            _lastEvent.value = "내비 연결 실패: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun fail(what: String, t: Throwable) {
        Log.w(TAG, "NaviHelper $what", t)
        _status.value = LinkStatus.UNAVAILABLE
        pendingRoute = null
        _lastEvent.value = "내비 연결 실패: $what"
    }

    private fun describe(code: NaviErrorCode): String = when (code) {
        NaviErrorCode.CAUSE_LOW_BATTERY -> "배터리가 부족합니다"
        NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> "탐색 가능 거리를 초과했습니다"
        NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> "목적지가 현재 위치와 너무 가깝습니다"
        NaviErrorCode.CAUSE_SEARCH_ROUTE -> "경로 탐색 서버 오류"
        NaviErrorCode.CAUSE_SEARCH_TRIP -> "트립 탐색 오류"
        else -> code.name
    }

    private companion object {
        const val TAG = "PickMeUp"
    }
}
