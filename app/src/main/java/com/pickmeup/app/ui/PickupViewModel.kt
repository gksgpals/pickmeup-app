package com.pickmeup.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickmeup.app.platform.LinkStatus
import com.pickmeup.app.platform.NotifyResult
import com.pickmeup.app.platform.Platform
import com.pickmeup.app.platform.PlatformFactory
import com.pickmeup.core.data.SampleHubRepository
import com.pickmeup.core.domain.HubRepository
import com.pickmeup.core.domain.MessageComposer
import com.pickmeup.core.domain.PickupEvent
import com.pickmeup.core.domain.PickupPhase
import com.pickmeup.core.domain.PickupReducer
import com.pickmeup.core.domain.PickupState
import com.pickmeup.core.domain.SimulatedTraffic
import com.pickmeup.core.domain.SpotRecommender
import com.pickmeup.core.domain.UtteranceParser
import com.pickmeup.core.domain.WAIT_THRESHOLD_MINUTES
import com.pickmeup.core.model.Hub
import com.pickmeup.core.model.PassengerNeeds
import com.pickmeup.core.model.PickupRequest
import com.pickmeup.core.model.PickupSpot
import com.pickmeup.core.model.RankedSpot
import com.pickmeup.core.model.TrafficLevel
import com.pickmeup.core.model.WaitAdvice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

// ───────────────────────────── UI 상태 ─────────────────────────────

/** 픽업 요청 입력 초안(음성 해석 결과 또는 터치 입력). */
data class Draft(
    val hubId: String? = null,
    val entranceId: String? = null,
    /** null = 아직 확인되지 않음(요청 시 기본 5분으로 계산). */
    val readyInMinutes: Int? = null,
    val needs: PassengerNeeds = PassengerNeeds(),
    val passengerLabel: String? = null,
    val phone: String = "",
    /** 음성/시나리오 입력 원문. 터치 입력이면 null. */
    val rawUtterance: String? = null,
    /** 음성 해석에서 나온 거점 후보 id(점수 순). 2개 이상이면 운전자가 고릅니다. */
    val hubCandidateIds: List<String> = emptyList(),
)

data class LinkState(
    val vehicle: LinkStatus = LinkStatus.CONNECTING,
    val navi: LinkStatus = LinkStatus.CONNECTING,
    val speech: LinkStatus = LinkStatus.CONNECTING,
    val voice: LinkStatus = LinkStatus.CONNECTING,
)

enum class NotifyKind { WAITING, MEETING }

data class NotifyUi(
    val sending: Boolean,
    val message: String,
    val spotId: String,
    val spotName: String,
    val kind: NotifyKind,
    val result: NotifyResult? = null,
)

enum class BannerKind { INFO, WARN, ERROR }

data class Banner(val text: String, val kind: BannerKind, val id: Long = System.nanoTime())

enum class DemoClock(val label: String) { NOW("현재 시각"), EVENING("저녁 19:00 (시나리오)") }

enum class PresetIcon { RAIN, TRAFFIC, HOSPITAL, SCHOOL }

data class DemoPreset(val title: String, val subtitle: String, val utterance: String, val icon: PresetIcon)

/** 시연 패널의 교통 조절 항목: 하나의 접근 도로 구간과 그 구간을 쓰는 지점들. */
data class TrafficLink(val linkId: String, val spotNames: List<String>, val level: TrafficLevel)

data class UiState(
    val pickup: PickupState = PickupState(),
    val hubs: List<Hub> = emptyList(),
    val presets: List<DemoPreset> = emptyList(),
    val draft: Draft = Draft(),
    val ranked: List<RankedSpot> = emptyList(),
    val waitAdvice: WaitAdvice? = null,
    val selectedBoardingId: String? = null,
    val selectedWaitingId: String? = null,
    val readyAtMillis: Long? = null,
    val remainingSec: Int? = null,
    val countdownTotalSec: Int? = null,
    val notify: NotifyUi? = null,
    val links: LinkState = LinkState(),
    val parkingBrakeOn: Boolean? = null,
    val anyDoorOpen: Boolean? = null,
    val brakeSimulated: Boolean = false,
    val doorSimulated: Boolean = false,
    val naviEvent: String? = null,
    val listening: Boolean = false,
    val banner: Banner? = null,
    val touchInputOpen: Boolean = false,
    val demoPanelOpen: Boolean = false,
    val trafficLinks: List<TrafficLink> = emptyList(),
    val clock: DemoClock = DemoClock.NOW,
    val arrivedAtWaiting: Boolean = false,
    /** 대기 단계를 거치지 않고 바로 승차 지점으로 갔는지. */
    val waitingSkipped: Boolean = false,
    /** 완료 화면 요약용: 어떤 방식으로 탑승 완료가 확인됐는지. */
    val completedBySensor: Boolean? = null,
) {
    val phase: PickupPhase get() = pickup.phase
    val selectedHub: Hub? get() = hubs.firstOrNull { it.id == draft.hubId }
    val selectedBoarding: RankedSpot? get() = ranked.firstOrNull { it.spot.id == selectedBoardingId }
    val selectedWaiting: RankedSpot? get() =
        waitAdvice?.waitingCandidates?.firstOrNull { it.spot.id == selectedWaitingId }

    /** 대기 장소 → 승차 지점 예상 이동 시간(분). */
    val travelMinutesToBoarding: Int?
        get() {
            val from = pickup.waitingSpot ?: return null
            val to = pickup.boardingSpot ?: return null
            return estimateDriveMinutes(from.stopPoint, to.stopPoint)
        }

    /** 키보드 입력 허용 여부: 주행 중으로 확인되면(주차 브레이크 해제) 막습니다. */
    val keyboardAllowed: Boolean get() = parkingBrakeOn != false
}

// ───────────────────────────── ViewModel ─────────────────────────────

class PickupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: HubRepository = SampleHubRepository()
    private val parser = UtteranceParser(repository)
    private val traffic = SimulatedTraffic()
    private val recommender = SpotRecommender(traffic)

    private val platform: Platform = try {
        PlatformFactory.create(app.applicationContext)
    } catch (t: Throwable) {
        Log.w(TAG, "PlatformFactory 실패, 오프라인 모드로 동작", t)
        OfflinePlatform.create()
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** 시연 패널이 덮어쓰는 차량 신호(null = 실제 센서 값 사용). */
    private val simBrake = MutableStateFlow<Boolean?>(null)
    private val simDoor = MutableStateFlow<Boolean?>(null)

    private var tickerJob: Job? = null
    private var notifyJob: Job? = null
    private var bannerJob: Job? = null
    private var doorPulseJob: Job? = null
    private var boardingManuallyPicked = false
    private var autoReadyFired = false

    init {
        val hubs = runCatching { repository.all() }.getOrElse { emptyList() }
        seedTraffic(hubs)
        _ui.update { it.copy(hubs = hubs, presets = buildPresets(hubs)) }

        safe("vehicle.start") { platform.vehicle.start() }
        safe("navi.start") { platform.navi.start() }

        viewModelScope.launch {
            combine(
                platform.vehicle.status,
                platform.navi.status,
                platform.speech.status,
                platform.voice.status,
            ) { v, n, s, o -> LinkState(v, n, s, o) }.collect { links -> _ui.update { it.copy(links = links) } }
        }
        // 주차 브레이크: 실제 센서 또는 시연 패널 값. TO_WAITING 중 체결되는 순간 대기 모드로 전환.
        viewModelScope.launch {
            var prev: Boolean? = null
            combine(platform.vehicle.parkingBrakeOn, simBrake) { real, sim -> (sim ?: real) to (sim != null) }
                .collect { (on, simulated) ->
                    _ui.update { it.copy(parkingBrakeOn = on, brakeSimulated = simulated) }
                    if (on == true && prev != true && _ui.value.phase == PickupPhase.TO_WAITING) {
                        dispatch(PickupEvent.Parked(bySensor = true), expect = PickupPhase.WAITING)
                    }
                    prev = on
                }
        }
        // 도어: BOARDING 중 열리는 순간 탑승 완료.
        viewModelScope.launch {
            var prev: Boolean? = null
            combine(platform.vehicle.anyDoorOpen, simDoor) { real, sim -> (sim ?: real) to (sim != null) }
                .collect { (open, simulated) ->
                    _ui.update { it.copy(anyDoorOpen = open, doorSimulated = simulated) }
                    if (open == true && prev != true && _ui.value.phase == PickupPhase.BOARDING) {
                        boarded(bySensor = true)
                    }
                    prev = open
                }
        }
        // 내비 안내 종료(도착).
        viewModelScope.launch {
            var prev = false
            platform.navi.arrived.collect { arrived ->
                if (arrived && !prev) onNaviArrived()
                prev = arrived
            }
        }
        viewModelScope.launch {
            platform.navi.lastEvent.collect { e -> _ui.update { it.copy(naviEvent = e) } }
        }
        viewModelScope.launch {
            platform.speech.listening.collect { l -> _ui.update { it.copy(listening = l) } }
        }
    }

    // ───────────────────────── 입력(IDLE / PLANNING) ─────────────────────────

    /** 마이크 버튼(권한 허용 후). 인식 중이면 취소. */
    fun startListening() {
        if (_ui.value.listening) {
            safe("speech.cancel") { platform.speech.cancel() }
            return
        }
        try {
            platform.speech.listenOnce(
                onResult = { text -> viewModelScope.launch { handleUtterance(text) } },
                onError = { msg -> viewModelScope.launch { onSpeechError(msg) } },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "listenOnce 실패", t)
            onSpeechError("음성 인식을 시작할 수 없어 화면 입력으로 전환합니다")
        }
    }

    fun cancelListening() = safe("speech.cancel") { platform.speech.cancel() }

    private fun onSpeechError(message: String) {
        showBanner(message.ifBlank { "음성을 인식하지 못했습니다" }, BannerKind.WARN)
        _ui.update { it.copy(touchInputOpen = true, listening = false) }
    }

    /** 시연 시나리오 버튼: 음성 인식과 같은 경로로 문장을 해석합니다. */
    fun runPreset(preset: DemoPreset) = handleUtterance(preset.utterance)

    fun handleUtterance(text: String) {
        val parsed = runCatching { parser.parse(text) }.getOrElse {
            Log.w(TAG, "parse 실패", it); null
        }
        if (parsed == null) {
            showBanner("문장을 해석하지 못했습니다. 화면에서 선택해 주세요", BannerKind.WARN)
            _ui.update { it.copy(touchInputOpen = true) }
            return
        }
        // 출입구가 첫 후보가 아닌 다른 후보 거점에 속하면 그 거점을 우선합니다.
        val candidates = parsed.hubCandidates
        val hub = parsed.entrance?.let { e -> candidates.firstOrNull { h -> h.entrances.any { it.id == e.id } } }
            ?: candidates.firstOrNull()
        val entranceId = parsed.entrance?.id?.takeIf { id -> hub?.entrances?.any { it.id == id } == true }
        _ui.update {
            it.copy(
                draft = Draft(
                    hubId = hub?.id,
                    entranceId = entranceId,
                    readyInMinutes = parsed.readyInMinutes,
                    needs = parsed.needs,
                    passengerLabel = parsed.passengerLabel,
                    phone = it.draft.phone,
                    rawUtterance = text,
                    hubCandidateIds = candidates.map { h -> h.id },
                ),
            )
        }
        if (hub == null) {
            showBanner("‘$text’ 에서 거점을 찾지 못했습니다. 화면에서 선택해 주세요", BannerKind.WARN)
            _ui.update { it.copy(touchInputOpen = true) }
            return
        }
        if (candidates.size > 1) {
            showBanner("거점 후보가 ${candidates.size}곳입니다. 맞는 곳을 선택해 주세요", BannerKind.INFO)
        }
        startPlanning()
    }

    fun openTouchInput() = _ui.update { it.copy(touchInputOpen = true) }

    fun selectHub(id: String) {
        _ui.update { s ->
            val hub = s.hubs.firstOrNull { it.id == id } ?: return@update s
            val keepEntrance = s.draft.entranceId?.takeIf { e -> hub.entrances.any { it.id == e } }
                ?: resolveEntrance(hub, s.draft.rawUtterance)
            s.copy(draft = s.draft.copy(hubId = id, entranceId = keepEntrance))
        }
        replanIfPlanning()
    }

    /** 파서는 첫 후보 거점의 출입구만 찾으므로, 다른 후보를 고르면 원문에서 다시 찾습니다(가장 긴 별칭 우선). */
    private fun resolveEntrance(hub: Hub, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = raw.replace(" ", "")
        return hub.entrances
            .flatMap { e -> (e.aliases + e.name).map { alias -> e to alias.replace(" ", "") } }
            .filter { (_, alias) -> alias.isNotEmpty() && text.contains(alias) }
            .maxByOrNull { (_, alias) -> alias.length }
            ?.first?.id
    }

    fun selectEntrance(id: String?) {
        _ui.update { it.copy(draft = it.draft.copy(entranceId = id)) }
        replanIfPlanning()
    }

    fun selectReady(minutes: Int) {
        _ui.update { it.copy(draft = it.draft.copy(readyInMinutes = minutes)) }
        replanIfPlanning(resetReadyClock = true)
    }

    fun toggleMobility() {
        _ui.update { it.copy(draft = it.draft.copy(needs = it.draft.needs.copy(mobilityImpaired = !it.draft.needs.mobilityImpaired))) }
        replanIfPlanning()
    }

    fun toggleLuggage() {
        _ui.update { it.copy(draft = it.draft.copy(needs = it.draft.needs.copy(heavyLuggage = !it.draft.needs.heavyLuggage))) }
        replanIfPlanning()
    }

    fun selectPassengerLabel(label: String?) {
        _ui.update { it.copy(draft = it.draft.copy(passengerLabel = label)) }
        replanIfPlanning()
    }

    fun updatePhone(value: String) {
        if (!_ui.value.keyboardAllowed) return
        val cleaned = value.filter { it.isDigit() || it == '-' }.take(13)
        _ui.update { it.copy(draft = it.draft.copy(phone = cleaned)) }
    }

    /** 터치 입력의 "안내 시작". */
    fun submitTouchInput() {
        if (_ui.value.draft.hubId == null) {
            showBanner("픽업할 거점을 먼저 선택해 주세요", BannerKind.WARN)
            return
        }
        _ui.update { it.copy(draft = it.draft.copy(rawUtterance = null, hubCandidateIds = emptyList())) }
        startPlanning()
    }

    private fun buildRequest(d: Draft = _ui.value.draft): PickupRequest? {
        val hub = d.hubId?.let { id -> _ui.value.hubs.firstOrNull { it.id == id } ?: runCatching { repository.byId(id) }.getOrNull() }
            ?: return null
        return PickupRequest(
            hub = hub,
            entrance = hub.entrances.firstOrNull { it.id == d.entranceId },
            readyInMinutes = d.readyInMinutes ?: DEFAULT_READY_MINUTES,
            passengerName = d.passengerLabel,
            needs = d.needs,
        )
    }

    private fun startPlanning() {
        val req = buildRequest() ?: return
        boardingManuallyPicked = false
        setReadyClock(req.readyInMinutes)
        if (_ui.value.phase == PickupPhase.IDLE || _ui.value.phase == PickupPhase.PLANNING) {
            dispatch(PickupEvent.Planned(req), expect = PickupPhase.PLANNING)
            ensurePickup { if (it.request != req) it.copy(request = req) else it }
        }
        _ui.update { it.copy(touchInputOpen = false, selectedWaitingId = null, selectedBoardingId = null) }
        recompute()
        val count = _ui.value.ranked.size
        speak(if (count > 0) "${req.hub.name} 승차 지점 ${count}곳을 찾았습니다" else "조건에 맞는 승차 지점이 없습니다")
    }

    private fun replanIfPlanning(resetReadyClock: Boolean = false) {
        if (_ui.value.phase != PickupPhase.PLANNING) return
        val req = buildRequest() ?: return
        if (resetReadyClock) setReadyClock(req.readyInMinutes)
        dispatch(PickupEvent.Planned(req), expect = PickupPhase.PLANNING)
        ensurePickup { if (it.request != req) it.copy(request = req) else it }
        recompute()
    }

    private fun setReadyClock(readyMinutes: Int) {
        val total = readyMinutes * 60
        _ui.update {
            it.copy(
                readyAtMillis = System.currentTimeMillis() + total * 1000L,
                countdownTotalSec = total,
                remainingSec = total,
            )
        }
        autoReadyFired = false
        startTicker()
    }

    // ───────────────────────── 추천 ─────────────────────────

    private fun minuteOfDay(clock: DemoClock = _ui.value.clock): Int = when (clock) {
        DemoClock.EVENING -> 19 * 60
        DemoClock.NOW -> Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    }

    private fun currentRequest(): PickupRequest? = _ui.value.pickup.request ?: buildRequest()

    /** 승차 지점 순위와 대기 판단을 다시 계산합니다(입력 변경·교통 변경·시각 변경 시). */
    private fun recompute() {
        val req = currentRequest()
        if (req == null) {
            _ui.update { it.copy(ranked = emptyList(), waitAdvice = null, trafficLinks = emptyList()) }
            return
        }
        var clock = _ui.value.clock
        var ranked = rank(req, minuteOfDay(clock))
        if (ranked.isEmpty() && clock == DemoClock.NOW) {
            val evening = rank(req, minuteOfDay(DemoClock.EVENING))
            if (evening.isNotEmpty()) {
                clock = DemoClock.EVENING
                ranked = evening
                showBanner("현재 시각에 이용 가능한 지점이 없어 시연 시각(저녁 19:00) 기준으로 보여 드립니다", BannerKind.INFO)
            }
        }
        val advice = runCatching { recommender.adviseWaiting(req, minuteOfDay(clock)) }
            .onFailure { Log.w(TAG, "adviseWaiting 실패", it) }
            .getOrNull()
        _ui.update { s ->
            val boardingId = when {
                boardingManuallyPicked && ranked.any { it.spot.id == s.selectedBoardingId } -> s.selectedBoardingId
                else -> ranked.firstOrNull()?.spot?.id
            }
            val waitIds = advice?.waitingCandidates.orEmpty().map { it.spot.id }
            val waitingId = s.selectedWaitingId?.takeIf { it in waitIds } ?: waitIds.firstOrNull()
            s.copy(
                clock = clock,
                ranked = ranked,
                waitAdvice = advice,
                selectedBoardingId = boardingId,
                selectedWaitingId = waitingId,
                trafficLinks = buildTrafficLinks(ranked, advice),
            )
        }
    }

    private fun rank(req: PickupRequest, minute: Int): List<RankedSpot> =
        runCatching { recommender.rankBoarding(req, minute) }
            .onFailure { Log.w(TAG, "rankBoarding 실패", it) }
            .getOrElse { emptyList() }

    private fun buildTrafficLinks(ranked: List<RankedSpot>, advice: WaitAdvice?): List<TrafficLink> {
        val snapshot = runCatching { traffic.snapshot() }.getOrElse { emptyMap() }
        val all = ranked + advice?.waitingCandidates.orEmpty()
        return all.mapNotNull { r -> r.spot.approachLinkId?.let { it to r.spot.name } }
            .groupBy({ it.first }, { it.second })
            .map { (link, names) ->
                TrafficLink(link, names.distinct(), snapshot[link] ?: runCatching { traffic.levelOf(link) }.getOrElse { TrafficLevel.UNKNOWN })
            }
    }

    private fun seedTraffic(hubs: List<Hub>) {
        val snapshot = runCatching { traffic.snapshot() }.getOrElse { emptyMap() }
        hubs.flatMap { it.spots }.mapNotNull { it.approachLinkId }.distinct()
            .filter { it !in snapshot }
            .forEach { link -> safe("traffic.set") { traffic.set(link, TrafficLevel.SMOOTH) } }
    }

    fun selectBoarding(spotId: String) {
        val s = _ui.value
        val spot = s.ranked.firstOrNull { it.spot.id == spotId }?.spot ?: return
        boardingManuallyPicked = true
        _ui.update { it.copy(selectedBoardingId = spotId) }
        when (s.phase) {
            PickupPhase.WAITING -> {
                ensurePickup { it.copy(boardingSpot = spot) }
                showBanner("승차 지점을 ‘${spot.name}’(으)로 바꿨습니다", BannerKind.INFO)
                sendWaitingMessage(spot)
            }
            PickupPhase.TO_BOARDING -> {
                ensurePickup { it.copy(boardingSpot = spot) }
                routeTo(spot)
                sendMeetingMessage(spot, DEFAULT_DIRECT_ETA, announce = "승차 지점을 변경했습니다.")
            }
            PickupPhase.BOARDING -> {
                // 도착한 승차 지점을 쓸 수 없을 때: 대체 지점으로 다시 이동(경로·알림은 onPhaseChanged에서 처리)
                dispatch(PickupEvent.StartToBoarding(spot), expect = PickupPhase.TO_BOARDING) {
                    it.copy(boardingSpot = spot)
                }
            }
            else -> Unit
        }
    }

    fun selectWaiting(spotId: String) = _ui.update { it.copy(selectedWaitingId = spotId) }

    // ───────────────────────── 단계 전환 ─────────────────────────

    fun startToWaiting() {
        val s = _ui.value
        val waiting = s.selectedWaiting?.spot ?: s.waitAdvice?.waitingCandidates?.firstOrNull()?.spot
        val boarding = s.selectedBoarding?.spot ?: s.ranked.firstOrNull()?.spot
        if (waiting == null || boarding == null) {
            showBanner("대기 장소 또는 승차 지점을 선택해 주세요", BannerKind.WARN)
            return
        }
        ensurePickup { it.copy(boardingSpot = boarding) }
        dispatch(PickupEvent.StartToWaiting(waiting), expect = PickupPhase.TO_WAITING) {
            it.copy(
                waitingSpot = it.waitingSpot ?: waiting,
                boardingSpot = it.boardingSpot ?: boarding,
            )
        }
    }

    fun startToBoarding() {
        val boarding = _ui.value.selectedBoarding?.spot ?: _ui.value.ranked.firstOrNull()?.spot
        if (boarding == null) {
            showBanner("승차 지점을 선택해 주세요", BannerKind.WARN)
            return
        }
        dispatch(PickupEvent.StartToBoarding(boarding), expect = PickupPhase.TO_BOARDING) {
            it.copy(boardingSpot = it.boardingSpot ?: boarding)
        }
    }

    fun manualParked() {
        if (_ui.value.phase != PickupPhase.TO_WAITING) return
        dispatch(PickupEvent.Parked(bySensor = false), expect = PickupPhase.WAITING)
    }

    fun passengerReady(byPassengerSignal: Boolean = true) {
        val phase = _ui.value.phase
        if (phase != PickupPhase.WAITING && phase != PickupPhase.TO_WAITING) return
        dispatch(PickupEvent.PassengerReady(byPassengerSignal), expect = PickupPhase.TO_BOARDING) {
            it.copy(boardingSpot = it.boardingSpot ?: _ui.value.selectedBoarding?.spot)
        }
    }

    fun arrivedAtBoarding() {
        if (_ui.value.phase != PickupPhase.TO_BOARDING) return
        dispatch(PickupEvent.ArrivedAtBoarding, expect = PickupPhase.BOARDING)
    }

    fun boarded(bySensor: Boolean) {
        if (_ui.value.phase != PickupPhase.BOARDING) return
        _ui.update { it.copy(completedBySensor = bySensor) }
        dispatch(PickupEvent.Boarded(bySensor), expect = PickupPhase.COMPLETED)
    }

    fun cancel() {
        if (_ui.value.phase == PickupPhase.IDLE) return
        dispatch(PickupEvent.Cancel, expect = PickupPhase.IDLE)
        resetSession(notice = "픽업을 취소했습니다")
        speak("픽업을 취소했습니다")
    }

    fun restart() = resetSession(notice = null)

    private fun resetSession(notice: String?) {
        safe("navi.cancelRoute") { platform.navi.cancelRoute() }
        stopTicker()
        notifyJob?.cancel()
        simBrake.value = null
        simDoor.value = null
        boardingManuallyPicked = false
        autoReadyFired = false
        _ui.update {
            UiState(
                pickup = PickupState(notice = notice),
                hubs = it.hubs,
                presets = it.presets,
                draft = Draft(phone = it.draft.phone),
                links = it.links,
                parkingBrakeOn = it.parkingBrakeOn,
                anyDoorOpen = it.anyDoorOpen,
                naviEvent = it.naviEvent,
                demoPanelOpen = it.demoPanelOpen,
                clock = it.clock,
                trafficLinks = emptyList(),
                banner = it.banner,
            )
        }
    }

    private fun onNaviArrived() {
        when (_ui.value.phase) {
            PickupPhase.TO_WAITING -> {
                if (_ui.value.arrivedAtWaiting) return
                _ui.update { it.copy(arrivedAtWaiting = true) }
                if (_ui.value.parkingBrakeOn == true) {
                    dispatch(PickupEvent.Parked(bySensor = true), expect = PickupPhase.WAITING)
                } else {
                    speak("대기 장소에 도착했습니다. 주차 브레이크를 체결하면 대기 모드로 전환합니다")
                }
            }
            PickupPhase.TO_BOARDING -> arrivedAtBoarding()
            else -> Unit
        }
    }

    /**
     * 상태 머신에 이벤트를 전달합니다. 화면은 이 단계에서만 해당 버튼을 보여 주므로,
     * 리듀서가 전환을 거부하더라도 시연이 멈추지 않도록 [expect] 단계로 맞춥니다.
     */
    private fun dispatch(
        event: PickupEvent,
        expect: PickupPhase? = null,
        patch: (PickupState) -> PickupState = { it },
    ) {
        val before = _ui.value.pickup
        var after = try {
            PickupReducer.reduce(before, event)
        } catch (t: Throwable) {
            Log.w(TAG, "reduce 실패: $event", t)
            before
        }
        if (expect != null && after.phase != expect) {
            Log.w(TAG, "리듀서 결과 ${after.phase} ≠ 기대 $expect ($event) → 기대 단계로 보정")
            after = after.copy(phase = expect)
        }
        after = patch(after)
        _ui.update { it.copy(pickup = after) }
        if (before.phase != after.phase) onPhaseChanged(before.phase, after)
    }

    private inline fun ensurePickup(transform: (PickupState) -> PickupState) {
        _ui.update { it.copy(pickup = transform(it.pickup)) }
    }

    private fun onPhaseChanged(from: PickupPhase, state: PickupState) {
        when (state.phase) {
            PickupPhase.TO_WAITING -> {
                _ui.update { it.copy(arrivedAtWaiting = false, waitingSkipped = false) }
                state.waitingSpot?.let { routeTo(it) }
                speak("대기 장소로 안내합니다. ${state.waitingSpot?.name.orEmpty()}")
            }
            PickupPhase.WAITING -> {
                // 남은 준비 시간이 너무 짧으면 최소 1분은 대기 모드를 보여 줍니다.
                val now = System.currentTimeMillis()
                val readyAt = _ui.value.readyAtMillis
                if (readyAt == null || readyAt - now < 60_000L) {
                    _ui.update { it.copy(readyAtMillis = now + 60_000L, countdownTotalSec = 60, remainingSec = 60) }
                }
                autoReadyFired = false
                startTicker()
                val boarding = state.boardingSpot ?: _ui.value.selectedBoarding?.spot
                sendWaitingMessage(boarding, announce = "대기 모드입니다.")
            }
            PickupPhase.TO_BOARDING -> {
                simBrake.value = null // 승차 지점으로 출발 = 주차 브레이크 해제
                val boarding = state.boardingSpot ?: _ui.value.selectedBoarding?.spot ?: return
                routeTo(boarding)
                if (from == PickupPhase.PLANNING) _ui.update { it.copy(waitingSkipped = true) }
                val n = _ui.value.notify
                val alreadyNotified = n != null && n.kind == NotifyKind.MEETING && n.spotId == boarding.id
                if (!alreadyNotified) {
                    val eta = when (from) {
                        PickupPhase.PLANNING -> maxOf(state.request?.readyInMinutes ?: 0, DEFAULT_DIRECT_ETA)
                        PickupPhase.WAITING, PickupPhase.TO_WAITING ->
                            _ui.value.travelMinutesToBoarding ?: DEFAULT_DIRECT_ETA
                        else -> DEFAULT_DIRECT_ETA
                    }
                    sendMeetingMessage(boarding, eta, announce = "승차 지점으로 안내합니다.")
                } else {
                    speak("승차 지점으로 안내를 전환합니다. ${boarding.name}")
                }
            }
            PickupPhase.BOARDING -> {
                stopTicker()
                simDoor.value = null
                speak("승차 지점에 도착했습니다. 동승자가 탑승하면 자동으로 완료됩니다")
            }
            PickupPhase.COMPLETED -> {
                stopTicker()
                simDoor.value = null
                speak("픽업이 완료되었습니다. 안전 운전하세요")
            }
            PickupPhase.IDLE, PickupPhase.PLANNING -> Unit
        }
    }

    private fun routeTo(spot: PickupSpot) {
        val address = _ui.value.pickup.request?.hub?.roadAddress.orEmpty()
        safe("navi.requestRoute") { platform.navi.requestRoute(spot.stopPoint, spot.name, address) }
    }

    // ───────────────────────── 동승자 알림 ─────────────────────────

    /** 승차 지점으로 출발할 때: 합류 위치 + 도착 예정 시간. */
    private fun sendMeetingMessage(spot: PickupSpot, etaMinutes: Int, announce: String? = null) {
        val req = _ui.value.pickup.request ?: buildRequest() ?: return
        val message = runCatching { MessageComposer.meetingMessage(req, spot, etaMinutes) }
            .getOrElse { "[픽미업] ${req.hub.name} ${spot.meetingSign}에서 만나요. 약 ${etaMinutes}분 뒤 도착 예정입니다." }
        deliver(message, spot, NotifyKind.MEETING, announce)
    }

    /** 대기 장소 도착(대기 모드 진입) 시: 근처 대기 중 + 합류 위치 + 승차 지점까지 소요 시간. */
    private fun sendWaitingMessage(boarding: PickupSpot?, announce: String? = null) {
        val req = _ui.value.pickup.request ?: buildRequest() ?: return
        val travel = _ui.value.travelMinutesToBoarding
        val message = runCatching { MessageComposer.waitingMessage(req, boarding, travel) }
            .getOrElse { "[픽미업] 근처에서 기다리고 있어요. 준비되면 알려 주세요." }
        val spot = boarding ?: _ui.value.pickup.waitingSpot ?: return
        deliver(message, spot, NotifyKind.WAITING, announce)
    }

    private fun deliver(message: String, spot: PickupSpot, kind: NotifyKind, announce: String?) {
        _ui.update { it.copy(notify = NotifyUi(sending = true, message = message, spotId = spot.id, spotName = spot.name, kind = kind)) }
        val phone = _ui.value.draft.phone.filter { it.isDigit() }.ifBlank { null }
        notifyJob?.cancel()
        notifyJob = viewModelScope.launch {
            val result: NotifyResult = try {
                withTimeoutOrNull(NOTIFY_TIMEOUT_MS) { platform.notifier.notify(phone, message) }
                    ?: NotifyResult.Fallback("전송 응답이 없어 화면 안내로 전환했습니다", message)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "notify 실패", t)
                NotifyResult.Fallback("전송 중 오류가 발생했습니다", message)
            }
            _ui.update { s ->
                val n = s.notify
                if (n != null && n.message == message) s.copy(notify = n.copy(sending = false, result = result)) else s
            }
            val prefix = announce?.let { "$it " }.orEmpty()
            when (result) {
                is NotifyResult.Sent ->
                    if (isPrefilledSmsApp(result)) speak("${prefix}문자 앱에 합류 안내를 채워 두었습니다. 정차 중에 전송해 주세요")
                    else speak("${prefix}동승자에게 합류 위치를 보냈습니다")
                is NotifyResult.Fallback -> speak("${prefix}문자를 보낼 수 없어 화면에 안내 문구를 띄웠습니다")
            }
        }
    }

    fun resendMessage() {
        val spot = _ui.value.pickup.boardingSpot ?: return
        when (_ui.value.phase) {
            PickupPhase.WAITING -> sendWaitingMessage(spot)
            PickupPhase.BOARDING -> sendMeetingMessage(spot, 0)
            else -> sendMeetingMessage(spot, DEFAULT_DIRECT_ETA)
        }
    }

    // ───────────────────────── 카운트다운 ─────────────────────────

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun tick() {
        val readyAt = _ui.value.readyAtMillis ?: return
        val remaining = ((readyAt - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
        if (remaining != _ui.value.remainingSec) _ui.update { it.copy(remainingSec = remaining) }
        if (remaining == 0 && _ui.value.phase == PickupPhase.WAITING && !autoReadyFired) {
            autoReadyFired = true
            showBanner("동승자 준비 시간이 되어 승차 지점으로 안내를 전환합니다", BannerKind.INFO)
            passengerReady(byPassengerSignal = false)
        }
    }

    // ───────────────────────── 시연 패널 ─────────────────────────

    fun toggleDemoPanel() = _ui.update { it.copy(demoPanelOpen = !it.demoPanelOpen) }

    fun closeDemoPanel() = _ui.update { it.copy(demoPanelOpen = false) }

    /** 주차 브레이크 체결/해제 시뮬레이션(센서 경로와 같은 처리). */
    fun demoToggleBrake() {
        val on = _ui.value.parkingBrakeOn == true
        if (_ui.value.phase == PickupPhase.TO_WAITING) {
            // 대기 장소 이동 중에는 항상 "체결"로 동작. 이미 체결 상태(정지한 에뮬레이터 등)면 엣지가 없으므로 직접 전환.
            simBrake.value = true
            if (on) dispatch(PickupEvent.Parked(bySensor = true), expect = PickupPhase.WAITING)
            return
        }
        simBrake.value = !on
    }

    /** 도어 열림 시뮬레이션: 1.5초 열렸다 닫힘. */
    fun demoDoorPulse() {
        if (_ui.value.phase == PickupPhase.BOARDING && _ui.value.anyDoorOpen == true) {
            boarded(bySensor = true) // 이미 열린 상태면 엣지가 생기지 않으므로 직접 처리
            return
        }
        doorPulseJob?.cancel()
        simDoor.value = true
        doorPulseJob = viewModelScope.launch {
            delay(1_500)
            simDoor.value = false
            delay(500)
            simDoor.value = null
        }
    }

    fun demoNaviArrived() {
        val phase = _ui.value.phase
        if (phase != PickupPhase.TO_WAITING && phase != PickupPhase.TO_BOARDING) {
            showBanner("내비 도착은 이동 중 단계에서만 시연할 수 있습니다", BannerKind.INFO)
            return
        }
        onNaviArrived()
    }

    fun demoPassengerReady() {
        val phase = _ui.value.phase
        if (phase != PickupPhase.WAITING && phase != PickupPhase.TO_WAITING) {
            showBanner("동승자 준비 신호는 대기 단계에서 시연할 수 있습니다", BannerKind.INFO)
            return
        }
        showBanner("동승자가 준비 완료 신호를 보냈습니다", BannerKind.INFO)
        passengerReady(byPassengerSignal = true)
    }

    /** 카운트다운 1분 앞당기기. */
    fun demoFastForward() {
        _ui.update { s ->
            val at = s.readyAtMillis ?: return@update s
            s.copy(readyAtMillis = at - 60_000L)
        }
        tick()
    }

    fun demoCycleTraffic(linkId: String) {
        val current = runCatching { traffic.snapshot()[linkId] ?: traffic.levelOf(linkId) }.getOrElse { TrafficLevel.UNKNOWN }
        val next = when (current) {
            TrafficLevel.SMOOTH -> TrafficLevel.SLOW
            TrafficLevel.SLOW -> TrafficLevel.CONGESTED
            TrafficLevel.CONGESTED -> TrafficLevel.UNKNOWN
            TrafficLevel.UNKNOWN -> TrafficLevel.SMOOTH
        }
        safe("traffic.set") { traffic.set(linkId, next) }
        val beforeTop = _ui.value.ranked.firstOrNull()?.spot?.id
        recompute()
        val afterTop = _ui.value.ranked.firstOrNull()
        if (afterTop != null && afterTop.spot.id != beforeTop) {
            showBanner("교통 상황 변경으로 1순위가 ‘${afterTop.spot.name}’(으)로 바뀌었습니다", BannerKind.WARN)
        }
    }

    fun demoSetClock(clock: DemoClock) {
        _ui.update { it.copy(clock = clock) }
        recompute()
    }

    fun dismissBanner() = _ui.update { it.copy(banner = null) }

    // ───────────────────────── 공통 ─────────────────────────

    private fun showBanner(text: String, kind: BannerKind) {
        val banner = Banner(text, kind)
        _ui.update { it.copy(banner = banner) }
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(BANNER_MS)
            _ui.update { if (it.banner?.id == banner.id) it.copy(banner = null) else it }
        }
    }

    private fun speak(text: String) = safe("voice.speak") { platform.voice.speak(text) }

    private inline fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$what 실패", t)
        }
    }

    override fun onCleared() {
        safe("speech.release") { platform.speech.cancel(); platform.speech.release() }
        safe("voice.release") { platform.voice.release() }
        safe("vehicle.stop") { platform.vehicle.stop() }
        safe("navi.stop") { platform.navi.stop() }
        super.onCleared()
    }

    private fun buildPresets(hubs: List<Hub>): List<DemoPreset> {
        val ids = hubs.map { it.id }.toSet()
        val list = mutableListOf<DemoPreset>()
        if (SampleHubRepository.HANBIT_ID in ids) {
            list += DemoPreset(
                title = "비 오는 저녁 · 상가 친구 픽업",
                subtitle = "준비 10분 → 거점 선택 → 무료 대기 장소 경유",
                utterance = "상가 동문에서 친구 태울 거야. 10분 정도 걸린대",
                icon = PresetIcon.RAIN,
            )
            list += DemoPreset(
                title = "곧 나옴 · 진입로 정체 대응",
                subtitle = "준비 3분 → 바로 승차 · 시연 패널에서 LINK-HB-01 정체",
                utterance = "한빛상가 동문에서 친구 태울 거야. 3분쯤 걸린대",
                icon = PresetIcon.TRAFFIC,
            )
        }
        if (SampleHubRepository.SAESOL_ID in ids) list += DemoPreset(
            title = "병원 · 휠체어 이용 어르신",
            subtitle = "보행 약자 → 계단·턱 없는 지점만",
            utterance = "새솔병원 정문에서 할머니 모시고 갈게. 휠체어 타셔. 십 분 정도 걸린대",
            icon = PresetIcon.HOSPITAL,
        )
        if (SampleHubRepository.NURI_ID in ids) list += DemoPreset(
            title = "학원가 · 이미 나와 있음",
            subtitle = "대기 없이 바로 승차 지점으로",
            utterance = "누리학원가 후문, 딸이 이미 나와 있대",
            icon = PresetIcon.SCHOOL,
        )
        if (list.isEmpty()) hubs.take(2).forEach { h ->
            val e = h.entrances.firstOrNull()?.name.orEmpty()
            list += DemoPreset(h.name, "시연 문장", "${h.name} ${e}에서 친구 태울 거야. 10분 정도 걸린대", PresetIcon.RAIN)
        }
        return list
    }

    companion object {
        private const val TAG = "PickMeUp"
        const val DEFAULT_READY_MINUTES = 5
        /** 바로 승차 지점으로 갈 때 동승자 안내에 쓰는 기본 도착 예상(분). 차량 위치를 쓰지 않기 때문입니다. */
        const val DEFAULT_DIRECT_ETA = 5
        private const val NOTIFY_TIMEOUT_MS = 10_000L
        private const val BANNER_MS = 4_500L
        val READY_OPTIONS = listOf(0, 3, 5, 10, 15, 20)
        val WAIT_THRESHOLD = WAIT_THRESHOLD_MINUTES
        val PASSENGER_LABELS = listOf("친구", "가족", "아이", "어르신", "동료")

        /** "차량 문자 앱" 채널은 문자 앱에 내용을 채워 열기만 하므로 운전자가 전송을 눌러야 합니다. */
        fun isPrefilledSmsApp(result: NotifyResult.Sent): Boolean = result.channel.contains("문자 앱")
    }
}
