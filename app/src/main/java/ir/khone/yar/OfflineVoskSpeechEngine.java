package ir.khone.yar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** On-device Vosk speech recognition. Audio is processed locally by Vosk. */
public final class OfflineVoskSpeechEngine implements RecognitionListener {
    public interface Listener {
        void onStatus(String status);
        void onText(String text);
        void onError(String message);
    }

    // Treat a long pause as the end of the user's complete message, not each sentence.
    private static final long END_OF_MESSAGE_SILENCE_MS = 6000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final StringBuilder collectedText = new StringBuilder();
    private volatile boolean stopped = false;
    private volatile boolean delivered = false;
    private Model model;
    private SpeechService speechService;
    private Listener listener;
    private final Runnable finishAfterSilence = this::deliverCollectedText;

    public void start(Context context, File modelDirectory, Listener callback) {
        stopped = false;
        delivered = false;
        listener = callback;
        synchronized (collectedText) { collectedText.setLength(0); }
        postStatus("در حال آماده‌سازی شنیدن…");
        loader.execute(() -> {
            try {
                if (stopped) return;
                Model loaded = new Model(modelDirectory.getAbsolutePath());
                if (stopped) {
                    loaded.close();
                    return;
                }
                model = loaded;
                Recognizer recognizer = new Recognizer(model, 16000.0f);
                SpeechService service = new SpeechService(recognizer, 16000.0f);
                speechService = service;
                if (stopped) {
                    service.stop();
                    return;
                }
                postStatus("🎙 آماده شنیدن هستم؛ راحت و کامل صحبت کن. بعد از پایان حرفت چند ثانیه مکث کن.");
                service.startListening(this);
            } catch (Throwable e) {
                postError("راه‌اندازی گفتار آفلاین ممکن نشد: " +
                        (e.getMessage() == null ? "خطای مدل یا میکروفون" : e.getMessage()));
                stop();
            }
        });
    }

    public void stop() {
        stopped = true;
        main.removeCallbacks(finishAfterSilence);
        loader.shutdownNow();
        SpeechService service = speechService;
        speechService = null;
        if (service != null) {
            try { service.stop(); } catch (Exception ignored) {}
        }
        Model loaded = model;
        model = null;
        if (loaded != null) {
            try { loaded.close(); } catch (Exception ignored) {}
        }
    }

    @Override public void onPartialResult(String hypothesis) {
        // Intentionally do not show partial hypotheses: they cause the screen to flicker
        // and make fragments look like separate messages.
    }

    @Override public void onResult(String hypothesis) {
        collectResult(hypothesis, "text");
    }

    @Override public void onFinalResult(String hypothesis) {
        collectResult(hypothesis, "text");
    }

    private void collectResult(String json, String key) {
        if (stopped || delivered) return;
        String text = extractText(json, key);
        if (text.isEmpty()) return;

        synchronized (collectedText) {
            String current = collectedText.toString();
            // Vosk may report the same final segment through both result callbacks.
            if (current.length() == 0 || !current.toString().endsWith(text)) {
                if (current.length() > 0) collectedText.append(' ');
                collectedText.append(text);
            }
        }
        main.removeCallbacks(finishAfterSilence);
        main.postDelayed(finishAfterSilence, END_OF_MESSAGE_SILENCE_MS);
    }

    private void deliverCollectedText() {
        if (stopped || delivered) return;
        String text;
        synchronized (collectedText) { text = collectedText.toString().trim(); }
        if (text.isEmpty()) return;
        delivered = true;
        Listener callback = listener;
        if (callback != null) callback.onText(text);
        stop();
    }

    @Override public void onError(Exception e) {
        if (stopped) return;
        postError("خطای تشخیص گفتار آفلاین: " +
                (e == null || e.getMessage() == null ? "دوباره تلاش کن." : e.getMessage()));
        stop();
    }

    @Override public void onTimeout() {
        if (stopped || delivered) return;
        synchronized (collectedText) {
            if (collectedText.length() > 0) {
                main.removeCallbacks(finishAfterSilence);
                main.post(finishAfterSilence);
                return;
            }
        }
        // No speech yet: keep the same model/service ready instead of asking the user
        // to tap the microphone again after a short initial wait.
        main.postDelayed(() -> {
            SpeechService service = speechService;
            if (!stopped && !delivered && service != null) {
                try { service.startListening(this); }
                catch (Throwable e) {
                    postError("میکروفون آماده نشد؛ دوباره دکمهٔ صوتی را بزن.");
                    stop();
                }
            }
        }, 400L);
    }

    private String extractText(String json, String key) {
        try { return new JSONObject(json).optString(key, "").trim(); }
        catch (Exception ignored) { return ""; }
    }

    private void postStatus(String message) {
        Listener callback = listener;
        if (callback != null) main.post(() -> { if (!stopped) callback.onStatus(message); });
    }

    private void postError(String message) {
        Listener callback = listener;
        if (callback != null) main.post(() -> callback.onError(message));
    }
}
