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

    private final Handler main = new Handler(Looper.getMainLooper());
    // Keep the heavy Vosk model in memory between utterances to avoid reloading it on every tap.
    private static volatile Model sharedModel;
    private static final Object MODEL_LOCK = new Object();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private volatile boolean stopped = false;
    private volatile boolean delivered = false;
    private Model model;
    private SpeechService speechService;
    private Listener listener;

    public void start(Context context, File modelDirectory, Listener callback) {
        stopped = false;
        delivered = false;
        listener = callback;
        postStatus("مدل Vosk دانلود شده؛ در حال بارگذاری فایل‌های مدل در حافظه…");
        loader.execute(() -> {
            try {
                if (stopped) return;
                Model loaded = sharedModel;
                if (loaded == null) {
                    postStatus("بار اول است؛ مدل گفتار فارسی را در حافظه آماده می‌کنم. ممکن است چند لحظه طول بکشد…");
                    synchronized (MODEL_LOCK) {
                        loaded = sharedModel;
                        if (loaded == null) {
                            loaded = new Model(modelDirectory.getAbsolutePath());
                            sharedModel = loaded;
                        }
                    }
                } else {
                    postStatus("مدل گفتار آماده است؛ میکروفون را روشن می‌کنم…");
                }
                if (stopped) return;
                model = loaded;
                Recognizer recognizer = new Recognizer(loaded, 16000.0f);
                SpeechService service = new SpeechService(recognizer, 16000.0f);
                speechService = service;
                if (stopped) {
                    service.stop();
                    return;
                }
                postStatus("🎙 حالت آفلاین فعال است؛ صحبت کن و بعد مکث کن.");
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
        loader.shutdownNow();
        SpeechService service = speechService;
        speechService = null;
        if (service != null) {
            try { service.stop(); } catch (Exception ignored) {}
        }
        // Keep sharedModel alive for the next voice command; closing it here forces a slow reload.
        model = null;
    }

    @Override public void onPartialResult(String hypothesis) {
        if (stopped) return;
        String text = extractText(hypothesis, "partial");
        if (!text.isEmpty()) postStatus("شنیدم: " + text);
    }

    @Override public void onResult(String hypothesis) {
        deliverResult(hypothesis);
    }

    @Override public void onFinalResult(String hypothesis) {
        deliverResult(hypothesis);
    }

    private void deliverResult(String json) {
        if (stopped || delivered) return;
        String text = extractText(json, "text");
        if (text.isEmpty()) return;
        delivered = true;
        Listener callback = listener;
        if (callback != null) main.post(() -> {
            callback.onText(text);
        });
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
        postError("صدایی تشخیص داده نشد؛ دوباره تلاش کن.");
        stop();
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
