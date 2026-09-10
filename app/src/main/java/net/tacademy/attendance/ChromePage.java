package net.tacademy.attendance;

import java.net.URI;
import java.time.LocalDate;
import java.util.regex.*;

/** Parses only labeled attendance fields, never unrelated times in the Chrome UI. */
public final class ChromePage {
    private static final String TIME = "((?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?)";
    private ChromePage() {}
    public static boolean attendanceAddress(String shown) {
        if (shown == null) return false;
        String value = shown.trim();
        if (!value.contains("://")) value = "https://" + value;
        if (!SiteProtocol.allowedNavigation(value)) return false;
        URI uri = URI.create(value);
        return "/Worknet/Present/Present.asp".equalsIgnoreCase(uri.getPath());
    }
    public static LocalDate date(String text) {
        Matcher match = Pattern.compile("출결일\\s*:\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(text);
        if (!match.find()) throw new IllegalArgumentException("Attendance date missing");
        return LocalDate.parse(match.group(1));
    }
    public static Policy.Record record(String text) {
        String in = time(text, "입실"), out = time(text, "퇴실");
        if (!out.isEmpty() && in.isEmpty()) throw new IllegalArgumentException("Incomplete record");
        return new Policy.Record(in, out);
    }
    private static String time(String text, String label) {
        Matcher match = Pattern.compile(label + "\\s*시간\\s*:\\s*" + TIME + "(?![0-9:])").matcher(text);
        if (match.find()) return match.group(1);
        if (Pattern.compile(label + "\\s*시간\\s*:").matcher(text).find()) throw new IllegalArgumentException("Unknown time format");
        return "";
    }
    public static boolean checkoutConfirmation(String text) {
        return text.contains("정말로 퇴실 하시겠습니까?");
    }
}
