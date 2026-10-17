package ir.khone.yar;

import org.junit.Test;
import static org.junit.Assert.*;

public class FreeQuotaPolicyTest {
    @Test public void allowsRecordsOneThroughTen() {
        assertTrue(FreeQuotaPolicy.canCreate(0));
        assertTrue(FreeQuotaPolicy.canCreate(9));
    }

    @Test public void blocksTheEleventhRecord() {
        assertFalse(FreeQuotaPolicy.canCreate(10));
        assertFalse(FreeQuotaPolicy.canCreate(11));
    }

    @Test public void reportsRemainingAllowanceWithoutGoingNegative() {
        assertEquals(10, FreeQuotaPolicy.remaining(0));
        assertEquals(1, FreeQuotaPolicy.remaining(9));
        assertEquals(0, FreeQuotaPolicy.remaining(10));
        assertEquals(0, FreeQuotaPolicy.remaining(15));
    }

    @Test public void rejectsInvalidNegativeCount() {
        assertFalse(FreeQuotaPolicy.canCreate(-1));
        assertEquals(10, FreeQuotaPolicy.remaining(-1));
    }
}
