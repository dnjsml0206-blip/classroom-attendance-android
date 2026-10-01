package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
final class OrientationChecks {
    private final Instrumentation test;private Context c;private Prefs p;private Activity activity,main;private Map<String,?> saved;private int count,savedPage;private SharedPreferences bookmarks;
    OrientationChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());bookmarks=c.getSharedPreferences("course_documents",0);savedPage=bookmarks.getInt("orientation_page",-1);bookmarks.edit().remove("orientation_page").commit();
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putString("chrome_pending","orientation-sentinel").commit();});
        main=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        View entry=main.getWindow().getDecorView().findViewWithTag("attendance-orientation");check(entry!=null,"Today has the orientation material entry point");
        ui(()->entry.requestRectangleOnScreen(new android.graphics.Rect(0,0,entry.getWidth(),entry.getHeight()),true));settle();capture("01-entry.png");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(OrientationActivity.class.getName(),null,false);ui(entry::performClick);activity=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);check(activity!=null,"Today button opens the in-app reader");awaitPage(1);
        check(value("orientation-number").equals("1 / 47")&&!view("orientation-prev").isEnabled(),"All 47 bundled pages are available offline with a first-page boundary");capture("02-reader.png");
        View pdf=view("orientation-page");Field scale=pdf.getClass().getDeclaredField("scale");scale.setAccessible(true);float initial=scale.getFloat(pdf);
        ui(()->{long now=SystemClock.uptimeMillis();for(int i=0;i<2;i++){MotionEvent down=MotionEvent.obtain(now+i*100,now+i*100,MotionEvent.ACTION_DOWN,pdf.getWidth()/2f,pdf.getHeight()/2f,0),up=MotionEvent.obtain(now+i*100,now+i*100+40,MotionEvent.ACTION_UP,pdf.getWidth()/2f,pdf.getHeight()/2f,0);pdf.dispatchTouchEvent(down);pdf.dispatchTouchEvent(up);down.recycle();up.recycle();}});settle();check(scale.getFloat(pdf)>initial,"Double tap enlarges the document");capture("03-zoom.png");
        click("orientation-next");awaitPage(2);check(bookmarks.getInt("orientation_page",-1)==1,"Successful page rendering saves the bookmark");
        click("orientation-number");Field field=OrientationActivity.class.getDeclaredField("jumpDialog");field.setAccessible(true);AlertDialog dialog=(AlertDialog)field.get(activity);EditText input=dialog.getWindow().getDecorView().findViewWithTag("orientation-jump-input");final EditText number=input;
        ui(()->{number.setText("48");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});check(dialog.isShowing()&&number.getError()!=null,"Out-of-range page input stays in the dialog");
        ui(()->{number.setText("47");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});awaitPage(47);check(!view("orientation-next").isEnabled(),"Last page renders and forward navigation is bounded");
        close();open();awaitPage(47);check(value("orientation-number").equals("47 / 47"),"Reopening resumes the last page");
        click("orientation-cover");awaitPage(1);check(!view("orientation-prev").isEnabled(),"First-page shortcut works from the last page");
        ui(()->{for(int i=0;i<8;i++)view("orientation-next").performClick();});awaitPage(9);check(value("orientation-number").equals("9 / 47"),"Rapid navigation only displays the latest requested page");
        click("orientation-cover");awaitPage(1);
        monitor=test.addMonitor(OrientationActivity.class.getName(),null,false);ui(activity::recreate);Activity replacement=test.waitForMonitorWithTimeout(monitor,8000);test.removeMonitor(monitor);check(replacement!=null,"Activity recreation restores the reader");activity=replacement;awaitPage(1);
        check(!p.enabled()&&!p.auto()&&!MonitorService.running&&!ChromeAttendanceService.busy()&&"orientation-sentinel".equals(p.raw.getString("chrome_pending","")),"Document reading preserves attendance state and pending clicks");
        restore();result.putString("stream","PASS: "+count+" orientation checks; offline, no attendance requests.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void open(){activity=test.startActivitySync(new Intent(c,OrientationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void awaitPage(int page)throws Exception{long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until){if(value("orientation-number").equals(page+" / 47")&&value("orientation-status").startsWith("오프라인")){settle();return;}SystemClock.sleep(150);}throw new AssertionError("Render timeout: "+page+" "+value("orientation-status"));}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void settle(){SystemClock.sleep(450);test.waitForIdleSync();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{SystemClock.sleep(300);Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"orientation-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(main!=null){ui(main::finish);main=null;}if(bookmarks!=null){var e=bookmarks.edit();if(savedPage>=0)e.putInt("orientation_page",savedPage);else e.remove("orientation_page");e.commit();}if(c==null||saved==null)return;ui(()->{var edit=p.raw.edit().clear();for(var item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();AlarmScheduler.arm(c,false);});}
}
