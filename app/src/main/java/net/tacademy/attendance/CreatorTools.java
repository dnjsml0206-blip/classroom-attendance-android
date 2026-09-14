package net.tacademy.attendance;

import android.content.*;

/** Available only in a privately built creator APK, never via a public-app preference. */
final class CreatorTools {
    static boolean enabled(){return BuildConfig.CREATOR_TOOLS;}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("creator_tools",Context.MODE_PRIVATE);}
    static boolean testsVisible(Context c){return enabled()&&prefs(c).getBoolean("tests_visible",true);}
    static void require(){if(!enabled())throw new IllegalStateException("제작자용 앱에서만 사용할 수 있습니다.");}
    static void completeTests(Context c){require();LockLab.cancel(c);c.getSystemService(android.app.NotificationManager.class).cancel(LockLab.NOTICE);prefs(c).edit().putBoolean("tests_visible",false).apply();}
    static void enableTests(Context c){require();prefs(c).edit().putBoolean("tests_visible",true).apply();}
}
