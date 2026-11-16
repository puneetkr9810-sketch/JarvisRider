package com.jarvisrider.core

enum class AnswerIntent { ANSWER, REJECT, UNKNOWN }

data class AnswerDecision(val intent: AnswerIntent, val speaker: Boolean = false)

/**
 * Classifies the rider's reply to "Call from X. Say answer or reject."
 * Works on English, Hinglish and Devanagari Hindi ("हाँ उठा लो", "काट दो").
 * Any negation ("don't answer", "mat uthao", "nahi") is treated as reject.
 */
object AnswerParser {

    private val negations = setOf("no", "nahi", "nahin", "nai", "nahee", "nahii", "mat", "dont", "not", "na", "naa", "nope", "nah", "never")

    private val rejectWords = setOf(
        "reject", "rejected", "decline", "cut", "cat", "kut", "kaat", "kat", "kaato", "kato", "kaatdo", "katdo",
        "katt", "kaat", "busy", "ignore", "disconnect", "drop", "end", "hangup", "later", "silent", "band",
        "chhodo", "chodo", "baad", "mute", "deny"
    )
    private val rejectPhrases = listOf("hang up", "kaat do", "kat do", "kaat de", "cut it", "cut kar", "cut karo", "band karo", "baad mein", "baad me", "call later", "rehne do", "rahne do", "not now")

    private val answerWords = setOf(
        "answer", "answered", "answering", "anser", "ansar", "pick", "pickup", "accept", "receive", "recieve", "attend",
        "haan", "haa", "ha", "han", "hann", "hanji", "haanji", "yes", "yeah", "yep", "yup", "ya", "ok", "okay", "sure",
        "utha", "uthao", "uthaao", "uthalo", "uthaalo", "uthana", "connect", "jarur", "zaroor", "jaroor", "zarur", "bilkul",
        "lelo", "talk", "baat", "chalo", "theek", "thik"
    )
    private val answerPhrases = listOf("pick up", "utha lo", "uthaa lo", "utha do", "le lo", "baat karo", "baat karao", "theek hai", "thik hai", "go ahead")

    fun parse(text: String): AnswerDecision {
        val tokens = TextNorm.tokens(text)
        if (tokens.isEmpty()) return AnswerDecision(AnswerIntent.UNKNOWN)
        val joined = tokens.joinToString(" ")
        val keys = tokens.map { TextNorm.phoneticKey(it) }
        val speaker = tokens.any { it == "speaker" || it == "loudspeaker" || it == "speekar" || it == "speakerphone" }

        fun hasWord(set: Set<String>): Boolean {
            val setKeys = set.map { TextNorm.phoneticKey(it) }.toSet()
            return tokens.any { it in set } || keys.any { it.length >= 3 && it in setKeys }
        }
        fun hasPhrase(list: List<String>): Boolean = list.any { " $joined ".contains(" $it ") }

        val negationKeys = negations.map { TextNorm.phoneticKey(it) }.filter { it.length >= 3 }.toSet()
        val negated = tokens.any { it in negations } || keys.any { it.length >= 3 && it in negationKeys }
        val reject = hasWord(rejectWords) || hasPhrase(rejectPhrases)
        val answer = hasWord(answerWords) || hasPhrase(answerPhrases)

        return when {
            negated -> AnswerDecision(AnswerIntent.REJECT)
            reject -> AnswerDecision(AnswerIntent.REJECT)
            answer -> AnswerDecision(AnswerIntent.ANSWER, speaker)
            else -> AnswerDecision(AnswerIntent.UNKNOWN)
        }
    }

    /** Uses the first recognizer alternative that gives a clear decision. */
    fun parseAlternatives(alternatives: List<String>): AnswerDecision {
        for (alt in alternatives) {
            val d = parse(alt)
            if (d.intent != AnswerIntent.UNKNOWN) return d
        }
        return AnswerDecision(AnswerIntent.UNKNOWN)
    }
}
