# SmartSight

**Empowering the Visually Impaired Through AI-Powered Android Accessibility**

SmartSight is an AI-powered Android accessibility app that gives visually impaired users a complete, voice-driven window to the world around them — combining real-time object detection, text recognition, and hands-free voice control in a single, fully offline app.

> Final Year Project — Computer Science, Lebanese University – Faculty of Science II, 2025/2026
> Wissam Chidiac, Celine Chehade, Marielynn Ibrahim

---

## The Problem

- **2.2B+** people worldwide live with some form of vision impairment (1 in 27 people globally).
- They can't describe their environment in real time, and printed signs, labels, and documents remain inaccessible.
- Existing assistive tools are expensive, not context-aware, limited to single tasks, and poorly integrated with smartphones.
- No single app unifies object detection, text reading, and offline voice control.

**Why should vision loss mean losing independence?**

## The Solution

SmartSight brings four capabilities together in one app:

- **Object Detection** — real-time detection, announced via TTS
- **Text Recognition (OCR)** — reads any printed text aloud instantly
- **Voice Interaction** — hands-free STT/TTS control of all key features
- **TalkBack Native** — deep Android accessibility integration, screen-reader compatible

## Core Features

**Real-Time Object Detection**
- TensorFlow Lite, detects 80+ object classes
- Announces detected objects via TTS
- Runs entirely on-device — no internet required

**Optical Character Recognition**
- Google ML Kit OCR engine
- Camera-to-speech in under 3 seconds
- Works on signs, menus, and documents; supports English & French

**Voice-Driven Interface**
- STT accepts voice commands for all app actions
- Rename, delete, and set reminders — all hands-free
- TTS feedback for every action

**TalkBack & Accessibility**
- Native Android TalkBack integration
- Large buttons and text, gesture-based navigation
- TalkBack status auto-detected on launch

## How It Works

1. **App Launch** — first launch asks for a username via STT and stores the profile; returning users are greeted by name via TTS.
2. **TalkBack Check** — the system checks whether TalkBack is enabled and guides the user to activate it if not.
3. **Smart Scan** — camera permission is checked, an image is captured, and TensorFlow Lite + ML Kit process it.
4. **Results via TTS** — the object is announced or the text is read aloud; existing saved-item matches are flagged.
5. **Save & Return** — the user can save the item under a custom name and optionally add a reminder via STT, then return home.

Fully offline — no internet connection or account required at any step.

## System Architecture

```
USER INPUT LAYER        Voice Commands (STT) · Camera Feed · Touch Gestures
PROCESSING LAYER        TensorFlow Lite Object Detection · ML Kit OCR · TtsHelper Engine
ANDROID FRAMEWORK       Activity Manager · TalkBack API · CameraX
OUTPUT LAYER            TTS Speech Output · Accessible UI (Large Buttons) · Room DB (Saved Items)
```

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java (Android) |
| AI / Vision | TensorFlow Lite (real-time object detection model) |
| OCR | Google ML Kit (on-device text recognition) |
| Speech | Android STT / TTS |
| Camera | CameraX API (lifecycle-aware capture) |
| Accessibility | TalkBack API |
| Storage | Room Database (persistent local storage for saved items & reminders) |
| Architecture | MVC pattern with centralized helper classes (e.g. `TtsHelper`) |
| Min SDK | API 21+ (Android 5.0 — required by CameraX, TFLite & ML Kit) |

## Testing & Results

- **80+** object classes detectable (TFLite model)
- **< 3s** max scan processing time (per NFR)
- **4** app activities unit tested (100% coverage)
- **100%** TalkBack compatibility

**Unit testing:** all helper classes tested individually, including STT lifecycle state coverage and TTS language injection.

**Integration testing:** activity ↔ helper communication, the full camera → TFLite/OCR → TTS pipeline, and end-to-end TalkBack event handling.

## Challenges & Solutions

**TTS/STT feedback loop bug** — STT and TTS being enabled simultaneously caused an unintended feedback loop. Solved by rebuilding the entire TTS layer into a centralized `TtsHelper.java` with a lifecycle-aware state machine.

**Touch event conflicts** — TalkBack's gesture system blocked custom touch handlers. Solved by implementing `View.AccessibilityDelegate` to intercept and re-route accessibility events.

**Real-time performance / detection consistency** — object detection wasn't consistent when scanning from different angles. Solved by implementing `ImageFingerprintExtractor.java`, which creates a fingerprint for each object.

## Social Impact

- Enables independent navigation for 2.2B+ visually impaired users
- Free and open-source — no cost barrier to access
- Works fully offline — usable in low-connectivity regions (Lebanon, MENA)
- Reduces dependence on human assistance for daily tasks
- Promotes digital inclusion and equal access to technology
- Supports English & French for multilingual users

## Roadmap

**v2.0**
- GPS + indoor navigation integration
- Scene description via Vision-Language Models

**v3.0**
- Multi-language support (Arabic, French, English)
- Wearable device (smart glasses) integration

**Beyond**
- Cloud AI for complex scene understanding
- Color detection, distance estimation, and proximity beep for saved items

## Team

- Wissam Chidiac
- Celine Chehade
- Marielynn Ibrahim

Final Year Project, Computer Science, Lebanese University – Faculty of Science II, 2025/2026
