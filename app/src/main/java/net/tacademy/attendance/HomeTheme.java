package net.tacademy.attendance;

import java.time.*;

/** Today-screen appearance only; never starts or changes attendance work. */
public enum HomeTheme {
    WARM(0xFFFAF6EF, 0xFFFFFCF7, 0xFF422C23, 0xFF776156, 0xFF965031, 0xFFF7E4D4, 0xFFEBDDCF, 0xFFF1BC91),
    NIGHT(0xFF101B2B, 0xFF1A293D, 0xFFF3F6FC, 0xFFACBCD0, 0xFFA7CFF5, 0xFF263D59, 0xFF34465E, 0xFFA7CFF5);

    public final int background, surface, ink, muted, accent, soft, border, button;
    HomeTheme(int background, int surface, int ink, int muted, int accent, int soft, int border, int button) {
        this.background=background; this.surface=surface; this.ink=ink; this.muted=muted;
        this.accent=accent; this.soft=soft; this.border=border; this.button=button;
    }
    public static LocalTime nightStarts(String entryEnd, String checkout) {
        return LocalTime.ofSecondOfDay(Math.max(LocalTime.parse(entryEnd).toSecondOfDay(), LocalTime.parse(checkout).toSecondOfDay()-3600));
    }
    public static HomeTheme at(ZonedDateTime now, String entryEnd, String checkout) {
        return now.withZoneSameInstant(Policy.ZONE).toLocalTime().isBefore(nightStarts(entryEnd, checkout)) ? WARM : NIGHT;
    }
}