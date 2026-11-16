package com.jarvisrider.core

/** Spoken prompts. Hindi prompts are in Devanagari so a hi-IN TTS voice pronounces them well. */
class Prompts(val hindi: Boolean) {
    val whoToCall get() = if (hindi) "किसे कॉल करना है?" else "Who do you want to call?"
    val didntCatch get() = if (hindi) "माफ़ कीजिए, समझ नहीं आया।" else "Sorry, I didn't catch that."
    val tryAgainWho get() = if (hindi) "फिर से नाम बोलिए।" else "Please say the name again."
    val goodbye get() = if (hindi) "ठीक है, बाद में बात करते हैं।" else "Okay, goodbye."
    val cancelled get() = if (hindi) "ठीक है।" else "Okay, cancelled."
    val micMissing get() = if (hindi) "माइक्रोफ़ोन की अनुमति नहीं है। कृपया जार्विस राइडर ऐप खोलकर अनुमति दें।"
        else "Microphone permission is missing. Please open Jarvis Rider and grant it."
    val contactsMissing get() = if (hindi) "कॉन्टैक्ट्स की अनुमति नहीं है। कृपया ऐप खोलकर अनुमति दें।"
        else "Contacts permission is missing. Please open Jarvis Rider and grant it."
    val callMissing get() = if (hindi) "कॉल करने की अनुमति नहीं है। कृपया ऐप खोलकर अनुमति दें।"
        else "Phone call permission is missing. Please open Jarvis Rider and grant it."
    val recognizerUnavailable get() = if (hindi) "आवाज़ पहचान उपलब्ध नहीं है। ऑफ़लाइन भाषा पैक डाउनलोड करें या इंटरनेट चालू करें।"
        else "Speech recognition is not available. Download the offline language pack or turn on internet."
    val noRedial get() = if (hindi) "पिछला नंबर नहीं मिला।" else "I couldn't find a number to redial."
    val callFailed get() = if (hindi) "कॉल नहीं हो पाई।" else "Sorry, the call could not be placed."
    val sayAnswerOrReject get() = if (hindi) "उठाऊँ या काटूँ?" else "Say answer or reject."
    val unknownCaller get() = if (hindi) "अज्ञात नंबर" else "an unknown number"
    val rejected get() = if (hindi) "कॉल काट दी।" else "Call rejected."
    val rejectUnsupported get() = if (hindi) "इस फ़ोन पर कॉल काटना संभव नहीं।" else "Rejecting calls is not supported on this Android version."
    val ttsTest get() = if (hindi) "नमस्ते! मैं जार्विस राइडर हूँ। सुरक्षित चलाइए।"
        else "Hello! I am Jarvis Rider. Ride safe."

    fun calling(name: String, speaker: Boolean): String = if (hindi) {
        "$name को कॉल कर रहा हूँ" + if (speaker) ", स्पीकर पर।" else "।"
    } else {
        "Calling $name" + if (speaker) " on speaker." else "."
    }

    fun notFound(name: String): String = if (hindi) "$name कॉन्टैक्ट्स में नहीं मिला।" else "I couldn't find $name in your contacts."

    fun whichOne(names: List<String>): String {
        val list = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + (if (hindi) " या " else ", or ") + names.last()
        }
        return if (hindi) "$list। कौन सा?" else "I found $list. Which one?"
    }

    fun callFrom(who: String): String = if (hindi) "$who का कॉल है।" else "Call from $who."
    fun callFromAsk(who: String): String = callFrom(who) + " " + sayAnswerOrReject
}
