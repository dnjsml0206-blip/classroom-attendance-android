package net.tacademy.attendance;

/** One user-requested connection attempt; UI clicks never constitute a successful connection. */
final class WifiConnectPolicy {
    static final long TIMEOUT_MS=20000;
    enum Action { WAIT, SELECT_NETWORK, CONNECT, CONNECTED, NEED_INPUT, WIFI_OFF, LEFT_SETTINGS, LOCKED, SETTINGS_UNREADABLE, TIMEOUT }
    record Frame(String owner,boolean ready,boolean wifiOn,boolean connected,boolean wifiPage,
                 boolean credentials,boolean row,boolean targetDetails,boolean connectButton){}
    private final long started;
    private long actionAt;
    private boolean sawSettings,selected,connectSent;
    WifiConnectPolicy(long now){started=now;actionAt=now;}
    Action next(long now,Frame f){
        if(now-started>=TIMEOUT_MS)return Action.TIMEOUT;
        if(!f.ready)return Action.LOCKED;
        if(!f.wifiOn)return Action.WIFI_OFF;
        if(f.connected)return Action.CONNECTED;
        if(!f.owner.equals("com.android.settings")){
            if(f.owner.isEmpty())return now-started>=6000?Action.SETTINGS_UNREADABLE:Action.WAIT;
            if(!sawSettings&&f.owner.equals("net.tacademy.attendance"))return Action.WAIT;
            return Action.LEFT_SETTINGS;
        }
        sawSettings=true;
        if(selected&&f.credentials)return Action.NEED_INPUT;
        if(now-actionAt<1000)return Action.WAIT;
        if(!selected&&f.wifiPage&&f.row){selected=true;actionAt=now;return Action.SELECT_NETWORK;}
        if(selected&&!connectSent&&f.targetDetails&&f.connectButton){connectSent=true;actionAt=now;return Action.CONNECT;}
        return Action.WAIT;
    }
}
