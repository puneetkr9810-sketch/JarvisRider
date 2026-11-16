package com.jarvisrider.core

import java.text.Normalizer

/** Text normalisation and Hinglish phonetic keys shared by all parsers/matchers. */
object TextNorm {

    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")
    private val spaces = Regex("\\s+")

    /** Lowercase Latin text with punctuation removed; Devanagari is transliterated first. */
    fun normalize(text: String): String {
        val latin = Transliterator.toLatin(text)
        val noApostrophe = latin.replace("'", "").replace("’", "")
        val decomposed = Normalizer.normalize(noApostrophe, Normalizer.Form.NFD).replace(diacritics, "")
        return decomposed.lowercase()
            .replace(nonAlnum, " ")
            .replace(spaces, " ")
            .trim()
    }

    fun tokens(text: String): List<String> {
        val n = normalize(text)
        return if (n.isEmpty()) emptyList() else n.split(' ')
    }

    /**
     * Phonetic key tuned for Indian names written in Latin script, so that spelling
     * variants collapse together: Preeti/Priti, Mohd/Mohammad-ish vowels, Vijay/Vijai,
     * Shubham/Subham, Khushboo/Kushbu, Farhan/Pharhan, Zubair/Jubair, Raju/Raaju ...
     */
    fun phoneticKey(word: String): String {
        var s = normalize(word).replace(" ", "")
        if (s.isEmpty()) return s
        s = s.replace("ck", "k")
            .replace(Regex("c(?!h)"), "k")
            .replace("q", "k")
            .replace("x", "ks")
            .replace("chh", "c")
            .replace("ch", "c")
            .replace("ph", "f")
            .replace("bh", "b")
            .replace("dh", "d")
            .replace("th", "t")
            .replace("kh", "k")
            .replace("gh", "g")
            .replace("jh", "j")
            .replace("sh", "s")
            .replace("zh", "j")
            .replace("w", "v")
            .replace("z", "j")
            .replace("ee", "i")
            .replace("ii", "i")
            .replace("oo", "u")
            .replace("uu", "u")
            .replace("aa", "a")
            .replace("ou", "o")
            .replace("au", "o")
            .replace("ai", "e")
            .replace("ey", "e")
            .replace(Regex("ay$"), "e")
            .replace(Regex("y$"), "i")
        // collapse doubled letters: "pappu" -> "papu"
        val sb = StringBuilder()
        for (ch in s) if (sb.isEmpty() || sb.last() != ch) sb.append(ch)
        s = sb.toString()
        if (s.length > 2 && s.endsWith("h")) s = s.dropLast(1)
        if (s.length > 3 && s.endsWith("a")) s = s.dropLast(1)
        return s
    }

    fun phoneticPhrase(text: String): String = tokens(text).joinToString(" ") { phoneticKey(it) }

    /** Digits only, keeping a leading '+'. */
    fun digitsOf(text: String): String {
        val trimmed = text.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /** "9876543210" -> "9 8 7 6 5, 4 3 2 1 0" so TTS reads digit by digit. */
    fun spokenDigits(number: String): String {
        val d = number.filter { it.isDigit() || it == '+' }.replace("+", "plus ")
        if (d.isEmpty()) return number
        val chars = d.filter { it.isDigit() }.toList()
        val prefix = if (d.startsWith("plus ")) "plus " else ""
        val groups = chars.chunked(5).joinToString(", ") { g -> g.joinToString(" ") }
        return prefix + groups
    }
}
