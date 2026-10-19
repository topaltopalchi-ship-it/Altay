package ir.khone.yar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and installs the Persian Vosk model into app-private storage.
 *
 * The app invokes downloadModel() in response to the user's voice-button tap
 * when no local model is installed. Downloads are stored in app-private storage.
 */
public final class OfflineModelDownloader {
    public static final String MODEL_DIRECTORY = "vosk-model-small-fa-0.42";
    private static final String MODEL_URL =
            "https://github.com/topaltopalchi-ship-it/Altay/releases/download/vosk-fa-model-v1/vosk-fa-clean.zip";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    public interface Callback {
        void onProgress(long downloadedBytes, long totalBytes);
        void onInstalled(File modelDirectory);
        void onError(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private OfflineModelDownloader() {}

    public static File getInstalledModelDirectory(Context context) {
        File model = new File(context.getFilesDir(), MODEL_DIRECTORY);
        return new File(model, "am").isDirectory() ? model : null;
    }

    /** Call only after the user explicitly approves downloading the offline model. */
    public static void downloadModel(Context context, Callback callback) {
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File stagingZip = new File(appContext.getCacheDir(), MODEL_DIRECTORY + ".zip.part");
            File completedZip = new File(appContext.getCacheDir(), MODEL_DIRECTORY + ".zip");
            File stagingDir = new File(appContext.getFilesDir(), MODEL_DIRECTORY + ".installing");
            File installedDir = new File(appContext.getFilesDir(), MODEL_DIRECTORY);

            HttpURLConnection connection = null;
            try {
                File existing = getInstalledModelDirectory(appContext);
                if (existing != null) {
                    postInstalled(callback, existing);
                    return;
                }

                URL url = new URL(MODEL_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("دریافت مدل ناموفق بود (HTTP " + status + ").");
                }

                long total = connection.getContentLength();
                long downloaded = 0;
                long lastProgressReport = 0;
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(stagingZip)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        downloaded += count;
                        if (downloaded - lastProgressReport >= 262144 || (total > 0 && downloaded >= total)) {
                            final long progress = downloaded;
                            MAIN.post(() -> callback.onProgress(progress, total));
                            lastProgressReport = downloaded;
                        }
                    }
                    out.getFD().sync();
                }
                if (downloaded == 0) throw new IllegalStateException("فایل مدل خالی دریافت شد.");
                if (!stagingZip.renameTo(completedZip)) {
                    copyFile(stagingZip, completedZip);
                    //noinspection ResultOfMethodCallIgnored
                    stagingZip.delete();
                }

                deleteRecursively(stagingDir);
                if (!stagingDir.mkdirs() && !stagingDir.isDirectory()) {
                    throw new IllegalStateException("فضای کافی برای نصب مدل وجود ندارد.");
                }
                unzipSafely(completedZip, stagingDir);

                File extractedModel = new File(stagingDir, MODEL_DIRECTORY);
                if (!new File(extractedModel, "am").isDirectory()) {
                    if (new File(stagingDir, "am").isDirectory()) {
                        extractedModel.mkdirs();
                        for (File item : stagingDir.listFiles()) {
                            if (item.equals(extractedModel)) continue;
                            File target = new File(extractedModel, item.getName());
                            if (item.isDirectory()) {
                                copyDirectory(item, target);
                            } else {
                                copyFile(item, target);
                            }
                        }
                    } else {
                        throw new IllegalStateException("ساختار مدل دریافتی معتبر نیست.");
                    }
                }
                deleteRecursively(installedDir);
                if (!extractedModel.renameTo(installedDir)) {
                    copyDirectory(extractedModel, installedDir);
                }
                if (getInstalledModelDirectory(appContext) == null) {
                    throw new IllegalStateException("نصب مدل کامل نشد.");
                }
                postInstalled(callback, installedDir);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "خطا در دریافت مدل آفلاین." : e.getMessage();
                MAIN.post(() -> callback.onError(message));
            } finally {
                if (connection != null) connection.disconnect();
                //noinspection ResultOfMethodCallIgnored
                stagingZip.delete();
                //noinspection ResultOfMethodCallIgnored
                completedZip.delete();
                deleteRecursively(stagingDir);
            }
        });
    }

    private static void unzipSafely(File zipFile, File destination) throws Exception {
        String root = destination.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = zis.getNextEntry()) != null) {
                File target = new File(destination, entry.getName());
                if (!target.getCanonicalPath().startsWith(root)) {
                    throw new SecurityException("مسیر نامعتبر داخل فایل مدل.");
                }
                if (entry.isDirectory()) {
                    if (!target.mkdirs() && !target.isDirectory()) {
                        throw new IllegalStateException("ساخت پوشهٔ مدل ناموفق بود.");
                    }
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IllegalStateException("ساخت پوشهٔ مدل ناموفق بود.");
                    }
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) out.write(buffer, 0, count);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.getFD().sync();
        }
    }

    private static void copyDirectory(File from, File to) throws Exception {
        if (from.isDirectory()) {
            if (!to.mkdirs() && !to.isDirectory()) throw new IllegalStateException("کپی مدل ناموفق بود.");
            File[] children = from.listFiles();
            if (children == null) throw new IllegalStateException("خواندن فایل‌های مدل ناموفق بود.");
            for (File child : children) copyDirectory(child, new File(to, child.getName()));
        } else {
            copyFile(from, to);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void postInstalled(Callback callback, File model) {
        MAIN.post(() -> callback.onInstalled(model));
    }
}
