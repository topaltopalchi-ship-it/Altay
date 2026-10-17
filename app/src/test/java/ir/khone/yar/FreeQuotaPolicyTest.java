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

    @Test public void countsOnlyTopLevelBulletRecords() {
        assertEquals(0, FreeQuotaPolicy.countRecords("", 0));
        assertEquals(0, FreeQuotaPolicy.countRecords(null, 0));
        assertEquals(2, FreeQuotaPolicy.countRecords("• آب\\n  جزئیات\\n• برق\\n  مبلغ: 500", 0));
    }

    @Test public void reconcilesListAndVoiceRecordCountersWithoutDoubleCounting() {
        assertEquals(4, FreeQuotaPolicy.countRecords("• آب\\n• برق", 4));
        assertEquals(2, FreeQuotaPolicy.countRecords("• آب\\n• برق\\n• گاز", 2));
        assertEquals(0, FreeQuotaPolicy.countRecords("تفصیل بدون ثبت", -5));
    }
}
