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
    private OfflinePersianTts offlineTts;
    private boolean stopping = false;
    private boolean speaking = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        offlineTts = new OfflinePersianTts(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int language = tts.setLanguage(new Locale("fa", "IR"));
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
        handler.post(() -> beginListening());
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
                        updateNotification("نام OBA شنیده شد؛ برنامه باز می‌شود");
                        Intent launch = new Intent(VoiceWakeService.this, MainActivity.class);
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try { startActivity(launch); } catch (Exception ignored) {}
                        speaking = true;
                        Runnable fallback = () -> {
                            if (tts != null) {
                                tts.speak("جانا", TextToSpeech.QUEUE_FLUSH, null, "khoneyar_wake_reply");
                            } else if (!stopping) {
                                speaking = false;
                                handler.postDelayed(() -> beginListening(), 1200);
                            }
                        };
                        if (offlineTts != null) {
                            offlineTts.speak("جانا", fallback, () -> {
                                if (!stopping) {
                                    speaking = false;
                                    handler.postDelayed(() -> beginListening(), 700);
                                }
                            });
                        } else {
                            fallback.run();
                        }
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
            speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizer.startListening(speech);
        } catch (Exception e) {
            handler.postDelayed(() -> beginListening(), 1800);
        }
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('‌', ' ').replace(" ", "")
            .replace("‌", "").replace("ئ", "ی").replace("ك", "ک").replace("ي", "ی")
            .replace("آ", "ا").replace("أ", "ا").replace("إ", "ا")
            .replace("ه", "ه");
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
        if (offlineTts != null) {
            offlineTts.shutdown();
            offlineTts = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
