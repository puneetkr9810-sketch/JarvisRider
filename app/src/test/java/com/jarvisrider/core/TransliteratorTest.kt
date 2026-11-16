package com.jarvisrider.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TransliteratorTest {
    @Test fun names() {
        assertEquals("raahul", Transliterator.toLatin("राहुल"))
        assertEquals("preeti", Transliterator.toLatin("प्रीति"))
        assertEquals("raam", Transliterator.toLatin("राम"))
    }

    @Test fun commandWords() {
        assertEquals("ko kol karo", Transliterator.toLatin("को कॉल करो"))
        assertEquals("fon", Transliterator.toLatin("फ़ोन"))
        assertEquals("karnaa", Transliterator.toLatin("करना"))
        assertEquals("kaat do", Transliterator.toLatin("काट दो"))
        assertEquals("haan", Transliterator.toLatin("हाँ"))
    }

    @Test fun latinUntouched() = assertEquals("Rahul", Transliterator.toLatin("Rahul"))

    @Test fun phoneticKeys() {
        assertEquals(TextNorm.phoneticKey("Preeti"), TextNorm.phoneticKey("Priti"))
        assertEquals(TextNorm.phoneticKey("Shubham"), TextNorm.phoneticKey("Subham"))
        assertEquals(TextNorm.phoneticKey("Vijay"), TextNorm.phoneticKey("Vijai"))
        assertEquals(TextNorm.phoneticKey("Raahul"), TextNorm.phoneticKey("Rahul"))
    }

    @Test fun spokenDigits() {
        assertEquals("9 8 7 6 5, 4 3 2 1 0", TextNorm.spokenDigits("98765 43210"))
        assertEquals("plus 9 1 9 8 7, 6 5", TextNorm.spokenDigits("+91 98765"))
    }
}
