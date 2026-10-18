FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    VOSK_MODEL_PATH=/opt/vosk-model-small-fa-0.42 \
    PORT=8080

WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN mkdir -p /opt && \
    curl -fL --retry 3 https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip -o /tmp/fa-model.zip && \
    unzip -q /tmp/fa-model.zip -d /opt && \
    rm /tmp/fa-model.zip

COPY server.py .
EXPOSE 8080
CMD ["python", "server.py"]
