package net.tacademy.attendance;

import java.time.LocalDate;
import java.util.regex.*;
import java.net.URI;

public final class SiteProtocol {
    public static final String BASE = "https://skplanet.atosoft.net/Worknet/Present/";
    public static final String PAGE = BASE + "Present.asp";
    public static final String AUTH_PAGE = BASE + "PAuthWay.asp";
    private SiteProtocol() {}
    public static String secureNavigation(String url) {
        try {
            URI uri = URI.create(url.trim());
            if ("http".equalsIgnoreCase(uri.getScheme()) && "skplanet.atosoft.net".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 80)) {
                String result = "https://skplanet.atosoft.net" + (uri.getRawPath() == null ? "" : uri.getRawPath());
                if (uri.getRawQuery() != null) result += "?" + uri.getRawQuery();
                if (uri.getRawFragment() != null) result += "#" + uri.getRawFragment();
                return result;
            }
            return allowedNavigation(url) ? url : null;
        } catch (RuntimeException e) { return null; }
    }
    public static boolean allowedNavigation(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "skplanet.atosoft.net".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (RuntimeException e) { return false; }
    }
    /** Only the two supported entry pages; never broaden the automation site's boundary. */
    public static String shortcutAddress(String pasted) {
        if(pasted==null)return null;
        String value=pasted.trim();
        Matcher urls=Pattern.compile("https?://[^\\s<>\"\\[\\]]+",Pattern.CASE_INSENSITIVE).matcher(value);
        if(urls.find()){value=urls.group();if(urls.find())return null;}
        else if(value.regionMatches(true,0,"skplanet.atosoft.net/",0,20))value="https://"+value;
        try{
            String secure=secureNavigation(value);if(secure==null||!allowedNavigation(secure))return null;
            URI uri=URI.create(secure);String path=uri.getPath();
            String base="/Worknet/Present/Present.asp".equalsIgnoreCase(path)?PAGE:"/Worknet/Present/PAuthWay.asp".equalsIgnoreCase(path)?AUTH_PAGE:null;
            if(base==null)return null;
            return base+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
        }catch(RuntimeException e){return null;}
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
