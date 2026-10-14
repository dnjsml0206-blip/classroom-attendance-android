package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
/** Emulator-only navigation and protected diagnostic drawer checks; automation is disabled. */
final class TabLayoutChecks {
    final Instrumentation test;Context c;Prefs prefs;MainActivity activity;Map<String,?> saved;int count;
    TabLayoutChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();prefs=new Prefs(c);saved=new HashMap<>(prefs.raw.getAll());
        ui(()->{prefs.raw.edit().putBoolean("first_use_guide_pending",false).putBoolean("enabled",false).putBoolean("auto",false).putString("chrome_pending","tab-test-sentinel").commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));});
        activity=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        check(view("attendance-address")==null&&view("initial-settings")!=null,"Settings routes address to initial setup");
        for(String tag:new String[]{"automation","setup-schedule","setup-days"})check(view(tag)!=null,"Settings contains "+tag);
        shot("settings-reorganized.png",false);
        click("initial-settings");
        check(view("attendance-address")!=null&&view("initial-methods")!=null&&view("initial-steps")!=null,"Initial setup contains address, methods and ordered manual");
        shot("initial-setup.png",true);
        ui(activity::onBackPressed);settle();
        check(view("initial-settings")!=null,"Back returns to Settings");
        String oldAddress=prefs.raw.getString("chrome_shortcut_url",null);
        prefs.raw.edit().putString("chrome_shortcut_url",SiteProtocol.PAGE+"?course=demo").commit();
        check(prefs.chromeAddress(SiteProtocol.PAGE).equals(SiteProtocol.PAGE+"?course=demo"),"Attendance launches resolve the saved shortcut");
        prefs.raw.edit().putString("chrome_shortcut_url",SiteProtocol.AUTH_PAGE).commit();
        check(prefs.chromeAddress(SiteProtocol.PAGE).equals(SiteProtocol.PAGE)&&prefs.chromeAddress(SiteProtocol.AUTH_PAGE).equals(SiteProtocol.AUTH_PAGE),"Authentication shortcut cannot replace the actual attendance page");
        prefs.raw.edit().putString("chrome_shortcut_url","https://example.com/").commit();check(prefs.shortcutAddress().equals(SiteProtocol.PAGE),"Invalid stored shortcut falls back to supported page");
        if(oldAddress==null)prefs.raw.edit().remove("chrome_shortcut_url").commit();else prefs.raw.edit().putString("chrome_shortcut_url",oldAddress).commit();
        check(view("general-notifications")!=null&&view("creator-diagnostics")!=null,"Settings retains notifications and creator tools");
        check(view("setup-location")==null&&view("setup-wifi")==null&&view("setup-readiness")==null,"Automatic settings are absent from general Settings");
        check(view("creator-body").getVisibility()==View.GONE,"Creator actions start collapsed");
        check(!findText(activity.getWindow().getDecorView(),"로그 확인 · GitHub 업로드").isShown(),"Logs are not visible before password entry");
        click("creator-toggle");AlertDialog dialog=dialog();EditText password=dialog.getWindow().getDecorView().findViewWithTag("creator-password");
        ui(()->{password.setText("0410");dialog.getButton(-1).performClick();});
        check(dialog.isShowing()&&password.getError()!=null&&view("creator-body").getVisibility()==View.GONE,"Previous password leaves creator drawer locked");
        ui(()->{password.setText("7671");dialog.getButton(-1).performClick();});settle();
        check(view("creator-body").getVisibility()==View.VISIBLE&&!dialog.isShowing(),"Correct password expands the inline drawer");
        click("creator-toggle");check(view("creator-body").getVisibility()==View.GONE,"Tapping the open header collapses and relocks");
        unlock();tab(2);
        for(String tag:new String[]{"setup-readiness","setup-wifi","setup-location","setup-screen","setup-chrome"})check(view(tag)!=null,"Automatic attendance contains "+tag);
        check(view("general-notifications")==null&&view("creator-diagnostics")==null,"General settings and logs are absent from automatic attendance");
        check(findText(activity.getWindow().getDecorView(),"자동출석")!=null&&findText(activity.getWindow().getDecorView(),"연결")==null,"Bottom navigation uses the new automatic attendance name");
        check(view("automation")==null&&view("setup-schedule")==null&&view("setup-days")==null,"Reservation cards only appear in Settings");
        View access=view("setup-row-ACCESSIBILITY"),notify=view("setup-row-NOTIFICATIONS"),location=view("setup-row-LOCATION_PERMISSION"),auto=view("setup-row-AUTO_CLICK");
        check(access.getTop()<notify.getTop()&&notify.getTop()<location.getTop()&&location.getTop()<auto.getTop(),"Manual recording comes before scheduled and trusted settings");
        shot("automatic-setup.png",false);
        shot("automatic-setup-full.png",true);
        check(PhoneSettings.primary(c,PhoneSettings.Target.SCHEDULE).getIntExtra("open_tab",-1)==1,"Reservation readiness link targets the new tab");
        tab(1);check(view("creator-body").getVisibility()==View.GONE,"Changing tabs requires the password again");
        unlock();ui(()->test.callActivityOnPause(activity));check(view("creator-body").getVisibility()==View.GONE,"Leaving the activity relocks creator tools");ui(()->test.callActivityOnResume(activity));settle();
        check(!prefs.enabled()&&!prefs.auto()&&!MonitorService.running&&"tab-test-sentinel".equals(prefs.raw.getString("chrome_pending","")),"Navigation and password checks never enable or submit attendance");
        restore();result.putString("stream","PASS: "+count+" tab consolidation and password drawer checks. No attendance requests.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void shot(String name,boolean full)throws Exception{
        test.waitForIdleSync();
        java.io.File dest=new java.io.File(c.getExternalFilesDir(null),name);
        if(full){
            ui(()->{try{
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("content");field.setAccessible(true);
                View v=(View)field.get(activity);
                android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(v.getWidth(),v.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas=new android.graphics.Canvas(image);canvas.drawColor(0xFFF4F7F6);v.draw(canvas);
                try(var out=new java.io.FileOutputStream(dest)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
            }catch(Exception e){throw new RuntimeException(e);}});
        }else{
            android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();
            try(var out=new java.io.FileOutputStream(dest)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        }
    }
    private void unlock()throws Exception{click("creator-toggle");AlertDialog d=dialog();ui(()->{((EditText)d.getWindow().getDecorView().findViewWithTag("creator-password")).setText("7671");d.getButton(-1).performClick();});settle();}
    private AlertDialog dialog()throws Exception{Field f=MainActivity.class.getDeclaredField("creatorPasswordDialog");f.setAccessible(true);return (AlertDialog)f.get(activity);}
    private void tab(int tab)throws Exception{Method m=MainActivity.class.getDeclaredMethod("render",int.class);m.setAccessible(true);ui(()->{try{m.invoke(activity,tab);}catch(Exception e){throw new RuntimeException(e);}});settle();}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private View findText(View v,String label){if(v instanceof TextView t&&label.contentEquals(t.getText()))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),label);if(found!=null)return found;}return null;}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private void settle(){SystemClock.sleep(300);test.waitForIdleSync();}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    @SuppressWarnings("unchecked") private void restore(){if(activity!=null){ui(activity::finish);activity=null;}if(saved==null)return;ui(()->{var edit=prefs.raw.edit().clear();for(var e:saved.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();ScheduleController.reconcile(c);});}
}
