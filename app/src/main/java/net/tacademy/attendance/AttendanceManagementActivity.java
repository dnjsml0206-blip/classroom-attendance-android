package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Compact, local attendance estimates with explicit missing-data and forecast assumptions. */
public final class AttendanceManagementActivity extends Activity {
    private Prefs prefs;
    private HomeTheme theme;
    private LinearLayout content;
    private ScrollView scroll;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=this::render;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(key==null||key.startsWith("record:")||key.startsWith("manual_attendance:")||Set.of("days","excluded","late_after","late_disabled","checkout").contains(key)){
            handler.removeCallbacks(refresh);handler.postDelayed(refresh,100);
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);theme=HomeTheme.at(ZonedDateTime.now(Policy.ZONE),prefs.end(),prefs.checkout());
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(theme.background);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(8),dp(6),dp(12),dp(6));
        toolbar.addView(button("‹","출결 관리 닫기",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
        toolbar.addView(text("출결 관리",22,theme.ink,true),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("계산 기준","출결률 계산 기준",this::explain),new LinearLayout.LayoutParams(dp(92),dp(48)));root.addView(toolbar);
        scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(12),dp(16),dp(24));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(
            theme==HomeTheme.NIGHT?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        render();
        if(getIntent().getBooleanExtra("edit_late",false)){getIntent().removeExtra("edit_late");editLate();}
    }
    @Override protected void onResume(){super.onResume();prefs.raw.registerOnSharedPreferenceChangeListener(changes);render();}
    @Override protected void onPause(){prefs.raw.unregisterOnSharedPreferenceChangeListener(changes);handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private void render(){
        if(content==null)return;int position=scroll.getScrollY();content.removeAllViews();AttendanceMetrics.Summary s=prefs.attendanceSummary();
        LinearLayout course=card("수업 기간");
        line(course,"2026.08.31 – 2027.02.26",18,theme.ink,"course-period");
        line(course,"총 "+s.total()+"일 · 수업 요일과 휴강일 반영",13,theme.muted,"course-total");
        line(course,"공휴일 · 과정명세서의 휴강일 제외",12,theme.muted,null);
        if(prefs.lateAfter().isEmpty()){
            LinearLayout warning=card("지각 판정 기준이 필요해요");
            line(warning,"현재 집계에는 직접 선택한 지각만 포함됩니다. 입·퇴실 기록이 있어도 정상 출석으로 확정한 것이 아닙니다.",13,theme.accent,"attendance-classification-warning");
            warning.addView(button("지각 기준 설정  ›","지각 기준 설정",this::editLate),new LinearLayout.LayoutParams(-1,dp(48)));
        }
        LinearLayout current=card("현재 출결률 · 앱 기록 기준");
        line(current,AttendanceMetrics.percent(s.currentRate()),32,theme.accent,"attendance-current-rate");
        line(current,"확인된 "+s.known()+"일 기준 · 미확인/진행 중 "+s.missing()+"일",13,theme.muted,"attendance-missing");
        line(current,"결석 "+s.absent()+"일  ·  지각 "+s.late()+"회  ·  조퇴 "+s.early()+"회",14,theme.ink,"attendance-counts");
        Button calendar=button("달력에서 기록 확인 · 수정  ›","출결 달력 열기",()->startActivity(new Intent(this,AttendanceCalendarActivity.class).putExtra("theme",theme.name())));
        current.addView(calendar,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout finalRate=card("최종 예상 출결률 · 목표 80%");
        line(finalRate,AttendanceMetrics.percent(s.projectedRate()),28,s.projectedRate()<80?0xFFC15C43:theme.accent,"attendance-final-rate");
        line(finalRate,"남은 "+s.future()+"일과 미확인 "+s.missing()+"일을 모두 출석한 경우",12,theme.muted,"attendance-forecast-assumption");
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.round(s.projectedRate()*10));
        progress.setContentDescription("최종 예상 출결률 "+AttendanceMetrics.percent(s.projectedRate())+", 목표 80퍼센트");finalRate.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        LinearLayout budget=card("80%를 유지하려면");
        line(budget,s.total()==0?"수업 요일을 설정해 주세요":s.exceeded()>0?"결석 환산 "+s.exceeded()+"일 초과":"추가 결석 최대 "+s.remaining()+"일",24,theme.accent,"attendance-remaining");
        line(budget,"최소 출석 인정 "+s.required()+"일 / 전체 "+s.total()+"일",13,theme.muted,null);
        line(budget,"결석 환산 "+s.equivalent()+"일 / 허용 "+s.allowance()+"일",14,theme.ink,"attendance-equivalent");
        line(budget,"지각·조퇴 "+(s.late()+s.early())+"회 → 결석 "+s.converted()+"일 + 잔여 "+s.remainder()+"회",13,theme.muted,null);
        if(s.missing()>0)line(budget,"미확인 "+s.missing()+"일이 모두 출석이라는 가정입니다. 지난 기록을 채우면 정확해져요.",12,0xFFC15C43,null);
        LinearLayout threshold=card("지각 기준");
        Button late=button(prefs.lateAfter().isEmpty()?"기준 시각 설정  ›":prefs.lateAfter()+"부터 입실은 지각  ›","지각 기준 시각 설정",this::editLate);
        late.setTag("attendance-late-threshold");threshold.addView(late,new LinearLayout.LayoutParams(-1,dp(48)));
        line(threshold,prefs.lateAfter().isEmpty()?"아직 기준이 없습니다. 지각은 달력에서 직접 표시해 주세요.":"사이트 입실 시각으로 분류해요. 달력에서 직접 선택한 상태가 우선합니다.",12,theme.muted,null);
        if(!prefs.lateAfter().isEmpty())threshold.addView(button("자동 지각 분류 끄기","지각 기준 삭제",()->{prefs.raw.edit().putBoolean("late_disabled",true).apply();render();}));
        scroll.post(()->scroll.scrollTo(0,position));
    }
    private void editLate(){
        LocalTime time;try{time=LocalTime.parse(prefs.lateAfter());}catch(RuntimeException e){time=LocalTime.of(9,11);}
        TimePickerDialog picker=new TimePickerDialog(this,(view,h,m)->{prefs.raw.edit().putString("late_after",LocalTime.of(h,m).toString()).putBoolean("late_disabled",false).apply();render();},time.getHour(),time.getMinute(),true);
        picker.setTitle("이 시각부터 입실하면 지각");picker.show();
    }
    private void explain(){
        new AlertDialog.Builder(this).setTitle("출결률 계산 기준")
            .setMessage("현재 출결률 = (확인된 수업일 − 결석 환산일) ÷ 확인된 수업일입니다. 미확인 날짜는 결석으로 단정하지 않고 계산에서 제외합니다.\n\n지각과 조퇴는 합산 3회마다 결석 1일로 환산합니다. 같은 날 지각·조퇴가 함께 있으면 각각 1회로 셉니다. 잔여 1~2회는 다음 환산에 이월합니다.\n\n최종 예상은 미확인 날짜와 남은 수업을 모두 출석한다고 가정합니다. 실제 최종 출결률은 이후 기록에 따라 달라집니다. 결석 가능 횟수는 이 가정에서 80% 이상을 유지할 수 있는 추가 결석 일수입니다.\n\n달력의 직접 선택이 사이트 시각의 자동 분류보다 우선합니다. 수기 상태는 앱에만 저장됩니다. 지각 기준은 별도로 설정하며 조퇴는 예정 퇴실 시각 전 퇴실로 분류합니다.\n\n이 수치는 앱 기록과 지정한 규칙으로 계산한 일수 기준 예상입니다. 기관의 공식 출결 결과와는 차이가 있을 수 있습니다.")
            .setPositiveButton("확인",null).show();
    }
    private void line(LinearLayout parent,String value,int size,int color,String tag){TextView t=text(value,size,color,size>=18);if(tag!=null)t.setTag(tag);parent.addView(t);}
    private LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(shape(theme.surface,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);content.addView(c,p);line(c,title,13,theme.muted,null);return c;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(5));t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String label,String description,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(theme.accent);b.setContentDescription(description);b.setBackground(shape(theme.soft,12));b.setMinWidth(0);b.setPadding(dp(6),dp(4),dp(6),dp(4));b.setOnClickListener(v->action.run());return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(radius));return s;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
