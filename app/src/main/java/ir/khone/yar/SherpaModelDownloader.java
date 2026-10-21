package ir.khone.yar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads and installs the complete offline Persian voice package. */
public final class SherpaModelDownloader {
    private static final String ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2";
    private static final String MODEL_FOLDER = "vits-piper-fa_IR-amir-medium";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onProgress(String fileName, long downloadedBytes, long totalBytes);
        void onInstalled(File modelDirectory);
        void onError(String message);
    }

    private SherpaModelDownloader() {}

    public static File getInstalledModelDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "sherpa-tts/" + MODEL_FOLDER);
        File model = new File(dir, "fa_IR-amir-medium.onnx");
        File tokens = new File(dir, "tokens.txt");
        File espeak = new File(dir, "espeak-ng-data");
        return model.isFile() && model.length() > 10L * 1024 * 1024
                && tokens.isFile() && espeak.isDirectory() ? dir : null;
    }

    public static void downloadModel(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File base = new File(app.getFilesDir(), "sherpa-tts");
            File archive = new File(base, "voice-package.tar.bz2");
            File staging = new File(base, "installing");
            try {
                File installed = getInstalledModelDirectory(app);
                if (installed != null) {
                    MAIN.post(() -> callback.onInstalled(installed));
                    return;
                }
                if (!base.exists() && !base.mkdirs() && !base.isDirectory()) {
                    throw new IllegalStateException("فضای ذخیره‌سازی صدای فارسی آماده نشد.");
                }
                deleteTree(staging);
                if (!staging.mkdirs() && !staging.isDirectory()) {
                    throw new IllegalStateException("فضای نصب صدای فارسی آماده نشد.");
                }
                downloadArchive(new URL(ARCHIVE_URL), archive, callback);
                extractArchive(archive, staging);
                File extracted = new File(staging, MODEL_FOLDER);
                if (!new File(extracted, "fa_IR-amir-medium.onnx").isFile()
                        || !new File(extracted, "tokens.txt").isFile()
                        || !new File(extracted, "espeak-ng-data").isDirectory()) {
                    throw new IllegalStateException("بستهٔ صدای فارسی کامل نیست.");
                }
                File destination = new File(base, MODEL_FOLDER);
                deleteTree(destination);
                if (!extracted.renameTo(destination)) copyTree(extracted, destination);
                deleteTree(staging);
                archive.delete();
                File ready = getInstalledModelDirectory(app);
                if (ready == null) throw new IllegalStateException("نصب صدای فارسی کامل نشد.");
                MAIN.post(() -> callback.onInstalled(ready));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "دریافت صدای فارسی ناموفق بود." : e.getMessage();
                MAIN.post(() -> callback.onError(message));
            }
        });
    }

    private static void downloadArchive(URL url, File target, Callback callback) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".part");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "OBA-Android");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IllegalStateException("دریافت بستهٔ صدای فارسی ناموفق بود (HTTP " + code + ").");
            long total = connection.getContentLengthLong(), downloaded = 0;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    downloaded += count;
                    if (downloaded % (2L * 1024 * 1024) < buffer.length || (total > 0 && downloaded >= total)) {
                        long current = downloaded;
                        MAIN.post(() -> callback.onProgress("بستهٔ صدای فارسی", current, total));
                    }
                }
                out.getFD().sync();
            }
            if (downloaded < 10L * 1024 * 1024) throw new IllegalStateException("بستهٔ صدای فارسی ناقص دریافت شد.");
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) throw new IllegalStateException("ذخیرهٔ بستهٔ صدای فارسی ناموفق بود.");
        } finally {
            connection.disconnect();
        }
    }

    private static void extractArchive(File archive, File destination) throws Exception {
        String root = destination.getCanonicalPath() + File.separator;
        try (InputStream file = new java.io.FileInputStream(archive);
             BZip2CompressorInputStream bz = new BZip2CompressorInputStream(file);
             TarArchiveInputStream tar = new TarArchiveInputStream(bz)) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = tar.getNextTarEntry()) != null) {
                File output = new File(destination, entry.getName());
                String path = output.getCanonicalPath();
                if (!path.startsWith(root)) throw new IllegalStateException("مسیر نامعتبر در بستهٔ صدای فارسی.");
                if (entry.isDirectory()) {
                    if (!output.exists()) output.mkdirs();
                } else if (entry.isFile()) {
                    File parent = output.getParentFile();
                    if (!parent.exists()) parent.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(output)) {
                        int n;
                        while ((n = tar.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private static void copyTree(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(target, child.getName()));
        } else {
            try (InputStream in = new java.io.FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
                byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n);
            }
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
