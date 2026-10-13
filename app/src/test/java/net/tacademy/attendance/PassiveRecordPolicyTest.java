package net.tacademy.attendance;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;
public final class PassiveRecordPolicyTest {
    private static final LocalDate DAY=LocalDate.of(2026,10,13);
    private static final String URL=SiteProtocol.PAGE;
    private static String page(String in,String out){return "수강생 출결관리\n출결일 : 2026-10-13\n입실시간 : "+in+(out.isEmpty()?"":"\n퇴실시간 : "+out);}
    @Test public void eitherOffEnablesPassiveOnly(){assertTrue(PassiveRecordPolicy.enabled(false,false));assertTrue(PassiveRecordPolicy.enabled(false,true));assertTrue(PassiveRecordPolicy.enabled(true,false));assertFalse(PassiveRecordPolicy.enabled(true,true));}
    @Test public void entryThenCheckoutAreSaved(){var a=PassiveRecordPolicy.read(URL,page("09:03",""),DAY,null);assertEquals(new Policy.Record("09:03",""),a);assertEquals(new Policy.Record("09:03","17:50:05"),PassiveRecordPolicy.read(URL,page("09:03","17:50:05"),DAY,a));}
    @Test public void repeatedAndPartialPagesCannotOverwrite(){var a=new Policy.Record("09:03","");assertNull(PassiveRecordPolicy.read(URL,page("09:03",""),DAY,a));var b=new Policy.Record("09:03","17:50:05");assertNull(PassiveRecordPolicy.read(URL,page("09:03",""),DAY,b));assertNull(PassiveRecordPolicy.read(URL,page("09:03","17:50:05"),DAY,b));}
    @Test public void differentIdentityOrChangedTimeDoesNotOverwrite(){assertNull(PassiveRecordPolicy.read(URL,page("09:10","17:50"),DAY,new Policy.Record("09:03","")));}
    @Test public void wrongDateAddressAndPageRejected(){assertNull(PassiveRecordPolicy.read("https://example.com",page("09:03",""),DAY,null));assertNull(PassiveRecordPolicy.read(URL,page("09:03","").replace("2026-10-13","2026-10-12"),DAY,null));assertNull(PassiveRecordPolicy.read(URL,"출결일 : 2026-10-13 입실시간 : 09:03",DAY,null));}
    @Test public void errorsAndUnconfirmedDialogNeverBecomeRecords(){for(String error:new String[]{"정말로 퇴실 하시겠습니까?","인증절차를 진행한후","HTTP ERROR 503"})assertNull(PassiveRecordPolicy.read(URL,page("09:03","17:50")+error,DAY,null));}
    @Test public void malformedIncompleteAndReversedTimesRejected(){assertNull(PassiveRecordPolicy.read(URL,page("99:00",""),DAY,null));assertNull(PassiveRecordPolicy.read(URL,"수강생 출결관리 출결일 : 2026-10-13 퇴실시간 : 17:50",DAY,null));assertNull(PassiveRecordPolicy.read(URL,page("09:03","08:50"),DAY,null));}
    @Test public void completedPageCanSupplyBothTimes(){assertEquals(new Policy.Record("09:03","15:30"),PassiveRecordPolicy.read(URL,page("09:03","15:30"),DAY,null));}
}
