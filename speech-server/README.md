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

## Android integration

This service is only the server side. OBA's Android client must explicitly connect to its `wss://.../ws` endpoint, stream PCM audio, parse the JSON results, and pass recognized text to the existing voice-command handler. That client integration must be implemented and tested separately; adding this folder alone does not connect the app.
