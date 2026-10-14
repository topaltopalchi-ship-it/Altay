package ir.khone.yar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Evaluates simple Persian voice-calculator expressions without network access. */
public final class VoiceCalculator {
    private VoiceCalculator() {}

    public static double evaluate(String input) {
        String s = normalizeDigits(input == null ? "" : input.toLowerCase(Locale.ROOT));
        // Turn common discount requests into the final price after discount.
        s = s.replace("قیمت با", " ").replace("با تخفیف", " تخفیف ");
        java.util.regex.Matcher discount = java.util.regex.Pattern
            .compile("([0-9]+(?:\\\\.[0-9]+)?)\\\\s*درصد\\\\s*تخفیف\\\\s*از\\\\s*([0-9]+(?:\\\\.[0-9]+)?(?:\\\\s*(?:هزار|میلیون))?)")
            .matcher(s);
        StringBuffer discounted = new StringBuffer();
        while (discount.find()) {
            String percent = discount.group(1);
            String price = discount.group(2);
            discount.appendReplacement(discounted, java.util.regex.Matcher.quoteReplacement(
                "(" + price + " - (" + price + " * " + percent + " %))"));
        }
        discount.appendTail(discounted);
        s = discounted.toString();
        s = s.replace("ماشین حساب", " ")
             .replace("حساب کن", " ")
             .replace("نتیجه", " ")
             .replace("برابر است با", " ")
             .replace("تقسیم بر", " تقسیم ")
             .replace("تقسیم‌بر", " تقسیم ")
             .replace("به علاوه", " جمع ")
             .replace("بعلاوه", " جمع ")
             .replace("به‌علاوه", " جمع ")
             .replace("منهای", " منفی ")
             .replace("منها", " منفی ")
             .replace("تفریق", " منفی ")
             .replace("ضربدر", " ضرب ")
             .replace("ضرب در", " ضرب ")
             .replace("ضرب", " * ")
             .replace("جمع", " + ")
             .replace("تقسیم", " / ")
             .replace("درصد", " % ")
             .replace("منفی", " - ")
             .replace("ممیز", ".");
        // Spoken Persian amount multipliers and currency words.
        s = s.replaceAll("([0-9]+(?:\\.[0-9]+)?)\\s*میلیون", "$1 * 1000000")
             .replaceAll("([0-9]+(?:\\.[0-9]+)?)\\s*هزار", "$1 * 1000")
             .replace("تومان", " ").replace("تومن", " ").replace("ریال", " ");
        // Ignore common conversational filler, but reject unknown words later.
        s = s.replace("اوبا", " ").replace("لطفا", " ").replace("لطفاً", " ");
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:[0-9]+(?:\\.[0-9]+)?|[()+*/%\\-])")
            .matcher(s);
        int last = 0;
        while (m.find()) {
            String gap = s.substring(last, m.start()).trim();
            if (!gap.isEmpty()) throw new IllegalArgumentException("عبارت شامل کلمه یا عمل ناشناخته است: " + gap);
            tokens.add(m.group());
            last = m.end();
        }
        if (!s.substring(last).trim().isEmpty())
            throw new IllegalArgumentException("عبارت کامل تشخیص داده نشد: " + s.substring(last).trim());
        if (tokens.isEmpty()) throw new IllegalArgumentException("عدد یا عبارت ریاضی پیدا نشد");
        Parser p = new Parser(tokens);
        double result = p.expression();
        if (p.hasNext()) throw new IllegalArgumentException("عبارت ریاضی کامل نیست");
        if (Double.isNaN(result) || Double.isInfinite(result))
            throw new ArithmeticException("نتیجه قابل محاسبه نیست");
        return result;
    }

    private static String normalizeDigits(String s) {
        char[] fa = {'۰','۱','۲','۳','۴','۵','۶','۷','۸','۹'};
        char[] ar = {'٠','١','٢','٣','٤','٥','٦','٧','٨','٩'};
        for (int i=0;i<10;i++) {
            s = s.replace(fa[i], (char)('0'+i)).replace(ar[i], (char)('0'+i));
        }
        return s.replace('٫','.').replace('،',' ').replace(',', ' ');
    }

    private static final class Parser {
        private final List<String> t;
        private int i = 0;
        Parser(List<String> tokens) { t = tokens; }
        boolean hasNext() { return i < t.size(); }

        double expression() {
            double value = term();
            while (hasNext() && (peek("+") || peek("-"))) {
                String op = t.get(i++);
                double rhs = term();
                value = op.equals("+") ? value + rhs : value - rhs;
            }
            return value;
        }
        double term() {
            double value = percentage();
            while (hasNext() && (peek("*") || peek("/"))) {
                String op = t.get(i++);
                double rhs = percentage();
                if (op.equals("*")) value *= rhs;
                else {
                    if (rhs == 0d) throw new ArithmeticException("تقسیم بر صفر ممکن نیست");
                    value /= rhs;
                }
            }
            return value;
        }
        double percentage() {
            double value = unary();
            // A spoken percent sign is postfix: "10 درصد" means 0.10.
            while (hasNext() && peek("%")) {
                i++;
                value /= 100d;
            }
            return value;
        }
        double unary() {
            if (hasNext() && peek("+")) { i++; return unary(); }
            if (hasNext() && peek("-")) { i++; return -unary(); }
            if (!hasNext()) throw new IllegalArgumentException("عدد بعد از عملگر وجود ندارد");
            if (peek("(")) {
                i++;
                double v = expression();
                if (!hasNext() || !peek(")")) throw new IllegalArgumentException("پرانتز بسته نشده است");
                i++;
                return v;
            }
            String token = t.get(i++);
            if (token.equals(")") || token.matches("[+*/%-]"))
                throw new IllegalArgumentException("در این قسمت عدد لازم است");
            return Double.parseDouble(token);
        }
        boolean peek(String value) { return hasNext() && t.get(i).equals(value); }
    }
}
