package net.tacademy.attendance;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LockdownSelectionTest {
    private LockdownPolicy.Node node(String text,String description,boolean click,int parent,String bounds){
        return new LockdownPolicy.Node("com.android.systemui",text,description,true,true,click,bounds,parent);
    }
    private List<LockdownPolicy.Node> menu(){return new ArrayList<>(List.of(
        node("","",false,-1,"root"),node("전원 끄기","",true,0,"power"),node("다시 시작","",true,0,"restart")));
    }
    private LockdownPolicy.Selection select(List<LockdownPolicy.Node> nodes){return LockdownPolicy.select("com.android.systemui",nodes,true);}
    @Test public void selectsOnlyLockAmongFourPowerActions(){
        var nodes=menu();nodes.add(node("긴급 모드","",true,0,"emergency"));nodes.add(node("잠금 모드","",true,0,"lock"));
        var s=select(nodes);assertEquals("LOCKDOWN_TARGET_READY",s.state());assertEquals(4,s.target());assertEquals(1,s.candidates());
    }
    @Test public void supportsNestedOemButtonWrapperAndRoleDescription(){
        var nodes=menu();nodes.add(node("","버튼",true,0,"lock"));nodes.add(node("","",false,3,"wrapper"));
        nodes.add(node("","",false,4,"inner"));nodes.add(node("잠금 모드","잠금 모드, 버튼",false,5,"label"));
        assertEquals(3,select(nodes).target());
    }
    @Test public void knownRoleSuffixAlsoAllowsMenuIdentification(){
        assertTrue(LockdownPolicy.powerMenu("android",List.of("전원 끄기, 버튼","다시 시작 버튼")));
        assertTrue(LockdownPolicy.lockdownLabel("Lockdown, button"));
        assertFalse(LockdownPolicy.lockdownLabel("Lockdown button settings"));
    }
    @Test public void rejectsContainerWithPowerAndLockChildren(){
        var nodes=menu();nodes.set(0,node("","",true,-1,"root"));nodes.add(node("잠금 모드","",false,0,"label"));
        assertEquals("LOCKDOWN_TARGET_UNSAFE",select(nodes).state());
    }
    @Test public void rejectsMixedDescriptionEvenWhenTextSaysLock(){
        var nodes=menu();nodes.add(node("잠금 모드","잠금 모드 / 전원 끄기",true,0,"lock"));
        assertEquals("LOCKDOWN_TARGET_UNSAFE",select(nodes).state());
    }
    @Test public void missingLockDoesNotSelectNearestOtherButton(){
        assertEquals("LOCKDOWN_ITEM_NOT_FOUND",select(menu()).state());
    }
    @Test public void rejectsUnreadableOrTruncatedMenuEvenWithVisibleLock(){
        var nodes=menu();nodes.add(node("잠금 모드","",true,0,"lock"));
        assertEquals("LOCKDOWN_TREE_INCOMPLETE",LockdownPolicy.select("com.android.systemui",nodes,false).state());
    }
    @Test public void multipleDistinctLockTargetsAreAmbiguous(){
        var nodes=menu();nodes.add(node("잠금 모드","",true,0,"lock1"));nodes.add(node("Lockdown","",true,0,"lock2"));
        assertEquals("LOCKDOWN_TARGET_AMBIGUOUS",select(nodes).state());
    }
    @Test public void repeatedLabelAndIconWithinOneButtonAreOneTarget(){
        var nodes=menu();nodes.add(node("","",true,0,"lock"));nodes.add(node("잠금 모드","",false,3,"label"));nodes.add(node("","잠금 모드",false,3,"icon"));
        assertEquals(3,select(nodes).target());assertEquals(1,select(nodes).candidates());
    }
    @Test public void wrongPackageCannotImpersonatePowerMenu(){
        var nodes=menu();nodes.add(node("잠금 모드","",true,0,"lock"));
        assertEquals("POWER_MENU_NOT_RECOGNIZED",LockdownPolicy.select("com.android.chrome",nodes,true).state());
    }
    @Test public void disabledOrInvisibleLockNeverSelected(){
        for(boolean visible:List.of(false,true)){
            var nodes=menu();nodes.add(new LockdownPolicy.Node("com.android.systemui","잠금 모드","",visible,false,true,"lock",0));
            assertEquals(-1,select(nodes).target());
        }
    }
    @Test public void blankBoundsAreNotClickTargets(){
        var nodes=menu();nodes.add(node("잠금 모드","",true,0,""));assertEquals("LOCKDOWN_TARGET_UNSAFE",select(nodes).state());
    }
    @Test public void successIsNotReportedForMissingRejectedOrUnconfirmedLock(){
        for(String reason:List.of("LOCKDOWN_ITEM_NOT_FOUND","LOCKDOWN_CLICK_REJECTED","LOCKDOWN_LOCK_UNCONFIRMED"))
            assertNotEquals(LockdownPolicy.resultText("LOCKDOWN_LOCK_OBSERVED"),LockdownPolicy.resultText(reason));
    }
}
