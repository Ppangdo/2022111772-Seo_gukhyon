package com.example.myandroidapp;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

// C 화면 "폰에 저장된 동작" 리스트뷰의 어댑터
// 행을 길게 누르면 위쪽 허브 슬롯 칸으로 끌어 넣을 수 있다
public class MotionAdapter extends BaseAdapter {

    public interface OnDelete {
        void onDelete(AppState.Motion m);
    }

    static final String DRAG_LABEL = "motion";

    private final Context context;
    private final AppState s;
    private final OnDelete onDelete;

    public MotionAdapter(Context context, AppState s, OnDelete onDelete) {
        this.context = context;
        this.s = s;
        this.onDelete = onDelete;
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
        row.findViewById(R.id.motion_delete).setOnClickListener(v -> onDelete.onDelete(m));

        // 길게 눌러 끌기 시작
        View card = row.findViewById(R.id.motion_card);
        card.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText(DRAG_LABEL, String.valueOf(m.id));
            v.startDragAndDrop(data, new NameShadow(context, m.name), null, 0);
            return true;
        });
        card.setContentDescription(m.name + ", 길게 눌러 허브 슬롯으로 끌기");
        return row;
    }

    // 끌 때 손가락을 따라오는 동작 이름표
    private static class NameShadow extends View.DragShadowBuilder {
        private final String text;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int w, h, pad;

        NameShadow(Context c, String text) {
            this.text = text;
            paint.setTextSize(Ui.dp(c, 15));
            paint.setFakeBoldText(true);
            pad = Ui.dp(c, 14);
            w = (int) paint.measureText(text) + pad * 2;
            h = Ui.dp(c, 44);
        }

        @Override
        public void onProvideShadowMetrics(Point size, Point touch) {
            size.set(w, h);
            touch.set(w / 2, h / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            paint.setColor(Ui.PRIMARY);
            canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, paint);
            paint.setColor(Ui.WHITE);
            canvas.drawText(text, pad, h / 2f - (paint.descent() + paint.ascent()) / 2, paint);
        }
    }
}
