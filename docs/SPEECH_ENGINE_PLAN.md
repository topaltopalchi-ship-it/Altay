# OBA speech engine implementation plan

This document is the implementation contract for adding Persian speech recognition without changing the existing UI.

## Product decisions

- Keep the current programmatic UI and its layout unchanged.
- Keep online and offline recognition as separate engines; do not generate assistant answers with an LLM.
- Online mode may use Android's configured speech recognition service initially. It requires internet and the provider may process audio remotely.
- Offline mode will use the Persian Vosk model `vosk-model-small-fa-0.42` locally on-device.
- Never download the offline model automatically. Show a clear consent dialog with model size, Wi-Fi/data warning, and cancel option before starting the download.
- Store the model in app-private storage, verify download completion, and allow retry after interruption.
- Do not route audio through Supabase; Supabase is not a speech-recognition runtime.
- Wake-word detection is a separate lifecycle/foreground-service concern. Do not claim reliable locked-screen wake-word support until it is tested on physical Android devices.

## Safe implementation sequence

1. Add the offline model downloader and model-state checks without changing any UI.
2. Add a consent entry point using an existing voice/settings interaction; do not add new visible controls until the current UI screenshot is compared.
3. Add the Vosk recognizer behind a speech-engine interface, keeping Android online recognition as the online implementation.
4. Route the existing voice-command result handling through that interface.
5. Verify Persian recognition with microphone input, offline airplane-mode use, interrupted download/retry, permission denial, and app restart.
6. Test wake-word behavior with the screen locked and document Android/OEM restrictions.
7. Run the Android build and tests; do not publish an APK or mark the work complete before these checks pass.

## Acceptance criteria

- No UI layout changes.
- Offline model download occurs only after explicit user consent.
- After the model is installed, offline recognition works with network disabled.
- Online mode reports a useful error when no online recognition provider is available.
- No LLM-generated answers are added.
- Build/test results and device-test limitations are reported honestly.
