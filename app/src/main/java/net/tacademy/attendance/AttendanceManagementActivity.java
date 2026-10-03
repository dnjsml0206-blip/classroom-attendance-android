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
    private final Runnable clockRefresh=new Runnable(){public void run(){render();handler.postDelayed(this,30000);}};
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(key==null||key.startsWith("record:")||key.startsWith("manual_attendance:")||Set.of("days","excluded","late_after","late_disabled","checkout","vacation_rule").contains(key)){
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
    @Override protected void onResume(){super.onResume();prefs.raw.registerOnSharedPreferenceChangeListener(changes);render();handler.postDelayed(clockRefresh,30000);}
    @Override protected void onPause(){prefs.raw.unregisterOnSharedPreferenceChangeListener(changes);handler.removeCallbacks(refresh);handler.removeCallbacks(clockRefresh);super.onPause();}
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
        LinearLayout current=card("현재 출결률");
        line(current,s.currentDisplay(),32,theme.accent,"attendance-current-rate");
        line(current,"종료된 수업 "+s.elapsed()+"일 기준 · 미기록 "+s.missing()+"일은 출석 처리",13,theme.muted,"attendance-missing");
        line(current,"오늘은 수업 종료 후 포함 · "+CourseSchedule.classEnd(LocalDate.now(Policy.ZONE))+" 종료",12,theme.muted,"attendance-cutoff");
        line(current,"결석 "+s.absent()+"일  ·  지각 "+s.late()+"회  ·  조퇴 "+s.early()+"회",14,theme.ink,"attendance-counts");
        Button calendar=button("달력에서 기록 확인 · 수정  ›","출결 달력 열기",()->startActivity(new Intent(this,AttendanceCalendarActivity.class).putExtra("theme",theme.name())));
        current.addView(calendar,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout finalRate=card("최종 예상 출결률 · 목표 80%");
        line(finalRate,s.projectedDisplay(),28,s.projectedRate()<80?0xFFC15C43:theme.accent,"attendance-final-rate");
        line(finalRate,"남은 수업 "+s.future()+"일을 모두 출석한 경우",12,theme.muted,"attendance-forecast-assumption");
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress((int)Math.round(s.projectedRate()*10));
        progress.setContentDescription("최종 예상 출결률 "+s.projectedDisplay()+", 목표 80퍼센트");finalRate.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        LinearLayout budget=card("80%를 유지하려면");
        line(budget,s.total()==0?"수업 요일을 설정해 주세요":s.exceeded()>0?"결석 환산 "+s.exceededDisplay()+"일 초과":"결석 "+s.absenceBudget()+"일 또는\n지각·조퇴 "+s.eventBudget()+"회 가능",22,theme.accent,"attendance-remaining");
        line(budget,"최소 출석 인정 "+s.requiredDisplay()+"일 / 전체 "+s.total()+"일",13,theme.muted,null);
        line(budget,"결석 환산 "+s.equivalentDisplay()+"일 / 허용 "+s.allowanceDisplay()+"일",14,theme.ink,"attendance-equivalent");
        line(budget,"지각·조퇴 "+(s.late()+s.early())+"회 → 결석 "+s.convertedDisplay()+"일 · 매회 ⅓일 반영",13,theme.muted,null);
        line(budget,"각각의 최대치예요. 두 허용량을 더해서 사용할 수는 없어요.",12,theme.muted,null);
        LinearLayout vacation=card("휴가 관리");VacationLedger.Balance v=prefs.vacationBalance();
        line(vacation,"잔여 "+v.available()+"일",26,theme.accent,"vacation-remaining");
        line(vacation,"부여 "+v.earned()+"일 · 사용 "+v.used()+"일 · 예약 "+v.reserved()+"일",14,theme.ink,"vacation-counts");
        line(vacation,"미사용 휴가는 누적 · 휴가일은 출석 인정 · 잔여는 예약 제외",12,theme.muted,null);
        Button vacationRule=button("월 1일 부여 · "+prefs.vacationRule().label+"  ›","휴가 지급 기준 선택",this::editVacationRule);vacationRule.setTag("vacation-rule");LinearLayout.LayoutParams ruleSize=new LinearLayout.LayoutParams(-1,-2);ruleSize.bottomMargin=dp(8);vacation.addView(vacationRule,ruleSize);
        Button vacationCalendar=button("달력에서 휴가 등록  ›","휴가 달력 열기",()->startActivity(new Intent(this,AttendanceCalendarActivity.class)));vacationCalendar.setTag("vacation-calendar");vacation.addView(vacationCalendar,new LinearLayout.LayoutParams(-1,dp(48)));
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
    private void editVacationRule(){
        VacationLedger.GrantRule[] rules=VacationLedger.GrantRule.values();String[] labels={"수업월 시작일 · 8/31, 9/30, 10/31…","한 달 수료 후 · 9/30, 10/31…","매달 1일 · 9/1, 10/1…"};
        new AlertDialog.Builder(this).setTitle("매월 휴가 1일 부여 기준").setSingleChoiceItems(labels,prefs.vacationRule().ordinal(),(dialog,index)->{
            if(!VacationLedger.valid(prefs.vacations(),rules[index])){Toast.makeText(this,"이미 등록한 휴가가 새 지급 기준을 초과해요. 달력에서 먼저 확인해 주세요.",Toast.LENGTH_LONG).show();return;}
            prefs.raw.edit().putString("vacation_rule",rules[index].name()).apply();dialog.dismiss();render();
        }).setNegativeButton("닫기",null).show();
    }
    private void explain(){
        new AlertDialog.Builder(this).setTitle("출결률 계산 기준")
            .setMessage("현재 출결률 = (종료된 수업일 − 결석 환산일) ÷ 종료된 수업일입니다. 오늘 수업은 실제 수업 종료 시각부터 포함합니다. 일반 수업 18:00, 단축수업은 시간표의 16:00 또는 17:00 기준입니다. 자동 퇴실 시각과는 별개입니다.\n\n사용자 요청에 따라 기록이 없는 지난 수업일은 출석으로 계산합니다. 입·퇴실 시각을 만들지는 않습니다. 달력에서 수정한 상태가 우선합니다.\n\n지각과 조퇴는 각각 1회당 결석 ⅓일로 즉시 반영합니다. 1회는 ⅓일, 2회는 ⅔일, 3회는 1일입니다. 같은 날 둘 다 선택하면 ⅔일을 차감합니다. 계산 중에는 소수점 이하를 버리지 않고 출결률 표시만 소수점 첫째 자리로 반올림합니다.\n\n80% 기준에서 이미 사용한 결석 환산일을 빼고 추가 허용량을 계산합니다. 결석 일수와 지각·조퇴 횟수는 각각의 최대치이며 동시에 더해서 사용할 수 없습니다. 최종 예상은 남은 수업을 모두 출석한 경우입니다.\n\n휴가는 매월 1일씩 누적하고 출석으로 인정합니다. 지급일 기준은 휴가 관리에서 바꿀 수 있습니다. 미래 예약도 잔여에서 제외하며, 휴가 취소 시 반환합니다. 휴가 등록은 앱 기록이며 기관 신청을 대신하지 않습니다.")
            .setPositiveButton("확인",null).show();
    }
    private void line(LinearLayout parent,String value,int size,int color,String tag){TextView t=text(value,size,color,size>=18);if(tag!=null)t.setTag(tag);parent.addView(t);}
    private LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(shape(theme.surface,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);content.addView(c,p);line(c,title,13,theme.muted,null);return c;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(5));t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String label,String description,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(theme.accent);b.setContentDescription(description);b.setBackground(shape(theme.soft,12));b.setMinWidth(0);b.setPadding(dp(6),dp(4),dp(6),dp(4));b.setOnClickListener(v->action.run());return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(radius));return s;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
