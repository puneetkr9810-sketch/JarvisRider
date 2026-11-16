package com.jarvisrider.core

/** What the rider asked for. */
sealed class Command {
    data class Call(val name: String, val speaker: Boolean) : Command()
    data class CallNumber(val number: String, val speaker: Boolean) : Command()
    /** [lastAny] = true for "call back" (last call in either direction), false for redial (last outgoing). */
    data class Redial(val speaker: Boolean, val lastAny: Boolean) : Command()
    /** The rider said only "call" / "call on speaker" without a name. */
    data class AskWho(val speaker: Boolean) : Command()
    object Cancel : Command()
    data class Unknown(val text: String) : Command()
}

/**
 * Parses English, Hindi (Devanagari) and Hinglish call commands, e.g.
 * "call Rahul", "Rahul ko call karo", "राहुल को फ़ोन लगाओ", "call mom on speaker",
 * "redial", "dobara call karo", "call back", "call 98765 43210", "cancel".
 */
object CommandParser {

    private val callWords = setOf(
        "call", "kol", "kal", "kaal", "col", "cal", "caal", "koll", "calling",
        "phone", "fon", "phon", "fone", "dial", "ring"
    )
    private val hindiCallVerbs = setOf("lagao", "laga", "lagado", "lagaao", "milao", "mila", "milado", "milaao")
    private val verbWords = setOf(
        "karo", "kar", "karna", "karni", "kariye", "kariy", "karie", "karein", "karen", "kijiye", "kijie",
        "do", "de", "dena", "dijiye", "karde", "kardo", "karado", "karao", "karwao", "krdo", "kro",
        "lagao", "laga", "lagado", "lagaao", "lagana", "milao", "mila", "milado", "milaao", "milana",
        "please", "plz", "pls", "now", "abhi", "jaldi", "zara", "jara", "na", "naa", "yaar", "jee"
    )
    private val connectorWords = setOf("ko", "se", "ke", "ka", "ki", "liye", "to", "up", "for", "with", "pe", "par", "per", "on", "the", "a", "an", "mein", "me")
    private val fillerWords = setOf(
        "hey", "hi", "hello", "jarvis", "ok", "okay", "please", "plz", "pls", "can", "could", "would",
        "you", "will", "i", "want", "wanna", "need", "to", "a", "an", "the", "make", "place", "give",
        "zara", "jara", "jaldi", "abhi", "mujhe", "my", "mere", "meri", "mera", "mujhko", "humko",
        "kindly", "just", "quickly", "now", "bhai", "yaar"
    )
    private val speakerHints = setOf("speaker", "speakerphone", "loudspeaker", "speekar", "spikar", "speakar", "spekar", "speeker", "loud")
    private val speakerNoise = setOf("on", "pe", "par", "per", "with", "mode", "the", "phone") // only removed when a speaker word exists
    private val redialMarkers = setOf(
        "redial", "redail", "redialing", "dobara", "dubara", "dobaara", "phirse", "firse", "again",
        "last", "previous", "pichhla", "pichla", "pichle", "aakhri", "akhri"
    )
    private val callBackMarkers = setOf("back", "callback", "wapas", "vapas", "waapas")
    private val redialPhrases = listOf("phir se", "fir se", "re dial", "one more time", "ek baar aur", "ek aur baar")
    private val redialNoise = setOf("number", "dialed", "dialled", "dialled", "wala", "vala", "wale", "vale", "person", "contact", "caller", "jisne", "kiya", "tha", "aaya", "aya", "missed")
    private val cancelPhrases = listOf(
        "cancel", "stop", "never mind", "nevermind", "nothing", "exit", "quit", "no one", "nobody",
        "rehne do", "rahne do", "rehen do", "kuch nahi", "kuchh nahi", "kuch nahin", "kuchh nahin",
        "band karo", "chhodo", "chodo", "chhod do", "chod do", "mat karo", "koi nahi", "koi nahin", "forget it", "leave it"
    )

    fun parse(text: String): Command {
        val tokens0 = TextNorm.tokens(text)
        if (tokens0.isEmpty()) return Command.Unknown(text)
        val joined0 = tokens0.joinToString(" ")

        if (isCancel(tokens0, joined0)) return Command.Cancel

        var tokens = tokens0
        val speaker = tokens.any { isSpeakerWord(it) }
        if (speaker) tokens = tokens.filterNot { isSpeakerWord(it) || it in speakerNoise }

        // Plain phone number: "call 98765 43210" / "9876543210 pe call karo"
        val digitTokens = tokens.filter { t -> t.all { it.isDigit() } }
        val digitCount = digitTokens.sumOf { it.length }
        if (digitCount >= 3 && tokens.any { it in callWords || it in hindiCallVerbs || it.all(Char::isDigit) }) {
            return Command.CallNumber(digitTokens.joinToString(""), speaker)
        }

        val joined = tokens.joinToString(" ")
        val hasRedial = tokens.any { it in redialMarkers } || redialPhrases.any { containsPhrase(joined, it) }
        val hasCallBack = tokens.any { it in callBackMarkers }
        if (hasRedial || hasCallBack) {
            var stripped = joined
            for (p in redialPhrases) stripped = (" $stripped ").replace(" $p ", " ").trim()
            val rest = stripped.split(' ').filter {
                it.isNotEmpty() && it !in redialMarkers && it !in callBackMarkers && it !in callWords &&
                    it !in verbWords && it !in connectorWords && it !in fillerWords && it !in redialNoise &&
                    it !in hindiCallVerbs
            }
            return if (rest.isEmpty()) Command.Redial(speaker, lastAny = hasCallBack && !hasRedial)
            else Command.Call(rest.joinToString(" "), speaker)
        }

        val callIdx = tokens.indexOfFirst { it in callWords }
        if (callIdx >= 0) {
            val after = clean(tokens.subList(callIdx + 1, tokens.size))
            val before = clean(tokens.subList(0, callIdx))
            val name = if (after.isNotEmpty()) after else before
            return if (name.isEmpty()) Command.AskWho(speaker) else Command.Call(name.joinToString(" "), speaker)
        }

        // "Rahul ko lagao" / "mummy ko milao"
        val verbIdx = tokens.indexOfFirst { it in hindiCallVerbs }
        if (verbIdx > 0 && tokens.subList(0, verbIdx).contains("ko")) {
            val name = clean(tokens.subList(0, verbIdx))
            if (name.isNotEmpty()) return Command.Call(name.joinToString(" "), speaker)
        }

        if (speaker) {
            val name = clean(tokens)
            if (name.isNotEmpty()) return Command.Call(name.joinToString(" "), true)
            return Command.AskWho(true)
        }
        return Command.Unknown(text)
    }

    /** Strip filler words from a free answer to "who do you want to call?" ("Rahul please" -> "Rahul"). */
    fun cleanName(text: String): String {
        val tokens = TextNorm.tokens(text).filterNot { isSpeakerWord(it) }
        return clean(tokens).joinToString(" ")
    }

    private fun clean(tokens: List<String>): List<String> {
        var t = tokens.filterNot { it in fillerWords }
        // trim verb/connector words from both ends, keep words inside the name ("Sharma ji")
        val trim = verbWords + connectorWords + callWords + hindiCallVerbs
        while (t.isNotEmpty() && t.first() in trim) t = t.drop(1)
        while (t.isNotEmpty() && t.last() in trim) t = t.dropLast(1)
        // possessive leftovers like "rahul s"
        if (t.size > 1 && t.last() == "s") t = t.dropLast(1)
        return t
    }

    private fun isSpeakerWord(t: String): Boolean {
        if (t in speakerHints) return true
        if (t.length in 5..9 && t.startsWith("sp")) {
            return Similarity.jaroWinkler(TextNorm.phoneticKey(t), TextNorm.phoneticKey("speaker")) >= 0.88
        }
        return false
    }

    private fun isCancel(tokens: List<String>, joined: String): Boolean {
        if (tokens.first() == "cancel" || tokens.first() == "stop") return true
        if (cancelPhrases.any { joined == it }) return true
        // short replies that contain a cancel phrase and no call word: "nahi rehne do"
        return tokens.size <= 4 && tokens.none { it in callWords } && cancelPhrases.any { containsPhrase(joined, it) }
    }

    private fun containsPhrase(joined: String, phrase: String): Boolean =
        " $joined ".contains(" $phrase ")
}
