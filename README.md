# JarvisRider

A **lightweight, free, open-source, hands-free voice assistant for motorbike riders** using a Bluetooth helmet headset.
JarvisRider replaces Gemini/Google Assistant as the phone's *digital assistant* so you can make, answer and reject calls entirely by voice, in **English (India), Hindi and Hinglish**.

- No ads, no analytics, no accounts, no API keys, no paid services.
- Nothing runs while idle: no background service and no wake-word listening.
- Release APK is about **130 KB** (Kotlin, framework APIs only, R8 + resource shrinking).
- minSdk 26 (Android 8.0), targetSdk 34 (Android 14).

**Download:** [JarvisRider.apk (latest release)](https://github.com/puneetkr9810-sketch/JarvisRider/releases/download/v1.0.0/JarvisRider.apk)

---

## Features

### 1. Default digital assistant
- Implements `VoiceInteractionService`, `VoiceInteractionSessionService`, `VoiceInteractionSession` and the required `RecognitionService`. The recognition service is a transparent proxy to the real speech engine, so voice typing in other apps keeps working.
- `ACTION_ASSIST`, `ACTION_VOICE_COMMAND` (Bluetooth headset voice-dial button) and `VOICE_SEARCH_HANDS_FREE` all open a transparent overlay that works on the lock screen.
- Ways to start it: power-button long-press (where the phone supports assistant on power button), home long-press or corner swipe, or the headset button.
- First-run screen with a status checklist and one button per setup step: permissions, default assistant (`ACTION_VOICE_INPUT_SETTINGS`, with fallbacks to *Default apps* and *Settings*), and battery optimization.

### 2. Outgoing calls by voice
Flow: beep → "Who do you want to call?" → listen → match contact → "Calling Rahul Sharma" → call is placed.

| You say | Result |
|---|---|
| `Rahul` (after the prompt) | calls Rahul |
| `call Rahul`, `phone Rahul Sharma`, `please call Rahul` | calls Rahul |
| `Rahul ko call karo`, `mummy ko phone lagao`, `राहुल को कॉल करो` | calls Rahul / Mummy |
| `call mom on speaker`, `speaker pe papa ko call karo` | calls on speaker (`EXTRA_START_CALL_WITH_SPEAKERPHONE`) |
| `redial`, `call again`, `dobara call karo`, `phir se call karo` | last outgoing number |
| `call back`, `wapas call karo` | last number in the call log |
| `call 98765 43210` | dials the number |
| `cancel`, `rehne do`, `kuch nahi` | stops |

- Contacts are read on demand with `ContactsContract`, one entry per contact, preferring the default or mobile number. Nothing is cached.
- Matching combines normalized Jaro-Winkler, Levenshtein on phonetic keys, a Hinglish phonetic key (Preeti=Priti, Shubham=Subham, Vijay=Vijai, Khushbu=Kushboo, Farhan=Pharhan, Zubair=Jubair), Devanagari→Latin transliteration (राहुल → Rahul), and relationship aliases (mom → Mummy/Maa/Ammi, dad → Papa, brother → Bhaiya …).
- The accept threshold is 0.85. If several contacts are within 0.05 of the best match, it reads out up to three ("I found Amit Kumar, Amit Singh, or Amit Verma. Which one?"). Answer with "first", "second", "doosra", "last" or the name.
- If nothing matches well: "Sorry, I didn't catch that." It retries up to two more times, then says goodbye. In **Auto** language mode the retry listens in hi-IN.
- Calls are placed with `TelecomManager.placeCall`, falling back to `ACTION_CALL`.

### 3. Incoming calls by voice
- A manifest-registered `PHONE_STATE` receiver (protected by `READ_PHONE_STATE`) wakes the app only when the phone rings. **No persistent service.**
- It starts a **short-lived foreground service** that lives only while the call rings. The service looks up the caller (`PhoneLookup`), mutes the ringtone, and says *"Call from Rahul. Say answer or reject."* It then listens for about 6.5 seconds, twice at most.
- Answer words: *answer, pick up, accept, receive, yes, ok, haan, han ji, utha lo, uthao, theek hai, हाँ, उठा लो*.
- Reject words: *reject, decline, cut, hang up, busy, later, no, nahi, kaat do, mat uthao, baad mein, काट दो, नहीं*. Any negation ("don't answer") counts as reject.
- Answer uses `TelecomManager.acceptRingingCall()`. Reject uses `TelecomManager.endCall()` (Android 9+). Both need `ANSWER_PHONE_CALLS`.
- The flow stops immediately if the call is answered, rejected or ended any other way. The ringtone is always restored, even if the process dies, because a persisted flag is cleared on the next phone-state change.
- Audio: transient audio focus pauses or ducks music while JarvisRider talks and listens. If a Bluetooth headset is connected, audio is routed over **Bluetooth SCO** (`setCommunicationDevice` on Android 12+, `startBluetoothSco` below that). This means prompts play in the helmet and the helmet microphone is used.

### 4. Wake word: not included (deliberately)
The spec allowed leaving it out if it couldn't be made reliable with free tools. It is **not shipped**, for these reasons:
- A wake word needs a microphone foreground service running all the time. That conflicts with the "almost no battery while idle" goal.
- Vosk would need native libraries (~7–10 MB per CPU type) plus a ~40 MB model. openWakeWord would need an ONNX/TFLite runtime. Either one makes the app 100–300 times bigger.
- Neither could be tested for false triggers or missed wake-ups on a real phone in wind or engine noise. Shipping it untested would break the "don't ship a broken feature" rule.

Use the power button, home long-press, or the headset button instead. It is possible as a separate opt-in build flavor later (see *Future changes*).

### 5. Efficiency
- Idle: nothing runs. While JarvisRider is the default assistant, Android keeps its empty `VoiceInteractionService` bound. That service holds no resources or timers. Every assistant (Gemini included) works the same way.
- `SpeechRecognizer` and `TextToSpeech` are created per interaction and released immediately in `finally` blocks.
- No caches. Contacts are loaded only while matching. Kotlin coroutines are the only library.

### 6. Reliability and UX
- Everything is spoken, so you never need to look at the screen. Every listen has a timeout and every flow ends gracefully.
- **Test screen:** TTS test, voice recognition with parsed command, contact-match scores, a sample incoming-call announcement, and the full assistant.
- **Help screen:** battery optimization (opens the one-tap dialog), autostart settings for Xiaomi, Oppo, Realme, OnePlus, Vivo, Samsung and Huawei, power-button setup, a command list, and troubleshooting.
- Missing permissions are announced by voice ("Microphone permission is missing…") and shown on screen. Nothing crashes on denial.

---

## Install guide

1. **Download** `JarvisRider.apk` on the phone from the link above (GitHub → Releases).
2. **Allow unknown sources:** when Android asks, tap *Settings* → enable *Allow from this source* for your browser or Files app, then go back.
3. **Install:** tap *Install*. If Play Protect warns about an unknown developer, tap *More details → Install anyway*. The app is open source; you can build it yourself from this repo.
4. **Open JarvisRider** → tap **1. Grant permissions** → *Continue* → allow every permission.
   - On Android 13+, the *Call log* permission may be greyed out for sideloaded apps ("Restricted setting"). Fix it: Settings → Apps → JarvisRider → ⋮ (top-right) → **Allow restricted settings**, then grant it again.
5. Tap **2. Set as default digital assistant**. Choose *Digital assistant app* (or *Default assist app*) and pick **JarvisRider**.
6. Tap **3. Disable battery optimization** → *Allow*. On Xiaomi, Oppo, Realme, Vivo or Samsung, also follow **Help → Open autostart / background settings**.
7. **Power button (optional):** Settings → System → Gestures → *Press and hold power button* → *Digital assistant* (Pixel and many Android 12+ phones). Samsung's side key cannot open third-party assistants, so use home long-press or the headset button there.
8. **Test:** open *Test screen* → *Test TTS*, then *Test voice*. Then long-press home or power and say "call <name>".
9. Recommended: download offline speech packs so recognition works without mobile data. Google app → Settings → Voice → *Offline speech recognition* → English (India) and Hindi.

---

## What is tested vs. not tested

| Area | Status |
|---|---|
| Project builds: `./gradlew clean lintDebug testDebugUnitTest assembleDebug assembleRelease` | ✅ green locally and in GitHub Actions |
| Android Lint | ✅ "No issues found" (0 errors, 0 warnings) |
| Unit tests: fuzzy/phonetic name matching incl. Hinglish and Devanagari, command parsing (English, Hinglish, Devanagari), answer/reject parsing, transliteration | ✅ 51 tests pass |
| Robolectric smoke tests (Android 14 framework on JVM): all screens start without permissions, assistant intents and services resolve from the manifest, receiver ignores spoofed or idle broadcasts, receiver starts the service on a real ring, service stops itself and cleans up | ✅ 11 tests pass |
| Install and run on a **real phone or emulator** | ❌ **Not done.** No emulator or device was available in the build environment (no KVM). |
| Appearing in *Digital assistant app* list, power-button / headset launch | ⚠️ Implemented per the platform contract, not verified on a device |
| Outgoing call end-to-end with speech, SCO helmet audio | ⚠️ Not verified on a device |
| Incoming-call voice answer/reject | ⚠️ Not verified on a device. See limitations below. |

## Known limitations

- **Incoming-call voice listening on Android 11–14:** Android blocks background microphone use unless the app is exempt.
  - JarvisRider tries a `microphone` foreground service. That is allowed when JarvisRider is the **default assistant** (Android exempts the VoiceInteractionService app) and starting it from the background needs **battery optimization disabled** on Android 12+.
  - If Android refuses, JarvisRider falls back to a `phoneCall` foreground service and **only announces the caller**. If no foreground service is allowed at all, a TTS-only announcement runs from the receiver. You then answer with the headset button.
  - Android 11–13 may also silently give the recognizer silence; it then times out and leaves the call ringing.
  - Never crashes.
- **Rejecting calls** needs Android 9+. On Android 8.x you are told it is not supported.
- **"Answer on speaker"** is only attempted below Android 12. Third-party apps cannot reliably change call routing on 12+.
- **Speech recognition** uses the phone's engine, usually Google. Without internet it needs the offline language packs. Very noisy wind or engine audio reduces accuracy; a headset with a boom mic helps.
- **Bluetooth SCO** behaviour varies between headsets. If prompts don't reach the helmet, toggle *Use the Bluetooth headset microphone* in Settings.
- **Caller number** needs the *Call log* permission (Android 9+). Without it, the caller is announced as "unknown number".
- **Power button:** the setting exists only on some phones (see install guide).
- **Wake word:** not included (see above).

---

## Permissions and why

| Permission | Why |
|---|---|
| RECORD_AUDIO | hear commands |
| READ_CONTACTS | match names, announce callers |
| CALL_PHONE | place calls |
| ANSWER_PHONE_CALLS | answer and reject ringing calls |
| READ_PHONE_STATE | know when the phone rings |
| READ_CALL_LOG | caller number on Android 9+, redial and call back |
| MODIFY_AUDIO_SETTINGS | Bluetooth SCO routing, ringtone mute |
| BLUETOOTH_CONNECT (BLUETOOTH ≤ 30) | detect the helmet headset |
| POST_NOTIFICATIONS | notification for the short incoming-call service |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, FOREGROUND_SERVICE_PHONE_CALL, MANAGE_OWN_CALLS | the short incoming-call service (Android 14 requires these for its two types). Not used for any always-on listening. |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | one-tap "disable battery optimization" dialog |

**Exported components:** the assistant services require the system-only `BIND_VOICE_INTERACTION` permission, and the receiver requires `READ_PHONE_STATE` and re-checks the real call state. `AssistActivity` is exported by design so the system can launch it, and `SettingsActivity` is exported so the system Settings gear icon can open it. `ProxyRecognitionService` follows the standard recognition-service contract: the framework enforces `RECORD_AUDIO` on callers.

---

## Build it yourself

Requirements: JDK 17 and the Android SDK (platform 34).

```bash
git clone https://github.com/puneetkr9810-sketch/JarvisRider.git
cd JarvisRider
./gradlew clean lintDebug testDebugUnitTest assembleDebug assembleRelease
# APKs: app/build/outputs/apk/{debug,release}/
```

**Release signing:** set `JR_KEYSTORE_FILE`, `JR_KEYSTORE_PASSWORD`, `JR_KEY_ALIAS` and `JR_KEY_PASSWORD`. Without them, the release build is signed with the debug key so it is still installable.

**CI** (`.github/workflows/build.yml`) runs lint, tests and both builds on every push, and uploads the APKs as artifacts. On a `v*` tag it also publishes a GitHub Release with `JarvisRider.apk`. The signing key comes from the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. The keystore is never committed.

## Project layout

```
app/src/main/java/com/jarvisrider/
  core/      pure Kotlin: Transliterator, TextNorm (phonetic keys), Similarity,
             NameMatcher + OptionPicker, CommandParser, AnswerParser, Prompts, Prefs, Perms
  voice/     Speaker (TTS), Listener (SpeechRecognizer), RecognizerPicker,
             AudioSession (focus, SCO, beep), Conversation (outgoing dialogue)
  call/      IncomingCallReceiver, IncomingCallService, IncomingFlow, TtsAnnouncer,
             RingerMuter, CallActions
  contacts/  ContactsRepo
  assist/    JarvisVoiceInteractionService, JarvisSessionService, JarvisSession,
             ProxyRecognitionService
  ui/        MainActivity (first run), AssistActivity (overlay), SettingsActivity,
             TestActivity, HelpActivity, SystemSettings (deep links)
```

## Troubleshooting

- **"Speech recognition is not available":** install or update the Google app, or download the offline language packs (see install step 9).
- **JarvisRider not in the assistant list:** reboot, then check Settings → Apps → Default apps → Digital assistant app. Some OEM skins call it "Assist & voice input".
- **Long-press opens Gemini anyway:** set the default digital assistant again, and turn off "Hey Google" / Gemini overlays.
- **Prompts come out of the phone speaker:** make sure the headset is connected for *calls* (HFP), not only media, and enable *Use the Bluetooth headset microphone*.
- **Incoming calls not announced:** grant Phone and Call log permissions, disable battery optimization, allow autostart (Help screen), and don't force-stop the app.
- **Wrong contact:** use *Test screen → Test contact match* to see the scores. Saving contacts with distinct names helps.

## Future changes
Open an issue in this repository describing the change, or ask the assistant that built this. Ideas: an opt-in wake-word flavor (Vosk), WhatsApp calls, reading SMS aloud, more languages.

## License
MIT. See [LICENSE](LICENSE).
