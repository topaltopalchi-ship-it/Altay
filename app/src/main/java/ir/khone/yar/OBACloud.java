package ir.khone.yar;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Supabase Auth client for OBA account sign-up, sign-in, and session handling. Never uses a service-role key. */
final class OBACloud {
    private static final String BASE = "https://sxyaujvliummbvzznejq.supabase.co";
    // Public publishable key; row-level security and the user's JWT protect private rows.
    private static final String API_KEY = "sb_publishable_AQdI9Ehm-lf7p1Rs7bu4BA_m60TTlen";
    private static final String PREFS = "khoneyar";
    private static final String ACCESS = "oba_cloud_access_token";
    private static final String REFRESH = "oba_cloud_refresh_token";
    private static final String EXPIRES = "oba_cloud_expires_at";
    private static final String USER = "oba_cloud_user_email";
    private static final String USER_ID = "oba_cloud_user_id";

    private OBACloud() {}

    static boolean isSignedIn(Context c) {
        return !get(c, ACCESS).isEmpty() && !get(c, REFRESH).isEmpty();
    }

    static String userEmail(Context c) { return get(c, USER); }

    static void signOut(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ACCESS).remove(REFRESH).remove(EXPIRES).remove(USER).remove(USER_ID).apply();
    }

    static String signUp(Context c, String email, String password, String username) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email.trim());
        body.put("password", password);
        JSONObject metadata = new JSONObject();
        String clean = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        if (clean.matches("[a-z0-9_]{3,24}")) metadata.put("username", clean);
        body.put("data", metadata);
        JSONObject result = request("POST", "/auth/v1/signup", body, null, null);
        saveSession(c, result, email);
        if (!isSignedIn(c)) return "ثبت‌نام ارسال شد. اگر تأیید ایمیل فعال است، ایمیل را تأیید کن و سپس وارد شو.";
        return "حساب ساخته شد و ورود انجام شد.";
    }

    static String signIn(Context c, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email.trim());
        body.put("password", password);
        JSONObject result = request("POST", "/auth/v1/token?grant_type=password", body, null, null);
        saveSession(c, result, email);
        if (!isSignedIn(c)) throw new Exception("ورود انجام نشد؛ نشست معتبر از سرور دریافت نشد.");
        return "ورود موفق بود.";
    }

    private static String validToken(Context c) throws Exception {
        String token = get(c, ACCESS);
        long expiry = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(EXPIRES, 0);
        if (!token.isEmpty() && expiry > System.currentTimeMillis() / 1000L + 60) return token;
        String refresh = get(c, REFRESH);
        if (refresh.isEmpty()) throw new Exception("نشست منقضی شده؛ دوباره وارد حساب شو.");
        JSONObject body = new JSONObject().put("refresh_token", refresh);
        JSONObject result = request("POST", "/auth/v1/token?grant_type=refresh_token", body, null, null);
        saveSession(c, result, get(c, USER));
        token = get(c, ACCESS);
        if (token.isEmpty()) throw new Exception("تمدید نشست انجام نشد؛ دوباره وارد شو.");
        return token;
    }

    private static void saveSession(Context c, JSONObject result, String email) {
        String access = result.optString("access_token", "");
        String refresh = result.optString("refresh_token", "");
        long expires = result.optLong("expires_at", 0);
        if (expires == 0 && result.optLong("expires_in", 0) > 0)
            expires = System.currentTimeMillis() / 1000L + result.optLong("expires_in");
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (!access.isEmpty()) e.putString(ACCESS, access);
        if (!refresh.isEmpty()) e.putString(REFRESH, refresh);
        if (expires > 0) e.putLong(EXPIRES, expires);
        if (email != null && !email.isEmpty()) e.putString(USER, email);
        JSONObject user = result.optJSONObject("user");
        if (user != null && !user.optString("id", "").isEmpty()) e.putString(USER_ID, user.optString("id"));
        e.apply();
    }

    private static String get(Context c, String key) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "");
    }

    private static JSONObject request(String method, String path, Object body, String token, String prefer) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BASE + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("apikey", API_KEY);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
            if (prefer != null) conn.setRequestProperty("Prefer", prefer);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
            }
            if (code < 200 || code >= 300) {
                String message = "خطای ارتباط با سرور (" + code + ")";
                try {
                    JSONObject error = new JSONObject(response.toString());
                    String detail = error.optString("msg", error.optString("message", error.optString("error_description", "")));
                    if (!detail.isEmpty()) message += ": " + detail;
                } catch (Exception ignored) {}
                throw new Exception(message);
            }
            String text = response.toString();
            return text.isEmpty() ? new JSONObject() : text.startsWith("[") ? new JSONObject().put("_array", new JSONArray(text)) : new JSONObject(text);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
