import json
import os
import secrets
from pathlib import Path

from aiohttp import web, WSMsgType
from vosk import Model, KaldiRecognizer, SetLogLevel

MODEL_PATH = Path(os.environ.get("VOSK_MODEL_PATH", "/opt/vosk-model-small-fa-0.42"))
TOKEN = os.environ.get("OBA_SPEECH_TOKEN", "")
PORT = int(os.environ.get("PORT", "8080"))

SetLogLevel(-1)
model = None


async def health(_request):
    return web.json_response({"ok": model is not None, "service": "oba-persian-stt"})


async def websocket_handler(request):
    if not TOKEN:
        return web.json_response({"error": "server token is not configured"}, status=503)

    auth = request.headers.get("Authorization", "")
    expected = "Bearer " + TOKEN
    if not secrets.compare_digest(auth, expected):
        return web.json_response({"error": "unauthorized"}, status=401)

    ws = web.WebSocketResponse(max_msg_size=1024 * 1024, heartbeat=25)
    await ws.prepare(request)

    recognizer = KaldiRecognizer(model, 16000)
    recognizer.SetWords(False)

    try:
        async for msg in ws:
            if msg.type == WSMsgType.BINARY:
                if recognizer.AcceptWaveform(msg.data):
                    await ws.send_str(recognizer.Result())
                else:
                    await ws.send_str(recognizer.PartialResult())
            elif msg.type == WSMsgType.TEXT:
                try:
                    payload = json.loads(msg.data)
                except json.JSONDecodeError:
                    await ws.send_json({"error": "invalid_json"})
                    continue
                if payload.get("command") == "reset":
                    recognizer = KaldiRecognizer(model, 16000)
                    recognizer.SetWords(False)
                    await ws.send_json({"reset": True})
                elif payload.get("command") == "finish":
                    await ws.send_str(recognizer.FinalResult())
                    break
                else:
                    await ws.send_json({"ready": True, "sample_rate": 16000, "format": "pcm_s16le_mono"})
            elif msg.type in (WSMsgType.ERROR, WSMsgType.CLOSE, WSMsgType.CLOSED):
                break
    finally:
        if not ws.closed:
            await ws.close()
    return ws


async def on_startup(_app):
    global model
    if not MODEL_PATH.is_dir():
        raise RuntimeError(f"Vosk model directory missing: {MODEL_PATH}")
    model = Model(str(MODEL_PATH))


app = web.Application(client_max_size=1024 * 1024)
app.router.add_get("/health", health)
app.router.add_get("/ws", websocket_handler)
app.on_startup.append(on_startup)

if __name__ == "__main__":
    web.run_app(app, host="0.0.0.0", port=PORT)
