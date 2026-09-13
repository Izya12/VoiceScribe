# VoiceScribe — Phase 3 Project Blueprint

**Version:** 1.0.0
**Date:** 2026-09-13
**Status:** DRAFT — Phase 3 output, prepared for lead review and PR into `main`
**Author:** engineer (delegated session)
**Inputs (read fully):** `promt.md` (contract, authoritative), `docs/ARCHITECTURE.md` v2.0.0 (ratified Phase 2), `docs/PROJECT_MANIFEST.md`, `docs/RESEARCH.md` (ratified Phase 1), `/home/team/shared/AUDIT.md` (2026-08-10), `/home/team/shared/ENV.md`, `/home/team/shared/ENV_STATUS.md` (toolchain limits).
**Reference API doc status:** `SHERPA_API.md` (researcher's sherpa-onnx API reference) was **not yet available** at writing time. This Blueprint uses the sherpa-onnx class names verified in Phase 1 research (`docs/RESEARCH.md` §1.1, §2.1) and the well-known `com.k2fsa.sherpa.onnx` Kotlin API surface (same shape the demo's `SpeechEngine.kt` imports were written against). **Phase 4 step 0 must fold `SHERPA_API.md` into §3.3 and re-verify every engine class name against it before writing `:engine` code.**

---

## 1. Goals, Authority, and Corrections

### 1.1 What this document provides

1. **Full project tree** — every directory and every source file that must exist after Phase 4, for all five modules (`:core:model`, `:core:domain`, `:engine`, `:data`, `:app`), plus build/configuration files. Each file is marked **NEW**, **REUSE+REFACTOR** (from the demo per AUDIT.md), or **DELETE**, with a one-line purpose and the key classes/APIs it exposes.
2. **Ordered implementation sequence** — Phase 4 execution order, bottom-up by dependency, with milestones, per-file dependencies, and what verifies each step.
3. **Gap analysis vs AUDIT.md** — explicit demo-file → decision → target-file table.
4. **Contract cross-reference** — every `promt.md` section mapped to implementing file(s), plus open Phase-4 decisions with recommended options.
5. **Test plan skeleton** — test files per module and required fixtures.
6. **Phase 4 acceptance gate** — the checklist that defines "Phase 4 done".

Phase 4 may then be executed mechanically, file-by-file, in dependency order, with **no design decisions left open**.

### 1.2 Authority: the contract wins

Where `promt.md` (the contract) and `docs/ARCHITECTURE.md` conflict, **`promt.md` wins**. This is the owner-ratified rule (§1 of this project's working rules) and it is restated explicitly here so Phase 4 never guesses. Conflicts found while authoring this Blueprint:

| # | Conflict | Contract (wins) | ARCHITECTURE.md says | Resolution in this Blueprint |
|---|---|---|---|---|
| C1 | **Job state set (§22)** | 14 canonical states: `CREATED, VALIDATING, PREPARING_AUDIO, LOADING_MODEL, DETECTING_LANGUAGE, RUNNING_VAD, RUNNING_DIARIZATION, RUNNING_TRANSCRIPTION, ASSEMBLING_RESULT, READY_FOR_REVIEW, EXPORTING, COMPLETED, CANCELLED, FAILED` | §10.2 simplified list (`SUBMITTED, DECODING, PREPROCESSING, DIARIZING, TRANSCRIBING, COMPLETED`) | **Correction:** `:core:model/JobState.kt` MUST use the contract's 14-state canonical set, including `READY_FOR_REVIEW` and `EXPORTING`. ARCHITECTURE §10.2 is superseded. See §3.1 and Open Decision D1. |
| C2 | **Word timestamps** | §29 (word timestamps architecturally supported; never fabricate if backend cannot provide) + §30 (integer microseconds canonical) | §10.1 items 5/6 (Word with exact timestamps) | Aligned; canonical representation = `Long` microseconds everywhere (word, segment, diarization timelines). Conversion to seconds happens only at the sherpa-onnx boundary (native API returns seconds) and at serialization (SRT/VTT/TXT/JSON). See §3.1 and contract mapping §6. |
| C3 | **Timestamps § in lead brief** | In `promt.md`, §82 is **SECURITY** (file input, model download, native). Word timestamps are §29–30. | — | This Blueprint maps timestamps to §29/§30 and maps §82 security requirements to its own implementing files. Both are covered; no requirement is dropped. |
| C4 | **Search index** | §76: full-text search on canonical transcript (no index tech mandated) | §13: SQLite **FTS5** | Room's built-in `@Fts4` is the only tokenized full-text option that is safe across `minSdk 24` (Android framework SQLite ships FTS3/FTS4 on all API levels; FTS5 only API 30+). **Deviation note:** implement with Room `@Fts4` (contentEntity-backed) to meet §76 on all supported devices; keep the schema such that an FTS5 upgrade is a Room-migration-only change. See Open Decision D2. |
| C5 | **Job state machine diagram** | §22: optional states may be added where justified; invalid transitions must be rejected | §10.2 forbidden-transitions list | The §22 set + transition table in `JobStateMachine` (∈ `:core:domain`) subsumes ARCHITECTURE §10.2's forbidden transitions (no terminal→active, no skipping, `CANCELLED` → no terminal success). |

### 1.3 Reference API doc folding

When `SHERPA_API.md` is available it MUST be folded into §3.3 of this document (engine module class list) before Phase 4 starts. If a class name in §3.3 differs from SHERPA_API.md, **SHERPA_API.md is the authority for the engine module** only if it cites the installed AAR version's Kotlin API (it is produced from the same `com.k2fsa:sherpa-onnx` sources). Any mismatch is recorded in the engine module's PR review.

---

## 2. Module Topology & Build Restructure

### 2.1 The five modules (per ARCHITECTURE §1, contract §16–19)

```
:app  (Compose UI, MVVM, MediaProcessingService FGS, Hilt DI, navigation)
  └── depends on :data, :engine, :core:domain, :core:model
:data (Room DB + FTS, SAF, model download/install, media decode, repos)
  └── depends on :core:domain, :core:model
:engine (sherpa-onnx wrappers: ASR/VAD/Diarization/LangID; BackendProvider)
  └── depends on :core:domain, :core:model, com.k2fsa:sherpa-onnx (AAR)
:core:domain (use cases, interfaces, state machine, errors)
  └── depends on :core:model
:core:model (pure entities/value objects; zero Android deps)
```
- Dependency rule (§19): arrows point inward to `:core:model`. No module may depend on `:app`; no Android SDK types (except `kotlin.*`/`kotlinx.coroutines` on `:core:*`) leak into `:core:model`/`:core:domain` (contract §40: domain never depends on `android.net.Uri` — see `MediaSource`).
- `:engine` and `:data` are Android library modules (they need `Context`, `android.*`). They are implemented *after* `:core:*` tests pass, and *before* `:app`.
- `:data` and `:engine` do **not** depend on each other: the pipeline (∈ `:core:domain`) depends only on interfaces; concrete wiring happens in `:app` Hilt modules (ARCHITECTURE §1.1 note).

### 2.2 Build files to create/change in Phase 4 (see tree §3.6)

- `settings.gradle.kts` — REWRITE: `include(":core:model", ":core:domain", ":engine", ":data", ":app")`.
- Root `build.gradle.kts` — REWRITE: declare plugin aliases `apply false` for `android.library`, `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt.android`; keep memory-safe Gradle config.
- `gradle/libs.versions.toml` — REFACTOR (catalog aligned 2026-09-09 already): add `sherpa-onnx` (com.k2fsa:sherpa-onnx — exact version pinned at Phase 4 step 0, see D3), `androidx.hilt-navigation-compose`, `hilt-android` + `hilt-compiler` (KSP), `androidx.lifecycle-service` (for Service compat); keep okhttp/moshi (downloader + JSON), room 2.8.4, compose BOM 2026.06.01, kotlinx-coroutines, junit/robolectric/espresso. Remove nothing that is used; **ensure no firebase/appcheck/secrets/google-services entries remain active** (AUDIT privacy §79 / §80).
- Five per-module `build.gradle.kts` (NEW). Key points:
  - `:core:model` / `:core:domain`: `java-library` + `kotlin` (JVM); no Android plugin. Tests: JUnit4 + kotlinx-coroutines-test.
  - `:engine`: `com.android.library`; `implementation("com.k2fsa:sherpa-onnx:<ver>")`; ABI filters `arm64-v8a, armeabi-v7a, x86_64` (PROJECT_MANIFEST §3); packaging blocks to keep the AAR's `.so` for those ABIs only; `@Parcelize`/kotlin-parcelize if needed.
  - `:data`: `com.android.library`; room-runtime/room-ktx + `ksp(room-compiler)`; okhttp; robolectric for JVM media tests.
  - `:app`: `com.android.application`; compose; hilt (+ ksp); `androidx.lifecycle:lifecycle-service`; navigation-compose; manifest with FGS `mediaProcessing` (§2.4).
- `gradle.properties` — KEEP as-is (already memory-safe: `-Xmx2g`, `workers.max=2`, `parallel=false`, `daemon=false`; ENV_STATUS.md).
- Resource files (`res/*`) and `app/src/main/AndroidManifest.xml` — REUSE+REFACTOR (§3.5, §3.6).

### 2.3 Namespace & applicationId decision

Target packages follow the Phase-2-ratified `PROJECT_MANIFEST.md §7` tree: `com.example.core.model`, `com.example.core.domain`, `com.example.engine`, `com.example.data`, `com.example.app`. `applicationId` stays `com.aistudio.voicescribe.qkxvpt` (demo value; changing it would orphan installed data/SAF grants). **Open Decision D4:** a production identity rename (`com.example.*` → owner-approved domain) is deferred to a dedicated post-Phase-4 refactor PR so Phase 4 does not churn every package reference.

### 2.4 AndroidManifest changes required in Phase 4

Add: `FOREGROUND_SERVICE_MEDIA_PROCESSING` permission, `<service android:name=".service.MediaProcessingService" android:foregroundServiceType="mediaProcessing" android:exported="false"/>`, `android:name=".VoiceScribeApplication"` on `<application>`. Remove: `FOREGROUND_SERVICE_DATA_SYNC` (unused once FGS type is mediaProcessing, RESEARCH §3.1). Keep `INTERNET` (model download only), `POST_NOTIFICATIONS`, `RECORD_AUDIO` (future recording), READ media permissions as-is per AUDIT (media via SAF does not strictly need READ_MEDIA_*, but keep for the picker UX).

---

## 3. Full Project Tree

Legend — **NEW**: write from scratch (no demo code); **REUSE+REFACTOR**: copy demo logic into the new location, adapting per AUDIT/architecture (never copy fake logic); **DELETE**: remove from the build (fabricated/stub/fake per AUDIT). Paths are relative to the repo root. `src/main/java/<pkg>/…` is abbreviated as `<pkg>/…`.

### 3.1 `:core:model` — pure Kotlin, zero Android dependencies (20 files)

| # | File | Status | Purpose | Key classes / APIs |
|---|---|---|---|---|
| M1 | `com/example/core/model/JobState.kt` | REUSE+REFACTOR (demo `JobState` had 11 of the 14 states) | **Canonical job state enum — contract §22 set, not ARCHITECTURE §10.2** (Correction C1). UI display names (RU/EN). | `enum class JobState { CREATED, VALIDATING, PREPARING_AUDIO, LOADING_MODEL, DETECTING_LANGUAGE, RUNNING_VAD, RUNNING_DIARIZATION, RUNNING_TRANSCRIPTION, ASSEMBLING_RESULT, READY_FOR_REVIEW, EXPORTING, COMPLETED, CANCELLED, FAILED }` + `val isTerminal: Boolean` |
| M2 | `com/example/core/model/LanguageMode.kt` | NEW | Language selection mode (§24). | `enum class LanguageMode { AUTO, MANUAL }` |
| M3 | `com/example/core/model/DiarizationMode.kt` | NEW | Diarization mode (§25). | `enum class DiarizationMode { DISABLED, AUTOMATIC, KNOWN_SPEAKER_COUNT }` |
| M4 | `com/example/core/model/BackendPreference.kt` | NEW | Frozen accelerator policy: CPU-only validated backend (RESEARCH part 1/2; PROJECT_MANIFEST §4). | `enum class BackendPreference { CPU }` (single value; extension point for validated GPU paths per §3.7) |
| M5 | `com/example/core/model/ModelType.kt` | NEW | Model role used by registry + installer. | `enum class ModelType { WHISPER_ASR, VAD, DIARIZATION_SEGMENTATION, DIARIZATION_EMBEDDING }` |
| M6 | `com/example/core/model/ModelDescriptor.kt` | NEW (demo `VoiceModelConfig`/`ModelDescriptorEntity` collapse) | Model registry entry — contract §33 fields; carries license + checksum + URLs. Registry presence ≠ installed (contract §34). | `data class ModelDescriptor(id, name, version, provider, type: ModelType, format, quantization, sizeBytes, estimatedRamBytes, supportedLanguages, supportedBackends, license, licenseUrl, downloadUrl, checksumSha256, managedFileNames: List<String>)` |
| M7 | `com/example/core/model/ModelInstallState.kt` | NEW | Install lifecycle incl. download states (§35–36, §39). | `enum class ModelInstallState { NOT_INSTALLED, QUEUED, DOWNLOADING, PAUSED, VERIFYING, INSTALLED, FAILED, CANCELLED, CORRUPTED }` + `data class ModelDownloadProgress(bytesDownloaded, totalBytes, state)` |
| M8 | `com/example/core/model/MediaSource.kt` | NEW | Domain-safe media reference — never `android.net.Uri` (contract §40). | `data class MediaSource(uriString, displayName, mimeType, sizeBytes)` + `fun toUri(): android.net.Uri` in `:app` adapter only |
| M9 | `com/example/core/model/MediaMetadata.kt` | REUSE+REFACTOR (demo `MediaMetadata`) | Extracted metadata (§42–43); **all durations in `Long` µs** (§30, Correction C3). | `data class MediaMetadata(fileName, isVideo, mimeType, durationUs: Long, sizeBytes, sampleRate, channels, codec?)` |
| M10 | `com/example/core/model/TranscriptionConfig.kt` | REUSE+REFACTOR (demo `TranscriptionConfig`) | Per-job configuration (§23–25). | `data class TranscriptionConfig(modelId, languageMode: LanguageMode, manualLanguage, diarizationMode: DiarizationMode, expectedSpeakerCount, enableVad, backend: BackendPreference, threadCount, qualityMode)` |
| M11 | `com/example/core/model/TranscriptionJob.kt` | REUSE+REFACTOR (demo entities) | Persistent job aggregate (§21). | `data class TranscriptionJob(id: String, source: MediaSource, metadata: MediaMetadata, config: TranscriptionConfig, language: String?, languageDetectionResult: LanguageDetectionResult?, status: JobState, progress: JobProgress, segments: List<TranscriptionSegment>, speakers: List<Speaker>, statistics: TranscriptionStatistics?, errors: List<JobError>, createdAt/startedAt/completedAt: Long)` |
| M12 | `com/example/core/model/TranscriptionSegment.kt` | REUSE+REFACTOR (demo `TranscriptSegmentEntity`) | Canonical segment (§28, §31). | `data class TranscriptionSegment(id: Long, jobId: String, startUs: Long, endUs: Long, speakerId: Long?, text: String, confidence: Float, words: List<Word>)` |
| M13 | `com/example/core/model/Word.kt` | NEW (needed for §29) | Word-level timestamp (playback highlight, §29/§82-lead-brief → §29). | `data class Word(id: Long, segmentId: Long, word: String, startUs: Long, endUs: Long, confidence: Float)` |
| M14 | `com/example/core/model/Speaker.kt` | REUSE+REFACTOR (demo `SpeakerEntity`) | Stable speaker identity (§26, §77: rename must not change references — only `displayName`/`colorIndex` change). | `data class Speaker(id: Long, jobId: String, displayName: String, colorIndex: Int, confidence: Float)` |
| M15 | `com/example/core/model/SpeechRegion.kt` | EXTRACT-FILE (demo `engine/vad/SpeechRegion`) | VAD output region (streaming, in-memory, lightweight §7.1). | `data class SpeechRegion(startUs: Long, endUs: Long, energyScore: Float)` |
| M16 | `com/example/core/model/SpeakerSegment.kt` | EXTRACT-FILE (demo `engine/diarization/DiarizedSegment`) | Diarization output timeline. | `data class SpeakerSegment(startUs: Long, endUs: Long, speaker: SpeakerRef, confidence: Float)` — `SpeakerRef(orderIndex: Int)` stable per job |
| M17 | `com/example/core/model/TranscriptionStatistics.kt` | NEW | §78 metrics; **never fabricate** — nullable fields. | `data class TranscriptionStatistics(durationUs, processingTimeMs, rtf: Float?, modelId, backend, peakMemoryBytes: Long?, threadCount, detectedLanguage: String?, speakerCount: Int?)` |
| M18 | `com/example/core/model/JobProgress.kt` | NEW | §46 progress: honest, no misleading precision. | `data class JobProgress(stage: JobState, stageFraction: Float?, overallFraction: Float?)` — fractions nullable when not meaningful |
| M19 | `com/example/core/model/ExportFormat.kt` | REUSE (demo `ExportFormat`) | Export formats (§3.6/§70–75). | `enum class ExportFormat(val extension, val mimeType, val label) { TXT, SRT, VTT, JSON }` + `schemaVersion = 1` constant (JSON §75) |
| M20 | `com/example/core/model/LanguageDetectionResult.kt` | NEW (ARCHITECTURE §4 shape; contract §24) | AUTO-detection result, ISO-639-1 + honest confidence. | `data class LanguageDetectionResult(languageCode: String, confidence: Float?)` — confidence nullable when not meaningful (§24/§28 never fabricate) |

### 3.2 `:core:domain` — use cases, interfaces, state machine, errors (20 files)

| # | File | Status | Purpose | Key classes / APIs |
|---|---|---|---|---|
| D1 | `com/example/core/domain/engine/SpeechEngine.kt` | REUSE+REFACTOR (demo `SpeechEngine` interface — rewrite to §52) | ASR abstraction, replaceable backend (§3.2, §19, §52). | `interface SpeechEngine : AutoCloseable { suspend fun load(model: ModelDescriptor, language: String?, numThreads: Int); suspend fun transcribe(pcm: FloatArray, startUs: Long, token: CancellationToken): TranscriptionResult; fun unload() }` + `data class TranscriptionResult(text, words: List<Word>, language: String)` |
| D2 | `com/example/core/domain/engine/VadEngine.kt` | NEW (interface; replaces demo `VoiceActivityDetector` heuristic) | VAD abstraction (§54): streaming regions. | `interface VadEngine : AutoCloseable { fun init(config); fun process(samples: FloatArray, token: CancellationToken): List<SpeechRegion>; fun flush(): List<SpeechRegion>; fun reset() }` |
| D3 | `com/example/core/domain/engine/DiarizationEngine.kt` | NEW (interface; replaces demo `SpeakerDiarizer` heuristics) | Diarization abstraction (§53). | `interface DiarizationEngine : AutoCloseable { suspend fun diarize(pcm: FloatArray, mode: DiarizationMode, expectedSpeakers: Int?, token: CancellationToken, onProgress: (Float)->Unit): List<SpeakerSegment> }` |
| D4 | `com/example/core/domain/engine/LanguageDetector.kt` | NEW (interface) | AUTO language detection (§24, ARCHITECTURE §4). | `interface LanguageDetector : AutoCloseable { suspend fun detectLanguage(pcm30s: FloatArray, model: ModelDescriptor, token: CancellationToken): LanguageDetectionResult }` |
| D5 | `com/example/core/domain/engine/BackendProvider.kt` | NEW | Accelerator/capability abstraction (§3.7, §59–62): evidence-based, CPU chain only. | `data class DeviceCapabilities(ramMb, cpuCores, abi, storageFreeMb)`; `data class BackendProfile(level: CPU_FULL_THREADS / CPU_REDUCED_THREADS / SERIAL, numThreads: Int)`; `interface BackendProvider { suspend fun capabilities(): DeviceCapabilities; fun recommendProfile(): BackendProfile; fun lowRamProfile(): BackendProfile }` |
| D6 | `com/example/core/domain/repository/TranscriptionRepository.kt` | REUSE+REFACTOR (demo `TranscriptionRepository`) | Job/transcript persistence (Room-backed in `:data`). | `interface TranscriptionRepository { fun observeJobs(): Flow<List<TranscriptionJob>>; fun observeJob(id): Flow<TranscriptionJob?>; suspend fun createJob(job): String; suspend fun updateJobState(id, state); suspend fun saveTranscript(jobId, segments, speakers, words, statistics); suspend fun renameSpeaker(jobId, speakerId, newName); suspend fun deleteJob(id) }` |
| D7 | `com/example/core/domain/repository/ModelRepository.kt` | REUSE+REFACTOR (demo `ModelRepository` + registry) | Registry ⊗ installed-state separation (§34, §3.4). | `interface ModelRepository { fun observeAll(): Flow<List<ModelInstallation>>; suspend fun install(id, token); suspend fun pause(id); suspend fun resume(id); suspend fun cancel(id); suspend fun delete(id); suspend fun setActive(id); fun observeActive(): Flow<String?> }` + `data class ModelInstallation(descriptor: ModelDescriptor, state: ModelInstallState, progress: ModelDownloadProgress?, installedVersion: String?)` |
| D8 | `com/example/core/domain/media/MediaDecoder.kt` | NEW (interface; impl in `:data`) | Streaming decode + resample to 16 kHz mono float (contract §44, §51). | `interface MediaDecoder : AutoCloseable { suspend fun open(source: MediaSource): MediaMetadata; fun pcmChunks(): Flow<PcmChunk>; fun close() }` + `data class PcmChunk(samples: FloatArray, sampleRate: Int, startUs: Long)` |
| D9 | `com/example/core/domain/export/TranscriptExporter.kt` | REUSE+REFACTOR (demo `TranscriptExporter` → interface) | Stateless format generators (§70–75, §12). | `interface TranscriptExporter { fun exportToTxt(job, segments, speakers): String; fun exportToSrt(...): String; fun exportToVtt(...): String; fun exportToJson(job, segments, speakers, statistics, schemaVersion = 1): String }` |
| D10 | `com/example/core/domain/downloader/ModelDownloader.kt` | REUSE+REFACTOR (demo `ModelDownloadManager` → interface) | Download w/ progress, pause/resume, integrity (§39, §82). | `interface ModelDownloader { suspend fun download(url, toTempFile, expectedSha256, token, onProgress: (Long, Long)->Unit): File }` — resume via HTTP Range on the impl |
| D11 | `com/example/core/domain/log/TranscriptionLogger.kt` | NEW (interface; impl in `:app`) | Structured, job-correlated logging port (§83–85). | `interface TranscriptionLogger { fun e/jobId, component, message; fun w(...); fun i(...); fun d(...); fun t(...) }` — signature: `log(level, jobId: String?, component, event, message)` |
| D12 | `com/example/core/domain/usecase/RunTranscriptionUseCase.kt` | REUSE+REFACTOR (demo `MainViewModel.runPipeline` skeleton → real orchestration) | **The pipeline (§51):** decode → VAD → diarize → detect language → Whisper → timestamp align → speaker assign → assemble; drives §22 state machine, §47 cancellation, §49 partial results, §96 low-RAM governor. | `class RunTranscriptionUseCase(repo, speech, vad, diar, lang, decoderF, exporter?no, logger, backend)` with `suspend fun run(jobId): JobResult`; emits `Flow<JobProgress>` |
| D13 | `com/example/core/domain/usecase/GetModelsUseCase.kt` | NEW | List registry + installed states for Model Manager screen (§3.4). | `suspend fun available(): List<ModelInstallation>` |
| D14 | `com/example/core/domain/usecase/ManageModelUseCase.kt` | NEW | Install/delete/switch/pause/resume/cancel; enforces §35–38 (active-model delete guard, atomic install, switch keeps previous on failure). | `suspend fun install(id); delete(id); switch(id); pause(id); resume(id); cancel(id)` |
| D15 | `com/example/core/domain/usecase/ExportTranscriptUseCase.kt` | NEW | Export to string + write via SAF callback (app provides writer) (§70–75, §108). | `suspend fun export(jobId, format, writer: (String, String mime) -> Unit)` — validates invariants first (D20) |
| D16 | `com/example/core/domain/usecase/SearchTranscriptUseCase.kt` | NEW | Full-text search on canonical transcript with speaker filter (§76). | `suspend fun search(jobId, query, speakerId?): Flow<List<SearchHit>>` — `data class SearchHit(segmentId, wordIndex?, snippet, startUs)` |
| D17 | `com/example/core/domain/job/JobStateMachine.kt` | NEW (pure logic) | §22 transition table + validation; unit-testable (§101). | `object JobStateMachine { fun canTransition(from, to): Boolean; fun requireTransition(from, to) }` — terminal lock, no skipping, `CANCELLED`→terminal rules |
| D18 | `com/example/core/domain/error/TranscriptionException.kt` | NEW (ARCHITECTURE §9) | Typed, non-leaking errors (§9, §86). | `sealed class TranscriptionException : Exception` with `DecodingException, VadException, DiarizationException, RecognitionException, ModelManagerException, StorageException, SearchException` |
| D19 | `com/example/core/domain/util/TimestampFormatter.kt` | NEW | µs → SRT/VTT/TXT/JSON timestamps at serialization boundary (§30). | `object TimestampFormatter { fun srt(startUs, endUs): String; fun vtt(...); fun clock(startUs): String }` — pure, tested |
| D20 | `com/example/core/domain/util/SegmentValidator.kt` | NEW | Export/pipeline invariants (§30/§102): `startUs >= 0`, `endUs > startUs`, chronological order, speaker refs valid. | `object SegmentValidator { fun validate(segments, speakers): List<ValidationError>; fun sanitize(...) }` |

### 3.3 `:engine` — sherpa-onnx wrappers + native boundary (8 files)

Class names below are the real `com.k2fsa.sherpa.onnx` Kotlin API (verified in Phase 1 research: `docs/RESEARCH.md` §1.1 `Vad.kt`, §2.1 `OfflineSpeakerDiarization.kt`; ASR classes from the sherpa-onnx kotlin-api `OfflineRecognizer.kt` — same surface the demo `SpeechEngine.kt` imports referenced). **Re-verify against SHERPA_API.md at Phase 4 step 0 (§1.3).**

| # | File | Status | Purpose | Key classes / APIs |
|---|---|---|---|---|
| E1 | `com/example/engine/SherpaNative.kt` | NEW | Native library gate + diagnostics (§55–57, ARCHITECTURE §14). | `object SherpaNative { fun load() }` → `System.loadLibrary("sherpa-onnx-jni")`; installs JNI stdout/stderr log-redirect to logcat; throws `RecognitionException`/`IllegalStateException` if missing |
| E2 | `com/example/engine/backend/BackendProviderImpl.kt` | NEW | Evidence-based CPU-only backend chain (§59–62, §3.7; RAM probe via `ActivityManager`, storage via `StatFs`). | `class BackendProviderImpl(context) : BackendProvider` — `capabilities()`, `recommendProfile()` (CPU 4 threads default → 2 → serial), `lowRamProfile()` (§96) |
| E3 | `com/example/engine/whisper/SherpaWhisperEngine.kt` | NEW (replaces demo `WhisperEngine`/`WhisperEngineAdapter`/`WhisperLib` — all DELETE) | `SpeechEngine` impl on `OfflineRecognizer` (§52). **Ptr lifecycle per ARCHITECTURE §5**: wrapper owns `OfflineRecognizer`; close → `nativeDelete` equivalent; synchronized calls; cancellation checked between segments; seconds→µs mapping (§30). | Uses: `com.k2fsa.sherpa.onnx.OfflineRecognizer`, `OfflineRecognizerConfig`, `OfflineModelConfig`, `OfflineWhisperModelConfig` (`language`, `encoder`, `decoder`, `task`), `OfflineStream` (`createStream()`, `decode(stream)`, `getResult(stream)` → `OfflineRecognizerResult(text, tokens: Array<OfflineRecognizerResult.Token(start, end, text)>)`) |
| E4 | `com/example/engine/vad/SherpaVadEngine.kt` | NEW (replaces demo `VoiceActivityDetector` RMS heuristic) | `VadEngine` impl on sherpa `Vad` (Silero) (§54). | Uses: `com.k2fsa.sherpa.onnx.Vad`, `VadModelConfig` (+ `SileroVadModelConfig`), methods `acceptWaveform(FloatArray)`, `isSpeechDetected()`, `pop()/front()`, `reset()`, `flush()`; maps speech windows → `SpeechRegion(startUs, endUs)` |
| E5 | `com/example/engine/diarization/SherpaDiarizationEngine.kt` | NEW (replaces demo `SpeakerDiarizer` heuristics) | `DiarizationEngine` impl on `OfflineSpeakerDiarization` (§53); §25 modes (`KNOWN_SPEAKER_COUNT` → `numClusters=N` w/ graceful threshold fallback; `AUTOMATIC` → `numClusters=-1` + threshold). | Uses: `OfflineSpeakerDiarization`, `OfflineSpeakerDiarizationConfig(segmentation = OfflineSpeakerSegmentationModelConfig(model, numThreads, provider="cpu"), embedding = SpeakerEmbeddingExtractorConfig(model, ...), clustering = FastClusteringConfig(numClusters, threshold), minDurationOn, minDurationOff)`; `process(FloatArray): Array<OfflineSpeakerDiarizationSegment>`, `processWithCallback(..., onProgress)`; segment `start/end` (seconds), `speaker: Int` → stable per-job `SpeakerRef` |
| E6 | `com/example/engine/lang/SherpaLanguageDetector.kt` | NEW | `LanguageDetector` impl via `SpokenLanguageIdentification` reusing the installed Whisper tiny/base encoder/decoder (§24/§4.1, minimal disk). | Uses: `SpokenLanguageIdentification`, `SpokenLanguageIdentificationConfig(whisper = OfflineWhisperModelConfig(...))`, `compute(stream): String` (ISO-639-1); confidence from softmax over language-token log-probs with SNR fallback (ARCHITECTURE §4.1); unsupported/low-confidence → fallback "en" then "ru" (D9) |
| E7 | `com/example/engine/config/SherpaModelConfigFactory.kt` | NEW | Maps `ModelDescriptor` (registry) → sherpa configs; single place for AAR config construction; provider="cpu", numThreads from `BackendProfile`. | `object SherpaModelConfigFactory { fun offlineModelConfig(descriptor, language, threads): OfflineModelConfig; fun vadModelConfig(descriptor, params): VadModelConfig; fun diarizationConfig(seg, emb): OfflineSpeakerDiarizationConfig }` |
| E8 | `com/example/engine/util/SegmentMapper.kt` | NEW | Seconds↔µs + `OfflineRecognizerResult` → domain `Word`/`TranscriptionSegment`; speaker containment mapping by word midpoint (ARCHITECTURE §3.1 step 7). | `object SegmentMapper { fun toWords(result, startUs): List<Word>; fun assignSpeakers(segments, speakerTimeline): List<TranscriptionSegment> }` — pure, unit-testable (JVM) |

**Whisper.cpp fallback engine** (`whispercpp/WhisperCppEngine.kt`) is **deliberately NOT in the Phase 4 tree**: sherpa-onnx AAR is the ratified primary (RESEARCH part 1 decision matrix 216/235); the `SpeechEngine` interface (D1) is the replaceable boundary. If AAR integration fails hard at Phase 4 M4, the lead opens a change request (contract §117) and a fallback engine is added then. No stub of it is created now (contract §122).

### 3.4 `:data` — Room, model installation, media decode, export (28 files)

| # | File | Status | Purpose | Key classes / APIs |
|---|---|---|---|---|
| R1 | `com/example/data/database/VoiceScribeDatabase.kt` | REUSE+REFACTOR (demo `AppDatabase`) | Room DB, all entities + FTS table, version, migrations (§10, §13). | `@Database(version = 2, entities = [TranscriptionJobEntity, TranscriptionSegmentEntity, WordEntity, SpeakerEntity, StatisticsEntity, ModelDescriptorEntity, TranscriptionSegmentFts])`; `voiceScribeDao(...)`; `MIGRATION_1_2` from `Migrations.kt` |
| R2 | `com/example/data/database/Converters.kt` | REUSE (demo `StringListConverter`) | Type converters (string lists, enums). | `StringListConverter`, `JobStateConverter`, `ModelInstallStateConverter` |
| R3 | `com/example/data/database/Migrations.kt` | NEW | Demo schema v1 (4 entities, `durationMs`) → v2 (µs, Word/Statistics/FTS). | `val MIGRATION_1_2: Migration` (add tables/columns, migrate `durationMs`→`durationUs`) |
| R4 | `com/example/data/database/entity/TranscriptionJobEntity.kt` | REUSE+REFACTOR | Job row. | `@Entity(tableName="transcription_jobs")` — id PK, mediaUri, mediaName, mimeType, **durationUs: Long**, config JSON, status (JobState), createdAt/startedAt/completedAt, activeModelId |
| R5 | `com/example/data/database/entity/TranscriptionSegmentEntity.kt` | REUSE+REFACTOR | Segment row (canonical transcript). | PK auto id, FK jobId, **startUs/endUs Long**, text, confidence, FK speakerId (nullable) |
| R6 | `com/example/data/database/entity/WordEntity.kt` | NEW | Word row (§29). | PK id, FK segmentId, word, **startUs/endUs Long**, confidence, index |
| R7 | `com/example/data/database/entity/SpeakerEntity.kt` | REUSE+REFACTOR | Speaker row (stable id, mutable display name §77). | PK id, FK jobId, displayName, colorIndex, confidence |
| R8 | `com/example/data/database/entity/StatisticsEntity.kt` | NEW | Per-job statistics (§78). | PK jobId (1-to-1), durationUs, processingTimeMs, rtf, modelId, backend, peakMemoryBytes, threadCount, detectedLanguage, speakerCount |
| R9 | `com/example/data/database/entity/ModelDescriptorEntity.kt` | REUSE+REFACTOR | Persisted registry/installed state (§33–34). | PK id, name, version, provider, type, format, quantization, sizeBytes, estimatedRamBytes, license, licenseUrl, downloadUrl, checksumSha256, managedFileNames, installState, installedVersion, isActive |
| R10 | `com/example/data/database/dao/TranscriptionJobDao.kt` | REUSE+REFACTOR | Job queries. | `@Query` `observeAll(): Flow<List<...>>`, `observeById`, `insert`, `updateStatus`, `updateProgress` |
| R11 | `com/example/data/database/dao/TranscriptSegmentDao.kt` | REUSE+REFACTOR | Segment + word + speaker join queries. | `observeByJob(jobId): Flow<List<SegmentWithWords>>`, `insertAll(segments, words)`, `deleteByJob` |
| R12 | `com/example/data/database/dao/TranscriptSegmentFtsEntity.kt` | NEW | FTS index table (§13/§76, Correction C4 → **@Fts4**). | `@Fts4(contentEntity = TranscriptionSegmentEntity::class) @Entity(tableName="segment_fts") data class SegmentFts(segmentId, text)` |
| R13 | `com/example/data/database/dao/TranscriptSegmentFtsDao.kt` | NEW | FTS MATCH search. | `@Query("SELECT ... FROM segment_fts WHERE segment_fts MATCH :query") fun search(query, jobId?): List<FtsMatch>`; speaker filter via join |
| R14 | `com/example/data/database/dao/WordDao.kt` | NEW | Word queries (highlighting §29). | `observeBySegment`, `insertAll` |
| R15 | `com/example/data/database/dao/SpeakerDao.kt` | REUSE+REFACTOR | Speaker CRUD + rename (§77). | `observeByJob`, `insertAll`, `rename(id, newDisplayName)` |
| R16 | `com/example/data/database/dao/StatisticsDao.kt` | NEW | Statistics row. | `insert`, `observeByJob` |
| R17 | `com/example/data/database/dao/ModelDescriptorDao.kt` | REUSE+REFACTOR | Model registry/state persistence. | `observeAll(): Flow<List<ModelDescriptorEntity>>`, `upsert`, `updateInstallState`, `setActive` |
| R18 | `com/example/data/repository/TranscriptionRepositoryImpl.kt` | REUSE+REFACTOR (demo `TranscriptionRepository`, 378 lines) | Implements D6: entity↔model mapping, save full transcript atomically (job+segments+words+speakers+statistics in one `withTransaction`), broadcast Flow. | Constructor: simple DAO set; `withTransaction { ... }` |
| R19 | `com/example/data/repository/ModelRepositoryImpl.kt` | REUSE+REFACTOR (demo `ModelRepository`) | Implements D7: registry (from `ModelCatalog`) ⊗ installed state (DB + filesDir scan + corruption check), active lock (§34–38). | observeAll / install / delete / switch; corruption = existence+size+optional SHA spot-check (§35) |
| R20 | `com/example/data/download/ModelDownloadManager.kt` | REUSE+REFACTOR (demo `ModelDownloadManager` 179 lines — real HTTP, redirects/HTML-guard/rollback; **add** SHA-256 on the fly, atomic install, pause/resume via `Range`, cancel) | Implementation of D10 for registry models (§36, §39, §82). | OkHttp client; `download(url, tmpFile, expectedSha256, token, onProgress)`; writes `.tmp` under `cacheDir/downloads`; temp never looks installed (§36) |
| R21 | `com/example/data/download/ChecksumVerifier.kt` | NEW | Streaming SHA-256 (± HKDF-free) verify (demo checksums were fake MD5-sized — AUDIT). | `object ChecksumVerifier { suspend fun verify(file: File, expectedSha256: String, token): Boolean }` (streaming `MessageDigest`) |
| R22 | `com/example/data/storage/ModelStorageManager.kt` | NEW | Filesystem half of §36–37: paths (`filesDir/models`), atomic `renameTo`/`ATOMIC_MOVE`, delete with active-model guard, tmp cleanup, free-space checks (§97). | `fun finalDir(modelId): File; fun atomicMove(tmp, final); suspend fun delete(id); fun freeBytes(): Long` |
| R23 | `com/example/data/media/MediaDecoder.kt` | REUSE+REFACTOR (demo `AudioProcessor` 303 lines — real MediaCodec/MediaExtractor decode + metadata) | Streaming decode of SAF-backed source → 16 kHz mono float chunks (§41–44, §51): `MediaExtractor`+`MediaCodec` (framework codecs; RESEARCH §3.4), resample call-through. | `class MediaDecoder(context) : com.example.core.domain.media.MediaDecoder` — `open(source)`, `pcmChunks(): Flow<PcmChunk>` (30 s chunks, §7.1), `close()` |
| R24 | `com/example/data/media/AudioResampler.kt` | NEW (resample logic extracted from demo `AudioProcessor`) | Channel downmix + linear/Sinc-ish resample to 16 kHz float32 (`[-1,1]`) (§44). | `object AudioResampler { fun toMono16k(input: FloatArray, inSampleRate, inChannels): FloatArray }` |
| R25 | `com/example/data/media/PcmCache.kt` | NEW | Temp 30 s PCM chunk file in `cacheDir`, bounded, cleaned on success/failure/process interruption (§7.1, §45, §97). | `class PcmCache(context)` — `write(chunk)`, `readChunk(idx)`, `clear()` |
| R26 | `com/example/data/export/TranscriptExporterImpl.kt` | REUSE+REFACTOR (demo `TranscriptExporter` 170 lines — real generators, `schemaVersion=1`) | Implements D9: TXT/SRT/VTT/JSON from **canonical transcript only** (§70), invariant validation first (§30/§108), UTF-8. | Constructor takes `SegmentValidator`; `exportToTxt/Srt/Vtt/Json` |
| R27 | `com/example/data/search/TranscriptSearchImpl.kt` | NEW | Implements D16 on FTS (R13) with speaker filter + timestamp navigation (§76). | `search(jobId, query, speakerId?)` |
| R28 | `com/example/data/model/ModelCatalog.kt` | NEW (absorbs demo `VoiceModelConfig` registry — DELETE that file) | Hardcoded verified registry per PROJECT_MANIFEST §5 with SHA-256 + license URLs (§15). | `object ModelCatalog` — Whisper Tiny int8 (39 MB), Whisper Base int8 (74 MB), Whisper Small/Medium int8 (HIGH), `silero_vad.onnx` 0.64 MB / `silero_vad.int8.onnx` 0.21 MB, pyannote-segmentation int8 1.5 MB / fp32 5.7 MB, 3D-Speaker ERes2Net base 39.6 MB; each `ModelDescriptor` lists managed files (encoder/decoder/tokens for Whisper) |

### 3.5 `:app` — Compose UI, MVVM, FGS, Hilt (28 files)

| # | File | Status | Purpose | Key classes / APIs |
|---|---|---|---|---|
| A1 | `com/example/app/VoiceScribeApplication.kt` | NEW | App entry; Hilt; installs `SherpaNative.load()` (in EngineModule init), initializes Room, resumes interrupted jobs (contract §50 — conservative: mark interrupted `RUNNING_*` jobs `FAILED` unless resumable). | `@HiltAndroidApp class VoiceScribeApplication : Application()` |
| A2 | `com/example/app/di/AppModule.kt` | NEW | Hilt: Room DB, DAOs, repositories. | `@Module @InstallIn(SingletonComponent::class)` — `@Provides` `VoiceScribeDatabase`, `TranscriptionRepository` (impl), `ModelRepository` (impl), `ModelCatalog` |
| A3 | `com/example/app/di/EngineModule.kt` | NEW | Hilt: bind engines to sherpa impls + `BackendProviderImpl` (§19 replacement boundary). | `@Binds` `SpeechEngine→SherpaWhisperEngine`, `VadEngine→SherpaVadEngine`, `DiarizationEngine→SherpaDiarizationEngine`, `LanguageDetector→SherpaLanguageDetector`, `BackendProvider→BackendProviderImpl` |
| A4 | `com/example/app/di/UseCaseModule.kt` | NEW | Hilt: provide `RunTranscriptionUseCase`, `GetModelsUseCase`, `ManageModelUseCase`, `ExportTranscriptUseCase`, `SearchTranscriptUseCase`, `TranscriptionLogger` (→ `AndroidJobLogger`). | |
| A5 | `com/example/app/service/MediaProcessingService.kt` | NEW (ARCHITECTURE §11, §6.2, §67) | FGS type `mediaProcessing`: starts on job submit, runs `RunTranscriptionUseCase` in `serviceScope(SupervisorJob + Dispatchers.Default)`, progress notification, Cancel action → cancels scope → `CANCELLED` state persistence; survives Activity recreation (§66). | `class MediaProcessingService : Service()` — `startForeground(NOTIF_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)`; binds jobId via Intent extra |
| A6 | `com/example/app/service/TranscriptionNotification.kt` | NEW | Notification channel + builder (progress bar + cancel action, POST_NOTIFICATIONS runtime request). | `object TranscriptionNotification { fun createChannel(ctx); fun build(ctx, job, progress): Notification }` |
| A7 | `com/example/app/ui/MainActivity.kt` | REUSE+REFACTOR | Single-activity shell, nav host, adaptive window size (RESEARCH §3.3). | `ComponentActivity` + `setContent { VoiceScribeTheme { VoiceScribeNavHost() } }` |
| A8 | `com/example/app/ui/navigation/VoiceScribeNavHost.kt` | NEW | Navigation graph (all screens reachable, predictable back — §65). | `NavHost(navController, start = Screen.Home)` |
| A9 | `com/example/app/ui/navigation/Screen.kt` | REUSE+REFACTOR (demo `Screen.kt`) | Route enum. | `enum class Screen(route) { Home, Jobs, JobDetail(jobId), Models, Settings, Benchmark, About }` |
| A10 | `com/example/app/ui/screens/HomeScreen.kt` | REUSE+REFACTOR | Pick media (SAF `ACTION_OPEN_DOCUMENT` + persistable grant), show recommended model (D5 profile), configure quick settings, Start → submit job → start FGS. | Composable + `HomeViewModel`; empty/error/loading states (§64) |
| A11 | `com/example/app/ui/screens/JobsScreen.kt` | REUSE+REFACTOR | Job list with status chips + live progress; Cancel action (§47 UI); tap → detail. | `JobsViewModel` |
| A12 | `com/example/app/ui/screens/TranscriptDetailScreen.kt` | REUSE+REFACTOR | Transcript review: speaker-colored segments, word timestamps, rename speaker (§77), search bar with highlight (§76), export buttons → SAF picker → `SafExporter` (§70). | `TranscriptDetailViewModel` |
| A13 | `com/example/app/ui/screens/ModelManagerScreen.kt` | REUSE+REFACTOR | Registry list: download / pause / resume / cancel / delete (active guard) / switch active; shows size, SHA state, license link (§3.4, §15). | `ModelManagerViewModel` |
| A14 | `com/example/app/ui/screens/SettingsScreen.kt` | NEW | Language AUTO/MANUAL + EN/RU (§24), VAD enable + threshold (§1.1 research), diarization mode + expected count (§25), thread count, backend read-only (CPU), storage info, overlap limitation note (§32 documentation), privacy statement (§79). | `SettingsViewModel` (persist via DataStore/SharedPreferences) |
| A15 | `com/example/app/ui/screens/BenchmarkScreen.kt` | REUSE+REFACTOR (demo `BenchmarkScreen` — remove fakes) | Real performance lab: RTF, peak RAM, per-stage times from `TranscriptionStatistics` (§88–91); "NOT MEASURED" where no data (contract §130). | `BenchmarkViewModel` |
| A16 | `com/example/app/ui/components/ProgressCard.kt` | REUSE | Job progress card (stage + fraction). | Composable |
| A17 | `com/example/app/ui/components/SpeakerBadge.kt` | REUSE | Speaker chip w/ color + rename affordance. | Composable |
| A18 | `com/example/app/ui/components/AudioWaveformVisualizer.kt` | REUSE+REFACTOR | Waveform from real PCM (chunks from `PcmCache`), no fake waves. | Composable |
| A19 | `com/example/app/ui/viewmodel/HomeViewModel.kt` | NEW (split from demo `MainViewModel`) | Home state: media pick, config, submit job (create → `CREATED` → start service), recommended model. | `StateFlow<HomeUiState>` |
| A20 | `com/example/app/ui/viewmodel/JobsViewModel.kt` | NEW | Job list + progress + cancel. | `StateFlow<List<TranscriptionJob>>` via repo `observeJobs()` |
| A21 | `com/example/app/ui/viewmodel/TranscriptDetailViewModel.kt` | NEW | Transcript, rename, search, export orchestration (SAF `ACTION_CREATE_DOCUMENT` + `SafExporter`). | `StateFlow<DetailUiState>` |
| A22 | `com/example/app/ui/viewmodel/ModelManagerViewModel.kt` | NEW | Model states + actions via `ManageModelUseCase`/`GetModelsUseCase`. | `StateFlow<ModelsUiState>` |
| A23 | `com/example/app/ui/viewmodel/SettingsViewModel.kt` | NEW | Settings persistence + apply. | |
| A24 | `com/example/app/ui/theme/Color.kt` | REUSE | Theme colors. | |
| A25 | `com/example/app/ui/theme/Theme.kt` | REUSE | `VoiceScribeTheme` (demo theme reused; adjust for accessibility contrast §68). | |
| A26 | `com/example/app/ui/theme/Type.kt` | REUSE | Typography (text scaling §68). | |
| A27 | `com/example/app/util/AndroidJobLogger.kt` | NEW (implements D11; `AppLogger` absorbed) | Logcat impl of `TranscriptionLogger` with `[jobId] [component] event` structured lines (§83–85); never logs transcript text; release = WARN+ only (§84). | |
| A28 | `com/example/app/util/SafExporter.kt` | NEW | Writes exporter string to SAF `ACTION_CREATE_DOCUMENT` URI output stream (§12, §70). | `suspend fun write(uri: Uri, content: String)` |

### 3.6 Build, manifest, and config files (root/app level)

| # | File | Status | Purpose |
|---|---|---|---|
| B1 | `settings.gradle.kts` | REWRITE | `include(":core:model", ":core:domain", ":engine", ":data", ":app")`, repositories: google + mavenCentral (+ `mavenCentral` for sherpa-onnx) |
| B2 | `build.gradle.kts` (root) | REWRITE | Plugin aliases `apply false`: android application/library, kotlin android/compose, ksp, hilt |
| B3 | `gradle/libs.versions.toml` | REFACTOR | + sherpa-onnx, hilt, kotlinx-coroutines already present, room 2.8.4, compose BOM 2026.06.01; drop unused firebase entries (commented ok — remove active refs) |
| B4 | `:core:model/build.gradle.kts` | NEW | JVM lib (java-library + kotlin), junit test deps |
| B5 | `:core:domain/build.gradle.kts` | NEW | JVM lib, kotlinx-coroutines-core, junit, coroutines-test |
| B6 | `:engine/build.gradle.kts` | NEW | Android lib, `com.k2fsa:sherpa-onnx` AAR, jni test deps |
| B7 | `:data/build.gradle.kts` | NEW | Android lib, room + ksp, okhttp, robolectric |
| B8 | `:app/build.gradle.kts` | REWRITE (demo single module) | Android app, compose, hilt + ksp, navigation, lifecycle-service |
| B9 | `gradle.properties` | KEEP | memory-safe (already merged) |
| B10 | `app/src/main/AndroidManifest.xml` | REUSE+REFACTOR | FGS `mediaProcessing` (§2.4), Hilt app class, permissions |
| B11 | `app/src/main/res/**` | REUSE | strings (RU/EN), themes, icons, `file_paths.xml` |
| B12 | `app/proguard-rules.pro` | REUSE+REFACTOR | keep sherpa-onnx JNI classes; R8 minify rules |

---

## 4. Gap Analysis vs AUDIT.md

Every demo file (rows from AUDIT §"Ключевые находки" + §"Что переиспользовать") maps to a decision and a target in the new tree. "DELETE" = removed from the build in Phase 4.

| Demo file (repo-root-relative) | AUDIT finding | Decision | Target in new tree |
|---|---|---|---|
| `app/src/main/cpp/whisper.h` | Fabricated API (§123), not upstream whisper.cpp | **DELETE** | — (sherpa AAR provides JNI; no custom C++ in Phase 4) |
| `app/src/main/cpp/whisper-jni.cpp` | Skeleton against fabricated header; not linked (no externalNativeBuild) | **DELETE** | — |
| `app/src/main/cpp/CMakeLists.txt` | Not wired into build | **DELETE** | — (native boundary via `com.k2fsa:sherpa-onnx` AAR; ARCHITECTURE §5) |
| `app/src/main/java/com/k2fsa/sherpa/onnx/SherpaOnnx.kt` | Full stub: fake `OfflineStream(1001L)`, empty decode, `getResult()→""` | **DELETE** | replaced by real `com.k2fsa.sherpa.onnx` classes from the AAR (§3.3) |
| `app/src/main/java/com/example/engine/SpeechEngine.kt` | Interface skeleton (real-ish shape, wrong deps) | REUSE (skeleton) | `:core:domain/engine/SpeechEngine.kt` (D1) — rewritten to §52 contract |
| `app/src/main/java/com/example/engine/SpeechEngineFactory.kt` | Factory over fake engines | **DELETE** | replaced by Hilt `EngineModule` (A3) + `BackendProvider` |
| `app/src/main/java/com/example/engine/whisper/WhisperEngine.kt` (200 l) | Pipeline skeleton against fake runtime; hardcoded ru; fake progress | **DELETE** | `SherpaWhisperEngine` (E3) + `RunTranscriptionUseCase` (D12) |
| `app/src/main/java/com/example/engine/whisper/WhisperEngineAdapter.kt` | Adapter over fake engine | **DELETE** | D1/E3 |
| `app/src/main/java/com/example/engine/whisper/WhisperLib.kt` | Fabricated JNI bridge (no such native symbols) | **DELETE** | E1 `SherpaNative` (loads real `sherpa-onnx-jni`) |
| `app/src/main/java/com/example/engine/audio/AudioProcessor.kt` (303 l) | **Real** MediaCodec/MediaExtractor decode + metadata; no chunking; full-file FloatArray (OOM risk §44) | **REUSE+REFACTOR** (extract decode + add 30 s chunking, µs) | `:data/media/MediaDecoder.kt` (R23) + `:data/media/AudioResampler.kt` (R24) + `PcmCache` (R25) |
| `app/src/main/java/com/example/engine/export/TranscriptExporter.kt` (170 l) | **Real** TXT/SRT/VTT/JSON generators, schemaVersion=1; no invariant validation, no SAF | **REUSE+REFACTOR** | `:data/export/TranscriptExporterImpl.kt` (R26) + `:core:domain/export/TranscriptExporter.kt` (D9) + `SegmentValidator` (D20) + `SafExporter` (A28) |
| `app/src/main/java/com/example/engine/vad/VoiceActivityDetector.kt` | Primitive RMS, bugs, fallback-to-whole-file | **DELETE** (logic); **EXTRACT-FILE** (model) | `SpeechRegion` → `:core:model/SpeechRegion.kt` (M15); real VAD → `SherpaVadEngine` (E4) |
| `app/src/main/java/com/example/engine/vad/SpeechRegion` (in VAD file) | usable data class | EXTRACT-FILE | M15 |
| `app/src/main/java/com/example/engine/diarization/SpeakerDiarizer.kt` | Heuristic energy+ZCR, max 2 speakers, hardcoded confidence, result unused | **DELETE** (logic); **EXTRACT-FILE** (model) | `SpeakerSegment` → M16; real → `SherpaDiarizationEngine` (E5) |
| `app/src/main/java/com/example/engine/diarization/DiarizedSegment` | usable data class | EXTRACT-FILE | M16 (`SpeakerSegment`) |
| `app/src/main/java/com/example/data/model/JobState.kt` | 11 of §22 states; real display names | **REUSE+REFACTOR** | `:core:model/JobState.kt` (M1) — add `READY_FOR_REVIEW`, `EXPORTING` (Correction C1) |
| `app/src/main/java/com/example/data/model/TranscriptionConfig.kt` | Good shape; default ru; backend Preference string | **REUSE+REFACTOR** | M10 — typed `LanguageMode`/`DiarizationMode`/`BackendPreference` |
| `app/src/main/java/com/example/data/model/VoiceModelConfig.kt` | Registry with fake/rough data (GigaAM entry out of scope, empty checksums, fake sizes) | **DELETE** (file); content → registry | `:data/model/ModelCatalog.kt` (R28) with PROJECT_MANIFEST §5 SHA-256 catalog |
| `app/src/main/java/com/example/data/model/ExportFormat.kt` | Good | **REUSE** | M19 |
| `app/src/main/java/com/example/data/model/MediaMetadata.kt` | Good; `durationMs` | **REUSE+REFACTOR** | M9 (`durationUs`) |
| `app/src/main/java/com/example/data/local/entity/*.kt` (4) | Real schema base; `durationMs`; no words/statistics | **REUSE+REFACTOR** | R4, R5, R7, R9 (+ new R6 `WordEntity`, R8 `StatisticsEntity`) |
| `app/src/main/java/com/example/data/local/dao/*.kt` (4) | Real DAOs | **REUSE+REFACTOR** | R10, R11, R15, R17 (+ new R13 FTS, R14 Word, R16 Statistics) |
| `app/src/main/java/com/example/data/local/AppDatabase.kt` | Real | **REUSE+REFACTOR** | R1 `VoiceScribeDatabase` (+ FTS + migrations) |
| `app/src/main/java/com/example/data/local/converter/StringListConverter.kt` | Real | **REUSE** | R2 |
| `app/src/main/java/com/example/data/repository/ModelDownloadManager.kt` (179 l) | **Real** HTTP w/ redirects/HTML-guard/rollback; fake MD5-shaped checksums, non-atomic install, no pause/resume | **REUSE+REFACTOR** | `:data/download/ModelDownloadManager.kt` (R20) + `ChecksumVerifier` (R21) + `ModelStorageManager` (R22) |
| `app/src/main/java/com/example/data/repository/ModelRepository.kt` (217 l) | Real-ish; fake checksums/data | **REUSE+REFACTOR** | R19 `ModelRepositoryImpl` + R28 catalog |
| `app/src/main/java/com/example/data/repository/TranscriptionRepository.kt` (378 l) | Real pipeline data layer; cancel = DB flag only; no service | **REUSE+REFACTOR** | R18 (atomic save, µs, FTS wiring) |
| `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt` | StateFlow shell **good**; seed jobs with invented transcripts (§122), hardcoded confidence/language, fake `HardwareInfo(hasVulkan=true)` | **REUSE+REFACTOR** (split) | A19–A23 ViewModels; real capabilities → E2 `BackendProviderImpl`; seeds removed |
| `app/src/main/java/com/example/ui/viewmodel/HardwareInfo` (in MainViewModel) | Fake flags | **DELETE** | real `DeviceCapabilities` from E2 |
| `app/src/main/java/com/example/ui/screens/HomeScreen.kt` | Shell | **REUSE+REFACTOR** | A10 |
| `app/src/main/java/com/example/ui/screens/JobsScreen.kt` | Shell | **REUSE+REFACTOR** | A11 |
| `app/src/main/java/com/example/ui/screens/ModelManagerScreen.kt` | Shell | **REUSE+REFACTOR** | A13 |
| `app/src/main/java/com/example/ui/screens/BenchmarkScreen.kt` | Fake benchmark mockups | **REUSE+REFACTOR** | A15 (real statistics only; NOT MEASURED otherwise) |
| `app/src/main/java/com/example/ui/screens/TranscriptDetailScreen.kt` | Shell (no SAF export, no search) | **REUSE+REFACTOR** | A12 |
| `app/src/main/java/com/example/ui/components/*` (ProgressCard, SpeakerBadge, AudioWaveformVisualizer) | Useful | **REUSE** | A16–A18 (waveform binds real PCM) |
| `app/src/main/java/com/example/ui/components/DebugSettingsSection.kt` | Debug leftovers | **DELETE** (fold into Settings) | A14 `SettingsScreen` |
| `app/src/main/java/com/example/ui/navigation/Screen.kt` | Good | **REUSE+REFACTOR** | A9 |
| `app/src/main/java/com/example/ui/theme/*` | Good | **REUSE** | A24–A26 |
| `app/src/main/java/com/example/util/AppLogger.kt` | Basic log | **REUSE+REFACTOR** | A27 `AndroidJobLogger` (structured §83–85) |
| `app/src/main/java/com/example/MainActivity.kt` | Shell | **REUSE+REFACTOR** | A7 |
| `app/src/test/java/com/example/ExportAndVADTest.kt` (87 l) | 1 of 2 meaningful tests (exporter + VAD) | **REUSE+REFACTOR** | split into `:data` exporter tests + `:core:model`/domain tests (§7) |
| `app/src/test/java/com/example/ExampleUnitTest.kt`, `ExampleRobolectricTest.kt`, `GreetingScreenshotTest.kt` | Placeholder/roborazzi (removed) | **DELETE** | replaced by §7 test skeleton |
| `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` | Fails placeholder | **DELETE** | replaced by §7 instrumented tests |
| `app/build.gradle.kts` (single module) | Legacy deps, no AAR | REWRITE | B8 + module structure B4–B7 |
| `metadata.json` (root) | Claims `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` — contradicts §79 | **DELETE/UPDATE** | remove capability claim (owner-facing; update in Phase 4, flag in PR) |
| `.env.example`, firebase/google-services/secrets remnants | Cloud leftovers (§79) | **DELETE** | remove from repo in Phase 4 |

**AUDIT §"Что переиспользовать" summary:** AudioProcessor (1), ModelDownloadManager (2), TranscriptExporter (3), Room schema (4), pipeline skeleton+JobState+MainViewModel (5), UI shell (6) — all six map to REUSE+REFACTOR targets above; none of the fake AI layer survives.---

## 5. Ordered Implementation Sequence (Phase 4 execution order)

Bottom-up by dependency. **Rule for every step:** complete the file with a real implementation (no `TODO`, no stubs — contract §121–122), run its verification, commit a small focused commit (WORKFLOW rule 5), then proceed. Memory-safe verification on this 3.9 GB machine (**never** run a whole-project `assembleDebug` in one Gradle invocation): build module-by-module with `./gradlew :<module>:<task> -x lint` per ENV_STATUS.md; `gradle.properties` already limits heap/workers/daemon.

**Phase 4 step 0 — pre-flight (no code):** verify AAR availability `com.k2fsa:sherpa-onnx:<latest-stable>` on Maven Central from the build machine (Phase 1 research flagged Maven Central timeouts from the shared box — if unreachable, use a Gradle mirror approved by the lead), pin the version in the catalog; fold in `SHERPA_API.md` (§1.3); re-verify the AAR's minSdk ≤ 24.

### Milestones

| Milestone | Contents | Verification (what proves it done) |
|---|---|---|
| **M0** | Build restructure: `settings.gradle.kts`, root build, catalog, 5 module build files with empty source sets | `./gradlew :core:model:assemble :core:domain:assemble :engine:assembleDebug :data:assembleDebug :app:assembleDebug` — all five resolve and compile with zero sources |
| **M1** | `:core:model` complete (M1–M20) | `./gradlew :core:model:test` — state machine, invariants, timestamp conversion tests green |
| **M2** | `:core:domain` complete (D1–D20) | `./gradlew :core:domain:test` — `JobStateMachine`, `SegmentValidator`, `TimestampFormatter`, `RunTranscriptionUseCase` (fake repo/engines, §122 allows mocks in tests), exporter dispatch tests green |
| **M3** | `:data` complete (R1–R28) | `./gradlew :data:testDebugUnitTest` — checksum, downloader (MockWebServer), exporter golden files, Room in-memory DAO+FTS, migrations; then `:data:assembleDebug` |
| **M4** | `:engine` complete (E1–E8) + AAR wiring | `./gradlew :engine:assembleDebug` resolves the real AAR and compiles; instrumented JNI tests on device: `loadLibrary` + tiny-model inference smoke (via `connectedDebugAndroidTest` on an emulator/device — **not** on this build box, per ENV_STATUS.md) |
| **M5** | `:app` complete: DI, service, VMs, screens, navigation, theme, manifest (A1–A28, B8, B10–B12) | `./gradlew :app:assembleDebug`; ViewModel unit tests; navigation/UI instrumented tests (device) |
| **M6** | Integration + cleanup + acceptance | Full acceptance gate §8: delete demo files, grep gates, run module tests, run `:app:assembleDebug`, PR review (contract §112 — never claim a build without running it) |

### Step-by-step file sequence

Legend: **→** = depends on (exact files/interfaces written earlier in this sequence). Steps execute in order; each row lists only the dependencies that must already exist.

| Step | File | Depends on (exact) | Verifies |
|---|---|---|---|
| 1 | B1 `settings.gradle.kts`, B3 catalog, B2 root build, B4–B8 module build files | — (versions from §2.2 / RESEARCH §3) | M0: five modules assemble empty |
| 2 | M1 `JobState.kt` | — | unit: all 14 contract states, `isTerminal` set |
| 3 | M2 `LanguageMode`, M3 `DiarizationMode`, M4 `BackendPreference`, M5 `ModelType` | M1 | compile |
| 4 | M19 `ExportFormat.kt` | — | compile |
| 5 | M6 `ModelDescriptor`, M7 `ModelInstallState` | M5 | unit: descriptor invariants (SHA-256 regex 64-hex) |
| 6 | M8 `MediaSource`, M9 `MediaMetadata` | — | compile |
| 7 | M10 `TranscriptionConfig` | M2, M3, M4 | compile |
| 8 | M20 `LanguageDetectionResult` | M1 | compile |
| 9 | M17 `TranscriptionStatistics`, M18 `JobProgress`, M15 `SpeechRegion`, M16 `SpeakerSegment` | M1 | compile |
| 10 | M13 `Word`, M14 `Speaker`, M12 `TranscriptionSegment` | M15, M16 | compile |
| 11 | M11 `TranscriptionJob` (aggregate) | M8, M9, M10, M12, M14, M17, M18, M20 | unit: aggregate invariants |
| 12 | **M1 gate → `./gradlew :core:model:test`** | all M* | unit suite green |
| 13 | D17 `JobStateMachine` | M1 | unit: every legal/illegal transition of §22 |
| 14 | D18 `TranscriptionException` (sealed hierarchy) | — | compile |
| 15 | D20 `SegmentValidator`, D19 `TimestampFormatter` | M12, M13, M14 | unit: invariants §30/§102, SRT/VTT formatting |
| 16 | D1 `SpeechEngine` (+`TranscriptionResult`), D2 `VadEngine`, D3 `DiarizationEngine`, D4 `LanguageDetector`, D5 `BackendProvider` (+`DeviceCapabilities`, `BackendProfile`) | M6, M10, M15, M16, M20 | compile |
| 17 | D8 `MediaDecoder` (+`PcmChunk`), D9 `TranscriptExporter`, D10 `ModelDownloader`, D11 `TranscriptionLogger` | D18, M* | compile |
| 18 | D6 `TranscriptionRepository`, D7 `ModelRepository` (+`ModelInstallation`) | M11, M6, M7 | compile |
| 19 | D14 `ManageModelUseCase` | D7, D10, D18 | unit: §35–38 flows with fake repo/downloader |
| 20 | D13 `GetModelsUseCase` | D7 | compile |
| 21 | D16 `SearchTranscriptUseCase` | D6 | compile |
| 22 | D15 `ExportTranscriptUseCase` | D6, D9, D20 | unit: invariant rejection, format dispatch |
| 23 | D12 `RunTranscriptionUseCase` (pipeline §51, §22 transitions, §47 cancellation, §49 partial results, §96 low-RAM governor, §46 progress) | D1–D5, D6, D8, D17, D11 | unit with fakes: happy path; cancel at every stage; low-RAM profile switch; failure at every stage |
| 24 | **M2 gate → `./gradlew :core:domain:test`** | all D* | unit suite green |
| 25 | R2 `Converters`, R4–R9 entities (µs), R10–R17 DAOs + FTS (R12/R13), R3 `Migrations` | M1, M10, M12–M14, M6, M7 | instrumented Room in-memory tests |
| 26 | R1 `VoiceScribeDatabase` | R2–R17 | Room migration test v1→v2 |
| 27 | R21 `ChecksumVerifier` | — | unit: known SHA-256 vectors, streaming, cancellation |
| 28 | R28 `ModelCatalog` (PROJECT_MANIFEST §5 SHA-256 + licenses) | M6 | unit: every entry has 64-hex SHA-256, license, URL |
| 29 | R22 `ModelStorageManager` | M6 | unit with temp dirs: atomic move, delete-active guard, free-space |
| 30 | R20 `ModelDownloadManager` | D10, R21, R22, D11 | unit (MockWebServer): redirects, HTML-guard, SHA-256 mismatch → tmp deleted, Range resume, cancel |
| 31 | R19 `ModelRepositoryImpl` | D7, R1–R17, R20, R22, R28 | unit/instrumented: install state machine, corruption detection, active lock |
| 32 | R24 `AudioResampler`, R25 `PcmCache` | — | unit: 44.1 kHz → 16 kHz mono correctness, chunk bounds, cleanup |
| 33 | R23 `MediaDecoder` | D8, R24, R25, M9 | Robolectric: synthesized WAV → PCM chunks, metadata, malformed input → `DecodingException` |
| 34 | R26 `TranscriptExporterImpl` | D9, D20, M11, M12, M14 | unit: golden TXT/SRT/VTT/JSON incl. Unicode + invariant rejection |
| 35 | R27 `TranscriptSearchImpl` | D16, R13 | instrumented: FTS MATCH, speaker filter |
| 36 | R18 `TranscriptionRepositoryImpl` | D6, R1–R17 | instrumented: atomic save, observe flows, rename keeps speaker id |
| 37 | **M3 gate → `:data:testDebugUnitTest` + `:data:assembleDebug`** | R1–R28 | suite green; module compiles |
| 38 | E1 `SherpaNative`, E2 `BackendProviderImpl` | D5, D11 | instrumented: `loadLibrary("sherpa-onnx-jni")` ok; capability probe sanity |
| 39 | E7 `SherpaModelConfigFactory` | M6, D1–D4, M10 | unit (JVM): descriptor → sherpa configs vs SHERPA_API.md |
| 40 | E4 `SherpaVadEngine` | D2, E7 | instrumented: real Silero VAD on fixture WAV → regions |
| 41 | E8 `SegmentMapper` | M12, M13, M16, D1 | unit: seconds→µs, word/speaker containment |
| 42 | E6 `SherpaLanguageDetector` | D4, E7 | instrumented: EN/RU fixture detection (AUTO); fallback logic unit |
| 43 | E5 `SherpaDiarizationEngine` | D3, E7 | instrumented: 2-speaker fixture diarize; KNOWN_SPEAKER_COUNT → threshold fallback |
| 44 | E3 `SherpaWhisperEngine` | D1, E7, E8 | instrumented: tiny int8 model → text + word timestamps on fixture; ptr close/reopen, double-close safe |
| 45 | **M4 gate → `:engine:assembleDebug` + `connectedDebugAndroidTest` (device)** | E1–E8 | AAR resolves; JNI tests pass |
| 46 | A1 `VoiceScribeApplication`, A2–A4 DI modules | R1, R18, R19, E3–E6, E2, D12–D16, A27 | compile |
| 47 | A6 `TranscriptionNotification`, A5 `MediaProcessingService` (FGS mediaProcessing) | D12, A27, A2–A4 | instrumented: FGS start/stop, cancel → `CANCELLED`, progress notification |
| 48 | A24–A26 theme, A9 `Screen`, A7 `MainActivity`, A8 `VoiceScribeNavHost` | — | compile + navigation UI test |
| 49 | A27 `AndroidJobLogger`, A28 `SafExporter` | D11 | unit: structured `[jobId]` lines, no transcript text logged; instrumented: SAF stream write |
| 50 | A19–A23 ViewModels | D6, D7, D12–D16, A27 | unit: state flows, form validation |
| 51 | A10–A15 Screens (Home, Jobs, Detail, Models, Settings, Benchmark) | A19–A23, A28 | Compose UI tests: empty/error/loading states, rename, search bar, export |
| 52 | A16–A18 components | A10–A15 | compile |
| 53 | B10 manifest (FGS type, permissions), B11 res, B12 proguard | A1, A5 | `:app:assembleDebug` |
| 54 | **M5 gate → `:app:assembleDebug`** | all A* | app assembles incl. AAR packaging |
| 55 | **M6 integration**: delete demo files (all DELETE rows of §4 — `cpp/`, `SherpaOnnx.kt`, `WhisperLib.kt`, fakes, `metadata.json` cloud capability), run grep gates, run module tests, run acceptance gate §8, open PR | everything | acceptance checklist §8 all green |---

## 6. Contract Cross-Reference (promt.md → implementing files)

Where a requirement needs a Phase-4 decision, the recommended option is in §9 (D#).

| Contract section | Requirement | Implementing file(s) | Notes / decisions |
|---|---|---|---|
| §3.1 Offline processing | No cloud inference; works offline with installed model | Entire pipeline: D12 `RunTranscriptionUseCase` → E3–E6 (local AAR) | No network call exists anywhere except `ModelDownloadManager` (R20). Verified by §8 grep gate + offline test (§81). |
| §3.2 Whisper transcription | Serious maintained implementation | E3 `SherpaWhisperEngine` on `OfflineRecognizer`; D1 `SpeechEngine` abstraction | sherpa-onnx AAR ratified (RESEARCH part 1). Fallback boundary = D1 (whisper.cpp deferred — D6). |
| §3.3 Speaker diarization | Local diarization, segments ↔ speaker IDs | E5 `SherpaDiarizationEngine`; M14 `Speaker`; M16 `SpeakerSegment`; D12 assign step | OfflineSpeakerDiarization per RESEARCH §2.1. |
| §3.4 Model manager | discover/download/progress/pause/resume/verify/install/update/delete/switch/corruption/recovery/recommend | D7 `ModelRepository`, D14 `ManageModelUseCase`, R19/R20/R21/R22, R28 `ModelCatalog`, E2 `BackendProviderImpl` (recommendation) | Pause/resume only where the transport supports Range (R20) — §39 "where technically supported". |
| §3.5 Language | AUTO detection + MANUAL selection | M2 `LanguageMode`, M20 `LanguageDetectionResult`, D4 `LanguageDetector`, E6 `SherpaLanguageDetector`, A14 Settings screen | AUTO → `SpokenLanguageIdentification` on installed Whisper encoder/decoder; MANUAL bypasses detection (ARCHITECTURE §4). |
| §3.6 Export | TXT/SRT/VTT/JSON | D9 `TranscriptExporter` (+csv rows D15), R26 `TranscriptExporterImpl`, A28 `SafExporter`, A12 screen | Exports canonical transcript only (§70). |
| §3.7/§3.8 Hardware accel + fallback | Validated backend only; graceful fallback chain | M4 `BackendPreference`, D5 `BackendProvider`, E2 `BackendProviderImpl` | Frozen: CPU only (RESEARCH). Chain: CPU(4) → CPU(2) → serial (PROJECT_MANIFEST §4). Decision D7. |
| §15 License audit | No incompatible models/deps; attribution | R28 `ModelCatalog` (license + licenseUrl per model), A14 Settings (privacy/attribution) | All catalog models MIT/Apache-2.0 per RESEARCH §4. Reverb diarization excluded (non-commercial). |
| §16–19 Architecture, dependency rule | Clean modules, controlled deps | §2.1 topology; Gradle enforced | 5 modules; `:core:*` zero-Android. |
| §21 TranscriptionJob | Persistent job aggregate | M11 `TranscriptionJob`, R4 entity | |
| §22 Job states | Explicit states; invalid transitions rejected | M1 `JobState` (14 states), D17 `JobStateMachine` | **Correction C1**: contract set wins over ARCHITECTURE §10.2. Also §22 "optional states may be added where justified" — none added. |
| §23–25 Config & modes | Config fields; AUTO/MANUAL; diarization modes | M10 `TranscriptionConfig`, M2, M3; A14 settings | |
| §26/§77 Speaker model & editing | Stable IDs; rename = metadata only | M14 `Speaker` (id immutable), A12 rename → R15 `SpeakerDao.rename`, R18 repo | |
| §28/§29/§30 Segments, word timestamps, integrity | Optional backend-unsupported fields not fabricated; integer µs canonical; invariants | M12/M13 (µs Long), D20 `SegmentValidator`, E8 `SegmentMapper` (seconds→µs), R26 exports | Lead brief's "timestamps §82" maps here (§82 in promt.md is Security; Correction C3). |
| §31 Transcript assembly | Canonical transcript reconciles Whisper+VAD+diar | D12 pipeline final stage; R18 atomic save | |
| §32 Overlapping speech | Document limitation; never fabricate precision | E5 (dominant speaker mapping for overlap), A14 Settings note | ARCHITECTURE §16.3 risk; pyannote up to 3 overlap, DB/UI 1 speaker per segment — documented in UI. |
| §33/§34 Model entity & registry | Registry ≠ installed; full descriptor | M6 `ModelDescriptor`, R9 entity, R28 catalog, D7/R19 | |
| §35 Model validation | existence → size → checksum → format → metadata → backend → resources | D14, R19 (size+SHA spot-check), R21 `ChecksumVerifier`, E7 config factory | Full runtime format check happens at engine load (M4 instrumented tests). |
| §36 Atomic installation | temp → download → verify → atomic move → register | R20 `ModelDownloadManager`, R21, R22 `ModelStorageManager` | `.tmp` in `cacheDir`; partial downloads never visible (§36). |
| §37 Model deletion | active check → stop → release → remove → update → UI | D14, E3–E6 unload via `AutoCloseable`, R22, R19 | Never delete active model (A13 UI guard). |
| §38 Model switching | release old → load new → validate → activate; keep previous on failure | D14, R19, E3 | |
| §39 Download states | QUEUED/DOWNLOADING/PAUSED/VERIFYING/COMPLETED/FAILED/CANCELLED | M7 `ModelInstallState` + `ModelDownloadProgress`, R20 | |
| §40–44 Media input → preprocessing | SAF-safe input; formats; validation; metadata; 16 kHz mono float, no full-file RAM | M8 `MediaSource`, M9 `MediaMetadata`, D8 `MediaDecoder`, R23/R24/R25 (30 s chunks, PcmCache) | MediaExtractor/MediaCodec framework codecs (D8 decision). |
| §45–46 Temp storage & progress | Cleaned tmp; honest stage progress | R25 `PcmCache`, R20 tmp, M18 `JobProgress` | |
| §47 Cancellation | UI → domain → worker → inference → JNI → native; clean release | D12 (checks `CancellationToken` between segments), A5 service scope cancel, D1–D4 token params, E3 (per-segment check) | |
| §48 Pause | Not exposed for transcription (contract allows omitting) | — | Pause for transcription intentionally NOT implemented (safe resumability not guaranteed); download pause IS (R20). |
| §49/§50 Partial results & recovery | Preserve completed hours; resume only when consistent | D12 (persist completed segments incrementally), A1 Application (interrupted job → FAILED unless resumable) | Decision D8: Phase 4 recovery = safe restart, resume deferred. |
| §51 AI pipeline | Media→decoder→preproc→VAD→segmentation/embedding→Whisper→align→assign→assemble | D12 `RunTranscriptionUseCase` (orchestration), R23, E4, E5, E6, E3, E8 | Exact canonical order justified by RESEARCH; diarization before ASR per ARCHITECTURE §3. |
| §52–54 Engine abstractions | Layer must not depend on runtime | D1/D2/D3 interfaces; E3/E4/E5 impls (Hilt binds A3) | |
| §55–57 JNI boundary & native memory | Explicit lifetime, ownership, cleanup | E1 `SherpaNative`, E3–E6 wrappers (ptr lifecycle per ARCHITECTURE §5: synchronized close, idempotent), A3 | No custom C++ in Phase 4 — AAR owns native side. |
| §60–62 Device capabilities & fallback | Evidence-based backend selection | E2 `BackendProviderImpl`, E7 config (`numThreads` per profile) | Real probe (ActivityManager/StatFs); the demo's fake `HardwareInfo` is deleted. |
| §65–67 Navigation/lifecycle/background | Reachable screens; long work survives lifecycle; FGS | A8/A9 nav, A5 `MediaProcessingService` (type `mediaProcessing`, ARCHITECTURE §11), A1 | WorkManager rejected for active transcription (ARCHITECTURE §11). |
| §68/§69 Accessibility & adaptive UI | TalkBack, semantics, large screens | A7 (windowSizeClass), A10–A15 (semantics), A24–A26 theme | |
| §70–75 Export architecture | Canonical-only export, formats, JSON schemaVersion | D9, R26, M19 (`schemaVersion = 1`) | |
| §76 Search | FTS on canonical transcript + speaker filter + timestamp navigation | D16, R27, R13 FTS | Room `@Fts4` (Correction C4 / D2). |
| §78 Statistics | honest metrics | M17 `TranscriptionStatistics`, R8 entity, A15 Benchmark | Nullable when unavailable. |
| §79/§80 Privacy & network boundary | No cloud, no telemetry; network only for model downloads | A27 logger (no transcript text), R20 downloader (only network component), A14 privacy statement | grep gate §8 (no Firebase/Gemini/secrets). |
| §81 Offline verification | Full offline test matrix | QA phase list in §7 (offline scenarios) | Phase 4 checks per M4/M5; full QA in Phase 7 per contract. |
| §82 Security | URI/MIME validation; HTTPS; checksum; atomic; native safety | R23 (URI/MIME validation in decode), R20 (HTTPS + SHA-256 + atomic), E1–E6 (AAR native safety), D18 typed errors | Lead brief's timestamp §82 → §29/§30 (Correction C3). |
| §83–85 Logging & job correlation | structured `[jobId]` across Kotlin/JNI/C++; no transcript text | D11 `TranscriptionLogger`, A27 `AndroidJobLogger` (`[jobId] [component] event`), E1 native log redirect | |
| §86/§87 Native diagnostics & crash | No cloud crash reporting | D18 typed exceptions (native error string surfaced), A27 local export of diagnostics | |
| §88–91 Benchmark & RTF | Real measurements; RTF = processing/media | M17 `TranscriptionStatistics.rtf`, A15 `BenchmarkScreen`, R8 | "NOT MEASURED" until real runs. |
| §96 Low-RAM | reduce parallelism/buffers under pressure, no uncontrolled growth | E2 `lowRamProfile()`, D12 governor (halve threads, serial, GC between stages, user warning), R25 bounded cache | Memory-safe on 3.9 GB build box also applies to Gradle runs (ENV_STATUS). |
| §97 Storage pressure | Estimate temp storage; warn; cleanup after failure | R22 free-space check, D12 pre-flight, R25/R20 cleanup | |
| §100–104 Testing strategy | Unit/property/native/concurrency | §7 test plan (all levels) | |
| §105–111 Test matrices | Media/transcription/diarization/export/failure/lifecycle/leaks | §7 QA-phase lists | Mostly Phase 7 QA; unit foundations in Phase 4. |
| §112 Build verification | Never claim builds untested | §5 milestones M0–M6, §8 gate | |
| §113–114 Static analysis & traceability | No dead code; every requirement mapped | This table + §5 dependency order | |
| §115–120 Project state machine, freeze, manifest | Don't skip states; freeze after blueprint | This document = THE freeze input; `docs/PROJECT_MANIFEST.md` updated at M6 | |
| §121–123 No placeholders / no fabricated APIs | Complete files only; research before API use | §8 gate (grep for `TODO`, `fun decode...{}`, fake seeds, fabricated JNI); E-section uses only real `com.k2fsa.sherpa.onnx` classes | |
| §128/§130 Uncertainty & honest benchmarking | Report impossibilities; NOT MEASURED vs ESTIMATED | §9 open decisions; A15 benchmark UI | |

---

## 7. Test Plan Skeleton (per ARCHITECTURE §15 and contract §100–104)

Fixture strategy: JVM fixtures = synthesized WAV/PCM (16 kHz mono sine/voice-like), golden transcript files, SHA-256 known vectors, MockWebServer-served model bytes. Device fixtures = tiny Whisper int8 ONNX (gradle-managed download in androidTest source set, ~39 MB), `silero_vad.onnx` (~0.6 MB), a 2-speaker diarization WAV from k2-fsa's diarization release test assets, malformed/empty/silent WAVs, UTF-8 multi-language transcript samples.

### 7.1 `:core:model/src/test/kotlin/...`
- `JobStateTest.kt` — 14 states exist; `isTerminal` correct; display names.
- `TranscriptionConfigTest.kt` — defaults, mode combos, expectedSpeakerCount validation in KNOWN_SPEAKER_COUNT.
- `WordSegmentInvariantTest.kt` — §30 invariants on sample aggregates (property-style loop).
- `ModelDescriptorTest.kt` — checksum format, license presence, managed file names non-empty.
- `TimestampUsTest.kt` — µs arithmetic helpers (if placed here) / overflow guards.

### 7.2 `:core:domain/src/test/kotlin/...`
- `JobStateMachineTest.kt` — every legal transition; every forbidden transition throws; terminal lock; skip rejection; CANCELLED→terminal rejection.
- `SegmentValidatorTest.kt` — monotonic order, `endUs > startUs`, speaker-ref validity, sanitize of empty text.
- `TimestampFormatterTest.kt` — SRT/VTT/TXT golden strings, Unicode, hour rollover.
- `RunTranscriptionUseCaseTest.kt` — fakes for repo + 4 engines + decoder + logger: happy path walks §22 states in order; cancel at each stage persists `CANCELLED`; failure at each stage → `FAILED` with correct typed exception; low-RAM profile switch mid-run; partial-result persistence on failure (§49).
- `ManageModelUseCaseTest.kt` — §35–38: install sequence validates checksum before atomic move; delete of active model rejected; switch keeps previous model on load failure.
- `ExportTranscriptUseCaseTest.kt` — dispatch to all 4 formats; invariant violation → error, nothing exported.
- `BackendProviderContractTest.kt` — profile ordering CPU(4) → CPU(2) → SERIAL; `recommendProfile` never above RAM threshold.

### 7.3 `:data/src/test/kotlin/...` (JVM/Robolectric) + `:data/src/androidTest/kotlin/...`
- `ChecksumVerifierTest.kt` — known SHA-256 vector; streaming on large temp file; cancel throws.
- `ModelDownloadManagerTest.kt` (MockWebServer) — redirect follow; HTML guard; SHA mismatch → tmp deleted + `ModelManagerException`; Range resume; cancel mid-download leaves tmp removable.
- `ModelStorageManagerTest.kt` — atomicMove semantics; delete-active guard; freeBytes.
- `AudioResamplerTest.kt` — 44.1k stereo → 16k mono: length, range `[-1,1]`, tone frequency sanity.
- `PcmCacheTest.kt` — chunk bounds; clear() after success and failure.
- `MediaDecoderRobolectricTest.kt` — synthesized 44.1k WAV → correct chunk count/size; MP3 (fixture); malformed file → `DecodingException`; silent file → empty regions.
- `TranscriptExporterImplTest.kt` — golden TXT/SRT/VTT/JSON incl. RU text, quoting; schemaVersion=1 in JSON; invariant rejection.
- Room DAO tests (androidTest, in-memory): `VoiceScribeDatabaseTest` (migration 1→2), `TranscriptSegmentFtsDaoTest` (MATCH, case-insensitive, speaker filter), `TranscriptionRepositoryImplTest` (atomic save: job+segments+words+speakers+statistics in one transaction; observe flows; rename speaker keeps id), `ModelDescriptorDaoTest`.

### 7.4 `:engine/src/androidTest/kotlin/...` (JNI/native — §103)
- `SherpaNativeLoadTest.kt` — `System.loadLibrary("sherpa-onnx-jni")` succeeds; double load safe.
- `SherpaWhisperEngineTest.kt` — tiny int8 model: decode fixture → non-empty text + word timestamps (µs, monotonic); language="en" forced; close then re-open; double-close no crash (§5 ptr lifecycle).
- `SherpaVadEngineTest.kt` — fixture speech/silence → regions with expected boundaries; reset/flush behavior.
- `SherpaDiarizationEngineTest.kt` — 2-speaker fixture → 2 clusters; KNOWN_SPEAKER_COUNT=2; mismatch fallback to threshold.
- `SherpaLanguageDetectorTest.kt` — EN and RU fixtures → correct codes; low-confidence fallback logic (unit, JVM).
- `SpeakerAssignMappingTest.kt` (JVM, E8) — word-midpoint containment mapping.
- Concurrency/cancellation (contract §104): `CancellationDuringInferenceTest.kt` — cancel between segments; native resources released (ptr=0 after close).

### 7.5 `:app/src/test/kotlin/...` + `:app/src/androidTest/kotlin/...`
- VM unit tests: `HomeViewModelTest` (media pick → CREATED → service start intent), `JobsViewModelTest` (progress mapping, cancel), `TranscriptDetailViewModelTest` (rename → repo, search query state, export intents), `ModelManagerViewModelTest` (install/pause/delete/switch state, active guard), `SettingsViewModelTest` (persistence).
- `AndroidJobLoggerTest` — line format `[jobId:…] [component] event`; no transcript text in payload; level filtering.
- Instrumented: `MediaProcessingServiceTest` — startService with mediaProcessing type; progress notification updates; cancel action → job `CANCELLED`; service stops; process-restart recovery (job not silently lost, §50).
- Compose UI: `NavigationTest`, `HomeScreenTest` (empty/error states, picker intent), `TranscriptDetailScreenTest` (search highlight, rename, export SAF intent), `ModelManagerScreenTest` (download progress chip).
- `SafExporterInstrumentedTest` — create document URI → stream write → read-back equality.
- End-to-end smoke (device): tiny WAV fixture → job → transcript → export TXT/SRT (Phase 7 baseline).

---

## 8. Phase 4 Acceptance Gate ("Phase 4 done" checklist)

Phase 4 is NOT done until every checkbox is green. Run in M6; each item references its milestone.

**Files & structure**
- [ ] All five modules exist with every file from §3 present and real (no empty bodies, no `TODO`, no debug stubs — contract §121/§122).
- [ ] `settings.gradle.kts` includes exactly `:core:model, :core:domain, :engine, :data, :app`; module build files enforce the §2.1 dependency direction (Gradle prevents cycles).
- [ ] `:core:model` / `:core:domain` have zero Android SDK imports (`android.*` grep on both modules = 0 hits).
- [ ] The old demo AI layer is deleted from the build: `app/src/main/cpp/` (whisper.h, whisper-jni.cpp, CMakeLists.txt), `app/src/main/java/com/k2fsa/sherpa/onnx/SherpaOnnx.kt`, `WhisperEngine*.kt`, `WhisperLib.kt`, `VoiceActivityDetector.kt`, `SpeakerDiarizer.kt`, `SpeechEngineFactory.kt`, fake seeds/HardwareInfo, `DebugSettingsSection.kt`; `metadata.json` no longer claims SERVER_SIDE_GEMINI_API; no Firebase/AppCheck/google-services/secrets in build files or catalog.
- [ ] Grep gates pass on `app/src/main`, `:core:*`, `:data`, `:engine` sources: `TODO|FIXME` = 0; `OfflineStream(1001|fake|Fake|seed|GEMINI|com.google.firebase` = 0 (test mocks allowed in test sources only, §122).

**Build & engine (contract §112 — builds must be run, not assumed)**
- [ ] `./gradlew :core:model:test` and `:core:domain:test` and `:data:testDebugUnitTest` all green (run on this box, memory-safe per ENV_STATUS.md).
- [ ] `./gradlew :engine:assembleDebug` resolves the **real** `com.k2fsa:sherpa-onnx` AAR (version pinned in catalog) and compiles E1–E8 — verified by build output, not by inspection.
- [ ] `./gradlew :app:assembleDebug` assembles (AAR `.so` packaged for arm64-v8a/armeabi-v7a/x86_64).
- [ ] Engine instrumented JNI tests pass on a device/emulator (loadLibrary + tiny-model inference + ptr lifecycle) — recorded with result in the M4 PR.

**Behavioral correctness**
- [ ] `JobState` is the contract §22 canonical 14-state set incl. `READY_FOR_REVIEW` and `EXPORTING`; `JobStateMachine` rejects every invalid transition (unit-tested).
- [ ] All timestamps persisted/serialized as integer µs (Long); seconds appear only at the sherpa-native boundary and at SRT/VTT/TXT serialization (§30).
- [ ] Model install is atomic: `.tmp` download → SHA-256 verify → `ATOMIC_MOVE`; a partial download can never appear installed (§36); active model cannot be deleted (§37); switch keeps previous model on failure (§38).
- [ ] Cancellation propagates job → service scope → use case → engines (per-segment checks); post-cancel state is `CANCELLED` and all native/temp resources are released (§47/§45).
- [ ] Export writes only from the canonical transcript, passes `SegmentValidator`, and JSON carries `schemaVersion=1` (§70–75); exporter golden tests green.
- [ ] Search is FTS-backed on the canonical transcript with speaker filter (§76).
- [ ] No cloud path: `INTERNET` is used only by the model downloader; privacy grep gates pass (§79/§80).

**Reportable**
- [ ] Per-file verification log produced during Phase 4 (each file's step + verification result per §5), attached to the M6 PR for the lead's Module Review (contract §119/§120: file states GENERATED → REVIEWED → VERIFIED only after the lead's module review in Phase 5).

---

## 9. Open Decisions with Recommended Options

These are the only decisions Phase 4 must (re)confirm — everything else is frozen by §1–§8. Each lists the recommended option; the lead ratifies at PR review.

| # | Decision | Recommendation | Rationale |
|---|---|---|---|
| D1 | JobState canonical set | **Contract §22, all 14 states** (Correction C1) | Contract wins (§1.2); the 11-state demo list is REUSE but must gain `READY_FOR_REVIEW` + `EXPORTING`. Non-negotiable per lead brief. |
| D2 | FTS index | **Room `@Fts4` (contentEntity)** with FTS5 upgrade path (Correction C4) | FTS4 safe on minSdk 24; FTS5 only API 30+. Meets §76. |
| D3 | `com.k2fsa:sherpa-onnx` version | **Pin latest stable at Phase 4 step 0** (verify Maven Central; record in catalog + SHERPA_API.md cross-check) | Research flagged Maven Central unreachable from the build box — resolve connectivity first; version must not be invented. |
| D4 | Package/applicationId | **Keep `com.example.*` + `com.aistudio.voicescribe.qkxvpt` for Phase 4**; plan a dedicated rename PR after Phase 4 | Matches ratified PROJECT_MANIFEST §7; avoids churn; applicationId rename would orphan installed data/SAF grants. |
| D5 | Media decode stack | **Framework `MediaExtractor`+`MediaCodec`** (reuse AUDIT AudioProcessor), Media3 optional later | Already proven in demo; no extra dependency; system codecs cover MP3/AAC/M4A/OGG/MP4 (§41). Media3/FFmpeg remain a documented Stage-2 option (PROJECT_MANIFEST §9.3). |
| D6 | whisper.cpp fallback engine | **Not built in Phase 4**; `SpeechEngine` is the replaceable boundary | sherpa-onnx is ratified primary; a fallback is only added via a §117 change request if M4 fails. No stub created (contract §122). |
| D7 | Accelerator backends | **CPU only** (numThreads 4 default → 2 → serial under pressure); GPU/NNAPI/Vulkan frozen Unsupported (PROJECT_MANIFEST §4) | Research: no validated Android GPU path for Whisper ONNX (RESEARCH part 1 §'Ускорение'). Fallback chain satisfies §3.8. |
| D8 | Interrupted-job recovery (§50) | **Phase 4: safe restart** (interrupted active jobs → `FAILED` with preserved partial segments, never silent duplicates); true resume deferred | Contract allows restart when consistency can't be guaranteed; resume-of-chunks is a post-Phase-4 enhancement. |
| D9 | Language fallback on low confidence | **Fallback order `en` → `ru`** (detected outside supported set or confidence too low) | Whisper English is the most robust; RU second per product priority (ARCHITECTURE §4.1). |
| D10 | JSON export schema | **Keep `schemaVersion = 1`** (demo generators are correct per AUDIT) and freeze its formal structure in `R26` KDoc | Demo already uses v1 correctly; §75 requires versioning, not a redesign. |
| D11 | Downloader HTTP client | **OkHttp** (already in catalog), keep demo's redirect/HTML-guard/rollback logic; `Range` resume | Reuses AUDIT §2-quality code; HttpURLConnection replaced for testability (MockWebServer). |
| D12 | WorkManager | **None in Phase 4**; FGS `mediaProcessing` for transcription; passive registry sync/cache cleanup deferred | ARCHITECTURE §11 rejects WorkManager for active transcription; avoids speculative infra (contract §129). |