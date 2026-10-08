# Model and conversion provenance

## Source model

- Model: `facebook/mms-tts-swh` (Swahili MMS-TTS)
- Source revision: `c66a113bf598cd86fe1dfd3c0b5b56d4caa4e6f5`
- Source: https://huggingface.co/facebook/mms-tts-swh
- Architecture: single-speaker VITS, stochastic duration predictor, 39-token character vocabulary,
  16,000 Hz waveform output.
- Source `model.safetensors` SHA-256:
  `c830aa67ab9199c036e92274ce6b3a31ddf1fa9434b66b70dd485f81b87dc2f8`
- Source `config.json` SHA-256:
  `8695749d49be938a0d4cd233a9a22f554064de7cae6c41cf48f0317bd4b86686`
- Source `vocab.json` SHA-256:
  `c6cf8098e45c6c94a2ad0afc2814e3a8fc446383fb0005ecef8df5d3eb09efb0`
- License: CC BY-NC 4.0 (non-commercial); this is a release/distribution gate.

## Bundled ONNX conversion

- Conversion source: `willwade/mms-tts-multilingual-models-onnx`, Hub revision
  `4f49fc254f11ddb0e35fbf25b6e48b04463a6779`, path `swh/model.onnx`.
- Converter source: https://github.com/willwade/mms-tts-multilingual-models-onnx (MIT).
- ONNX SHA-256:
  `af4f2e2174960af06a7a7d07810ea7eb2d78fba827be7690abb230159717d250`
- ONNX size: 114,017,796 bytes (109 MiB).
- ONNX opset: 13. Inputs: int64 character IDs/length plus `noise_scale`, `length_scale`, and
  `noise_scale_w`; output: float waveform. The graph includes dynamic `Range`/shape operations.

The conversion is used unchanged. MMS VITS uses stochastic duration prediction, so bit-exact
waveform comparison with PyTorch is not an appropriate acceptance test unless both random sources
are controlled. Validation instead records successful source-PyTorch and ONNX runs on identical
token IDs, both producing finite 16 kHz audio with comparable duration and amplitude.

## Runtime

- `com.microsoft.onnxruntime:onnxruntime-android:1.23.2`
- CPU execution provider initially. NNAPI/XNNPACK acceleration has not been selected because the
  dynamic VITS graph must first be measured on the target phone and operator partitioning may vary.
- No network permission or remote inference path is included.

## Required notices

Retain Meta/MMS attribution, this provenance record, the upstream model license, and the
conversion attribution in any APK distribution. The original MMS paper is Pratap et al.,
"Scaling Speech Technology to 1,000+ Languages," arXiv:2305.13516 (2023).
