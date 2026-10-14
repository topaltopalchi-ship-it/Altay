package ir.khone.yar;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.app.Notification;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import android.content.SharedPreferences;
import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "khoneyar_reminders";

    public static void schedule(Context context, int id, long when, String title, boolean monthly) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("monthly", monthly);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            else alarms.set(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    public static void cancel(Context context, int id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
        context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE).edit().remove("reminder_" + id).apply();
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restore(context);
            return;
        }
        int id = intent.getIntExtra("id", 0);
        String title = intent.getStringExtra("title");
        if (title == null) title = "یادآوری خانه";
        boolean monthly = intent.getBooleanExtra("monthly", false);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "یادآوری‌های خونه‌یار", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("قسط‌ها، خریدها و کارهای خانه");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("یادآوری خونه‌یار")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title))
                .setAutoCancel(true).setContentIntent(contentIntent)\n                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setPriority(Notification.PRIORITY_HIGH);
        try { manager.notify(id, builder.build()); } catch (SecurityException ignored) {}\n        final String spoken = "هشدار خونه‌یار. موعد یادآوری: " + title;\n        speakReminder(context, spoken);

        if (monthly) {
            SharedPreferences prefs = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE);
            String saved = prefs.getString("reminder_" + id, "");
            String[] parts = saved.split("\\n", 4);
            int day = parts.length > 3 ? parseInt(parts[3], 1) : 1;
            Calendar next = Calendar.getInstance();
            next.add(Calendar.MONTH, 1);
            next.set(Calendar.DAY_OF_MONTH, Math.min(day, next.getActualMaximum(Calendar.DAY_OF_MONTH)));
            long oldTime = parts.length > 0 ? parseLong(parts[0], System.currentTimeMillis()) : System.currentTimeMillis();
            Calendar old = Calendar.getInstance();
            old.setTimeInMillis(oldTime);
            next.set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY));
            next.set(Calendar.MINUTE, old.get(Calendar.MINUTE));
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            while (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.MONTH, 1);
            String nextSaved = next.getTimeInMillis() + "\nmonthly\n" + title + "\n" + day;
            prefs.edit().putString("reminder_" + id, nextSaved).apply();
            schedule(context, id, next.getTimeInMillis(), title, true);
        } else {
            context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE).edit().remove("reminder_" + id).apply();
        }
    }

    private static void speakReminder(Context context, String message) {\n        try {\n            final TextToSpeech[] holder = new TextToSpeech[1];\n            holder[0] = new TextToSpeech(context.getApplicationContext(), result -> {\n                if (result == TextToSpeech.SUCCESS) {\n                    holder[0].setLanguage(new Locale("fa", "IR"));\n                    holder[0].speak(message, TextToSpeech.QUEUE_FLUSH, null, "khoneyar_reminder");\n                }\n            });\n        } catch (Exception ignored) {}\n    }\n\n    public static void restore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE);
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("reminder_")) continue;
            try {
                int id = Integer.parseInt(key.substring("reminder_".length()));
                String value = prefs.getString(key, "");
                String[] parts = value.split("\\n", 4);
                if (parts.length < 3) continue;
                long when = Long.parseLong(parts[0]);
                boolean monthly = "monthly".equals(parts[1]);
                String title = parts[2];
                if (when <= System.currentTimeMillis() && !monthly) {
                    prefs.edit().remove(key).apply();
                    continue;
                }
                if (when <= System.currentTimeMillis() && monthly) {
                    Calendar next = Calendar.getInstance();
                    next.add(Calendar.MONTH, 1);
                    int day = parts.length > 3 ? parseInt(parts[3], next.get(Calendar.DAY_OF_MONTH)) : next.get(Calendar.DAY_OF_MONTH);
                    next.set(Calendar.DAY_OF_MONTH, Math.min(day, next.getActualMaximum(Calendar.DAY_OF_MONTH)));
                    Calendar old = Calendar.getInstance(); old.setTimeInMillis(when);
                    next.set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY));
                    next.set(Calendar.MINUTE, old.get(Calendar.MINUTE));
                    when = next.getTimeInMillis();
                    prefs.edit().putString(key, when + "\nmonthly\n" + title + "\n" + day).apply();
                }
                schedule(context, id, when, title, monthly);
            } catch (Exception ignored) {}
        }
    }

    private static int parseInt(String s, int fallback) { try { return Integer.parseInt(s); } catch (Exception e) { return fallback; } }
    private static long parseLong(String s, long fallback) { try { return Long.parseLong(s); } catch (Exception e) { return fallback; } }
}
