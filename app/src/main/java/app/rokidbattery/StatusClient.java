package app.rokidbattery;

import android.content.*;
import android.os.*;
import com.rokid.sprite.aiapp.externalapp.IDeviceStatusCallback;
import com.rokid.sprite.aiapp.externalapp.IMediaStreamService;
import org.json.JSONObject;

/** Uses the status-only Binder interface shipped in CXR-L 1.0.4.
 * Binding extras match CXRLink; only device-status observers are registered.
 * No session is opened, and no audio, image, AI or view observers are registered.
 */
final class StatusClient {
    interface Listener { void connection(boolean connected); void battery(int value, boolean charging, String name); void failure(String message); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private IMediaStreamService remote;
    private boolean bound, closed;
    private volatile long generation;
    private final IDeviceStatusCallback callback = new IDeviceStatusCallback.Stub() {
        public void onDeviceConnectChanged(boolean connected) { main.post(() -> { if (!closed) { generation++; listener.connection(connected); if (connected) refresh(); } }); }
        public void onDeviceInfoNotifiy(String json) {
            long epoch = generation;
            main.post(() -> {
                if (closed || remote == null || epoch != generation) return;
                try {
                    JSONObject info = new JSONObject(json);
                    Object raw = info.opt("batteryLevel");
                    if (!(raw instanceof Number)) { listener.failure("Battery field unavailable"); return; }
                    double value = ((Number)raw).doubleValue();
                    if (!Double.isFinite(value) || value != Math.rint(value) || value < 0 || value > 100) { listener.failure("Battery value invalid"); return; }
                    // Query current connection before displaying a possibly delayed callback.
                    if (!remote.isDeviceConnected()) { listener.connection(false); return; }
                    listener.battery((int)value, info.optBoolean("ischarging"), info.optString("deviceName", ""));
                } catch (Exception e) { listener.failure("Device status unavailable"); }
            });
        }
        public void onWearingStatusNotify(boolean value) {}
        public void onCurrentScenesNotify(String scenes) {}
    };
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            remote = IMediaStreamService.Stub.asInterface(binder);
            try {
                if (!remote.registerDeviceStatusCallback(callback)) { listener.failure("Hi Rokid status subscription denied; authorize again"); return; }
                refresh();
            } catch (Exception e) { listener.failure("Hi Rokid service unavailable"); }
        }
        public void onServiceDisconnected(ComponentName name) { remote = null; generation++; listener.connection(false); }
        public void onBindingDied(ComponentName name) { remote = null; generation++; listener.failure("Hi Rokid service restarted"); }
        public void onNullBinding(ComponentName name) { remote = null; listener.failure("Hi Rokid authorization needs renewal"); }
    };
    StatusClient(Context context, Listener listener) { this.context = context; this.listener = listener; }
    void bind(String token) {
        if (closed || bound || token.isEmpty()) return;
        Intent intent = new Intent("com.rokid.sprite.aiapp.externalapp.MEDIA_STREAM_SERVICE").setPackage(State.HI_ROKID);
        intent.putExtra("auth_token", token).putExtra("auth_package", context.getPackageName());
        try { bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE); if (!bound) listener.failure("Cannot bind Hi Rokid; open it and reconnect"); }
        catch (RuntimeException e) { listener.failure("Hi Rokid status binding unavailable"); }
    }
    void refresh() {
        if (closed || remote == null) return;
        try { boolean value = remote.isDeviceConnected(); listener.connection(value); if (value) remote.getDeviceInfo(); }
        catch (Exception e) { listener.failure("Hi Rokid status unavailable"); }
    }
    void close() {
        closed = true; generation++;
        if (remote != null) { try { remote.unregisterDeviceStatusCallback(callback); } catch (Exception ignored) {} }
        remote = null;
        if (bound) { try { context.unbindService(connection); } catch (RuntimeException ignored) {} }
        bound = false;
    }
}
