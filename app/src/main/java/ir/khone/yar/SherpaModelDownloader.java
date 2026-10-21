package ir.khone.yar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads the Persian Piper voice assets for the optional Sherpa-ONNX TTS engine. */
public final class SherpaModelDownloader {
    private static final String MODEL_NAME = "fa_IR-amir-medium.onnx";
    private static final String BASE_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/fa/fa_IR/amir/medium/";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onProgress(String fileName, long downloadedBytes, long totalBytes);
        void onInstalled(File modelDirectory);
        void onError(String message);
    }

    private SherpaModelDownloader() {}

    public static File getInstalledModelDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "sherpa-tts/fa_IR-amir-medium");
        File model = new File(dir, MODEL_NAME);
        File config = new File(dir, MODEL_NAME + ".json");
        return model.isFile() && model.length() > 1024 * 1024 && config.isFile() ? dir : null;
    }

    public static void downloadModel(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File dir = new File(app.getFilesDir(), "sherpa-tts/fa_IR-amir-medium");
            try {
                File installed = getInstalledModelDirectory(app);
                if (installed != null) {
                    File alreadyInstalled = installed;
                    MAIN.post(() -> callback.onInstalled(alreadyInstalled));
                    return;
                }
                if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
                    throw new IllegalStateException("فضای ذخیره‌سازی صدای فارسی آماده نشد.");
                }
                downloadFile(new URL(BASE_URL + MODEL_NAME + "?download=true"),
                        new File(dir, MODEL_NAME), MODEL_NAME, callback);
                downloadFile(new URL(BASE_URL + MODEL_NAME + ".json?download=true"),
                        new File(dir, MODEL_NAME + ".json"), MODEL_NAME + ".json", callback);
                installed = getInstalledModelDirectory(app);
                if (installed == null) throw new IllegalStateException("فایل‌های صدای فارسی کامل دریافت نشدند.");
                File ready = installed;
                MAIN.post(() -> callback.onInstalled(ready));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "دریافت صدای فارسی ناموفق بود." : e.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        });
    }

    private static void downloadFile(URL url, File target, String label, Callback callback) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".part");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("دریافت " + label + " ناموفق بود (HTTP " + code + ").");
            }
            long total = connection.getContentLengthLong();
            long downloaded = 0;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    downloaded += count;
                    if (downloaded % (1024 * 1024) < buffer.length || (total > 0 && downloaded >= total)) {
                        long current = downloaded;
                        MAIN.post(() -> callback.onProgress(label, current, total));
                    }
                }
                out.getFD().sync();
            }
            if (downloaded < 1024) throw new IllegalStateException("فایل " + label + " ناقص دریافت شد.");
            if (target.exists() && !target.delete()) throw new IllegalStateException("جایگزینی فایل مدل ناموفق بود.");
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("ذخیره فایل " + label + " ناموفق بود.");
            }
        } finally {
            connection.disconnect();
        }
    }
}
