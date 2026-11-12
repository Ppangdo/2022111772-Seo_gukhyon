package com.example.myandroidapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

// 앱 전체 상태와 시뮬레이션 로직. 목업(Main.dc.html)의 Component 클래스를 옮긴 것이다.
// 화면(프래그먼트)은 Listener 로 등록해 두고 상태가 바뀌면 다시 그린다.
public class AppState {

    public interface Listener {
        // 목록·버튼 등 구조가 바뀌었을 때
        void onStateChanged();

        // 60ms 마다: 신체 모형 움직임, 진행 막대, 일치도 등 애니메이션만
        void onTick();
    }

    public static final Map<String, String> SEGN = new LinkedHashMap<>();

    static {
        SEGN.put("head", "머리");
        SEGN.put("chest", "가슴");
        SEGN.put("pelvis", "골반");
        SEGN.put("rua", "오른 윗팔");
        SEGN.put("rla", "오른 아래팔");
        SEGN.put("rh", "오른손");
        SEGN.put("lua", "왼 윗팔");
        SEGN.put("lla", "왼 아래팔");
        SEGN.put("lh", "왼손");
        SEGN.put("rth", "오른 허벅지");
        SEGN.put("rsh", "오른 정강이");
        SEGN.put("rf", "오른발");
        SEGN.put("lth", "왼 허벅지");
        SEGN.put("lsh", "왼 정강이");
        SEGN.put("lf", "왼발");
    }

    public static final String[] BASES = {"stand", "sit", "lie"};

    public static String baseName(String base) {
        if ("sit".equals(base)) return "앉은 자세";
        if ("lie".equals(base)) return "누운 자세";
        return "선 자세";
    }

    public static String typeName(String type) {
        return "pose".equals(type) ? "포즈형" : "교정형";
    }

    // 녹화할 때마다 차례로 나오는 가짜 일치도(시뮬레이션). 68, 63 은 일부러 넣은 나쁜 기록이다.
    private static final int[] PLAN_POSE = {93, 90, 95, 68, 91, 88, 94, 92, 89, 90};
    private static final int[] PLAN_FIX = {96, 94, 63, 95, 93, 92, 95, 94, 91, 96};

    public static class Node {
        public final String id;
        public String seg;
        public int bat;
        public String link;
        public boolean hub;

        Node(String id, String seg, int bat, String link, boolean hub) {
            this.id = id;
            this.seg = seg;
            this.bat = bat;
            this.link = link;
            this.hub = hub;
        }

        public boolean lowBat() {
            return bat < 20;
        }

        public boolean weak() {
            return "weak".equals(link);
        }

        public boolean warn() {
            return lowBat() || weak();
        }
    }

    public static class Motion {
        public long id;
        public String name;
        public String type;
        public List<String> nodes = new ArrayList<>();
        public int takes;
        public String base;
        public int sens;
        public String sim;
        public int hubSlot = -1;

        public boolean isPose() {
            return "pose".equals(type);
        }
    }

    public static class Take {
        public final int n;
        public final int sim;
        public Boolean kept; // true 남김, false 버림, null 경고(결정 필요)

        Take(int n, int sim, Boolean kept) {
            this.n = n;
            this.sim = sim;
            this.kept = kept;
        }
    }

    public static class Rec {
        public String name = "오른팔 들기";
        public String type = "pose";
        public List<String> nodes = new ArrayList<>(Arrays.asList("02", "03"));
        public int delay = 3;
        public float dur = 1f;
        public int match = 80;

        public boolean isPose() {
            return "pose".equals(type);
        }
    }

    public static class LogEntry {
        public String time;
        public String name;
        public String kind;
        public String out;
        public boolean warn;
    }

    public static class Flash {
        public String text;
        public long until;
        public boolean warn;
    }

    private static AppState instance;

    public static AppState get(Context context) {
        if (instance == null) instance = new AppState(context.getApplicationContext());
        return instance;
    }

    private final MotionDbHelper db;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Random random = new Random();

    public String tab = "A";

    // A. 노드 설정·보정
    public String base = "stand";
    public boolean back;
    public String measure = "done"; // idle, count, measure, done
    public int measureN;
    private long measureT0;
    public int measureProg;
    public String measuredAt = "09:12:40";
    public final List<Node> nodes = new ArrayList<>();
    public boolean pairing;
    public String selSeg = "rua";
    public String placing;
    public String dragOver;
    public String identify;
    private long identUntil;
    public String identMsg = "";
    public final Map<String, int[]> corr = new HashMap<>();
    public boolean confirmReset;

    // 신체 모형 자세(도 단위)와 상체 기울기(0~1)
    public float armR = 4, elbowR = 4, armL = 4, elbowL = 4, lean = 0;

    // B. 동작 녹화·검토
    public final Rec rec = new Rec();
    public String recPhase = "idle"; // idle, count, rec
    public int countN;
    private long recT0;
    public int recProg;
    private int recPlan = 90;
    public final List<Take> takes = new ArrayList<>();
    private int takeSeq;
    public Take replay;
    private long replayT0;
    public Long editId;

    // C. 동작 목록
    public final List<Motion> actions = new ArrayList<>();
    public String sync = "synced"; // synced, pending, sending
    public Long pickFor;

    // D. 실시간 테스트
    public String simMode = "idle"; // arm, lean, busy, idle
    public boolean sendOut = true;
    public final Map<Long, Integer> sims = new HashMap<>();
    public final Map<Long, Long> badMs = new HashMap<>();
    public final List<LogEntry> log = new ArrayList<>();
    public Flash flash;
    private final Map<Long, Long> badSince = new HashMap<>();
    private final Map<Long, Long> lastFire = new HashMap<>();
    private float busyArm = 20, busyLean = 0.1f;
    private long busyT;

    public String note = "";

    private AppState(Context context) {
        db = new MotionDbHelper(context);
        nodes.add(new Node("01", "chest", 82, "good", true));
        nodes.add(new Node("02", "rua", 74, "good", false));
        nodes.add(new Node("03", "rla", 15, "good", false));
        nodes.add(new Node("04", null, 96, "weak", false));
        actions.addAll(db.loadAll());
        handler.post(ticker);
    }

    // ---------- 리스너와 타이머 ----------

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onStateChanged();
    }

    private void later(Runnable r, long ms) {
        handler.postDelayed(r, ms);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 60);
        }
    };

    private void tick() {
        if (listeners.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean structural = false;
        if (identify != null && now > identUntil) {
            identify = null;
            structural = true;
        }
        if (flash != null && now > flash.until) flash = null;
        if ("measure".equals(measure)) measureProg = (int) Math.min(100, (now - measureT0) / 20);
        if ("B".equals(tab)) structural |= recTick(now);
        if ("D".equals(tab)) structural |= simTick(now);

        if (structural) {
            changed();
        } else {
            for (Listener l : new ArrayList<>(listeners)) l.onTick();
        }
    }

    private static float ease(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
    }

    private static String hhmmss() {
        return new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
    }

    // ---------- 공통 ----------

    public void go(String newTab) {
        tab = newTab;
        armR = 4;
        elbowR = 4;
        lean = 0;
        replay = null;
        placing = null;
        dragOver = null;
        if ("D".equals(newTab)) {
            badSince.clear();
            lastFire.clear();
            sims.clear();
            badMs.clear();
            simMode = "idle";
            flash = null;
        }
        changed();
    }

    public void clearNote() {
        note = "";
        changed();
    }

    public Node nodeById(String id) {
        for (Node n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    public Node nodeAt(String seg) {
        if (seg == null) return null;
        for (Node n : nodes) if (seg.equals(n.seg)) return n;
        return null;
    }

    public int placedCount() {
        int c = 0;
        for (Node n : nodes) if (n.seg != null) c++;
        return c;
    }

    public String readout() {
        return "오른팔 " + Math.round(armR) + "° · 상체 앞 기울기 " + Math.round(lean * 35) + "°";
    }

    // ---------- A. 노드 설정·보정 ----------

    public void setBack(boolean value) {
        back = value;
        changed();
    }

    public void setBase(String b) {
        if (b.equals(base)) return;
        base = b;
        measure = "idle";
        note = "기준 자세를 " + baseName(b) + "로 바꿨습니다. 사용자가 그 자세를 취하게 한 뒤 다시 측정하세요.";
        changed();
    }

    public void startMeasure() {
        if ("count".equals(measure) || "measure".equals(measure)) return;
        measureStep(3);
    }

    private void measureStep(final int n) {
        if (n > 0) {
            measure = "count";
            measureN = n;
            changed();
            later(() -> measureStep(n - 1), 1000);
        } else {
            measure = "measure";
            measureT0 = System.currentTimeMillis();
            measureProg = 0;
            changed();
            later(() -> {
                measure = "done";
                measuredAt = hhmmss();
                changed();
            }, 2000);
        }
    }

    public void addNode() {
        if (pairing) return;
        pairing = true;
        changed();
        later(() -> {
            String id = String.format(Locale.US, "%02d", nodes.size() + 1);
            nodes.add(new Node(id, null, 100, "good", false));
            pairing = false;
            note = "노드 " + id + "가 허브에 페어링되었습니다. 미배치 칸에서 신체 모형으로 옮기세요.";
            changed();
        }, 1500);
    }

    public void togglePlacing(String id) {
        if (id.equals(placing)) {
            placing = null;
            note = "";
        } else {
            placing = id;
            note = "노드 " + id + "를 붙일 부위를 신체 모형에서 누르세요.";
        }
        changed();
    }

    public void segClick(String seg) {
        if (!"A".equals(tab)) return;
        if (placing != null) {
            placeNode(placing, seg);
            return;
        }
        selSeg = seg;
        confirmReset = false;
        changed();
    }

    public void selectNode(Node n) {
        if (!"A".equals(tab)) return;
        selSeg = n.seg;
        placing = null;
        confirmReset = false;
        changed();
    }

    // seg 가 null 이면 배치 해제. 다른 노드가 있던 자리면 서로 자리를 바꾼다.
    public void placeNode(String id, String seg) {
        Node node = nodeById(id);
        dragOver = null;
        placing = null;
        if (node == null) {
            changed();
            return;
        }
        if (node.hub) {
            note = "허브(01)는 가슴에 고정되어 있습니다.";
            changed();
            return;
        }
        Node occ = null;
        if (seg != null) {
            Node at = nodeAt(seg);
            if (at != null && at != node) occ = at;
        }
        if (occ != null && occ.hub) {
            note = "가슴에는 허브가 고정되어 있습니다. 다른 부위를 고르세요.";
            changed();
            return;
        }
        String from = node.seg;
        node.seg = seg;
        if (occ != null) occ.seg = from;
        String msg = seg != null ? "노드 " + id + "를 " + SEGN.get(seg) + "에 붙였습니다." : "노드 " + id + "의 배치를 해제했습니다.";
        if (occ != null) {
            msg += " 원래 있던 노드 " + occ.id + "는 " + (from != null ? SEGN.get(from) : "미배치 칸") + "으로 옮겼습니다.";
        }
        if (seg != null) selSeg = seg;
        note = msg;
        changed();
    }

    // 끌기 중에 손가락 아래 부위를 표시만 한다(목록은 다시 그리지 않는다)
    public void setDragOver(String seg) {
        dragOver = seg;
    }

    private void identifyNode(String id, String msg) {
        identify = id;
        identUntil = System.currentTimeMillis() + 3000;
        identMsg = msg;
        changed();
    }

    public void shake() {
        Node t = null;
        for (Node n : nodes) {
            if (n.seg == null) {
                t = n;
                break;
            }
        }
        if (t == null) t = nodes.get(nodes.size() - 1);
        identifyNode(t.id, "실물 노드 " + t.id + "가 흔들렸습니다. 화면에서 깜빡이는 노드입니다.");
    }

    public void led() {
        Node n = nodeAt(selSeg);
        if (n != null) identifyNode(n.id, "노드 " + n.id + "의 LED가 3초 동안 깜빡입니다.");
    }

    public void unplaceSelected() {
        Node n = nodeAt(selSeg);
        if (n != null) placeNode(n.id, null);
    }

    public int[] corrOf(String seg) {
        int[] c = corr.get(seg);
        return c != null ? c : new int[]{0, 0, 0};
    }

    public boolean hasCorr(String seg) {
        int[] c = corrOf(seg);
        return c[0] != 0 || c[1] != 0 || c[2] != 0;
    }

    public void setCorr(int axis, int value) {
        int[] c = corrOf(selSeg).clone();
        c[axis] = value;
        corr.put(selSeg, c);
        changed();
    }

    public int corrCount() {
        int count = 0;
        for (String k : corr.keySet()) if (hasCorr(k)) count++;
        return count;
    }

    public void setConfirmReset(boolean value) {
        confirmReset = value;
        changed();
    }

    public void resetCorr() {
        corr.clear();
        confirmReset = false;
        note = "보정값을 처음 상태로 되돌렸습니다.";
        changed();
    }

    // ---------- B. 동작 녹화·검토 ----------

    public void setType(String type) {
        boolean pose = "pose".equals(type);
        rec.type = type;
        rec.dur = pose ? 1f : 2f;
        rec.nodes = new ArrayList<>(pose ? Arrays.asList("02", "03") : Arrays.asList("01"));
        rec.name = pose ? "오른팔 들기" : "바른 앉은 자세";
        takes.clear();
        takeSeq = 0;
        editId = null;
        changed();
    }

    // 입력 중인 글자를 덮어쓰지 않도록 다시 그리지 않는다
    public void setRecName(String name) {
        rec.name = name;
    }

    public void toggleRecNode(String id) {
        if (rec.nodes.contains(id)) rec.nodes.remove(id);
        else rec.nodes.add(id);
        changed();
    }

    public void changeDelay(int d) {
        rec.delay = Math.max(1, Math.min(10, rec.delay + d));
        changed();
    }

    public void changeDur(float d) {
        rec.dur = Math.max(0.5f, Math.min(5f, rec.dur + d));
        changed();
    }

    public void setMatch(int value) {
        rec.match = value;
        changed();
    }

    public void startRec() {
        if (!"idle".equals(recPhase)) return;
        if (rec.nodes.isEmpty()) {
            note = "사용할 노드를 하나 이상 고르세요.";
            changed();
            return;
        }
        int[] plan = rec.isPose() ? PLAN_POSE : PLAN_FIX;
        final int sim = plan[takeSeq % 10];
        replay = null;
        recStep(rec.delay, sim);
    }

    private void recStep(final int n, final int sim) {
        if (n > 0) {
            recPhase = "count";
            countN = n;
            changed();
            later(() -> recStep(n - 1, sim), 1000);
        } else {
            recPhase = "rec";
            recT0 = System.currentTimeMillis();
            recPlan = sim;
            recProg = 0;
            changed();
        }
    }

    private boolean recTick(long now) {
        boolean structural = false;
        if ("rec".equals(recPhase)) {
            float p = Math.min(1f, (now - recT0) / (rec.dur * 1000f));
            if (rec.isPose()) {
                float peak = 150 - (100 - recPlan) * 1.6f;
                armR = 4 + (peak - 4) * ease(Math.min(1f, p * 1.6f));
                lean = 0;
            } else {
                armR = 4;
                lean = ((100 - recPlan) / 60f) * ease(p);
            }
            recProg = Math.round(p * 100);
            if (p >= 1) {
                takeSeq++;
                takes.add(new Take(takeSeq, recPlan, recPlan >= rec.match ? Boolean.TRUE : null));
                recPhase = "idle";
                recProg = 0;
                armR = 4;
                lean = 0;
                structural = true;
            }
        } else if (replay != null) {
            float p = Math.min(1f, (now - replayT0) / 1600f);
            float k = p < 0.75f ? ease(p / 0.75f) : 1f;
            if (rec.isPose()) {
                armR = 4 + (150 - (100 - replay.sim) * 1.6f - 4) * k;
                lean = 0;
            } else {
                lean = ((100 - replay.sim) / 60f) * k;
                armR = 4;
            }
            if (p >= 1) {
                replay = null;
                armR = 4;
                lean = 0;
                structural = true;
            }
        } else if ("count".equals(recPhase)) {
            armR = 4 + 2 * (float) Math.sin(now / 500.0);
        }
        elbowR = armR > 60 ? 12 : 4;
        return structural;
    }

    public void startReplay(Take t) {
        replay = t;
        replayT0 = System.currentTimeMillis();
        changed();
    }

    public void setKept(Take t, Boolean kept) {
        t.kept = kept;
        changed();
    }

    public int keptCount() {
        int c = 0;
        for (Take t : takes) if (Boolean.TRUE.equals(t.kept)) c++;
        return c;
    }

    public int pendingCount() {
        int c = 0;
        for (Take t : takes) if (t.kept == null) c++;
        return c;
    }

    // 남긴 기록 중 일치도가 가장 높은 기록
    public Take representative() {
        Take rep = null;
        for (Take t : takes) {
            if (Boolean.TRUE.equals(t.kept) && (rep == null || t.sim > rep.sim)) rep = t;
        }
        return rep;
    }

    public boolean cannotSave() {
        return keptCount() < 3 || pendingCount() > 0 || !"idle".equals(recPhase);
    }

    public void saveAction(boolean toHub) {
        int kept = keptCount();
        String name = rec.name.trim();
        Motion m = editId != null ? findAction(editId) : null;
        if (m != null) {
            if (!name.isEmpty()) m.name = name;
            m.nodes = new ArrayList<>(rec.nodes);
            m.takes = kept;
            db.update(m);
        } else {
            m = new Motion();
            m.name = name.isEmpty() ? "새 동작" : name;
            m.type = rec.type;
            m.nodes = new ArrayList<>(rec.nodes);
            m.takes = kept;
            m.base = base;
            m.sens = rec.isPose() ? 85 : 75;
            m.sim = rec.isPose() ? "arm" : "lean";
            m.id = db.insert(m);
            actions.add(m);
        }
        String msg = "\"" + m.name + "\"을 폰에 저장했습니다.";
        if (toHub) {
            if (m.hubSlot >= 0) {
                sync = "pending";
                msg = "\"" + m.name + "\"의 바뀐 기록을 허브로 보낼 준비가 됐습니다.";
            } else {
                int empty = emptySlot();
                if (empty >= 0) {
                    setSlot(empty, m);
                    sync = "pending";
                    msg = "\"" + m.name + "\"을 허브 슬롯 " + (empty + 1) + "에 넣었습니다.";
                } else {
                    pickFor = m.id;
                    msg = "허브 슬롯이 가득 찼습니다. 교체할 슬롯을 고르세요.";
                }
            }
        }
        takes.clear();
        takeSeq = 0;
        editId = null;
        note = msg;
        go("C");
    }

    // C 화면의 "녹화 추가": 기존 기록을 다시 불러와 B 화면에서 이어서 녹화한다
    public void addTakes(Motion a) {
        int[] plan = a.isPose() ? PLAN_POSE : PLAN_FIX;
        takes.clear();
        for (int i = 0; i < a.takes; i++) {
            int sim = plan[i % 10] < 80 ? 90 : plan[i % 10];
            takes.add(new Take(i + 1, sim, Boolean.TRUE));
        }
        takeSeq = a.takes;
        editId = a.id;
        rec.name = a.name;
        rec.type = a.type;
        rec.nodes = new ArrayList<>(a.nodes);
        rec.dur = a.isPose() ? 1f : 2f;
        go("B");
    }

    public void newAction() {
        rec.type = "pose";
        setType("pose");
        go("B");
    }

    // ---------- C. 동작 목록 ----------

    public Motion findAction(Long id) {
        if (id == null) return null;
        for (Motion m : actions) if (m.id == id) return m;
        return null;
    }

    public Motion hubAt(int slot) {
        for (Motion m : actions) if (m.hubSlot == slot) return m;
        return null;
    }

    private int emptySlot() {
        for (int i = 0; i < 3; i++) if (hubAt(i) == null) return i;
        return -1;
    }

    private void setSlot(int slot, Motion m) {
        Motion old = hubAt(slot);
        if (old != null && old != m) {
            old.hubSlot = -1;
            db.update(old);
        }
        if (m != null) {
            m.hubSlot = slot;
            db.update(m);
        }
    }

    public void insertToHub(Motion m) {
        int empty = emptySlot();
        if (empty >= 0) {
            setSlot(empty, m);
            sync = "pending";
            pickFor = null;
        } else {
            pickFor = m.id;
        }
        changed();
    }

    public void pickSlot(int slot) {
        Motion m = findAction(pickFor);
        if (m != null) setSlot(slot, m);
        pickFor = null;
        sync = "pending";
        changed();
    }

    public void cancelPick() {
        pickFor = null;
        changed();
    }

    public void removeFromSlot(int slot) {
        Motion m = hubAt(slot);
        if (m != null) {
            m.hubSlot = -1;
            db.update(m);
        }
        sync = "pending";
        changed();
    }

    public void deleteAction(Motion m) {
        db.delete(m.id);
        actions.remove(m);
        changed();
    }

    public void sendNow() {
        sync = "sending";
        changed();
        later(() -> {
            sync = "synced";
            note = "허브에 반영했습니다. 이제 앱 없이도 동작합니다.";
            changed();
        }, 1200);
    }

    public String nodesText(List<String> list) {
        return TextUtils.join("·", list);
    }

    // ---------- D. 실시간 테스트 ----------

    public void setSimMode(String mode) {
        busyT = 0;
        simMode = mode;
        changed();
    }

    public void setSendOut(boolean value) {
        sendOut = value;
        changed();
    }

    // 슬라이더를 끄는 중에 화면을 다시 만들지 않도록 onTick 으로만 반영한다
    public void setSens(Motion m, int value) {
        m.sens = value;
        db.update(m);
    }

    private boolean simTick(long now) {
        float tArm = 6 + 4 * (float) Math.sin(now / 700.0);
        float tLean = 0.03f;
        if ("arm".equals(simMode)) {
            tArm = 150;
        } else if ("lean".equals(simMode)) {
            tArm = 6;
            tLean = 1;
        } else if ("busy".equals(simMode)) {
            if (now - busyT > 900) {
                busyArm = 10 + random.nextFloat() * 80;
                busyLean = random.nextFloat() * 0.3f;
                busyT = now;
            }
            tArm = busyArm;
            tLean = busyLean;
        }
        armR += (tArm - armR) * 0.12f;
        lean += (tLean - lean) * 0.1f;
        elbowR = armR > 60 ? 12 : 4;

        boolean fired = false;
        for (int i = 0; i < 3; i++) {
            Motion a = hubAt(i);
            if (a == null) continue;
            float v;
            if ("arm".equals(a.sim)) v = 100 - Math.abs(armR - 150) * 0.55f;
            else if ("lean".equals(a.sim)) v = 100 - lean * 65;
            else v = 18 + 6 * (float) Math.sin(now / 900.0 + i);
            v = Math.max(0, Math.min(100, v + (random.nextFloat() - 0.5f) * 2));
            sims.put(a.id, Math.round(v));

            Long last = lastFire.get(a.id);
            long lastT = last != null ? last : 0;
            boolean fire = false;
            if (a.isPose()) {
                if (v >= a.sens && now - lastT > 3000) fire = true;
            } else if (v < a.sens) {
                Long since = badSince.get(a.id);
                if (since == null) {
                    since = now;
                    badSince.put(a.id, now);
                }
                long el = now - since;
                badMs.put(a.id, el);
                if (el >= 1500 && now - lastT >= 15000) fire = true;
            } else {
                badSince.remove(a.id);
                badMs.remove(a.id);
                lastFire.put(a.id, 0L);
            }

            if (fire) {
                lastFire.put(a.id, now);
                fired = true;
                int track = i + 1;
                LogEntry e = new LogEntry();
                e.time = hhmmss();
                e.name = a.name;
                e.kind = a.isPose() ? "포즈 발동" : "자세 이탈 경고";
                e.warn = !a.isPose();
                e.out = sendOut ? "소리 모듈 · 트랙 " + track : "화면 표시만";
                log.add(0, e);
                while (log.size() > 8) log.remove(log.size() - 1);
                Flash f = new Flash();
                f.text = a.isPose() ? "발동: " + a.name + " → 트랙 " + track : "경고: \"" + a.name + "\"에서 벗어났습니다";
                f.until = now + 1800;
                f.warn = !a.isPose();
                flash = f;
            }
        }
        return fired;
    }
}
