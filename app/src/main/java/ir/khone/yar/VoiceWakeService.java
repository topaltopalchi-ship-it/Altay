package ir.khone.yar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceWakeService extends Service {
    public static final String ACTION_START = "ir.khone.yar.WAKE_START";
    public static final String ACTION_STOP = "ir.khone.yar.WAKE_STOP";
    private static final String CHANNEL_ID = "khoneyar_wake_channel";
    private static final int NOTIFICATION_ID = 7102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean stopping = false;
    private boolean speaking = false;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        try { createChannel(); } catch (Throwable ignored) {}
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OBA:VoiceWakeService");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Throwable ignored) { wakeLock = null; }
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    try {
                        tts.setLanguage(new Locale("fa", "IR"));
                        tts.setSpeechRate(0.92f);
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override public void onStart(String id) {}
                            @Override public void onDone(String id) {
                                handler.post(() -> {
                                    speaking = false;
                                    if (!stopping) handler.postDelayed(() -> beginListening(), 700);
                                });
                            }
                            @Override public void onError(String id) {
                                handler.post(() -> {
                                    speaking = false;
                                    if (!stopping) handler.postDelayed(() -> beginListening(), 700);
                                });
                            }
                        });
                    } catch (Throwable ignored) { tts = null; }
                }
            });
        } catch (Throwable ignored) { tts = null; }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopping = true;
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        try {
            // Android 14+ can reject microphone foreground services when runtime
            // permission or while-in-use eligibility is missing. Never let that
            // exception terminate the app process.
            startForeground(NOTIFICATION_ID, buildNotification("در حال آماده‌سازی شنیدن نام اوبا…"));
        } catch (Throwable e) {
            stopping = true;
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            try { wakeLock.acquire(); } catch (Exception ignored) {}
        }
        handler.post(() -> {
            if (!stopping) beginListening();
        });
        return START_STICKY;
    }

    private void beginListening() {
        if (stopping || speaking) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("تشخیص گفتار در دسترس نیست؛ سرویس گفتار فارسی را بررسی کن");
            handler.postDelayed(() -> beginListening(), 5000);
            return;
        }
        try {
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) {
                    updateNotification("در حال شنیدن؛ بگو «اوبا»");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    if (!stopping && !speaking) handler.postDelayed(() -> beginListening(), 900);
                }
                @Override public void onResults(android.os.Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    boolean wake = false;
                    if (matches != null) {
                        for (String phrase : matches) {
                            String normalized = normalize(phrase);
                            if (normalized.contains("اوبا") || normalized.contains("oba") ||
                                normalized.contains("خونهیار") || normalized.contains("خانیار") ||
                                normalized.contains("خونهیار") || normalized.contains("خونیار")) {
                                wake = true;
                                break;
                            }
                        }
                    }
                    if (wake) {
                        updateNotification("نام اوبا شنیده شد؛ میکروفون فرمان فعال می‌شود");
                        Intent launch = new Intent(VoiceWakeService.this, MainActivity.class);
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        launch.putExtra("ir.khone.yar.START_VOICE_AFTER_WAKE", true);
                        // Release the wake-word microphone before MainActivity starts command capture.
                        stopping = true;
                        handler.removeCallbacksAndMessages(null);
                        try { startActivity(launch); } catch (Exception ignored) {}
                        stopSelf();
                    } else if (!stopping) {
                        handler.postDelayed(() -> beginListening(), 650);
                    }
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) {}
                @Override public void onEvent(int eventType, android.os.Bundle params) {}
            });
            Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR");
            speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            // Prefer the installed offline speech service when the phone supports it.
            // This is a preference, not a guarantee; some Android providers ignore it.
            speech.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizer.startListening(speech);
        } catch (Throwable e) {
            // Speech service providers and native recognizers can throw runtime
            // or linkage errors on some devices. Keep the service alive and retry.
            if (!stopping) handler.postDelayed(() -> beginListening(), 2500);
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        // Normalize common Persian/Arabic variants and remove marks/punctuation so
        // "او با", "اُبا", and "OBA!" can all match the same wake word.
        return text.toLowerCase(Locale.ROOT)
            .replace("‌", "").replace(" ", "")
            .replace("ئ", "ی").replace("ك", "ک").replace("ي", "ی")
            .replace("آ", "ا").replace("أ", "ا").replace("إ", "ا")
            .replace("ؤ", "و").replace("ة", "ه")
            .replaceAll("[\\u064B-\\u065F\\u0670]", "")
            .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "شنیدن نام OBA", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان دائمی برای سرویس شنیدن نام OBA");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 7103, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("OBA")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true);
        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && !stopping) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
        wakeLock = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
