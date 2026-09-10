package net.tacademy.attendance;

import android.content.Context;
import android.net.Network;
import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import java.nio.charset.*;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;

public final class SiteClient {
    public static final class Failure extends Exception {
        public final String code;
        public Failure(String code, String message) { super(message); this.code = code; }
    }
    public record Snapshot(Policy.Record record, LocalDate date) {}
    private final Context context;
    private final Network network;
    private final String cookies, query, course, scope;
    public SiteClient(Context context, Network network, String cookies) throws Exception {
        this.context = context;
        this.network = network;
        this.cookies = cookies;
        String code = AuthVault.cookie(cookies, "CstrCode");
        String courseCode = AuthVault.cookie(cookies, "CstrCCode");
        query = "strCode=" + URLEncoder.encode(code, "UTF-8") + "&strCCode=" + URLEncoder.encode(courseCode, "UTF-8");
        course = "strCCode=" + URLEncoder.encode(courseCode, "UTF-8");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((code + "\u0000" + courseCode).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value & 255));
        scope = hex.toString();
    }
    public String scope() { return scope; }

    private String get(String file, String params) throws Failure {
        NetworkAccess.requireSame(context, network);
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(SiteProtocol.BASE + file + (params.isEmpty() ? "" : "?" + params));
            connection = (HttpsURLConnection) network.openConnection(url);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cookie", cookies);
            connection.setRequestProperty("Referer", SiteProtocol.PAGE);
            connection.setRequestProperty("Cache-Control", "no-cache");
            if (connection.getResponseCode() != 200)
                throw new Failure("HTTP", "사이트 응답을 확인할 수 없습니다. 인증 또는 접속 상태를 확인해 주세요");
            Charset charset = Charset.forName("EUC-KR");
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("utf-8")) charset = StandardCharsets.UTF_8;
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (data.size() + count > 262144) throw new Failure("FORMAT", "사이트 응답 형식이 변경되었습니다");
                    data.write(buffer, 0, count);
                }
            }
            NetworkAccess.requireSame(context, network);
            return new String(data.toByteArray(), charset).replace("\uFEFF", "").trim();
        } catch (IOException e) {
            throw new Failure("CONNECTION", "통신이 지연되거나 연결이 끊겼습니다");
        } finally { if (connection != null) connection.disconnect(); }
    }

    public Snapshot inspect() throws Failure {
        try {
            LocalDate date = SiteProtocol.serverDate(get("Present.asp", ""));
            if (!date.equals(LocalDate.now(Policy.ZONE)))
                throw new Failure("DATE", "휴대폰과 사이트의 출결일이 다릅니다");
            if (!SiteProtocol.permissionFlag(get("PresentIP.asp", course)))
                throw new Failure("IP", "사이트에서 허용하지 않는 Wi-Fi입니다");
            if (!SiteProtocol.permissionFlag(get("PresentPeriodChk.asp", course)))
                throw new Failure("PERIOD", "사이트에서 현재 과정을 출석 가능 상태로 확인하지 못했습니다");
            return new Snapshot(Policy.parseRecord(get("PresentRollBookChk.asp", query)), date);
        } catch (IllegalArgumentException e) {
            throw new Failure("FORMAT", "사이트 응답 형식을 확인해야 합니다. 자동 처리를 보류했습니다");
        }
    }
    public Policy.Record record() throws Failure {
        try { return Policy.parseRecord(get("PresentRollBookChk.asp", query)); }
        catch (IllegalArgumentException e) { throw new Failure("FORMAT", "출석 기록을 확인할 수 없습니다"); }
    }
    /** Endpoints and fields were observed in the site's own public JavaScript. */
    public boolean submit(boolean checkout) throws Failure {
        String response = get(checkout ? "PresentOut_ok.asp" : "PresentIn_ok.asp", query);
        return !response.equals("0");
    }
}
