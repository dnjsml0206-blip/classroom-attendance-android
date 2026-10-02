package net.tacademy.attendance;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LockdownPolicyTest {
    @Test public void acceptsOnlyExactSystemPackages() {
        assertTrue(LockdownPolicy.systemPackage("com.android.systemui"));
        assertTrue(LockdownPolicy.systemPackage("android"));
        for (String name : Arrays.asList(null, "", "com.android.settings", "com.android.chrome",
                "net.tacademy.attendance", "com.samsung.android.app", "com.android.systemui.fake",
                "fake.android", "COM.ANDROID.SYSTEMUI", " com.android.systemui ")) {
            assertFalse(String.valueOf(name), LockdownPolicy.systemPackage(name));
        }
    }

    @Test public void acceptsSamsungAndAndroidLockdownLabelsWithWhitespaceAndCaseNormalization() {
        for (String label : List.of("잠금 모드", "잠금모드", "잠금", "Lockdown", "Lockdown mode",
                "  잠금  모드  ", "잠금\n모드", " LOCKDOWN\tMODE ", "Lockdown\u00a0mode")) {
            assertTrue(label, LockdownPolicy.lockdownLabel(label));
        }
    }

    @Test public void rejectsSubstringMatchesAndOtherPowerActions() {
        for (String label : Arrays.asList(null, "", " ", "잠금 해제", "잠금 모드 설정", "잠금화면",
                "잠금 모드를 사용하세요", "Lockdown settings", "Enable Lockdown", "Lockdown mode off",
                "Lock down", "Power off", "전원 끄기", "Restart", "다시 시작", "Emergency",
                "긴급 모드", "긴급전화", "Lockdown / Power off", "잠금/전원 끄기")) {
            assertFalse(String.valueOf(label), LockdownPolicy.lockdownLabel(label));
        }
    }

    @Test public void recognizesKoreanSamsungAndEnglishAndroidPowerMenus() {
        assertTrue(LockdownPolicy.powerMenu("com.android.systemui",
                List.of("전원 끄기", "다시 시작", "긴급 모드", "잠금 모드")));
        assertTrue(LockdownPolicy.powerMenu("android",
                List.of("Power off", "Restart", "Emergency", "Lockdown")));
        assertTrue(LockdownPolicy.powerMenu("com.android.systemui",
                List.of(" POWER  OFF ", "RESTART", "Lockdown mode")));
        assertTrue(LockdownPolicy.powerMenu("android",
                Arrays.asList(null, "전원\n끄기", "다시\t시작")));
    }

    @Test public void requiresBothExactPowerOffAndRestartLabels() {
        for (List<String> labels : List.of(List.<String>of(), List.of("잠금 모드"),
                List.of("전원 끄기", "잠금 모드"), List.of("다시 시작", "잠금 모드"),
                List.of("Power off device", "Restart"), List.of("Power off", "Restart now"),
                List.of("전원 끄기 설정", "다시 시작"), List.of("전원 끄기", "자동으로 다시 시작"),
                List.of("Power off / Restart", "Lockdown"))) {
            assertFalse(labels.toString(), LockdownPolicy.powerMenu("com.android.systemui", labels));
        }
        assertFalse(LockdownPolicy.powerMenu("android", null));
    }

    @Test public void similarMenusFromOtherAppsNeverPass() {
        List<String> labels = List.of("Power off", "Restart", "Lockdown");
        for (String name : Arrays.asList(null, "", "com.android.chrome", "com.android.settings",
                "net.tacademy.attendance", "com.android.systemui.fake")) {
            assertFalse(String.valueOf(name), LockdownPolicy.powerMenu(name, labels));
        }
    }
}
