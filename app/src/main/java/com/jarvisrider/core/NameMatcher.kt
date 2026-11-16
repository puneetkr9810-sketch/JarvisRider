package com.jarvisrider.core

import kotlin.math.max
import kotlin.math.min

/** Result of matching a spoken name against the contact list. */
sealed class MatchResult<out T> {
    data class Single<T>(val item: T, val score: Double) : MatchResult<T>()
    data class Ambiguous<T>(val options: List<Scored<T>>) : MatchResult<T>()
    data class NoMatch(val bestScore: Double) : MatchResult<Nothing>()
}

data class Scored<T>(val item: T, val score: Double)

/**
 * Fuzzy + phonetic name matcher.
 *
 * Score per name = max of
 *  - whole-string similarity (plain and phonetic), and
 *  - token coverage: every spoken token matched to its best contact-name token.
 * Word similarity blends Jaro-Winkler (plain + phonetic) with Levenshtein on phonetic keys,
 * and identical phonetic keys count as a near-exact match (Hinglish spelling variants).
 */
object NameMatcher {

    const val ACCEPT = 0.85
    const val AMBIGUITY_MARGIN = 0.05
    const val MAX_OPTIONS = 3

    /** Relationship words people actually use for contacts in India. */
    private val aliasGroups: List<Set<String>> = listOf(
        setOf("mom", "mommy", "mummy", "mummi", "mammi", "mumma", "mamma", "maa", "ma", "mother", "mum", "ammi", "amma", "mataji", "mata ji", "aai"),
        setOf("dad", "daddy", "papa", "pappa", "father", "pitaji", "pita ji", "abbu", "baba", "pa", "dady", "appa"),
        setOf("brother", "bhai", "bhaiya", "bhaiyya", "bro", "bhaijaan", "veerji", "bhaiyaa"),
        setOf("sister", "didi", "di", "behen", "bahen", "sis", "behna", "dii"),
        setOf("wife", "biwi", "patni", "wifey", "begum", "missus", "home minister"),
        setOf("husband", "pati", "hubby", "pati dev"),
        setOf("grandma", "dadi", "nani", "grandmother", "dadima", "nanima"),
        setOf("grandpa", "dada", "nana", "grandfather", "dadaji", "nanaji"),
        setOf("home", "ghar", "house")
    )

    fun wordScore(q: String, n: String): Double {
        if (q.isEmpty() || n.isEmpty()) return 0.0
        if (q == n) return 1.0
        val pq = TextNorm.phoneticKey(q)
        val pn = TextNorm.phoneticKey(n)
        if (pq.isNotEmpty() && pq == pn) return 0.97
        val jw = max(Similarity.jaroWinkler(q, n), Similarity.jaroWinkler(pq, pn))
        val lev = Similarity.levenshteinSimilarity(pq, pn)
        return min(0.96, 0.75 * jw + 0.25 * lev)
    }

    /** Similarity between a spoken phrase and a contact name, 0..1. */
    fun score(spoken: String, contactName: String): Double {
        val qTokens = TextNorm.tokens(spoken)
        val nTokens = TextNorm.tokens(contactName)
        if (qTokens.isEmpty() || nTokens.isEmpty()) return 0.0
        var best = phraseScore(qTokens, nTokens)
        val joined = qTokens.joinToString(" ")
        val joinedKey = TextNorm.phoneticPhrase(joined)
        val group = aliasGroups.firstOrNull { g -> joined in g || g.any { TextNorm.phoneticPhrase(it) == joinedKey } }
        if (group != null) {
            for (alias in group) {
                val s = phraseScore(alias.split(' '), nTokens)
                if (s > best) best = s
            }
        }
        return best
    }

    private fun phraseScore(q: List<String>, n: List<String>): Double {
        val whole = wordScore(q.joinToString(""), n.joinToString(""))
        var sum = 0.0
        for (qt in q) {
            var b = 0.0
            for (nt in n) {
                val s = wordScore(qt, nt)
                if (s > b) b = s
            }
            sum += b
        }
        val coverage = sum / q.size
        val extraTokens = n.size > q.size
        val tokenScore = if (extraTokens) coverage * 0.96 else coverage
        return max(whole, tokenScore)
    }

    /**
     * Match several recognizer alternatives (best first) against [items].
     * Each item is scored with the best-scoring alternative.
     */
    fun <T> match(queries: List<String>, items: List<T>, nameOf: (T) -> String): MatchResult<T> {
        val cleanQueries = queries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
        if (cleanQueries.isEmpty() || items.isEmpty()) return MatchResult.NoMatch(0.0)
        val scored = ArrayList<Scored<T>>(items.size)
        for (item in items) {
            val name = nameOf(item)
            var s = 0.0
            for ((idx, q) in cleanQueries.withIndex()) {
                // Lower-ranked recognizer alternatives get a small penalty.
                val v = score(q, name) - idx * 0.01
                if (v > s) s = v
            }
            scored.add(Scored(item, s))
        }
        scored.sortByDescending { it.score }
        val top = scored.first().score
        if (top < ACCEPT) return MatchResult.NoMatch(top)
        val close = scored.filter { it.score >= ACCEPT && it.score >= top - AMBIGUITY_MARGIN }
        // Collapse duplicates that have exactly the same display name.
        val distinct = close.distinctBy { TextNorm.normalize(nameOf(it.item)) }.take(MAX_OPTIONS)
        return if (distinct.size == 1) MatchResult.Single(distinct[0].item, distinct[0].score)
        else MatchResult.Ambiguous(distinct)
    }
}

/** Picks one of up to 3 read-out options from the rider's reply ("second one", "doosra", "Verma"). */
object OptionPicker {

    private val ordinals: List<Set<String>> = listOf(
        setOf("first", "1st", "one", "1", "pehla", "pahla", "pehle", "pehli", "pahli", "ek", "number one", "first one"),
        setOf("second", "2nd", "two", "2", "doosra", "dusra", "doosri", "dusri", "do", "number two", "second one", "too", "to"),
        setOf("third", "3rd", "three", "3", "teesra", "tisra", "teesri", "tisri", "teen", "number three", "third one")
    )
    private val lastWords = setOf("last", "aakhri", "akhri", "last one", "aakhiri")

    fun pick(reply: String, options: List<String>): Int? {
        if (options.isEmpty()) return null
        val tokens = TextNorm.tokens(reply)
        if (tokens.isEmpty()) return null
        // Name match first: a spoken name is more specific than a stray ordinal-like word.
        val result = NameMatcher.match(listOf(reply), options.indices.toList()) { options[it] }
        if (result is MatchResult.Single && result.score >= 0.9) return result.item
        for ((idx, set) in ordinals.withIndex()) {
            if (idx >= options.size) break
            if (tokens.any { it in set } || set.any { it.contains(' ') && reply.lowercase().contains(it) }) {
                // "to"/"too"/"do" are only accepted when they are the whole reply (they are common filler words).
                val weak = setOf("to", "too", "do")
                val strongHit = tokens.any { it in set && it !in weak }
                if (strongHit || tokens.size == 1) return idx
            }
        }
        if (tokens.any { it in lastWords }) return options.size - 1
        if (result is MatchResult.Single) return result.item
        return null
    }
}
