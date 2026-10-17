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
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONException;

public class MainActivity extends Activity {
    private LinearLayout root, cards;
    private TextView status, totals, recent, remindersView, inventoryView;
    private SharedPreferences prefs;
    private SpeechRecognizer recognizer;
    private Button wakeButton;
    private TextToSpeech tts;
    private String pendingMode = "auto";
    private long incomeTotal = 0, expenseTotal = 0;
    private final ArrayList<String> entries = new ArrayList<>();
    private static final int AUDIO_PERMISSION = 44;
    private static final int BACKUP_CREATE = 71, BACKUP_RESTORE = 72;
    private int dark = Color.rgb(32, 52, 105), green = Color.rgb(0, 157, 112), cream = Color.rgb(244, 248, 255);
    private int cardIndex = 0;
    private boolean usageGateApproved = false;
    private String pendingCategoryVoice = "";
    private static final String OBA_SUPABASE_URL = "https://sxyaujvliummbvzznejq.supabase.co";
    private static final String OBA_SUPABASE_KEY = "sb_publishable_AQdI9Ehm-lf7p1Rs7bu4BA_m60TTlen";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("khoneyar", MODE_PRIVATE);
        incomeTotal = prefs.getLong("income", 0);
        expenseTotal = prefs.getLong("expense", 0);
        loadEntries();
        tts = new TextToSpeech(this, result -> { if (tts != null) tts.setLanguage(new Locale("fa", "IR")); });
        buildUi();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("تشخیص گفتار روی این گوشی در دسترس نیست. سرویس گفتار فارسی گوشی را فعال کنید.");
        }
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(cream);
        root.setPadding(dp(18), dp(18), dp(18), dp(12));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("🏠 OBA", 29, dark, true); brand.setGravity(Gravity.CENTER);
        content.addView(brand, lp(-1, -2, 0, 8, 0, 2));
        TextView subtitle = text("مدیریت خانه، فقط با حرف زدن", 16, Color.DKGRAY, false); subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, lp(-1, -2, 0, 0, 0, 18));

        TextView categoryTitle = text("دسته‌بندی هزینه‌ها", 20, dark, true);
        content.addView(categoryTitle, lp(-1,-2,0,4,0,8));
        TextView categoryHint = text("یک دسته را انتخاب کن تا زیرگروه و فرم ثبت هزینه باز شود.", 14, Color.DKGRAY, false);
        content.addView(categoryHint, lp(-1,-2,0,0,0,8));
        String[][] categoryButtons = {
            {"🏠 خانه", "خانه"}, {"🚗 ماشین", "ماشین"}, {"🏪 مغازه", "مغازه"},
            {"🏫 مدرسه", "مدرسه"}, {"🏥 سلامت", "سلامت"}, {"📦 سایر", "سایر"}
        };
        for (String[] item : categoryButtons) {
            Button categoryButton = button(item[0], Color.WHITE, dark, 17);
            categoryButton.setMinHeight(dp(54));
            categoryButton.setOnClickListener(v -> showCategorySubgroups(item[1]));
            content.addView(categoryButton, lp(-1,-2,0,2,0,5));
        }

        Button speak = button("🎙  صحبت کن؛ OBA گوش می‌دهد", Color.rgb(0, 150, 220), Color.WHITE, 20);
        speak.setMinHeight(dp(76)); speak.setOnClickListener(v -> startVoice("auto"));
        content.addView(speak, lp(-1, -2, 0, 4, 0, 6));

        wakeButton = button("🎙 میانبر صوتی: اول بگو «اوبا»", Color.rgb(124, 83, 210), Color.WHITE, 16);
        wakeButton.setMinHeight(dp(58));
        wakeButton.setOnClickListener(v -> startVoice("wake"));
        content.addView(wakeButton, lp(-1, -2, 0, 0, 0, 10));
        status = text("مثلاً بگو: «هزینه ۸۰ هزار تومان برای نان»", 15, dark, false);
        status.setGravity(Gravity.CENTER); status.setPadding(dp(8),dp(10),dp(8),dp(10));
        content.addView(status, lp(-1,-2,0,0,0,12));

        totals = text("", 17, dark, true); totals.setGravity(Gravity.CENTER);
        totals.setPadding(dp(14),dp(18),dp(14),dp(18)); totals.setBackground(round(Color.rgb(225, 250, 239), 22));
        content.addView(totals, lp(-1,-2,0,0,0,12));

        LinearLayout alertPanel = new LinearLayout(this);
        alertPanel.setOrientation(LinearLayout.VERTICAL);
        alertPanel.setPadding(dp(14),dp(12),dp(14),dp(12));
        alertPanel.setBackground(round(Color.rgb(255,235,238),18));
        alertPanel.addView(text("🔔 هشدارها و آلارم‌ها",18,Color.rgb(150,25,35),true));
        alertPanel.addView(text("برای عبور هزینه از سقف بودجه هشدار بگیر؛ برای زمان تعویض روغن، سرویس یا هر کار دیگری هم آلارم زمان‌دار تنظیم کن.",13,dark,false),lp(-1,-2,0,5,0,8));
        Button budgetAlert = button("🎯 تنظیم سقف هزینه / بودجه",Color.WHITE,dark,15);
        budgetAlert.setOnClickListener(v -> showBudgetLimitForm());
        alertPanel.addView(budgetAlert,lp(-1,-2,0,2,0,4));
        Button maintenanceAlert = button("🛠 تنظیم آلارم زمان تعویض یا سرویس",Color.WHITE,dark,15);
        maintenanceAlert.setOnClickListener(v -> showManualMaintenanceReminder());
        alertPanel.addView(maintenanceAlert,lp(-1,-2,0,2,0,0));
        content.addView(alertPanel,lp(-1,-2,0,2,0,14));

        LinearLayout backupPanel = new LinearLayout(this); backupPanel.setOrientation(LinearLayout.VERTICAL); backupPanel.setPadding(dp(14),dp(12),dp(14),dp(12)); backupPanel.setBackground(round(Color.rgb(255,243,224),20));
        TextView backupTitle = text("🛡️ پشتیبان اطلاعات روی گوشی",17,Color.rgb(124,70,0),true); backupPanel.addView(backupTitle);
        TextView backupDesc = text("یک فایل پشتیبان در حافظه گوشی ذخیره کن تا اگر برنامه حذف شد، بتوانی اطلاعات را برگردانی.",13,dark,false); backupPanel.addView(backupDesc,lp(-1,-2,0,5,0,8));
        Button saveBackup = button("📥 ذخیره نسخه پشتیبان",Color.rgb(255,143,0),Color.WHITE,16); saveBackup.setOnClickListener(v -> createBackup()); backupPanel.addView(saveBackup,lp(-1,-2,0,2,0,4));
        Button restoreBackup = button("📤 بازیابی نسخه پشتیبان",Color.rgb(0,137,123),Color.WHITE,16); restoreBackup.setOnClickListener(v -> chooseBackup()); backupPanel.addView(restoreBackup,lp(-1,-2,0,2,0,0));
        content.addView(backupPanel,lp(-1,-2,0,8,0,14));

        TextView label = text("دسترسی سریع صوتی", 19, dark, true);
        content.addView(label, lp(-1,-2,0,6,0,8));
        cards = new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL);
        addVoiceCard("🧾 ثبت مخارج", "بگو: «خرج ۱۲۰ هزار تومان برای خرید»", "expense");
        addVoiceCard("💰 ثبت درآمد", "بگو: «درآمد ۵ میلیون تومان حقوق»", "income");
        addVoiceCard("🧮 ماشین‌حساب صوتی", "بگو: «ماشین حساب ۲۰۰۰۰ جمع ۳۰۰۰۰»", "calc");
        addVoiceCard("🛒 فهرست خرید", "بگو: «به فهرست خرید شیر اضافه کن»", "shopping");
        addVoiceCard("✅ کارهای خانه", "بگو: «کار خانه تمیز کردن آشپزخانه»", "task");
        addVoiceCard("⏰ یادآوری‌های من", "بگو: «یادآوری قسط فردا ساعت ۹ صبح» یا «یادآوری قبض روز ۲۰ هر ماه ساعت ۱۰»", "reminder");
        addVoiceCard("🚗 ماشین خانه", "بگو: «تعویض روغن در کیلومتر ۸۵ هزار؛ سرویس بعدی ۹۰ هزار کیلومتر»", "vehicle");
        addVoiceCard("🏥 سلامت", "بگو: «هزینه ۳۰۰ هزار تومان دکتر» یا «دارو ۱۵۰ هزار تومان خریدم»", "health");
        content.addView(cards);
        TextView remindersTitle = text("⏰ یادآوری‌های فعال", 19, dark, true);
        content.addView(remindersTitle, lp(-1,-2,0,12,0,6));
        remindersView = text("", 14, Color.DKGRAY, false); remindersView.setPadding(dp(12),dp(10),dp(12),dp(10)); remindersView.setBackground(round(Color.WHITE,14));
        content.addView(remindersView, lp(-1,-2,0,0,0,12));
        TextView inventoryTitle = text("🧺 موجودی خانه و خریدهای ثبت‌شده", 19, dark, true);
        content.addView(inventoryTitle, lp(-1,-2,0,8,0,6));
        inventoryView = text("", 14, Color.DKGRAY, false); inventoryView.setPadding(dp(12),dp(10),dp(12),dp(10)); inventoryView.setBackground(round(Color.WHITE,14));
        content.addView(inventoryView, lp(-1,-2,0,0,0,12));
        TextView recentTitle = text("آخرین ثبت‌ها", 19, dark, true);
        content.addView(recentTitle, lp(-1,-2,0,12,0,6));
        recent = text("", 14, Color.DKGRAY, false); recent.setPadding(dp(12),dp(10),dp(12),dp(10)); recent.setBackground(round(Color.WHITE,14));
        content.addView(recent, lp(-1,-2,0,0,0,18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        TextView foot = text("نسخهٔ اولیه • اطلاعات روی همین گوشی ذخیره می‌شود", 12, Color.GRAY, false); foot.setGravity(Gravity.CENTER);
        root.addView(foot, lp(-1,-2,0,8,0,0));
        setContentView(root);
        refresh();
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
        if ("entries".equals(key) || "vehicleRecords".equals(key)) {
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

    private String categorySlug(String category) {
        if ("خانه".equals(category)) return "home";
        if ("ماشین".equals(category)) return "vehicle";
        if ("مغازه".equals(category)) return "shop";
        if ("مدرسه".equals(category)) return "school";
        if ("سلامت".equals(category)) return "health";
        return "other";
    }

    private long categorySpent(String category) { return prefs.getLong("category_expense_" + categorySlug(category), 0L); }

    private void addCategorySpend(String category, long amount) {
        if (category == null || category.isEmpty() || amount <= 0) return;
        String key = "category_expense_" + categorySlug(category);
        long current = prefs.getLong(key, 0L);
        if (amount <= Long.MAX_VALUE - current) prefs.edit().putLong(key, current + amount).apply();
        checkBudgetAlert();
    }

    private void showBudgetLimitForm() {
        String[] categories = new String[]{"خانه", "ماشین", "مغازه", "مدرسه", "سلامت", "سایر"};
        android.widget.Spinner categoryPicker = new android.widget.Spinner(this);
        categoryPicker.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("سقف هزینه به تومان");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16),dp(8),dp(16),0);
        form.addView(text("بخش موردنظر",14,dark,false));
        form.addView(categoryPicker,lp(-1,-2,0,4,0,8));
        form.addView(input,lp(-1,-2,0,2,0,4));
        Runnable loadLimit = () -> {
            String category = categories[categoryPicker.getSelectedItemPosition()];
            long current = prefs.getLong("budget_" + categorySlug(category), 0L);
            input.setText(current > 0 ? String.valueOf(current) : "");
        };
        categoryPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { loadLimit.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        new AlertDialog.Builder(this).setTitle("تنظیم سقف هزینهٔ هر بخش")
            .setMessage("برای هر بخش سقف جداگانه تعیین کن؛ با عبور هزینهٔ همان بخش از سقف، اوبا هشدار می‌دهد.")
            .setView(form).setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره سقف", (d,w) -> {
                String category = categories[categoryPicker.getSelectedItemPosition()];
                String raw = normalize(input.getText().toString()).replace("٬","").replace(",","");
                try {
                    long limit = Long.parseLong(raw);
                    if (limit <= 0) throw new NumberFormatException();
                    prefs.edit().putLong("budget_" + categorySlug(category), limit)
                        .putBoolean("budget_alerted_" + categorySlug(category), false).apply();
                    String msg = "سقف هزینهٔ بخش " + category + " روی " + fmt(limit) + " تومان تنظیم شد.";
                    status.setText(msg); say(msg); checkBudgetAlert();
                } catch (Exception e) {
                    Toast.makeText(this, "یک مبلغ معتبر و بزرگ‌تر از صفر وارد کن.", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void showManualMaintenanceReminder() {
        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setHint("مثلاً تعویض روغن، سرویس ماشین یا تعویض فیلتر");
        new AlertDialog.Builder(this).setTitle("آلارم سرویس یا تعویض")
            .setMessage("عنوان هشدار را وارد کن؛ سپس تاریخ و ساعت را انتخاب می‌کنی.")
            .setView(titleInput).setNegativeButton("لغو", null)
            .setPositiveButton("انتخاب تاریخ", (d,w) -> {
                String title = titleInput.getText().toString().trim();
                if (title.isEmpty()) title = "زمان سرویس یا تعویض رسیده است";
                final String reminderTitle = title;
                Calendar now = Calendar.getInstance();
                new android.app.DatePickerDialog(this, (picker, year, month, day) -> {
                    Calendar due = Calendar.getInstance();
                    due.set(Calendar.YEAR, year); due.set(Calendar.MONTH, month); due.set(Calendar.DAY_OF_MONTH, day);
                    new android.app.TimePickerDialog(this, (timePicker, hour, minute) -> {
                        due.set(Calendar.HOUR_OF_DAY, hour); due.set(Calendar.MINUTE, minute);
                        due.set(Calendar.SECOND, 0); due.set(Calendar.MILLISECOND, 0);
                        if (due.getTimeInMillis() <= System.currentTimeMillis()) {
                            Toast.makeText(this, "تاریخ و ساعت باید در آینده باشد.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        int id = (int)(System.currentTimeMillis() & 0x7fffffff);
                        String saved = due.getTimeInMillis() + "\nonce\n" + reminderTitle + "\n-1";
                        prefs.edit().putString("reminder_" + id, saved).apply();
                        ReminderReceiver.schedule(this, id, due.getTimeInMillis(), reminderTitle, false);
                        addEntry("هشدار", reminderTitle, 0);
                        refresh();
                        String msg = "آلارم «" + reminderTitle + "» برای " +
                            new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(due.getTime()) + " تنظیم شد.";
                        status.setText(msg); say(msg);
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 45);
                        }
                    }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
            }).show();
    }

    private void triggerBudgetAlert(String category, long limit) {
        String slug = categorySlug(category), key = "budget_alerted_" + slug;
        if (prefs.getBoolean(key, false)) return;
        prefs.edit().putBoolean(key, true).apply();
        String msg = "هشدار اوبا! هزینهٔ بخش " + category + " از سقف مجاز " + fmt(limit) + " تومان عبور کرده است.";
        status.setText(msg); say(msg); Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                String channelId = "oba_budget_alerts";
                if (Build.VERSION.SDK_INT >= 26) {
                    android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId,"هشدار سقف هزینه",android.app.NotificationManager.IMPORTANCE_HIGH);
                    channel.enableVibration(true); manager.createNotificationChannel(channel);
                }
                android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(this,channelId)
                    : new android.app.Notification.Builder(this).setPriority(android.app.Notification.PRIORITY_HIGH);
                builder.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("هشدار اوبا: " + category)
                    .setContentText("هزینه از سقف " + fmt(limit) + " تومان عبور کرده است.")
                    .setAutoCancel(true).setDefaults(android.app.Notification.DEFAULT_SOUND | android.app.Notification.DEFAULT_VIBRATE);
                manager.notify(8701 + Math.abs(slug.hashCode() % 1000),builder.build());
            }
        } catch (SecurityException ignored) {}
    }

    private void checkBudgetAlert() {
        String[] categories = new String[]{"خانه", "ماشین", "مغازه", "مدرسه", "سلامت", "سایر"};
        for (String category : categories) {
            String slug = categorySlug(category);
            long limit = prefs.getLong("budget_" + slug, 0L), spent = categorySpent(category);
            if (limit > 0 && spent > limit) triggerBudgetAlert(category, limit);
            else prefs.edit().putBoolean("budget_alerted_" + slug, false).apply();
        }
    }

    private void showCategorySubgroups(String category) {
        String[] groups;
        if ("خانه".equals(category)) groups = new String[]{"مواد غذایی", "لوازم خانه", "قبض و خدمات", "اجاره و تعمیرات", "نظافت", "سایر هزینه‌های خانه"};
        else if ("ماشین".equals(category)) groups = new String[]{"سوخت", "روغن و فیلتر", "تسمه تایم و تعمیرات", "بیمه", "لاستیک", "پارکینگ و عوارض", "سایر هزینه‌های ماشین"};
        else if ("مغازه".equals(category)) groups = new String[]{"خرید کالا", "اجاره مغازه", "قبض و خدمات", "حقوق", "تعمیرات", "سایر هزینه‌های مغازه"};
        else if ("مدرسه".equals(category)) groups = new String[]{"شهریه", "کتاب و لوازم‌التحریر", "رفت‌وآمد", "لباس فرم", "فعالیت‌ها", "سایر هزینه‌های مدرسه"};
        else if ("سلامت".equals(category)) groups = new String[]{"دکتر", "دارو", "آزمایش", "بیمه درمانی", "دندان‌پزشکی", "سایر هزینه‌های سلامت"};
        else groups = new String[]{"خرید متفرقه", "هدیه", "سفر", "سایر"};
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(choices);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("زیرگروه «" + category + "»")
            .setView(scroll)
            .setNegativeButton("بازگشت", null)
            .create();
        for (String group : groups) {
            Button choice = button("✍️ ثبت دستی: " + group, Color.WHITE, dark, 16);
            choice.setMinHeight(dp(54));
            choice.setOnClickListener(v -> {
                dialog.dismiss();
                showCategoryExpenseForm(category, group);
            });
            choices.addView(choice, lp(-1, -2, 0, 3, 0, 5));
        }
        Button voiceChoice = button("🎙 ثبت با صدا در بخش «" + category + "»", Color.rgb(225,238,255), dark, 16);
        voiceChoice.setMinHeight(dp(56));
        voiceChoice.setOnClickListener(v -> {
            dialog.dismiss();
            pendingCategoryVoice = category;
            status.setText("برای ثبت در بخش " + category + " بگو: هزینه، مبلغ و شرح آن؛ مثلاً «هزینه ۸۰ هزار تومان برای نان».");
            say("مبلغ و شرح را بگو");
            startVoice("auto");
        });
        choices.addView(voiceChoice, lp(-1,-2,0,8,0,5));
        if ("مغازه".equals(category)) {
            Button incomeChoice = button("＋ ثبت فروش / درآمد مغازه", Color.rgb(220, 247, 232), Color.rgb(20, 100, 55), 16);
            incomeChoice.setMinHeight(dp(58));
            incomeChoice.setOnClickListener(v -> {
                dialog.dismiss();
                showCategoryIncomeForm();
            });
            choices.addView(incomeChoice, lp(-1, -2, 0, 8, 0, 5));
            Button voiceIncome = button("🎙 ثبت فروش مغازه با صدا", Color.rgb(225,238,255), dark, 16);
            voiceIncome.setMinHeight(dp(54));
            voiceIncome.setOnClickListener(v -> {
                dialog.dismiss();
                status.setText("برای ثبت فروش بگو: «درآمد مغازه ۵۰۰ هزار تومان فروش کالا».");
                startVoice("auto");
            });
            choices.addView(voiceIncome, lp(-1,-2,0,2,0,5));
        }
        dialog.show();
    }

    private void showCategoryIncomeForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText amountInput = new EditText(this);
        amountInput.setHint("مبلغ فروش به تومان");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        form.addView(amountInput, lp(-1, -2, 0, 0, 0, 8));
        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("شرح فروش (اختیاری)");
        descriptionInput.setSingleLine(true);
        form.addView(descriptionInput, lp(-1, -2, 0, 0, 0, 4));
        new AlertDialog.Builder(this).setTitle("ثبت درآمد مغازه")
            .setView(form).setNegativeButton("لغو", null)
            .setPositiveButton("ثبت درآمد", (dialog, which) -> {
                String rawAmount = amountInput.getText().toString().trim()
                    .replace("۰","0").replace("۱","1").replace("۲","2").replace("۳","3")
                    .replace("۴","4").replace("۵","5").replace("۶","6").replace("۷","7")
                    .replace("۸","8").replace("۹","9").replace("٬","").replace(",","");
                long amount;
                try { amount = Long.parseLong(rawAmount); }
                catch (Exception e) { Toast.makeText(this, "مبلغ معتبر وارد کن.", Toast.LENGTH_LONG).show(); return; }
                if (amount <= 0 || amount > Long.MAX_VALUE - incomeTotal) {
                    Toast.makeText(this, "مبلغ درآمد معتبر نیست.", Toast.LENGTH_LONG).show();
                    return;
                }
                String description = descriptionInput.getText().toString().trim();
                String title = description.isEmpty() ? "فروش مغازه" : "فروش مغازه — " + description;
                consumeFeatureUse("shop_income", () -> {
                    if (amount > Long.MAX_VALUE - incomeTotal) {
                        Toast.makeText(this, "مبلغ بیش از حد مجاز است.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    incomeTotal += amount;
                    prefs.edit().putLong("income", incomeTotal).apply();
                    addEntry("درآمد مغازه", title, amount);
                    refresh();
                    String confirmation = "درآمد مغازه به مبلغ " + fmt(amount) + " تومان ثبت شد.";
                    status.setText(confirmation);
                    say(confirmation);
                    Toast.makeText(this, confirmation, Toast.LENGTH_LONG).show();
                });
            }).show();
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
                String entryTitle = subgroup + (description.isEmpty() ? "" : " — " + description);
                consumeFeatureUse(categoryFeatureKey(category), () -> {
                    if (amount > Long.MAX_VALUE - expenseTotal) {
                        Toast.makeText(this, "مبلغ بیش از حد مجاز است.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    expenseTotal += amount;
                    prefs.edit().putLong("expense", expenseTotal).apply();
                    addCategorySpend(category, amount);
                    addEntry(category, entryTitle, amount);
                    refresh();
                    String confirmation = "هزینه " + fmt(amount) + " تومان در «" + category + " / " + subgroup + "» ثبت شد.";
                    status.setText(confirmation);
                    say(confirmation);
                    Toast.makeText(this, confirmation, Toast.LENGTH_LONG).show();
                });
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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("تشخیص گفتار در دسترس نیست؛ سرویس گفتار Google و زبان فارسی را در گوشی بررسی کن.");
            say("تشخیص گفتار در این گوشی در دسترس نیست"); return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION); return;
        }
        try {
            if (recognizer != null) { recognizer.destroy(); recognizer = null; }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                public void onReadyForSpeech(Bundle b) { status.setText("🎙 گوش می‌دهم… حالا صحبت کن"); }
                public void onBeginningOfSpeech() { status.setText("صدایت دریافت شد…"); }
                public void onRmsChanged(float r) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { status.setText("در حال فهمیدن صحبتت…"); }
                public void onError(int e) {
                    String msg = e == SpeechRecognizer.ERROR_NO_MATCH ? "متوجه نشدم؛ آرام‌تر و با مبلغ واضح بگو." :
                        e == SpeechRecognizer.ERROR_NETWORK ? "سرویس تشخیص گفتار به اینترنت نیاز دارد یا اتصال قطع است." :
                        "تشخیص صدا انجام نشد. دوباره تلاش کن.";
                    status.setText(msg); say(msg);
                }
                public void onResults(Bundle b) {
                    ArrayList<String> result = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (result != null && !result.isEmpty()) handleVoice(result.get(0));
                }
                public void onPartialResults(Bundle b) {}
                public void onEvent(int a, Bundle b) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "با فارسی صحبت کن");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizer.startListening(intent);
        } catch (Exception e) {
            status.setText("شروع میکروفون ممکن نشد؛ مجوز میکروفون و سرویس گفتار را بررسی کن.");
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

private String featureKeyForVoice(String raw) {
        String s = normalize(raw);
        if (!pendingCategoryVoice.isEmpty() && contains(s,"هزینه","خرج","پرداخت کردم","قیمت خرید")) {
            return categoryFeatureKey(pendingCategoryVoice);
        }
        if (pendingMode != null && !pendingMode.equals("auto")) {
            if (pendingMode.startsWith("stock_")) return "inventory";
            return pendingMode;
        }
        // Keep classification aligned with handleVoice() and never bill an
        // unrecognized phrase as an expense by default.
        if (contains(s,"بودجه خانه","بودجه ماهانه","تنظیم بودجه")) return "budget";
        if (contains(s,"موجودی خانه","چی داریم","چه چیزهایی داریم")) return "inventory";
        if (contains(s,"مانده","خلاصه حساب","گزارش مالی","جمع هزینه","جمع درآمد")) return "summary";
        if (contains(s,"سلامت","دکتر","پزشک","دارو","درمان","آزمایش","بیمارستان")) return "health";
        if (contains(s,"ماشین","خودرو","تعویض روغن","روغن موتور","کیلومتر","لنت ترمز","باتری ماشین","سرویس خودرو")) return "vehicle";
        if (contains(s,"یادآوری","یادم بنداز","یادآور")) return "reminder";
        if (contains(s,"درآمد","حقوق","فروختم","دریافت کردم")) return "income";
        if (contains(s,"ماشین حساب","حساب کن","جمع","ضرب","منهای","تقسیم")) return "calc";
        if (contains(s,"خریدم","خرید کردم","خریداری کردم","خرید به قیمت","خرید به مبلغ","خرید","فهرست")) return "shopping";
        if (contains(s,"کار خانه","وظیفه","تمیزکاری")) return "task";
        if (contains(s,"هزینه","خرج","پرداخت کردم","قیمت خرید")) return "expense";
        return null;
    }

    private void consumeFeatureUseBeforeVoice(String raw) {
        String featureKey = featureKeyForVoice(raw);
        if (featureKey == null) {
            String message = "متوجه نوع درخواست نشدم؛ لطفاً فرمان را واضح‌تر بگو.";
            status.setText(message); say(message); return;
        }
        String normalized = normalize(raw);
        if ((featureKey.equals("expense") || featureKey.equals("income") || featureKey.equals("health")) && parseAmount(normalized) <= 0) {
            String message = "مبلغ را هم بگو؛ مثلاً «هزینه پنجاه هزار تومان برای نان».";
            status.setText(message); say(message); return;
        }
        consumeFeatureUse(featureKey, () -> {
            usageGateApproved = true;
            handleVoice(raw);
        });
    }

    private void consumeFeatureUse(String featureKey, Runnable onAllowed) {
        String token = getSharedPreferences("oba_auth", MODE_PRIVATE).getString("access_token", "");
        if (token.isEmpty()) {
            status.setText("برای استفاده از این قابلیت، ابتدا وارد حساب اوبا شو.");
            say("ابتدا وارد حساب اوبا شو");
            return;
        }
        status.setText("در حال بررسی سهمیهٔ استفاده...");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String effectiveToken = refreshSessionIfNeeded(token);
                URL url = new URL(OBA_SUPABASE_URL + "/rest/v1/rpc/oba_consume_feature_use");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", OBA_SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + effectiveToken);
                JSONObject body = new JSONObject();
                body.put("p_feature_key", featureKey);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                if (code < 200 || code >= 300) throw new Exception("بررسی سهمیه انجام نشد (HTTP " + code + "). ورود و اینترنت را بررسی کن.");
                org.json.JSONArray rows = new org.json.JSONArray(response.toString());
                if (rows.length() == 0) throw new Exception("پاسخ سهمیه از سرور معتبر نیست.");
                JSONObject row = rows.getJSONObject(0);
                boolean allowed = row.optBoolean("allowed", false);
                int remaining = row.optInt("remaining", 0);
                runOnUiThread(() -> {
                    if (!allowed) {
                        status.setText("۱۰ استفادهٔ رایگان این قابلیت تمام شده است. برای ادامه، اشتراک لازم است.");
                        new AlertDialog.Builder(this).setTitle("پایان سهمیهٔ رایگان")
                            .setMessage("۱۰ استفادهٔ رایگان این قابلیت مصرف شده است. برای ادامه باید اشتراک تهیه کنی.")
                            .setPositiveButton("متوجه شدم", null).show();
                        return;
                    }
                    status.setText("استفادهٔ مجاز است؛ " + remaining + " بار رایگان باقی مانده.");
                    onAllowed.run();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "بررسی سهمیه ممکن نشد. اتصال اینترنت و ورود به حساب را بررسی کن." : e.getMessage();
                runOnUiThread(() -> { status.setText(message); say(message); });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String categoryFeatureKey(String category) {
        if ("خانه".equals(category)) return "home_expense";
        if ("ماشین".equals(category)) return "vehicle_expense";
        if ("مغازه".equals(category)) return "shop_expense";
        if ("مدرسه".equals(category)) return "school_expense";
        if ("سلامت".equals(category)) return "health_expense";
        return "other_expense";
    }

private String refreshSessionIfNeeded(String currentToken) throws Exception {
        SharedPreferences auth = getSharedPreferences("oba_auth", MODE_PRIVATE);
        if (auth.getLong("expires_at", 0L) > System.currentTimeMillis() + 120000L) return currentToken;
        String refreshToken = auth.getString("refresh_token", "");
        if (refreshToken.isEmpty()) throw new Exception("نشست ورود منقضی شده است؛ دوباره وارد اوبا شو.");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(OBA_SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("apikey", OBA_SUPABASE_KEY);
            JSONObject request = new JSONObject();
            request.put("refresh_token", refreshToken);
            try (OutputStream out = c.getOutputStream()) {
                out.write(request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            java.io.InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder raw = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("تمدید ورود انجام نشد؛ دوباره وارد اوبا شو.");
            JSONObject session = new JSONObject(raw.toString());
            String access = session.optString("access_token", "");
            String rotatedRefresh = session.optString("refresh_token", "");
            if (access.isEmpty() || rotatedRefresh.isEmpty()) throw new Exception("پاسخ تمدید ورود معتبر نیست؛ دوباره وارد اوبا شو.");
            long seconds = session.optLong("expires_in", 3600L);
            auth.edit().putString("access_token", access).putString("refresh_token", rotatedRefresh)
                .putLong("expires_at", System.currentTimeMillis() + seconds * 1000L).apply();
            return access;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void handleVoice(String raw) {
        String spoken = normalize(raw);
        if ("wake".equals(pendingMode)) {
            if (contains(spoken, "اوبا", "اُبا", "وبا")) {
                status.setText("بله، در خدمتم. حالا نام بخش را بگو.");
                sayThenListenForNavigation("بله، در خدمتم");
            } else {
                status.setText("نام اوبا تشخیص داده نشد. دوباره روی میانبر صوتی بزن و بگو «اوبا».");
                say("لطفاً بگو اوبا");
            }
            return;
        }
        if ("navigate".equals(pendingMode)) {
            if (contains(spoken, "خانه", "خونه")) showCategorySubgroups("خانه");
            else if (contains(spoken, "ماشین", "خودرو")) showCategorySubgroups("ماشین");
            else if (contains(spoken, "مغازه", "فروشگاه", "کسب و کار")) showCategorySubgroups("مغازه");
            else if (contains(spoken, "مدرسه", "مکتب")) showCategorySubgroups("مدرسه");
            else if (contains(spoken, "سلامت", "دکتر", "درمان")) showCategorySubgroups("سلامت");
            else if (contains(spoken, "سایر", "متفرقه")) showCategorySubgroups("سایر");
            else {
                status.setText("بخش را متوجه نشدم؛ بگو خانه، ماشین، مغازه یا مدرسه.");
                say("نام بخش را دوباره بگو");
            }
            return;
        }
        if (!usageGateApproved) {
            consumeFeatureUseBeforeVoice(raw);
            return;
        }
        usageGateApproved = false;
        String s = normalize(raw);
        status.setText("شنیدم: «" + raw + "»");
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
            if (amount <= 0) {
                pendingCategoryVoice = "";
                status.setText("مبلغ را هم بگو؛ مثلاً «هزینه پنجاه هزار تومان برای نان»"); say("لطفاً مبلغ را هم بگو"); return;
            }
            String title = cleanDescription(s, mode);
            if (mode.equals("income")) {
                incomeTotal += amount; prefs.edit().putLong("income",incomeTotal).apply();
                addEntry("درآمد", title, amount); say("درآمد به مبلغ " + fmt(amount) + " تومان ثبت شد");
            } else {
                String category = pendingCategoryVoice;
                pendingCategoryVoice = "";
                expenseTotal += amount; prefs.edit().putLong("expense",expenseTotal).apply();
                if (!category.isEmpty()) addCategorySpend(category, amount);
                addEntry(category.isEmpty() ? "هزینه" : category, title, amount);
                say("هزینه " + fmt(amount) + " تومان " + (category.isEmpty() ? "ثبت شد" : "در بخش " + category + " ثبت شد"));
            }
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
            addCategorySpend("سلامت", amount);
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
        String category = contains(s, "ماشین", "خودرو") ? "ماشین"
            : contains(s, "مغازه", "فروشگاه") ? "مغازه"
            : contains(s, "مدرسه") ? "مدرسه"
            : contains(s, "سلامت", "درمان") ? "سلامت"
            : contains(s, "سایر", "متفرقه") ? "سایر" : "خانه";
        String slug = categorySlug(category);
        prefs.edit().putLong("budget_" + slug, amount).putBoolean("budget_alerted_" + slug, false).apply();
        long remaining = amount - categorySpent(category);
        String msg = "سقف هزینهٔ بخش " + category + " روی " + fmt(amount) + " تومان تنظیم شد. ماندهٔ سقف " + fmt(remaining) + " تومان است.";
        status.setText(msg); say(msg); checkBudgetAlert(); refresh();
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
        addCategorySpend("خانه", amount);
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
        String row = type + " • " + title + (amount>0 ? " • " + fmt(amount) + " تومان" : "");
        entries.add(0,row); while(entries.size()>12) entries.remove(entries.size()-1);
        StringBuilder b=new StringBuilder(); for(String e:entries)b.append(e).append("\n");
        prefs.edit().putString("entries",b.toString()).apply();
    }
    private void loadEntries() {
        String all=prefs.getString("entries","");
        if(!all.isEmpty()) for(String line:all.split("\n")) if(!line.trim().isEmpty()) entries.add(line);
    }
    private void refresh() {
        checkBudgetAlert();
        if(totals!=null) { long budget=prefs.getLong("home_budget",0); totals.setText("💰 درآمد: "+fmt(incomeTotal)+" تومان\n🧾 هزینه: "+fmt(expenseTotal)+" تومان\n📊 مانده حساب: "+fmt(incomeTotal-expenseTotal)+" تومان" + (budget>0 ? "\n🎯 مانده بودجه خانه: "+fmt(budget-expenseTotal)+" تومان" : "\n🎯 بودجه را صوتی تعیین کن")); }
        if(recent!=null) { StringBuilder b=new StringBuilder(); for(String e:entries) b.append("• ").append(e).append("\n"); recent.setText(b.length()==0?"هنوز چیزی ثبت نشده است":b.toString().trim()); }
        if(remindersView!=null) refreshReminders();
        if(inventoryView!=null) refreshInventory();
    }
    private String fmt(long n) { return new DecimalFormat("#,###").format(n); }
    private void sayThenListenForNavigation(String message) {
        if (tts == null) {
            startVoice("navigate");
            return;
        }
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if ("oba_wake_ack".equals(utteranceId)) runOnUiThread(() -> startVoice("navigate"));
            }
            @Override public void onError(String utteranceId) {
                if ("oba_wake_ack".equals(utteranceId)) runOnUiThread(() -> status.setText("برای ادامه، نام بخش را لمس کن یا دوباره میانبر صوتی را بزن."));
            }
        });
        int result = tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "oba_wake_ack");
        if (result == TextToSpeech.ERROR) startVoice("navigate");
    }

    private void say(String s) { if(tts!=null) tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"khoneyar"); }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.RIGHT); v.setTextDirection(View.TEXT_DIRECTION_RTL);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    private Button button(String s,int bg,int fg,int size) {
        Button b=new Button(this); b.setText(s); b.setTextSize(size); b.setTextColor(fg); b.setAllCaps(false); b.setBackground(round(bg,20)); return b;
    }
    private GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    @Override protected void onDestroy() {
        if(recognizer!=null) recognizer.destroy();
        if(tts!=null) tts.shutdown();
        super.onDestroy();
    }
}