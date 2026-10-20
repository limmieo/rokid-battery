package app.rokidbattery;

public final class BatteryPolicyTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        BatteryPolicy p = new BatteryPolicy();
        p.reading(53, false, 1000); check(!p.visible(1000), "Disconnected callbacks must not show battery");
        p.connection(true); check(!p.visible(1000), "Connection alone has no valid battery");
        p.reading(53, false, 1000); check(p.visible(1000), "Current connected reading shows");
        check(p.visible(90999), "Reading remains valid just before expiry");
        check(!p.visible(91000), "Stale reading is hidden at expiry");
        p.connection(false); check(!p.visible(1100), "Disconnect removes immediately");
        p.connection(true); check(!p.visible(1100), "Reconnect never reuses prior battery");
        p.reading(-1, false, 1200); check(!p.visible(1200), "Invalid negative percentage hidden");
        p.reading(101, false, 1200); check(!p.visible(1200), "Over 100 hidden");
        p.reading(0, false, 1300); check(p.visible(1300) && p.percentage == 0, "Explicit empty battery valid");
        p.reading(100, true, 1400); check(p.visible(1400) && p.charging, "Full and charging preserved");
        p.connection(true); check(p.visible(1500), "Repeated connected polls preserve fresh reading");
        System.out.println("PASS: disconnect, reconnect, freshness, invalid readings, 0%, 100%, charging");
    }
}
