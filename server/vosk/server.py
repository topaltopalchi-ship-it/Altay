import json
import os
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from vosk import Model, KaldiRecognizer, SetLogLevel

SetLogLevel(-1)
app = FastAPI(title="OBA Persian Vosk Gateway", version="0.1.0")
MODEL_PATH = Path(os.getenv("VOSK_MODEL_PATH", "/opt/vosk-model"))
SAMPLE_RATE = 16000
model = None


@app.on_event("startup")
def load_model():
    global model
    if not MODEL_PATH.exists() or not (MODEL_PATH / "conf").exists():
        raise RuntimeError(f"Vosk model is missing at {MODEL_PATH}")
    model = Model(str(MODEL_PATH))


@app.get("/health")
def health():
    return {"ok": model is not None, "engine": "vosk", "language": "fa-IR"}


@app.websocket("/ws")
async def transcribe(websocket: WebSocket):
    await websocket.accept()
    recognizer = KaldiRecognizer(model, SAMPLE_RATE)
    recognizer.SetWords(False)
    try:
        await websocket.send_json({"type": "ready", "sample_rate": SAMPLE_RATE, "format": "pcm_s16le_mono"})
        while True:
            message = await websocket.receive()
            if message.get("bytes") is not None:
                chunk = message["bytes"]
                if recognizer.AcceptWaveform(chunk):
                    result = json.loads(recognizer.Result())
                    text = (result.get("text") or "").strip()
                    if text:
                        await websocket.send_json({"type": "final", "text": text})
                else:
                    result = json.loads(recognizer.PartialResult())
                    partial = (result.get("partial") or "").strip()
                    if partial:
                        await websocket.send_json({"type": "partial", "text": partial})
            elif message.get("text") is not None:
                command = json.loads(message["text"])
                if command.get("type") == "finish":
                    result = json.loads(recognizer.FinalResult())
                    await websocket.send_json({"type": "final", "text": (result.get("text") or "").strip()})
                    await websocket.close()
                    return
                if command.get("type") == "ping":
                    await websocket.send_json({"type": "pong"})
    except WebSocketDisconnect:
        return
