package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.os.*;
import android.widget.*;
import java.util.*;

/** Refreshes measured settings without replacing controls or moving the user's scroll position. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic view requires its owning Activity; never inflated from XML.
final class SetupStatusView extends LinearLayout {
    private record Row(PhoneSettings.Target target,String label,boolean required,boolean ready,String state){}
    private final Activity activity;
    private final boolean extras;
    private final TextView summary;
    private final List<TextView> states=new ArrayList<>();
    private final List<Row> initial;
    SetupStatusView(Activity activity,boolean extras){
        super(activity);this.activity=activity;this.extras=extras;setOrientation(VERTICAL);
        summary=label("",16);summary.setTypeface(null,Typeface.BOLD);addView(summary);
        if(extras){TextView hint=label("항목을 누르면 해당 설정으로 이동합니다. 돌아오면 다시 확인합니다.",12);hint.setTextColor(0xFF687D77);addView(hint);}
        initial=read();
        for(Row item:initial){
            if(item.target==PhoneSettings.Target.ACCESSIBILITY)section("01  앱이 Chrome에 접속해 출결처리","오늘 탭에서 입실하기·퇴실하기를 누르면 앱이 처리해요.",false);
            if(item.target==PhoneSettings.Target.LOCATION_PERMISSION)section("02  잠금해제 해주면 앱이 출결처리","예약 시간에 잠금을 풀어 주세요. ①·② 설정과 아래 자동 클릭을 켜 주세요.",true);
            if(item.target==PhoneSettings.Target.AUTO_CLICK)section("03  시간되면 앱이 알아서 자동출결","①·② 설정 후 자동 클릭과 Extend Unlock을 설정해요. 잠금해제 유지 상태와 출석 Wi-Fi 연결이 필요해요.",true);
            LinearLayout row=new LinearLayout(activity);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(49));row.setPadding(0,dp(6),0,dp(6));
            TextView title=label(item.label,14);
            if(item.target==PhoneSettings.Target.ACCESSIBILITY){
                row.setOrientation(VERTICAL);row.setGravity(android.view.Gravity.START);
                LinearLayout heading=new LinearLayout(activity);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
                heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                Button help=new Button(activity);help.setText("설정하는 법 보기");help.setTextSize(12);help.setAllCaps(false);help.setTag("accessibility-howto");
                help.setOnClickListener(v->AccessibilitySetupHelp.show(activity));heading.addView(help,new LinearLayout.LayoutParams(-2,dp(48)));row.addView(heading,new LinearLayout.LayoutParams(-1,-2));
            }else row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView state=label("",12);state.setGravity(android.view.Gravity.END);states.add(state);row.addView(state,new LinearLayout.LayoutParams(dp(125),-2));
            row.setBackgroundResource(android.R.drawable.list_selector_background);row.setFocusable(true);row.setClickable(true);
            row.setTag("setup-row-"+item.target.name());
            row.setOnClickListener(v->{if(item.target==PhoneSettings.Target.AUTO_CLICK)ChromeClickSettings.show(activity,this::refresh);else PhoneSettings.open(activity,item.target);});row.setContentDescription(item.label+" 설정 열기");addView(row);
            if(item.target==PhoneSettings.Target.WIFI){
                TextView intelligent=label("인텔리전트 Wi-Fi 설정  ›",14);intelligent.setMinHeight(dp(49));intelligent.setGravity(android.view.Gravity.CENTER_VERTICAL);
                intelligent.setBackgroundResource(android.R.drawable.list_selector_background);intelligent.setFocusable(true);intelligent.setClickable(true);
                intelligent.setTag("setup-intelligent-wifi");intelligent.setContentDescription("인텔리전트 Wi-Fi 설정 열기");
                intelligent.setOnClickListener(v->ChromeAttendanceService.intelligentWifi(activity));addView(intelligent);
            }
        }
        refresh();
    }
    private void section(String title,String hint,boolean divider){
        if(divider){android.view.View line=new android.view.View(activity);line.setBackgroundColor(0xFFE1E6E4);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(14),0,dp(14));addView(line,lp);}
        TextView heading=label(title,14);heading.setTypeface(null,Typeface.BOLD);addView(heading);
        TextView description=label(hint,11);description.setTextColor(0xFF687D77);addView(description);
    }
    private TextView label(String s,int size){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(0xFF183631);t.setPadding(0,dp(3),0,dp(3));return t;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private List<Row> read(){
        Prefs p=new Prefs(activity);boolean fine=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean location=fine&&ScheduleController.backgroundLocationAllowed(activity);
        boolean locationOn=Build.VERSION.SDK_INT<28||activity.getSystemService(LocationManager.class).isLocationEnabled();
        boolean access=ChromeAttendanceService.available();List<Row> rows=new ArrayList<>();
        rows.add(new Row(PhoneSettings.Target.ACCESSIBILITY,"Chrome 접근성",true,access,access?"완료":ChromeAttendanceService.configured(activity)?"연결 대기":"설정 필요"));
        boolean notifications=Notices.attendanceNotificationsAllowed(activity);rows.add(new Row(PhoneSettings.Target.NOTIFICATIONS,"출결 완료 알림",true,notifications,notifications?"완료":"설정 필요"));
        rows.add(new Row(PhoneSettings.Target.ATTENDANCE_WIFI,"출석 Wi-Fi 등록",false,!p.ssids().isEmpty(),p.ssids().isEmpty()?"미등록 · 등록하기":"등록됨 · "+p.ssids().size()+"개"));
        rows.add(new Row(PhoneSettings.Target.CLASSROOM_LOCATION,"강의실 위치 등록",false,p.hasLocation(),p.hasLocation()?"등록됨 · "+p.radius()+"m":"미등록 · 등록하기"));
        rows.add(new Row(PhoneSettings.Target.LOCATION_PERMISSION,"위치 항상 허용",true,location,location?"완료":!fine?"정확한 위치 필요":"항상 허용 필요"));
        rows.add(new Row(PhoneSettings.Target.LOCATION_SERVICE,"휴대폰 위치",true,locationOn,locationOn?"켜짐":"꺼짐"));
        boolean alarms=AlarmScheduler.allowed(activity);rows.add(new Row(PhoneSettings.Target.ALARMS,"정확한 알람",true,alarms,alarms?"완료":"설정 필요"));
        rows.add(new Row(PhoneSettings.Target.SCHEDULE,"출석 예약",true,p.enabled(),p.enabled()?"켜짐":"꺼짐"));
        if(extras){
            boolean departureAlerts=p.guidanceNotifications()&&Notices.attentionNotificationsAllowed(activity);rows.add(new Row(PhoneSettings.Target.TEST_NOTIFICATIONS,"퇴실 이탈 알림",false,departureAlerts,departureAlerts?"허용됨":!p.guidanceNotifications()?"앱에서 꺼짐":"알림 허용 필요"));
            NetworkAccess.State wifi=NetworkAccess.inspect(activity);rows.add(new Row(PhoneSettings.Target.WIFI,"현재 출석 Wi-Fi",false,wifi.allowed(),wifi.allowed()?"연결됨":wifi.wifiTransportPresent()?"등록·권한 확인":"연결 필요"));
            boolean battery=activity.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(activity.getPackageName());rows.add(new Row(PhoneSettings.Target.BATTERY,"배터리 최적화 제외",false,battery,battery?"제외됨":"최적화 적용"));
        }
        rows.add(new Row(PhoneSettings.Target.AUTO_CLICK,"Chrome 자동 클릭(Extended Unlock)",true,p.auto(),p.auto()?(p.useExtendUnlock()?"신뢰 상태 사용":"직접 잠금해제"):"꺼짐"));
        return rows;
    }
    void refresh(){
        List<Row> rows=read();int ready=0,required=0;
        for(int i=0;i<rows.size();i++){Row r=rows.get(i);if(r.required){required++;if(r.ready)ready++;}
            TextView state=states.get(i);state.setText(activity.getString(R.string.setup_state,r.ready?"✓":r.required?"!":"·",r.state));state.setTextColor(r.ready?0xFF007C78:r.required?0xFF9A5800:0xFF687D77);
            state.setContentDescription(r.label+": "+r.state);((android.view.View)state.getParent()).setContentDescription(r.label+": "+r.state+" 설정 열기");
        }
        summary.setText("자동 처리 준비 "+ready+" / "+required+" · 버튼으로 출결은 ①부터");summary.setTextColor(ready==required?0xFF007C78:0xFF9A5800);
    }
}
