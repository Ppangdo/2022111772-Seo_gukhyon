package com.example.myandroidapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

// C 화면 "폰에 저장된 동작" 리스트뷰의 어댑터
public class MotionAdapter extends BaseAdapter {

    private final Context context;
    private final AppState s;

    public MotionAdapter(Context context, AppState s) {
        this.context = context;
        this.s = s;
    }

    @Override
    public int getCount() {
        return s.actions.size();
    }

    @Override
    public AppState.Motion getItem(int position) {
        return s.actions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView != null ? convertView
                : LayoutInflater.from(context).inflate(R.layout.item_motion, parent, false);
        final AppState.Motion m = getItem(position);

        ((TextView) row.findViewById(R.id.motion_name)).setText(m.name);
        Ui.typeBadge(row.findViewById(R.id.motion_type), m.type);
        ((TextView) row.findViewById(R.id.motion_meta)).setText(
                "노드 " + s.nodesText(m.nodes) + " · 기록 " + m.takes + "회 · 녹화 기준 " + AppState.baseName(m.base));

        TextView mismatch = row.findViewById(R.id.motion_mismatch);
        boolean diff = !m.base.equals(s.base);
        mismatch.setVisibility(diff ? View.VISIBLE : View.GONE);
        mismatch.setText("⚠ 현재 기준 자세(" + AppState.baseName(s.base) + ")와 다릅니다");

        TextView hub = row.findViewById(R.id.motion_hub);
        View buttons = row.findViewById(R.id.motion_buttons);
        boolean inHub = m.hubSlot >= 0;
        hub.setVisibility(inHub ? View.VISIBLE : View.GONE);
        buttons.setVisibility(inHub ? View.GONE : View.VISIBLE);
        if (inHub) {
            hub.setText("허브 슬롯 " + (m.hubSlot + 1));
            hub.setBackground(Ui.round(context, Ui.PRIMARY_SOFT, 0, 0, 999));
        }
        row.findViewById(R.id.motion_insert).setOnClickListener(v -> s.insertToHub(m));
        row.findViewById(R.id.motion_delete).setOnClickListener(v ->
                new AlertDialog.Builder(context)
                        .setTitle("동작 삭제")
                        .setMessage("\"" + m.name + "\"을 폰에서 삭제할까요? 녹화 기록도 함께 지워집니다.")
                        .setPositiveButton("삭제", (d, w) -> s.deleteAction(m))
                        .setNegativeButton("취소", null)
                        .show());
        return row;
    }
}
