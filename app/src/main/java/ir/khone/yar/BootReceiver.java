package ir.khone.yar;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        ReminderReceiver.restore(context);

        // Android 14+ restricts starting a microphone foreground service directly
        // from BOOT_COMPLETED. On older Android versions, restore the listener when
        // the user has already granted microphone access and enabled OBA once.
        if (Build.VERSION.SDK_INT >= 34) return;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;

        SharedPreferences prefs = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("wake_enabled", false)
                || OfflineModelDownloader.getInstalledModelDirectory(context) == null) return;

        Intent service = new Intent(context, VoiceWakeService.class);
        service.setAction(VoiceWakeService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception ignored) {
            // The next foreground app launch will retry starting the wake listener.
        }
    }
}
