package com.example.myandroidapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.slider.Slider;

import java.util.Locale;

// D. 실시간 테스트: 흉내 버튼으로 움직임을 만들고, 허브 슬롯의 동작이 발동하는지 본다
public class LiveTestFragment extends Fragment implements AppState.Listener {

    private static final String[][] SIM_MODES = {
            {"arm", "오른팔 들기 흉내"}, {"lean", "허리 굽히기 흉내"}, {"busy", "평소 움직임"}, {"idle", "가만히 있기"}};

    private static class LiveRow {
        AppState.Motion motion;
        View view, thr;
        LinearLayout bar;
        TextView state, sim, thrLabel, sensValue;
    }

    private AppState s;
    private BodyView body;
    private TextView figTitle, readout, logEmpty;
    private FlowLayout simButtons;
    private CheckBox sendOut;
    private LinearLayout liveBox, logBox;
    private final LiveRow[] rows = new LiveRow[3];
    private String hubSignature = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        s = AppState.get(requireContext());
        View root = inflater.inflate(R.layout.fragment_test, container, false);
        figTitle = root.findViewById(R.id.fig_title);
        readout = root.findViewById(R.id.readout);
        body = root.findViewById(R.id.body);
        body.setup(s, "D", null);
        simButtons = root.findViewById(R.id.sim_buttons);
        for (String[] m : SIM_MODES) {
            TextView p = (TextView) inflater.inflate(R.layout.item_pill, simButtons, false);
            p.setTextSize(14);
            p.setText(m[1]);
            p.setTag(m[0]);
            final String mode = m[0];
            p.setOnClickListener(v -> s.setSimMode(mode));
            simButtons.addView(p);
        }
        sendOut = root.findViewById(R.id.send_out);
        sendOut.setOnCheckedChangeListener((b, checked) -> {
            if (b.isPressed()) s.setSendOut(checked);
        });
        liveBox = root.findViewById(R.id.live);
        logBox = root.findViewById(R.id.log);
        logEmpty = root.findViewById(R.id.log_empty);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        s.addListener(this);
        hubSignature = "";
        onStateChanged();
    }

    @Override
    public void onStop() {
        s.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStateChanged() {
        figTitle.setText("신체 모형 · " + AppState.baseName(s.base));
        for (int i = 0; i < simButtons.getChildCount(); i++) {
            TextView p = (TextView) simButtons.getChildAt(i);
            Ui.pill(p, p.getTag().equals(s.simMode), 10);
        }
        if (sendOut.isChecked() != s.sendOut) sendOut.setChecked(s.sendOut);

        // 허브 구성이 바뀔 때만 행을 새로 만든다(슬라이더를 끄는 중에 사라지지 않게)
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            AppState.Motion m = s.hubAt(i);
            sig.append(m != null ? m.id : -1).append(',');
        }
        if (!sig.toString().equals(hubSignature)) {
            hubSignature = sig.toString();
            buildRows();
        }
        bindLog();
        onTick();
    }

    @Override
    public void onTick() {
        body.invalidate();
        readout.setText(s.readout());
        for (LiveRow r : rows) {
            if (r == null || r.motion == null) continue;
            AppState.Motion a = r.motion;
            Integer value = s.sims.get(a.id);
            int v = value != null ? value : 0;
            boolean pose = a.isPose();
            boolean over = pose && v >= a.sens;
            boolean bad = !pose && v < a.sens;
            Long el = s.badMs.get(a.id);
            float sec = Math.min(1.5f, (el != null ? el : 0) / 1000f);

            r.state.setText(pose ? (over ? "기준 넘음" : "대기")
                    : (bad ? String.format(Locale.US, "이탈 %.1f / 1.5초", sec) : "바른 자세 유지"));
            r.state.setTextColor(over ? Ui.PRIMARY_DARK : (bad ? Ui.WARN_DARK : (pose ? Ui.MUTED : Ui.OK_DARK)));
            Ui.bar(r.bar, v, pose ? (over ? Ui.PRIMARY : Ui.PRIMARY_PALE) : (bad ? Ui.WARN : Ui.OK));
            r.sim.setText("일치도 " + v + "%");
            r.thrLabel.setText((pose ? "발동 기준 " : "이탈 기준 ") + a.sens + "%");
            r.sensValue.setText(a.sens + "%");
            int w = r.bar.getWidth();
            if (w > 0) r.thr.setTranslationX(w * a.sens / 100f - r.thr.getWidth() / 2f);
        }
    }

    private void buildRows() {
        liveBox.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < 3; i++) {
            LiveRow r = new LiveRow();
            r.motion = s.hubAt(i);
            r.view = inflater.inflate(R.layout.item_live, liveBox, false);
            ((TextView) r.view.findViewById(R.id.live_num)).setText("슬롯 " + (i + 1));
            TextView name = r.view.findViewById(R.id.live_name);
            TextView type = r.view.findViewById(R.id.live_type);
            View detail = r.view.findViewById(R.id.live_detail);
            r.state = r.view.findViewById(R.id.live_state);
            if (r.motion == null) {
                name.setText("비어 있음");
                type.setVisibility(View.GONE);
                detail.setVisibility(View.GONE);
                r.state.setText("");
            } else {
                final AppState.Motion a = r.motion;
                name.setText(a.name);
                Ui.typeBadge(type, a.type);
                r.bar = r.view.findViewById(R.id.live_bar);
                r.thr = r.view.findViewById(R.id.live_thr);
                r.sim = r.view.findViewById(R.id.live_sim);
                r.thrLabel = r.view.findViewById(R.id.live_thr_label);
                View sens = r.view.findViewById(R.id.live_sens);
                ((TextView) sens.findViewById(R.id.slider_label)).setText("인식 민감도");
                r.sensValue = sens.findViewById(R.id.slider_value);
                Slider sl = sens.findViewById(R.id.slider);
                sl.setValueFrom(50);
                sl.setValueTo(98);
                sl.setStepSize(1);
                sl.setValue(Math.max(50, Math.min(98, a.sens)));
                sl.setContentDescription(a.name + " 인식 민감도");
                sl.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) s.setSens(a, (int) value);
                });
            }
            rows[i] = r;
            liveBox.addView(r.view);
        }
    }

    private void bindLog() {
        logBox.removeAllViews();
        logEmpty.setVisibility(s.log.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AppState.LogEntry e : s.log) {
            View row = inflater.inflate(R.layout.item_log, logBox, false);
            ((TextView) row.findViewById(R.id.log_time)).setText(e.time);
            TextView text = row.findViewById(R.id.log_text);
            String kind = e.kind;
            android.text.SpannableString span = new android.text.SpannableString(kind + " · " + e.name);
            span.setSpan(new android.text.style.ForegroundColorSpan(e.warn ? Ui.WARN_DARK : Ui.PRIMARY_DARK),
                    0, kind.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, kind.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(span);
            ((TextView) row.findViewById(R.id.log_out)).setText(e.out);
            logBox.addView(row);
        }
    }
}
