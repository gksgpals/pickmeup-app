package com.pickmeup.core

import com.pickmeup.core.data.SampleHubRepository
import com.pickmeup.core.domain.MessageComposer
import com.pickmeup.core.model.PickupRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageComposerTest {
    private val hub = SampleHubRepository().byId(SampleHubRepository.HANBIT_ID)!!
    private val spot = hub.spots.first { it.id == "hanbit-s1" }

    @Test
    fun meetingMessage_matchesDocExample() {
        val msg = MessageComposer.meetingMessage(PickupRequest(hub, null, 3, "친구"), spot, 2)
        assertEquals("[픽미업] 친구님, 한빛상가 동문 바깥 파란 약국 간판 앞에서 만나요. 약 2분 뒤 도착 예정입니다.", msg)
    }

    @Test
    fun meetingMessage_fallbackLabelAndZeroEta() {
        val msg = MessageComposer.meetingMessage(PickupRequest(hub, null, 0, null), spot, 0)
        assertEquals("[픽미업] 동승자님, 한빛상가 동문 바깥 파란 약국 간판 앞에서 만나요. 곧 도착합니다.", msg)
        val blank = MessageComposer.meetingMessage(PickupRequest(hub, null, 0, "  "), spot, 1)
        assertTrue(blank.startsWith("[픽미업] 동승자님,"))
    }

    @Test
    fun waitingMessage_includesMeetingPlace() {
        val msg = MessageComposer.waitingMessage(PickupRequest(hub, null, 15, "엄마"), spot, 2)
        assertTrue(msg.startsWith("[픽미업] 엄마님, 근처에서 기다리고 있어요."))
        assertTrue(msg.contains("한빛상가 동문 바깥 파란 약국 간판 앞"))
        assertTrue(msg.contains("약 2분"))
    }
}
