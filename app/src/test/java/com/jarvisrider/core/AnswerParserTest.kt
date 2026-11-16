package com.jarvisrider.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerParserTest {

    private fun intent(text: String) = AnswerParser.parse(text).intent

    @Test fun answerEnglish() {
        for (t in listOf("answer", "Answer it", "pick up", "pickup", "accept", "yes", "okay answer the call", "receive", "attend"))
            assertEquals(t, AnswerIntent.ANSWER, intent(t))
    }

    @Test fun answerHinglish() {
        for (t in listOf("haan", "haa", "han ji", "utha lo", "uthao", "haan utha lo", "theek hai", "le lo"))
            assertEquals(t, AnswerIntent.ANSWER, intent(t))
    }

    @Test fun answerDevanagari() {
        for (t in listOf("हाँ", "उठा लो", "हाँ उठाओ"))
            assertEquals(t, AnswerIntent.ANSWER, intent(t))
    }

    @Test fun rejectEnglish() {
        for (t in listOf("reject", "decline", "cut", "cut it", "no", "hang up", "ignore", "busy", "call later"))
            assertEquals(t, AnswerIntent.REJECT, intent(t))
    }

    @Test fun rejectHinglish() {
        for (t in listOf("kaat do", "kat do", "nahi", "nahin", "mat uthao", "baad mein", "band karo"))
            assertEquals(t, AnswerIntent.REJECT, intent(t))
    }

    @Test fun rejectDevanagari() {
        for (t in listOf("काट दो", "नहीं", "मत उठाओ"))
            assertEquals(t, AnswerIntent.REJECT, intent(t))
    }

    @Test fun negationWins() {
        assertEquals(AnswerIntent.REJECT, intent("don't answer"))
        assertEquals(AnswerIntent.REJECT, intent("do not pick up"))
        assertEquals(AnswerIntent.REJECT, intent("nahi uthana"))
    }

    @Test fun unknown() {
        assertEquals(AnswerIntent.UNKNOWN, intent("what is the weather"))
        assertEquals(AnswerIntent.UNKNOWN, intent(""))
    }

    @Test fun speakerFlag() {
        val d = AnswerParser.parse("answer on speaker")
        assertEquals(AnswerIntent.ANSWER, d.intent)
        assertTrue(d.speaker)
    }

    @Test fun alternatives() {
        assertEquals(AnswerIntent.ANSWER, AnswerParser.parseAlternatives(listOf("blah", "pick up")).intent)
        assertEquals(AnswerIntent.UNKNOWN, AnswerParser.parseAlternatives(emptyList()).intent)
    }
}
