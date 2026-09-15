package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** App-owned preferences and measured Android permission state, without leaving the app. */
public final class NotificationSettingsActivity extends Activity {
    private static final int INK=0xFF183631, MUTED=0xFF687D77, TEAL=0xFF007C78, PERMISSION=21;
    private Prefs prefs;
    private TextView permissionState, permissionHelp, guidanceState;
    private Button permissionButton;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);prefs=new Prefs(this);Notices.channels(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFFF4F7F6);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(8),dp(6),dp(16),dp(6));
        Button back=new Button(this);back.setText("‹");back.setTextSize(28);back.setTextColor(TEAL);back.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        back.setContentDescription("알림 설정 닫기");back.setOnClickListener(v->finish());toolbar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        toolbar.addView(text("앱 알림 설정",22,INK,true));root.addView(toolbar);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(12),dp(16),dp(24));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        LinearLayout result=card(content,"입실 · 퇴실 완료");
        TextView always=text("항상 알림 남기기",18,TEAL,true);always.setTag("notification-completion");result.addView(always);
        note(result,"출결 완료를 확인하면 입실·퇴실 알림을 각각 남깁니다. 직접 지우기 전까지 유지됩니다.");
        LinearLayout guidance=card(content,"연결 · 처리 안내");
        Switch toggle=new Switch(this);toggle.setTag("notification-guidance");toggle.setText("안내 알림 받기");toggle.setTextSize(17);toggle.setTextColor(INK);toggle.setMinHeight(dp(48));toggle.setChecked(prefs.guidanceNotifications());guidance.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        note(guidance,"Wi-Fi 연결 요청, 퇴실 미완료·강의실 이탈, Chrome 확인과 오류 안내를 받습니다.");
        guidanceState=text("",13,MUTED,false);guidanceState.setTag("notification-guidance-state");guidance.addView(guidanceState);
        toggle.setOnCheckedChangeListener((button,on)->{Notices.guidanceNotifications(this,on);refresh();});
        LinearLayout permission=card(content,"휴대폰 알림 허용 상태");
        permissionState=text("",17,TEAL,true);permissionState.setTag("notification-permission-state");permission.addView(permissionState);
        permissionHelp=text("",13,MUTED,false);permissionHelp.setPadding(0,dp(8),0,dp(8));permission.addView(permissionHelp);
        permissionButton=new Button(this);permissionButton.setText("알림 권한 요청");permissionButton.setTextColor(TEAL);permissionButton.setTag("notification-permission-request");permissionButton.setMinHeight(dp(48));permission.addView(permissionButton,new LinearLayout.LayoutParams(-1,-2));
        permissionButton.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                prefs.raw.edit().putBoolean("notification_permission_requested",true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},PERMISSION);
            }
        });
        TextView hint=text("감시 중 표시와 실행한 테스트의 결과 알림은 해당 기능이 동작할 때 유지됩니다.",12,MUTED,false);hint.setPadding(dp(4),dp(4),dp(4),0);content.addView(hint);
        refresh();
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==PERMISSION)refresh();}
    private void refresh(){
        if(permissionState==null)return;
        guidanceState.setText(prefs.guidanceNotifications()?"켜짐 · 안내가 필요할 때 알립니다":"꺼짐 · 입실·퇴실 완료 알림은 계속 남깁니다");
        boolean result=Notices.attendanceNotificationsAllowed(this), attention=Notices.attentionNotificationsAllowed(this);
        boolean global=getSystemService(NotificationManager.class).areNotificationsEnabled();
        boolean runtime=Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED;
        boolean canRequest=runtime&&(!prefs.raw.getBoolean("notification_permission_requested",false)||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS));
        permissionState.setText(!global?"휴대폰에서 알림 차단됨":!result?"출결 완료 알림 차단됨":!attention?"연결·처리 안내 채널 차단됨":"알림 허용됨");
        permissionState.setTextColor(result&&attention?TEAL:0xFF9A4418);
        permissionHelp.setText(canRequest?"아래 버튼을 누르면 앱 위에 알림 권한 요청창이 열립니다.":
            result&&attention?"설정은 이 화면에서 바로 저장됩니다. 휴대폰에서도 알림을 허용하고 있습니다.":
            "휴대폰에서 차단한 알림은 앱에서 직접 풀 수 없습니다. 휴대폰 설정의 출석 메이트 알림에서 허용해야 합니다. 앱의 안내 알림 설정은 여기에서 계속 바꿀 수 있습니다.");
        permissionButton.setVisibility(canRequest?View.VISIBLE:View.GONE);
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout card(LinearLayout parent,String title){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(18),dp(20),dp(18));
        GradientDrawable shape=new GradientDrawable();shape.setColor(android.graphics.Color.WHITE);shape.setCornerRadius(dp(20));card.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);parent.addView(card,params);
        TextView heading=text(title,13,TEAL,true);heading.setPadding(0,0,0,dp(12));card.addView(heading);return card;
    }
    private void note(LinearLayout parent,String value){TextView note=text(value,13,MUTED,false);note.setPadding(0,dp(8),0,dp(8));parent.addView(note);}
}
