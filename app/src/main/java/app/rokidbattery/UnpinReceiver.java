package app.rokidbattery;

import android.app.*;
import android.content.*;

public final class UnpinReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        State.prefs(context).edit().putBoolean("promote", false).apply();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (var active : manager.getActiveNotifications()) {
            if (active.getId() != 1) continue;
            Notification old = active.getNotification();
            old.extras.putBoolean("android.requestPromotedOngoing", false);
            Notification updated = Notification.Builder.recoverBuilder(context, old).setActions().build();
            manager.notify(1, updated);
        }
    }
}
