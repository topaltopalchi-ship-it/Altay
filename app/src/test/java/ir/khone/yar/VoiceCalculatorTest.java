package ir.khone.yar;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VoiceCalculatorTest {
    @Test
    public void addsAndRespectsOperatorPrecedence() {
        assertEquals(800.0, VoiceCalculator.evaluate("ماشین حساب ۲۰۰ جمع ۳۰۰ ضربدر ۲"), 0.0001);
    }

    @Test
    public void convertsSpokenThousandAndMillion() {
        assertEquals(500000.0, VoiceCalculator.evaluate("۵۰۰ هزار"), 0.0001);
        assertEquals(2000000.0, VoiceCalculator.evaluate("۲ میلیون"), 0.0001);
    }

    @Test
    public void calculatesPostfixPercent() {
        assertEquals(100000.0, VoiceCalculator.evaluate("۵۰۰۰۰۰ ضربدر ۲۰ درصد"), 0.0001);
    }

    @Test
    public void calculatesDiscountedPriceWithAndWithoutLeadingBa() {
        assertEquals(400000.0, VoiceCalculator.evaluate("۲۰ درصد تخفیف از ۵۰۰ هزار تومان"), 0.0001);
        assertEquals(400000.0, VoiceCalculator.evaluate("با ۲۰ درصد تخفیف از ۵۰۰ هزار تومان"), 0.0001);
        assertEquals(400000.0, VoiceCalculator.evaluate("قیمت با ۲۰ درصد تخفیف از ۵۰۰ هزار تومان"), 0.0001);
    }

    @Test
    public void rejectsDivisionByZero() {
        try {
            VoiceCalculator.evaluate("۱۰ تقسیم بر ۰");
            fail("Expected division by zero to be rejected");
        } catch (ArithmeticException expected) {
            // Expected.
        }
    }
}
