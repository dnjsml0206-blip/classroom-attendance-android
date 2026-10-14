package net.tacademy.attendance;
import org.junit.Test;import java.time.*;import static org.junit.Assert.*;
public class CheckoutResumePolicyTest {
    private final ZonedDateTime now=ZonedDateTime.of(2026,10,14,18,10,0,0,Policy.ZONE);
    private boolean allowed(String day,boolean enabled,boolean auto,boolean classDay,boolean done,LocalTime start){
        return CheckoutResumePolicy.allowed(day,now,enabled,auto,classDay,done,start);
    }
    @Test public void notificationCanResumeAfterAutomaticRetryWindow(){assertTrue(allowed("2026-10-14",true,true,true,false,LocalTime.of(17,50,5)));}
    @Test public void staleOrCompletedNotificationCannotClick(){
        assertFalse(allowed("2026-10-13",true,true,true,false,LocalTime.NOON));
        assertFalse(allowed(null,true,true,true,false,LocalTime.NOON));
        assertFalse(allowed("2026-10-14",true,true,true,true,LocalTime.NOON));
    }
    @Test public void honorsDisabledSettingsHolidayAndCheckoutTime(){
        assertFalse(allowed("2026-10-14",false,true,true,false,LocalTime.NOON));
        assertFalse(allowed("2026-10-14",true,false,true,false,LocalTime.NOON));
        assertFalse(allowed("2026-10-14",true,true,false,false,LocalTime.NOON));
        assertFalse(allowed("2026-10-14",true,true,true,false,LocalTime.of(18,10,5)));
    }
}
