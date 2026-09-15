package net.tacademy.attendance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.*;
import android.net.wifi.*;
import android.os.Build;

/** SSID absence is distinguished from an absent Wi-Fi transport. Never infer an allowed SSID. */
public final class NetworkAccess {
    public record State(Network network, String ssid, String reason, String code, boolean wifiTransportPresent) {
        public boolean allowed() { return network != null; }
        public boolean ssidVisible() { return !ssid.isEmpty(); }
    }
    public static State inspect(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean present=false;int wifiCount=0;
        try {
            Network[] networks=cm.getAllNetworks();
            for(Network n:networks){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){present=true;wifiCount++;}}
            if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
                return new State(null,"","정확한 위치 권한이 필요합니다","FINE_LOCATION_REQUIRED",present);
            if(Build.VERSION.SDK_INT>=28&&!context.getSystemService(LocationManager.class).isLocationEnabled())
                return new State(null,"","휴대폰의 위치 기능을 켜 주세요","LOCATION_DISABLED",present);
            Network active=cm.getActiveNetwork();NetworkCapabilities current=cm.getNetworkCapabilities(active);
            if(current!=null&&current.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                return new State(null,"","VPN을 해제한 뒤 지정 Wi-Fi에서 확인해 주세요","VPN_ACTIVE",present);
            String observed="";
            for(Network network:networks){
                NetworkCapabilities caps=cm.getNetworkCapabilities(network);
                if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
                String ssid="";
                if(Build.VERSION.SDK_INT>=29&&caps.getTransportInfo() instanceof WifiInfo)
                    ssid=Policy.normalizeSsid(((WifiInfo)caps.getTransportInfo()).getSSID());
                // With one Wi-Fi transport, WifiManager identifies it even when mobile data is the default.
                // Never assign the primary SSID to an ambiguous secondary Wi-Fi network.
                if(ssid.isEmpty()&&(network.equals(active)||wifiCount==1)){
                    @SuppressWarnings("deprecation") WifiInfo info=wm.getConnectionInfo();
                    if(info!=null)ssid=Policy.normalizeSsid(info.getSSID());
                }
                if(!ssid.isEmpty())observed=ssid;
                if(!ssid.isEmpty()&&new Prefs(context).ssids().contains(ssid)){
                    if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        return new State(null,ssid,"Wi-Fi 인터넷 연결을 확인해 주세요","INTERNET_UNVALIDATED",true);
                    if(!network.equals(active))return new State(null,ssid,"Wi-Fi를 기본 인터넷 연결로 사용해 주세요","WIFI_NOT_DEFAULT",true);
                    return new State(network,ssid,"","ALLOWED",true);
                }
            }
            if(!present)return new State(null,"","연결된 Wi-Fi가 없습니다. 등록 Wi-Fi에 연결해 주세요","NO_WIFI_TRANSPORT",false);
            if(!observed.isEmpty())return new State(null,observed,"등록되지 않은 Wi-Fi에 연결되어 있습니다","SSID_NOT_REGISTERED",true);
            boolean background=ScheduleController.backgroundLocationAllowed(context);
            return new State(null,"",background?"Wi-Fi 연결은 감지됐지만 이름을 읽지 못했습니다. 위치 권한과 위치 기능을 확인해 주세요"
                :"Wi-Fi 연결은 감지됐지만 이름을 읽지 못했습니다. 위치 권한을 항상 허용으로 설정해 주세요",
                background?"SSID_UNAVAILABLE":"SSID_UNAVAILABLE_BACKGROUND_PERMISSION",true);
        }catch(SecurityException e){return new State(null,"","Wi-Fi 확인 권한을 다시 허용해 주세요","PERMISSION_DENIED",present);}
    }
}
