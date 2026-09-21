package net.tacademy.attendance;

import android.app.KeyguardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.*;

/** Selects only an exact SSID row in the native Settings list. Never reads or enters credentials. */
final class WifiConnectAssist {
    private final Context context;
    private final String target;
    private final WifiConnectPolicy policy=new WifiConnectPolicy(SystemClock.elapsedRealtime());
    private boolean finished;
    private final Set<String> observations=new HashSet<>();
    WifiConnectAssist(Context context,String target){this.context=context;this.target=target;}
    boolean step(AccessibilityNodeInfo root){
        try{
            NetworkAccess.State network=NetworkAccess.inspect(context);
            KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
            boolean ready=context.getSystemService(PowerManager.class).isInteractive()&&!keyguard.isKeyguardLocked()&&!keyguard.isDeviceLocked();
            WifiManager manager=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String owner=root==null?"":String.valueOf(root.getPackageName());
            List<AccessibilityNodeInfo> nodes=new ArrayList<>();
            if("com.android.settings".equals(owner))collect(root,nodes,0);
            boolean page=false,credentials=false,details=false;
            for(AccessibilityNodeInfo n:nodes){
                if(!n.isVisibleToUser())continue;
                if(n.isPassword()||n.isEditable()){credentials=true;continue;}
                String text=safeText(n),id=n.getViewIdResourceName();
                if(Set.of("Wi-Fi","Wi‑Fi","WLAN","와이파이","인터넷","Internet").contains(text))page=true;
                if(target.equals(text)&&Set.of("android:id/alertTitle","com.android.settings:id/alertTitle",
                        "com.android.settings:id/entity_header_title","com.android.settings:id/wifi_name").contains(id==null?"":id))details=true;
            }
            AccessibilityNodeInfo row=networkRow(nodes),connect=connectButton(nodes);
            String observation=root==null?"SETTINGS_ROOT_UNAVAILABLE":!"com.android.settings".equals(owner)?"WAITING_SETTINGS_WINDOW":credentials?"CREDENTIAL_ENTRY_VISIBLE":
                details&&connect!=null?"TARGET_CONNECT_FOUND":row!=null?"TARGET_ROW_FOUND":page?"TARGET_NOT_VISIBLE":"WAITING_WIFI_LIST";
            if(observations.add(observation))log(DiagnosticLog.Event.WIFI_CONNECT_STEP,observation);
            WifiConnectPolicy.Action action=policy.next(SystemClock.elapsedRealtime(),new WifiConnectPolicy.Frame(owner,ready,
                manager!=null&&manager.isWifiEnabled(),network.allowed()&&target.equals(network.ssid()),page,credentials,row!=null,details,connect!=null));
            if(action==WifiConnectPolicy.Action.WAIT)return false;
            if(action==WifiConnectPolicy.Action.SELECT_NETWORK||action==WifiConnectPolicy.Action.CONNECT){
                log(DiagnosticLog.Event.WIFI_CONNECT_STEP,action.name()+"_REQUEST");
                // Consume the attempt even on rejection; never repeat taps after an uncertain response.
                boolean accepted=(action==WifiConnectPolicy.Action.SELECT_NETWORK?row:connect).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                log(DiagnosticLog.Event.WIFI_CONNECT_STEP,action.name()+(accepted?"_ACCEPTED":"_REJECTED"));
                if(!accepted){finish("ACTION_REJECTED",true);return true;}
                return false;
            }
            finish(action.name(),action!=WifiConnectPolicy.Action.LEFT_SETTINGS);return true;
        }catch(RuntimeException e){finish(DiagnosticLog.failureCode(e),true);return true;}
    }
    private static void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> nodes,int depth){
        if(n==null||depth>25||nodes.size()>=400)return;nodes.add(n);
        if(n.isPassword()||n.isEditable())return;
        for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),nodes,depth+1);
    }
    private static String safeText(AccessibilityNodeInfo n){return n.isPassword()||n.isEditable()||n.getText()==null?"":n.getText().toString();}
    private AccessibilityNodeInfo networkRow(List<AccessibilityNodeInfo> nodes){
        AccessibilityNodeInfo result=null;
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser()||!n.isEnabled()||!target.equals(safeText(n)))continue;
            String id=n.getViewIdResourceName();
            if(!"android:id/title".equals(id)&&!"com.android.settings:id/title".equals(id))continue;
            AccessibilityNodeInfo candidate=n;
            for(int i=0;candidate!=null&&i<4;i++,candidate=candidate.getParent()){
                if(!candidate.isClickable()||!candidate.isEnabled()||!candidate.isVisibleToUser()||candidate.isCheckable())continue;
                boolean list=false;AccessibilityNodeInfo parent=candidate.getParent();
                for(int j=0;parent!=null&&j<3;j++,parent=parent.getParent()){
                    String type=String.valueOf(parent.getClassName());if(type.contains("RecyclerView")||type.contains("ListView")){list=true;break;}
                }
                if(list){if(result!=null&&!result.equals(candidate))return null;result=candidate;}break;
            }
        }
        return result;
    }
    private static AccessibilityNodeInfo connectButton(List<AccessibilityNodeInfo> nodes){
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser()||!n.isEnabled()||!n.isClickable()||!Set.of("연결","Connect","CONNECT").contains(safeText(n)))continue;
            String id=n.getViewIdResourceName();
            if(Set.of("android:id/button1","com.android.settings:id/connect","com.android.settings:id/connect_button","com.android.settings:id/button_connect").contains(id==null?"":id))return n;
        }
        return null;
    }
    void finish(String reason,boolean notify){
        if(finished)return;finished=true;log(DiagnosticLog.Event.WIFI_CONNECT_FINISHED,reason);
        if(!notify)return;
        String message=switch(reason){
            case "CONNECTED"->"출석용 Wi-Fi 연결을 확인했어요";
            case "SETTINGS_UNREADABLE"->"휴대폰 Wi-Fi 화면을 자동으로 읽지 못했어요. "+target+"를 선택해 주세요";
            case "NEED_INPUT"->"비밀번호·인증을 이 화면에서 완료해 주세요";
            case "WIFI_OFF"->"Wi-Fi를 켠 뒤 "+target+"를 선택해 주세요";
            case "LOCKED"->"잠금을 해제한 뒤 Wi-Fi 연결을 다시 눌러 주세요";
            default->"자동 연결을 확인하지 못했어요. "+target+"를 직접 선택해 주세요";
        };
        Toast.makeText(context,message,Toast.LENGTH_LONG).show();
    }
    private void log(DiagnosticLog.Event event,String reason){DiagnosticLog.add(context,event,Policy.Phase.IDLE,reason);}
}
