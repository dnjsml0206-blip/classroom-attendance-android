package net.tacademy.attendance;

import android.app.*;
import android.widget.*;

/** One entry point for automatic clicks and optional trusted unlocking. */
final class ChromeClickSettings {
    static void show(Activity activity,Runnable changed) {
        Prefs prefs=new Prefs(activity);
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(pad,pad,pad,pad);
        Switch auto=new Switch(activity);auto.setText("Chrome 입실·퇴실 자동 클릭");auto.setTextSize(16);auto.setMinHeight(pad*3);auto.setChecked(prefs.auto());body.addView(auto);
        TextView hint=new TextView(activity);hint.setTextSize(13);hint.setTextColor(0xFF536963);
        hint.setText("예약과 필요한 권한을 설정하면 출석 시간에 작동해요. Extended Unlock은 선택이에요. 인증이 유지된 잠금화면은 바로 닫고 출석하며, PIN·지문 재인증이 필요할 때만 직접 잠금을 풀어 주세요.");body.addView(hint);
        body.addView(new ExtendUnlockSettings(activity,changed));
        auto.setOnCheckedChangeListener((view,on)->{
            if(on&&!ChromeAttendanceService.available()){
                auto.setChecked(false);Toast.makeText(activity,"Chrome 접근성을 먼저 연결해 주세요",Toast.LENGTH_LONG).show();return;
            }
            if(on)prefs.enableAutomaticClick();else {ActivationRun.cancel();prefs.raw.edit().putBoolean("auto",false).apply();}
            prefs.log(on?"Chrome 자동 클릭 켜짐":"Chrome 기록 수집 모드");
            ScheduleController.reconcile(activity);changed.run();
        });
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Chrome 자동 클릭(Extended Unlock)").setView(scroll).setPositiveButton("완료",null).create();
        dialog.setOnDismissListener(d->{changed.run();if(activity instanceof MainActivity main)main.refreshAutomaticSettings();});
        dialog.show();
    }
}
