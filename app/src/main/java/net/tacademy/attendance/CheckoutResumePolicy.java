package net.tacademy.attendance;
import java.time.*;
/** A dated notification is a user-initiated checkout, never permission for early/next-day clicks. */
final class CheckoutResumePolicy {
    static boolean allowed(String day,ZonedDateTime now,boolean enabled,boolean automatic,
                           boolean classDay,boolean completed,LocalTime checkout) {
        ZonedDateTime local=now.withZoneSameInstant(Policy.ZONE);
        return day!=null && day.equals(local.toLocalDate().toString()) && enabled && automatic
            && classDay && !completed && !local.toLocalTime().isBefore(checkout);
    }
}
