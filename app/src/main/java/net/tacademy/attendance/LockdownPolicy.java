package net.tacademy.attendance;

import java.util.List;
import java.util.Locale;

/** Exact labels only: this policy must never select power-off, restart or emergency actions. */
final class LockdownPolicy {
    static final int POLL_MS = 200;
    static final int MENU_TIMEOUT_MS = 4000;
    static final int SETTLE_MS = 600;
    static final int LOCK_TIMEOUT_MS = 3000;

    private LockdownPolicy() {}

    static boolean systemPackage(String packageName) {
        return "com.android.systemui".equals(packageName) || "android".equals(packageName);
    }

    static boolean lockdownLabel(String label) {
        return switch (normalize(label)) {
            case "잠금 모드", "잠금모드", "잠금", "lockdown", "lockdown mode" -> true;
            default -> false;
        };
    }

    static boolean powerMenu(String packageName, List<String> labels) {
        if (!systemPackage(packageName) || labels == null) return false;
        boolean powerOff = false;
        boolean restart = false;
        for (String label : labels) {
            switch (normalize(label)) {
                case "전원 끄기", "power off" -> powerOff = true;
                case "다시 시작", "restart" -> restart = true;
                default -> { }
            }
        }
        return powerOff && restart;
    }

    private static String normalize(String label) {
        return label == null ? "" : label.replaceAll("[\\s\\p{Z}]+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
