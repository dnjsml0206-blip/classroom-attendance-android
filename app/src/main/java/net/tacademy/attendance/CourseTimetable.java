package net.tacademy.attendance;
import java.time.*;
import java.util.*;
/** Transcribed from the supplied one-page course specification; no inferred start/end times. */
final class CourseTimetable {
    enum Category { STUDY("학습"), PROJECT("프로젝트"), SPECIAL("특강 · 발표"); final String label;Category(String label){this.label=label;} }
    record Lesson(LocalDate date,String title,String shortTitle,int hours,Category category){}
    record Module(String title,Category category,List<Lesson> lessons){
        int hours(){return lessons.stream().mapToInt(Lesson::hours).sum();}
        LocalDate first(){return lessons.get(0).date();}
        LocalDate last(){return lessons.get(lessons.size()-1).date();}
    }
    static final NavigableMap<LocalDate,Lesson> DAYS=build();
    static Lesson on(LocalDate date){return DAYS.get(date);}
    static LocalDate clamp(LocalDate d){return d.isBefore(CourseCalendar.START)?CourseCalendar.START:d.isAfter(CourseCalendar.END)?CourseCalendar.END:d;}
    static List<Lesson> month(YearMonth month){return DAYS.values().stream().filter(d->YearMonth.from(d.date()).equals(month)).toList();}
    static int totalHours(){return DAYS.values().stream().mapToInt(Lesson::hours).sum();}
    static List<Module> modules(){
        Map<String,List<Lesson>> groups=new LinkedHashMap<>();for(Lesson lesson:DAYS.values())groups.computeIfAbsent(lesson.title(),k->new ArrayList<>()).add(lesson);
        return groups.entrySet().stream().map(e->new Module(e.getKey(),e.getValue().get(0).category(),List.copyOf(e.getValue()))).toList();
    }
    private static NavigableMap<LocalDate,Lesson> build(){TreeMap<LocalDate,Lesson> days=new TreeMap<>();
        add(days,"2026-08-31","2026-08-31","OT · 특강","OT",6,Category.SPECIAL);
        add(days,"2026-09-01","2026-09-01","AI+DA 입문 · 취업 특강","AI입문",7,Category.SPECIAL);
        add(days,"2026-09-02","2026-09-09","파이썬","파이썬",8,Category.STUDY);
        add(days,"2026-09-10","2026-09-11","SQL","SQL",8,Category.STUDY);
        add(days,"2026-09-14","2026-09-17","모델링을 위한 수학","수학",8,Category.STUDY);
        add(days,"2026-09-18","2026-09-18","데이터 서비스 기획","기획",6,Category.SPECIAL);
        add(days,"2026-09-21","2026-09-22","모델링을 위한 수학","수학",8,Category.STUDY);
        add(days,"2026-09-28","2026-09-30","데이터 수집 · EDA","수집/EDA",8,Category.STUDY);
        add(days,"2026-10-01","2026-10-02","머신러닝","머신러닝",8,Category.STUDY);
        add(days,"2026-10-06","2026-10-07","태블로 시각화","태블로",6,Category.SPECIAL);
        add(days,"2026-10-08","2026-10-12","머신러닝","머신러닝",8,Category.STUDY);
        add(days,"2026-10-13","2026-10-13","EDA 발표","EDA발표",8,Category.SPECIAL);
        add(days,"2026-10-14","2026-10-15","클라우드 · 리눅스","클라우드",8,Category.STUDY);
        add(days,"2026-10-16","2026-10-22","딥러닝","딥러닝",8,Category.STUDY);
        add(days,"2026-10-23","2026-10-23","세미나 · AWS/GIT","AWS/GIT",8,Category.SPECIAL);
        add(days,"2026-10-26","2026-10-29","ML/DL 프로젝트","ML/DL",7,Category.PROJECT);
        add(days,"2026-10-30","2026-11-05","LLM","LLM",8,Category.STUDY);
        add(days,"2026-11-06","2026-11-11","컴퓨터 비전","컴퓨터비전",8,Category.STUDY);
        add(days,"2026-11-12","2026-11-12","세미나","세미나",8,Category.SPECIAL);
        add(days,"2026-11-13","2026-11-17","멀티모달","멀티모달",8,Category.STUDY);
        add(days,"2026-11-18","2026-11-20","이미지 생성","이미지생성",8,Category.STUDY);
        add(days,"2026-11-23","2026-11-25","AI Agent 구현","AI Agent",8,Category.STUDY);
        add(days,"2026-11-26","2026-12-04","AI 모델링 프로젝트","AI모델링",8,Category.PROJECT);
        add(days,"2026-12-07","2026-12-07","알고리즘 · 코딩 테스트","코딩테스트",8,Category.SPECIAL);
        add(days,"2026-12-08","2026-12-08","알고리즘 · 코딩 테스트","코딩테스트",7,Category.SPECIAL);
        add(days,"2026-12-09","2027-02-17","실전 프로젝트","프로젝트",8,Category.PROJECT);
        add(days,"2027-02-18","2027-02-18","최종 발표","최종발표",8,Category.SPECIAL);
        add(days,"2027-02-19","2027-02-23","실전 프로젝트","프로젝트",6,Category.PROJECT);
        add(days,"2027-02-24","2027-02-25","취업 컨설팅","컨설팅",8,Category.SPECIAL);
        add(days,"2027-02-26","2027-02-26","현직자 멘토링","멘토링",8,Category.SPECIAL);
        return Collections.unmodifiableNavigableMap(days);
    }
    private static void add(Map<LocalDate,Lesson> days,String first,String last,String title,String shortTitle,int hours,Category category){
        for(LocalDate d=LocalDate.parse(first);!d.isAfter(LocalDate.parse(last));d=d.plusDays(1))if(CourseCalendar.classDay(d,Set.of(1,2,3,4,5),Set.of())){
            if(days.put(d,new Lesson(d,title,shortTitle,hours,category))!=null)throw new IllegalStateException("Duplicate lesson: "+d);
        }
    }
}
