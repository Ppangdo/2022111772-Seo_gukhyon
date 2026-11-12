package com.example.myandroidapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

// C. 동작 목록: 위쪽(헤더)은 허브 슬롯, 아래 리스트뷰는 폰에 저장된 동작
public class MotionListFragment extends Fragment implements AppState.Listener {

    private AppState s;
    private View header;
    private MotionAdapter adapter;
    private TextView syncText, syncBase, btnSend, pickText;
    private View pickBox;
    private LinearLayout slotsBox;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        s = AppState.get(requireContext());
        ListView list = (ListView) inflater.inflate(R.layout.fragment_list, container, false);
        header = inflater.inflate(R.layout.list_header_c, list, false);
        list.addHeaderView(header, null, false);
        adapter = new MotionAdapter(requireContext(), s);
        list.setAdapter(adapter);

        syncText = header.findViewById(R.id.sync_text);
        syncBase = header.findViewById(R.id.sync_base);
        btnSend = header.findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> s.sendNow());
        pickBox = header.findViewById(R.id.pick_box);
        pickText = header.findViewById(R.id.pick_text);
        header.findViewById(R.id.btn_cancel_pick).setOnClickListener(v -> s.cancelPick());
        slotsBox = header.findViewById(R.id.slots);
        header.findViewById(R.id.btn_new).setOnClickListener(v -> s.newAction());
        return list;
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

    @Override
    public void onStateChanged() {
        Ui.bindNote(header, s);
        if ("synced".equals(s.sync)) syncText.setText("허브와 동기화됨");
        else if ("sending".equals(s.sync)) syncText.setText("허브로 보내는 중");
        else syncText.setText("바뀐 내용이 아직 허브에 반영되지 않았습니다");
        syncText.setTextColor("pending".equals(s.sync) ? Ui.WARN_DARK : Ui.OK_DARK);
        syncBase.setText("현재 기준 자세: " + AppState.baseName(s.base));
        btnSend.setVisibility("pending".equals(s.sync) ? View.VISIBLE : View.GONE);

        AppState.Motion pick = s.findAction(s.pickFor);
        pickBox.setVisibility(pick != null ? View.VISIBLE : View.GONE);
        if (pick != null) pickText.setText("\"" + pick.name + "\"을 넣을 슬롯을 고르세요. 빠지는 동작은 폰에 남습니다.");

        bindSlots(pick != null);
        adapter.notifyDataSetChanged();
    }

    private void bindSlots(boolean picking) {
        slotsBox.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            final AppState.Motion a = s.hubAt(i);
            View v = inflater.inflate(R.layout.item_slot, slotsBox, false);
            if (picking) v.setBackground(Ui.round(requireContext(), 0xFFF7F9FE, Ui.PRIMARY, 2, 12));
            else if (a != null) v.setBackground(Ui.round(requireContext(), Ui.WHITE, 0xFFDCE1E8, 1, 12));
            else v.setBackground(Ui.round(requireContext(), Ui.SURFACE2, 0xFFC3CAD6, 1, 12));

            ((TextView) v.findViewById(R.id.slot_num)).setText("슬롯 " + (i + 1));
            TextView type = v.findViewById(R.id.slot_type);
            v.findViewById(R.id.slot_empty).setVisibility(a == null ? View.VISIBLE : View.GONE);
            int vis = a != null ? View.VISIBLE : View.GONE;
            type.setVisibility(vis);
            v.findViewById(R.id.slot_name).setVisibility(vis);
            v.findViewById(R.id.slot_meta).setVisibility(vis);
            v.findViewById(R.id.slot_actions).setVisibility(vis);
            TextView mismatch = v.findViewById(R.id.slot_mismatch);
            mismatch.setVisibility(View.GONE);

            if (a != null) {
                Ui.typeBadge(type, a.type);
                ((TextView) v.findViewById(R.id.slot_name)).setText(a.name);
                ((TextView) v.findViewById(R.id.slot_meta)).setText(
                        "노드 " + s.nodesText(a.nodes) + " · 기록 " + a.takes + "회 · 소리 모듈 트랙 " + (i + 1));
                if (!a.base.equals(s.base)) {
                    mismatch.setVisibility(View.VISIBLE);
                    mismatch.setText("⚠ 녹화 기준 " + AppState.baseName(a.base) + " · 현재 " + AppState.baseName(s.base));
                }
                v.findViewById(R.id.slot_add).setOnClickListener(x -> s.addTakes(a));
                v.findViewById(R.id.slot_test).setOnClickListener(x -> s.go("D"));
                v.findViewById(R.id.slot_out).setOnClickListener(x -> s.go("E"));
                v.findViewById(R.id.slot_remove).setOnClickListener(x -> s.removeFromSlot(slot));
            }

            TextView pickBtn = v.findViewById(R.id.slot_pick);
            pickBtn.setVisibility(picking ? View.VISIBLE : View.GONE);
            pickBtn.setText(a != null ? "이 자리와 교체" : "이 자리에 넣기");
            pickBtn.setOnClickListener(x -> s.pickSlot(slot));
            slotsBox.addView(v);
        }
    }
}
