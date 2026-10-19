# OBA Persian speech server

A small WebSocket speech-to-text service for OBA using Vosk and the official small Persian model. It performs speech recognition only; it does not generate LLM responses.

## Endpoints

- `GET /health` — health check.
- `GET /ws` — WebSocket streaming endpoint. Send 16 kHz, mono, signed 16-bit PCM audio as binary frames. Send `{"config":{"sample_rate":16000}}` first if desired. The server returns Vosk JSON partial/final results.

The WebSocket requires `Authorization: Bearer <OBA_SPEECH_TOKEN>`. Set a long random token in the hosting provider's environment variables. Do not commit secrets to Git.

## Local run

Build and run the Docker image:

```sh
docker build -t oba-speech-server .
docker run --rm -p 8080:8080 -e OBA_SPEECH_TOKEN='replace-with-a-long-random-secret' oba-speech-server
```

Then check `http://localhost:8080/health`.

## Hosting notes

Deploy this directory as a Docker web service on a provider that supports persistent processes and WebSockets. The Docker build downloads the official Persian Vosk model. Free hosting quotas and sleep/cold-start behavior depend on the provider and can change.


## Try a free Koyeb deployment

Koyeb documents a free instance with 512 MB RAM and 0.1 vCPU, and supports Docker WebSocket services. That is a very small resource budget for speech recognition, so this is an experiment—not a performance guarantee. If the model fails to load or requests time out, do not upgrade or add billing unless you explicitly choose to.

1. Sign in to [Koyeb](https://app.koyeb.com/) and choose **Create App**.
2. Choose **Web Service** with **GitHub** as the source and connect `topaltopalchi-ship-it/Altay`.
3. Select branch `feature/oba-speech-engines`.
4. Set the **work directory / root directory** to `speech-server` so Koyeb uses this folder's Dockerfile.
5. Select the free instance only. Before deploying, check the displayed price is zero.
6. Add environment variable `OBA_SPEECH_TOKEN` with a long random secret. Keep it in Koyeb's secret/environment settings, not in GitHub.
7. Deploy and wait for the build to finish. The build downloads the Persian model, so it may take several minutes.
8. Open the service's public URL with `/health`. A healthy response should contain `"ok": true`. Use `wss://YOUR-HOST/ws` for the WebSocket endpoint; never use plain `ws://` from the Android app.

If Koyeb does not offer a zero-cost instance for your account/region, stop before confirming any paid option. Share the deployment error or the service URL (never share the token) to continue troubleshooting.

## Important limitations

- This does not deploy itself: a user must authorize Koyeb to access the GitHub repository and create the service.
- Free instance availability and policies can change. The Koyeb free tier documented at the time of writing is 512 MB RAM / 0.1 vCPU, which may be too constrained for Vosk.
- A successful server deployment still does not connect the Android app. Android must implement an authenticated TLS WebSocket client and stream microphone PCM while preserving the existing UI.
- Do not send financial records or other app data to this speech service; it is intended to receive audio only.

## Android integration

This service is only the server side. OBA's Android client must explicitly connect to its `wss://.../ws` endpoint, stream PCM audio, parse the JSON results, and pass recognized text to the existing voice-command handler. That client integration must be implemented and tested separately; adding this folder alone does not connect the app.
