package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;

/** Temporary, user-operated lab. Never silently enables trust agents or changes screen security. */
public final class LockLabActivity extends Activity {
    private static final int INK=0xFF183631, MUTED=0xFF687D77, ACCENT=0xFF007C78;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView current, progress, log, preparation, procedure, expected, guideNote, readiness;
    private Button reserve, immediate, checkout;
    private String methodExpected="";
    private SetupStatusView setupStatus;
    private Spinner methods, delay;
    private RadioGroup purpose;
    private RadioButton previewPurpose,attendancePurpose;
    private boolean actualSelected(){return purpose!=null&&purpose.getCheckedRadioButtonId()==attendancePurpose.getId();}
    private boolean purposeSelected(){return purpose!=null&&purpose.getCheckedRadioButtonId()!=-1;}
    private final Runnable refresh=new Runnable(){@Override public void run(){update();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!CreatorTools.testsVisible(this)){finish();return;}
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF4F7F6);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(22),dp(22),dp(30));scroll.addView(content);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFFF4F7F6);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });setContentView(root);
        content.addView(text("임시 메뉴 · 기기에서 직접 확인",12,ACCENT,true));space(content,8);
        content.addView(text("잠금 상태 테스트",28,INK,true));space(content,8);
        content.addView(text("설정 확인 → 조회 또는 실제 출결 선택 → 예약 → 결과 기록\n화면 켜짐·잠금 해제 상태에서도 시험할 수 있습니다.",14,MUTED,false));space(content,18);

        LinearLayout setup=card("실행 준비");readiness=text("",14,INK,true);setup.addView(readiness);
        setupStatus=new SetupStatusView(this,true);setup.addView(setupStatus);
        setup.addView(text("Extend Unlock 활성 여부는 앱에서 판별할 수 없습니다. 휴대폰 설정과 실제 시험으로 확인하세요.",12,MUTED,false));
        LinearLayout select=card("1. 테스트 방법");
        methods=new Spinner(this);String[] labels=new String[LockTestPolicy.Mode.values().length];
        for(int i=0;i<labels.length;i++)labels[i]=LockTestPolicy.Mode.values()[i].label;
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);methods.setAdapter(adapter);methods.setSelection(LockTestPolicy.Mode.EXTEND.ordinal());select.addView(methods,new LinearLayout.LayoutParams(-1,dp(52)));
        preparation=guideStep(select,"준비");
        procedure=guideStep(select,"실행");
        expected=guideStep(select,"성공 확인");
        guideNote=text("",12,MUTED,false);space(select,12);select.addView(guideNote);
        add(select,"처음 한 번 확인할 설정",false,this::firstHelp);
        methods.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){@Override public void onNothingSelected(AdapterView<?> p){} @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){describe();}});

        LinearLayout run=card("2. 실행");
        delay=new Spinner(this);delay.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"15초 후 실행","30초 후 실행","60초 후 실행"}));run.addView(delay,new LinearLayout.LayoutParams(-1,dp(48)));
        run.addView(text("먼저 실행 목적을 선택하세요",16,INK,true));
        purpose=new RadioGroup(this);purpose.setTag("trial-purpose");
        previewPurpose=new RadioButton(this);previewPurpose.setId(View.generateViewId());previewPurpose.setText(R.string.trial_preview_purpose);previewPurpose.setTextColor(INK);previewPurpose.setMinHeight(dp(54));purpose.addView(previewPurpose);
        attendancePurpose=new RadioButton(this);attendancePurpose.setId(View.generateViewId());attendancePurpose.setText(R.string.trial_actual_purpose);attendancePurpose.setTextColor(INK);attendancePurpose.setMinHeight(dp(54));purpose.addView(attendancePurpose);run.addView(purpose);
        if(state!=null&&state.containsKey("trial_actual"))purpose.check(state.getBoolean("trial_actual")?attendancePurpose.getId():previewPurpose.getId());
        run.addView(text("위의 기본 설정 완료와 별개인 선택입니다. ‘Chrome 열기만 시험’은 입실·퇴실 버튼을 누르지 않습니다. 실제 시험은 출석 예약·시간·Wi-Fi·자동 클릭 조건을 그대로 확인합니다.",12,MUTED,false));
        reserve=add(run,"조회 테스트 예약",true,()->startTrial(new int[]{15,30,60}[delay.getSelectedItemPosition()]));
        immediate=add(run,"지금 조회 테스트",false,()->startTrial(0));
        checkout=add(run,"오늘 퇴실 테스트 예약",true,this::checkoutTrial);
        run.addView(text("오늘 퇴실 예약은 설정한 퇴실 시각에 한 번 실행합니다. 대기 중에는 기존 출석을 보류하지 않으며, 실행 시각부터 최대 2분간 시험합니다. Extend Unlock 시험은 먼저 PIN·지문으로 한 번 해제하고 신뢰 조건을 유지한 뒤 화면을 끄세요. 예약 시각까지 앱을 강제 종료하지 마세요.",12,MUTED,false));
        purpose.setOnCheckedChangeListener((group,id)->update());add(run,"예약·테스트 취소",false,()->{LockLab.cancel(this);update();});
        progress=text("",13,ACCENT,true);space(run,12);run.addView(progress);

        LinearLayout phone=card("3. 휴대폰 상태와 신뢰 설정");phone.addView(text(LockLab.device(),14,INK,true));current=text("",13,MUTED,false);space(phone,8);phone.addView(current);
        phone.addView(text("Extend Unlock은 예전 Smart Lock입니다. 휴대폰에서 신뢰하는 기기·장소·신체 활동 감지를 직접 설정한 뒤 각각 시험하세요. 앱에서는 활성화된 신뢰 방식을 판별할 수 없습니다.",13,MUTED,false));
        add(phone,"보안 · Extend Unlock 설정",false,()->PhoneSettings.open(this,PhoneSettings.Target.EXTEND));
        add(phone,"신뢰 방식별 준비 방법",false,this::trustHelp);
        add(phone,"Chrome 접근성 설정",false,()->PhoneSettings.open(this,PhoneSettings.Target.ACCESSIBILITY));
        add(phone,"알람 및 리마인더 설정",false,()->PhoneSettings.open(this,PhoneSettings.Target.ALARMS));
        add(phone,"위치 항상 허용 설정",false,()->PhoneSettings.open(this,PhoneSettings.Target.LOCATION_PERMISSION));
        add(phone,"테스트 알림 설정",false,()->PhoneSettings.open(this,PhoneSettings.Target.TEST_NOTIFICATIONS));
        add(phone,"서버 인증 · API 준비 상태",false,()->new AlertDialog.Builder(this).setTitle("서버 직접 처리 · 미연동")
            .setMessage("Chrome 인증은 앱의 서버 인증으로 공유되지 않습니다. 현재 연결 진단은 휴대폰의 Wi-Fi 조건만 확인하며 서버에 접속하지 않습니다.\n\n앱 전용 인증 방법과 출결 API가 확보되어야 잠금 상태의 실제 출결 처리를 연결할 수 있습니다. 기기 조건 확인을 인증·출결 성공으로 표시하지 않습니다.")
            .setPositiveButton("확인",null).show());

        LinearLayout results=card("4. 테스트 기록");log=text("",12,INK,false);log.setTextIsSelectable(true);results.addView(log);
        add(results,"성공 · 부분 성공 · 실패 기록",true,this::verdict);
        results.addView(text("로그 업로드는 설정 상단의 ‘진단 · 제작자용’에서 할 수 있습니다.",12,MUTED,false));
        add(results,"결과 전체 복사",false,()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("출석 메이트 잠금 테스트",LockLab.allReports(this)));Toast.makeText(this,"이전 결과를 포함해 복사했습니다",Toast.LENGTH_SHORT).show();});
        add(results,"이전 테스트 함께 보기",false,()->{TextView reports=text(LockLab.allReports(this),13,INK,false);reports.setTextIsSelectable(true);reports.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView all=new ScrollView(this);all.addView(reports);new AlertDialog.Builder(this).setTitle("최근 테스트 · 최대 11회").setView(all).setPositiveButton("닫기",null).show();});
        add(content,"설정으로 돌아가기",false,this::finish);describe();update();
    }
    private LockTestPolicy.Mode selected(){return LockTestPolicy.Mode.values()[methods.getSelectedItemPosition()];}
    private void describe(){
        if(preparation==null)return;
        int guide=switch(selected()){
            case WAKE -> R.array.lock_guide_wake;
            case EXTEND -> R.array.lock_guide_extend;
            case DEVICE -> R.array.lock_guide_device;
            case PLACE -> R.array.lock_guide_place;
            case BODY -> R.array.lock_guide_body;
            case AUTH -> R.array.lock_guide_auth;
            case NETWORK -> R.array.lock_guide_network;
        };
        String[] steps=getResources().getStringArray(guide);
        preparation.setText(steps[0]);procedure.setText(steps[1]);methodExpected=steps[2];expected.setText(methodExpected);guideNote.setText(steps[3]);
        if(purpose!=null){boolean available=selected()!=LockTestPolicy.Mode.NETWORK;attendancePurpose.setEnabled(available);
            previewPurpose.setText(available?"Chrome 열기만 시험 · 출결 안 함":"기기 연결만 진단 · 출결 안 함");
            if(!available)purpose.check(previewPurpose.getId());update();}
    }
    private void startTrial(int seconds){
        if(!purposeSelected()){Toast.makeText(this,"실행 목적을 먼저 선택해 주세요",Toast.LENGTH_SHORT).show();return;}
        try{
            String token=LockLab.prepare(this,selected(),actualSelected(),seconds);
            if(seconds==0){LockLab.fire(this,token,"앱에서 즉시 실행");if(selected()==LockTestPolicy.Mode.NETWORK)new Thread(()->LockTestReceiver.diagnose(getApplicationContext(),token),"lock-network-diagnostic").start();}
            else Toast.makeText(this,seconds+"초 안에 전원 버튼으로 화면을 꺼 주세요",Toast.LENGTH_LONG).show();
            update();
        }catch(IllegalStateException e){showProblems(e.getMessage());}
    }
    private void checkoutTrial(){
        if(!actualSelected()){showProblems("‘실제 입실·퇴실 시험’을 선택해 주세요. Chrome 열기만 시험하면 퇴실 버튼을 누르지 않습니다.");return;}
        try{LockLab.prepareCheckout(this,selected());update();Toast.makeText(this,"오늘 "+Policy.checkoutStartTime(new Prefs(this).checkout())+" 퇴실 테스트를 예약했습니다",Toast.LENGTH_LONG).show();}
        catch(IllegalStateException e){showProblems(e.getMessage());}
    }
    private void showProblems(String message){
        java.util.List<PhoneSettings.Target> targets=new java.util.ArrayList<>();java.util.List<String> labels=new java.util.ArrayList<>();
        for(String issue:message.split("\n")){PhoneSettings.Target target=PhoneSettings.forProblem(issue);if(target!=null&&!targets.contains(target)){targets.add(target);labels.add(issue+"  ›");}}
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("설정이 필요합니다").setPositiveButton("닫기",null);
        if(targets.isEmpty())dialog.setMessage(message);
        else dialog.setItems(labels.toArray(new String[0]),(d,index)->PhoneSettings.open(this,targets.get(index)));
        dialog.show();
    }
    private TextView guideStep(LinearLayout parent,String title){
        space(parent,12);parent.addView(text(title,12,ACCENT,true));space(parent,3);
        TextView value=text("",14,INK,false);parent.addView(value);return value;
    }
    private void firstHelp(){new AlertDialog.Builder(this).setTitle("처음 한 번 확인할 설정")
        .setMessage("1. 아래 ‘휴대폰 상태와 신뢰 설정’에서 Chrome 접근성·위치 항상 허용·알람 및 리마인더·테스트 알림을 허용하세요.\n\n2. Chrome에서 평소 출결 주소가 열리는지 확인하세요. 연결 진단은 휴대폰 설정만 확인하며 서버에는 접속하지 않습니다.\n\n3. 조회만 확인할 때는 ‘Chrome 열기만 시험’을 선택하세요. 입실·퇴실까지 시험하려면 출석 예약과 Chrome 자동 클릭을 켜고 ‘실제 입실·퇴실 시험’을 선택하세요. 예약한 뒤 전원 버튼으로 화면을 끄고, 자동 동작을 확인할 때까지 알림이나 화면을 누르지 마세요. 시스템 인증 방식은 인증 요청이 뜨면 직접 인증하세요.\n\n4. 예약 시각이 지나도 반응이 없으면 최대 2분 뒤 앱으로 돌아와 기록을 확인하세요. 결과 입력에서 직접 화면 켬·잠금 해제·알림 누름·무조작 중 해당 항목을 기록하세요. 예약에서 진입했다는 표시는 자동 잠금 해제 성공을 뜻하지 않습니다.")
        .setPositiveButton("확인",null).show();}
    private void trustHelp(){new AlertDialog.Builder(this).setTitle("Extend Unlock · 신뢰 방식")
        .setMessage("먼저 PIN 등으로 한 번 잠금을 해제한 후 시험하세요.\n\n• 신뢰 기기: 등록한 Bluetooth 기기 연결\n• 신뢰 장소: 휴대폰에 등록한 장소에 머무르기\n• 신체 활동 감지: 휴대 중과 책상 위 상태 비교\n\n지원 항목과 설정 경로는 기기마다 다릅니다. 신뢰 유지 시간은 제한되어 있으며 잠금 모드·재부팅·재인증 요구 뒤에는 다시 인증이 필요할 수 있습니다.\n\n앱은 보안 설정을 자동으로 켜거나 변경하지 않습니다.")
        .setPositiveButton("확인",null).setNeutralButton("Google 안내",(d,w)->open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://support.google.com/android/answer/9075927?hl=ko")))).show();}
    private void verdict(){
        if(LockLab.id(this).isEmpty()){Toast.makeText(this,"먼저 테스트를 실행해 주세요",Toast.LENGTH_SHORT).show();return;}
        String token=LockLab.id(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(8),dp(22),dp(8));
        Spinner result=new Spinner(this);String[] values={"성공","부분 성공","실패","미확인"};result.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        String previous=LockLab.prefs(this).getString("verdict","미확인");for(int i=0;i<values.length;i++)if(values[i].equals(previous))result.setSelection(i);form.addView(result);
        form.addView(text("테스트 중 직접 조작했나요?",14,INK,true));
        String[] interactions={"UNKNOWN","UNTOUCHED","MANUAL_WAKE","MANUAL_UNLOCK","NOTIFICATION","SYSTEM_AUTH"};
        String[] labels=new String[interactions.length];for(int i=0;i<labels.length;i++)labels[i]=LockLab.interactionLabel(interactions[i]);
        Spinner interaction=new Spinner(this);interaction.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        String previousInteraction=LockLab.prefs(this).getString("user_interaction","UNKNOWN");for(int i=0;i<interactions.length;i++)if(interactions[i].equals(previousInteraction))interaction.setSelection(i);form.addView(interaction);
        EditText note=new EditText(this);note.setHint("예: 화면은 켜졌지만 PIN 입력이 필요했음");note.setText(LockLab.prefs(this).getString("note",""));note.setMinLines(2);note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});form.addView(note);
        new AlertDialog.Builder(this).setTitle("직접 확인한 결과").setView(form).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{
            if(!token.equals(LockLab.id(this))){Toast.makeText(this,"새 테스트가 시작되어 저장하지 않았습니다",Toast.LENGTH_SHORT).show();return;}
            LockLab.verdict(this,values[result.getSelectedItemPosition()],note.getText().toString(),interactions[interaction.getSelectedItemPosition()]);update();
        }).show();
    }
    private void update(){if(progress==null)return;LockLab.expire(this);
        readiness.setText(!purposeSelected()?"실행 목적 미선택 · 아래에서 선택하세요":actualSelected()?"실제 출결 시험 · 조건을 만족하면 입실·퇴실 버튼을 누릅니다":"조회 전용 · 입실·퇴실 버튼을 누르지 않습니다");setupStatus.refresh();
        expected.setText(actualSelected()?getString(R.string.trial_real_success):methodExpected);
        reserve.setEnabled(purposeSelected());immediate.setEnabled(purposeSelected());checkout.setEnabled(actualSelected());
        reserve.setText(!purposeSelected()?"실행 목적 선택 후 예약":actualSelected()?"실제 입실·퇴실 시험 예약":selected()==LockTestPolicy.Mode.NETWORK?"기기 연결 진단 예약":"Chrome 열기만 예약 · 출결 안 함");
        immediate.setText(!purposeSelected()?"실행 목적 선택 후 시험":actualSelected()?"지금 실제 입실·퇴실 시험":selected()==LockTestPolicy.Mode.NETWORK?"지금 기기 연결 진단":"지금 Chrome 열기만 · 출결 안 함");
        checkout.setText(getString(R.string.checkout_trial_button,Policy.checkoutStartTime(new Prefs(this).checkout()).toString()));
        current.setText(getString(R.string.lock_lab_state,LockLab.snapshot(this),ChromeAttendanceService.available()?"연결됨":"연결 필요",AlarmScheduler.allowed(this)?"허용":"미허용"));
        long seconds=Math.max(0,(LockLab.prefs(this).getLong("due",0)-System.currentTimeMillis()+999)/1000);
        String stage=LockLab.prefs(this).getString("stage","아직 실행하지 않았습니다.");
        progress.setText(LockLab.live(this,LockLab.id(this))&&seconds>0?getString(R.string.lock_lab_remaining,stage,seconds):stage);
        if(LockLab.live(this,LockLab.id(this))&&LockLab.prefs(this).getBoolean("checkout_trial",false)&&seconds>0)progress.setText(getString(R.string.checkout_trial_waiting,java.time.Instant.ofEpochMilli(LockLab.prefs(this).getLong("due",0)).atZone(Policy.ZONE).toLocalTime().toString()));
        String report=LockLab.report(this);if(!log.getText().toString().equals(report))log.setText(report);
    }
    private void open(Intent intent){try{startActivity(intent);}catch(RuntimeException e){Toast.makeText(this,"휴대폰 설정에서 해당 메뉴를 직접 열어 주세요",Toast.LENGTH_LONG).show();}}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(4),1);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(20));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);content.addView(c,lp);c.addView(text(title,14,ACCENT,true));space(c,10);return c;}
    private Button add(LinearLayout p,String label,boolean primary,Runnable action){space(p,10);Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?0xFFFFFFFF:ACCENT);b.setStateListAnimator(null);b.setMinHeight(dp(48));GradientDrawable bg=new GradientDrawable();bg.setColor(primary?ACCENT:0xFFE5F2EF);bg.setCornerRadius(dp(12));b.setBackground(bg);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->action.run());p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(purposeSelected())state.putBoolean("trial_actual",actualSelected());}
    @Override protected void onResume(){super.onResume();ChromeAttendanceService.cancelSettingsMenu();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
