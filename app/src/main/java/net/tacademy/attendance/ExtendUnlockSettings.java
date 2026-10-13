package net.tacademy.attendance;

import android.app.*;
import android.view.Gravity;
import android.widget.*;

/** Optional app preference; never changes the phone's trust settings itself. */
final class ExtendUnlockSettings extends LinearLayout {
    ExtendUnlockSettings(Activity a, Runnable changed) {
        super(a); setOrientation(VERTICAL); int pad=(int)(16*getResources().getDisplayMetrics().density);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setColor(0xFFFFFFFF); bg.setCornerRadius(pad); setBackground(bg); LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2); outer.bottomMargin=pad; setLayoutParams(outer); setPadding(pad,pad,pad,pad); setTag("extend-unlock-optional");
        Prefs p=new Prefs(a);
        LinearLayout heading=new LinearLayout(a); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(a);title.setText("Extend Unlock · 선택 설정");title.setTextSize(16);title.setTextColor(0xFF183631);title.setTypeface(null,1);
        heading.addView(title,new LayoutParams(0,-2,1));
        Button help=new Button(a);help.setText("?");help.setContentDescription("Extend Unlock 설명 보기");heading.addView(help,new LayoutParams(pad*3,pad*3));addView(heading);
        help.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("Extend Unlock이란?")
            .setMessage("신뢰하는 장소나 Bluetooth 기기 근처에서 잠금 해제 상태를 유지하는 Android 기능이에요. Wi-Fi 등록과는 별도로 휴대폰에서 설정해야 해요.\n\n사용하면 신뢰 상태에서 화면을 깨워 자동출석을 시도해요. PIN·지문 재인증이 필요한 경우에는 자동으로 풀 수 없어요.\n\n사용하지 않으면 예약 시간에 화면만 한 번 깨워요. 잠금이 풀려 있을 때만 자동출석하며 PIN·지문 인증이나 밀어서 해제가 필요하면 멈춰요. 출석 시간 안에 직접 해제하면 다시 확인해요. 자동출석을 끄면 화면도 깨우지 않고, 직접 연 Chrome의 출결 기록만 저장해요.\n\n이 선택은 앱의 동작만 바꿔요. 휴대폰에 이미 등록한 Extend Unlock은 아래 휴대폰 설정에서 직접 해제해야 해요. 완료 후 잠금 모드를 쓰면 다음에는 PIN 등으로 한 번 해제해야 해요.")
            .setPositiveButton("확인",null).show());
        Switch choice=new Switch(a);choice.setText("Extend Unlock 사용");choice.setTextSize(16);choice.setMinHeight(pad*3);choice.setChecked(p.useExtendUnlock());addView(choice);
        TextView state=new TextView(a);state.setTextSize(13);state.setTextColor(0xFF536963);addView(state);
        Button open=new Button(a);open.setText("휴대폰 Extend Unlock 설정");addView(open);open.setOnClickListener(v->PhoneSettings.open(a,PhoneSettings.Target.EXTEND));
        Runnable refresh=()->state.setText(p.useExtendUnlock()?"선택됨 · 휴대폰 신뢰 설정을 확인해 주세요":"기본 방식 · 화면 깨우기, 잠금이 풀린 경우 자동출석");refresh.run();
        choice.setOnCheckedChangeListener((v,on)->{
            ScheduledUnlock.cancel(a,"EXTEND_PREFERENCE_CHANGED");
            var editor=p.raw.edit().putBoolean("use_extend_unlock",on);
            editor.remove("scheduled_lock_mode").apply();refresh.run();changed.run();
        });
    }
}
