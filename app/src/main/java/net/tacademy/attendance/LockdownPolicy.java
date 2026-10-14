package net.tacademy.attendance;

import java.util.*;

/** Exact labels only: this policy must never select power-off, restart or emergency actions. */
final class LockdownPolicy {
    static final int POLL_MS = 200;
    static final int MENU_TIMEOUT_MS = 8000;
    static final int SETTLE_MS = 600;
    static final int LOCK_TIMEOUT_MS = 4000;
    static final int NODE_LIMIT = 256;

    private LockdownPolicy() {}

    static boolean authenticatedLock(boolean secure,boolean keyguard,boolean locked) {
        return secure && keyguard && locked;
    }
    /** Only an identified SystemUI icon is eligible; no gesture action label is required. */
    static boolean lockIcon(String owner,String id,boolean visible,boolean enabled) {
        return id!=null && "com.android.systemui".equals(owner) && visible && enabled
            && Set.of("com.android.systemui:id/lock_icon","com.android.systemui:id/lock_icon_view").contains(id);
    }
    static final int ICON_HOLD_MS = 1200;
    record Hold(float x,float y) {}
    static Hold iconHold(int left,int top,int right,int bottom,int width,int height,float density){
        if(width<=0||height<=0||!Float.isFinite(density)||density<=0||left<0||top<0||right>width||bottom>height
            ||right<=left||bottom<=top||right-left>width/3||bottom-top>height/5||bottom>height*0.35f)return null;
        return new Hold((left+right)/2f,(top+bottom)/2f);
    }

    static boolean systemPackage(String packageName) {
        return "com.android.systemui".equals(packageName) || "android".equals(packageName);
    }

    static boolean lockdownLabel(String label) {
        return switch (actionLabel(label)) {
            case "잠금 모드", "잠금모드", "잠금", "lockdown", "lockdown mode" -> true;
            default -> false;
        };
    }

    static boolean powerMenu(String packageName, List<String> labels) {
        if (!systemPackage(packageName) || labels == null) return false;
        boolean powerOff = false;
        boolean restart = false;
        for (String label : labels) {
            switch (actionLabel(label)) {
                case "전원 끄기", "power off" -> powerOff = true;
                case "다시 시작", "restart" -> restart = true;
                default -> { }
            }
        }
        return powerOff && restart;
    }

    /** Only platform role suffixes are removable; descriptive or mixed-action text is never guessed. */
    private static String actionLabel(String label) {
        String value=normalize(label);
        for(String suffix:List.of(", 버튼", ", button", " 버튼", " button"))
            if(value.endsWith(suffix))return value.substring(0,value.length()-suffix.length()).trim();
        return value;
    }

    record Node(String owner,String text,String description,boolean visible,boolean enabled,
                boolean clickable,String bounds,int parent) {}
    record Selection(String state,int target,int labels,int candidates) {
        boolean menu(){return !state.equals("POWER_MENU_NOT_RECOGNIZED");}
    }

    /** Parent/child evidence is supplied by the Android reader; unit tests exercise OEM-shaped trees. */
    static Selection select(String owner,List<Node> nodes,boolean complete) {
        List<String> labels=new ArrayList<>();
        for(Node n:nodes)if(n.visible()){labels.add(n.text());labels.add(n.description());}
        if(!powerMenu(owner,labels))return new Selection("POWER_MENU_NOT_RECOGNIZED",-1,0,0);
        int lockLabels=0;Map<String,Integer> candidates=new LinkedHashMap<>();
        for(int index=0;index<nodes.size();index++){
            Node n=nodes.get(index);
            if(!n.visible()||!n.enabled()||!(lockdownLabel(n.text())||lockdownLabel(n.description())))continue;
            lockLabels++;
            int candidate=index;
            for(int depth=0;depth<6&&candidate>=0&&candidate<nodes.size();depth++){
                Node parent=nodes.get(candidate);
                if(parent.clickable()&&parent.enabled()&&parent.visible()){
                    if(systemPackage(parent.owner())&&lockOnlySubtree(nodes,candidate)&&!parent.bounds().isBlank())
                        candidates.put(parent.bounds(),candidate);
                    break;
                }
                candidate=parent.parent();
            }
        }
        String state=!complete?"LOCKDOWN_TREE_INCOMPLETE":lockLabels==0?"LOCKDOWN_ITEM_NOT_FOUND":
            candidates.isEmpty()?"LOCKDOWN_TARGET_UNSAFE":candidates.size()>1?"LOCKDOWN_TARGET_AMBIGUOUS":"LOCKDOWN_TARGET_READY";
        return new Selection(state,state.equals("LOCKDOWN_TARGET_READY")?candidates.values().iterator().next():-1,lockLabels,candidates.size());
    }

    private static boolean lockOnlySubtree(List<Node> nodes,int root){
        boolean found=false;int count=0;
        for(int i=0;i<nodes.size();i++){
            int cursor=i;boolean child=false;
            for(int depth=0;cursor>=0&&cursor<nodes.size()&&depth<NODE_LIMIT;depth++){
                if(cursor==root){child=true;break;}cursor=nodes.get(cursor).parent();
            }
            if(!child)continue;
            if(++count>32||!systemPackage(nodes.get(i).owner()))return false;
            for(String label:Arrays.asList(nodes.get(i).text(),nodes.get(i).description())){
                String clean=normalize(label);
                if(clean.isEmpty()||clean.equals("버튼")||clean.equals("button"))continue;
                if(!lockdownLabel(label))return false;
                found=true;
            }
        }
        return found;
    }

    static String resultText(String reason){
        if(reason==null||reason.isEmpty()||reason.equals("NOT_TESTED"))return "아직 테스트하지 않았어요";
        return switch(reason){
            case "LOCKDOWN_RUNNING" -> "자물쇠 우선 · 잠금 상태 확인 중…";
            case "LOCKDOWN_LOCK_OBSERVED" -> "잠금 버튼 클릭 후 인증 잠금 확인";
            case "AUTHENTICATED_LOCK_OBSERVED" -> "이미 인증 잠금 확인 · 추가 잠금 시도 생략";
            case "LOCK_ICON_LOCK_OBSERVED" -> "자물쇠 동작 후 인증 잠금 확인";
            case "LOCK_ICON_UNAVAILABLE" -> "지원되는 자물쇠 잠금 동작을 찾지 못했어요";
            case "LOCK_ICON_UNCONFIRMED" -> "자물쇠 동작 후 인증 잠금을 확인하지 못했어요";
            case "LOCK_ICON_REJECTED" -> "휴대폰이 자물쇠 잠금 동작을 거절했어요";
            case "LOCKDOWN_ITEM_NOT_FOUND" -> "전원 메뉴에서 잠금 항목을 읽지 못했어요 · 잠금 모드 표시 설정 확인";
            case "LOCKDOWN_TARGET_UNSAFE" -> "잠금 글자는 보이지만 단독으로 누를 버튼을 확인하지 못했어요";
            case "LOCKDOWN_TARGET_AMBIGUOUS" -> "잠금 후보가 여러 개라 누르지 않았어요";
            case "LOCKDOWN_TREE_INCOMPLETE" -> "전원 메뉴 일부를 읽지 못해 누르지 않았어요";
            case "LOCKDOWN_TARGET_UNSTABLE" -> "잠금 버튼 위치가 안정되기 전에 확인 시간이 끝났어요";
            case "LOCKDOWN_CLICK_REJECTED" -> "잠금 버튼을 찾았지만 휴대폰이 클릭을 거절했어요";
            case "LOCKDOWN_LOCK_UNCONFIRMED" -> "클릭은 전달했지만 인증 잠금 전환을 확인하지 못했어요";
            case "POWER_MENU_REJECTED" -> "휴대폰이 전원 메뉴 열기를 허용하지 않았어요";
            case "NO_SECURE_SCREEN_LOCK" -> "PIN·패턴·비밀번호 잠금부터 설정해 주세요";
            case "SCREEN_ALREADY_OFF" -> "이미 화면이 꺼져 있어 잠금 모드를 실행하지 못했어요";
            case "LOCKDOWN_CANCELLED" -> "잠금 작업이 취소됐어요";
            default -> "전원 메뉴를 읽지 못했어요 · 진단 로그 확인";
        };
    }

    private static String normalize(String label) {
        return label == null ? "" : label.replaceAll("[\\s\\p{Z}]+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
