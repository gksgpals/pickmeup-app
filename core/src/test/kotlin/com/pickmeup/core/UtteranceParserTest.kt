package com.pickmeup.core

import com.pickmeup.core.data.SampleHubRepository
import com.pickmeup.core.domain.UtteranceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceParserTest {
    private val repo = SampleHubRepository()
    private val parser = UtteranceParser(repo)

    @Test
    fun scenario_namedHub_eastGate_threeMinutes() {
        val p = parser.parse("한빛상가 동문에서 친구 태울 거야. 3분쯤 걸린대")
        assertEquals(listOf(SampleHubRepository.HANBIT_ID), p.hubCandidates.map { it.id })
        assertEquals("hanbit-east", p.entrance?.id)
        assertEquals(3, p.readyInMinutes)
        assertEquals("친구", p.passengerLabel)
        assertFalse(p.needs.mobilityImpaired)
        assertFalse(p.needs.heavyLuggage)
    }

    @Test
    fun scenario_genericHub_eastGate_tenMinutes_prefersHubWithThatEntrance() {
        val p = parser.parse("상가 동문에서 친구 태울 거야. 10분 정도 걸린대")
        assertEquals(SampleHubRepository.HANBIT_ID, p.hubCandidates.first().id)
        assertTrue(p.hubCandidates.size >= 2) // 운전자가 확인·선택
        assertEquals("hanbit-east", p.entrance?.id)
        assertEquals(10, p.readyInMinutes)
        assertEquals("친구", p.passengerLabel)
    }

    @Test
    fun ambiguousGenericWord_returnsMultipleCandidates() {
        val p = parser.parse("상가에서 엄마 태울 거야")
        val ids = p.hubCandidates.map { it.id }.toSet()
        assertEquals(setOf(SampleHubRepository.HANBIT_ID, SampleHubRepository.NURI_ID), ids)
        assertNull(p.entrance)
        assertNull(p.readyInMinutes)
        assertEquals("엄마", p.passengerLabel)
    }

    @Test
    fun partialName_andSpacedName() {
        assertEquals(SampleHubRepository.HANBIT_ID, parser.parse("한빛 정문으로 가자").hubCandidates.single().id)
        assertEquals(SampleHubRepository.NURI_ID, parser.parse("누리 학원가 후문").hubCandidates.single().id)
        assertEquals("nuri-back", parser.parse("누리 학원가 후문").entrance?.id)
    }

    @Test
    fun hospital_mobility_koreanNumeral() {
        val p = parser.parse("새솔병원 정문에서 할머니 모시고 갈게. 휠체어 타셔. 십 분 정도 걸린대")
        assertEquals(SampleHubRepository.SAESOL_ID, p.hubCandidates.single().id)
        assertEquals("saesol-main", p.entrance?.id)
        assertEquals(10, p.readyInMinutes)
        assertEquals("할머니", p.passengerLabel)
        assertTrue(p.needs.mobilityImpaired)
    }

    @Test
    fun alreadyOut_isZero() {
        val p = parser.parse("누리학원가 후문, 딸이 이미 나와 있대")
        assertEquals("nuri-back", p.entrance?.id)
        assertEquals(0, p.readyInMinutes)
        assertEquals("딸", p.passengerLabel)
        assertEquals(0, parser.parse("금방 나온대").readyInMinutes)
        assertEquals(0, parser.parse("아들 바로 나온대").readyInMinutes)
        val waiting = parser.parse("친구가 벌써 기다리고 있대")
        assertEquals(0, waiting.readyInMinutes)
        assertFalse("기다리 ≠ 다리", waiting.needs.mobilityImpaired)
    }

    @Test
    fun luggage_and_nativeFiveMinutes() {
        val p = parser.parse("한빛상가 정문, 친구가 짐이 많대. 오 분 걸린대")
        assertEquals("hanbit-main", p.entrance?.id)
        assertEquals(5, p.readyInMinutes)
        assertTrue(p.needs.heavyLuggage)
        assertTrue(parser.parse("캐리어 두 개 있대").needs.heavyLuggage)
        assertTrue(parser.parse("가방이 엄청 많대").needs.heavyLuggage)
    }

    @Test
    fun minuteExpressions() {
        assertEquals(30, parser.parse("반시간 걸린대").readyInMinutes)
        assertEquals(30, parser.parse("30분 뒤에 끝나").readyInMinutes)
        assertEquals(60, parser.parse("한 시간 정도 걸린대").readyInMinutes)
        assertEquals(90, parser.parse("한 시간 반 걸린대").readyInMinutes)
        assertEquals(70, parser.parse("1시간 10분 걸린대").readyInMinutes)
        assertEquals(15, parser.parse("십오 분이면 나온대").readyInMinutes)
        assertEquals(10, parser.parse("5~10분 걸린대").readyInMinutes)
        assertEquals(UtteranceParser.SOON_MINUTES, parser.parse("곧 나온대").readyInMinutes)
        assertNull("시각 표현은 준비 시간 아님", parser.parse("5시 30분에 끝나").readyInMinutes)
        assertNull(parser.parse("이 분이 나오신대").readyInMinutes)
    }

    @Test
    fun nothingRecognized() {
        val p = parser.parse("오늘 날씨 어때")
        assertTrue(p.hubCandidates.isEmpty())
        assertNull(p.entrance)
        assertNull(p.readyInMinutes)
        assertNull(p.passengerLabel)
        assertEquals("오늘 날씨 어때", p.raw)
    }
}
