package net.tacademy.attendance;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Pure scheduling and protocol rules; all attendance times use Korea time. */
public final class Policy {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final int CHECKOUT_DELAY_SECONDS = 5;
    private static final Pattern TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?");
    public enum Phase { IDLE, ENTRY, CHECKOUT }
    private Policy() {}
    public static String normalizeSsid(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) value = value.substring(1, value.length() - 1);
        return value.equalsIgnoreCase("<unknown ssid>") || value.equalsIgnoreCase("0x") ? "" : value;
    }
    public static boolean validSsid(String value) {
        return !normalizeSsid(value).isEmpty() && value.getBytes(StandardCharsets.UTF_8).length <= 32
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
        if ((!in.isEmpty() && !TIME.matcher(in).matches()) || (!out.isEmpty() && !TIME.matcher(out).matches()) || (in.isEmpty() && !out.isEmpty()))
            throw new IllegalArgumentException("Unknown attendance state");
        return new Record(in, out);
    }
    public static boolean isClassDay(ZonedDateTime now, Set<Integer> days, Set<String> excluded) {
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        return days.contains(local.getDayOfWeek().getValue()) && !excluded.contains(local.toLocalDate().toString()) && CourseCalendar.dayOff(local.toLocalDate()).isEmpty();
    }
    public static boolean validSchedule(String start, String end, String checkout, int retryMinutes) {
        try {
            LocalTime a = LocalTime.parse(start), b = LocalTime.parse(end), c = LocalTime.parse(checkout);
            return a.getSecond() == 0 && b.getSecond() == 0 && c.getSecond() == 0
                && a.isBefore(b) && !b.isAfter(c) && retryMinutes >= 1 && retryMinutes <= 60
                && c.toSecondOfDay() + retryMinutes * 60 < 86400;
        } catch (RuntimeException e) { return false; }
    }
    public static boolean inWindow(ZonedDateTime now, String start, String end) {
        LocalTime time = now.withZoneSameInstant(ZONE).toLocalTime();
        return !time.isBefore(LocalTime.parse(start)) && time.isBefore(LocalTime.parse(end));
    }
    public static LocalTime checkoutStartTime(String checkout) { return LocalTime.parse(checkout).plusSeconds(CHECKOUT_DELAY_SECONDS); }
    public static LocalTime checkoutStartTime(LocalDate day,String checkout) { return CourseSchedule.dispatch(day,checkout); }
    public static boolean checkoutDue(ZonedDateTime now, String checkout, int retryMinutes) {
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        ZonedDateTime start = local.toLocalDate().atTime(LocalTime.parse(CourseSchedule.checkout(local.toLocalDate(),checkout))).atZone(ZONE);
        return !local.isBefore(start.plusSeconds(CourseSchedule.cushion(local.toLocalDate()))) && local.isBefore(start.plusMinutes(retryMinutes));
    }
    public static Phase phase(ZonedDateTime now, Set<Integer> days, Set<String> excluded,
            String start, String end, String checkout, int retryMinutes, boolean checkedIn, boolean checkedOut) {
        if (!validSchedule(start, end, checkout, retryMinutes) || !isClassDay(now, days, excluded) || checkedOut) return Phase.IDLE;
        if (checkoutDue(now, checkout, retryMinutes)) return Phase.CHECKOUT;
        return !checkedIn && inWindow(now, start, end) ? Phase.ENTRY : Phase.IDLE;
    }
    /** Includes stop boundaries so a sleeping service also stops when its window ends. */
    public static ZonedDateTime nextWake(ZonedDateTime now, Set<Integer> days, Set<String> excluded,
            String start, String end, String checkout, int retryMinutes, boolean checkedIn, boolean checkedOut) {
        if (days.isEmpty() || !validSchedule(start, end, checkout, retryMinutes)) return null;
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        for (int offset = 0; offset < 370; offset++) {
            LocalDate day = local.toLocalDate().plusDays(offset);
            ZonedDateTime entry = day.atTime(LocalTime.parse(start)).atZone(ZONE);
            if (!isClassDay(entry, days, excluded)) continue;
            List<ZonedDateTime> candidates = new ArrayList<>();
            if (offset > 0 || (!checkedIn && !checkedOut)) {
                candidates.add(entry);
                candidates.add(day.atTime(LocalTime.parse(end)).atZone(ZONE));
            }
            if (offset > 0 || !checkedOut) {
                ZonedDateTime out = day.atTime(LocalTime.parse(CourseSchedule.checkout(day,checkout))).atZone(ZONE);
                candidates.add(out.plusSeconds(CourseSchedule.cushion(day))); candidates.add(out.plusMinutes(retryMinutes));
            }
            for (ZonedDateTime candidate : candidates) if (candidate.isAfter(local)) return candidate;
        }
        return null;
    }
    public static boolean nearby(float distance, float accuracy, int radius) {
        return Float.isFinite(distance) && Float.isFinite(accuracy) && distance >= 0 && accuracy >= 0
            && accuracy <= radius && distance <= radius;
    }
}
