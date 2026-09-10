package net.tacademy.attendance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.*;
import android.net.wifi.*;
import android.os.Build;

/** Selects an actual permitted Wi-Fi Network; all site requests use that Network. */
public final class NetworkAccess {
    public record State(Network network, String ssid, String reason) {
        public boolean allowed() { return network != null; }
    }

    public static State inspect(Context context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return new State(null, "", "정확한 위치 권한이 필요합니다");
        LocationManager location = context.getSystemService(LocationManager.class);
        if (Build.VERSION.SDK_INT >= 28 && !location.isLocationEnabled())
            return new State(null, "", "휴대폰의 위치 기능을 켜 주세요");
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities current = cm.getNetworkCapabilities(active);
            if (current != null && current.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                return new State(null, "", "VPN을 해제한 뒤 지정 Wi-Fi에서 확인해 주세요");
            String observed = "";
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
                String ssid = "";
                if (Build.VERSION.SDK_INT >= 29 && caps.getTransportInfo() instanceof WifiInfo)
                    ssid = Policy.normalizeSsid(((WifiInfo) caps.getTransportInfo()).getSSID());
                if (ssid.isEmpty() && network.equals(active)) {
                    @SuppressWarnings("deprecation") WifiInfo info = wm.getConnectionInfo();
                    if (info != null) ssid = Policy.normalizeSsid(info.getSSID());
                }
                observed = ssid;
                if (!ssid.isEmpty() && new Prefs(context).ssids().contains(ssid)) {
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        return new State(null, ssid, "Wi-Fi 인터넷 연결을 확인해 주세요");
                    return new State(network, ssid, "");
                }
            }
            return new State(null, observed, observed.isEmpty()
                ? "지정 Wi-Fi 연결을 확인할 수 없습니다" : "등록되지 않은 Wi-Fi에 연결되어 있습니다");
        } catch (SecurityException e) {
            return new State(null, "", "Wi-Fi 확인 권한을 다시 허용해 주세요");
        }
    }

    public static void requireSame(Context context, Network network) throws SiteClient.Failure {
        State state = inspect(context);
        if (!state.allowed() || !state.network().equals(network))
            throw new SiteClient.Failure("NETWORK_CHANGED", "지정 Wi-Fi 연결이 변경되었습니다");
    }
}
