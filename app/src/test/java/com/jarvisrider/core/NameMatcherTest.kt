package com.jarvisrider.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    private val contacts = listOf(
        "Rahul Sharma", "Rohit Verma", "Rakesh Kumar", "Mummy", "Papa", "Priti Singh",
        "Vijai Kumar", "Shubham Gupta", "Khushbu", "Amit Kumar", "Amit Singh", "Farhan Ali",
        "Zubair Khan", "Neha Office", "Bhaiya", "Sandeep"
    )

    private fun single(query: String): String {
        val r = NameMatcher.match(listOf(query), contacts) { it }
        assertTrue("expected single match for '$query' but got $r", r is MatchResult.Single)
        return (r as MatchResult.Single).item
    }

    @Test fun exactFirstName() = assertEquals("Rahul Sharma", single("rahul"))
    @Test fun fullName() = assertEquals("Rahul Sharma", single("Rahul Sharma"))
    @Test fun spellingVariantRahool() = assertEquals("Rahul Sharma", single("rahool"))
    @Test fun devanagariName() = assertEquals("Rahul Sharma", single("राहुल"))
    @Test fun devanagariFullName() = assertEquals("Priti Singh", single("प्रीति"))
    @Test fun hinglishPreeti() = assertEquals("Priti Singh", single("preeti"))
    @Test fun hinglishVijay() = assertEquals("Vijai Kumar", single("vijay"))
    @Test fun hinglishSubham() = assertEquals("Shubham Gupta", single("subham"))
    @Test fun hinglishKushboo() = assertEquals("Khushbu", single("kushboo"))
    @Test fun hinglishPharhan() = assertEquals("Farhan Ali", single("pharhan"))
    @Test fun hinglishJubair() = assertEquals("Zubair Khan", single("jubair"))
    @Test fun rohitNotRahul() = assertEquals("Rohit Verma", single("rohit"))
    @Test fun sandipVariant() = assertEquals("Sandeep", single("sandip"))
    @Test fun aliasMom() = assertEquals("Mummy", single("mom"))
    @Test fun aliasMaaHindi() = assertEquals("Mummy", single("मम्मी"))
    @Test fun aliasDad() = assertEquals("Papa", single("dad"))
    @Test fun aliasBrother() = assertEquals("Bhaiya", single("brother"))

    @Test fun ambiguousAmit() {
        val r = NameMatcher.match(listOf("amit"), contacts) { it }
        assertTrue(r is MatchResult.Ambiguous)
        val names = (r as MatchResult.Ambiguous).options.map { it.item }.toSet()
        assertEquals(setOf("Amit Kumar", "Amit Singh"), names)
    }

    @Test fun ambiguousCapsAtThree() {
        val list = listOf("Amit A", "Amit B", "Amit C", "Amit D", "Amit E")
        val r = NameMatcher.match(listOf("amit"), list) { it }
        assertTrue(r is MatchResult.Ambiguous)
        assertEquals(3, (r as MatchResult.Ambiguous).options.size)
    }

    @Test fun noMatchForGarbage() {
        val r = NameMatcher.match(listOf("xyzqw"), contacts) { it }
        assertTrue(r is MatchResult.NoMatch)
    }

    @Test fun differentNameBelowThreshold() {
        assertTrue(NameMatcher.score("ravi", "Rahul Sharma") < NameMatcher.ACCEPT)
        assertTrue(NameMatcher.score("sunil", "Sandeep") < NameMatcher.ACCEPT)
    }

    @Test fun usesSecondAlternative() {
        val r = NameMatcher.match(listOf("xyzqw", "farhan"), contacts) { it }
        assertTrue(r is MatchResult.Single)
        assertEquals("Farhan Ali", (r as MatchResult.Single).item)
    }

    @Test fun emptyInputs() {
        assertTrue(NameMatcher.match(emptyList(), contacts) { it } is MatchResult.NoMatch)
        assertTrue(NameMatcher.match(listOf("rahul"), emptyList<String>()) { it } is MatchResult.NoMatch)
    }

    @Test fun optionPickerOrdinals() {
        val opts = listOf("Amit Kumar", "Amit Singh", "Amit Verma")
        assertEquals(0, OptionPicker.pick("first one", opts))
        assertEquals(1, OptionPicker.pick("doosra", opts))
        assertEquals(2, OptionPicker.pick("third", opts))
        assertEquals(1, OptionPicker.pick("Singh", opts))
        assertEquals(2, OptionPicker.pick("amit verma", opts))
        assertEquals(2, OptionPicker.pick("last", opts))
        assertNull(OptionPicker.pick("banana", opts))
    }

    @Test fun similarityBasics() {
        assertEquals(3, Similarity.levenshtein("kitten", "sitting"))
        assertEquals(1.0, Similarity.jaroWinkler("rahul", "rahul"), 1e-9)
        assertTrue(Similarity.jaroWinkler("martha", "marhta") > 0.95)
        assertEquals(0.0, Similarity.jaroWinkler("", "abc"), 1e-9)
    }
}
