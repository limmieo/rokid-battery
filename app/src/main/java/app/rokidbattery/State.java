package app.rokidbattery;

import android.content.Context;
import android.content.SharedPreferences;

final class State {
    static final String HI_ROKID = "com.rokid.sprite.global.aiapp";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("companion", Context.MODE_PRIVATE); }
    static void status(Context c, String text) {
        prefs(c).edit().putString("status", text).apply();
        android.util.Log.i("RokidBattery", text);
    }
}
