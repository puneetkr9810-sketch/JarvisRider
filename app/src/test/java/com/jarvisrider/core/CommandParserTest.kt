package com.jarvisrider.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private fun call(text: String): Command.Call {
        val c = CommandParser.parse(text)
        assertTrue("'$text' parsed as $c", c is Command.Call)
        return c as Command.Call
    }

    @Test fun englishCall() {
        assertEquals("rahul", call("call Rahul").name)
        assertEquals("rahul sharma", call("Call Rahul Sharma").name)
        assertEquals("rahul", call("please call rahul").name)
        assertEquals("rahul", call("can you call Rahul").name)
        assertEquals("rahul", call("phone Rahul").name)
        assertEquals("rahul", call("dial Rahul").name)
        assertEquals("rahul", call("make a call to Rahul").name)
        assertEquals("mom", call("call my mom").name)
        assertEquals("rahuls", call("call Rahul's").name)
    }

    @Test fun englishSpeaker() {
        val c = call("call mom on speaker")
        assertEquals("mom", c.name)
        assertTrue(c.speaker)
        assertTrue(call("call Rahul on speakerphone").speaker)
        assertTrue(!call("call Rahul").speaker)
    }

    @Test fun hinglishCall() {
        assertEquals("rahul", call("Rahul ko call karo").name)
        assertEquals("rahul", call("rahul ko call kar do").name)
        assertEquals("mummy", call("mummy ko phone lagao").name)
        assertEquals("rahul", call("call karo Rahul ko").name)
        assertEquals("papa", call("papa ko lagao").name)
        assertEquals("sharma ji", call("sharma ji ko phone karo").name)
        assertEquals("bhaiya", call("zara bhaiya ko call karna").name)
    }

    @Test fun hinglishSpeaker() {
        val c = call("speaker pe Rahul ko call karo")
        assertEquals("rahul", c.name)
        assertTrue(c.speaker)
    }

    @Test fun devanagariCall() {
        assertEquals("raahul", call("राहुल को कॉल करो").name)
        assertEquals("mammee", call("मम्मी को फ़ोन लगाओ").name)
        val c = call("स्पीकर पर पापा को कॉल करो")
        assertTrue(c.speaker)
        assertEquals("paapaa", c.name)
    }

    @Test fun redial() {
        assertEquals(Command.Redial(speaker = false, lastAny = false), CommandParser.parse("redial"))
        assertEquals(Command.Redial(speaker = false, lastAny = false), CommandParser.parse("call again"))
        assertEquals(Command.Redial(speaker = false, lastAny = false), CommandParser.parse("call the last number"))
        assertEquals(Command.Redial(speaker = false, lastAny = false), CommandParser.parse("dobara call karo"))
        assertEquals(Command.Redial(speaker = false, lastAny = false), CommandParser.parse("phir se call karo"))
        assertEquals(Command.Redial(speaker = true, lastAny = false), CommandParser.parse("redial on speaker"))
        assertEquals(Command.Redial(speaker = false, lastAny = true), CommandParser.parse("call back"))
        assertEquals(Command.Redial(speaker = false, lastAny = true), CommandParser.parse("wapas call karo"))
    }

    @Test fun redialWithNameIsCall() {
        assertEquals("rahul", call("call Rahul again").name)
        assertEquals("rahul", call("Rahul ko phir se call karo").name)
    }

    @Test fun numbers() {
        assertEquals(Command.CallNumber("9876543210", false), CommandParser.parse("call 98765 43210"))
        assertEquals(Command.CallNumber("9876543210", true), CommandParser.parse("9876543210 pe call karo speaker pe"))
    }

    @Test fun cancel() {
        assertEquals(Command.Cancel, CommandParser.parse("cancel"))
        assertEquals(Command.Cancel, CommandParser.parse("never mind"))
        assertEquals(Command.Cancel, CommandParser.parse("rehne do"))
        assertEquals(Command.Cancel, CommandParser.parse("kuch nahi"))
        assertEquals(Command.Cancel, CommandParser.parse("stop"))
    }

    @Test fun askWho() {
        assertEquals(Command.AskWho(false), CommandParser.parse("call"))
        assertEquals(Command.AskWho(true), CommandParser.parse("call on speaker"))
        assertEquals(Command.AskWho(false), CommandParser.parse("call karo"))
    }

    @Test fun unknownIsJustAName() {
        assertTrue(CommandParser.parse("Rahul") is Command.Unknown)
        assertEquals("rahul", CommandParser.cleanName("Rahul please"))
        assertEquals("rahul", CommandParser.cleanName("Rahul ko"))
        assertTrue(CommandParser.parse("") is Command.Unknown)
    }
}
