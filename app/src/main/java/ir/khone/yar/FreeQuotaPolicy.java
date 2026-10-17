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
}
