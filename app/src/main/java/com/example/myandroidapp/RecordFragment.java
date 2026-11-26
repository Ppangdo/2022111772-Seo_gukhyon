package com.example.myandroidapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;

// B. 동작 녹화·검토
// 위쪽 작업대(모형·진행·큰 녹화 버튼)는 늘 보이고, 설정과 기록 검토는 아래 시트에서 연다
public class RecordFragment extends Fragment implements AppState.Listener {

    private AppState s;
    private View root;
    private BodyView body;
    private BottomSheetBehavior<View> sheet;
    private TextView editing, figTitle, readout, recPct, bodyHint, summary, pendingWarn;
    private View recBox;
    private LinearLayout recBar;
    private EditText recName;
    private View typePose, typeFix;
    private FlowLayout useNodes;
    private TextView delayValue, durValue, matchValue;
    private Slider matchSlider;
    private TextView btnRec, keptCount, takesEmpty, btnSavePhone, btnSaveHub, btnSaveHub2, saveHint;
    private LinearLayout keptBar, takesBox;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        s = AppState.get(requireContext());
        root = inflater.inflate(R.layout.fragment_record, container, false);

        // 작업대
        editing = root.findViewById(R.id.editing);
        figTitle = root.findViewById(R.id.fig_title);
        readout = root.findViewById(R.id.readout);
        body = root.findViewById(R.id.body);
        body.setup(s, "B", new BodyView.Callbacks() {
            @Override
            public void onSegmentTap(String seg) {
            }

            @Override
            public void onNodeTap(AppState.Node node) {
                s.toggleRecNode(node.id);
            }
        });
        bodyHint = root.findViewById(R.id.body_hint);
        recBox = root.findViewById(R.id.rec_box);
        recPct = root.findViewById(R.id.rec_pct);
        recBar = root.findViewById(R.id.rec_bar);
        recBar.setBackground(Ui.round(requireContext(), Ui.REC_SOFT, 0, 0, 5));
        summary = root.findViewById(R.id.summary);
        pendingWarn = root.findViewById(R.id.pending_warn);
        keptCount = root.findViewById(R.id.kept_count);
        keptBar = root.findViewById(R.id.kept_bar);
        btnRec = root.findViewById(R.id.btn_rec);
        btnRec.setOnClickListener(v -> {
            sheet.setState(BottomSheetBehavior.STATE_HIDDEN);
            s.startRec();
        });
        btnSaveHub = root.findViewById(R.id.btn_save_hub);
        btnSaveHub.setOnClickListener(v -> s.saveAction(true));

        // 시트: 처음엔 숨겨 두고, 설정 요약이나 "결정 필요"를 누르면 연다
        sheet = BottomSheetBehavior.from(root.findViewById(R.id.sheet));
        sheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        root.findViewById(R.id.summary_row).setOnClickListener(v -> sheet.setState(BottomSheetBehavior.STATE_EXPANDED));
        pendingWarn.setOnClickListener(v -> {
            sheet.setState(BottomSheetBehavior.STATE_EXPANDED);
            scrollToPendingTake();
        });
        root.findViewById(R.id.btn_close_sheet).setOnClickListener(v -> sheet.setState(BottomSheetBehavior.STATE_HIDDEN));
        root.findViewById(R.id.sheet_header).setOnClickListener(v -> sheet.setState(BottomSheetBehavior.STATE_HIDDEN));

        recName = root.findViewById(R.id.rec_name);
        recName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence t, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                s.setRecName(e.toString());
                summary.setText(summaryText());
            }
        });
        typePose = root.findViewById(R.id.type_pose);
        typeFix = root.findViewById(R.id.type_fix);
        typePose.setOnClickListener(v -> s.setType("pose"));
        typeFix.setOnClickListener(v -> s.setType("fix"));
        useNodes = root.findViewById(R.id.use_nodes);

        View delay = root.findViewById(R.id.delay);
        delayValue = delay.findViewById(R.id.step_value);
        delay.findViewById(R.id.step_minus).setOnClickListener(v -> s.changeDelay(-1));
        delay.findViewById(R.id.step_plus).setOnClickListener(v -> s.changeDelay(1));
        delay.findViewById(R.id.step_minus).setContentDescription("시작 지연 줄이기");
        delay.findViewById(R.id.step_plus).setContentDescription("시작 지연 늘리기");
        View dur = root.findViewById(R.id.dur);
        durValue = dur.findViewById(R.id.step_value);
        dur.findViewById(R.id.step_minus).setOnClickListener(v -> s.changeDur(-0.5f));
        dur.findViewById(R.id.step_plus).setOnClickListener(v -> s.changeDur(0.5f));
        dur.findViewById(R.id.step_minus).setContentDescription("녹화 길이 줄이기");
        dur.findViewById(R.id.step_plus).setContentDescription("녹화 길이 늘리기");

        View match = root.findViewById(R.id.match);
        match.findViewById(R.id.slider_label).setVisibility(View.GONE);
        matchValue = match.findViewById(R.id.slider_value);
        matchSlider = match.findViewById(R.id.slider);
        matchSlider.setValueFrom(60);
        matchSlider.setValueTo(95);
        matchSlider.setStepSize(1);
        matchSlider.setContentDescription("녹화 간 일치 기준");
        matchSlider.addOnChangeListener((sl, value, fromUser) -> {
            if (fromUser) s.setMatch((int) value);
        });

        takesBox = root.findViewById(R.id.takes);
        takesEmpty = root.findViewById(R.id.takes_empty);
        btnSavePhone = root.findViewById(R.id.btn_save_phone);
        btnSaveHub2 = root.findViewById(R.id.btn_save_hub2);
        saveHint = root.findViewById(R.id.save_hint);
        btnSavePhone.setOnClickListener(v -> s.saveAction(false));
        btnSaveHub2.setOnClickListener(v -> s.saveAction(true));
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        s.addListener(this);
        onStateChanged();
    }

    @Override
    public void onStop() {
        s.removeListener(this);
        super.onStop();
    }

    @Override
    public void onTick() {
        body.invalidate();
        readout.setText(s.readout());
        boolean rec = "rec".equals(s.recPhase);
        recBox.setVisibility(rec ? View.VISIBLE : View.GONE);
        bodyHint.setVisibility(rec ? View.GONE : View.VISIBLE);
        if (rec) {
            recPct.setText(s.recProg + "%");
            Ui.bar(recBar, s.recProg, Ui.REC);
        }
    }

    // "결정 필요"를 눌러 시트를 열면 첫 경고 기록이 보이도록 스크롤한다
    private void scrollToPendingTake() {
        for (int i = 0; i < s.takes.size() && i < takesBox.getChildCount(); i++) {
            if (s.takes.get(i).kept != null) continue;
            final View row = takesBox.getChildAt(i);
            final View scroll = root.findViewById(R.id.sheet_scroll);
            final View card = root.findViewById(R.id.takes_card);
            scroll.post(() -> ((androidx.core.widget.NestedScrollView) scroll)
                    .smoothScrollTo(0, card.getTop() + takesBox.getTop() + row.getTop() - Ui.dp(requireContext(), 12)));
            return;
        }
    }

    private String summaryText() {
        String name = s.rec.name.trim().isEmpty() ? "이름 없음" : s.rec.name.trim();
        String nodes = s.rec.nodes.isEmpty() ? "노드 없음" : "노드 " + s.nodesText(s.rec.nodes);
        return name + " · " + AppState.typeName(s.rec.type) + " · " + nodes
                + " · 지연 " + s.rec.delay + "초 · " + Ui.seconds(s.rec.dur);
    }

    @Override
    public void onStateChanged() {
        Ui.bindNote(root, s);
        AppState.Motion edit = s.findAction(s.editId);
        editing.setVisibility(edit != null ? View.VISIBLE : View.GONE);
        if (edit != null) {
            editing.setText("\"" + edit.name + "\"에 녹화를 추가하는 중 · 기존 기록 " + edit.takes + "개");
        }
        figTitle.setText("신체 모형 · " + AppState.baseName(s.base));
        onTick();

        summary.setText(summaryText());
        summary.setTextColor(s.rec.nodes.isEmpty() ? Ui.WARN_DARK : Ui.INK);

        boolean idle = "idle".equals(s.recPhase);
        Ui.enable(btnRec, idle);
        if ("count".equals(s.recPhase)) btnRec.setText(s.countN + "초 후 시작");
        else if ("rec".equals(s.recPhase)) btnRec.setText("녹화 중");
        else btnRec.setText((s.takes.size() + 1) + "번째 녹화 시작");

        int kept = s.keptCount();
        int pending = s.pendingCount();
        keptCount.setText("남긴 기록 " + kept + "개");
        Ui.bar(keptBar, Math.min(100, kept * 10), Ui.PRIMARY);
        pendingWarn.setVisibility(pending > 0 ? View.VISIBLE : View.INVISIBLE);
        pendingWarn.setText("⚠ 결정 필요 " + pending + "개 ›");

        boolean cannot = s.cannotSave();
        Ui.enable(btnSaveHub, !cannot);
        Ui.enable(btnSaveHub2, !cannot);
        Ui.enable(btnSavePhone, !cannot);
        AppState.Take rep = s.representative();
        if (pending > 0) saveHint.setText("경고가 뜬 기록을 남길지 버릴지 먼저 정하세요.");
        else if (kept < 3) saveHint.setText("남긴 기록이 3개 이상이어야 저장할 수 있습니다.");
        else saveHint.setText("대표 기록 #" + (rep != null ? rep.n : "") + "이 인식의 기준이 됩니다.");

        // 시트 안 설정
        if (!recName.getText().toString().equals(s.rec.name)) recName.setText(s.rec.name);
        boolean pose = s.rec.isPose();
        typePose.setBackground(pose ? Ui.round(requireContext(), Ui.PRIMARY_SOFT, Ui.PRIMARY, 2, 12)
                : Ui.round(requireContext(), Ui.SURFACE, Ui.BTN_LINE, 1, 12));
        typeFix.setBackground(!pose ? Ui.round(requireContext(), Ui.OK_SOFT, Ui.OK, 2, 12)
                : Ui.round(requireContext(), Ui.SURFACE, Ui.BTN_LINE, 1, 12));
        bindUseNodes();
        delayValue.setText(s.rec.delay + "초");
        durValue.setText(Ui.seconds(s.rec.dur));
        if (matchSlider.getValue() != s.rec.match) matchSlider.setValue(s.rec.match);
        matchValue.setText(s.rec.match + "%");
        bindTakes();
    }

    private void bindUseNodes() {
        useNodes.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AppState.Node n : s.nodes) {
            if (n.seg == null) continue;
            TextView p = (TextView) inflater.inflate(R.layout.item_pill, useNodes, false);
            p.setTextSize(16);
            p.setText(n.id + " " + AppState.SEGN.get(n.seg));
            Ui.pill(p, s.rec.nodes.contains(n.id), 999);
            final String id = n.id;
            p.setOnClickListener(v -> s.toggleRecNode(id));
            useNodes.addView(p);
        }
    }

    private void bindTakes() {
        takesBox.removeAllViews();
        takesEmpty.setVisibility(s.takes.isEmpty() ? View.VISIBLE : View.GONE);
        AppState.Take rep = s.representative();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (final AppState.Take t : s.takes) {
            View row = inflater.inflate(R.layout.item_take, takesBox, false);
            boolean pending = t.kept == null;
            boolean dropped = Boolean.FALSE.equals(t.kept);
            boolean isRep = rep == t;
            boolean replaying = s.replay == t;

            int rowColor = replaying ? Ui.PRIMARY_SOFT : (pending ? Ui.WARN_ROW : (dropped ? Ui.DROPPED : Ui.SURFACE2));
            row.setBackground(pending && !replaying ? Ui.warnRow(requireContext(), 10) : Ui.round(requireContext(), rowColor, 0, 0, 10));
            row.setAlpha(dropped ? 0.7f : 1f);
            ((TextView) row.findViewById(R.id.take_n)).setText("#" + t.n);
            Ui.bar(row.findViewById(R.id.take_bar), t.sim, pending ? Ui.WARN : (dropped ? Ui.DROPPED_BAR : Ui.PRIMARY));
            ((TextView) row.findViewById(R.id.take_sim)).setText(t.sim + "%");

            TextView status = row.findViewById(R.id.take_status);
            if (pending) {
                status.setText("⚠ 경고: 다른 기록과 차이가 큽니다");
                status.setTextColor(Ui.WARN_DARK);
            } else if (dropped) {
                status.setText("버림");
                status.setTextColor(Ui.MUTED);
            } else if (isRep) {
                status.setText("대표 기록");
                status.setTextColor(Ui.PRIMARY_DARK);
            } else {
                status.setText(t.sim < s.rec.match ? "남김 (기준 미달)" : "일치");
                status.setTextColor(Ui.OK_DARK);
            }
            status.setTypeface(null, pending || isRep ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            // 다시 보기는 모형에서 움직임을 보여주므로 시트를 닫는다
            row.findViewById(R.id.take_replay).setOnClickListener(v -> {
                sheet.setState(BottomSheetBehavior.STATE_HIDDEN);
                s.startReplay(t);
            });
            TextView keep = row.findViewById(R.id.take_keep);
            TextView drop = row.findViewById(R.id.take_drop);
            if (pending) {
                keep.setText("남기기");
                drop.setTextColor(Ui.WARN_DARK);
                drop.setBackground(Ui.round(requireContext(), Ui.SURFACE, Ui.WARN, 1, 10));
                keep.setOnClickListener(v -> s.setKept(t, Boolean.TRUE));
                drop.setOnClickListener(v -> s.setKept(t, Boolean.FALSE));
            } else if (dropped) {
                keep.setVisibility(View.GONE);
                drop.setText("되살리기");
                drop.setOnClickListener(v -> s.setKept(t, Boolean.TRUE));
            } else {
                keep.setVisibility(View.GONE);
                drop.setOnClickListener(v -> s.setKept(t, Boolean.FALSE));
            }
            takesBox.addView(row);
        }
    }
}
