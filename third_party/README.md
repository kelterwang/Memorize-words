# Speech dependencies

- sherpa-onnx 1.13.7: https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.7 (Apache-2.0, see sherpa-onnx-LICENSE). Official static-link ONNX Runtime Android AAR is vendored in app/libs; includes native libraries for Android ABIs and Kotlin API classes.
- Kokoro v1.0: https://huggingface.co/hexgrad/Kokoro-82M (Apache-2.0, see Kokoro-LICENSE). Converted model and voice table: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models . Included as compressed asset parts in the full APK and optionally as a separate ZIP.
- eSpeak NG phonemizer and data: https://github.com/espeak-ng/espeak-ng (GPL-3.0, see espeak-ng-COPYING). Used by the Kokoro frontend; data and license are included in the separate ZIP. The sherpa-onnx source above contains build integration for the native phonemizer.
- ONNX Runtime: https://github.com/microsoft/onnxruntime (MIT). Included in the upstream AAR.

Keep these notices and upstream license texts when redistributing dependencies. The upstream projects' licenses apply to their respective code, model and data; Apache licensing of Kokoro does not replace eSpeak NG's license.

Pinned AAR SHA-256: `220d7cb25ac6e57ec34082bc2551ebd7ec7d4d96cbfea520839e208f92347837`.
