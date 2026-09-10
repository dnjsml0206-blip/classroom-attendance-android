package net.tacademy.attendance;

import java.time.LocalDate;
import java.util.regex.*;
import java.net.URI;

public final class SiteProtocol {
    public static final String BASE = "https://skplanet.atosoft.net/Worknet/Present/";
    public static final String PAGE = BASE + "Present.asp";
    private SiteProtocol() {}
    public static boolean allowedNavigation(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "skplanet.atosoft.net".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (RuntimeException e) { return false; }
    }
    public static boolean permissionFlag(String response) {
        String value = response == null ? "" : response.replace("\uFEFF", "").trim();
        if (value.equals("1")) return true;
        if (value.equals("0")) return false;
        throw new IllegalArgumentException("Unknown permission response");
    }
    public static LocalDate serverDate(String html) {
        Matcher match = Pattern.compile("출결일\\s*:\\s*</th>\\s*<th[^>]*>\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!match.find()) throw new IllegalArgumentException("Site structure changed");
        return LocalDate.parse(match.group(1));
    }
}
