package app.rokidbattery;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Pair;
import android.widget.*;
import com.rokid.sprite.aiapp.externalapp.auth.*;
import java.util.regex.Pattern;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private CompanionDeviceManager manager;
    private final Runnable update = new Runnable() { public void run() { status.setText(State.prefs(MainActivity.this).getString("status", "Not set up")); handler.postDelayed(this, 1000); }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); manager = getSystemService(CompanionDeviceManager.class);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 48, 40, 32);
        TextView title = new TextView(this); title.setText("Rokid Battery"); title.setTextSize(26); layout.addView(title);
        TextView info = new TextView(this); info.setText("Shows your Style battery while connected. Tap the notification to open Hi Rokid.\n\nSet up once: allow notifications and nearby-device access, authorize Hi Rokid, then select your already-paired glasses. Android handles background wake-up. Hi Rokid keeps its normal connection."); layout.addView(info);
        status = new TextView(this); status.setTextSize(18); status.setPadding(0, 28, 0, 28); layout.addView(status);
        Button setup = new Button(this); setup.setText("Set up / resume"); layout.addView(setup); setup.setOnClickListener(v -> setup());
        Button stop = new Button(this); stop.setText("Stop monitoring"); layout.addView(stop); stop.setOnClickListener(v -> stop());
        Button open = new Button(this); open.setText("Open Hi Rokid"); layout.addView(open); open.setOnClickListener(v -> { Intent launch = getPackageManager().getLaunchIntentForPackage(State.HI_ROKID); if (launch != null) startActivity(launch); });
        Button widget = new Button(this); widget.setText("Add battery widget"); layout.addView(widget); widget.setOnClickListener(v -> {
            android.appwidget.AppWidgetManager widgets = getSystemService(android.appwidget.AppWidgetManager.class);
            if (widgets.isRequestPinAppWidgetSupported()) widgets.requestPinAppWidget(new ComponentName(this, BatteryWidget.class), null, null);
            else Toast.makeText(this, "Add Rokid Style battery from your widget picker", Toast.LENGTH_LONG).show();
        });
        TextView widgetInfo = new TextView(this); widgetInfo.setText("The widget has a glasses icon and green battery bar. Lock-screen placement depends on Samsung's widget support. The notification is also readable on the lock screen when your notification settings allow it. Android controls its position."); layout.addView(widgetInfo);
        setContentView(layout);
    }
    @Override protected void onResume() { super.onResume(); handler.post(update); }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
    private void setup() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT}, 10); return;
        }
        authorize();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 10 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) authorize();
        else State.status(this, "Notifications and nearby-device permission are needed for monitoring");
    }
    private void authorize() {
        try {
            if (!AuthorizationHelper.INSTANCE.isRequiredHiRokidInstalled(this)) { State.status(this, "Install or update Hi Rokid first"); return; }
            Pair<Integer, Intent> result = AuthorizationHelper.INSTANCE.requestAuthorization(this, new GlassPermission[0], 1001);
            if (result != null) parse(result.first, result.second);
        } catch (RuntimeException e) { State.status(this, "Could not open Hi Rokid authorization"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1001) parse(result, data);
    }
    private void parse(int result, Intent data) {
        AuthResult auth = AuthorizationHelper.INSTANCE.parseAuthorizationResult(result, data);
        if (!(auth instanceof AuthResult.AuthSuccess) || !AuthorizationHelper.INSTANCE.isConnectHiRokid()) { State.status(this, "Hi Rokid authorization canceled or denied"); return; }
        String token = ((AuthResult.AuthSuccess)auth).getToken();
        if (token == null || token.isEmpty()) return;
        State.prefs(this).edit().putString("token", token).putBoolean("enabled", true).apply();
        associate();
    }
    private void associate() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { State.status(this, "Nearby-device permission needed"); return; }
        try {
            for (AssociationInfo info : manager.getMyAssociations()) { observe(info); return; }
            ArrayList<BluetoothDevice> devices = new ArrayList<>();
            Pattern pattern = Pattern.compile("(?i).*(glasses|rokid).*");
            for (BluetoothDevice device : getSystemService(BluetoothManager.class).getAdapter().getBondedDevices()) {
                if (device.getName() != null && pattern.matcher(device.getName()).matches()) devices.add(device);
            }
            if (devices.isEmpty()) { State.status(this, "Pair Style in Hi Rokid first, then try setup again"); return; }
            if (devices.size() == 1) requestAssociation(devices.get(0));
            else {
                String[] names = devices.stream().map(BluetoothDevice::getName).toArray(String[]::new);
                new AlertDialog.Builder(this).setTitle("Select your paired Style glasses").setItems(names, (dialog, index) -> requestAssociation(devices.get(index))).setNegativeButton("Cancel", null).show();
            }
        } catch (RuntimeException e) { State.status(this, "Companion setup unavailable; try again"); }
    }
    private void requestAssociation(BluetoothDevice device) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { State.status(this, "Nearby-device permission needed"); return; }
        try {
            State.status(this, "Requesting association with " + device.getName());
            State.prefs(this).edit().putString("deviceName", device.getName()).apply();
            AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder().setAddress(device.getAddress()).build())
                .setSingleDevice(true).build();
            manager.associate(request, getMainExecutor(), new CompanionDeviceManager.Callback() {
                @Override public void onAssociationPending(IntentSender chooser) {
                    State.status(MainActivity.this, "Confirm your paired glasses in the Android dialog");
                    try { startIntentSenderForResult(chooser, 2001, null, 0, 0, 0); }
                    catch (IntentSender.SendIntentException e) { State.status(MainActivity.this, "Could not open glasses selection"); }
                }
                @Override public void onAssociationCreated(AssociationInfo info) { observe(info); }
                @Override public void onFailure(CharSequence message) { State.status(MainActivity.this, "Glasses setup failed: " + message); }
            });
        } catch (RuntimeException e) { State.status(this, "Companion setup unavailable; try again"); }
    }
    private void observe(AssociationInfo info) {
        State.prefs(this).edit().putInt("association", info.getId()).apply();
        try { manager.startObservingDevicePresence(new ObservingDevicePresenceRequest.Builder().setAssociationId(info.getId()).build()); State.status(this, "Monitoring enabled; waiting for glasses"); }
        catch (RuntimeException e) { State.status(this, "Could not enable glasses presence monitoring"); }
    }
    private void stop() {
        State.prefs(this).edit().putBoolean("enabled", false).remove("token").apply();
        for (AssociationInfo info : manager.getMyAssociations()) {
            try { manager.stopObservingDevicePresence(new ObservingDevicePresenceRequest.Builder().setAssociationId(info.getId()).build()); manager.disassociate(info.getId()); } catch (RuntimeException ignored) {}
        }
        getSystemService(android.app.NotificationManager.class).cancelAll(); State.status(this, "Monitoring stopped");
        BatteryWidget.update(this);
    }
}
