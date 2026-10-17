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

        Button allCategories = button("📂 همه بخش‌های OBA  •  ۱۳ دسته", Color.WHITE, dark, 17);
        allCategories.setMinHeight(dp(58));
        allCategories.setOnClickListener(v -> showCategoriesDialog());
        content.addView(allCategories, lp(-1, -2, 0, 0, 0, 12));

        Button speak = button("🎙  صحبت کن؛ OBA گوش می‌دهد", Color.rgb(0, 150, 220), Color.WHITE, 20);
        speak.setMinHeight(dp(76)); speak.setOnClickListener(v -> startVoice("auto"));
        content.addView(speak, lp(-1, -2, 0, 4, 0, 6));

        EditText typedCommand = new EditText(this);
        typedCommand.setSingleLine(false);
        typedCommand.setMinLines(2);
        typedCommand.setMaxLines(3);
        typedCommand.setTextSize(16);
        typedCommand.setHint("اگر صدا کار نکرد، فرمان را اینجا بنویس؛ مثلاً هزینه ۸۰ هزار تومان برای نان");
        typedCommand.setPadding(dp(12), dp(10), dp(12), dp(10));
        typedCommand.setBackground(round(Color.WHITE, 14));
        content.addView(typedCommand, lp(-1, -2, 0, 4, 0, 6));
        Button runTypedCommand = button("⌨️ اجرای فرمان نوشتاری", Color.rgb(0, 137, 123), Color.WHITE, 16);
        runTypedCommand.setOnClickListener(v -> {
            String command = typedCommand.getText().toString().trim();
            if (command.isEmpty()) {
                status.setText("اول فرمان را بنویس.");
                return;
            }
            pendingMode = "auto";
            handleVoice(command);
            typedCommand.setText("");
        });
        content.addView(runTypedCommand, lp(-1, -2, 0, 0, 0, 10));

        TextView manualTitle = text("📝 ثبت دستی درآمد و هزینه", 19, dark, true);
        content.addView(manualTitle, lp(-1, -2, 0, 4, 0, 6));
        EditText manualDescription = new EditText(this);
        manualDescription.setSingleLine(true);
        manualDescription.setTextSize(16);
        manualDescription.setHint("شرح مورد؛ مثلاً خرید نان یا حقوق");
        manualDescription.setPadding(dp(12), dp(8), dp(12), dp(8));
        manualDescription.setBackground(round(Color.WHITE, 12));
        content.addView(manualDescription, lp(-1, -2, 0, 0, 0, 5));
        EditText manualAmount = new EditText(this);
        manualAmount.setSingleLine(true);
        manualAmount.setInputType(2);
        manualAmount.setTextSize(16);
        manualAmount.setHint("مبلغ به تومان");
        manualAmount.setPadding(dp(12), dp(8), dp(12), dp(8));
        manualAmount.setBackground(round(Color.WHITE, 12));
        content.addView(manualAmount, lp(-1, -2, 0, 0, 0, 5));
        LinearLayout manualButtons = new LinearLayout(this);
        manualButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button saveExpense = button("ثبت هزینه", Color.rgb(205, 65, 65), Color.WHITE, 15);
        saveExpense.setOnClickListener(v -> saveManualEntry(manualDescription, manualAmount, false));
        Button saveIncome = button("ثبت درآمد", Color.rgb(0, 137, 90), Color.WHITE, 15);
        saveIncome.setOnClickListener(v -> saveManualEntry(manualDescription, manualAmount, true));
        manualButtons.addView(saveExpense, new LinearLayout.LayoutParams(0, dp(52), 1));
        manualButtons.addView(saveIncome, new LinearLayout.LayoutParams(0, dp(52), 1));
        content.addView(manualButtons, lp(-1, -2, 0, 0, 0, 12));

        wakeButton = button(prefs.getBoolean("wake_enabled", false) ? "🔕 خاموش کردن شنیدن نام OBA" : "🎧 فعال‌سازی شنیدن نام OBA در صفحه قفل", Color.rgb(124, 83, 210), Color.WHITE, 16);
        wakeButton.setMinHeight(dp(58));
        wakeButton.setOnClickListener(v -> toggleWakeService());
        content.addView(wakeButton, lp(-1, -2, 0, 0, 0, 10));
        status = text("مثلاً بگو: «هزینه ۸۰ هزار تومان برای نان»", 15, dark, false);
        status.setGravity(Gravity.CENTER); status.setPadding(dp(8),dp(10),dp(8),dp(10));
        content.addView(status, lp(-1,-2,0,0,0,12));

        totals = text("", 17, dark, true); totals.setGravity(Gravity.CENTER);
        totals.setPadding(dp(14),dp(18),dp(14),dp(18)); totals.setBackground(round(Color.rgb(225, 250, 239), 22));
        content.addView(totals, lp(-1,-2,0,0,0,12));

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


    private void showCategoriesDialog() {
        String[] labels = {
            "🏠 خانه", "🚗 ماشین", "📄 اسناد", "🛡️ گارانتی وسایل", "🎒 مدرسه",
            "💰 امور مالی خانواده", "🛒 خرید و انبار خانه", "👨‍👩‍👧 خانواده",
            "📅 تقویم و یادآوری‌ها", "🔧 تعمیرات و نگهداری", "🩺 سلامت خانواده",
            "💼 کار و کسب‌وکار", "🔐 گاوصندوق دیجیتال"
        };
        String[] keys = {
            "home_records", "car_records", "document_records", "warranty_records", "school_records",
            "finance_records", "shopping_records", "family_records", "calendar_records",
            "maintenance_records", "health_records", "business_records", "safe_records"
        };
        String[] hints = {
            "هزینه‌های خانه، خریدها و کارهای روزمره",
            "سرویس، تعمیر، کیلومتر و هزینه‌های خودرو",
            "مدارک مهم، تاریخ انقضا و محل نگهداری",
            "نام وسیله، شماره فاکتور و پایان گارانتی",
            "تکالیف، برنامه مدرسه، شهریه و یادآوری‌ها",
            "بودجه ماهانه، بدهی، طلب و اقساط؛ ثبت مالی جداگانه هم در صفحه اصلی هست",
            "فهرست خرید و اقلام موردنیاز خانه",
            "مناسبت‌ها و اطلاعات ضروری اعضای خانواده",
            "قرارها، قبض‌ها و کارهای سررسیددار",
            "پکیج، کولر، لوازم خانگی و تعمیرکار",
            "نوبت پزشک، دارو و واکسن",
            "سفارش‌ها، فروش و مطالبات کسب‌وکار",
            "فهرست اسناد و اطلاعات ضروری؛ رمزهای حساس را اینجا ننویس"
        };
        new AlertDialog.Builder(this)
            .setTitle("همه بخش‌های OBA")
            .setItems(labels, (dialog, which) -> openCategory(keys[which], labels[which], hints[which]))
            .setNegativeButton("بستن", null)
            .show();
    }

    private void addCategoryButton(LinearLayout content, String label, String key, String hint) {
        Button b = button(label + "   ›", Color.WHITE, dark, 17);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        b.setMinHeight(dp(54));
        b.setBackground(round(Color.WHITE, 14));
        b.setOnClickListener(v -> openCategory(key, label, hint));
        content.addView(b, lp(-1, -2, 0, 0, 0, 7));
    }

    private void openCategory(String key, String label, String hint) {
        String saved = prefs.getString(key, "");
        String message = saved.isEmpty() ? "هنوز موردی ثبت نشده است." : saved;
        new AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage(message)
            .setPositiveButton("➕ ثبت مورد جدید", (dialog, which) -> showAddCategoryRecord(key, label, hint))
            .setNegativeButton("بستن", null)
            .show();
    }

    private void showAddCategoryRecord(String key, String label, String hint) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(18), dp(8), dp(18), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText titleField = new EditText(this);
        titleField.setSingleLine(true);
        titleField.setHint("عنوان؛ مثلاً قبض آب یا کارت واکسن");
        form.addView(titleField, lp(-1, -2, 0, 0, 0, 8));
        EditText detailField = new EditText(this);
        detailField.setMinLines(2);
        detailField.setHint(hint);
        form.addView(detailField, lp(-1, -2, 0, 0, 0, 4));
        new AlertDialog.Builder(this)
            .setTitle("ثبت در " + label)
            .setView(form)
            .setPositiveButton("ذخیره", (dialog, which) -> {
                String title = titleField.getText().toString().trim();
                String detail = detailField.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "عنوان را وارد کن.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String record = "• " + title + (detail.isEmpty() ? "" : "\n  " + detail);
                String old = prefs.getString(key, "");
                String updated = record + (old.isEmpty() ? "" : "\n\n" + old);
                prefs.edit().putString(key, updated).apply();
                Toast.makeText(this, "در بخش " + label + " ذخیره شد.", Toast.LENGTH_SHORT).show();
                openCategory(key, label, hint);
            })
            .setNegativeButton("انصراف", null)
            .show();
    }

    private void saveManualEntry(EditText descriptionField, EditText amountField, boolean income) {
        String description = descriptionField.getText().toString().trim();
        String rawAmount = normalize(amountField.getText().toString()).replaceAll("[,،\\s]", "");
        long amount;
        try {
            amount = Long.parseLong(rawAmount);
        } catch (Exception ex) {
            amount = 0;
        }
        if (description.isEmpty()) {
            status.setText("لطفاً شرح مورد را بنویس.");
            descriptionField.requestFocus();
            return;
        }
        if (amount <= 0) {
            status.setText("لطفاً مبلغ معتبر را به تومان وارد کن.");
            amountField.requestFocus();
            return;
        }
        if (income) {
            incomeTotal += amount;
            prefs.edit().putLong("income", incomeTotal).apply();
            addEntry("درآمد", description, amount);
        } else {
            expenseTotal += amount;
            prefs.edit().putLong("expense", expenseTotal).apply();
            addEntry("هزینه", description, amount);
        }
        String message = (income ? "درآمد" : "هزینه") + " «" + description + "» به مبلغ " + fmt(amount) + " تومان ثبت شد.";
        status.setText(message);
        say(message);
        descriptionField.setText("");
        amountField.setText("");
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

    private void handleVoice(String raw) {
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
        if(totals!=null) { long budget=prefs.getLong("home_budget",0); totals.setText("💰 درآمد: "+fmt(incomeTotal)+" تومان\n🧾 هزینه: "+fmt(expenseTotal)+" تومان\n📊 مانده حساب: "+fmt(incomeTotal-expenseTotal)+" تومان" + (budget>0 ? "\n🎯 مانده بودجه خانه: "+fmt(budget-expenseTotal)+" تومان" : "\n🎯 بودجه را صوتی تعیین کن")); }
        if(recent!=null) { StringBuilder b=new StringBuilder(); for(String e:entries) b.append("• ").append(e).append("\n"); recent.setText(b.length()==0?"هنوز چیزی ثبت نشده است":b.toString().trim()); }
        if(remindersView!=null) refreshReminders();
        if(inventoryView!=null) refreshInventory();
    }
    private String fmt(long n) { return new DecimalFormat("#,###").format(n); }
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