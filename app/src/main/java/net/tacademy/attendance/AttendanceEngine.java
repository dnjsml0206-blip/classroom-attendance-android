package net.tacademy.attendance;

import android.content.Context;
import android.os.SystemClock;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@android.annotation.SuppressLint("ApplySharedPref") // Pending writes must be durable before a network mutation; all run on the worker thread.
public final class AttendanceEngine {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile long nextRead = 0;
    private static int failures = 0;
    private AttendanceEngine() {}

    public static void request(Context context, boolean force, Runnable finished) {
        Context app = context.getApplicationContext();
        if (!BUSY.compareAndSet(false, true)) {
            if (force) EXECUTOR.execute(() -> {
                BUSY.set(true);
                try { run(app, true); }
                finally { BUSY.set(false); if (finished != null) finished.run(); }
            });
            else if (finished != null) finished.run();
            return;
        }
        EXECUTOR.execute(() -> {
            try { run(app, force); }
            catch (Exception unexpected) {
                new Prefs(app).status("처리 확인 필요", "앱을 열어 권한과 연결 상태를 확인해 주세요");
            } finally {
                BUSY.set(false);
                if (finished != null) finished.run();
            }
        });
    }
    private static void run(Context context, boolean force) {
        Prefs prefs = new Prefs(context);
        if (!prefs.enabled()) return;
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        if (!Policy.isClassDay(now, prefs.days(), prefs.excluded())) {
            prefs.status("수업일이 아닙니다", "설정한 다음 수업일에 감시합니다"); return;
        }
        NetworkAccess.State wifi = NetworkAccess.inspect(context);
        if (!wifi.allowed()) { prefs.status("Wi-Fi 연결 대기", wifi.reason()); return; }
        if (!new AuthVault(context).ready()) {
            prefs.status("사이트 인증이 필요합니다", "연결 탭에서 출석 사이트를 인증해 주세요"); return;
        }
        boolean due = Policy.checkoutDue(now);
        boolean entry = Policy.inWindow(now, prefs.start(), prefs.end());
        if (!force && !due && !entry) return;
        if (due && nextRead - SystemClock.elapsedRealtime() > 60000) nextRead = 0;
        if (!force && SystemClock.elapsedRealtime() < nextRead) return;
        nextRead = SystemClock.elapsedRealtime() + 60000;
        try {
            SiteClient client = new SiteClient(context, wifi.network(), new AuthVault(context).read());
            SiteClient.Snapshot snapshot = client.inspect();
            Policy.Record record = snapshot.record();
            failures = 0;
            String today = snapshot.date().toString();
            storeRecord(prefs, record, today);
            String pending = prefs.raw.getString("pending", "");
            String inKey = today + ":" + client.scope() + ":in";
            String outKey = today + ":" + client.scope() + ":out";
            if ((pending.equals(inKey) && record.checkedIn()) || (pending.equals(outKey) && record.checkedOut()))
                prefs.raw.edit().remove("pending").commit();
            pending = prefs.raw.getString("pending", "");
            if (pending.equals(inKey) || pending.equals(outKey)) {
                prefs.status("처리 결과 확인 필요", "이전 요청의 반영 여부가 불명확합니다. 연결 탭에서 기록을 확인해 주세요"); return;
            }
            if (!prefs.enabled()) return;
            ZonedDateTime afterRead = ZonedDateTime.now(Policy.ZONE);
            if (!afterRead.toLocalDate().toString().equals(today) ||
                !Policy.isClassDay(afterRead, prefs.days(), prefs.excluded())) return;
            due = Policy.checkoutDue(afterRead);
            entry = Policy.inWindow(afterRead, prefs.start(), prefs.end());

            if (record.checkedOut()) {
                prefs.status("오늘 출석이 완료되었습니다", "입실 " + record.in() + " · 퇴실 " + record.out());
                nextRead = SystemClock.elapsedRealtime() + 900000; return;
            }
            if (!prefs.auto()) {
                prefs.status("조회 모드로 감시 중", record.checkedIn() ? "입실 " + record.in() + " · 자동 처리는 꺼져 있습니다" : "입실 가능 · 자동 처리는 꺼져 있습니다");
                return;
            }
            boolean checkout = due && record.checkedIn() && !record.checkedOut();
            boolean checkin = entry && !record.checkedIn();
            if (!checkout && !checkin) {
                prefs.status(record.checkedIn() ? "입실 완료 · 퇴실 대기" : "입실 시간 대기",
                    record.checkedIn() ? "입실 " + record.in() + " · 17:50 퇴실 예정" : prefs.start() + "–" + prefs.end() + "에 입실을 확인합니다");
                if (record.checkedIn() && !due) nextRead = SystemClock.elapsedRealtime() + 900000;
                if (due && !record.checkedIn()) Notices.alert(context, "퇴실을 처리하지 않았습니다", "오늘 입실 기록이 없습니다");
                return;
            }
            NetworkAccess.requireSame(context, wifi.network());
            String requestKey = checkout ? outKey : inKey;
            // Durable marker precedes the network write. Never blindly resend an ambiguous GET.
            if (!prefs.raw.edit().putString("pending", requestKey).commit())
                throw new SiteClient.Failure("STORAGE", "처리 상태를 저장할 수 없습니다");
            if (!prefs.enabled() || !prefs.auto()) { prefs.raw.edit().remove("pending").commit(); return; }
            prefs.status(checkout ? "퇴실 처리 확인 중" : "입실 처리 확인 중", "사이트에 반영된 기록을 확인합니다");
            if (!client.submit(checkout)) {
                prefs.raw.edit().remove("pending").commit();
                throw new SiteClient.Failure("IP", "사이트에서 출석 요청을 허용하지 않았습니다");
            }
            Policy.Record after = client.record();
            storeRecord(prefs, after, today);
            if (checkout ? after.checkedOut() : after.checkedIn()) {
                prefs.raw.edit().remove("pending").commit();
                String title = checkout ? "퇴실 완료" : "입실 완료";
                String time = checkout ? after.out() : after.in();
                prefs.status(title + " " + time, "사이트 기록에서 확인했습니다");
                prefs.log(title + " " + time);
                Notices.alert(context, title, "사이트 기록 " + time);
                failures = 0;
            } else {
                prefs.status("처리 결과 확인 필요", "서버 기록이 아직 확인되지 않았습니다. 자동 재전송을 보류합니다");
                Notices.alert(context, "출석 기록을 확인해 주세요", "요청 결과가 불명확해 중복 처리를 보류했습니다");
            }
        } catch (SiteClient.Failure e) {
            failures = Math.min(failures + 1, 4);
            nextRead = SystemClock.elapsedRealtime() + (due ? 60000 : Math.min(900000, 60000L << (failures - 1)));
            prefs.status("출석 확인 보류", e.getMessage());
            prefs.log("확인 보류: " + e.code);
            if (due || !prefs.raw.getString("pending", "").isEmpty())
                Notices.alert(context, "출석 확인이 필요합니다", e.getMessage());
        } catch (Exception e) {
            prefs.status("인증을 다시 확인해 주세요", "연결 탭에서 인증을 저장해 주세요");
        }
    }
    private static void storeRecord(Prefs prefs, Policy.Record record, String day) {
        prefs.raw.edit().putString("record_day", day).putString("in", record.in()).putString("out", record.out()).apply();
    }
}
