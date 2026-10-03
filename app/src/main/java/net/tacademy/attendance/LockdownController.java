package net.tacademy.attendance;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;
import org.json.*;

/** One bounded system power-menu attempt. No coordinate taps or non-lock actions. */
final class LockdownController {
    private final AccessibilityService service;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean active,clicked,menuOpened;
    private int addedFlags,traceCount;
    private long started,stableAt,clickedAt;
    private Policy.Phase phase=Policy.Phase.IDLE;
    private String attempt="",stableTarget="",lastTrace="",lastRead="POWER_MENU_NOT_READABLE";
    private JSONObject evidence=new JSONObject();
    private Runnable complete,fallback;
    LockdownController(AccessibilityService service){this.service=service;}
    boolean active(){return active;}
    void start(Policy.Phase phase,String attempt,Runnable complete,Runnable fallback){
        cancel();this.phase=phase;this.attempt=attempt;this.complete=complete;this.fallback=fallback;
        active=true;clicked=false;menuOpened=false;stableAt=0;stableTarget="";traceCount=0;lastTrace="";
        lastRead="POWER_MENU_NOT_READABLE";evidence=new JSONObject();started=SystemClock.elapsedRealtime();
        remember("LOCKDOWN_RUNNING");
        event(DiagnosticLog.Event.LOCKDOWN_REQUEST,"POWER_MENU_REQUEST");
        if(!service.getSystemService(KeyguardManager.class).isDeviceSecure()){fail("NO_SECURE_SCREEN_LOCK");return;}
        if(!service.getSystemService(PowerManager.class).isInteractive()){fail("SCREEN_ALREADY_OFF");return;}
        try{
            AccessibilityServiceInfo info=service.getServiceInfo();
            if(info!=null){int desired=AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                addedFlags=desired&~info.flags;info.flags|=desired;service.setServiceInfo(info);}
            menuOpened=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
        }catch(RuntimeException e){fail("POWER_MENU_"+DiagnosticLog.failureCode(e));return;}
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
                try(Frame frame=read()){
                    long observedAt=SystemClock.elapsedRealtime();
                    evidence=frame.details();lastRead=frame.state;
                    String trace=frame.state+":"+frame.source+":"+frame.menus+":"+frame.lockLabels+":"+frame.candidates;
                    if(!trace.equals(lastTrace)&&traceCount<6){lastTrace=trace;traceCount++;event(DiagnosticLog.Event.LOCKDOWN_STEP,frame.state);}
                    if(frame.target!=null){
                        if(!frame.key.equals(stableTarget)){stableTarget=frame.key;stableAt=observedAt;}
                        if(observedAt-stableAt>=LockdownPolicy.SETTLE_MS&&observedAt-started<LockdownPolicy.MENU_TIMEOUT_MS){
                            clicked=true;clickedAt=observedAt;event(DiagnosticLog.Event.LOCKDOWN_STEP,"LOCKDOWN_CLICK_PLANNED");
                            boolean accepted=frame.target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            event(DiagnosticLog.Event.LOCKDOWN_STEP,accepted?"LOCKDOWN_CLICK_ACCEPTED":"LOCKDOWN_CLICK_REJECTED");
                            if(!accepted){fail("LOCKDOWN_CLICK_REJECTED");return;}
                        }
                    }else{stableTarget="";stableAt=0;}
                }
                if(!clicked&&SystemClock.elapsedRealtime()-started>=LockdownPolicy.MENU_TIMEOUT_MS){fail(lastRead.equals("LOCKDOWN_TARGET_READY")?"LOCKDOWN_TARGET_UNSTABLE":lastRead);return;}
            }
        }catch(RuntimeException e){fail("LOCKDOWN_"+DiagnosticLog.failureCode(e));return;}
        handler.postDelayed(this,LockdownPolicy.POLL_MS);
    }};

    private static final class Tree implements AutoCloseable {
        final List<AccessibilityNodeInfo> live=new ArrayList<>();
        final List<LockdownPolicy.Node> facts=new ArrayList<>();
        boolean complete=true;
        Tree(AccessibilityNodeInfo root){
            live.add(root);List<Integer> parents=new ArrayList<>();parents.add(-1);
            for(int i=0;i<live.size();i++){
                AccessibilityNodeInfo n=live.get(i);Rect bounds=new Rect();n.getBoundsInScreen(bounds);
                facts.add(new LockdownPolicy.Node(string(n.getPackageName()),string(n.getText()),string(n.getContentDescription()),
                    n.isVisibleToUser(),n.isEnabled(),n.isClickable()||n.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK),bounds.isEmpty()?"":bounds.toShortString(),parents.get(i)));
                for(int j=0;j<n.getChildCount();j++){
                    if(live.size()>=LockdownPolicy.NODE_LIMIT){complete=false;break;}
                    AccessibilityNodeInfo child=n.getChild(j);if(child==null){complete=false;continue;}
                    live.add(child);parents.add(i);
                }
            }
        }
        public void close(){for(var n:live)n.recycle();}
    }
    private static final class Frame implements AutoCloseable {
        final List<Tree> trees=new ArrayList<>();final Set<Integer> windowsSeen=new HashSet<>();
        int windows,systemRoots,menus,lockLabels,candidates,nodes,readFailures;
        boolean incomplete;String source="NONE",state="POWER_MENU_NOT_READABLE",key="";
        AccessibilityNodeInfo target;
        void root(AccessibilityNodeInfo root,String from){
            if(root==null)return;
            if(!LockdownPolicy.systemPackage(string(root.getPackageName()))){root.recycle();return;}
            if(windowsSeen.contains(root.getWindowId())){root.recycle();return;}
            if(!root.refresh()){readFailures++;root.recycle();return;}
            windowsSeen.add(root.getWindowId());systemRoots++;
            Tree tree=new Tree(root);trees.add(tree);nodes+=tree.facts.size();
            var selection=LockdownPolicy.select(string(root.getPackageName()),tree.facts,tree.complete);
            if(!selection.menu())return;
            menus++;lockLabels+=selection.labels();candidates+=selection.candidates();incomplete|=!tree.complete;
            if(menus==1){state=selection.state();source=from;}
            if(selection.target()>=0){
                AccessibilityNodeInfo chosen=tree.live.get(selection.target());
                if(target!=null){target=null;state="LOCKDOWN_TARGET_AMBIGUOUS";}
                else if(!state.equals("LOCKDOWN_TARGET_AMBIGUOUS")){target=chosen;key=root.getWindowId()+":"+tree.facts.get(selection.target()).bounds();source=from;state=selection.state();}
            }
        }
        void conclude(){
            if(incomplete){state="LOCKDOWN_TREE_INCOMPLETE";target=null;}
            else if(candidates>1||menus>1){state="LOCKDOWN_TARGET_AMBIGUOUS";target=null;}
        }
        JSONObject details(){JSONObject d=new JSONObject();try{
            d.put("lock_window_count",windows).put("lock_system_roots",systemRoots).put("lock_menu_count",menus)
                .put("lock_label_count",lockLabels).put("lock_candidate_count",candidates).put("lock_node_count",nodes)
                .put("lock_tree_incomplete",incomplete).put("lock_read_failures",readFailures).put("lock_root_source",source).put("lock_read_state",state);
        }catch(JSONException ignored){}return d;}
        public void close(){for(Tree t:trees)t.close();}
    }
    private Frame read(){
        Frame frame=new Frame();
        try{
            if(Build.VERSION.SDK_INT>=33)service.clearCache();
            List<AccessibilityWindowInfo> windows=service.getWindows();frame.windows=windows.size();
            try{
                for(int i=0;i<Math.min(24,windows.size());i++){
                    AccessibilityWindowInfo w=windows.get(i);
                    if(w.isFocused()||w.isActive()||w.getType()==AccessibilityWindowInfo.TYPE_SYSTEM)
                        frame.root(w.getRoot(),"SYSTEM_WINDOWS");
                }
                if(windows.size()>24)frame.incomplete=true;
            }finally{for(var w:windows)w.recycle();}
            frame.root(service.getRootInActiveWindow(),"ACTIVE_ROOT");frame.conclude();return frame;
        }catch(RuntimeException e){frame.close();throw e;}
    }
    private static String string(CharSequence value){return value==null?"":value.toString();}
    private void dismissMenu(){
        if(menuOpened&&!clicked)try(Frame frame=read()){
            if(frame.menus>0)service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        }catch(RuntimeException ignored){}
    }
    private void restoreFlags(){
        if(addedFlags==0)return;
        try{AccessibilityServiceInfo info=service.getServiceInfo();if(info!=null){info.flags&=~addedFlags;service.setServiceInfo(info);}}
        catch(RuntimeException ignored){}addedFlags=0;
    }
    private void fail(String reason){dismissMenu();finish(reason,true);}
    private void finish(String reason,boolean useFallback){
        if(!active)return;active=false;handler.removeCallbacksAndMessages(null);restoreFlags();event(DiagnosticLog.Event.LOCKDOWN_RESULT,reason);
        remember(reason);Runnable next=useFallback?fallback:complete;complete=null;fallback=null;if(next!=null)next.run();
    }
    private void remember(String reason){new Prefs(service).raw.edit().putString("lockdown_last_result",reason).putLong("lockdown_last_at",System.currentTimeMillis()).putString("lockdown_last_evidence",evidence.toString()).apply();}
    private void event(DiagnosticLog.Event type,String reason){JSONObject d=new JSONObject();try{
        for(Iterator<String> i=evidence.keys();i.hasNext();){String name=i.next();d.put(name,evidence.get(name));}
        KeyguardManager k=service.getSystemService(KeyguardManager.class);d.put("attempt_id",attempt).put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("click_attempted",clicked)
            .put("screen_interactive",service.getSystemService(PowerManager.class).isInteractive()).put("device_locked",k.isDeviceLocked()).put("keyguard_locked",k.isKeyguardLocked()).put("device_secure",k.isDeviceSecure());
        }catch(JSONException ignored){}DiagnosticLog.add(service,type,phase,reason,d);}
    void cancel(){
        if(active){dismissMenu();event(DiagnosticLog.Event.LOCKDOWN_RESULT,"LOCKDOWN_CANCELLED");remember("LOCKDOWN_CANCELLED");}
        active=false;handler.removeCallbacksAndMessages(null);restoreFlags();complete=null;fallback=null;
    }
}
