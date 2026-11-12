package com.example.myandroidapp;

import android.os.Bundle;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

// C. 동작 목록
// 위쪽 허브 슬롯 3칸은 고정, 아래 리스트뷰는 폰에 저장된 동작. 목록 행을 길게 눌러 슬롯 칸으로 끌어 넣는다.
public class MotionListFragment extends Fragment implements AppState.Listener {

    private AppState s;
    private View root;
    private MotionAdapter adapter;
    private BottomSheetBehavior<View> sheet;
    private TextView syncText, syncBase, btnSend, pickText, slotsHint;
    private View pickBox, slotDetail;
    private LinearLayout slotsBox;
    private int dragOverSlot = -1;
    private int detailSlot = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        s = AppState.get(requireContext());
        root = inflater.inflate(R.layout.fragment_list, container, false);

        ListView list = root.findViewById(R.id.list);
        adapter = new MotionAdapter(requireContext(), s, this::deleteWithUndo);
        list.setAdapter(adapter);

        syncText = root.findViewById(R.id.sync_text);
        syncBase = root.findViewById(R.id.sync_base);
        btnSend = root.findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> s.sendNow());
        pickBox = root.findViewById(R.id.pick_box);
        pickText = root.findViewById(R.id.pick_text);
        root.findViewById(R.id.btn_cancel_pick).setOnClickListener(v -> s.cancelPick());
        slotsBox = root.findViewById(R.id.slots);
        slotsHint = root.findViewById(R.id.slots_hint);
        root.findViewById(R.id.btn_new).setOnClickListener(v -> s.newAction());

        sheet = BottomSheetBehavior.from(root.findViewById(R.id.sheet));
        sheet.setState(BottomSheetBehavior.STATE_HIDDEN);
        root.findViewById(R.id.btn_close_sheet).setOnClickListener(v -> closeDetail());
        root.findViewById(R.id.sheet_header).setOnClickListener(v -> closeDetail());
        slotDetail = root.findViewById(R.id.slot_detail);
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
    }

    // 삭제는 확인 창 대신 바로 지우고 "되돌리기"를 준다
    private void deleteWithUndo(AppState.Motion m) {
        s.deleteAction(m);
        // 보조자가 사용자를 돌보다가도 되돌릴 수 있게 6초 동안 보여준다
        Snackbar.make(root.findViewById(R.id.coordinator), "\"" + m.name + "\"을 삭제했습니다", 6000)
                .setAction("되돌리기", v -> s.undoDelete())
                .setActionTextColor(0xFF9DB5F5)
                .show();
    }

    private void closeDetail() {
        detailSlot = -1;
        sheet.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    @Override
    public void onStateChanged() {
        Ui.bindNote(root, s);
        if ("synced".equals(s.sync)) syncText.setText("허브와 동기화됨");
        else if ("sending".equals(s.sync)) syncText.setText("허브로 보내는 중");
        else syncText.setText("바뀐 내용이 허브에 아직 반영 안 됨");
        syncText.setTextColor("pending".equals(s.sync) ? Ui.WARN_DARK : Ui.OK_DARK);
        syncBase.setText("현재 기준 자세: " + AppState.baseName(s.base));
        btnSend.setVisibility("pending".equals(s.sync) ? View.VISIBLE : View.GONE);

        AppState.Motion pick = s.findAction(s.pickFor);
        pickBox.setVisibility(pick != null ? View.VISIBLE : View.GONE);
        if (pick != null) pickText.setText("\"" + pick.name + "\"을 넣을 슬롯을 위에서 고르세요. 빠지는 동작은 폰에 남습니다.");
        slotsHint.setText(pick != null ? "넣을 칸을 누르세요"
                : "목록의 동작을 길게 눌러 칸으로 끌면 넣거나 바꿉니다 · 칸을 누르면 상세");

        bindSlots(pick != null);
        if (detailSlot >= 0) bindDetail(detailSlot);
        adapter.notifyDataSetChanged();
    }

    private void bindSlots(boolean picking) {
        slotsBox.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            final AppState.Motion a = s.hubAt(i);
            View v = inflater.inflate(R.layout.item_slot_mini, slotsBox, false);
            if (i > 0) ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(Ui.dp(requireContext(), 8));
            styleSlot(v, a, picking, false);

            ((TextView) v.findViewById(R.id.mini_num)).setText("슬롯 " + (i + 1) + " · 트랙 " + (i + 1));
            TextView name = v.findViewById(R.id.mini_name);
            TextView type = v.findViewById(R.id.mini_type);
            if (a != null) {
                name.setText(a.name);
                name.setTextColor(Ui.INK);
                Ui.typeBadge(type, a.type);
                type.setVisibility(View.VISIBLE);
            } else {
                name.setText(picking ? "이 칸에 넣기" : "비어 있음");
                name.setTextColor(picking ? Ui.PRIMARY : Ui.MUTED);
                type.setVisibility(View.GONE);
            }
            v.setContentDescription("허브 슬롯 " + (i + 1) + ", " + (a != null ? a.name : "비어 있음"));

            v.setOnClickListener(x -> {
                if (s.pickFor != null) {
                    s.pickSlot(slot);
                } else if (a != null) {
                    detailSlot = slot;
                    bindDetail(slot);
                    sheet.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else {
                    s.showNote("목록에서 동작을 길게 눌러 슬롯 " + (slot + 1) + " 칸으로 끌거나, \"허브에 넣기\"를 누르세요.");
                }
            });

            // 목록에서 끌어온 동작을 받는다
            v.setOnDragListener((view, e) -> {
                switch (e.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return e.getClipDescription() != null
                                && MotionAdapter.DRAG_LABEL.contentEquals(e.getClipDescription().getLabel());
                    case DragEvent.ACTION_DRAG_ENTERED:
                        dragOverSlot = slot;
                        styleSlot(view, a, false, true);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        dragOverSlot = -1;
                        styleSlot(view, a, false, false);
                        return true;
                    case DragEvent.ACTION_DROP:
                        long id = Long.parseLong(e.getClipData().getItemAt(0).getText().toString());
                        AppState.Motion m = s.findAction(id);
                        if (m != null) s.placeInSlot(m, slot);
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        if (dragOverSlot == slot) dragOverSlot = -1;
                        styleSlot(view, s.hubAt(slot), s.pickFor != null, false);
                        return true;
                }
                return true;
            });
            slotsBox.addView(v);
        }
    }

    private void styleSlot(View v, AppState.Motion a, boolean picking, boolean over) {
        if (over) v.setBackground(Ui.round(requireContext(), Ui.PRIMARY_SOFT, Ui.PRIMARY, 2, 12));
        else if (picking) v.setBackground(Ui.round(requireContext(), 0xFFF7F9FE, Ui.PRIMARY, 2, 12));
        else if (a != null) v.setBackground(Ui.round(requireContext(), Ui.WHITE, 0xFFDCE1E8, 1, 12));
        else v.setBackground(Ui.round(requireContext(), Ui.SURFACE2, 0xFFC3CAD6, 1, 12));
    }

    // 시트 안의 슬롯 상세
    private void bindDetail(int slot) {
        final AppState.Motion a = s.hubAt(slot);
        if (a == null) {
            closeDetail();
            return;
        }
        View v = slotDetail;
        v.setBackground(Ui.round(requireContext(), Ui.WHITE, 0xFFDCE1E8, 1, 12));
        ((TextView) v.findViewById(R.id.slot_num)).setText("슬롯 " + (slot + 1));
        Ui.typeBadge(v.findViewById(R.id.slot_type), a.type);
        ((TextView) v.findViewById(R.id.slot_name)).setText(a.name);
        ((TextView) v.findViewById(R.id.slot_meta)).setText(
                "노드 " + s.nodesText(a.nodes) + " · 기록 " + a.takes + "회 · 소리 모듈 트랙 " + (slot + 1));
        TextView mismatch = v.findViewById(R.id.slot_mismatch);
        boolean diff = !a.base.equals(s.base);
        mismatch.setVisibility(diff ? View.VISIBLE : View.GONE);
        mismatch.setText("⚠ 녹화 기준 " + AppState.baseName(a.base) + " · 현재 " + AppState.baseName(s.base));
        v.findViewById(R.id.slot_empty).setVisibility(View.GONE);
        v.findViewById(R.id.slot_pick).setVisibility(View.GONE);
        v.findViewById(R.id.slot_add).setOnClickListener(x -> {
            closeDetail();
            s.addTakes(a);
        });
        v.findViewById(R.id.slot_test).setOnClickListener(x -> {
            closeDetail();
            s.go("D");
        });
        v.findViewById(R.id.slot_out).setOnClickListener(x -> {
            closeDetail();
            s.go("E");
        });
        v.findViewById(R.id.slot_remove).setOnClickListener(x -> {
            closeDetail();
            s.removeFromSlot(slot);
        });
    }
}
