package ir.khone.yar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

/**
 * Read-only gateway for market quote snapshots stored by a trusted backend job.
 * It does not scrape providers or contain provider credentials in the APK.
 */
public final class MarketQuotesClient {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int TIMEOUT_MS = 8000;

    private MarketQuotesClient() {}

    public static final class Quote {
        public final String symbol;
        public final String displayName;
        public final Double priceToman;
        public final Double changePercent;
        public final String source;
        public final String observedAt;
        public final String fetchedAt;
        public final boolean stale;

        Quote(JSONObject json) {
            symbol = json.optString("symbol", "");
            displayName = json.optString("display_name", symbol);
            priceToman = json.isNull("price_toman") ? null : json.optDouble("price_toman");
            changePercent = json.isNull("change_percent") ? null : json.optDouble("change_percent");
            source = json.optString("source", "");
            observedAt = json.optString("observed_at", "");
            fetchedAt = json.optString("fetched_at", "");
            stale = json.optBoolean("is_stale", true);
        }
    }

    public interface Callback {
        void onSuccess(List<Quote> quotes);
        void onError(String safeMessage);
    }

    public static void fetchQuotes(Callback callback) {
        if (callback == null) return;
        if (!BackendConfig.isSupabaseConfigured()) {
            MAIN.post(() -> callback.onError("اتصال سرور هنوز تنظیم نشده است."));
            return;
        }

        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String resource = "oba_market_quotes?select=symbol,display_name,price_toman,change_percent,source,observed_at,fetched_at,is_stale&order=symbol.asc";
                URL url = new URL(BackendConfig.restUrl(resource));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("apikey", BackendConfig.supabasePublishableKey());
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cache-Control", "no-cache");

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                String body = readFully(stream);
                if (status < 200 || status >= 300) {
                    MAIN.post(() -> callback.onError(status == 401 || status == 403
                        ? "دسترسی به اطلاعات بازار تنظیم نشده است."
                        : "دریافت قیمت‌ها انجام نشد؛ بعداً دوباره تلاش کن."));
                    return;
                }

                JSONArray array = new JSONArray(body);
                List<Quote> quotes = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) quotes.add(new Quote(item));
                }
                MAIN.post(() -> callback.onSuccess(quotes));
            } catch (Exception ignored) {
                MAIN.post(() -> callback.onError("اتصال به سرور برقرار نشد؛ اینترنت را بررسی کن."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readFully(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input;
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }
}
