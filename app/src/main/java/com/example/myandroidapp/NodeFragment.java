package com.example.myandroidapp;

import android.os.Bundle;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;

// A. 노드 설정·보정
public class NodeFragment extends Fragment implements AppState.Listener {

    private static final String[] AXES = {"앞뒤 기울기", "좌우 기울기", "비틀기"};

    private AppState s;
    private View root;
    private BodyView body;
    private TextView figTitle, figHint, btnFront, btnBack;
    private final TextView[] basePills = new TextView[3];
    private TextView btnMeasure, measureStatus;
    private LinearLayout measureBar;
    private TextView nodeCount, btnAddNode, trayEmpty, identMsg;
    private LinearLayout tray, trayList;
    private TextView selName, selSummary;
    private View selHasNode, selNoNode, btnUnplace, confirmBox;
    private final View[] cells = new View[4];
    private final Slider[] sliders = new Slider[3];
    private final TextView[] sliderValues = new TextView[3];
    private TextView corrSummary;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        s = AppState.get(requireContext());
        root = inflater.inflate(R.layout.fragment_node, container, false);

        body = root.findViewById(R.id.body);
        body.setup(s, "A", new BodyView.Callbacks() {
            @Override
            public void onSegmentTap(String seg) {
                s.segClick(seg);
            }

            @Override
            public void onNodeTap(AppState.Node node) {
                s.selectNode(node);
            }
        });
        figTitle = root.findViewById(R.id.fig_title);
        figHint = root.findViewById(R.id.fig_hint);
        btnFront = root.findViewById(R.id.btn_front);
        btnBack = root.findViewById(R.id.btn_back);
        btnFront.setOnClickListener(v -> s.setBack(false));
        btnBack.setOnClickListener(v -> s.setBack(true));

        LinearLayout pills = root.findViewById(R.id.base_pills);
        for (int i = 0; i < 3; i++) {
            TextView p = (TextView) inflater.inflate(R.layout.item_pill, pills, false);
            final String b = AppState.BASES[i];
            p.setText(AppState.baseName(b));
            p.setOnClickListener(v -> s.setBase(b));
            if (i > 0) ((LinearLayout.LayoutParams) p.getLayoutParams()).setMarginStart(Ui.dp(requireContext(), 8));
            pills.addView(p);
            basePills[i] = p;
        }
        btnMeasure = root.findViewById(R.id.btn_measure);
        measureStatus = root.findViewById(R.id.measure_status);
        measureBar = root.findViewById(R.id.measure_bar);
        btnMeasure.setOnClickListener(v -> s.startMeasure());

        nodeCount = root.findViewById(R.id.node_count);
        btnAddNode = root.findViewById(R.id.btn_add_node);
        btnAddNode.setOnClickListener(v -> s.addNode());
        tray = root.findViewById(R.id.tray);
        trayList = root.findViewById(R.id.tray_list);
        trayEmpty = root.findViewById(R.id.tray_empty);
        identMsg = root.findViewById(R.id.ident_msg);
        root.findViewById(R.id.btn_shake).setOnClickListener(v -> s.shake());
        // 모형의 노드를 이 칸으로 끌어오면 배치 해제
        tray.setOnDragListener((v, e) -> {
            if (e.getAction() == DragEvent.ACTION_DROP) {
                s.placeNode(e.getClipData().getItemAt(0).getText().toString(), null);
            }
            return true;
        });

        // 설정 시트: 머리 부분을 누르면 펼치고 접는다
        BottomSheetBehavior<View> sheet = BottomSheetBehavior.from(root.findViewById(R.id.sheet));
        root.findViewById(R.id.sheet_header).setOnClickListener(v -> sheet.setState(
                sheet.getState() == BottomSheetBehavior.STATE_EXPANDED
                        ? BottomSheetBehavior.STATE_COLLAPSED : BottomSheetBehavior.STATE_EXPANDED));
        selSummary = root.findViewById(R.id.sel_summary);

        selName = root.findViewById(R.id.sel_name);
        selHasNode = root.findViewById(R.id.sel_has_node);
        selNoNode = root.findViewById(R.id.sel_no_node);
        cells[0] = root.findViewById(R.id.cell0);
        cells[1] = root.findViewById(R.id.cell1);
        cells[2] = root.findViewById(R.id.cell2);
        cells[3] = root.findViewById(R.id.cell3);
        root.findViewById(R.id.btn_led).setOnClickListener(v -> s.led());
        btnUnplace = root.findViewById(R.id.btn_unplace);
        btnUnplace.setOnClickListener(v -> s.unplaceSelected());

        LinearLayout sliderBox = root.findViewById(R.id.sliders);
        for (int i = 0; i < 3; i++) {
            View row = inflater.inflate(R.layout.item_slider, sliderBox, false);
            ((TextView) row.findViewById(R.id.slider_label)).setText(AXES[i]);
            Slider sl = row.findViewById(R.id.slider);
            sl.setValueFrom(-30);
            sl.setValueTo(30);
            sl.setStepSize(1);
            sl.setContentDescription(AXES[i]);
            final int axis = i;
            sl.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) s.setCorr(axis, (int) value);
            });
            sliders[i] = sl;
            sliderValues[i] = row.findViewById(R.id.slider_value);
            sliderBox.addView(row);
        }
        corrSummary = root.findViewById(R.id.corr_summary);
        confirmBox = root.findViewById(R.id.confirm_box);
        root.findViewById(R.id.btn_reset).setOnClickListener(v -> s.setConfirmReset(true));
        root.findViewById(R.id.btn_do_reset).setOnClickListener(v -> s.resetCorr());
        root.findViewById(R.id.btn_cancel_reset).setOnClickListener(v -> s.setConfirmReset(false));
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
        if ("measure".equals(s.measure)) Ui.bar(measureBar, s.measureProg, Ui.PRIMARY);
    }

    @Override
    public void onStateChanged() {
        Ui.bindNote(root, s);
        boolean lie = "lie".equals(s.base);

        figTitle.setText("신체 모형 · " + AppState.baseName(s.base) + " · 배치 " + s.placedCount() + "/" + s.nodes.size());
        Ui.pill(btnFront, !s.back, 10);
        Ui.pill(btnBack, s.back, 10);
        figHint.setText(lie ? "누운 자세 기준입니다. 모형을 돌려서 보여줍니다."
                : (s.back ? "뒤에서 본 모습입니다. 화면 왼쪽이 사용자의 왼쪽입니다."
                : "앞에서 본 모습입니다. 화면 왼쪽이 사용자의 오른쪽입니다."));
        body.invalidate();

        for (int i = 0; i < 3; i++) Ui.pill(basePills[i], AppState.BASES[i].equals(s.base), 10);
        boolean busy = "count".equals(s.measure) || "measure".equals(s.measure);
        Ui.enable(btnMeasure, !busy);
        String name = AppState.baseName(s.base);
        switch (s.measure) {
            case "count":
                btnMeasure.setText(s.measureN + "초 후 측정");
                measureStatus.setText("사용자가 " + name + "를 취하도록 안내하세요.");
                break;
            case "measure":
                btnMeasure.setText("측정 중");
                measureStatus.setText(s.phoneMode ? "2초 동안 그대로 유지하세요. 폰 센서 방향을 평균내는 중입니다."
                        : "2초 동안 그대로 유지하세요.");
                break;
            case "done":
                btnMeasure.setText("다시 측정");
                measureStatus.setText(name + "로 측정함 · " + s.measuredAt + (s.measuredByPhone ? " · 폰 센서" : ""));
                break;
            default:
                btnMeasure.setText("기준 자세 측정");
                measureStatus.setText("아직 측정하지 않았습니다.");
        }
        measureBar.setVisibility("measure".equals(s.measure) ? View.VISIBLE : View.GONE);
        Ui.bar(measureBar, s.measureProg, Ui.PRIMARY);

        nodeCount.setText("현재 노드 수 " + s.nodes.size() + " · 배치 " + s.placedCount());
        btnAddNode.setText(s.pairing ? "페어링 중" : "노드 추가");
        Ui.enable(btnAddNode, !s.pairing);
        bindTray();
        identMsg.setText(s.identify != null ? s.identMsg : "실물 노드를 흔들면 화면의 해당 노드가 깜빡입니다.");

        bindSelection();
    }

    private void bindTray() {
        trayList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int count = 0;
        for (AppState.Node n : s.nodes) {
            if (n.seg != null) continue;
            count++;
            View chip = inflater.inflate(R.layout.item_tray_chip, trayList, false);
            boolean active = n.id.equals(s.placing);
            boolean ident = n.id.equals(s.identify);
            chip.setBackground(active || ident
                    ? Ui.round(requireContext(), Ui.PRIMARY_SOFT, Ui.PRIMARY, 2, 999)
                    : Ui.round(requireContext(), Ui.SURFACE, Ui.BTN_LINE, 1, 999));
            TextView dot = chip.findViewById(R.id.chip_dot);
            dot.setText(n.id);
            dot.setBackground(Ui.round(requireContext(), n.hub ? Ui.HUB : (n.warn() ? Ui.WARN_STRONG : Ui.PRIMARY), 0, 0, 999));
            ((TextView) chip.findViewById(R.id.chip_name)).setText("노드 " + n.id);
            TextView status = chip.findViewById(R.id.chip_status);
            status.setText(n.weak() ? "⚠ 연결 불량" : (n.lowBat() ? "⚠ 배터리 부족" : "배터리 " + n.bat + "%"));
            status.setTextColor(n.warn() ? Ui.WARN_DARK : Ui.MUTED);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chip.getLayoutParams();
            lp.bottomMargin = 0;
            lp.setMarginEnd(Ui.dp(requireContext(), 8));
            final AppState.Node node = n;
            chip.setOnClickListener(v -> s.togglePlacing(node.id));
            chip.setOnLongClickListener(v -> {
                BodyView.startNodeDrag(v, node);
                return true;
            });
            chip.setContentDescription("노드 " + n.id + ", 길게 눌러 끌기");
            trayList.addView(chip);
        }
        trayEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void bindSelection() {
        selName.setText(AppState.SEGN.get(s.selSeg));
        AppState.Node n = s.nodeAt(s.selSeg);
        selHasNode.setVisibility(n != null ? View.VISIBLE : View.GONE);
        selNoNode.setVisibility(n == null ? View.VISIBLE : View.GONE);
        selSummary.setText(n == null ? "노드 없음"
                : (n.warn() ? "⚠ " : "") + "노드 " + n.id + (n.hub ? " (허브)" : "") + " · 배터리 " + n.bat + "%" + (n.weak() ? " · 연결 불량" : ""));
        selSummary.setTextColor(n != null && n.warn() ? Ui.WARN_DARK : Ui.MUTED);
        if (n != null) {
            setCell(0, "노드 번호", n.hub ? n.id + " (허브)" : n.id, false);
            setCell(1, "배터리", n.bat + "%" + (n.lowBat() ? " 부족" : ""), n.lowBat());
            setCell(2, "연결 상태", n.weak() ? "불량" : "양호", n.weak());
            setCell(3, "신호 세기", n.weak() ? "약함" : "강함", false);
            btnUnplace.setVisibility(n.hub ? View.GONE : View.VISIBLE);
        }

        int[] cur = s.corrOf(s.selSeg);
        for (int i = 0; i < 3; i++) {
            if (sliders[i].getValue() != cur[i]) sliders[i].setValue(cur[i]);
            sliderValues[i].setText((cur[i] > 0 ? "+" : "") + cur[i] + "°");
        }
        int corrCount = s.corrCount();
        corrSummary.setText(corrCount > 0 ? "보정된 부위 " + corrCount + "곳" : "보정값 없음");
        confirmBox.setVisibility(s.confirmReset ? View.VISIBLE : View.GONE);
    }

    private void setCell(int i, String label, String value, boolean warn) {
        View c = cells[i];
        c.setBackground(Ui.round(requireContext(), warn ? Ui.WARN_SOFT : Ui.CELL, 0, 0, 10));
        ((TextView) c.findViewById(R.id.cell_label)).setText(label);
        TextView v = c.findViewById(R.id.cell_value);
        v.setText(value);
        v.setTextColor(warn ? Ui.WARN_DEEP : Ui.INK);
    }
}
