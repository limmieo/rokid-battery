package app.rokidbattery;

/** No cached reading is carried across a disconnect or allowed past its freshness limit. */
final class BatteryPolicy {
    static final long MAX_AGE_MS = 90000;
    boolean connected;
    int percentage = -1;
    boolean charging;
    long receivedAt;
    void connection(boolean value) {
        if (connected != value || !value) { percentage = -1; receivedAt = 0; }
        connected = value;
    }
    void reading(int value, boolean charging, long now) {
        if (!connected || value < 0 || value > 100) return;
        percentage = value; this.charging = charging; receivedAt = now;
    }
    boolean visible(long now) { return connected && percentage >= 0 && now - receivedAt < MAX_AGE_MS; }
}
