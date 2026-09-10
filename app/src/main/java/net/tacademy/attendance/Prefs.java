package net.tacademy.attendance;

import android.content.*;
import java.time.*;
import java.util.*;

public final class Prefs {
    public final SharedPreferences raw;
    public Prefs(Context context) {
        raw = context.getSharedPreferences("attendance", Context.MODE_PRIVATE);
        if (!raw.contains("initialized")) raw.edit()
            .putBoolean("initialized", true)
            .putStringSet("ssids", new HashSet<>(Set.of("TACADEMY_5G")))
            .putStringSet("days", new HashSet<>(Set.of("1","2","3","4","5"))).apply();
    }
    public Set<String> ssids() { return new TreeSet<>(raw.getStringSet("ssids", Set.of())); }
    public void ssids(Set<String> values) { raw.edit().putStringSet("ssids", new HashSet<>(values)).apply(); }
    public boolean enabled() { return raw.getBoolean("enabled", false); }
    public void enabled(boolean value) { raw.edit().putBoolean("enabled", value).apply(); }
    public boolean auto() { return raw.getBoolean("auto", false); }
    public String start() { return raw.getString("start", "08:00"); }
    public String end() { return raw.getString("end", "17:45"); }
    public Set<Integer> days() {
        Set<Integer> values = new HashSet<>();
        for (String day : raw.getStringSet("days", Set.of())) {
            try { values.add(Integer.parseInt(day)); } catch (NumberFormatException ignored) {}
        }
        return values;
    }
    public Set<String> excluded() { return new HashSet<>(raw.getStringSet("excluded", Set.of())); }
    public boolean hasLocation() { return raw.contains("latitude") && raw.contains("longitude"); }
    public double latitude() { return Double.longBitsToDouble(raw.getLong("latitude", 0)); }
    public double longitude() { return Double.longBitsToDouble(raw.getLong("longitude", 0)); }
    public int radius() { return raw.getInt("radius", 150); }
    public void location(double lat, double lon, int radius) {
        raw.edit().putLong("latitude", Double.doubleToLongBits(lat))
            .putLong("longitude", Double.doubleToLongBits(lon)).putInt("radius", radius).apply();
    }
    public void status(String title, String detail) {
        raw.edit().putString("status", title).putString("detail", detail)
            .putLong("updated", System.currentTimeMillis()).apply();
    }
    public void log(String message) {
        String stamp = ZonedDateTime.now(Policy.ZONE).toLocalTime().toString().substring(0, 5);
        String all = stamp + "  " + message + "\n" + raw.getString("log", "");
        String[] lines = all.split("\n");
        raw.edit().putString("log", String.join("\n", Arrays.copyOf(lines, Math.min(lines.length, 60)))).apply();
    }
}
