package com.example.myandroidapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;

import java.util.Locale;

// D. 실시간 테스트
// 흉내 버튼을 누르면서 모형과 판정 막대를 한 화면에서 같이 본다. 민감도와 발동 기록은 아래 시트.
public class LiveTestFragment extends Fragment implements AppState.Listener {

    private static final String[][] SIM_MODES = {
            {"arm", "오른팔 들기"}, {"lean", "허리 굽히기"}, {"busy", "평소 움직임"}, {"idle", "가만히"}};

    private static class LiveRow {
        AppState.Motion motion;
        View thr, sensRow;
        LinearLayout bar;
        TextView state, sim, sensValue;
    }

    private AppState s;
    private View root;
    private BodyView body;
    private BottomSheetBehavior<View> sheet;
    private NestedScrollView sheetScroll;
    private TextView readout, logEmpty, lastFire, logCount;
    private LinearLayout simButtons, liveBox, sensList, logBox;
    private CheckBox sendOut;
    private final LiveRow[] rows = new LiveRow[3];
    private String hubSignature = "";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        s = AppState.get(requireContext());
        root = inflater.inflate(R.layout.fragment_test, container, false);
        readout = root.findViewById(R.id.readout);
        body = root.findViewById(R.id.body);
        body.setup(s, "D", null);

        simButtons = root.findViewById(R.id.sim_buttons);
        for (String[] m : SIM_MODES) {
            TextView p = (TextView) inflater.inflate(R.layout.item_pill, simButtons, false);
            p.setTextSize(14);
            p.setPadding(Ui.dp(requireContext(), 12), 0, Ui.dp(requireContext(), 12), 0);
            p.setText(m[1]);
            p.setTag(m[0]);
            final String mode = m[0];
            p.setOnClickListener(v -> s.setSimMode(mode));
            ((LinearLayout.LayoutParams) p.getLayoutParams()).setMarginEnd(Ui.dp(requireContext(), 8));
            simButtons.addView(p);
        }
        liveBox = root.findViewById(R.id.live);
        lastFire = root.findViewById(R.id.last_fire);
        logCount = root.findViewById(R.id.log_count);

        sheet = BottomSheetBehavior.from(root.findViewById(R.id.sheet));
        sheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        sheetScroll = root.findViewById(R.id.sheet_scroll);
        root.findViewById(R.id.btn_close_sheet).setOnClickListener(v -> sheet.setState(BottomSheetBehavior.STATE_HIDDEN));
        root.findViewById(R.id.sheet_header).setOnClickListener(v -> sheet.setState(BottomSheetBehavior.STATE_HIDDEN));
        root.findViewById(R.id.last_row).setOnClickListener(v -> openSheetAt(root.findViewById(R.id.log_card)));

        sensList = root.findViewById(R.id.sens_list);
        sendOut = root.findViewById(R.id.send_out);
        sendOut.setOnCheckedChangeListener((b, checked) -> {
            if (b.isPressed()) s.setSendOut(checked);
        });
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

    // 시트를 펼치고 target 이 보이도록 스크롤한다
    private void openSheetAt(View target) {
        sheet.setState(BottomSheetBehavior.STATE_EXPANDED);
        sheetScroll.post(() -> {
            int y = 0;
            for (View v = target; v != null && v != sheetScroll; v = (View) v.getParent()) y += v.getTop();
            sheetScroll.smoothScrollTo(0, Math.max(0, y - Ui.dp(requireContext(), 12)));
        });
    }

    @Override
    public void onStateChanged() {
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
        bindLastFire();
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
                    : (bad ? String.format(Locale.US, "이탈 %.1f/1.5초", sec) : "바른 자세"));
            r.state.setTextColor(over ? Ui.PRIMARY_DARK : (bad ? Ui.WARN_DARK : (pose ? Ui.MUTED : Ui.OK_DARK)));
            Ui.bar(r.bar, v, pose ? (over ? Ui.PRIMARY : Ui.PRIMARY_PALE) : (bad ? Ui.WARN : Ui.OK));
            r.sim.setText(v + "% · 기준 " + a.sens + "%");
            r.sensValue.setText(a.sens + "%");
            int w = r.bar.getWidth();
            if (w > 0) r.thr.setTranslationX(w * a.sens / 100f - r.thr.getWidth() / 2f);
        }
    }

    private void buildRows() {
        liveBox.removeAllViews();
        sensList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < 3; i++) {
            LiveRow r = new LiveRow();
            r.motion = s.hubAt(i);
            View view = inflater.inflate(R.layout.item_live, liveBox, false);
            view.setBackground(Ui.round(requireContext(), Ui.SURFACE2, 0, 0, 10));
            ((TextView) view.findViewById(R.id.live_num)).setText(String.valueOf(i + 1));
            TextView name = view.findViewById(R.id.live_name);
            TextView type = view.findViewById(R.id.live_type);
            r.state = view.findViewById(R.id.live_state);
            r.sim = view.findViewById(R.id.live_sim);
            if (r.motion == null) {
                name.setText("비어 있음");
                name.setTextColor(Ui.MUTED);
                type.setVisibility(View.GONE);
                view.findViewById(R.id.live_track).setVisibility(View.GONE);
                r.sim.setText("C 목록에서 동작을 넣으세요");
                view.setClickable(false);
            } else {
                final AppState.Motion a = r.motion;
                name.setText(a.name);
                Ui.typeBadge(type, a.type);
                r.bar = view.findViewById(R.id.live_bar);
                r.thr = view.findViewById(R.id.live_thr);
                r.sensRow = buildSensRow(inflater, i, a, r);
                view.setContentDescription("슬롯 " + (i + 1) + " " + a.name + ", 눌러서 민감도 바꾸기");
                final View target = r.sensRow;
                view.setOnClickListener(v -> openSheetAt(target));
            }
            rows[i] = r;
            liveBox.addView(view);
        }
    }

    // 시트 안의 슬롯별 민감도 슬라이더
    private View buildSensRow(LayoutInflater inflater, int slot, AppState.Motion a, LiveRow r) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(requireContext(), 10), 0, 0);
        TextView title = new TextView(requireContext());
        title.setText("슬롯 " + (slot + 1) + " · " + a.name + " (" + AppState.typeName(a.type) + ")");
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Ui.INK);
        box.addView(title);

        View row = inflater.inflate(R.layout.item_slider, box, false);
        ((TextView) row.findViewById(R.id.slider_label)).setText(a.isPose() ? "발동 기준" : "이탈 기준");
        r.sensValue = row.findViewById(R.id.slider_value);
        Slider sl = row.findViewById(R.id.slider);
        sl.setValueFrom(50);
        sl.setValueTo(98);
        sl.setStepSize(1);
        sl.setValue(Math.max(50, Math.min(98, a.sens)));
        sl.setContentDescription(a.name + " 인식 민감도");
        sl.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) s.setSens(a, (int) value);
        });
        box.addView(row);
        sensList.addView(box);
        return box;
    }

    private void bindLastFire() {
        int n = s.log.size();
        logCount.setText(n > 0 ? "기록 " + n + " ›" : "기록 ›");
        if (n == 0) {
            lastFire.setText("아직 발동 없음 · 흉내 버튼을 눌러 보세요");
            lastFire.setTextColor(Ui.MUTED);
            return;
        }
        AppState.LogEntry e = s.log.get(0);
        lastFire.setTextColor(Ui.INK);
        lastFire.setText(logText(e, true));
    }

    private CharSequence logText(AppState.LogEntry e, boolean withTime) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (withTime) sb.append(e.time).append("  ");
        int start = sb.length();
        sb.append(e.kind);
        sb.setSpan(new ForegroundColorSpan(e.warn ? Ui.WARN_DARK : Ui.PRIMARY_DARK), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" · ").append(e.name);
        if (withTime) sb.append(" · ").append(e.out);
        return sb;
    }

    private void bindLog() {
        logBox.removeAllViews();
        logEmpty.setVisibility(s.log.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AppState.LogEntry e : s.log) {
            View row = inflater.inflate(R.layout.item_log, logBox, false);
            ((TextView) row.findViewById(R.id.log_time)).setText(e.time);
            ((TextView) row.findViewById(R.id.log_text)).setText(logText(e, false));
            ((TextView) row.findViewById(R.id.log_out)).setText(e.out);
            logBox.addView(row);
        }
    }
}
