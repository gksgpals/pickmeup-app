package com.pickmeup.core.domain

import com.pickmeup.core.model.PickupRequest
import com.pickmeup.core.model.PickupSpot

/** 동승자에게 보낼 합류 안내 문구(문자·화면 폴백 공용). */
object MessageComposer {

    private const val PREFIX = "[픽미업]"
    private const val DEFAULT_LABEL = "동승자"

    /** 예: "[픽미업] 친구님, 한빛상가 동문 바깥 파란 약국 간판 앞에서 만나요. 약 2분 뒤 도착 예정입니다." */
    fun meetingMessage(request: PickupRequest, boarding: PickupSpot, etaMinutes: Int): String {
        val place = meetingPlace(request, boarding)
        val eta = if (etaMinutes <= 0) "곧 도착합니다." else "약 ${etaMinutes}분 뒤 도착 예정입니다."
        return "$PREFIX ${honorific(request)}, ${place}에서 만나요. $eta"
    }

    /**
     * 대기 장소 도착 시 보내는 안내(추가 API).
     * 예: "[픽미업] 친구님, 근처에서 기다리고 있어요. 준비되면 알려 주세요. 합류 위치는 한빛상가 동문 바깥 파란 약국 간판 앞이고, 출발하면 약 2분 걸려요."
     */
    fun waitingMessage(request: PickupRequest, boarding: PickupSpot?, minutesToBoarding: Int?): String {
        val sb = StringBuilder("$PREFIX ${honorific(request)}, 근처에서 기다리고 있어요. 준비되면 알려 주세요.")
        if (boarding != null) {
            sb.append(" 합류 위치는 ${meetingPlace(request, boarding)}")
            if (minutesToBoarding != null && minutesToBoarding > 0) {
                sb.append("이고, 출발하면 약 ${minutesToBoarding}분 걸려요.")
            } else {
                sb.append("입니다.")
            }
        }
        return sb.toString()
    }

    private fun honorific(request: PickupRequest): String {
        val label = request.passengerName?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_LABEL
        return if (label.endsWith("님")) label else "${label}님"
    }

    private fun meetingPlace(request: PickupRequest, boarding: PickupSpot): String {
        val sign = boarding.meetingSign.trim()
        val hubName = request.hub.name
        return if (sign.startsWith(hubName)) sign else "$hubName $sign"
    }
}
