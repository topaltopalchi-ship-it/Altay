package ir.khone.yar;

import android.app.Activity;
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

/**
 * Optional on-device Persian TTS. The model is downloaded once, then synthesis
 * and playback happen locally. Android's system TTS remains the initial fallback.
 */
final class OfflinePersianTts {
    private static final String MODEL_ARCHIVE =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2";
    private static final String MODEL_DIR_NAME = "vits-piper-fa_IR-amir-medium";

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final File modelRoot;
    private final File modelDir;
    private volatile OfflineTts engine;
    private volatile boolean downloading;
    private volatile boolean downloadAttempted;
    private volatile boolean readyToastShown;
    private MediaPlayer player;

    OfflinePersianTts(Activity activity) {
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
        if (text == null || text.trim().isEmpty()) return true;
        if (engine == null) {
            ensureModelDownloaded();
            if (fallback != null) fallback.run();
            return false;
        }
        final String phrase = text.trim();
        worker.execute(() -> {
            OfflineTts current = engine;
            if (current == null) {
                mainHandler.post(() -> { if (fallback != null) fallback.run(); });
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
                mainHandler.post(() -> playFile(wav));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (fallback != null) fallback.run();
                    Toast.makeText(activity, "صدای آفلاین در دسترس نبود؛ از صدای گوشی استفاده شد.", Toast.LENGTH_SHORT).show();
                });
            }
        });
        return true;
    }

    private synchronized void ensureModelDownloaded() {
        if (downloading || downloadAttempted || engine != null) return;
        downloading = true;
        downloadAttempted = true;
        final boolean modelAlreadyDownloaded = hasModelFiles();
        Toast.makeText(activity, modelAlreadyDownloaded
                ? "در حال آماده‌سازی موتور گفتار آفلاین…"
                : "برای فعال‌شدن صدای آفلاین، مدل فارسی (حدود ۸۰ مگابایت) دانلود می‌شود.",
                Toast.LENGTH_LONG).show();
        worker.execute(() -> {
            File archive = new File(activity.getCacheDir(), "persian-tts-model.tar.bz2");
            try {
                if (!hasModelFiles()) {
                    downloadFile(MODEL_ARCHIVE, archive);
                    if (!modelRoot.exists() && !modelRoot.mkdirs()) {
                        throw new IllegalStateException("Cannot create model directory");
                    }
                    extractArchive(archive, modelRoot);
                    if (!hasModelFiles()) throw new IllegalStateException("Model files missing after extraction");
                }
                initializeEngine();
                mainHandler.post(() -> {
                    if (engine != null && !readyToastShown) {
                        readyToastShown = true;
                        Toast.makeText(activity, "مدل فارسی آفلاین آماده است؛ از این پس گفتار روی گوشی ساخته می‌شود.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                downloadAttempted = false;
                mainHandler.post(() -> Toast.makeText(activity,
                        "دانلود مدل گفتار ناموفق بود. اینترنت را بررسی کن و دوباره تلاش کن.", Toast.LENGTH_LONG).show());
            } finally {
                downloading = false;
                if (archive.exists()) archive.delete();
            }
        });
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

    private void playFile(File wav) {
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
                wav.delete();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                if (player == mp) player = null;
                wav.delete();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) {
                player.release();
                player = null;
            }
            wav.delete();
        }
    }

    private static void downloadFile(String address, File target) throws Exception {
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
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(partial))) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
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
