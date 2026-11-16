package com.jarvisrider.core

/**
 * Minimal Devanagari -> Latin (Hinglish style) transliterator.
 *
 * The hi-IN speech recognizer usually returns Devanagari ("राहुल को कॉल करो") while
 * contact names are usually typed in Latin script ("Rahul"). Converting everything to a
 * common Latin form lets the fuzzy/phonetic matcher compare them.
 *
 * It applies inherent-vowel ("schwa") insertion with the usual word-final and simple
 * medial schwa-deletion rules, which is good enough for names and short commands.
 */
object Transliterator {

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
        'ऍ' to "e", 'ऎ' to "e", 'ऒ' to "o"
    )

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
        // precomposed nukta letters
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "r",
        '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y"
    )

    private val nuktaForms = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'य' to "y"
    )

    private val vowelSigns = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
        'ॆ' to "e", 'ॊ' to "o"
    )

    private const val HALANT = '्'
    private const val NUKTA = '़'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'

    fun containsDevanagari(text: String): Boolean = text.any { it in '\u0900'..'\u097F' }

    fun toLatin(text: String): String {
        if (!containsDevanagari(text)) return text
        val out = StringBuilder()
        val word = StringBuilder()
        for (ch in text) {
            if (ch in '\u0900'..'\u097F' && ch != '।' && ch != '॥') {
                word.append(ch)
            } else {
                if (word.isNotEmpty()) {
                    out.append(transliterateWord(word.toString()))
                    word.clear()
                }
                out.append(if (ch == '।' || ch == '॥') '.' else ch)
            }
        }
        if (word.isNotEmpty()) out.append(transliterateWord(word.toString()))
        return out.toString()
    }

    /** One syllable unit: consonant(s) + vowel. [inherent] = vowel is the implicit schwa. */
    private class Unit(var text: String, var inherent: Boolean, val isConsonant: Boolean)

    private fun transliterateWord(word: String): String {
        val units = ArrayList<Unit>()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            when {
                c in '०'..'९' -> {
                    units.add(Unit(('0' + (c - '०')).toString(), false, false)); i++
                }
                consonants.containsKey(c) -> {
                    var base = consonants.getValue(c)
                    var j = i + 1
                    if (j < word.length && word[j] == NUKTA) {
                        base = nuktaForms[c] ?: base
                        j++
                    }
                    when {
                        j < word.length && word[j] == HALANT -> {
                            units.add(Unit(base, false, true)); j++
                        }
                        j < word.length && vowelSigns.containsKey(word[j]) -> {
                            units.add(Unit(base + vowelSigns.getValue(word[j]), false, true)); j++
                        }
                        else -> units.add(Unit(base + "a", true, true))
                    }
                    i = j
                }
                independentVowels.containsKey(c) -> {
                    units.add(Unit(independentVowels.getValue(c), false, false)); i++
                }
                c == ANUSVARA || c == CHANDRABINDU -> {
                    units.add(Unit("n", false, false)); i++
                }
                c == VISARGA -> {
                    units.add(Unit("h", false, false)); i++
                }
                vowelSigns.containsKey(c) -> { // stray matra
                    units.add(Unit(vowelSigns.getValue(c), false, false)); i++
                }
                else -> i++ // other signs (nukta alone, zero-width joiners, etc.)
            }
        }
        applySchwaDeletion(units)
        return units.joinToString("") { it.text }
    }

    private fun endsWithVowel(u: Unit): Boolean = u.text.lastOrNull()?.let { it in "aeiou" } ?: false
    private fun hasVowel(u: Unit): Boolean = u.text.any { it in "aeiou" }

    private fun applySchwaDeletion(units: MutableList<Unit>) {
        // Word-final schwa: "राम" -> "ram" (but keep it for one-letter words like "न" -> "na").
        val consonantCount = units.count { it.isConsonant }
        val last = units.lastOrNull { it.isConsonant || hasVowel(it) }
        if (last != null && last.inherent && consonantCount > 1 && units.last() === last) {
            last.text = last.text.dropLast(1)
            last.inherent = false
        }
        // Medial schwa: V C(a) C V -> drop the schwa ("करना" -> "karna").
        var k = 1
        while (k < units.size - 1) {
            val prev = units[k - 1]
            val cur = units[k]
            val next = units[k + 1]
            if (cur.inherent && endsWithVowel(prev) && next.isConsonant && hasVowel(next)) {
                cur.text = cur.text.dropLast(1)
                cur.inherent = false
                k += 2 // a deleted schwa closes the syllable; don't delete the next one
            } else {
                k++
            }
        }
    }
}
