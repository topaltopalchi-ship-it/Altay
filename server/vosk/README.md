# OBA Persian Vosk gateway (experimental)

This service provides Persian speech-to-text over WebSocket. It does not generate answers and does not use a token-based AI API.

## Protocol
- `GET /health` returns service/model status.
- Connect to `wss://YOUR-HOST/ws`.
- The server sends `{"type":"ready","sample_rate":16000,"format":"pcm_s16le_mono"}`.
- Send binary audio chunks as **16 kHz, mono, signed 16-bit little-endian PCM**.
- Receive JSON messages with `type=partial` and `type=final`.
- Send `{"type":"finish"}` to finalize the current utterance.

## Deploy on Render free
1. Create a new Web Service from this GitHub repository.
2. Select the `server/vosk` directory's Dockerfile (or use the root `render.yaml` after moving it to the repository root).
3. Choose the Free plan and deploy.
4. Check `/health`; it should return `{"ok":true,...}`.

Important: free instances can sleep and have limited RAM. This small Persian model is about 53 MB on disk but needs considerably more memory at runtime. Treat this as a prototype, not a production SLA.

## App integration status
The service protocol is prepared, but the Android app does **not yet stream microphone PCM to this WebSocket endpoint**. Do not configure the app to use this service until the Android client is implemented and the endpoint has been deployed and tested.
