package ir.khone.yar;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;

/**
 * Optional on-device Persian TTS. The model is downloaded once, then synthesis
 * and playback happen locally. Android's system TTS remains the initial fallback.
 */
final class OfflinePersianTts {
    private static final String MODEL_ARCHIVE =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2";
    private static final String MODEL_DIR_NAME = "vits-piper-fa_IR-amir-medium";

    private final Context activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final File modelRoot;
    private final File modelDir;
    private volatile OfflineTts engine;
    private volatile boolean downloading;
    private volatile boolean downloadAttempted;
    private volatile boolean readyToastShown;
    private MediaPlayer player;
    private final ArrayList<DownloadCallback> downloadCallbacks = new ArrayList<>();

    interface DownloadCallback {
        void onProgress(long downloadedBytes, long totalBytes);
        void onReady();
        void onError(String message);
    }

    OfflinePersianTts(Context activity) {
        activity = activity.getApplicationContext();
        this.activity = activity;
        File base = new File(activity.getFilesDir(), "offline-persian-tts");
        modelRoot = base;
        modelDir = new File(base, MODEL_DIR_NAME);
        // Load native inference lazily on the worker thread, never on the UI thread.
    }

    boolean isReady() {
        return engine != null;
    }

    /**
     * @return true when offline synthesis was queued; false means caller should use fallback.
     */
    boolean speak(String text, Runnable fallback) {
        return speak(text, fallback, null);
    }

    boolean speak(String text, Runnable fallback, Runnable onComplete) {
        if (text == null || text.trim().isEmpty()) {
            if (onComplete != null) mainHandler.post(onComplete);
            return true;
        }
        if (engine == null) {
            ensureModelDownloaded(null);
            if (fallback != null) fallback.run();
            return false;
        }
        final String phrase = text.trim();
        worker.execute(() -> {
            OfflineTts current = engine;
            if (current == null) {
                mainHandler.post(() -> {
                    if (fallback != null) fallback.run();
                    if (onComplete != null) onComplete.run();
                });
                return;
            }
            File wav = new File(activity.getCacheDir(), "oba-offline-speech-" + System.nanoTime() + ".wav");
            try {
                GenerationConfig generation = new GenerationConfig();
                generation.setSid(0);
                generation.setSpeed(1.0f);
                generation.setSilenceScale(0.2f);
                GeneratedAudio audio = current.generateWithConfigAndCallback(
                        phrase, generation, samples -> 1);
                audio.save(wav.getAbsolutePath());
                mainHandler.post(() -> playFile(wav, onComplete));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (fallback != null) fallback.run();
                    Toast.makeText(activity, "صدای آفلاین در دسترس نبود؛ از صدای گوشی استفاده شد.", Toast.LENGTH_SHORT).show();
                });
            }
        });
        return true;
    }

    synchronized void ensureModelDownloaded(DownloadCallback callback) {
        if (callback != null) downloadCallbacks.add(callback);
        if (engine != null) {
            notifyDownloadCallbacks(true, null);
            return;
        }
        if (downloading || downloadAttempted) return;
        downloading = true;
        downloadAttempted = true;
        final boolean modelAlreadyDownloaded = hasModelFiles();
        Toast.makeText(activity, modelAlreadyDownloaded
                ? "در حال آماده‌سازی موتور گفتار آفلاین…"
                : "برای فعال‌شدن صدای آفلاین، مدل فارسی (حدود ۸۰ مگابایت) دانلود می‌شود.",
                Toast.LENGTH_LONG).show();
        worker.execute(() -> {
            File archive = new File(activity.getCacheDir(), "persian-tts-model.tar.bz2");
            String stage = "آماده‌سازی دانلود";
            try {
                if (!hasModelFiles()) {
                    stage = "دریافت فایل مدل";
                    downloadFile(MODEL_ARCHIVE, archive, (downloaded, total) -> mainHandler.post(() -> notifyDownloadProgress(downloaded, total)));
                    stage = "بازکردن فایل فشرده مدل";
                    if (!modelRoot.exists() && !modelRoot.mkdirs()) {
                        throw new IllegalStateException("Cannot create model directory");
                    }
                    extractArchive(archive, modelRoot);
                    if (!hasModelFiles()) throw new IllegalStateException("Model files missing after extraction");
                }
                stage = "راه‌اندازی موتور گفتار آفلاین";
                initializeEngine();
                mainHandler.post(() -> {
                    notifyDownloadCallbacks(engine != null, engine != null ? null : "مدل گفتار نصب نشد.");
                    if (engine != null && !readyToastShown) {
                        readyToastShown = true;
                        Toast.makeText(activity, "مدل فارسی آفلاین آماده است؛ از این پس گفتار روی گوشی ساخته می‌شود.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                downloadAttempted = false;
                final String detail = stage + " ناموفق بود: "
                        + e.getClass().getSimpleName()
                        + (e.getMessage() == null || e.getMessage().trim().isEmpty() ? "" : " — " + e.getMessage());
                mainHandler.post(() -> {
                    notifyDownloadCallbacks(false, detail);
                    Toast.makeText(activity, detail, Toast.LENGTH_LONG).show();
                });
            } finally {
                downloading = false;
                if (archive.exists()) archive.delete();
            }
        });
    }

    private void notifyDownloadProgress(long downloaded, long total) {
        ArrayList<DownloadCallback> callbacks;
        synchronized (this) { callbacks = new ArrayList<>(downloadCallbacks); }
        for (DownloadCallback callback : callbacks) {
            if (callback != null) callback.onProgress(downloaded, total);
        }
    }

    private void notifyDownloadCallbacks(boolean ready, String message) {
        ArrayList<DownloadCallback> callbacks;
        synchronized (this) {
            callbacks = new ArrayList<>(downloadCallbacks);
            downloadCallbacks.clear();
        }
        for (DownloadCallback callback : callbacks) {
            if (callback == null) continue;
            if (ready) callback.onReady();
            else callback.onError(message == null ? "آماده‌سازی مدل گفتار ناموفق بود." : message);
        }
    }

    boolean isModelInstalled() {
        return hasModelFiles();
    }

    private boolean hasModelFiles() {
        return new File(modelDir, "fa_IR-amir-medium.onnx").isFile()
                && new File(modelDir, "tokens.txt").isFile()
                && new File(modelDir, "espeak-ng-data").isDirectory();
    }

    private void initializeEngine() {
        if (!hasModelFiles() || engine != null) return;
        OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
        vits.setModel(new File(modelDir, "fa_IR-amir-medium.onnx").getAbsolutePath());
        vits.setTokens(new File(modelDir, "tokens.txt").getAbsolutePath());
        vits.setDataDir(new File(modelDir, "espeak-ng-data").getAbsolutePath());

        OfflineTtsModelConfig model = new OfflineTtsModelConfig();
        model.setVits(vits);
        model.setNumThreads(2);
        model.setDebug(false);

        OfflineTtsConfig config = new OfflineTtsConfig();
        config.setModel(model);
        config.setMaxNumSentences(1);
        engine = new OfflineTts(activity.getAssets(), config);
    }

    private void playFile(File wav, Runnable onComplete) {
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        Runnable finish = () -> {
            if (completed.compareAndSet(false, true) && onComplete != null) onComplete.run();
        };
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            player = new MediaPlayer();
            player.setDataSource(wav.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                mp.release();
                if (player == mp) player = null;
                // Always notify the caller so background wake listening can resume.
                wav.delete();
                finish.run();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                if (player == mp) player = null;
                wav.delete();
                finish.run();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) {
                try { player.release(); } catch (Exception ignored) {}
                player = null;
            }
            wav.delete();
            // Playback failure must not leave VoiceWakeService stuck in speaking=true.
            mainHandler.post(finish);
        }
    }

    private interface ProgressListener { void onProgress(long downloadedBytes, long totalBytes); }

    private static void downloadFile(String address, File target, ProgressListener progressListener) throws Exception {
        File partial = new File(target.getAbsolutePath() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "OBA-Android");
        try {
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Model server HTTP " + code);
            long totalBytes = connection.getContentLengthLong();
            long downloadedBytes = 0;
            long lastReportedBytes = 0;
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(partial))) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    downloadedBytes += count;
                    if (downloadedBytes - lastReportedBytes >= 262144 || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                        progressListener.onProgress(downloadedBytes, totalBytes);
                        lastReportedBytes = downloadedBytes;
                    }
                }
            }
            if (partial.length() < 10_000_000L) throw new IllegalStateException("Model archive is unexpectedly small");
            if (target.exists()) target.delete();
            if (!partial.renameTo(target)) throw new IllegalStateException("Cannot finalize model download");
        } finally {
            connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    private static void extractArchive(File archive, File destination) throws Exception {
        String rootPath = destination.getCanonicalPath() + File.separator;
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(archive));
             BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(fileIn);
             TarArchiveInputStream tar = new TarArchiveInputStream(bzIn)) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = tar.getNextTarEntry()) != null) {
                if (entry.isSymbolicLink() || entry.isLink()) continue;
                File output = new File(destination, entry.getName());
                String canonical = output.getCanonicalPath();
                if (!canonical.startsWith(rootPath)) throw new IllegalStateException("Invalid model archive path");
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create model folder");
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("Cannot create model folder");
                }
                try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                    int count;
                    while ((count = tar.read(buffer)) != -1) out.write(buffer, 0, count);
                }
            }
        }
    }

    void shutdown() {
        worker.shutdownNow();
        OfflineTts current = engine;
        engine = null;
        if (current != null) current.release();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
