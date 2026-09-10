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
        migrateSchedule();
        if (!raw.getBoolean("chrome_backend", false))
            raw.edit().putBoolean("chrome_backend", true).remove("auth").remove("pending").apply();
    }
    private void migrateSchedule() {
        if (raw.getInt("schedule_version", 0) >= 2) return;
        SharedPreferences.Editor edit = raw.edit().putInt("schedule_version", 2);
        if (raw.getString("start", "08:00").equals("08:00")) edit.putString("start", "08:30");
        if (raw.getString("end", "17:45").equals("17:45")) edit.putString("end", "09:30");
        if (raw.getInt("radius", 150) == 150) edit.putInt("radius", 30);
        edit.apply();
        if (!Policy.validSchedule(start(), end(), checkout(), retryMinutes()))
            raw.edit().putString("start", "08:30").putString("end", "09:30").putString("checkout", "17:50").putInt("checkout_retry_minutes", 10).apply();
    }
    public Set<String> ssids() { return new TreeSet<>(raw.getStringSet("ssids", Set.of())); }
    public void ssids(Set<String> values) { raw.edit().putStringSet("ssids", new HashSet<>(values)).apply(); }
    public boolean enabled() { return raw.getBoolean("enabled", false); }
    public void enabled(boolean value) { raw.edit().putBoolean("enabled", value).apply(); }
    public boolean auto() { return raw.getBoolean("auto", false); }
    public String start() { return raw.getString("start", "08:30"); }
    public String end() { return raw.getString("end", "09:30"); }
    public String checkout() { return raw.getString("checkout", "17:50"); }
    public int retryMinutes() { return raw.getInt("checkout_retry_minutes", 10); }
    public boolean checkedInToday() { return today() && !raw.getString("in", "").isEmpty(); }
    public boolean checkedOutToday() { return checkedInToday() && !raw.getString("out", "").isEmpty(); }
    private boolean today() { return LocalDate.now(Policy.ZONE).toString().equals(raw.getString("record_day", "")); }
    public Policy.Phase phase(ZonedDateTime now) {
        if (!enabled()) return Policy.Phase.IDLE;
        return Policy.phase(now, days(), excluded(), start(), end(), checkout(), retryMinutes(), checkedInToday(), checkedOutToday());
    }
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
    public int radius() { return raw.getInt("radius", 30); }
    public void location(double lat, double lon, int radius) {
        raw.edit().putLong("latitude", Double.doubleToLongBits(lat))
            .putLong("longitude", Double.doubleToLongBits(lon)).putInt("radius", radius).apply();
    }
    public void status(String title, String detail) {
        raw.edit().putString("status", title).putString("detail", detail)
            .putLong("updated", System.currentTimeMillis()).apply();
    }
    public void record(String day, Policy.Record record) {
        raw.edit().putString("record_day", day).putString("in", record.in()).putString("out", record.out())
            .putString("record:" + day, record.in() + "|" + record.out()).apply();
    }
    public Policy.Record history(LocalDate day) {
        String value = raw.getString("record:" + day, null);
        if (value == null && day.toString().equals(raw.getString("record_day", "")))
            value = raw.getString("in", "") + "|" + raw.getString("out", "");
        if (value == null) return null;
        try { return Policy.parseRecord(value); } catch (IllegalArgumentException e) { return null; }
    }
    public String todayLog() { return raw.getString("log:" + LocalDate.now(Policy.ZONE), ""); }
    public void log(String message) {
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        String key = "log:" + now.toLocalDate();
        String stamp = now.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        String[] lines = (stamp + "  " + message + "\n" + raw.getString(key, "")).split("\n");
        raw.edit().putString(key, String.join("\n", Arrays.copyOf(lines, Math.min(lines.length, 80)))).apply();
    }
}