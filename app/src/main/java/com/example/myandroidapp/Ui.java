package com.example.myandroidapp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

// 화면들이 같이 쓰는 색과 모양 도우미
final class Ui {

    static final int WHITE = 0xFFFFFFFF;
    static final int INK = 0xFF14171F;
    static final int MUTED = 0xFF545B69;
    static final int BTN_LINE = 0xFFC9D0DB;
    static final int PRIMARY = 0xFF2453D6;
    static final int PRIMARY_DARK = 0xFF1A3FA6;
    static final int PRIMARY_SOFT = 0xFFE6ECFB;
    static final int PRIMARY_PALE = 0xFF8EA4DE;
    static final int WARN = 0xFFB4470F;
    static final int WARN_DARK = 0xFF8A3409;
    static final int WARN_DEEP = 0xFF6B2A07;
    static final int WARN_SOFT = 0xFFFCEBDD;
    static final int WARN_ROW = 0xFFFCEFE3;
    static final int OK = 0xFF1C7C4A;
    static final int OK_DARK = 0xFF145C37;
    static final int OK_SOFT = 0xFFE3F3EA;
    static final int REC = 0xFFB42318;
    static final int SURFACE2 = 0xFFF7F8FA;
    static final int CELL = 0xFFF3F5F8;
    static final int DROPPED = 0xFFF5F6F8;
    static final int DROPPED_BAR = 0xFFB8C0CC;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    // 선택된 알약 버튼은 파란 배경, 아니면 흰 배경에 회색 테두리
    static void pill(TextView v, boolean on, float radiusDp) {
        Context c = v.getContext();
        v.setBackground(on ? round(c, PRIMARY, PRIMARY, 1, radiusDp) : round(c, WHITE, BTN_LINE, 1, radiusDp));
        v.setTextColor(on ? WHITE : INK);
    }

    static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.45f);
    }

    static void typeBadge(TextView v, String type) {
        boolean pose = "pose".equals(type);
        v.setText(AppState.typeName(type));
        v.setTextColor(pose ? PRIMARY_DARK : OK_DARK);
        v.setBackground(round(v.getContext(), pose ? PRIMARY_SOFT : OK_SOFT, 0, 0, 999));
    }

    // 막대: 자식 0(채움)과 1(나머지)의 무게로 비율을 표현한다
    static void bar(LinearLayout bar, float pct, int color) {
        pct = Math.max(0, Math.min(100, pct));
        View fill = bar.getChildAt(0);
        View rest = bar.getChildAt(1);
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) rest.getLayoutParams();
        if (fp.weight != pct || rp.weight != 100 - pct) {
            fp.weight = pct;
            rp.weight = 100 - pct;
            bar.requestLayout();
        }
        if (fill.getTag() == null || (int) fill.getTag() != color) {
            fill.setTag(color);
            fill.setBackground(round(bar.getContext(), color, 0, 0, 4));
        }
    }

    // 모든 화면 위쪽의 파란 안내 띠
    static void bindNote(View root, AppState s) {
        View box = root.findViewById(R.id.note_box);
        if (box == null) return;
        box.setVisibility(s.note.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.note_text)).setText(s.note);
        root.findViewById(R.id.note_close).setOnClickListener(v -> s.clearNote());
    }

    static String seconds(float v) {
        return (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + "초";
    }
}
