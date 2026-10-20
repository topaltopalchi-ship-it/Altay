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

        // Voice wake-up is disabled. Only restore reminders after device restart.
        SharedPreferences prefs = context.getSharedPreferences("khoneyar", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("wake_enabled", false).apply();
    }
}
