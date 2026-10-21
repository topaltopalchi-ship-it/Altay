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
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Calendar;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "khoneyar_reminders_alarm_v3";
    private static final String CHANNEL_NAME = "یادآوری‌های OBA";

    public static void schedule(Context context, int id, long when, String title, boolean monthly) {
        scheduleOne(context, id, id, when, title, monthly, false);
        long earlyWhen = when - 1L * 60L * 60L * 1000L;
        if (earlyWhen > System.currentTimeMillis()) {
            scheduleOne(context, earlyRequestCode(id), id, earlyWhen, title, monthly, true);
        } else {
            cancelAlarm(context, earlyRequestCode(id));
        }
    }

    private static int earlyRequestCode(int id) { return id ^ 0x40000000; }

    private static void scheduleOne(Context context, int requestCode, int reminderId, long when, String title,
                                    boolean monthly, boolean early) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("id", reminderId);
        intent.putExtra("title", title);
        intent.putExtra("monthly", monthly);
        intent.putExtra("early", early);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
                } else {
                    alarms.set(AlarmManager.RTC_WAKEUP, when, pending);
                }
            } catch (SecurityException exactAlarmPermissionChanged) {
                // The special access can be revoked between checking it and scheduling.
                // Keep the reminder scheduled with the best inexact API available.
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
                    } else {
                        alarms.set(AlarmManager.RTC_WAKEUP, when, pending);
                    }
                } catch (SecurityException ignored) {
                    // Do not crash the app if Android blocks alarm scheduling entirely.
                }
            }
        }
    }

    private static void cancelAlarm(Context context, int requestCode) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
    }

    public static void cancel(Context context, int id) {
        cancelAlarm(context, id);
        cancelAlarm(context, earlyRequestCode(id));
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
        boolean early = intent != null && intent.getBooleanExtra("early", false);
        String spokenTitle = early ? "یک ساعت تا زمان یادآوری باقی مانده. " + title : "یادآوری اوبا. " + title;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            String channelId = channelIdFor(context);
            createChannel(context, manager, channelId);
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, channelId)
                    : new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(early ? "⏰ یادآوری OBA؛ یک ساعت مانده" : "⏰ یادآوری OBA")
                    .setContentText(title)
                    .setStyle(new Notification.BigTextStyle().bigText(spokenTitle))
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_MAX);
                String selected = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE)
                        .getString("reminder_alarm_sound_uri", "");
                Uri sound = selected.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(selected);
                if (sound != null) builder.setSound(sound);
            }
            try {
                manager.notify(early ? earlyRequestCode(id) : id, builder.build());
            } catch (SecurityException ignored) {
                // Notification permission may be denied by the user.
            }
        }

        // The one-hour heads-up stays a regular notification; loop the tone only at due time.
        if (!early) {
            Intent alarmService = new Intent(context, ReminderAlarmService.class);
            alarmService.setAction(ReminderAlarmService.ACTION_START);
            alarmService.putExtra("title", title);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(alarmService);
                else context.startService(alarmService);
            } catch (Exception ignored) {
                // Keep the regular notification as a fallback if Android blocks service startup.
            }
        }

        speakReminder(context.getApplicationContext(), spokenTitle, goAsync());

        if (early) return;

        if (monthly) {
            rescheduleMonthly(context, id, title);
        } else {
            context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE)
                    .edit().remove("reminder_" + id).apply();
        }
    }

    private static String channelIdFor(Context context) {
        String selected = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE)
                .getString("reminder_alarm_sound_uri", "");
        return CHANNEL_ID + (selected.isEmpty() ? "_default" : "_" + Integer.toHexString(selected.hashCode()));
    }

    private static void createChannel(Context context, NotificationManager manager, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String selected = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE)
                    .getString("reminder_alarm_sound_uri", "");
            Uri sound = selected.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(selected);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audio = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            NotificationChannel channel = new NotificationChannel(
                    channelId, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("یادآوری‌های متنی همراه با صدای هشدار");
            channel.enableVibration(true);
            channel.setSound(sound, audio);
            manager.createNotificationChannel(channel);
        }
    }

    private static void speakReminder(Context context, String message, PendingResult pendingResult) {
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) pendingResult.finish();
        };
        // Keep the receiver alive long enough for TextToSpeech to finish, but never indefinitely.
        handler.postDelayed(finish, 15000);
        try {
            final TextToSpeech[] holder = new TextToSpeech[1];
            holder[0] = new TextToSpeech(context, result -> {
                TextToSpeech engine = holder[0];
                if (engine == null) {
                    finish.run();
                    return;
                }
                if (result != TextToSpeech.SUCCESS) {
                    engine.shutdown();
                    finish.run();
                    return;
                }
                int languageResult = engine.setLanguage(new Locale("fa", "IR"));
                engine.setSpeechRate(0.9f);
                if (languageResult == TextToSpeech.LANG_MISSING_DATA
                        || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.shutdown();
                    finish.run();
                    return;
                }
                engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    private void complete() {
                        engine.shutdown();
                        handler.post(finish);
                    }
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) { complete(); }
                    @Override public void onError(String utteranceId) { complete(); }
                });
                int speakResult = engine.speak(message, TextToSpeech.QUEUE_FLUSH, null,
                        "khoneyar_reminder_" + System.currentTimeMillis());
                if (speakResult == TextToSpeech.ERROR) {
                    engine.shutdown();
                    finish.run();
                }
            });
        } catch (Exception ignored) {
            finish.run();
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
                next.getTimeInMillis() + "\nmonthly\n" + title + "\n" + day).apply();
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
                    prefs.edit().putString(key, when + "\nmonthly\n" + title + "\n" + day).apply();
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
