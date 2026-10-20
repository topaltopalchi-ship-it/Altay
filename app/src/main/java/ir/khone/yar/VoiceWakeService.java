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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.Locale;

public class VoiceWakeService extends Service {
    public static final String ACTION_START = "ir.khone.yar.WAKE_START";
    public static final String ACTION_WAKE_DETECTED = "ir.khone.yar.WAKE_DETECTED";
    public static final String ACTION_STOP = "ir.khone.yar.WAKE_STOP";
    private static final String CHANNEL_ID = "khoneyar_wake_channel";
    private static final int NOTIFICATION_ID = 7102;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private OfflineVoskSpeechEngine engine;
    private TextToSpeech tts;
    private boolean stopping = false;
    private boolean speaking = false;
    private boolean preparingModel = false;
    private boolean wakeDetected = false;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OBA:VoiceWakeService");
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
                            @Override public void onDone(String id) { resumeAfterSpeech(); }
                            @Override public void onError(String id) { resumeAfterSpeech(); }
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
            startForeground(NOTIFICATION_ID, buildNotification("در حال آماده‌سازی شنیدن آفلاین…"));
        } catch (Throwable e) {
            stopping = true;
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            try { wakeLock.acquire(); } catch (Exception ignored) {}
        }
        handler.post(this::prepareAndListen);
        return START_STICKY;
    }

    private void prepareAndListen() {
        if (stopping || speaking || preparingModel || engine != null) return;
        File model = OfflineModelDownloader.getInstalledModelDirectory(this);
        if (model == null) {
            preparingModel = true;
            updateNotification("مدل گفتار آفلاین در حال دریافت است…");
            OfflineModelDownloader.downloadModel(this, new OfflineModelDownloader.Callback() {
                @Override public void onProgress(long downloaded, long total) {
                    if (stopping) return;
                    String progress = total > 0
                        ? "دریافت مدل گفتار: " + (downloaded * 100 / total) + "%"
                        : "در حال دریافت مدل گفتار آفلاین…";
                    updateNotification(progress);
                }
                @Override public void onInstalled(File directory) {
                    preparingModel = false;
                    if (!stopping) startVosk(directory);
                }
                @Override public void onError(String message) {
                    preparingModel = false;
                    updateNotification("مدل آفلاین آماده نیست؛ برنامه را باز کن و اینترنت را بررسی کن");
                    if (!stopping) handler.postDelayed(VoiceWakeService.this::prepareAndListen, 15000);
                }
            });
            return;
        }
        startVosk(model);
    }

    private void startVosk(File modelDirectory) {
        if (stopping || speaking || engine != null) return;
        updateNotification("در حال راه‌اندازی Vosk آفلاین…");
        wakeDetected = false;
        OfflineVoskSpeechEngine next = new OfflineVoskSpeechEngine();
        engine = next;
        next.start(getApplicationContext(), modelDirectory, new OfflineVoskSpeechEngine.Listener() {
            @Override public void onStatus(String status) {
                if (!stopping) updateNotification(status);
            }
            @Override public void onPartialText(String text) {
                if (stopping || wakeDetected) return;
                if (isWakeWord(text)) {
                    wakeDetected = true;
                    if (engine == next) engine = null;
                    next.stop();
                    handleWakeWord();
                }
            }
            @Override public void onText(String text) {
                if (engine == next) engine = null;
                if (stopping || wakeDetected) return;
                if (isWakeWord(text)) {
                    wakeDetected = true;
                    handleWakeWord();
                } else {
                    updateNotification("در حال شنیدن؛ بگو «اوبا»");
                    handler.postDelayed(VoiceWakeService.this::prepareAndListen, 350);
                }
            }
            @Override public void onError(String message) {
                if (engine == next) engine = null;
                if (stopping) return;
                updateNotification("خطای شنیدن آفلاین؛ تلاش دوباره…");
                handler.postDelayed(VoiceWakeService.this::prepareAndListen, 1800);
            }
        });
    }

    private boolean isWakeWord(String text) {
        String normalized = normalize(text);
        return normalized.contains("اوبا") || normalized.contains("oba")
            || normalized.contains("خونهیار") || normalized.contains("خانیار")
            || normalized.contains("خونیار");
    }

    private void handleWakeWord() {
        if (stopping) return;
        updateNotification("کلمهٔ اوبا شنیده شد؛ در حال باز کردن برنامه…");
        Intent launch = new Intent(VoiceWakeService.this, MainActivity.class);
        launch.setAction(ACTION_WAKE_DETECTED);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(launch); } catch (Throwable e) {
            updateNotification("اوبا شنیده شد، اما بازکردن صفحه مسدود شد؛ اعلان را لمس کن.");
        }
        speaking = true;
        if (tts != null) {
            tts.speak("جانم", TextToSpeech.QUEUE_FLUSH, null, "oba_wake_reply");
        } else {
            speaking = false;
            handler.postDelayed(VoiceWakeService.this::prepareAndListen, 1200);
        }
    }

    private void resumeAfterSpeech() {
        handler.post(() -> {
            speaking = false;
            if (!stopping) {
                updateNotification("در حال شنیدن؛ بگو «اوبا»");
                handler.postDelayed(this::prepareAndListen, 700);
            }
        });
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace(" ", "").replace("‌", "")
            .replace("ئ", "ی").replace("ك", "ک").replace("ي", "ی")
            .replace("آ", "ا").replace("أ", "ا").replace("إ", "ا");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "شنیدن نام OBA", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان دائمی سرویس شنیدن آفلاین نام OBA");
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
            .setContentTitle("OBA").setContentText(text).setContentIntent(pending)
            .setOngoing(true).setOnlyAlertOnce(true);
        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && !stopping) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        if (engine != null) {
            engine.stop();
            engine = null;
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
