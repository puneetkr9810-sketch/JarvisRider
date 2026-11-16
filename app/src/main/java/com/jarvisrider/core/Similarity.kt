package com.jarvisrider.core

import kotlin.math.max
import kotlin.math.min

/** String similarity measures (pure Kotlin, allocation-light). */
object Similarity {

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 1.0 = identical, 0.0 = completely different. */
    fun levenshteinSimilarity(a: String, b: String): Double {
        val m = max(a.length, b.length)
        if (m == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / m
    }

    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return if (a.isEmpty()) 0.0 else 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val matchDistance = max(0, max(a.length, b.length) / 2 - 1)
        val aMatches = BooleanArray(a.length)
        val bMatches = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, b.length)
            for (j in start until end) {
                if (bMatches[j] || a[i] != b[j]) continue
                aMatches[i] = true
                bMatches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatches[i]) continue
            while (!bMatches[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
        var prefix = 0
        for (i in 0 until min(4, min(a.length, b.length))) {
            if (a[i] == b[i]) prefix++ else break
        }
        return jaro + prefix * 0.1 * (1 - jaro)
    }
}
