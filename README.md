# VoiceScribe — Local Android AI Transcription Application

VoiceScribe is a high-performance, native Android application designed for fully **offline (on-device) speech transcription**. Powered by the state-of-the-art local runtimes, it features Whisper speech-to-text, Silero voice activity detection (VAD), pyannote-based offline speaker diarization, spoken language identification, and robust transcript exporters.

---

## 🚀 Key Features

* **100% Offline Core:** Audio/video media is processed entirely on-device. No cloud APIs, no telemetry, no user profiles — absolute privacy.
* **Whisper ASR Engines:** Fully utilizes optimized ONNX-runtime and CPU ARM NEON instruction sets.
* **Speaker Diarization:** Multi-speaker segmentation, embedding extraction, and agglomerative clustering directly on the device.
* **Language Detection Subsystem:** Supports automated English/Russian auto-detection and force manual selection.
* **Secure Model Manager:** Complete model management registry supporting download, pause/resume, SHA-256 validation, and transaction-based atomic installations.
* **Production Export Formats:** Export transcripts natively to `TXT`, `SRT`, `VTT`, and `JSON` via Android's Storage Access Framework (SAF).

---

## 📂 Documentation

The project follows a strict research-driven engineering workflow. Comprehensive design plans can be explored in the `docs` folder:

1. **[Consolidated Technology Research](docs/RESEARCH.md)** — In-depth analysis of local runtimes, VAD options, diarization models, licenses, and on-device performance capabilities.
2. **[System Architecture Design](docs/ARCHITECTURE.md)** — The blueprint covering multi-module clean architecture, dependency graphs, reactive unidirectional data flow, JNI/native boundaries, threading, low-RAM strategies, and error propagation.
3. **[Project Manifest](docs/PROJECT_MANIFEST.md)** — Canonical tracking manifest covering technology stacks, model tiers, ABI compilation scopes, and test/QA statuses.

---

## 🛠️ Technology Stack & System Requirements

* **Platform SDK:** compileSdk 36, targetSdk 36, minSdk 24 (Android 7.0+)
* **Development Stack:** Jetpack Compose (BOM 2026.06.01), Kotlin 2.4.10, AGP 9.3.1, Room 2.8.4 (with FTS5), Media3 1.11.0, Hilt 2.51.1
* **Inference Library:** `com.k2fsa:sherpa-onnx` (Apache-2.0 Android AAR)
* **Target ABIs:** `arm64-v8a`, `armeabi-v7a`, `x86_64`
* **Execution Boundary:** Foreground Service with type `mediaProcessing` (safeguards execution limits on Android 14+).

---

## 📈 Project Status Machine

According to the engineering contract, VoiceScribe is currently in:

`INIT` ➔ `RESEARCHING` ➔ `RESEARCH_COMPLETE` ➔ 🎯 **`ARCHITECTING`** ➔ `ARCHITECTURE_REVIEW` ➔ `ARCHITECTURE_FROZEN` ➔ `IMPLEMENTING` ➔ `COMPLETE`

*The specification freeze (Phase 2 completion) is currently in review.*
