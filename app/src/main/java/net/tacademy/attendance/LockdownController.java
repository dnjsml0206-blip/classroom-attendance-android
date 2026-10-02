package net.tacademy.attendance;
import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import org.json.*;
/** One bounded power-menu attempt, exact system labels only, no coordinates or credential input. */
final class LockdownController {
    private final AccessibilityService service;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean active,clicked,menuOpened;
    private long started,seenAt,clickedAt;
    private Policy.Phase phase=Policy.Phase.IDLE;
    private String attempt="";
    private Runnable complete,fallback;
    LockdownController(AccessibilityService service){this.service=service;}
    boolean active(){return active;}
    void start(Policy.Phase phase,String attempt,Runnable complete,Runnable fallback){
        cancel();this.phase=phase;this.attempt=attempt;this.complete=complete;this.fallback=fallback;
        active=true;clicked=false;menuOpened=false;seenAt=0;started=SystemClock.elapsedRealtime();
        event(DiagnosticLog.Event.LOCKDOWN_REQUEST,"POWER_MENU_REQUEST");
        if(!service.getSystemService(KeyguardManager.class).isDeviceSecure()){fail("NO_SECURE_SCREEN_LOCK");return;}
        if(!service.getSystemService(PowerManager.class).isInteractive()){fail("SCREEN_ALREADY_OFF");return;}
        try{menuOpened=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);}
        catch(RuntimeException e){fail("POWER_MENU_"+DiagnosticLog.failureCode(e));return;}
        if(!menuOpened){fail("POWER_MENU_REJECTED");return;}
        handler.postDelayed(poll,LockdownPolicy.POLL_MS);
    }
    private final Runnable poll=new Runnable(){public void run(){
        if(!active)return;
        try{
            long now=SystemClock.elapsedRealtime();KeyguardManager keyguard=service.getSystemService(KeyguardManager.class);
            if(clicked){
                if(keyguard.isDeviceLocked()&&keyguard.isKeyguardLocked()){finish("LOCKDOWN_LOCK_OBSERVED",false);return;}
                if(now-clickedAt>=LockdownPolicy.LOCK_TIMEOUT_MS){fail("LOCKDOWN_LOCK_UNCONFIRMED");return;}
            }else{
                AccessibilityNodeInfo root=freshRoot();
                if(root!=null)try{
                    List<AccessibilityNodeInfo> nodes=nodes(root,180);List<String> labels=labels(nodes);
                    if(LockdownPolicy.powerMenu(string(root.getPackageName()),labels)){
                        if(seenAt==0){seenAt=now;event(DiagnosticLog.Event.LOCKDOWN_STEP,"POWER_MENU_OBSERVED");}
                        AccessibilityNodeInfo target=target(nodes);
                        if(target!=null&&now-seenAt>=LockdownPolicy.SETTLE_MS){
                            clicked=true;clickedAt=now;event(DiagnosticLog.Event.LOCKDOWN_STEP,"LOCKDOWN_CLICK_PLANNED");
                            boolean accepted=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            event(DiagnosticLog.Event.LOCKDOWN_STEP,accepted?"LOCKDOWN_CLICK_ACCEPTED":"LOCKDOWN_CLICK_REJECTED");
                            if(!accepted){fail("LOCKDOWN_CLICK_REJECTED");return;}
                        }
                    }else seenAt=0;
                }finally{root.recycle();}
                if(!clicked&&now-started>=LockdownPolicy.MENU_TIMEOUT_MS){fail(seenAt>0?"LOCKDOWN_ITEM_NOT_FOUND":"POWER_MENU_NOT_READABLE");return;}
            }
        }catch(RuntimeException e){fail("LOCKDOWN_"+DiagnosticLog.failureCode(e));return;}
        handler.postDelayed(this,LockdownPolicy.POLL_MS);
    }};
    private AccessibilityNodeInfo target(List<AccessibilityNodeInfo> nodes){
        Map<String,AccessibilityNodeInfo> choices=new HashMap<>();
        for(AccessibilityNodeInfo node:nodes)if(node.isVisibleToUser()&&node.isEnabled()&&(LockdownPolicy.lockdownLabel(string(node.getText()))||LockdownPolicy.lockdownLabel(string(node.getContentDescription())))){
            AccessibilityNodeInfo candidate=node;
            for(int i=0;i<3&&candidate!=null;i++,candidate=candidate.getParent())if(candidate.isClickable()&&candidate.isEnabled()&&candidate.isVisibleToUser()){
                if(!LockdownPolicy.systemPackage(string(candidate.getPackageName())))break;
                // Never climb to a container that also includes shutdown, restart, or any other action.
                if(!lockOnlyTree(candidate))break;
                Rect bounds=new Rect();candidate.getBoundsInScreen(bounds);if(!bounds.isEmpty())choices.put(bounds.toShortString(),candidate);break;
            }
        }
        return choices.size()==1?choices.values().iterator().next():null;
    }
    private AccessibilityNodeInfo freshRoot(){
        if(Build.VERSION.SDK_INT>=33)service.clearCache();
        AccessibilityNodeInfo root=service.getRootInActiveWindow();if(root!=null&&!root.refresh()){root.recycle();return null;}return root;
    }
    private static boolean lockOnlyTree(AccessibilityNodeInfo root){
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int seen=0;boolean labelFound=false;
        while(!queue.isEmpty()){
            if(++seen>24)return false;AccessibilityNodeInfo n=queue.removeFirst();
            for(String value:new String[]{string(n.getText()),string(n.getContentDescription())})if(!value.isBlank()){
                if(!LockdownPolicy.lockdownLabel(value))return false;labelFound=true;
            }
            for(int i=0;i<n.getChildCount();i++){
                if(queue.size()+seen>=24)return false;AccessibilityNodeInfo child=n.getChild(i);if(child==null)return false;queue.addLast(child);
            }
        }return labelFound;
    }
    private static List<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root,int limit){
        List<AccessibilityNodeInfo> all=new ArrayList<>();ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>();q.add(root);
        while(!q.isEmpty()&&all.size()<limit){AccessibilityNodeInfo n=q.removeFirst();all.add(n);for(int i=0;i<n.getChildCount()&&q.size()+all.size()<limit;i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)q.addLast(child);}}return all;
    }
    private static List<String> labels(List<AccessibilityNodeInfo> nodes){List<String> labels=new ArrayList<>();for(var n:nodes)if(n.isVisibleToUser()){String a=string(n.getText()),b=string(n.getContentDescription());if(!a.isBlank())labels.add(a);if(!b.isBlank())labels.add(b);}return labels;}
    private static String string(CharSequence value){return value==null?"":value.toString();}
    private void dismissMenu(){
        if(menuOpened&&!clicked)try{AccessibilityNodeInfo root=freshRoot();if(root!=null)try{if(LockdownPolicy.powerMenu(string(root.getPackageName()),labels(nodes(root,180))))service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);}finally{root.recycle();}}catch(RuntimeException ignored){}
    }
    private void fail(String reason){dismissMenu();finish(reason,true);}
    private void finish(String reason,boolean useFallback){
        if(!active)return;active=false;handler.removeCallbacksAndMessages(null);event(DiagnosticLog.Event.LOCKDOWN_RESULT,reason);
        new Prefs(service).raw.edit().putString("lockdown_last_result",reason).putLong("lockdown_last_at",System.currentTimeMillis()).apply();
        Runnable next=useFallback?fallback:complete;complete=null;fallback=null;if(next!=null)next.run();
    }
    private void event(DiagnosticLog.Event type,String reason){JSONObject d=new JSONObject();try{
        KeyguardManager k=service.getSystemService(KeyguardManager.class);d.put("attempt_id",attempt).put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("click_attempted",clicked)
            .put("screen_interactive",service.getSystemService(PowerManager.class).isInteractive()).put("device_locked",k.isDeviceLocked()).put("keyguard_locked",k.isKeyguardLocked()).put("device_secure",k.isDeviceSecure());
        }catch(JSONException ignored){}DiagnosticLog.add(service,type,phase,reason,d);}
    void cancel(){if(active){dismissMenu();event(DiagnosticLog.Event.LOCKDOWN_RESULT,"LOCKDOWN_CANCELLED");}active=false;handler.removeCallbacksAndMessages(null);complete=null;fallback=null;}
}
