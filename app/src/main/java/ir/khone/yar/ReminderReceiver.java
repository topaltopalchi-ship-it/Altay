package ir.khone.yar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Calendar;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "khoneyar_reminders";
    private static final String CHANNEL_NAME = "یادآوری‌های خونه‌یار";

    public static void schedule(Context context, int id, long when, String title, boolean monthly) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("monthly", monthly);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, when, pending);
            }
        }
    }

    public static void cancel(Context context, int id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
        context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE).edit()
                .remove("reminder_" + id).apply();
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restore(context);
            return;
        }

        int id = intent == null ? 0 : intent.getIntExtra("id", 0);
        String title = intent == null ? null : intent.getStringExtra("title");
        if (title == null || title.trim().isEmpty()) title = "زمان انجام کار رسیده است";
        boolean monthly = intent != null && intent.getBooleanExtra("monthly", false);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            createChannel(context, manager);
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("⏰ یادآوری خونه‌یار")
                    .setContentText(title)
                    .setStyle(new Notification.BigTextStyle().bigText(title))
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_MAX);
            }
            try {
                manager.notify(id, builder.build());
            } catch (SecurityException ignored) {
                // Notification permission may be denied by the user.
            }
        }

        speakReminder(context.getApplicationContext(), "یادآوری خونه‌یار. " + title);

        if (monthly) {
            rescheduleMonthly(context, id, title);
        } else {
            context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE)
                    .edit().remove("reminder_" + id).apply();
        }
    }

    private static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audio = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("یادآوری‌های متنی همراه با صدای هشدار");
            channel.enableVibration(true);
            channel.setSound(sound, audio);
            manager.createNotificationChannel(channel);
        }
    }

    private static void speakReminder(Context context, String message) {
        try {
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(context, result -> {
                TextToSpeech engine = holder[0];
                if (engine == null) return;
                if (result == TextToSpeech.SUCCESS) {
                    int languageResult = engine.setLanguage(new Locale("fa", "IR"));
                    engine.setSpeechRate(0.9f);
                    if (languageResult == TextToSpeech.LANG_MISSING_DATA
                            || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.shutdown();
                        return;
                    }
                    engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String utteranceId) {}
                        @Override public void onDone(String utteranceId) {
                            engine.shutdown();
                        }
                        @Override public void onError(String utteranceId) {
                            engine.shutdown();
                        }
                    });
                    engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "khoneyar_reminder_" + System.currentTimeMillis());
                } else {
                    engine.shutdown();
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void rescheduleMonthly(Context context, int id, String title) {
        SharedPreferences prefs = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE);
        String saved = prefs.getString("reminder_" + id, "");
        String[] parts = saved.split("\\n", 4);
        long oldTime = parts.length > 0 ? parseLong(parts[0], System.currentTimeMillis()) : System.currentTimeMillis();
        int day = parts.length > 3 ? parseInt(parts[3], 1) : 1;
        Calendar old = Calendar.getInstance();
        old.setTimeInMillis(oldTime);
        Calendar next = Calendar.getInstance();
        next.add(Calendar.MONTH, 1);
        next.set(Calendar.DAY_OF_MONTH, Math.min(day, next.getActualMaximum(Calendar.DAY_OF_MONTH)));
        next.set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY));
        next.set(Calendar.MINUTE, old.get(Calendar.MINUTE));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        while (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.MONTH, 1);
        prefs.edit().putString("reminder_" + id,
                next.getTimeInMillis() + "\\nmonthly\\n" + title + "\\n" + day).apply();
        schedule(context, id, next.getTimeInMillis(), title, true);
    }

    public static void restore(Context context) {
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
                if (when <= System.currentTimeMillis()) {
                    Calendar next = Calendar.getInstance();
                    Calendar old = Calendar.getInstance();
                    old.setTimeInMillis(when);
                    next.add(Calendar.MONTH, 1);
                    int day = parts.length > 3 ? parseInt(parts[3], old.get(Calendar.DAY_OF_MONTH))
                            : old.get(Calendar.DAY_OF_MONTH);
                    next.set(Calendar.DAY_OF_MONTH, Math.min(day, next.getActualMaximum(Calendar.DAY_OF_MONTH)));
                    next.set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY));
                    next.set(Calendar.MINUTE, old.get(Calendar.MINUTE));
                    next.set(Calendar.SECOND, 0);
                    next.set(Calendar.MILLISECOND, 0);
                    when = next.getTimeInMillis();
                    prefs.edit().putString(key, when + "\\nmonthly\\n" + title + "\\n" + day).apply();
                }
                schedule(context, id, when, title, monthly);
            } catch (Exception ignored) {
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Exception e) { return fallback; }
    }
}
