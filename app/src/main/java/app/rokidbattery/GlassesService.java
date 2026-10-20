package app.rokidbattery;

import android.app.*;
import android.companion.*;
import android.content.Intent;
import android.os.*;

public final class GlassesService extends CompanionDeviceService implements StatusClient.Listener {
    private static final int NOTIFICATION = 1;
    private static final long REFRESH_INTERVAL_MS = 30000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BatteryPolicy policy = new BatteryPolicy();
    private StatusClient client;
    private boolean present;
    private boolean blePresent, bluetoothPresent;
    private long lastBind;
    private final Runnable tick = new Runnable() { public void run() {
        if (!present) return;
        if (client != null) client.refresh();
        else if (SystemClock.elapsedRealtime() - lastBind >= 15000) attach();
        render(); main.postDelayed(this, REFRESH_INTERVAL_MS);
    }};
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("battery", "Glasses battery", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Battery while Style is connected"); channel.setShowBadge(false); channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        // Samsung may bind for an already-present device without replaying a
        // presence callback. Hi Rokid's status remains the display authority.
        main.post(this::appeared);
    }
    @Override public void onDevicePresenceEvent(DevicePresenceEvent event) {
        if (event.getAssociationId() != State.prefs(this).getInt("association", -1)) return;
        int type = event.getEvent();
        if (type == DevicePresenceEvent.EVENT_BT_CONNECTED) { bluetoothPresent = true; appeared(); }
        if (type == DevicePresenceEvent.EVENT_BLE_APPEARED) { blePresent = true; appeared(); }
        if (type == DevicePresenceEvent.EVENT_BT_DISCONNECTED) {
            bluetoothPresent = false; policy.connection(false); render();
            if (!blePresent) disappeared();
        }
        if (type == DevicePresenceEvent.EVENT_BLE_DISAPPEARED) { blePresent = false; if (!bluetoothPresent) disappeared(); }
        // BLE proximity alone never establishes a connected battery notification.
        // CDM unbinds and onDestroy clears the notification once all presence ends.
    }
    @Override public void onDeviceAppeared(AssociationInfo info) { if (info.getId() == State.prefs(this).getInt("association", -1)) appeared(); }
    @Override public void onDeviceDisappeared(AssociationInfo info) { if (info.getId() == State.prefs(this).getInt("association", -1)) disappeared(); }
    @Override public void onDeviceAppeared(String address) { if (ownsAddress(address)) appeared(); }
    @Override public void onDeviceDisappeared(String address) { if (ownsAddress(address)) disappeared(); }
    private boolean ownsAddress(String address) {
        for (AssociationInfo info : getSystemService(CompanionDeviceManager.class).getMyAssociations()) {
            if (info.getDeviceMacAddress() != null && info.getDeviceMacAddress().toString().equalsIgnoreCase(address)) return true;
        }
        return false;
    }
    private void appeared() {
        if (!State.prefs(this).getBoolean("enabled", false)) return;
        if (!present) { present = true; State.status(this, "Waiting for Hi Rokid connection"); }
        if (client == null) attach();
        main.removeCallbacks(tick); main.postDelayed(tick, REFRESH_INTERVAL_MS);
    }
    private void attach() {
        String token = State.prefs(this).getString("token", "");
        lastBind = SystemClock.elapsedRealtime();
        if (token.isEmpty()) { State.status(this, "Open Rokid Battery and authorize Hi Rokid"); return; }
        client = new StatusClient(this, this); client.bind(token);
    }
    private void disappeared() {
        present = false; main.removeCallbacks(tick); policy.connection(false);
        if (client != null) { client.close(); client = null; }
        getSystemService(NotificationManager.class).cancel(NOTIFICATION);
        BatteryWidget.publish(this, policy);
        State.status(this, "Disconnected");
    }
    public void connection(boolean value) {
        if (!present) return;
        boolean changed = policy.connected != value; policy.connection(value);
        if (changed || !value) State.status(this, value ? "Connected; waiting for battery" : "Disconnected");
        render();
    }
    public void battery(int value, boolean charging, String name) {
        if (!present) return;
        String selected = State.prefs(this).getString("deviceName", "");
        if (!selected.isEmpty() && !selected.equals(name)) { failure("Hi Rokid reports another device; notification hidden"); return; }
        policy.reading(value, charging, SystemClock.elapsedRealtime());
        if (policy.visible(SystemClock.elapsedRealtime())) State.status(this, "Connected • " + value + "%" + (charging ? " • Charging" : ""));
        render();
    }
    public void failure(String text) {
        policy.connection(false); render(); State.status(this, text);
        if (client != null) { StatusClient old = client; client = null; old.close(); }
    }
    private void render() {
        BatteryWidget.publish(this, policy);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!State.prefs(this).getBoolean("enabled", false) || !policy.visible(SystemClock.elapsedRealtime()) || !manager.areNotificationsEnabled()) { manager.cancel(NOTIFICATION); return; }
        Intent launch = getPackageManager().getLaunchIntentForPackage(State.HI_ROKID);
        if (launch == null) { manager.cancel(NOTIFICATION); return; }
        PendingIntent tap = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean promote = State.prefs(this).getBoolean("promote", true);
        Bundle extras = new Bundle();
        // Public extra added in API 36.1; earlier/OEM builds may ignore it.
        extras.putBoolean("android.requestPromotedOngoing", promote);
        PendingIntent unpin = PendingIntent.getBroadcast(this, 1, new Intent(this, UnpinReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, "battery")
            .setSmallIcon(app.rokidbattery.R.drawable.ic_glasses)
            .setContentTitle("Rokid Style • " + policy.percentage + "%" + (policy.charging ? " ⚡" : ""))
            .setContentText((policy.charging ? "Charging • " : "") + "Tap to open Hi Rokid")
            .setColor(0xFF36D477).setProgress(100, policy.percentage, false)
            .setShortCriticalText(policy.percentage + "%")
            .setVisibility(Notification.VISIBILITY_PUBLIC).addExtras(extras).setDeleteIntent(unpin)
            .setContentIntent(tap).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_STATUS);
        if (promote) builder.addAction(new Notification.Action.Builder(null, "Unpin", unpin).build());
        Notification notification = builder.build();
        manager.notify(NOTIFICATION, notification);
    }
    @Override public void onDestroy() { disappeared(); super.onDestroy(); }
}
