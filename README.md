# MMS Swahili TTS Engine

`MmsSwahiliTtsEngine` is a standalone Android `TextToSpeechService` that bundles an
offline ONNX conversion of Meta's `facebook/mms-tts-swh` VITS checkpoint. It exposes
Swahili (`sw`) through Android's standard `TextToSpeech` API; apps such as Survey2026
need no source changes to use it once Android selects this engine.

## Build

```bash
./gradlew :app:assembleDebug --no-daemon
```

The resulting debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime

- Android API 26+, arm64 target device validated in scope: Samsung Galaxy S25 / Android 16.
- `onnxruntime-android` runs the bundled VITS graph locally in this app's process.
- The model is copied from the APK to app-private storage once, checksum verified, then loaded.
- No `INTERNET` permission is declared. Text and audio remain in the engine process.
- Speech is mono 16-bit PCM at 16,000 Hz.
- Input is normalized to the model's 39-character vocabulary and split into 180-character
  chunks to bound synthesis work. Unsupported punctuation and characters become whitespace.

The VITS duration predictor is stochastic. Output waveforms are not expected to be bit-identical
between runs; the conversion validation checks model structure, sample rate, valid waveform
statistics, duration, and successful ONNX Runtime execution against a seeded PyTorch reference.

## Android TTS integration

Install the APK, then select **MMS Swahili TTS** in Android Settings → Text-to-speech output.
The service advertises `sw`, provides voice `mms-swh-vits`, streams buffers bounded by
`SynthesisCallback.getMaxBufferSize()`, and stops audio emission as soon as `onStop()` is called.
Native ONNX inference already in progress cannot be forcibly preempted by the Java API; requests
are serialized and stale output is discarded after Stop.

## Attribution and distribution gate

The source checkpoint is Meta's `facebook/mms-tts-swh`, commit
`c66a113bf598cd86fe1dfd3c0b5b56d4caa4e6f5`, licensed **CC BY-NC 4.0**. This license prohibits
commercial use. Do not distribute this APK commercially or through a commercial channel without
separate rights. See [docs/MODEL_PROVENANCE.md](docs/MODEL_PROVENANCE.md).

This project does not claim a quality advantage over any other engine. A controlled listening
comparison is required before making a voice-quality claim.

## Debug APK distribution

The manual GitHub Actions workflow builds a verified debug APK and publishes a unique GitHub
prerelease containing the APK, SHA-256 sidecar, and a QR PNG that links directly to the APK.
See [docs/RELEASE_AUTOMATION.md](docs/RELEASE_AUTOMATION.md) for triggering and distribution
restrictions.
