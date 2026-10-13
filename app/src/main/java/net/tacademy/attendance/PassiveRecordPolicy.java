package net.tacademy.attendance;

import java.time.LocalDate;

/** Passive observation never authorizes a click, navigation, wake, or server request. */
final class PassiveRecordPolicy {
    static boolean enabled(boolean reservation, boolean automaticClick) { return !reservation || !automaticClick; }
    static Policy.Record read(String address, String text, LocalDate today, Policy.Record old) {
        if(!ChromePage.attendanceAddress(address)||text==null||!text.contains("수강생 출결관리")
            ||ChromePage.serverUnavailable(text)||ChromePage.checkoutConfirmation(text)
            ||text.contains("인증절차를 진행"))return null;
        try {
            if(!ChromePage.date(text).equals(today))return null;
            Policy.Record seen=ChromePage.record(text);
            if(!seen.checkedIn())return null;
            // A partial or stale page must not erase an already confirmed checkout.
            if(old!=null&&old.checkedIn()) {
                if(!old.in().equals(seen.in()))return null;
                if(old.checkedOut())return null;
                if(!seen.checkedOut())return null;
            }
            if(seen.checkedOut()&&java.time.LocalTime.parse(seen.out()).isBefore(java.time.LocalTime.parse(seen.in())))return null;
            return seen;
        } catch(RuntimeException invalid){return null;}
    }
}
