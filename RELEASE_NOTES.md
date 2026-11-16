## JarvisRider v1.0.0

First release: a hands-free voice assistant for motorbike riders with Bluetooth helmet headsets.

**Install:** download `JarvisRider.apk` below, allow "Install unknown apps" for your browser/file manager, open it. Then follow the in-app setup: grant permissions, set JarvisRider as the default digital assistant, and turn off battery optimization.

- Outgoing calls by voice in English (India), Hindi and Hinglish: "call Rahul", "Rahul ko call karo", "call mom on speaker", "redial", "call back", "call 98765 43210".
- Fuzzy and phonetic contact matching (Rahool → Rahul, Preeti ↔ Priti, Devanagari names), with a spoken "which one?" step when several contacts match.
- Incoming calls: announces the caller and listens for "answer / pick up / haan / utha lo" or "reject / cut / kaat do / nahi". It falls back to announcing only when Android blocks background microphone use.
- Nothing runs while idle. The release APK is about 130 KB, with no ads, no analytics, no API keys and no internet needed by the app itself.

Known limitations, plus what has and has not been tested on real phones: see the README.
