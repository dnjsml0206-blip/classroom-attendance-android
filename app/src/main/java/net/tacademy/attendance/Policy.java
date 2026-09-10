package net.tacademy.attendance;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Pure rules, shared by the Android engine and JVM tests. */
public final class Policy {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?");
    private Policy() {}

    public static String normalizeSsid(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
            value = value.substring(1, value.length() - 1);
        if (value.equalsIgnoreCase("<unknown ssid>") || value.equalsIgnoreCase("0x")) return "";
        return value;
    }

    public static boolean validSsid(String value) {
        return !normalizeSsid(value).isEmpty()
            && value.getBytes(StandardCharsets.UTF_8).length <= 32
            && value.chars().noneMatch(Character::isISOControl);
    }

    public record Record(String in, String out) {
        public boolean checkedIn() { return !in.isEmpty(); }
        public boolean checkedOut() { return checkedIn() && !out.isEmpty(); }
    }

    public static Record parseRecord(String response) {
        String text = response == null ? "" : response.replace("\uFEFF", "").trim();
        if (text.isEmpty()) return new Record("", "");
        String[] fields = text.split("\\|", -1);
        if (fields.length != 2) throw new IllegalArgumentException("Unknown record format");
        String in = fields[0].trim(), out = fields[1].trim();
        if ((!in.isEmpty() && !TIME.matcher(in).matches()) ||
            (!out.isEmpty() && !TIME.matcher(out).matches()) ||
            (in.isEmpty() && !out.isEmpty()))
            throw new IllegalArgumentException("Unknown attendance state");
        return new Record(in, out);
    }

    public static boolean isClassDay(ZonedDateTime now, Set<Integer> days, Set<String> excluded) {
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        return days.contains(local.getDayOfWeek().getValue()) && !excluded.contains(local.toLocalDate().toString());
    }

    public static boolean inWindow(ZonedDateTime now, String start, String end) {
        LocalTime time = now.withZoneSameInstant(ZONE).toLocalTime();
        return !time.isBefore(LocalTime.parse(start)) && time.isBefore(LocalTime.parse(end));
    }

    public static boolean checkoutDue(ZonedDateTime now) {
        LocalTime time = now.withZoneSameInstant(ZONE).toLocalTime();
        return !time.isBefore(LocalTime.of(17, 50)) && time.isBefore(LocalTime.of(17, 55));
    }

    public static ZonedDateTime nextCheckout(ZonedDateTime now, Set<Integer> days, Set<String> excluded) {
        if (days.isEmpty()) return null;
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        for (int offset = 0; offset < 370; offset++) {
            ZonedDateTime candidate = local.toLocalDate().plusDays(offset).atTime(17, 50).atZone(ZONE);
            if (candidate.isAfter(local) && isClassDay(candidate, days, excluded)) return candidate;
        }
        return null;
    }
}
