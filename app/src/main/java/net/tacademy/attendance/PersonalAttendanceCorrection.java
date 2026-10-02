package net.tacademy.attendance;
import android.content.SharedPreferences;
/** Owner-confirmed corrections for this private creator build, never applied to the general build. */
final class PersonalAttendanceCorrection {
    static final String MARKER="owner_correction_20261001";
    private PersonalAttendanceCorrection(){}
    static boolean apply(SharedPreferences p,boolean creator){
        if(!creator||p.getBoolean(MARKER,false))return true;
        // Preserve observed timestamps. The user's explicit classification is the only source here.
        return p.edit().putString("manual_attendance:2026-09-28","EARLY_LEAVE")
            .putString("manual_attendance:2026-09-30","LATE")
            .putString("manual_source:2026-09-28","OWNER_CONFIRMED_20261001")
            .putString("manual_source:2026-09-30","OWNER_CONFIRMED_20261001")
            .putString("late_after","09:11").putBoolean("late_disabled",false).putBoolean(MARKER,true).commit();
    }
}
