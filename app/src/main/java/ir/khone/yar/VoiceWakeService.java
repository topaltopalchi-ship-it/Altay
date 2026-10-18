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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceWakeService extends Service {
    public static final String ACTION_START = "ir.khone.yar.WAKE_START";
    public static final String ACTION_STOP = "ir.khone.yar.WAKE_STOP";
    private static final String CHANNEL_ID = "khoneyar_wake_channel";
    private static final int NOTIFICATION_ID = 7102;
    private static final String OFFLINE_MODEL_NAME = "vosk-model-small-fa-0.42";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer onlineRecognizer;
    private Model offlineModel;
    private SpeechService offlineSpeech;
    private TextToSpeech tts;
    private boolean stopping = false;
    private boolean speaking = false;
    private boolean usingOffline = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
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
            }
        });
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
        startForeground(NOTIFICATION_ID, buildNotification("فعال؛ بگو «اوبا»"));
        handler.post(this::beginListening);
        return START_STICKY;
    }

    private void beginListening() {
        if (stopping || speaking) return;
        closeRecognizers();

        File modelDir = new File(getFilesDir(), OFFLINE_MODEL_NAME);
        if (new File(modelDir, "conf").isDirectory() && new File(modelDir, "am").isDirectory()) {
            try {
                if (offlineModel == null) offlineModel = new Model(modelDir.getAbsolutePath());
                Recognizer recognizer = new Recognizer(offlineModel, 16000.0f);
                offlineSpeech = new SpeechService(recognizer, 16000.0f);
                usingOffline = true;
                updateNotification("شنیدن آفلاین فعال است؛ بگو «اوبا»");
                offlineSpeech.startListening(new org.vosk.android.RecognitionListener() {
                    @Override public void onPartialResult(String hypothesis) {}
                    @Override public void onResult(String result) { checkWakePhrase(readText(result)); }
                    @Override public void onFinalResult(String result) { checkWakePhrase(readText(result)); }
                    @Override public void onError(Exception e) {
                        if (!stopping && !speaking) handler.postDelayed(() -> beginListening(), 1200);
                    }
                    @Override public void onTimeout() {
                        if (!stopping && !speaking) handler.postDelayed(() -> beginListening(), 400);
                    }
                });
                return;
            } catch (Exception e) {
                closeRecognizers();
                updateNotification("خطا در مدل آفلاین؛ تلاش برای تشخیص آنلاین");
            }
        } else {
            usingOffline = false;
            updateNotification("مدل آفلاین نصب نیست؛ تشخیص آنلاین");
        }
        beginOnlineListening();
    }

    private void beginOnlineListening() {
        if (stopping || speaking) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("تشخیص گفتار در دسترس نیست؛ مدل آفلاین را در OBA نصب کن");
            handler.postDelayed(this::beginListening, 5000);
            return;
        }
        try {
            if (onlineRecognizer != null) {
                onlineRecognizer.destroy();
                onlineRecognizer = null;
            }
            onlineRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            onlineRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) {
                    updateNotification("در حال شنیدن آنلاین؛ بگو «اوبا»");
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
                    if (matches != null) {
                        for (String phrase : matches) {
                            if (isWakePhrase(phrase)) {
                                respondToWake();
                                return;
                            }
                        }
                    }
                    if (!stopping) handler.postDelayed(() -> beginListening(), 650);
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) {}
                @Override public void onEvent(int eventType, android.os.Bundle params) {}
            });
            Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR");
            speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            onlineRecognizer.startListening(speech);
        } catch (Exception e) {
            handler.postDelayed(this::beginListening, 1800);
        }
    }

    private String readText(String json) {
        try {
            return new JSONObject(json).optString("text", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isWakePhrase(String phrase) {
        String normalized = normalize(phrase);
        return normalized.contains("اوبا") || normalized.contains("oba")
            || normalized.contains("خونهیار") || normalized.contains("خانیار")
            || normalized.contains("خونیار");
    }

    private void checkWakePhrase(String phrase) {
        if (stopping || speaking || phrase == null || phrase.trim().isEmpty()) return;
        if (isWakePhrase(phrase)) {
            respondToWake();
        }
    }

    private void respondToWake() {
        if (stopping || speaking) return;
        speaking = true;
        updateNotification("نام OBA شنیده شد؛ در حال بازکردن برنامه");
        // Android may restrict opening an Activity from the background while locked.
        try {
            Intent openApp = new Intent(this, MainActivity.class);
            openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(openApp);
        } catch (Exception ignored) {}

        if (tts != null) {
            tts.speak("در خدمتم", TextToSpeech.QUEUE_FLUSH, null, "khoneyar_wake_reply");
        } else {
            speaking = false;
            handler.postDelayed(this::beginListening, 700);
        }
    }

    private void closeRecognizers() {
        if (offlineSpeech != null) {
            try { offlineSpeech.stop(); } catch (Exception ignored) {}
            try { offlineSpeech.shutdown(); } catch (Exception ignored) {}
            offlineSpeech = null;
        }
        if (onlineRecognizer != null) {
            try { onlineRecognizer.cancel(); } catch (Exception ignored) {}
            try { onlineRecognizer.destroy(); } catch (Exception ignored) {}
            onlineRecognizer = null;
        }
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('‌', ' ').replace(" ", "")
            .replace("ئ", "ی").replace("ك", "ک").replace("ي", "ی");
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
        closeRecognizers();
        if (offlineModel != null) {
            try { offlineModel.close(); } catch (Exception ignored) {}
            offlineModel = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}