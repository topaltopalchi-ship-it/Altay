package ir.khone.yar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

public class ReminderAlarmService extends Service {
    public static final String ACTION_START = "ir.khone.yar.action.START_REMINDER_ALARM";
    public static final String ACTION_STOP = "ir.khone.yar.action.STOP_REMINDER_ALARM";
    private static final String CHANNEL_ID = "oba_persistent_alarm_playback_v1";
    private static final int NOTIFICATION_ID = 48021;
    private Ringtone ringtone;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAlarm();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        String title = intent == null ? "زمان انجام کار رسیده است" : intent.getStringExtra("title");
        if (title == null || title.trim().isEmpty()) title = "زمان انجام کار رسیده است";
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 48022, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ReminderAlarmService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 48023, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ آلارم OBA فعال است")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(title + "\\nآهنگ آلارم در حال پخش است. برای قطع آن دکمهٔ «قطع آلارم» را بزن."))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(content)
                .addAction(android.R.drawable.ic_media_pause, "قطع آلارم", stopPending);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) builder.setPriority(Notification.PRIORITY_MAX);
        startForeground(NOTIFICATION_ID, builder.build());
        if (ringtone == null || !ringtone.isPlaying()) startAlarmTone();
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "پخش آلارم OBA",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("اعلان ماندگار هنگام پخش آلارم");
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    private void startAlarmTone() {
        try {
            String selected = getSharedPreferences("khoneyar", MODE_PRIVATE)
                    .getString("reminder_alarm_sound_uri", "");
            Uri sound = selected == null || selected.isEmpty() ? null : Uri.parse(selected);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), sound);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception ignored) {
            if (ringtone != null) {
                try { ringtone.stop(); } catch (Exception ignoredAgain) {}
                ringtone = null;
            }
        }
    }

    private void stopAlarm() {
        if (ringtone != null) {
            try { ringtone.stop(); } catch (Exception ignored) {}
            ringtone = null;
        }
    }

    @Override public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
