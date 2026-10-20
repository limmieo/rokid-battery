package app.rokidbattery;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.Date;

public final class BatteryWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { update(context); }
    static void publish(Context context, BatteryPolicy policy) {
        boolean visible = State.prefs(context).getBoolean("enabled", false) && policy.visible(SystemClock.elapsedRealtime());
        State.prefs(context).edit().putInt("widgetBattery", visible ? policy.percentage : -1)
            .putBoolean("widgetCharging", visible && policy.charging)
            .putLong("widgetReceived", visible ? policy.receivedAt : 0)
            .putLong("widgetReported", visible ? System.currentTimeMillis() - (SystemClock.elapsedRealtime() - policy.receivedAt) : 0).apply();
        update(context);
    }
    static void update(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, BatteryWidget.class));
        if (ids.length == 0) return;
        var prefs = State.prefs(context);
        long age = SystemClock.elapsedRealtime() - prefs.getLong("widgetReceived", 0);
        long wallAge = System.currentTimeMillis() - prefs.getLong("widgetReported", 0);
        int value = prefs.getInt("widgetBattery", -1);
        boolean visible = prefs.getBoolean("enabled", false) && value >= 0 && value <= 100 && age >= 0 && age < BatteryPolicy.MAX_AGE_MS && wallAge >= 0 && wallAge < BatteryPolicy.MAX_AGE_MS;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.battery_widget);
        views.setTextViewText(R.id.widget_battery, visible ? value + "%" + (prefs.getBoolean("widgetCharging", false) ? " ⚡" : "") : "Disconnected");
        views.setProgressBar(R.id.widget_progress, 100, visible ? value : 0, false);
        views.setViewVisibility(R.id.widget_progress, visible ? View.VISIBLE : View.INVISIBLE);
        // A visible timestamp makes the last reading explicit if the host retains
        // this view after process death; widgets cannot guarantee live refresh.
        views.setTextViewText(R.id.widget_updated, visible ? (prefs.getBoolean("widgetCharging", false) ? "Charging • " : "Reported ") + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(prefs.getLong("widgetReported", 0))) : "Tap to open Hi Rokid");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(State.HI_ROKID);
        if (launch != null) views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids, views);
    }
}
