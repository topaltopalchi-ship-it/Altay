FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VOSK_MODEL_PATH=/opt/vosk-model

RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN mkdir -p /opt && \
    curl -fL --retry 3 https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip -o /tmp/model.zip && \
    unzip -q /tmp/model.zip -d /opt && \
    mv /opt/vosk-model-small-fa-0.42 /opt/vosk-model && \
    rm /tmp/model.zip

COPY server.py .
EXPOSE 10000
CMD ["sh", "-c", "uvicorn server:app --host 0.0.0.0 --port ${PORT:-10000}"]
