package net.tacademy.attendance;

import org.junit.Test;
import static org.junit.Assert.*;
import static net.tacademy.attendance.WifiConnectPolicy.Action.*;

public final class WifiConnectPolicyTest {
    private WifiConnectPolicy.Frame frame(String owner,boolean ready,boolean wifi,boolean connected,boolean page,boolean input,boolean row,boolean details,boolean connect){
        return new WifiConnectPolicy.Frame(owner,ready,wifi,connected,page,input,row,details,connect);
    }
    private WifiConnectPolicy.Frame list(boolean row){return frame("com.android.settings",true,true,false,true,false,row,false,false);}
    private WifiConnectPolicy.Frame dialog(boolean target,boolean input){return frame("com.android.settings",true,true,false,false,input,false,target,true);}
    @Test public void selectionAndConfirmationAreEachSingleAttempt(){
        var p=new WifiConnectPolicy(0);
        assertEquals(WAIT,p.next(500,list(true)));
        assertEquals(SELECT_NETWORK,p.next(1000,list(true)));
        assertEquals(WAIT,p.next(2000,list(true)));
        assertEquals(CONNECT,p.next(2100,dialog(true,false)));
        assertEquals(WAIT,p.next(5000,dialog(true,false)));
        assertEquals(TIMEOUT,p.next(20000,dialog(true,false)));
    }
    @Test public void clickAcceptanceDoesNotImplyConnected(){
        var p=new WifiConnectPolicy(0);p.next(1000,list(true));p.next(2100,dialog(true,false));
        assertEquals(WAIT,p.next(5000,list(false)));
        assertEquals(CONNECTED,p.next(5001,frame("com.android.settings",true,true,true,true,false,false,false,false)));
    }
    @Test public void wrongOrAmbiguousSsidHasNoSelectableRow(){
        var p=new WifiConnectPolicy(0);
        assertEquals(WAIT,p.next(1000,list(false)));
        assertEquals(TIMEOUT,p.next(20000,list(false)));
    }
    @Test public void aGenericConnectButtonWithoutTheTargetTitleIsNeverClicked(){
        var p=new WifiConnectPolicy(0);p.next(1000,list(true));
        assertEquals(WAIT,p.next(2100,dialog(false,false)));
    }
    @Test public void targetDialogCannotBeConfirmedBeforeThisAttemptSelectedTheNetwork(){
        assertEquals(WAIT,new WifiConnectPolicy(0).next(2000,dialog(true,false)));
    }
    @Test public void credentialInputStopsEvenIfPositiveButtonIsEnabled(){
        var p=new WifiConnectPolicy(0);p.next(1000,list(true));
        assertEquals(NEED_INPUT,p.next(2100,dialog(true,true)));
    }
    @Test public void anotherAppCannotProvideWifiButtons(){
        assertEquals(LEFT_SETTINGS,new WifiConnectPolicy(0).next(1000,frame("com.example.fake",true,true,false,true,false,true,true,true)));
    }
    @Test public void returningToTheAppStopsSelection(){
        var p=new WifiConnectPolicy(0);p.next(1000,list(false));
        assertEquals(LEFT_SETTINGS,p.next(1200,frame("net.tacademy.attendance",true,true,false,false,false,false,false,false)));
    }
    @Test public void startingActivityAndMissingTreeAreBoundedWaits(){
        var p=new WifiConnectPolicy(0);
        assertEquals(WAIT,p.next(1000,frame("net.tacademy.attendance",true,true,false,false,false,false,false,false)));
        assertEquals(WAIT,p.next(2000,frame("",true,true,false,false,false,false,false,false)));
        assertEquals(TIMEOUT,p.next(20000,frame("",true,true,false,false,false,false,false,false)));
    }
    @Test public void unreadableSystemScreenStopsPromptlyWithoutAnyClick(){
        var p=new WifiConnectPolicy(0);
        var empty=frame("",true,true,false,false,false,false,false,false);
        assertEquals(WAIT,p.next(5999,empty));
        assertEquals(SETTINGS_UNREADABLE,p.next(6000,empty));
    }
    @Test public void radioOffAndLockNeverCauseToggleOrConnectActions(){
        assertEquals(WIFI_OFF,new WifiConnectPolicy(0).next(1000,frame("com.android.settings",true,false,true,true,false,true,true,true)));
        assertEquals(WIFI_OFF,new WifiConnectPolicy(0).next(1000,frame("com.android.settings",true,false,false,true,false,true,true,true)));
        assertEquals(LOCKED,new WifiConnectPolicy(0).next(1000,frame("com.android.settings",false,true,false,true,false,true,true,true)));
    }
    @Test public void nonWifiSettingsListCannotCauseAnSsidClick(){
        assertEquals(WAIT,new WifiConnectPolicy(0).next(1000,frame("com.android.settings",true,true,false,false,false,true,false,false)));
    }
    @Test public void twoActionsCannotRaceOnTheSameUiFrame(){
        var p=new WifiConnectPolicy(0);p.next(1000,list(true));
        assertEquals(WAIT,p.next(1400,dialog(true,false)));
        assertEquals(CONNECT,p.next(2000,dialog(true,false)));
    }
}
