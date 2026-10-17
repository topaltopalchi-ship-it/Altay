package ir.khone.yar;

/** Local free-tier policy: ten records per section before asking for an account and subscription. */
public final class FreeQuotaPolicy {
    public static final int FREE_RECORD_LIMIT_PER_SECTION = 10;

    private FreeQuotaPolicy() {}

    public static boolean canCreate(int currentCount) {
        return currentCount >= 0 && currentCount < FREE_RECORD_LIMIT_PER_SECTION;
    }

    public static int remaining(int currentCount) {
        return Math.max(0, FREE_RECORD_LIMIT_PER_SECTION - Math.max(0, currentCount));
    }

    /**
     * Counts top-level bullet records and reconciles them with records created by voice
     * flows that may not be stored in the section's text list.
     */
    public static int countRecords(String savedText, int persistedCount) {
        int listed = 0;
        if (savedText != null && !savedText.trim().isEmpty()) {
            for (String line : savedText.split("\\n")) {
                if (line.trim().startsWith("• ")) listed++;
            }
        }
        return Math.max(listed, Math.max(0, persistedCount));
    }
}
