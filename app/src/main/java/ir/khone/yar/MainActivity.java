package ir.khone.yar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.provider.Settings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Calendar;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.json.JSONException;

public class MainActivity extends Activity {
    private LinearLayout root, cards;
    private TextView status, recent, remindersView, inventoryView;
    private SharedPreferences prefs;
    private SpeechRecognizer recognizer;
    private Button wakeButton;
    private TextToSpeech tts;
    private OfflinePersianTts offlinePersianTts;
    private OfflineVoskSpeechEngine offlineEngine;
    private boolean downloadingVoskModel = false;
    private String pendingMode = "auto";
    private long incomeTotal = 0, expenseTotal = 0;
    private final ArrayList<String> entries = new ArrayList<>();
    private static final int AUDIO_PERMISSION = 44;
    private static final int BACKUP_CREATE = 71, BACKUP_RESTORE = 72;
    private static final int HOME_DOC_CAMERA = 73, HOME_DOC_GALLERY = 74;
    private int dark = Color.rgb(32, 52, 105), green = Color.rgb(0, 157, 112), cream = Color.rgb(244, 248, 255);
    private int cardIndex = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("khoneyar", MODE_PRIVATE);
        incomeTotal = prefs.getLong("income", 0);
        expenseTotal = prefs.getLong("expense", 0);
        loadEntries();
        tts = new TextToSpeech(this, result -> {
            if (tts == null || result != TextToSpeech.SUCCESS) return;
            Locale persian = new Locale("fa", "IR");
            // Prefer a locally installed Persian voice so speech can work without a network.
            // Android cannot provide a voice that is not installed on the device; if none is
            // available offline, keep the system Persian default as a compatibility fallback.
            try {
                android.speech.tts.Voice offlinePersian = null;
                android.speech.tts.Voice anyPersian = null;
                for (android.speech.tts.Voice voice : tts.getVoices()) {
                    Locale voiceLocale = voice.getLocale();
                    if (voiceLocale == null || !"fa".equalsIgnoreCase(voiceLocale.getLanguage())) continue;
                    if (anyPersian == null) anyPersian = voice;
                    if (!voice.isNetworkConnectionRequired()
                            && ("IR".equalsIgnoreCase(voiceLocale.getCountry()) || voiceLocale.getCountry().isEmpty())) {
                        offlinePersian = voice;
                        break;
                    }
                }
                if (offlinePersian != null) {
                    tts.setVoice(offlinePersian);
                } else {
                    int languageResult = tts.setLanguage(persian);
                    if (languageResult < TextToSpeech.LANG_AVAILABLE && anyPersian != null) {
                        tts.setVoice(anyPersian);
                    }
                }
            } catch (Exception ignored) {
                tts.setLanguage(persian);
            }
        });
        offlinePersianTts = new OfflinePersianTts(this);
        buildUi();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("تشخیص گفتار روی این گوشی در دسترس نیست. سرویس گفتار فارسی گوشی را فعال کنید.");
        }
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(cream);
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("🏠 OBA", 27, dark, true); brand.setGravity(Gravity.CENTER);
        content.addView(brand, lp(-1, -2, 0, 4, 0, 1));
        TextView subtitle = text("مدیریت خانه، فقط با حرف زدن", 16, Color.DKGRAY, false); subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, lp(-1, -2, 0, 0, 0, 8));

        TextView categoryTitle = text("دسته‌بندی هزینه‌ها", 18, dark, true);
        content.addView(categoryTitle, lp(-1,-2,0,2,0,4));
        String[][] categoryButtons = {
            {"🏠 خانه", "خانه"}, {"🚗 ماشین", "ماشین"}, {"🏪 مغازه", "مغازه"},
            {"🏫 مدرسه", "مدرسه"}, {"🏥 سلامت", "سلامت"}, {"📦 سایر", "سایر"}
        };
        for (int i = 0; i < categoryButtons.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, categoryButtons.length); j++) {
                final String category = categoryButtons[j][1];
                Button categoryButton = button(categoryButtons[j][0], Color.WHITE, dark, 15);
                categoryButton.setMinHeight(dp(42));
                categoryButton.setOnClickListener(v -> showCategorySubgroups(category));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
                p.setMargins(dp(3), dp(2), dp(3), dp(2));
                row.addView(categoryButton, p);
            }
            content.addView(row, lp(-1,-2,0,0,0,0));
        }

        Button speak = button("🎙  صحبت کن؛ OBA گوش می‌دهد", Color.rgb(0, 150, 220), Color.WHITE, 20);
        speak.setMinHeight(dp(52)); speak.setOnClickListener(v -> startVoice("auto"));
        content.addView(speak, lp(-1, -2, 0, 4, 0, 6));

        wakeButton = button(prefs.getBoolean("wake_enabled", false) ? "🔕 خاموش کردن شنیدن نام OBA" : "🎧 فعال‌سازی شنیدن نام OBA در صفحه قفل", Color.rgb(124, 83, 210), Color.WHITE, 16);
        wakeButton.setMinHeight(dp(44));
        wakeButton.setOnClickListener(v -> toggleWakeService());
        content.addView(wakeButton, lp(-1, -2, 0, 0, 0, 10));
        status = text("مثلاً بگو: «هزینه ۸۰ هزار تومان برای نان»", 15, dark, false);
        status.setGravity(Gravity.CENTER); status.setPadding(dp(8),dp(10),dp(8),dp(10));
        content.addView(status, lp(-1,-2,0,0,0,12));

        // Compact access to the archive and existing utilities, without changing the category layout.
        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        Button archiveBtn = button("📚 آرشیو", Color.WHITE, dark, 10);
        archiveBtn.setOnClickListener(v -> showExpenseArchive());
        Button docsBtn = button("📄 مدارک", Color.WHITE, dark, 10);
        docsBtn.setOnClickListener(v -> showHomeDocuments());
        Button audioBtn = button("🎧 صداها", Color.WHITE, dark, 10);
        audioBtn.setOnClickListener(v -> showAudioArchive());
        Button backupBtn = button("💾 پشتیبان", Color.WHITE, dark, 10);
        backupBtn.setOnClickListener(v -> showBackupOptions());
        Button chartBtn = button("📊 هزینه‌ها", Color.WHITE, dark, 10);
        chartBtn.setOnClickListener(v -> showExpenseCharts());
        for (Button b : new Button[]{archiveBtn, docsBtn, audioBtn, backupBtn, chartBtn}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(54), 1);
            bp.setMargins(dp(2), dp(2), dp(2), dp(2));
            b.setMinHeight(dp(54));
            b.setPadding(dp(2), dp(2), dp(2), dp(2));
            b.setMaxLines(2);
            toolsRow.addView(b, bp);
        }
        content.addView(toolsRow, lp(-1, -2, 0, 2, 0, 2));
        Button calendarBtn = button("🗓️ تقویم مخارج روزانه", Color.rgb(224, 240, 255), dark, 15);
        calendarBtn.setMinHeight(dp(48));
        calendarBtn.setOnClickListener(v -> showExpenseCalendar());
        content.addView(calendarBtn, lp(-1, -2, 0, 2, 0, 6));
        Button smartBtn = button("🧠 تحلیل هوشمند هزینه‌ها و راهکار صرفه‌جویی", Color.rgb(255, 244, 220), dark, 14);
        smartBtn.setMinHeight(dp(44));
        smartBtn.setOnClickListener(v -> showSmartExpenseAnalysis());
        content.addView(smartBtn, lp(-1, -2, 0, 2, 0, 4));
        // Keep every home-screen action reachable on short displays by scrolling the content.
        ScrollView homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(false);
        homeScroll.setClipToPadding(false);
        homeScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(homeScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView foot = text("نسخهٔ اولیه • اطلاعات روی همین گوشی ذخیره می‌شود", 12, Color.GRAY, false); foot.setGravity(Gravity.CENTER);
        root.addView(foot, lp(-1,-2,0,8,0,0));
        setContentView(root);
        refresh();
    }




    private static final int[] CHART_COLORS = new int[]{Color.rgb(52,152,219), Color.rgb(46,204,113), Color.rgb(243,156,18), Color.rgb(155,89,182), Color.rgb(231,76,60), Color.rgb(26,188,156), Color.rgb(241,196,15)};

    private void showSmartExpenseAnalysis() {
        java.util.LinkedHashMap<String, Long> categories = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Long> subgroups = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Long> items = new java.util.LinkedHashMap<>();
        String[] names = new String[]{"خانه", "مدرسه", "ماشین", "مغازه", "سلامت", "سایر"};
        long all = 0;
        for (String name : names) {
            long amount = prefs.getLong("oba_chart_cat_" + name, 0L);
            if (amount > 0) { categories.put(name, amount); all += amount; }
            for (String subgroup : subgroupNames(name)) {
                long sub = prefs.getLong("oba_chart_sub_" + name + "_" + subgroup, 0L);
                if (sub > 0) subgroups.put(name + " / " + subgroup, sub);
            }
        }
        // Use only explicitly described, categorized purchases for item-level insights.
        for (String name : names) {
            for (String subgroup : subgroupNames(name)) {
                String prefix = "oba_smart_item_" + name + "_" + subgroup + "_";
                java.util.Map<String, ?> allPrefs = prefs.getAll();
                for (String key : allPrefs.keySet()) {
                    if (!key.startsWith(prefix)) continue;
                    Object value = allPrefs.get(key);
                    if (value instanceof Long && ((Long)value) > 0) {
                        String label = key.substring(prefix.length()).replace('_', ' ').trim();
                        items.put(name + " / " + label, ((Long)value));
                    }
                }
            }
        }
        if (categories.isEmpty() && subgroups.isEmpty() && prefs.getString("oba_expense_history", "").isEmpty()) {
            new AlertDialog.Builder(this).setTitle("🧠 تحلیل هوشمند هزینه‌ها")
                .setMessage("برای تحلیل دقیق، هزینه‌ها را از دکمه‌های دسته‌بندی ثبت کن و در شرح خرید نام مورد را هم بنویس؛ مثلاً «نان» یا «سینما». هرچه ثبت‌ها کامل‌تر باشد، پیشنهادها مفیدتر می‌شوند.")
                .setPositiveButton("متوجه شدم", null).show();
            return;
        }
        StringBuilder report = new StringBuilder();
        report.append("🧠 گزارش هزینه‌ها\n\n");
        if (all > 0) report.append("مجموع هزینه‌های دسته‌بندی‌شده: ").append(fmt(all)).append(" تومان\n\n");
        java.util.ArrayList<java.util.Map.Entry<String, Long>> ranked = new java.util.ArrayList<>(subgroups.entrySet());
        java.util.Collections.sort(ranked, (a,b) -> Long.compare(b.getValue(), a.getValue()));
        if (!ranked.isEmpty()) {
            report.append("بیشترین محل‌های خرج ثبت‌شده:\n");
            int limit = Math.min(5, ranked.size());
            for (int i=0; i<limit; i++) {
                java.util.Map.Entry<String,Long> e = ranked.get(i);
                double pct = all > 0 ? e.getValue()*100.0/all : 0;
                report.append("• ").append(e.getKey()).append(" — ").append(fmt(e.getValue())).append(" تومان");
                if (all > 0) report.append(" ( ").append(String.format(Locale.getDefault(), "%.1f", pct)).append("٪ از کل )");
                report.append("\n");
            }
            report.append("\n");
        }
        java.util.ArrayList<java.util.Map.Entry<String, Long>> topItems = new java.util.ArrayList<>(items.entrySet());
        java.util.Collections.sort(topItems, (a,b) -> Long.compare(b.getValue(), a.getValue()));
        if (!topItems.isEmpty()) {
            report.append("خریدهای دارای شرح که بیشترین مبلغ را دارند:\n");
            for (int i=0; i<Math.min(5, topItems.size()); i++) {
                java.util.Map.Entry<String,Long> e=topItems.get(i);
                report.append("• ").append(e.getKey()).append(" — ").append(fmt(e.getValue())).append(" تومان\n");
            }
            report.append("\n");
        }
        // Compare the last 30 days with the previous 30 days using dated records collected from now on.
        long now = System.currentTimeMillis();
        long day30 = 30L * 24L * 60L * 60L * 1000L;
        long currentStart = now - day30;
        long previousStart = now - 2L * day30;
        java.util.LinkedHashMap<String, Long> currentItems = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Long> previousItems = new java.util.LinkedHashMap<>();
        String history = prefs.getString("oba_expense_history", "");
        for (String line : history.split("\\n")) {
            String[] fields = line.split("\\t", -1);
            if (fields.length < 5) continue;
            try {
                long time = Long.parseLong(fields[0]);
                long amount = Long.parseLong(fields[4]);
                String item = fields[3].trim();
                if (item.isEmpty() || amount <= 0) continue;
                String key = item.toLowerCase(Locale.ROOT);
                if (time >= currentStart && time <= now) currentItems.put(key, currentItems.containsKey(key) ? currentItems.get(key) + amount : amount);
                else if (time >= previousStart && time < currentStart) previousItems.put(key, previousItems.containsKey(key) ? previousItems.get(key) + amount : amount);
            } catch (Exception ignored) {}
        }
        java.util.ArrayList<String> rising = new java.util.ArrayList<>();
        for (String item : currentItems.keySet()) {
            long currentAmount = currentItems.get(item);
            long previousAmount = previousItems.containsKey(item) ? previousItems.get(item) : 0L;
            if (previousAmount > 0 && currentAmount >= previousAmount * 1.2d) {
                double increase = (currentAmount - previousAmount) * 100.0 / previousAmount;
                rising.add(item + " — " + fmt(currentAmount) + " تومان (افزایش " + String.format(Locale.getDefault(), "%.0f", increase) + "٪)");
            }
        }
        if (!rising.isEmpty()) {
            report.append("⚠️ مواردی که در ۳۰ روز اخیر بیشتر خرج داشته‌اند (نسبت به ۳۰ روز قبل):\n");
            for (int i = 0; i < Math.min(5, rising.size()); i++) report.append("• ").append(rising.get(i)).append("\n");
            report.append("\n");
        } else if (!history.trim().isEmpty()) {
            report.append("📅 مقایسهٔ ۳۰ روزه: هنوز موردی با افزایش قابل‌مقایسه پیدا نشد؛ برای نتیجهٔ بهتر، ثبت هزینه‌ها را ادامه بده.\n\n");
        } else {
            report.append("📅 مقایسهٔ زمانی از امروز شروع می‌شود؛ هزینه‌های جدید با تاریخ ذخیره می‌شوند تا در آینده افزایش خرج قابل‌شناسایی باشد.\n\n");
        }
        report.append("💡 پیشنهادهای صرفه‌جویی:\n")
            .append("• برای نان و خوراکی‌ها، خرید روزانه را با نیاز واقعی خانواده هماهنگ کن تا دورریز کم شود.\n")
            .append("• برای سینما و تفریح، یک سقف ماهانه تعیین کن و پیش از خرید بلیت، هزینهٔ کل خانواده را بررسی کن.\n")
            .append("• هزینه‌های تکراری را هفته‌ای یک‌بار مرور کن و برای هر مورد سقف قابل‌قبول بگذار.\n\n")
            .append("توجه: این گزارش بر اساس مبالغ ثبت‌شده است؛ تا وقتی سابقهٔ زمانی کافی وجود نداشته باشد، اوبا نمی‌تواند با قطعیت بگوید خرجی نسبت به ماه قبل زیاد شده است. برای هشدار دقیق‌تر، نام خرید و مبلغ را در شرح هزینه ثبت کن.");
        new AlertDialog.Builder(this).setTitle("🧠 تحلیل هوشمند هزینه‌ها")
            .setMessage(report.toString()).setPositiveButton("باشه", null).show();
    }

    private void showExpenseCharts() {
        java.util.LinkedHashMap<String, Long> categories = new java.util.LinkedHashMap<>();
        String[] names = new String[]{"خانه", "مدرسه", "ماشین", "مغازه", "سلامت", "سایر"};
        for (String name : names) {
            long value = prefs.getLong("oba_chart_cat_" + name, 0L);
            if (value > 0) categories.put(name, value);
        }
        if (categories.isEmpty()) {
            // Older records predate chart totals; use visible saved entries as a best-effort starting point.
            for (String row : entries) {
                String[] parts = row.split("\\s*•\\s*");
                if (parts.length < 3) continue;
                String category = parts[0].trim();
                if (!java.util.Arrays.asList(names).contains(category)) continue;
                long amount = parseChartAmount(parts[parts.length - 1]);
                if (amount > 0) categories.put(category, categories.containsKey(category) ? categories.get(category) + amount : amount);
            }
        }
        if (categories.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("نمودار هزینه‌ها")
                .setMessage("هنوز هزینهٔ دسته‌بندی‌شده‌ای برای نمایش نمودار ثبت نشده است. از دکمه‌های دسته‌بندی خانه، مدرسه یا ماشین هزینه ثبت کن.")
                .setPositiveButton("باشه", null).show();
            return;
        }
        showPieDialog("سهم هر بخش از کل هزینه‌ها", categories, null);
    }

    private long parseChartAmount(String value) {
        String digits = value.replaceAll("[^0-9۰-۹٠-٩]", "").replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
        try { return Long.parseLong(digits); } catch (Exception e) { return 0; }
    }

    private void showPieDialog(String title, java.util.LinkedHashMap<String, Long> values, String parentCategory) {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(12), dp(8), dp(12), dp(4));
        long total = 0; for (Long v : values.values()) total += v;
        TextView totalText = text("مجموع: " + fmt(total) + " تومان", 15, dark, true); totalText.setGravity(Gravity.CENTER);
        panel.addView(totalText, lp(-1, -2, 0, 0, 0, 4));
        PieChartView chart = new PieChartView(values, total, name -> {
            if (parentCategory != null) return;
            java.util.LinkedHashMap<String, Long> subs = new java.util.LinkedHashMap<>();
            String[] known = subgroupNames(name);
            for (String subgroup : known) {
                long amount = prefs.getLong("oba_chart_sub_" + name + "_" + subgroup, 0L);
                if (amount > 0) subs.put(subgroup, amount);
            }
            if (!subs.isEmpty()) showPieDialog("جزئیات «" + name + "»", subs, name);
            else Toast.makeText(this, "برای این بخش هنوز جزئیات زیرگروه ثبت‌شده‌ای نیست.", Toast.LENGTH_SHORT).show();
        });
        panel.addView(chart, new LinearLayout.LayoutParams(-1, dp(250)));
        for (java.util.Map.Entry<String, Long> e : values.entrySet()) {
            double pct = total == 0 ? 0 : e.getValue() * 100.0 / total;
            TextView legend = text("●  " + e.getKey() + "   " + String.format(Locale.getDefault(), "%.1f%%", pct) + "   •   " + fmt(e.getValue()) + " تومان", 14, dark, false);
            legend.setTextColor(CHART_COLORS[Math.abs(e.getKey().hashCode()) % CHART_COLORS.length]);
            panel.addView(legend, lp(-1, -2, 0, 2, 0, 2));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(panel)
            .setNegativeButton(parentCategory == null ? "بستن" : "بازگشت به نمودار اصلی", (d, w) -> { if (parentCategory != null) showExpenseCharts(); }).create();
        dialog.show();
    }

    private String[] subgroupNames(String category) {
        if ("خانه".equals(category)) return new String[]{"مواد غذایی", "لوازم خانه", "قبض و خدمات", "اجاره و تعمیرات", "نظافت", "سایر هزینه‌های خانه"};
        if ("ماشین".equals(category)) return new String[]{"سوخت", "روغن و فیلتر", "تسمه تایم و تعمیرات", "بیمه", "لاستیک", "پارکینگ و عوارض", "سایر هزینه‌های ماشین"};
        if ("مدرسه".equals(category)) return new String[]{"شهریه", "کتاب و لوازم‌التحریر", "رفت‌وآمد", "لباس فرم", "فعالیت‌ها", "سایر هزینه‌های مدرسه"};
        if ("مغازه".equals(category)) return new String[]{"خرید کالا", "اجاره مغازه", "قبض و خدمات", "حقوق", "تعمیرات", "سایر هزینه‌های مغازه"};
        if ("سلامت".equals(category)) return new String[]{"دکتر", "دارو", "آزمایش", "بیمه درمانی", "دندان‌پزشکی", "سایر هزینه‌های سلامت"};
        return new String[]{"خرید متفرقه", "هدیه", "سفر", "سایر"};
    }

    private class PieChartView extends View {
        private final java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        private final java.util.ArrayList<Long> amounts = new java.util.ArrayList<>();
        private final long total;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.function.Consumer<String> onSlice;
        private final android.graphics.RectF oval = new android.graphics.RectF();
        private float radius, cx, cy;
        PieChartView(java.util.LinkedHashMap<String, Long> data, long sum, java.util.function.Consumer<String> click) {
            super(MainActivity.this); total = sum; onSlice = click;
            for (java.util.Map.Entry<String, Long> e : data.entrySet()) { labels.add(e.getKey()); amounts.add(e.getValue()); }
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas); if (total <= 0 || amounts.isEmpty()) return;
            radius = Math.min(getWidth() * 0.38f, getHeight() * 0.42f); cx = getWidth() / 2f; cy = getHeight() / 2f;
            oval.set(cx-radius, cy-radius, cx+radius, cy+radius);
            float start = -90f;
            for (int i=0; i<amounts.size(); i++) {
                float sweep = (float)(amounts.get(i) * 360.0 / total);
                paint.setColor(CHART_COLORS[i % CHART_COLORS.length]); paint.setStyle(Paint.Style.FILL);
                canvas.drawArc(oval, start, sweep, true, paint);
                if (sweep >= 18) {
                    double mid = Math.toRadians(start + sweep/2f); float tx = cx + (float)Math.cos(mid)*radius*0.62f; float ty = cy + (float)Math.sin(mid)*radius*0.62f;
                    paint.setColor(Color.WHITE); paint.setTextSize(dp(12)); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(String.format(Locale.getDefault(), "%.0f%%", amounts.get(i)*100.0/total), tx, ty, paint);
                }
                start += sweep;
            }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_UP || radius <= 0) return true;
            float dx=event.getX()-cx, dy=event.getY()-cy; if (Math.sqrt(dx*dx+dy*dy)>radius) return true;
            double angle = Math.toDegrees(Math.atan2(dy,dx)) + 90; if (angle < 0) angle += 360;
            double cursor=0; for (int i=0;i<amounts.size();i++) { cursor += amounts.get(i)*360.0/total; if (angle <= cursor) { onSlice.accept(labels.get(i)); break; } }
            return true;
        }
    }

    private void showBackupOptions() {
        new AlertDialog.Builder(this).setTitle("پشتیبان‌گیری")
            .setItems(new String[]{"ساخت فایل پشتیبان", "بازیابی فایل پشتیبان"}, (d, which) -> {
                if (which == 0) createBackup(); else chooseBackup();
            }).setNegativeButton("بستن", null).show();
    }

    // Convert Gregorian dates to the Jalali (Solar Hijri) calendar for Persian display.
    private int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] gdm = {0,31,((gy%4==0 && gy%100!=0)||gy%400==0)?29:28,31,30,31,30,31,31,30,31,30,31};
        int jy = (gy <= 1600) ? 0 : 979;
        gy -= (gy <= 1600) ? 621 : 1600;
        int days = 365 * gy + (gy / 4) - (gy / 100) + (gy / 400);
        for (int i = 0; i < gm; i++) days += gdm[i];
        days += gd - 1;
        int jdn = days - 79;
        int jnp = jdn / 12053;
        jdn %= 12053;
        jy += 33 * jnp + 4 * (jdn / 1461);
        jdn %= 1461;
        if (jdn >= 366) {
            jy += (jdn - 1) / 365;
            jdn = (jdn - 1) % 365;
        }
        int jm, jd;
        if (jdn < 186) { jm = 1 + jdn / 31; jd = 1 + jdn % 31; }
        else { jm = 7 + (jdn - 186) / 30; jd = 1 + (jdn - 186) % 30; }
        return new int[]{jy, jm, jd};
    }

    private int jalaliMonthLength(int jy, int jm) {
        if (jm <= 6) return 31;
        if (jm <= 11) return 30;
        // Leap-year check in the 33-year Jalali cycle, adequate for calendar navigation.
        int r = jy % 33;
        return (r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30) ? 30 : 29;
    }

    private String jalaliDateText(int gy, int gm, int gd) {
        int[] j = gregorianToJalali(gy, gm, gd);
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"};
        return j[2] + " " + months[j[1]-1] + " " + j[0];
    }

    private void showExpenseCalendar() {
        Calendar today = Calendar.getInstance();
        int[] jToday = gregorianToJalali(today.get(Calendar.YEAR), today.get(Calendar.MONTH)+1, today.get(Calendar.DAY_OF_MONTH));
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"};
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView prompt = new TextView(this);
        prompt.setText("تاریخ شمسی را انتخاب کن");
        prompt.setTextSize(16);
        prompt.setGravity(Gravity.CENTER);
        panel.addView(prompt, lp(-1, -2, 0, 0, 0, 10));
        android.widget.Spinner monthSpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> monthAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, months);
        monthSpinner.setAdapter(monthAdapter);
        monthSpinner.setSelection(jToday[1]-1);
        panel.addView(monthSpinner, lp(-1, -2, 0, 0, 0, 8));
        android.widget.EditText yearInput = new android.widget.EditText(this);
        yearInput.setSingleLine(true);
        yearInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        yearInput.setHint("سال شمسی، مثل " + jToday[0]);
        yearInput.setText(String.valueOf(jToday[0]));
        panel.addView(yearInput, lp(-1, -2, 0, 0, 0, 8));
        android.widget.Spinner daySpinner = new android.widget.Spinner(this);
        panel.addView(daySpinner, lp(-1, -2, 0, 0, 0, 8));
        Runnable refreshDays = () -> {
            int year;
            try { year = Integer.parseInt(yearInput.getText().toString().trim()); } catch (Exception e) { year = jToday[0]; }
            int month = monthSpinner.getSelectedItemPosition() + 1;
            int count = jalaliMonthLength(year, month);
            String[] days = new String[count];
            for (int i=0; i<count; i++) days[i] = String.valueOf(i+1);
            android.widget.ArrayAdapter<String> dayAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, days);
            daySpinner.setAdapter(dayAdapter);
            if (year == jToday[0] && month == jToday[1]) daySpinner.setSelection(Math.min(jToday[2]-1, count-1));
        };
        monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { refreshDays.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        yearInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) refreshDays.run(); });
        refreshDays.run();
        new AlertDialog.Builder(this).setTitle("تقویم شمسی مخارج")
            .setView(panel)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("نمایش مخارج", (dialog, which) -> {
                int jy;
                try { jy = Integer.parseInt(yearInput.getText().toString().trim()); } catch (Exception e) { jy = jToday[0]; }
                int jm = monthSpinner.getSelectedItemPosition()+1;
                int jd = daySpinner.getSelectedItemPosition()+1;
                // Convert Jalali selection to a Gregorian day by searching nearby dates.
                Calendar probe = Calendar.getInstance();
                probe.set(jy+621, Math.max(0, jm-1), jd, 0, 0, 0);
                probe.set(Calendar.MILLISECOND, 0);
                Calendar found = null;
                for (int offset=-220; offset<=220; offset++) {
                    Calendar candidate = (Calendar) probe.clone();
                    candidate.add(Calendar.DAY_OF_MONTH, offset);
                    int[] j = gregorianToJalali(candidate.get(Calendar.YEAR), candidate.get(Calendar.MONTH)+1, candidate.get(Calendar.DAY_OF_MONTH));
                    if (j[0]==jy && j[1]==jm && j[2]==jd) { found = candidate; break; }
                }
                if (found == null) { Toast.makeText(this, "تاریخ شمسی معتبر نیست", Toast.LENGTH_SHORT).show(); return; }
                Calendar selected = (Calendar) found.clone();
                selected.set(Calendar.HOUR_OF_DAY, 0); selected.set(Calendar.MINUTE, 0); selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0);
                long start = selected.getTimeInMillis();
                selected.add(Calendar.DAY_OF_MONTH, 1);
                long end = selected.getTimeInMillis();
                String history = prefs.getString("oba_expense_history", "");
                java.util.LinkedHashMap<String, Long> totalsByCategory = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, java.util.List<String>> detailsByCategory = new java.util.LinkedHashMap<>();
                String[] categories = {"خانه", "مدرسه", "ماشین", "سلامت", "سایر"};
                for (String category : categories) { totalsByCategory.put(category, 0L); detailsByCategory.put(category, new java.util.ArrayList<>()); }
                long dayTotal = 0L;
                if (!history.trim().isEmpty()) for (String row : history.split("\\n")) {
                    String[] parts = row.split("\\t", -1);
                    if (parts.length < 5) continue;
                    try {
                        long timestamp = Long.parseLong(parts[0]);
                        if (timestamp < start || timestamp >= end) continue;
                        String category = parts[1].trim();
                        if (!totalsByCategory.containsKey(category)) category = "سایر";
                        long amount = Long.parseLong(parts[4]);
                        totalsByCategory.put(category, totalsByCategory.get(category) + amount); dayTotal += amount;
                        String subgroup = parts[2].trim(), item = parts[3].trim();
                        detailsByCategory.get(category).add((subgroup.isEmpty() ? "" : subgroup + " — ") + item + " : " + fmt(amount) + " تومان");
                    } catch (Exception ignored) {}
                }
                StringBuilder report = new StringBuilder("مجموع مخارج: " + fmt(dayTotal) + " تومان\\n\\n");
                for (String category : categories) {
                    report.append("● ").append(category).append(": ").append(fmt(totalsByCategory.get(category))).append(" تومان\\n");
                    for (String detail : detailsByCategory.get(category)) report.append("   • ").append(detail).append("\\n");
                    report.append("\\n");
                }
                if (dayTotal == 0) report.append("برای این روز هزینه‌ای در آرشیو ثبت نشده است.");
                new AlertDialog.Builder(this).setTitle("مخارج " + jalaliDateText(jy, jm, jd)).setMessage(report.toString().trim()).setPositiveButton("باشه", null).setNeutralButton("روز دیگر", (d,w) -> showExpenseCalendar()).show();
            }).show();
    }

    private void showExpenseArchive() {
        String all = prefs.getString("entries", "");
        if (all.trim().isEmpty()) {
            new AlertDialog.Builder(this).setTitle("آرشیو هزینه‌ها")
                .setMessage("هنوز موردی ثبت نشده است. از دسته‌بندی‌ها هزینه ثبت کن.")
                .setPositiveButton("باشه", null).show();
            return;
        }
        String[] rows = all.split("\\n");
        String[] display = new String[rows.length];
        for (int i = 0; i < rows.length; i++) display[i] = rows[i];
        new AlertDialog.Builder(this).setTitle("آرشیو ثبت‌ها")
            .setItems(display, (d, which) -> new AlertDialog.Builder(this)
                .setTitle("جزئیات ثبت").setMessage(display[which])
                .setPositiveButton("باشه", null).show())
            .setNegativeButton("بستن", null).show();
    }

    private void showAudioArchive() {
        File folder = new File(getFilesDir(), "audio_records");
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".3gp"));
        if (files == null || files.length == 0) {
            new AlertDialog.Builder(this).setTitle("آرشیو صداها")
                .setMessage("هنوز صدایی ضبط نشده است. برای ضبط، دکمهٔ زیر را بزن.")
                .setPositiveButton("ضبط صدای جدید", (d, w) -> startAudioRecording())
                .setNegativeButton("بستن", null).show();
            return;
        }
        String[] labels = new String[files.length + 1];
        labels[0] = "🎙 ضبط صدای جدید";
        for (int i = 0; i < files.length; i++) labels[i + 1] = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(files[i].lastModified())) + " • " + files[i].getName();
        new AlertDialog.Builder(this).setTitle("آرشیو صداهای ذخیره‌شده")
            .setItems(labels, (d, which) -> {
                if (which == 0) { startAudioRecording(); return; }
                File chosen = files[which - 1];
                new AlertDialog.Builder(this).setTitle("پخش فایل صوتی")
                    .setMessage(labels[which]).setPositiveButton("پخش", (dd, ww) -> playAudioFile(chosen))
                    .setNegativeButton("حذف", (dd, ww) -> {
                        if (chosen.delete()) Toast.makeText(this, "فایل صوتی حذف شد.", Toast.LENGTH_SHORT).show();
                        else Toast.makeText(this, "حذف فایل ممکن نشد.", Toast.LENGTH_SHORT).show();
                    }).setNeutralButton("بستن", null).show();
            }).setNegativeButton("بستن", null).show();
    }

    private android.media.MediaRecorder recorder;
    private File currentAudioFile;
    private void startAudioRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
            Toast.makeText(this, "پس از اجازهٔ میکروفون، دوباره آرشیو صداها را باز کن.", Toast.LENGTH_LONG).show();
            return;
        }
        if (recorder != null) {
            Toast.makeText(this, "ضبط دیگری در حال اجراست.", Toast.LENGTH_SHORT).show(); return;
        }
        File folder = new File(getFilesDir(), "audio_records");
        if (!folder.exists()) folder.mkdirs();
        currentAudioFile = new File(folder, "OBA_audio_" + System.currentTimeMillis() + ".3gp");
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new android.media.MediaRecorder(this) : new android.media.MediaRecorder();
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(currentAudioFile.getAbsolutePath());
            recorder.prepare(); recorder.start();
            new AlertDialog.Builder(this).setTitle("ضبط صدا")
                .setMessage("ضبط فعال است. برای پایان ضبط، «پایان و ذخیره» را بزن.")
                .setCancelable(false)
                .setPositiveButton("پایان و ذخیره", (d, w) -> stopAudioRecording())
                .setNegativeButton("لغو ضبط", (d, w) -> { stopAudioRecording(); if (currentAudioFile != null) currentAudioFile.delete(); })
                .show();
        } catch (Exception e) {
            releaseRecorder();
            Toast.makeText(this, "شروع ضبط صدا ممکن نشد؛ مجوز میکروفون را بررسی کن.", Toast.LENGTH_LONG).show();
        }
    }
    private void stopAudioRecording() {
        try {
            if (recorder != null) { recorder.stop(); recorder.reset(); recorder.release(); recorder = null; }
            if (currentAudioFile != null && currentAudioFile.isFile() && currentAudioFile.length() > 0)
                Toast.makeText(this, "صدا در آرشیو خصوصی برنامه ذخیره شد.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (currentAudioFile != null) currentAudioFile.delete();
            Toast.makeText(this, "ضبط خیلی کوتاه بود یا ذخیره صدا انجام نشد.", Toast.LENGTH_LONG).show();
        } finally { releaseRecorder(); }
    }
    private void releaseRecorder() {
        if (recorder != null) { try { recorder.reset(); } catch (Exception ignored) {} try { recorder.release(); } catch (Exception ignored) {} recorder = null; }
    }
    private void playAudioFile(File file) {
        try {
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(p -> p.release());
            player.setOnErrorListener((p, what, extra) -> { p.release(); Toast.makeText(this, "پخش فایل ممکن نشد.", Toast.LENGTH_SHORT).show(); return true; });
            player.prepare(); player.start();
        } catch (Exception e) { Toast.makeText(this, "پخش فایل صوتی ممکن نشد.", Toast.LENGTH_LONG).show(); }
    }

    private void showHomeDocuments() {
        String[] options = new String[]{"📷 عکس گرفتن از مدرک", "🖼 انتخاب تصویر از گالری", "📂 مشاهده مدارک ذخیره‌شده"};
        new AlertDialog.Builder(this).setTitle("اسکن مدارک خانه")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    try {
                        Intent camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                        if (camera.resolveActivity(getPackageManager()) != null) startActivityForResult(camera, HOME_DOC_CAMERA);
                        else Toast.makeText(this, "دوربین در دسترس نیست.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) { Toast.makeText(this, "باز کردن دوربین ممکن نشد.", Toast.LENGTH_LONG).show(); }
                } else if (which == 1) {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    pick.setType("image/*");
                    startActivityForResult(pick, HOME_DOC_GALLERY);
                } else showSavedHomeDocuments();
            }).setNegativeButton("بستن", null).show();
    }

    private void saveHomeDocument(Bitmap bitmap, String defaultName) {
        try {
            File folder = new File(getFilesDir(), "home_documents");
            if (!folder.exists() && !folder.mkdirs()) throw new java.io.IOException("Cannot create folder");
            String fileName = "home_doc_" + System.currentTimeMillis() + ".jpg";
            File file = new File(folder, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out); }
            EditText nameInput = new EditText(this);
            nameInput.setText(defaultName);
            nameInput.setHint("مثلاً کارت ملی، شناسنامه، سند خانه");
            new AlertDialog.Builder(this).setTitle("نام مدرک")
                .setMessage("تصویر در حافظهٔ خصوصی برنامه ذخیره می‌شود.")
                .setView(nameInput).setNegativeButton("لغو", (d,w) -> appendHomeDocument(fileName, defaultName))
                .setPositiveButton("ذخیره", (d,w) -> {
                    String name = nameInput.getText().toString().trim();
                    appendHomeDocument(fileName, name.isEmpty() ? defaultName : name);
                }).show();
        } catch (Exception e) { Toast.makeText(this, "ذخیره تصویر مدرک انجام نشد.", Toast.LENGTH_LONG).show(); }
    }

    private void appendHomeDocument(String fileName, String name) {
        String saved = prefs.getString("home_documents_v1", "");
        String row = fileName + "|" + name.replace("|", " ").replace("\n", " ");
        prefs.edit().putString("home_documents_v1", saved.isEmpty() ? row : saved + "\n" + row).apply();
        Toast.makeText(this, "مدرک در آرشیو مدارک خانه ذخیره شد.", Toast.LENGTH_LONG).show();
    }

    private void showSavedHomeDocuments() {
        String saved = prefs.getString("home_documents_v1", "");
        if (saved.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("مدارک خانه").setMessage("هنوز مدرکی ذخیره نشده است.").setPositiveButton("باشه", null).show();
            return;
        }
        String[] rows = saved.split("\n");
        String[] labels = new String[rows.length];
        for (int i=0;i<rows.length;i++) {
            String[] p = rows[i].split("\\|",2);
            labels[i] = p.length > 1 ? p[1] : p[0];
        }
        new AlertDialog.Builder(this).setTitle("مدارک ذخیره‌شده").setItems(labels, (d,which) -> {
            String[] p = rows[which].split("\\|",2);
            File imageFile = new File(new File(getFilesDir(),"home_documents"), p[0]);
            if (!imageFile.isFile()) { Toast.makeText(this, "فایل تصویر پیدا نشد.", Toast.LENGTH_LONG).show(); return; }
            android.widget.ImageView preview = new android.widget.ImageView(this);
            preview.setImageBitmap(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
            preview.setAdjustViewBounds(true);
            ScrollView scroll = new ScrollView(this); scroll.addView(preview);
            new AlertDialog.Builder(this).setTitle(labels[which]).setView(scroll).setPositiveButton("بستن", null).show();
        }).setNegativeButton("بستن", null).show();
    }

    private void createBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "OBA-backup.json");
            startActivityForResult(intent, BACKUP_CREATE);
        } catch (Exception e) { Toast.makeText(this,"ذخیره فایل پشتیبان ممکن نشد.",Toast.LENGTH_LONG).show(); }
    }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, BACKUP_RESTORE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == HOME_DOC_CAMERA) {
            if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                Object image = data.getExtras().get("data");
                if (image instanceof Bitmap) saveHomeDocument((Bitmap) image, "مدرک خانه");
            }
            return;
        }
        if (requestCode == HOME_DOC_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) saveHomeDocument(bitmap, "مدرک خانه");
                    else Toast.makeText(this, "خواندن تصویر ممکن نشد.", Toast.LENGTH_LONG).show();
                } catch (Exception e) { Toast.makeText(this, "باز کردن تصویر ممکن نشد.", Toast.LENGTH_LONG).show(); }
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (requestCode == BACKUP_CREATE) {
                JSONObject json = new JSONObject();
                json.put("app","OBA");
                json.put("version",1);
                JSONObject values = new JSONObject();
                for (java.util.Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    // Service state is transient and must never be restored from a backup file.
                    if ("wake_enabled".equals(key)) continue;
                    if (isValidBackupPreference(key, value)) values.put(key, value);
                }
                json.put("preferences",values);
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new java.io.IOException("No output stream");
                    out.write(json.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                Toast.makeText(this,"نسخه پشتیبان در محل انتخابی گوشی ذخیره شد.",Toast.LENGTH_LONG).show();
            } else if (requestCode == BACKUP_RESTORE) {
                StringBuilder raw = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(data.getData()), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line=reader.readLine())!=null) {
                        if (raw.length() + line.length() > 1000000) {
                            Toast.makeText(this,"فایل پشتیبان بیش از حد بزرگ است.",Toast.LENGTH_LONG).show(); return;
                        }
                        raw.append(line);
                    }
                }
                JSONObject json = new JSONObject(raw.toString());
                if (!"OBA".equals(json.optString("app")) || json.optInt("version", -1) != 1 || !json.has("preferences") || !(json.opt("preferences") instanceof JSONObject)) {
                    Toast.makeText(this,"این فایل، نسخه پشتیبان معتبر OBA نیست.",Toast.LENGTH_LONG).show(); return;
                }
                JSONObject checkedValues = json.getJSONObject("preferences");
                java.util.Iterator<String> checkedKeys = checkedValues.keys();
                while (checkedKeys.hasNext()) {
                    String key = checkedKeys.next();
                    if (!isValidBackupPreference(key, checkedValues.opt(key))) {
                        Toast.makeText(this,"فایل پشتیبان دارای دادهٔ نامعتبر یا پشتیبانی‌نشده است؛ اطلاعات تغییر نکرد.",Toast.LENGTH_LONG).show(); return;
                    }
                }
                new AlertDialog.Builder(this).setTitle("بازیابی اطلاعات")
                    .setMessage("اطلاعات فعلی با اطلاعات داخل نسخه پشتیبان جایگزین می‌شوند. ادامه می‌دهی؟")
                    .setNegativeButton("انصراف",null).setPositiveButton("بازیابی", (dialog,which) -> {
                        try {
                            JSONObject values = json.getJSONObject("preferences");
                            SharedPreferences.Editor editor = prefs.edit().clear();
                            java.util.Iterator<String> keys = values.keys();
                            while(keys.hasNext()) {
                                String key=keys.next(); Object value=values.get(key);
                                if(value instanceof String) editor.putString(key,(String)value);
                                else if ("income".equals(key) || "expense".equals(key) || "home_budget".equals(key)) editor.putLong(key,((Number)value).longValue());
                                else if (key.startsWith("stock_")) editor.putInt(key,((Number)value).intValue());
                            }
                            editor.apply();
                            incomeTotal=prefs.getLong("income",0); expenseTotal=prefs.getLong("expense",0);
                            entries.clear(); loadEntries(); refresh();
                            Toast.makeText(this,"اطلاعات بازیابی شد. برای بازنشانی یادآوری‌ها برنامه را یک‌بار باز نگه دار.",Toast.LENGTH_LONG).show();
                        } catch(Exception ex) { Toast.makeText(this,"بازیابی انجام نشد: فایل را بررسی کن.",Toast.LENGTH_LONG).show(); }
                    }).show();
            }
        } catch(Exception e) { Toast.makeText(this,"خطا در فایل پشتیبان؛ دوباره تلاش کن.",Toast.LENGTH_LONG).show(); }
    }

    private boolean isValidBackupPreference(String key, Object value) {
        if (key == null || value == null) return false;
        if ("income".equals(key) || "expense".equals(key) || "home_budget".equals(key)) {
            if (!(value instanceof Number)) return false;
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) && number >= 0 && number <= Long.MAX_VALUE && number == Math.rint(number);
        }
        if ("entries".equals(key) || "vehicleRecords".equals(key) || "home_documents_v1".equals(key)) {
            return value instanceof String && ((String) value).length() <= 20000;
        }
        if (key.matches("reminder_[0-9]{1,10}")) {
            return value instanceof String && ((String) value).length() <= 2000;
        }
        if (key.startsWith("stock_") && key.length() > 6 && key.length() <= 106) {
            if (!(value instanceof Number)) return false;
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) && number >= 0 && number <= Integer.MAX_VALUE && number == Math.rint(number);
        }
        // Unknown keys and runtime-only flags are deliberately not imported.
        return false;
    }

    private void showCategorySubgroups(String category) {
        String[] groups;
        if ("خانه".equals(category)) groups = new String[]{"مواد غذایی", "لوازم خانه", "قبض و خدمات", "اجاره و تعمیرات", "نظافت", "سایر هزینه‌های خانه"};
        else if ("ماشین".equals(category)) groups = new String[]{"سوخت", "روغن و فیلتر", "تسمه تایم و تعمیرات", "بیمه", "لاستیک", "پارکینگ و عوارض", "سایر هزینه‌های ماشین"};
        else if ("مغازه".equals(category)) groups = new String[]{"خرید کالا", "اجاره مغازه", "قبض و خدمات", "حقوق", "تعمیرات", "سایر هزینه‌های مغازه"};
        else if ("مدرسه".equals(category)) groups = new String[]{"شهریه", "کتاب و لوازم‌التحریر", "رفت‌وآمد", "لباس فرم", "فعالیت‌ها", "سایر هزینه‌های مدرسه"};
        else if ("سلامت".equals(category)) groups = new String[]{"دکتر", "دارو", "آزمایش", "بیمه درمانی", "دندان‌پزشکی", "سایر هزینه‌های سلامت"};
        else groups = new String[]{"خرید متفرقه", "هدیه", "سفر", "سایر"};
        if ("خانه".equals(category)) {
            groups = new String[]{"مواد غذایی", "لوازم خانه", "قبض و خدمات", "اجاره و تعمیرات", "نظافت", "📄 اسکن مدارک خانه", "سایر هزینه‌های خانه"};
        }
        final String[] subgroupOptions = groups;
        new AlertDialog.Builder(this).setTitle("زیرگروه «" + category + "»")
            .setItems(subgroupOptions, (dialog, which) -> {
                if ("خانه".equals(category) && "📄 اسکن مدارک خانه".equals(subgroupOptions[which])) showHomeDocuments();
                else showEntryMethodDialog(category, subgroupOptions[which]);
            }).setNegativeButton("انصراف", null).show();
    }

    private void showEntryMethodDialog(String category, String subgroup) {
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(18), dp(8), dp(18), dp(8));

        Button manual = button("⌨️  ثبت دستی", Color.WHITE, Color.WHITE, 17);
        manual.setMinHeight(dp(54));
        manual.setOnClickListener(v -> {
            // Close the method chooser before opening the manual form.
            showCategoryExpenseForm(category, subgroup);
        });
        choices.addView(manual, lp(-1, -2, 0, 4, 0, 8));

        Button voice = button("🎙  ثبت صوتی", Color.WHITE, Color.WHITE, 17);
        voice.setMinHeight(dp(54));
        voice.setOnClickListener(v -> {
            status.setText("برای «" + category + " / " + subgroup + "» هزینه و مبلغ را بگو.");
            startVoice("auto");
        });
        choices.addView(voice, lp(-1, -2, 0, 0, 0, 4));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(category + " ← " + subgroup)
            .setView(choices)
            .setNegativeButton("انصراف", null)
            .create();
        manual.setOnClickListener(v -> {
            dialog.dismiss();
            showCategoryExpenseForm(category, subgroup);
        });
        voice.setOnClickListener(v -> {
            dialog.dismiss();
            status.setText("برای «" + category + " / " + subgroup + "» هزینه و مبلغ را بگو.");
            startVoice("auto");
        });
        dialog.show();
    }

    private void showCategoryExpenseForm(String category, String subgroup) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText amountInput = new EditText(this);
        amountInput.setHint("مبلغ به تومان (مثلاً ۱۵۰۰۰۰)");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(amountInput, lp(-1,-2,0,0,0,8));
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("شرح اختیاری (مثلاً خرید نان)");
        descriptionInput.setSingleLine(true);
        form.addView(descriptionInput, lp(-1,-2,0,0,0,4));
        new AlertDialog.Builder(this).setTitle(category + " ← " + subgroup)
            .setView(form).setNegativeButton("لغو", null)
            .setPositiveButton("ثبت هزینه", (dialog, which) -> {
                String rawAmount = amountInput.getText().toString().trim()
                    .replace("۰","0").replace("۱","1").replace("۲","2").replace("۳","3")
                    .replace("۴","4").replace("۵","5").replace("۶","6").replace("۷","7")
                    .replace("۸","8").replace("۹","9").replace("٬","").replace(",","");
                long amount;
                try { amount = Long.parseLong(rawAmount); }
                catch (Exception e) { Toast.makeText(this, "مبلغ معتبر وارد کن.", Toast.LENGTH_LONG).show(); return; }
                if (amount <= 0) { Toast.makeText(this, "مبلغ باید بیشتر از صفر باشد.", Toast.LENGTH_LONG).show(); return; }
                if (amount > Long.MAX_VALUE - expenseTotal) { Toast.makeText(this, "مبلغ بیش از حد مجاز است.", Toast.LENGTH_LONG).show(); return; }
                String description = descriptionInput.getText().toString().trim();
                expenseTotal += amount;
                prefs.edit().putLong("expense", expenseTotal).apply();
                String entryTitle = subgroup + (description.isEmpty() ? "" : " — " + description);
                addEntry(category, entryTitle, amount);
                refresh();
                String confirmation = "هزینه " + fmt(amount) + " تومان در «" + category + " / " + subgroup + "» ثبت شد.";
                status.setText(confirmation);
                say(confirmation);
                Toast.makeText(this, confirmation, Toast.LENGTH_LONG).show();
            }).show();
    }

    private void addVoiceCard(String title, String desc, String mode) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(12));
        card.setBackground(round(Color.WHITE,16));
        TextView t = text(title,18,dark,true); card.addView(t);
        TextView d = text(desc,13,Color.DKGRAY,false); card.addView(d, lp(-1,-2,0,4,0,0));
        card.setOnClickListener(v -> startVoice(mode));
        LinearLayout.LayoutParams p = lp(-1,-2,0,3,0,5); cards.addView(card,p);
    }

    private void startVoice(String mode) {
        pendingMode = mode;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
            return;
        }

        File voskModel = OfflineModelDownloader.getInstalledModelDirectory(this);
        boolean sherpaReady = offlinePersianTts != null && offlinePersianTts.isReady();
        if (voskModel != null) {
            startOfflineVoice(mode, voskModel);
            if (!sherpaReady) prepareSherpaModel();
            return;
        }

        // Start the required speech-recognition model download immediately on the voice-button tap.
        ensureVoskModelAndStart(mode);
    }

    private void prepareSherpaModel() {
        if (offlinePersianTts == null) offlinePersianTts = new OfflinePersianTts(this);
        if (offlinePersianTts.isReady()) return;
        offlinePersianTts.ensureModelDownloaded(new OfflinePersianTts.DownloadCallback() {
            @Override public void onProgress(long downloadedBytes, long totalBytes) {
                if (status == null || isFinishing()) return;
                if (totalBytes > 0) {
                    int percent = (int) Math.min(100L, downloadedBytes * 100L / totalBytes);
                    status.setText("دانلود صدای فارسی Sherpa: " + percent + "% (" + (downloadedBytes / (1024 * 1024)) + " مگابایت از " + (totalBytes / (1024 * 1024)) + ")");
                } else {
                    status.setText("دانلود صدای فارسی Sherpa: " + (downloadedBytes / (1024 * 1024)) + " مگابایت دریافت شد…");
                }
            }
            @Override public void onReady() {
                if (isFinishing()) return;
                if (status != null) status.setText("صدای فارسی Sherpa آماده است؛ تشخیص گفتار با Vosk جداگانه اجرا می‌شود.");
            }
            @Override public void onError(String message) {
                if (isFinishing()) return;
                String detail = (message == null || message.trim().isEmpty())
                        ? "علت خطای آماده‌سازی صدای آفلاین مشخص نشد."
                        : message;
                if (status != null) status.setText("صدای آفلاین آماده نشد: " + detail);
                Toast.makeText(MainActivity.this, detail, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void ensureVoskModelAndStart(String mode) {
        File modelDirectory = OfflineModelDownloader.getInstalledModelDirectory(this);
        if (modelDirectory != null) {
            startOfflineVoice(mode, modelDirectory);
            return;
        }
        if (downloadingVoskModel) {
            if (status != null) status.setText("دانلود مدل Vosk در حال انجام است؛ کمی صبر کن…");
            return;
        }
        downloadingVoskModel = true;
        if (status != null) status.setText("در حال دانلود مدل تشخیص گفتار فارسی Vosk…");
        OfflineModelDownloader.downloadModel(this, new OfflineModelDownloader.Callback() {
            @Override public void onProgress(long downloadedBytes, long totalBytes) {
                if (status == null || isFinishing()) return;
                if (totalBytes > 0) {
                    int percent = (int) Math.min(100L, downloadedBytes * 100L / totalBytes);
                    status.setText("دانلود مدل Vosk: " + percent + "%");
                } else {
                    status.setText("در حال دانلود مدل Vosk…");
                }
            }
            @Override public void onInstalled(File installedDirectory) {
                downloadingVoskModel = false;
                if (isFinishing()) return;
                if (status != null) status.setText("مدل تشخیص گفتار فارسی Vosk نصب شد؛ میکروفون را شروع می‌کنم…");
                startOfflineVoice(mode, installedDirectory);
                prepareSherpaModel();
            }
            @Override public void onError(String message) {
                downloadingVoskModel = false;
                if (isFinishing()) return;
                if (status != null) status.setText("دانلود مدل Vosk ناموفق بود: " + message);
                Toast.makeText(MainActivity.this, "دانلود مدل Vosk ناموفق بود: " + message, Toast.LENGTH_LONG).show();
                prepareSherpaModel();
            }
        });
    }

    private void startOfflineVoice(String mode, File modelDirectory) {
        try {
            if (recognizer != null) { recognizer.destroy(); recognizer = null; }
            if (offlineEngine != null) { offlineEngine.stop(); offlineEngine = null; }
            offlineEngine = new OfflineVoskSpeechEngine();
            offlineEngine.start(this, modelDirectory, new OfflineVoskSpeechEngine.Listener() {
                @Override public void onStatus(String message) {
                    if (status != null && !isFinishing()) status.setText(message);
                }
                @Override public void onText(String text) {
                    if (status != null && !isFinishing()) status.setText("شنیدم: «" + text + "»");
                    handleVoice(text);
                }
                @Override public void onError(String message) {
                    if (status != null && !isFinishing()) status.setText(message);
                    if (!isFinishing()) Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    if (offlineEngine != null) { offlineEngine.stop(); offlineEngine = null; }
                }
            });
        } catch (Exception e) {
            status.setText("شروع گفتار آفلاین ممکن نشد؛ دوباره دکمهٔ صوتی را بزن.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startVoice(pendingMode);
        else if (requestCode == 46 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startWakeService();
        else { status.setText("برای فرمان صوتی باید اجازهٔ میکروفون بدهی."); say("برای استفاده صوتی اجازه میکروفون لازم است"); }
    }

    private void toggleWakeService() {
        if (prefs.getBoolean("wake_enabled", false)) {
            stopService(new Intent(this, VoiceWakeService.class));
            prefs.edit().putBoolean("wake_enabled", false).apply();
            wakeButton.setText("🎧 فعال‌سازی شنیدن نام OBA در صفحه قفل");
            status.setText("شنیدن نام OBA خاموش شد.");
            say("شنیدن نام OBA خاموش شد");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 46);
            return;
        }
        startWakeService();
    }

    private void startWakeService() {
        Intent service = new Intent(this, VoiceWakeService.class);
        service.setAction(VoiceWakeService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            prefs.edit().putBoolean("wake_enabled", true).apply();
            if (wakeButton != null) wakeButton.setText("🔕 خاموش کردن شنیدن نام OBA");
            status.setText("شنیدن نام OBA فعال شد. بگو «اوبا»؛ برای توقف، دکمه خاموش کردن را بزن.");
            say("OBA آماده شنیدن نامش است");
        } catch (Exception e) {
            prefs.edit().putBoolean("wake_enabled", false).apply();
            status.setText("فعال‌سازی در پس‌زمینه ممکن نشد. برنامه را باز نگه دار و مجوز میکروفون را بررسی کن.");
            if (wakeButton != null) wakeButton.setText("🎧 فعال‌سازی شنیدن نام OBA در صفحه قفل");
        }
    }

    private boolean tryRegisterCategorizedExpense(String raw, String normalized) {
        String category = null;
        String subgroup = null;
        String[][] known = {
            {"خانه", "مواد غذایی", "لوازم خانه", "قبض و خدمات", "اجاره و تعمیرات", "نظافت", "سایر هزینه‌های خانه"},
            {"ماشین", "سوخت", "روغن و فیلتر", "تسمه تایم و تعمیرات", "بیمه", "لاستیک", "پارکینگ و عوارض", "سایر هزینه‌های ماشین"},
            {"مغازه", "خرید کالا", "اجاره مغازه", "قبض و خدمات", "حقوق", "تعمیرات", "سایر هزینه‌های مغازه"},
            {"مدرسه", "شهریه", "کتاب و لوازم‌التحریر", "رفت‌وآمد", "لباس فرم", "فعالیت‌ها", "سایر هزینه‌های مدرسه"},
            {"سلامت", "دکتر", "دارو", "آزمایش", "بیمه درمانی", "دندان‌پزشکی", "سایر هزینه‌های سلامت"},
            {"سایر", "خرید متفرقه", "هدیه", "سفر", "سایر"}
        };
        String[][] aliases = {
            {"خانه", "مواد غذایی", "لوازم خانه", "قبض و خدمات", "اجاره و تعمیرات", "نظافت", "سایر هزینه های خانه"},
            {"ماشین", "سوخت", "روغن و فیلتر", "تسمه تایم و تعمیرات", "بیمه", "لاستیک", "پارکینگ و عوارض", "سایر هزینه های ماشین"},
            {"مغازه", "خرید کالا", "اجاره مغازه", "قبض و خدمات", "حقوق", "تعمیرات", "سایر هزینه های مغازه"},
            {"مدرسه", "شهریه", "کتاب و لوازم التحریر", "رفت و آمد", "لباس فرم", "فعالیت ها", "سایر هزینه های مدرسه"},
            {"سلامت", "دکتر", "دارو", "آزمایش", "بیمه درمانی", "دندان پزشکی", "سایر هزینه های سلامت"},
            {"سایر", "خرید متفرقه", "هدیه", "سفر", "سایر"}
        };
        String[][] aliasesByCategory = {
            {"خانه","مواد غذایی","لوازم خانه","قبض و خدمات","اجاره و تعمیرات","نظافت","سایر هزینه های خانه"},
            {"ماشین","سوخت","روغن و فیلتر","تسمه تایم و تعمیرات","بیمه","لاستیک","پارکینگ و عوارض","سایر هزینه های ماشین"},
            {"مغازه","خرید کالا","اجاره مغازه","قبض و خدمات","حقوق","تعمیرات","سایر هزینه های مغازه"},
            {"مدرسه","شهریه","کتاب و لوازم التحریر","رفت و آمد","لباس فرم","فعالیت ها","سایر هزینه های مدرسه"},
            {"سلامت","دکتر","دارو","آزمایش","بیمه درمانی","دندان پزشکی","سایر هزینه های سلامت"},
            {"سایر","خرید متفرقه","هدیه","سفر","سایر"}
        };
        int catIndex = -1, subIndex = -1;
        for (int i=0; i<known.length; i++) {
            String cat = normalize(known[i][0]);
            if (normalized.contains(cat)) { catIndex=i; break; }
        }
        if (catIndex < 0) return false;
        for (int j=1; j<known[catIndex].length; j++) {
            String candidate = normalize(known[catIndex][j]);
            if (normalized.contains(candidate)) { subIndex=j; break; }
        }
        if (subIndex < 0 && catIndex == 0 && (normalized.contains("لوازم خانگی") || normalized.contains("وسایل خانه"))) {
            subIndex = 2; // Common spoken names for the "لوازم خانه" subgroup.
        }
        if (subIndex < 0) {
            for (int j=1; j<aliasesByCategory[catIndex].length; j++) {
                String candidate = normalize(aliasesByCategory[catIndex][j]);
                if (!candidate.isEmpty() && normalized.contains(candidate)) { subIndex=j; break; }
            }
        }
        if (subIndex < 0) return false;
        long amount = parseAmount(normalized);
        if (amount <= 0) return false;
        String title = raw;
        String[] remove = {"اوبا","اوبآ","خانه","ماشین","مغازه","مدرسه","سلامت","سایر",
            known[catIndex][subIndex], "تومان","تومن","هزار","میلیون","هزینه","خرج","خرید کردم","خریدم","خریداری کردم","به مبلغ","به قیمت"};
        for (String word : remove) if (word != null && !word.isEmpty()) title = title.replace(word, " ");
        title = title.replaceAll("[0-9۰-۹,،]+", " ").replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) title = "هزینه " + known[catIndex][subIndex];
        expenseTotal += amount;
        prefs.edit().putLong("expense", expenseTotal).apply();
        addEntry("هزینه • " + known[catIndex][0] + " • " + known[catIndex][subIndex], title, amount);
        String message = "ثبت شد؛ " + known[catIndex][0] + "، " + known[catIndex][subIndex] + "، " + fmt(amount) + " تومان، " + title;
        status.setText(message);
        say(message);
        refresh();
        return true;
    }

    private void handleVoice(String raw) {
        String s = normalize(raw);
        status.setText("شنیدم: «" + raw + "»");

        // Accept a complete spoken expense command, including category and subgroup,
        // in one utterance: "خانه، لوازم خانه، پنج میلیون، خرید ماشین ظرف‌شویی".
        if (tryRegisterCategorizedExpense(raw, s)) return;

        String mode = pendingMode;
        if (mode.equals("auto")) {
            if (contains(s,"بودجه خانه","بودجه ماهانه","تنظیم بودجه")) mode="budget";
            else if (contains(s,"موجودی خانه","چی داریم","چه چیزهایی داریم")) { refreshInventory(); status.setText(inventoryView.getText()); say("موجودی خانه روی صفحه نمایش داده شد"); return; }
            else if (contains(s,"مانده","خلاصه حساب","گزارش مالی","جمع هزینه","جمع درآمد")) {
                String summary="درآمد کل "+fmt(incomeTotal)+" تومان. هزینه کل "+fmt(expenseTotal)+" تومان. مانده "+fmt(incomeTotal-expenseTotal)+" تومان.";
                status.setText(summary); say(summary); return;
            } else if (contains(s,"سلامت","دکتر","پزشک","دارو","درمان","آزمایش","بیمارستان")) mode="health";
            else if (contains(s,"ماشین","خودرو","تعویض روغن","روغن موتور","کیلومتر","لنت ترمز","باتری ماشین","سرویس خودرو")) mode="vehicle";
            else if (contains(s,"یادآوری","یادم بنداز","یادآور")) mode="reminder";
            else if (contains(s,"درآمد","حقوق","فروختم","دریافت کردم")) mode="income";
            else if (contains(s,"ماشین حساب","حساب کن","جمع","ضرب","منهای","تقسیم")) mode="calc";
            else if (contains(s,"خریدم","خرید کردم","خریداری کردم","خرید به قیمت","خرید به مبلغ")) mode="shopping";
            else if (contains(s,"هزینه","خرج","پرداخت کردم","قیمت خرید")) mode="expense";
            else if (contains(s,"خرید","فهرست")) mode="shopping";
            else if (contains(s,"کار خانه","وظیفه","تمیزکاری")) mode="task";
            else mode="expense";
        }
        if (mode.equals("expense") || mode.equals("income")) {
            long amount = parseAmount(s);
            if (amount <= 0) { status.setText("مبلغ را هم بگو؛ مثلاً «هزینه پنجاه هزار تومان برای نان»"); say("لطفاً مبلغ را هم بگو"); return; }
            String title = cleanDescription(s, mode);
            if (mode.equals("income")) { incomeTotal += amount; prefs.edit().putLong("income",incomeTotal).apply(); addEntry("درآمد", title, amount); say("درآمد به مبلغ " + fmt(amount) + " تومان ثبت شد"); }
            else { expenseTotal += amount; prefs.edit().putLong("expense",expenseTotal).apply(); addEntry("هزینه", title, amount); say("هزینه " + fmt(amount) + " تومان ثبت شد"); }
            refresh(); return;
        }
        if (mode.equals("calc")) {
            try {
                double result = VoiceCalculator.evaluate(s);
                String resultText = new DecimalFormat("#,##0.####").format(result);
                String answer = "نتیجه محاسبه: " + resultText;
                status.setText(answer);
                say(answer);
            } catch (ArithmeticException ex) {
                String message = ex.getMessage() == null ? "این محاسبه ممکن نیست." : ex.getMessage();
                status.setText(message);
                say(message);
            } catch (IllegalArgumentException ex) {
                String message = "عبارت را واضح‌تر بگو؛ مثلاً «ماشین حساب ۲۰۰ جمع ۳۰ ضربدر ۲».";
                status.setText(message);
                say(message);
            }
            return;
        }
        if (mode.equals("health")) {
            long amount = parseAmount(s);
            if (amount <= 0) {
                status.setText("برای ثبت هزینه سلامت، مبلغ را هم بگو؛ مثلاً «هزینه ۳۰۰ هزار تومان دکتر».");
                say("مبلغ هزینه سلامت را هم بگو");
                return;
            }
            String title = raw.replace("تومان", "").replace("تومن", "").replace("هزار", "").replaceAll("[0-9۰-۹]+", " ").replaceAll("\\s+", " ").trim();
            if (title.isEmpty()) title = "هزینه سلامت";
            expenseTotal += amount;
            prefs.edit().putLong("expense", expenseTotal).apply();
            addEntry("سلامت", title, amount);
            String message = "هزینه سلامت به مبلغ " + fmt(amount) + " تومان ثبت شد.";
            status.setText(message); say(message); refresh(); return;
        }
        if (mode.equals("vehicle")) { handleVehicleVoice(raw, s); return; }
        if (mode.equals("shopping")) { handlePurchaseVoice(raw, s); return; }
        if (mode.equals("budget")) { handleBudgetVoice(raw, s); return; }
        if (mode.equals("stock_use")) { handleStockUse(raw); return; }
        if (mode.equals("task")) { addEntry("کار خانه", raw, 0); say("کار خانه ثبت شد"); refresh(); return; }
        if (mode.equals("reminder")) { handleReminderVoice(raw, s); return; }
        status.setText("فرمان دریافت شد: " + raw);
    }





    private void handleBudgetVoice(String raw, String s) {
        long amount = parseAmount(s);
        if (amount <= 0) {
            status.setText("مبلغ بودجه را بگو؛ مثلاً «بودجه ماهانه خانه پنج میلیون تومان»");
            say("مبلغ بودجه را بگو"); return;
        }
        prefs.edit().putLong("home_budget", amount).apply();
        long remaining = amount - expenseTotal;
        String msg = "بودجه خانه روی " + fmt(amount) + " تومان تنظیم شد. مانده بودجه با احتساب ثبت‌های فعلی " + fmt(remaining) + " تومان است.";
        status.setText(msg); say(msg); refresh();
    }

    private void handleStockUse(String raw) {
        String item = raw.replace("مصرف کردم", "").replace("تمام شده است", "").replace("تمام شده", "").replace("تمام شد", "").replace("از موجودی کم کن", "").trim();
        String key = "stock_" + item;
        int qty = prefs.getInt(key, 0);
        if (item.isEmpty() || qty <= 0) {
            String msg = "این مورد در موجودی پیدا نشد. نام کالا را مثل زمان خرید بگو.";
            status.setText(msg); say(msg); return;
        }
        prefs.edit().putInt(key, Math.max(0, qty - 1)).apply();
        String msg = "یک قلم " + item + " از موجودی کم شد.";
        status.setText(msg); say(msg); refresh();
    }

    private void handlePurchaseVoice(String raw, String s) {
        long amount = parseAmount(s);
        if (amount <= 0) {
            addEntry("فهرست خرید", raw, 0);
            status.setText("مورد خرید به فهرست اضافه شد؛ برای ثبت هزینه بگو «خریدم شیر به قیمت ۸۰ هزار تومان».");
            say("به فهرست خرید اضافه شد. برای کم شدن از بودجه، قیمت را هم بگو.");
            refresh(); return;
        }
        String item = raw.replace("خریدم", "").replace("خرید کردم", "").replace("خریداری کردم", "")
            .replace("به قیمت", " ").replace("به مبلغ", " ").replace("قیمت", " ")
            .replace("تومان", " ").replace("تومن", " ").replace("هزار", " ").replace("میلیون", " ")
            .replaceAll("[0-9۰-۹]+", " ").replaceAll("\\s+", " ").trim();
        if (item.isEmpty()) item = "خرید خانه";
        expenseTotal += amount;
        prefs.edit().putLong("expense", expenseTotal).apply();
        String key = "stock_" + item;
        int qty = prefs.getInt(key, 0);
        prefs.edit().putInt(key, qty + 1).apply();
        addEntry("خرید: " + item, raw, amount);
        long budget = prefs.getLong("home_budget", 0);
        String msg = item + " به مبلغ " + fmt(amount) + " تومان ثبت شد؛ یک قلم به موجودی اضافه شد و هزینه از حساب خانه کم شد.";
        if (budget > 0) msg += " مانده بودجه " + fmt(budget - expenseTotal) + " تومان است.";
        status.setText(msg); say(msg); refresh();
    }

    private void refreshInventory() {
        if (inventoryView == null) return;
        StringBuilder out = new StringBuilder();
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("stock_")) continue;
            try {
                int qty = prefs.getInt(key, 0);
                if (qty <= 0) continue;
                if (out.length() > 0) out.append("\\n");
                out.append("• ").append(key.substring(6)).append(" — ").append(qty).append(" قلم");
            } catch (Exception ignored) {}
        }
        inventoryView.setText(out.length() == 0 ? "هنوز خریدی در موجودی ثبت نشده است. بگو: «خریدم شیر به قیمت ۸۰ هزار تومان»." : out.toString());
    }

    private void refreshReminders() {
        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("reminder_")) continue;
            try {
                String value = prefs.getString(key, "");
                String[] parts = value.split("\\n", 4);
                if (parts.length < 3) continue;
                long when = Long.parseLong(parts[0]);
                boolean monthly = "monthly".equals(parts[1]);
                String title = parts[2];
                String date = new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(new Date(when));
                rows.add(when + "|" + "• " + title + " — " + date + (monthly ? " (هر ماه)" : ""));
            } catch (Exception ignored) {}
        }
        java.util.Collections.sort(rows, (a,b) -> Long.compare(Long.parseLong(a.substring(0,a.indexOf("|"))), Long.parseLong(b.substring(0,b.indexOf("|")))));
        StringBuilder out = new StringBuilder();
        for (String row : rows) {
            if (out.length() > 0) out.append("\n");
            out.append(row.substring(row.indexOf("|")+1));
        }
        remindersView.setText(out.length() == 0 ? "هنوز یادآوری زمان‌بندی‌شده‌ای ثبت نشده است. با صدا بگو: «یادآوری قسط فردا ساعت ۹ صبح»." : out.toString());
    }

    private void handleReminderVoice(String raw, String s) {
        if (contains(s, "لیست یادآوری", "یادآوری های من", "یادآوری‌های من", "یادآوری ها")) {
            refreshReminders();
            status.setText(remindersView.getText());
            say("یادآوری‌های فعال در بخش یادآوری‌های من نمایش داده شده‌اند");
            return;
        }
        if (contains(s, "لغو یادآوری", "حذف یادآوری", "پاک کن یادآوری")) {
            String target = raw.replace("لغو یادآوری", "").replace("حذف یادآوری", "").replace("پاک کن یادآوری", "").trim();
            int removed = 0;
            for (String key : new java.util.ArrayList<>(prefs.getAll().keySet())) {
                if (!key.startsWith("reminder_")) continue;
                try {
                    String[] parts = prefs.getString(key, "").split("\\n", 4);
                    if (parts.length >= 3 && (target.isEmpty() || parts[2].contains(target))) {
                        int rid = Integer.parseInt(key.substring("reminder_".length()));
                        ReminderReceiver.cancel(this, rid);
                        removed++;
                    }
                } catch (Exception ignored) {}
            }
            refresh();
            String msg = removed == 0 ? "یادآوری مطابق این نام پیدا نشد." : removed + " یادآوری لغو شد.";
            status.setText(msg); say(msg); return;
        }
        boolean monthly = contains(s, "هر ماه", "ماهانه");
        Calendar now = Calendar.getInstance();
        Calendar due = (Calendar) now.clone();
        int monthlyDay = -1;
        int hour = -1, minute = 0;
        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("ساعت\\s*([0-9]{1,2})(?:[:٫.]([0-9]{2}))?").matcher(s);
        if (tm.find()) {
            hour = Integer.parseInt(tm.group(1));
            if (tm.group(2) != null) minute = Integer.parseInt(tm.group(2));
            if (contains(s, "شب", "بعدازظهر", "بعد از ظهر") && hour < 12) hour += 12;
            if (contains(s, "صبح") && hour == 12) hour = 0;
            if (hour > 23 || minute > 59) hour = -1;
        }
        if (hour < 0) {
            status.setText("برای زمان‌بندی دقیق، ساعت را هم بگو؛ مثلاً «یادآوری قسط فردا ساعت ۹ صبح».");
            say("لطفاً ساعت یادآوری را هم بگو");
            return;
        }
        due.set(Calendar.HOUR_OF_DAY, hour);
        due.set(Calendar.MINUTE, minute);
        due.set(Calendar.SECOND, 0);
        due.set(Calendar.MILLISECOND, 0);

        if (monthly) {
            int day = extractReminderDay(s);
            monthlyDay = day;
            if (day < 1 || day > 31) {
                status.setText("برای یادآوری ماهانه، روز ماه را مشخص کن؛ مثلاً «یادآوری قسط روز ۲۰ هر ماه ساعت ۹ صبح».");
                say("روز ماه را هم بگو");
                return;
            }
            due.set(Calendar.DAY_OF_MONTH, Math.min(day, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
            if (!due.after(now)) {
                due.add(Calendar.MONTH, 1);
                due.set(Calendar.DAY_OF_MONTH, Math.min(day, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }
        } else if (contains(s, "پس فردا", "پس‌فردا")) {
            due.add(Calendar.DAY_OF_MONTH, 2);
        } else if (contains(s, "فردا")) {
            due.add(Calendar.DAY_OF_MONTH, 1);
        } else if (contains(s, "امروز", "امشب")) {
            if (!due.after(now)) due.add(Calendar.DAY_OF_MONTH, 1);
        } else {
            status.setText("تاریخ را هم مشخص کن؛ مثلاً «یادآوری قسط فردا ساعت ۹ صبح» یا «روز ۲۰ هر ماه ساعت ۹».");
            say("لطفاً روز یا تاریخ یادآوری را هم مشخص کن");
            return;
        }

        String title = raw.replace("یادم بنداز", "").replace("یادآوری", "").replace("یادآور", "").trim();
        title = title.replace("هر ماه", "").replace("ماهانه", "").replace("فردا", "").replace("پس فردا", "").replace("پس‌فردا", "").replace("امروز", "").replace("امشب", "").replace("صبح", "").replace("شب", "").replace("بعدازظهر", "").replace("بعد از ظهر", "");
        title = title.replaceAll("ساعت\\s*[0-9]{1,2}(?:[:٫.][0-9]{2})?", "").replaceAll("[0-9۰-۹]+", " ").replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) title = "یادآوری خانه";
        int id = (int)(System.currentTimeMillis() & 0x7fffffff);
        long when = due.getTimeInMillis();
        String saved = when + "\n" + (monthly ? "monthly" : "once") + "\n" + title + "\n" + monthlyDay;
        prefs.edit().putString("reminder_" + id, saved).apply();
        ReminderReceiver.schedule(this, id, when, title, monthly);
        String whenText = new SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(due.getTime());
        String confirmation = "یادآوری «" + title + "» برای " + whenText + (monthly ? " و تکرار ماهانه" : "") + " ثبت شد.";
        addEntry("یادآوری", confirmation, 0);
        status.setText(confirmation);
        say(confirmation);
        refresh();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 45);
        }
    }

    private int extractReminderDay(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:روز\\s*)?([0-9]{1,2})\\s*(?:ام)?\\s*(?:هر ماه|ماهانه)").matcher(s);
        if (m.find()) try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        m = java.util.regex.Pattern.compile("روز\\s*([0-9]{1,2})").matcher(s);
        if (m.find()) try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        String[] ordinals = {"سی و یکم","سی‌ویکم","سی‌ام","سی ام","بیست و نهم","بیست‌ونهم","بیست و هشتم","بیست‌وهشتم","بیست و هفتم","بیست‌وهفتم","بیست و ششم","بیست‌وششم","بیست و پنجم","بیست‌وپنجم","بیست و چهارم","بیست‌وچهارم","بیست و سوم","بیست‌وسوم","بیست و دوم","بیست‌ودوم","بیستم","نوزدهم","هجدهم","هفدهم","شانزدهم","پانزدهم","چهاردهم","سیزدهم","دوازدهم","یازدهم","دهم","نهم","هشتم","هفتم","ششم","پنجم","چهارم","سوم","دوم","اول"};
        int[] vals = {31,31,30,30,29,29,28,28,27,27,26,26,25,25,24,24,23,23,22,22,20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1};
        for (int i=0;i<ordinals.length;i++) if(s.contains(ordinals[i])) return vals[i];
        return -1;
    }

    private void handleVehicleVoice(String raw, String s) {
        boolean oil = contains(s, "روغن", "تعویض روغن");
        boolean service = oil || contains(s, "سرویس", "تعمیر", "لنت", "باتری", "لاستیک", "فیلتر", "شمع", "تسمه");
        if (!service) {
            status.setText("برای ثبت خودرو بگو چه سرویسی انجام شد؛ مثلاً تعویض روغن در کیلومتر ۸۵ هزار.");
            say("نام سرویس خودرو را بگو");
            return;
        }

        long currentKm = extractMileage(s);
        long nextKm = extractNextMileage(s);
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date());
        String kind = oil ? "تعویض روغن" : "سرویس/تعمیر خودرو";
        StringBuilder detail = new StringBuilder(kind).append(" • ").append(date);
        if (currentKm > 0) detail.append(" • کیلومتر فعلی: ").append(fmt(currentKm));
        if (nextKm > 0) detail.append(" • سرویس بعدی: ").append(fmt(nextKm)).append(" کیلومتر");

        String all = prefs.getString("vehicleRecords", "");
        String record = detail.toString();
        all = record + (all.isEmpty() ? "" : "\n" + all);
        String[] lines = all.split("\n");
        StringBuilder saved = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 30); i++) {
            if (i > 0) saved.append("\n");
            saved.append(lines[i]);
        }
        prefs.edit().putString("vehicleRecords", saved.toString()).apply();
        addEntry("ماشین خانه", record, 0);
        refresh();

        if (currentKm <= 0) {
            status.setText("نوع سرویس ثبت شد؛ برای کامل شدن سابقه، کیلومتر فعلی را هم بگو. مثلاً «تعویض روغن در کیلومتر ۸۵ هزار».");
            say("سرویس ثبت شد. برای کامل شدن سابقه، کیلومتر فعلی خودرو را هم بگو.");
        } else if (nextKm <= 0) {
            status.setText("سرویس در کیلومتر " + fmt(currentKm) + " ثبت شد. کیلومتر سرویس بعدی هنوز مشخص نشده است.");
            say(kind + " در کیلومتر " + fmt(currentKm) + " ثبت شد. برای یادآوری بر اساس کیلومتر، عدد سرویس بعدی را هم بگو.");
        } else {
            status.setText("ثبت شد: " + kind + " در کیلومتر " + fmt(currentKm) + "؛ سرویس بعدی در " + fmt(nextKm) + " کیلومتر.");
            say("ثبت شد. " + kind + " در کیلومتر " + fmt(currentKm) + " و سرویس بعدی در کیلومتر " + fmt(nextKm));
        }
    }

    private long extractMileage(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:کیلومتر\\s*)([0-9]+)\\s*هزار").matcher(s);
        if (m.find()) try { return Long.parseLong(m.group(1)) * 1000L; } catch (Exception ignored) {}
        m = java.util.regex.Pattern.compile("([0-9]+)\\s*هزار\\s*کیلومتر").matcher(s);
        if (m.find()) try { return Long.parseLong(m.group(1)) * 1000L; } catch (Exception ignored) {}
        m = java.util.regex.Pattern.compile("کیلومتر\\s*([0-9]+)").matcher(s);
        if (m.find()) try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
        m = java.util.regex.Pattern.compile("([0-9]+)\\s*کیلومتر").matcher(s);
        if (m.find()) try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
        return 0;
    }

    private long extractNextMileage(String s) {
        int idx = s.indexOf("بعدی");
        if (idx < 0) idx = s.indexOf("بعد");
        if (idx < 0 || idx >= s.length() - 1) return 0;
        return extractMileage(s.substring(idx));
    }

    private String cleanDescription(String s, String mode) {
        String x = s.replace("تومان","").replace("ریال","").replace("هزینه","").replace("خرج","").replace("مخارج","").replace("درآمد","").replace("حقوق","").trim();
        x = x.replaceAll("[0-9۰-۹]+"," ").replaceAll("\\s+"," ").trim();
        if (x.isEmpty()) return mode.equals("income") ? "درآمد" : "هزینه خانه";
        return x;
    }

    private long parseAmount(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]+").matcher(s);
        if (m.find()) {
            try {
                long n = Long.parseLong(m.group());
                if (s.contains("میلیون")) return n * 1000000L;
                if (s.contains("هزار")) return n * 1000L;
                return n;
            } catch (Exception ignored) {}
        }
        return wordNumber(s);
    }

    private long[] extractNumbers(String s) {
        java.util.ArrayList<Long> found = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]+").matcher(s);
        while(m.find()) try { found.add(Long.parseLong(m.group())); } catch(Exception ignored) {}
        if(found.isEmpty()) {
            String[] words=s.split("\\s+");
            for(String w:words) { long n=wordValue(w); if(n>0) found.add(n); }
        }
        long[] out=new long[found.size()]; for(int i=0;i<out.length;i++) out[i]=found.get(i); return out;
    }

    private long wordNumber(String s) {
        long total=0, current=0;
        for(String w:s.split("\\s+")) {
            if(w.equals("هزار")) { total += Math.max(1,current)*1000; current=0; }
            else if(w.equals("میلیون")) { total += Math.max(1,current)*1000000; current=0; }
            else { long v=wordValue(w); if(v>0) current += v; }
        }
        return total+current;
    }

    private long wordValue(String w) {
        switch(w) {
            case "یک": case "یه": case "اول": return 1;
            case "دو": case "دوتا": return 2;
            case "سه": return 3; case "چهار": return 4; case "پنج": return 5;
            case "شش": case "شیش": return 6; case "هفت": return 7; case "هشت": return 8; case "نه": return 9;
            case "ده": return 10; case "یازده": return 11; case "دوازده": return 12; case "سیزده": return 13;
            case "چهارده": return 14; case "پانزده": return 15; case "شانزده": return 16; case "هفده": return 17;
            case "هجده": return 18; case "نوزده": return 19; case "بیست": return 20; case "سی": return 30;
            case "چهل": return 40; case "پنجاه": return 50; case "شصت": return 60; case "هفتاد": return 70;
            case "هشتاد": return 80; case "نود": return 90; case "صد": return 100; case "دویست": return 200;
            case "سیصد": return 300; case "چهارصد": return 400; case "پانصد": return 500; case "هزار": return 1000;
            case "میلیون": return 1000000; default: return 0;
        }
    }

    private String normalize(String s) {
        return s.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
                .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')
                .replace("،"," ").replace("؟"," ").replace("رو"," ").replace("برای"," ").replace("تومن","تومان").trim();
    }
    private boolean contains(String s, String... words) { for(String w:words) if(s.contains(w)) return true; return false; }
    private void addEntry(String type, String title, long amount) {
        if (amount > 0 && !"درآمد".equals(type) && !"یادآوری".equals(type)) recordExpenseHistory(type, title, amount);
        if (amount > 0 && java.util.Arrays.asList("خانه", "ماشین", "مغازه", "مدرسه", "سلامت", "سایر").contains(type)) {
            prefs.edit().putLong("oba_chart_cat_" + type, prefs.getLong("oba_chart_cat_" + type, 0L) + amount).apply();
            String subgroup = title.split(" — ", 2)[0].trim();
            if (!subgroup.isEmpty()) prefs.edit().putLong("oba_chart_sub_" + type + "_" + subgroup, prefs.getLong("oba_chart_sub_" + type + "_" + subgroup, 0L) + amount).apply();
            String[] titleParts = title.split(" — ", 2);
            if (titleParts.length > 1) {
                String item = titleParts[1].trim();
                if (!item.isEmpty()) {
                    String safeItem = item.replaceAll("[^\\p{L}\\p{N} ]", "").trim().replace(' ', '_');
                    if (!safeItem.isEmpty()) {
                        String itemKey = "oba_smart_item_" + type + "_" + subgroup + "_" + safeItem;
                        prefs.edit().putLong(itemKey, prefs.getLong(itemKey, 0L) + amount).apply();
                    }
                }
            }
        }
        String row = type + " • " + title + (amount>0 ? " • " + fmt(amount) + " تومان" : "");
        entries.add(0,row); while(entries.size()>12) entries.remove(entries.size()-1);
        StringBuilder b=new StringBuilder(); for(String e:entries)b.append(e).append("\n");
        prefs.edit().putString("entries",b.toString()).apply();
    }
    private void recordExpenseHistory(String type, String title, long amount) {
        String[] parts = title.split(" — ", 2);
        String subgroup = parts.length > 1 ? parts[0].trim() : "";
        String item = (parts.length > 1 ? parts[1] : parts[0]).trim();
        long timestamp = System.currentTimeMillis();
        String row = timestamp + "\t" + type.replace('\t', ' ') + "\t" + subgroup.replace('\t', ' ') + "\t" + item.replace('\t', ' ') + "\t" + amount;
        String old = prefs.getString("oba_expense_history", "");
        String combined = row + (old.isEmpty() ? "" : "\n" + old);
        String[] rows = combined.split("\n");
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < Math.min(rows.length, 1000); i++) {
            if (i > 0) kept.append("\n");
            kept.append(rows[i]);
        }
        prefs.edit().putString("oba_expense_history", kept.toString()).apply();
    }

    private void loadEntries() {
        String all=prefs.getString("entries","");
        if(!all.isEmpty()) for(String line:all.split("\n")) if(!line.trim().isEmpty()) entries.add(line);
    }
    private void refresh() {
        if(recent!=null) { StringBuilder b=new StringBuilder(); for(String e:entries) b.append("• ").append(e).append("\n"); recent.setText(b.length()==0?"هنوز چیزی ثبت نشده است":b.toString().trim()); }
        if(remindersView!=null) refreshReminders();
        if(inventoryView!=null) refreshInventory();
    }
    private String fmt(long n) { return new DecimalFormat("#,###").format(n); }
    private void say(String s) {
        if (offlinePersianTts != null) {
            offlinePersianTts.speak(s, () -> speakWithSystemTts(s));
        } else {
            speakWithSystemTts(s);
        }
    }
    private void speakWithSystemTts(String s) {
        if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "khoneyar");
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.RIGHT); v.setTextDirection(View.TEXT_DIRECTION_RTL);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    private Button button(String s,int bg,int fg,int size) {
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(size);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        // Outer colored rim follows the button silhouette; the inset face creates a double-border 3D look.
        GradientDrawable rim = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(151, 184, 255), Color.rgb(50, 86, 214), Color.rgb(20, 34, 104)}
        );
        rim.setCornerRadius(dp(20));
        rim.setStroke(dp(1), Color.rgb(178, 204, 255));

        GradientDrawable face = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(83, 117, 239), Color.rgb(43, 63, 166), Color.rgb(24, 35, 104)}
        );
        face.setCornerRadius(dp(16));
        face.setStroke(dp(1), Color.rgb(112, 143, 255));

        android.graphics.drawable.LayerDrawable layered =
            new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{rim, face});
        layered.setLayerInset(1, dp(3), dp(3), dp(3), dp(3));
        b.setBackground(layered);
        b.setElevation(dp(5));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(48));
        return b;
    }
    private GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(58, 83, 190), color == Color.WHITE ? Color.rgb(31, 45, 126) : color}
        );
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.rgb(91, 121, 230));
        return d;
    }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    @Override protected void onDestroy() {
        if(recognizer!=null) recognizer.destroy();
        if(offlineEngine!=null) offlineEngine.stop();
        if(offlinePersianTts!=null) offlinePersianTts.shutdown();
        if(tts!=null) tts.shutdown();
        super.onDestroy();
    }
}