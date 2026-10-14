package net.tacademy.attendance;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import java.util.*;

/** Opens settings only. Permission switches and authentication remain controlled by the user. */
final class PhoneSettings {
    enum Target { ACCESSIBILITY, LOCATION_PERMISSION, LOCATION_SERVICE, ALARMS, NOTIFICATIONS, TEST_NOTIFICATIONS, BATTERY, WIFI, EXTEND, SCHEDULE, AUTO_CLICK }
    static Intent primary(Context c,Target target){
        String pkg=c.getPackageName();Uri app=Uri.parse("package:"+pkg);
        return switch(target){
            case ACCESSIBILITY -> accessibility(c);
            case LOCATION_PERMISSION, BATTERY -> new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,app);
            case LOCATION_SERVICE -> new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            case ALARMS -> Build.VERSION.SDK_INT>=31?new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,app):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,app);
            case NOTIFICATIONS, TEST_NOTIFICATIONS -> new Intent(c,NotificationSettingsActivity.class);
            case WIFI -> new Intent(Settings.ACTION_WIFI_SETTINGS);
            case EXTEND -> new Intent(Build.MANUFACTURER.equalsIgnoreCase("samsung")?Settings.ACTION_SETTINGS:Settings.ACTION_SECURITY_SETTINGS);
            case SCHEDULE -> new Intent(c,MainActivity.class).putExtra("open_tab",1).putExtra("focus_setting","automation");
            case AUTO_CLICK -> new Intent(c,MainActivity.class).putExtra("open_tab",2).putExtra("focus_setting","setup-readiness");
        };
    }
    private static Intent accessibility(Context c){
        // Android's detail action requires a system/installer permission; use the public highlighted list.
        String component=new ComponentName(c,ChromeAttendanceService.class).flattenToString();
        Bundle args=new Bundle();args.putString(":settings:fragment_args_key",component);
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtra(":settings:fragment_args_key",component)
            .putExtra(":settings:show_fragment_args",args);
    }
    static void open(Activity activity,Target target){
        ChromeAttendanceService.cancelSettingsMenu();
        if(target==Target.NOTIFICATIONS||target==Target.TEST_NOTIFICATIONS){
            // Keep every notification shortcut inside the app, including blocked permissions.
            launch(activity,primary(activity,target));return;
        }
        if(target==Target.EXTEND&&openTrustAgent(activity))return;
        boolean navigating=ChromeAttendanceService.settingsMenu(activity,target);
        if(launch(activity,primary(activity,target))){
            if(!navigating&&!guide(target).isEmpty())Toast.makeText(activity,guide(target),Toast.LENGTH_LONG).show();
            return;
        }
        ChromeAttendanceService.cancelSettingsMenu();
        Intent fallback=new Intent(target==Target.EXTEND?Settings.ACTION_SECURITY_SETTINGS:Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        if(target!=Target.EXTEND)fallback.setData(Uri.parse("package:"+activity.getPackageName()));
        if(!launch(activity,fallback))launch(activity,new Intent(Settings.ACTION_SETTINGS));
        Toast.makeText(activity,guide(target).isEmpty()?"휴대폰 설정에서 해당 항목을 확인해 주세요":guide(target),Toast.LENGTH_LONG).show();
    }
    static String guide(Target target){return switch(target){
        case ACCESSIBILITY -> "접근성 → 설치된 앱 → 출석 메이트 · Chrome 출석을 선택하세요";
        case LOCATION_PERMISSION -> "출석 메이트 앱 정보 → 권한 → 위치 → 항상 허용, 정확한 위치를 확인하세요";
        case BATTERY -> "출석 메이트 앱 정보 → 배터리에서 제한 상태를 확인하세요";
        case EXTEND -> "휴대폰 설정 → 잠금화면 및 AOD(또는 보안) → Extend Unlock을 선택하세요";
        default -> "";
    };}
    private static boolean launch(Activity a,Intent intent){try{a.startActivity(intent);return true;}catch(ActivityNotFoundException|SecurityException e){return false;}}
    private static boolean openTrustAgent(Activity a){
        PackageManager pm=a.getPackageManager();
        for(ResolveInfo resolved:pm.queryIntentServices(new Intent("android.service.trust.TrustAgentService"),PackageManager.GET_META_DATA)){
            ServiceInfo service=resolved.serviceInfo;
            if(service==null||!"com.google.android.gms".equals(service.packageName))continue;
            try(XmlResourceParser xml=service.loadXmlMetaData(pm,"android.service.trust.trustagent")){
                if(xml==null)continue;
                while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                    if(xml.getEventType()!=org.xmlpull.v1.XmlPullParser.START_TAG||!"trust-agent".equals(xml.getName()))continue;
                    String ns="http://schemas.android.com/apk/res/android";
                    String value=xml.getAttributeValue(ns,"settingsActivity");
                    int resource=xml.getAttributeResourceValue(ns,"settingsActivity",0);
                    if(resource!=0)value=pm.getResourcesForApplication(service.applicationInfo).getString(resource);
                    if(value==null)break;
                    ComponentName component=value.contains("/")?ComponentName.unflattenFromString(value):new ComponentName(service.packageName,value.startsWith(".")?service.packageName+value:value);
                    if(component==null||!service.packageName.equals(component.getPackageName()))break;
                    ActivityInfo info=pm.getActivityInfo(component,0);
                    if(info.exported&&info.enabled&&(info.permission==null||a.checkSelfPermission(info.permission)==PackageManager.PERMISSION_GRANTED))
                        return launch(a,new Intent().setComponent(component));
                    break;
                }
            }catch(Exception ignored){}
        }
        return false;
    }
    static Target forProblem(String problem){
        if(problem.contains("접근성"))return Target.ACCESSIBILITY;
        if(problem.contains("알람"))return Target.ALARMS;
        if(problem.contains("휴대폰의 위치 기능"))return Target.LOCATION_SERVICE;
        if(problem.contains("위치"))return Target.LOCATION_PERMISSION;
        if(problem.contains("결과 알림"))return Target.NOTIFICATIONS;
        if(problem.contains("자동 클릭"))return Target.AUTO_CLICK;
        if(problem.contains("Wi-Fi"))return Target.WIFI;
        if(problem.contains("출석 예약")||problem.contains("출석 시간")||problem.contains("퇴실 시각"))return Target.SCHEDULE;
        return null;
    }
}
