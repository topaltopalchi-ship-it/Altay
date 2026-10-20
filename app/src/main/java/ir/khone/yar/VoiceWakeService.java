package ir.khone.yar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VoiceWakeService extends Service {
    public static final String ACTION_START = "ir.khone.yar.WAKE_START";
    public static final String ACTION_STOP = "ir.khone.yar.WAKE_STOP";
    public static final String ACTION_PAUSE = "ir.khone.yar.WAKE_PAUSE";
    private static final String CHANNEL_ID = "khoneyar_wake_channel";
    private static final int NOTIFICATION_ID = 7102;
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.5.zip";
    private static final String MODEL_DIR_NAME = "vosk-model-fa";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile boolean stopping = false;
    private volatile boolean paused = false;
    private volatile boolean listening = false;
    private volatile boolean wakeHandled = false;
    private Thread worker;
    private AudioRecord audioRecord;
    private Model model;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopping = true;
            stopCapture();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        startForeground(NOTIFICATION_ID, buildNotification("آماده‌سازی تشخیص آفلاین اوبا"));
        if (ACTION_PAUSE.equals(action)) {
            paused = true;
            stopCapture();
            updateNotification("مکث هنگام استفاده از برنامه");
            return START_STICKY;
        }

        paused = false;
        ensureWorkerAfterPause();
        return START_STICKY;
    }

    private void ensureWorkerAfterPause() {
        handler.postDelayed(() -> {
            if (stopping || paused || listening) return;
            if (worker != null && worker.isAlive()) {
                ensureWorkerAfterPause();
                return;
            }
            worker = new Thread(() -> {
                if (model == null) prepareAndListen();
                else listenAgain();
            }, "OBA-Vosk-Wake");
            worker.start();
        }, 500);
    }

    private void prepareAndListen() {
        try {
            File modelDir = new File(getFilesDir(), MODEL_DIR_NAME);
            if (!isModelReady(modelDir)) {
                updateNotification("مدل تشخیص اوبا در حال دانلود است");
                downloadAndExtractModel(modelDir);
            }
            if (stopping || paused) return;
            updateNotification("آماده؛ بگو «اوبا»");
            model = new Model(modelDir.getAbsolutePath());
            listenLoop();
        } catch (Exception e) {
            updateNotification("مدل تشخیص آماده نشد؛ اینترنت و فضای خالی را بررسی کن");
            if (!stopping) handler.postDelayed(() -> {
                if (!stopping && (worker == null || !worker.isAlive())) {
                    worker = new Thread(this::prepareAndListen, "OBA-Vosk-Wake");
                    worker.start();
                }
            }, 8000);
        }
    }

    private void listenLoop() {
        final int sampleRate = 16000;
        int minBuffer = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) minBuffer = 8192;
        int bufferSize = Math.max(minBuffer, 8192);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        audioRecord = recorder;
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            audioRecord = null;
            updateNotification("میکروفون در دسترس نیست؛ اجازهٔ میکروفون را بررسی کن");
            return;
        }

        try (Recognizer recognizer = new Recognizer(model, sampleRate)) {
            byte[] buffer = new byte[4096];
            wakeHandled = false;
            listening = true;
            recorder.startRecording();
            updateNotification("در حال شنیدن؛ بگو «اوبا»");
            while (!stopping && !paused && listening && !wakeHandled) {
                int n = recorder.read(buffer, 0, buffer.length);
                if (n <= 0) continue;
                boolean finalResult = recognizer.acceptWaveForm(buffer, n);
                String json = finalResult ? recognizer.getResult() : recognizer.getPartialResult();
                if (isWakeWord(json)) {
                    wakeHandled = true;
                    paused = true;
                    listening = false;
                    updateNotification("اوبا شنیده شد؛ برنامه باز می‌شود");
                    handler.post(this::openApp);
                }
            }
        } catch (Exception e) {
            if (!stopping) updateNotification("تشخیص اوبا متوقف شد؛ دوباره تلاش می‌کنیم");
        } finally {
            listening = false;
            try { recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            if (audioRecord == recorder) audioRecord = null;
        }
    }

    private boolean isWakeWord(String json) {
        try {
            JSONObject result = new JSONObject(json);
            String phrase = result.optString("text", "");
            String partial = result.optString("partial", "");
            return containsWakeWord(phrase) || containsWakeWord(partial);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean containsWakeWord(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT)
            .replace(" ", "").replace("‌", "")
            .replace("ي", "ی").replace("ك", "ک")
            .replace("آ", "ا").replace("أ", "ا").replace("إ", "ا");
        return normalized.contains("اوبا") || normalized.contains("oba");
    }

    private void openApp() {
        if (stopping) return;
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(launch);
        } catch (Exception ignored) {
            updateNotification("اوبا شنیده شد؛ برای بازکردن برنامه اعلان را لمس کن");
        }
        // Do not restart microphone capture while MainActivity is foreground.
        // MainActivity resumes wake listening when it leaves the foreground.
    }

    private void listenAgain() {
        try {
            if (model != null && !stopping && !paused) listenLoop();
        } catch (Exception ignored) {}
    }

    private boolean isModelReady(File dir) {
        return dir.isDirectory() && new File(dir, "am").isDirectory()
            && new File(dir, "conf").isDirectory() && new File(dir, "graph").isDirectory();
    }

    private void downloadAndExtractModel(File targetDir) throws Exception {
        File zipFile = new File(getCacheDir(), "vosk-model-small-fa-0.5.zip");
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();
        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Model download HTTP " + response);
        }
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(zipFile)) {
            byte[] buffer = new byte[16384];
            int count;
            while (!stopping && (count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        } finally {
            connection.disconnect();
        }
        if (stopping) return;

        File tempDir = new File(getFilesDir(), MODEL_DIR_NAME + "-extracting");
        deleteRecursively(tempDir);
        if (!tempDir.mkdirs() && !tempDir.isDirectory()) throw new IllegalStateException("Cannot create model directory");
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while (!stopping && (entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                String[] parts = name.split("/", 2);
                if (parts.length < 2 || parts[1].isEmpty()) {
                    zip.closeEntry();
                    continue;
                }
                String relative = parts[1];
                File output = new File(tempDir, relative);
                String rootPath = tempDir.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(rootPath)) throw new SecurityException("Invalid model archive path");
                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("Cannot create model folder");
                } else {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot create model folder");
                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) out.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
        if (stopping) return;
        if (!isModelReady(tempDir)) throw new IllegalStateException("Downloaded archive does not contain a valid model");
        deleteRecursively(targetDir);
        if (!tempDir.renameTo(targetDir)) throw new IllegalStateException("Cannot install model");
        // The archive is no longer needed after successful extraction.
        //noinspection ResultOfMethodCallIgnored
        zipFile.delete();
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void stopCapture() {
        listening = false;
        AudioRecord current = audioRecord;
        if (current != null) {
            try { current.stop(); } catch (Exception ignored) {}
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "شنیدن نام OBA", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان سرویس تشخیص آفلاین نام OBA");
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
        stopCapture();
        if (model != null) {
            model.close();
            model = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}