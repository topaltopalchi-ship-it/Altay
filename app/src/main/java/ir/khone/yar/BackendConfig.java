package ir.khone.yar;

/**
 * Public, build-time configuration for OBA's Android client.
 *
 * SUPABASE_PUBLISHABLE_KEY is intentionally not a secret; database access must
 * still be protected by Row Level Security. Never put a Supabase secret/service
 * role key in this class or in the APK.
 */
public final class BackendConfig {
    private BackendConfig() {}

    public static String supabaseUrl() {
        return BuildConfig.SUPABASE_URL == null ? "" : BuildConfig.SUPABASE_URL.trim();
    }

    public static String supabasePublishableKey() {
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY == null
            ? "" : BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim();
    }

    public static boolean isSupabaseConfigured() {
        String url = supabaseUrl();
        String key = supabasePublishableKey();
        return url.startsWith("https://")
            && url.endsWith(".supabase.co")
            && !key.isEmpty()
            && !key.toLowerCase(java.util.Locale.ROOT).contains("secret");
    }

    public static String restUrl(String resource) {
        if (!isSupabaseConfigured() || resource == null || resource.trim().isEmpty()) {
            return "";
        }
        String base = supabaseUrl();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/rest/v1/" + resource;
    }
}
