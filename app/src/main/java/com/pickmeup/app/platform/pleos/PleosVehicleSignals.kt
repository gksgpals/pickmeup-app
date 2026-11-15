package com.pickmeup.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Vehicle SDK 어댑터.
 *
 * - 주차 브레이크(EPB): Vehicle SDK 2.3.2의 Brake 도메인은 "브레이크 페달" 상태만 제공하므로
 *   Safety 도메인의 경고등(Telltale) 중 ELECTRONIC_PARKING_BRAKE / PARKING_BRAKE 상태로 판단합니다.
 *   변경 콜백(registerTelltales)·capability 확인은 PRIVATE_CAR_SAFETY(시스템 전용) 권한이 필요해
 *   3rd party 앱이 쓸 수 있는 getTelltalesInfo(CAR_SAFETY)를 약 1초마다 조회합니다.
 * - 도어: Door.checkDoorPositionCapabilities로 지원 영역·범위를 확인한 뒤
 *   영역별 registerDoorPosition 리스너로 변화를 구독합니다(CAR_DOORS).
 *   개별 영역을 식별할 수 없으면 수동 확인으로 처리합니다.
 * - 어떤 예외가 나도 앱을 멈추지 않고 status/값(null)으로만 알립니다.
 */
class PleosVehicleSignals(context: Context) : VehicleSignals {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(LinkStatus.CONNECTING)
    override val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _parkingBrakeOn = MutableStateFlow<Boolean?>(null)
    override val parkingBrakeOn: StateFlow<Boolean?> = _parkingBrakeOn.asStateFlow()

    private val _anyDoorOpen = MutableStateFlow<Boolean?>(null)
    override val anyDoorOpen: StateFlow<Boolean?> = _anyDoorOpen.asStateFlow()

    private val _passengerDoors = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    override val passengerDoors: StateFlow<Map<String, Boolean?>> = _passengerDoors.asStateFlow()

    private var vehicle: Vehicle? = null
    private var loopJob: Job? = null

    /** 영역별 현재 도어 위치와 '닫힘' 기준값(capability 최소값). */
    private val doorPositions = ConcurrentHashMap<DoorArea, Int>()
    private val doorClosedValue = ConcurrentHashMap<DoorArea, Int>()
    private val doorListeners = ConcurrentHashMap<DoorArea, DoorPositionListener>()

    @Volatile private var doorsSubscribed = false
    @Volatile private var doorsGaveUp = false
    @Volatile private var telltaleFailures = 0
    @Volatile private var everSucceeded = false

    override fun start() {
        if (vehicle != null) return
        _status.value = LinkStatus.CONNECTING
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK 초기화 실패", t)
            markUnavailable()
            return
        }
        vehicle = v
        loopJob = scope.launch {
            var doorAttempts = 0
            while (isActive) {
                if (!doorsSubscribed && !doorsGaveUp) {
                    // initialize()는 서비스 바인딩을 비동기로 시작하므로 첫 호출이 실패하면 몇 번 재시도합니다.
                    if (doorAttempts < MAX_DOOR_ATTEMPTS) {
                        doorAttempts++
                        subscribeDoors(v)
                    } else {
                        doorsGaveUp = true
                        Log.w(TAG, "도어 위치 구독 불가 → 수동 확인")
                    }
                }
                pollTelltales(v)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        loopJob?.cancel()
        loopJob = null
        val v = vehicle ?: return
        vehicle = null
        try {
            val door = v.getDoor()
            doorListeners.forEach { (area, listener) ->
                runCatching { door.unregisterDoorPosition(area, listener) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "도어 리스너 해제 실패", t)
        }
        doorListeners.clear()
        doorPositions.clear()
        _passengerDoors.value = emptyMap()
        doorClosedValue.clear()
        doorsSubscribed = false
        doorsGaveUp = false
        telltaleFailures = 0
        everSucceeded = false
        try {
            v.release()
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle SDK 해제 실패", t)
        }
        _parkingBrakeOn.value = null
        _anyDoorOpen.value = null
        _status.value = LinkStatus.CONNECTING
    }

    // ---------------- 도어 ----------------

    private fun subscribeDoors(v: Vehicle) {
        try {
            val door = v.getDoor()
            door.checkDoorPositionCapabilities(
                { config: CapabilityConfig, areas: Map<DoorArea, AreaBounds<Int>> ->
                    if (vehicle !== v || doorsSubscribed) return@checkDoorPositionCapabilities
                    val readable = config.accessType == AccessType.READ ||
                        config.accessType == AccessType.READ_WRITE
                    val targets = areas.filterKeys { isPassengerDoor(it) }
                    if (!readable || targets.isEmpty()) {
                        Log.w(TAG, "도어 위치 조회 미지원: access=${config.accessType}, areas=${areas.keys.size}")
                        doorsGaveUp = true
                        return@checkDoorPositionCapabilities
                    }
                    doorsSubscribed = true
                    markConnected()
                    targets.forEach { (area, bounds) ->
                        doorClosedValue[area] = bounds.minValue
                        registerDoor(v, area)
                    }
                },
                { e: Exception -> Log.w(TAG, "checkDoorPositionCapabilities 실패: ${e.message}") },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "도어 capability 확인 예외", t)
        }
    }

    private fun registerDoor(v: Vehicle, area: DoorArea) {
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) {
                updateDoor(area, pos)
            }

            override fun onFailed(e: Exception) {
                updateDoor(area, null)
                Log.w(TAG, "도어 위치 콜백 실패(${area.values.joinToString()}): ${e.message}")
            }
        }
        try {
            val door = v.getDoor()
            door.registerDoorPosition(area, listener)
            doorListeners[area] = listener
            // 등록 직후 현재 값을 한 번 조회해 초기 상태를 채웁니다.
            door.getCurrentDoorPosition(
                area,
                { position -> updateDoor(area, position) },
                { e -> Log.w(TAG, "getCurrentDoorPosition 실패: ${e.message}") },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "도어 리스너 등록 예외", t)
        }
    }

    private fun updateDoor(area: DoorArea, position: Int?) {
        if (position == null) doorPositions.remove(area) else doorPositions[area] = position
        recomputeDoors()
    }

    private fun recomputeDoors() {
        _passengerDoors.value = doorClosedValue.keys.associate { area ->
            area.values.single().name to doorPositions[area]?.let { it > (doorClosedValue[area] ?: 0) }
        }
        _anyDoorOpen.value = if (doorPositions.isEmpty()) null else doorPositions.any { (area, pos) -> pos > (doorClosedValue[area] ?: 0) }
    }

    /** 앞 동승자석과 좌우 뒷문만 허용하며 복합 영역은 제외합니다. */
    private fun isPassengerDoor(area: DoorArea): Boolean =
        area.values.size == 1 && area.values.single() in setOf(DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)

    // ---------------- 주차 브레이크(경고등) ----------------

    private fun pollTelltales(v: Vehicle) {
        try {
            v.getSafety().getTelltalesInfo(
                { info: Telltales ->
                    if (vehicle !== v) return@getTelltalesInfo
                    telltaleFailures = 0
                    markConnected()
                    val list = info.telltales
                    _parkingBrakeOn.value = list.any {
                        (it.type == TelltaleType.ELECTRONIC_PARKING_BRAKE || it.type == TelltaleType.PARKING_BRAKE) &&
                            it.state != TelltaleState.OFF
                    }

                },
                { e: Exception -> onTelltaleFailed(e) },
            )
        } catch (t: Throwable) {
            onTelltaleFailed(t)
        }
    }

    private fun onTelltaleFailed(t: Throwable) {
        telltaleFailures++
        if (telltaleFailures == 1 || telltaleFailures % 30 == 0) {
            Log.w(TAG, "getTelltalesInfo 실패(${telltaleFailures}회): ${t.message}")
        }
        if (telltaleFailures >= MAX_TELLTALE_FAILURES) {
            _parkingBrakeOn.value = null
            if (!doorsSubscribed) _anyDoorOpen.value = null
            if (!everSucceeded || (!doorsSubscribed && doorsGaveUp)) markUnavailable()
        }
    }

    private fun markConnected() {
        everSucceeded = true
        _status.value = LinkStatus.CONNECTED
    }

    private fun markUnavailable() {
        _status.value = LinkStatus.UNAVAILABLE
        _parkingBrakeOn.value = null
        _anyDoorOpen.value = null
        _passengerDoors.value = emptyMap()
    }

    private companion object {
        const val TAG = "PickMeUp"
        const val POLL_INTERVAL_MS = 1_000L
        const val MAX_DOOR_ATTEMPTS = 5
        const val MAX_TELLTALE_FAILURES = 5
    }
}
