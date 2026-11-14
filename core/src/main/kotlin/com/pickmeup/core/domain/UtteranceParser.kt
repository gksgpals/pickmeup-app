package com.pickmeup.core.domain

import com.pickmeup.core.model.Entrance
import com.pickmeup.core.model.Hub
import com.pickmeup.core.model.ParsedUtterance
import com.pickmeup.core.model.PassengerNeeds

/**
 * 음성 인식 결과 문장을 규칙 기반으로 해석합니다(ML 없음).
 *
 * 예: "한빛상가 동문에서 친구 태울 거야. 3분쯤 걸린대"
 *  → 거점 [한빛상가], 출입구 동문, 준비 3분, 동승자 "친구"
 *
 * 찾지 못한 값은 null(또는 빈 후보 목록)로 두고, 화면에서 운전자가 수정합니다.
 */
class UtteranceParser(private val hubs: HubRepository) {

    fun parse(text: String): ParsedUtterance {
        val compact = compact(text)
        val ranked = rankHubs(compact)
        val entrance = ranked.firstOrNull()?.let { findEntrance(it, compact) }
        return ParsedUtterance(
            raw = text,
            hubCandidates = ranked,
            entrance = entrance,
            readyInMinutes = parseMinutes(text),
            passengerLabel = parsePassengerLabel(text),
            needs = parseNeeds(text),
        )
    }

    // ───────────────────────────── 거점 ─────────────────────────────

    private fun rankHubs(compact: String): List<Hub> {
        if (compact.isEmpty()) return emptyList()
        val scored = hubs.all().mapNotNull { hub ->
            val specific = specificScore(hub, compact)
            val generic = if (specific == 0 && genericMatch(hub, compact)) GENERIC_SCORE else 0
            val entranceBonus = if (findEntrance(hub, compact) != null) ENTRANCE_BONUS else 0
            val score = specific + generic + entranceBonus
            if (score > 0) Triple(hub, score, specific > 0) else null
        }
        if (scored.isEmpty()) return emptyList()
        // 이름(또는 고유 별칭)으로 분명히 지목한 거점이 있으면 그 거점들만 남깁니다.
        val pool = if (scored.any { it.third }) scored.filter { it.third } else scored
        return pool.sortedWith(compareByDescending<Triple<Hub, Int, Boolean>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    /** 이름·별칭·고유어(일반 명사 접미사를 뗀 부분) 매칭 점수. 0이면 고유 매칭 없음. */
    private fun specificScore(hub: Hub, compact: String): Int {
        var best = 0
        val name = compact(hub.name)
        if (name.length >= 2 && compact.contains(name)) best = maxOf(best, 100 + name.length)
        for (alias in hub.aliases) {
            val a = compact(alias)
            if (a.length >= 2 && a !in GENERIC_WORDS && compact.contains(a)) best = maxOf(best, 80 + a.length)
        }
        for (core in coreTokens(hub)) {
            if (compact.contains(core)) best = maxOf(best, 70 + core.length)
        }
        return best
    }

    /** "한빛상가" → "한빛", "누리학원가" → "누리" 처럼 일반 접미사를 뗀 고유어. */
    private fun coreTokens(hub: Hub): Set<String> {
        val out = mutableSetOf<String>()
        for (raw in listOf(hub.name) + hub.aliases) {
            val c = compact(raw)
            for (suffix in GENERIC_SUFFIXES) {
                if (c.endsWith(suffix) && c.length - suffix.length >= 2) {
                    out += c.substring(0, c.length - suffix.length)
                }
            }
        }
        return out
    }

    /** "상가", "병원"처럼 시설 종류만 말한 경우. */
    private fun genericMatch(hub: Hub, compact: String): Boolean {
        val names = (listOf(hub.name) + hub.aliases).map { compact(it) }
        return GENERIC_WORDS.any { word -> compact.contains(word) && names.any { it.contains(word) } }
    }

    // ───────────────────────────── 출입구 ─────────────────────────────

    private fun findEntrance(hub: Hub, compact: String): Entrance? {
        var best: Entrance? = null
        var bestLen = 0
        for (e in hub.entrances) {
            for (alias in e.aliases + e.name) {
                val a = compact(alias)
                if (a.length >= 2 && a.length > bestLen && compact.contains(a)) {
                    best = e
                    bestLen = a.length
                }
            }
        }
        return best
    }

    companion object {
        private const val GENERIC_SCORE = 30
        private const val ENTRANCE_BONUS = 15

        /** 시설 종류를 나타내는 일반 명사. 단독으로는 약한 매칭으로만 씁니다. */
        private val GENERIC_WORDS = listOf(
            "상가", "병원", "학원가", "학원", "빌딩", "센터", "플라자", "마트", "아파트", "메디컬",
        )
        private val GENERIC_SUFFIXES = listOf(
            "학원가", "상가", "병원", "학원", "빌딩", "센터", "플라자", "메디컬센터", "메디컬", "마트",
        )

        /** 공백·문장부호 제거, 소문자화. 이름 매칭용. */
        internal fun compact(s: String): String =
            s.lowercase().replace(Regex("[\\s.,!?~·'\"()\\[\\]-]+"), "")

        // ───────────────────────────── 준비 시간 ─────────────────────────────

        private const val NOT_HANGUL_BEFORE = "(?<![가-힣])"

        /** 시각 표현("5시 30분", "7시 반")은 준비 시간이 아니므로 먼저 지웁니다. */
        private val CLOCK = Regex("\\d+\\s*시(?!간)\\s*(\\d+\\s*분|반)?")
        private val DIGIT_HOURS = Regex("(\\d+)\\s*시간\\s*(반)?")
        private val NATIVE_HOURS = Regex("${NOT_HANGUL_BEFORE}(한|두|세|네)\\s*시간\\s*(반)?")
        private val HALF_HOUR = Regex("반\\s*시간")
        private val DIGIT_RANGE_MIN = Regex("(\\d+)\\s*(?:~|-|에서)\\s*(\\d+)\\s*분")
        private val DIGIT_MIN = Regex("(\\d+)\\s*분")
        private val SINO_MIN = Regex("${NOT_HANGUL_BEFORE}([일이삼사오육칠팔구십]+)\\s*분")
        private val SINO_SINGLE_CONTEXT = Regex("^\\s*(정도|쯤|뒤|후|걸|이면|만에|내|안에|남)")
        private val ZERO_WORDS = Regex(
            "이미|벌써|나와\\s*있|나와서\\s*기다|나왔|기다리고\\s*있|도착해\\s*있|금방|지금\\s*(나와|나간|나온|내려)|바로\\s*(나와|나온|나간|내려|와|온|올)",
        )
        private val SOON_WORDS = Regex("곧|잠깐|잠시만|조금만")

        internal fun parseMinutes(text: String): Int? {
            val t = CLOCK.replace(text, " ")
            var hours: Int? = null
            DIGIT_HOURS.find(t)?.let { m ->
                hours = m.groupValues[1].toInt() * 60 + if (m.groupValues[2].isNotEmpty()) 30 else 0
            }
            if (hours == null) NATIVE_HOURS.find(t)?.let { m ->
                val h = when (m.groupValues[1]) { "한" -> 1; "두" -> 2; "세" -> 3; else -> 4 }
                hours = h * 60 + if (m.groupValues[2].isNotEmpty()) 30 else 0
            }
            if (hours == null && HALF_HOUR.containsMatchIn(t)) hours = 30

            val minutes: Int? = DIGIT_RANGE_MIN.find(t)?.let { m ->
                maxOf(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            } ?: DIGIT_MIN.find(t)?.groupValues?.get(1)?.toInt()
                ?: sinoMinutes(t)

            if (hours != null || minutes != null) return (hours ?: 0) + (minutes ?: 0)
            if (ZERO_WORDS.containsMatchIn(text)) return 0
            if (SOON_WORDS.containsMatchIn(text)) return SOON_MINUTES
            return null
        }

        /** "곧 나온대"처럼 숫자 없이 금세 나온다는 표현의 기본값(분). */
        const val SOON_MINUTES = 3

        private fun sinoMinutes(t: String): Int? {
            for (m in SINO_MIN.findAll(t)) {
                val token = m.groupValues[1]
                // "이 분"(이분=this person), "구분" 같은 오인을 막기 위해 한 글자 "이/구"는 뒤 문맥을 확인합니다.
                if (token.length == 1 && (token == "이" || token == "구")) {
                    if (!SINO_SINGLE_CONTEXT.containsMatchIn(t.substring(m.range.last + 1))) continue
                }
                val v = sinoToInt(token) ?: continue
                if (v in 1..600) return v
            }
            return null
        }

        private val SINO_DIGITS = mapOf(
            '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9,
        )

        /** "오" → 5, "십" → 10, "십오" → 15, "이십" → 20, "삼십오" → 35. */
        internal fun sinoToInt(token: String): Int? {
            if (token.isEmpty()) return null
            val tenIdx = token.indexOf('십')
            if (tenIdx < 0) {
                return if (token.length == 1) SINO_DIGITS[token[0]] else null
            }
            if (token.indexOf('십', tenIdx + 1) >= 0) return null
            val tensPart = token.substring(0, tenIdx)
            val onesPart = token.substring(tenIdx + 1)
            val tens = when (tensPart.length) {
                0 -> 1
                1 -> SINO_DIGITS[tensPart[0]] ?: return null
                else -> return null
            }
            val ones = when (onesPart.length) {
                0 -> 0
                1 -> SINO_DIGITS[onesPart[0]] ?: return null
                else -> return null
            }
            return tens * 10 + ones
        }

        // ───────────────────────────── 동승자 ─────────────────────────────

        /** 말한 단어 → 화면·문자에 쓸 호칭. 긴 단어를 먼저 둡니다. */
        private val LABELS: List<Pair<String, String>> = listOf(
            "여자친구" to "여자친구", "남자친구" to "남자친구", "여친" to "여자친구", "남친" to "남자친구",
            "외할머니" to "외할머니", "외할아버지" to "외할아버지", "할머니" to "할머니", "할아버지" to "할아버지", "어르신" to "어르신",
            "시어머니" to "시어머니", "장모님" to "장모님", "부모님" to "부모님",
            "어머님" to "어머님", "아버님" to "아버님", "어머니" to "어머니", "아버지" to "아버지",
            "엄마" to "엄마", "아빠" to "아빠", "와이프" to "아내", "아내" to "아내", "남편" to "남편",
            "딸아이" to "딸", "딸내미" to "딸", "아들내미" to "아들", "아들" to "아들", "딸" to "딸",
            "애기" to "아기", "아기" to "아기", "아이" to "아이", "애" to "아이",
            "동생" to "동생", "언니" to "언니", "누나" to "누나", "오빠" to "오빠", "형" to "형",
            "친구" to "친구", "동료" to "동료", "선배" to "선배", "후배" to "후배",
            "선생님" to "선생님", "손님" to "손님",
        )
        private const val LABEL_END = "(?=$|\\s|[을를이가은는도랑과와하한께좀네만의들님,.!?]|에게)"
        private val LABEL_REGEXES: List<Pair<Regex, String>> = LABELS.map { (word, label) ->
            Regex("(?:(?<![가-힣])|(?<=우리|울|내))${word}${LABEL_END}") to label
        }

        internal fun parsePassengerLabel(text: String): String? {
            var bestIdx = Int.MAX_VALUE
            var bestLen = 0
            var best: String? = null
            for ((re, label) in LABEL_REGEXES) {
                val m = re.find(text) ?: continue
                val idx = m.range.first
                val len = m.value.length
                if (idx < bestIdx || (idx == bestIdx && len > bestLen)) {
                    bestIdx = idx; bestLen = len; best = label
                }
            }
            return best
        }

        // ───────────────────────────── 특화 조건 ─────────────────────────────

        private val MOBILITY = Regex(
            "휠체어|(?<!기)다리|거동|할머니|할아버지|어르신|노인|유모차|목발|지팡이|깁스|보행\\s*(이|이\\s*)?불편|걷기\\s*(힘들|불편|어려)|잘\\s*못\\s*걸|임산부|아기|애기",
        )
        private val LUGGAGE = Regex(
            "짐|캐리어|트렁크|(가방|쇼핑백|봉지|박스|상자)[가-힣\\s]{0,6}(많|여러|한가득|잔뜩)|많은\\s*(가방|쇼핑백|봉지|박스|상자)|(?<![가-힣])장\\s*(봤|본|보고|봐)|이삿",
        )

        internal fun parseNeeds(text: String): PassengerNeeds = PassengerNeeds(
            mobilityImpaired = MOBILITY.containsMatchIn(text),
            heavyLuggage = LUGGAGE.containsMatchIn(text),
        )
    }
}
