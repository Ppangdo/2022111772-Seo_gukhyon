package com.example.myandroidapp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

// 화면들이 같이 쓰는 색과 모양 도우미
final class Ui {

    // 색 있는 바탕 위 글자(밝은·어두운 테마 모두 흰색)
    static final int WHITE = 0xFFFFFFFF;

    // 아래 색은 res/values(-night)/colors.xml 에서 읽는다. 밝은·어두운 테마가 바뀌면 화면을 다시 만들면서 init() 이 다시 불린다
    static int SURFACE, INK, MUTED, BTN_LINE, LINE;
    static int PRIMARY, PRIMARY_DARK, PRIMARY_SOFT, PRIMARY_PALE;
    // 경고는 녹화 빨강과 헷갈리지 않게 앰버. 글자에는 WARN_DARK, 흰 글자를 올리는 바탕은 WARN_STRONG
    static int WARN, WARN_STRONG, WARN_DARK, WARN_DEEP, WARN_SOFT, WARN_ROW;
    // 상태가 아닌 것(모드, 동작 유형, 비활성 버튼)
    static int NEUTRAL;
    static int OK, OK_DARK, OK_SOFT, REC, REC_SOFT, HUB;
    static int SURFACE2, CELL, DROPPED, DROPPED_BAR;
    static int BODY_FILL, BODY_CORR, BODY_DROP, BODY_LINE, SEAT, SEAT_FILL;

    private Ui() {
    }

    static void init(Context c) {
        SURFACE = col(c, R.color.cw_surface);
        INK = col(c, R.color.cw_ink);
        MUTED = col(c, R.color.cw_muted);
        BTN_LINE = col(c, R.color.cw_btn_line);
        LINE = col(c, R.color.cw_line);
        PRIMARY = col(c, R.color.cw_primary);
        PRIMARY_DARK = col(c, R.color.cw_primary_dark);
        PRIMARY_SOFT = col(c, R.color.cw_primary_soft);
        PRIMARY_PALE = col(c, R.color.cw_primary_pale);
        WARN = col(c, R.color.cw_warn);
        WARN_STRONG = col(c, R.color.cw_warn_strong);
        WARN_DARK = col(c, R.color.cw_warn_dark);
        WARN_DEEP = col(c, R.color.cw_warn_deep);
        WARN_SOFT = col(c, R.color.cw_warn_soft);
        WARN_ROW = col(c, R.color.cw_warn_row);
        NEUTRAL = col(c, R.color.cw_neutral);
        OK = col(c, R.color.cw_ok);
        OK_DARK = col(c, R.color.cw_ok_dark);
        OK_SOFT = col(c, R.color.cw_ok_soft);
        REC = col(c, R.color.cw_rec);
        REC_SOFT = col(c, R.color.cw_rec_soft);
        HUB = col(c, R.color.cw_hub);
        SURFACE2 = col(c, R.color.cw_surface2);
        CELL = col(c, R.color.cw_cell);
        DROPPED = col(c, R.color.cw_dropped);
        DROPPED_BAR = col(c, R.color.cw_dropped_bar);
        BODY_FILL = col(c, R.color.cw_body_fill);
        BODY_CORR = col(c, R.color.cw_body_corr);
        BODY_DROP = col(c, R.color.cw_body_drop);
        BODY_LINE = col(c, R.color.cw_body_line);
        SEAT = col(c, R.color.cw_seat);
        SEAT_FILL = col(c, R.color.cw_seat_fill);
    }

    private static int col(Context c, int id) {
        return androidx.core.content.ContextCompat.getColor(c, id);
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
        v.setBackground(on ? round(c, PRIMARY, PRIMARY, 1, radiusDp) : round(c, SURFACE, BTN_LINE, 1, radiusDp));
        v.setTextColor(on ? WHITE : INK);
    }

    // 비활성 버튼: 투명도로 흐리게 하면 흰 글자가 안 읽히므로, 회색 바탕 + 진한 회색 글자로 바꾼다.
    // 다시 켜면 원래 배경과 글자색을 되돌린다.
    static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(1f);
        if (!(v instanceof TextView)) {
            v.setAlpha(on ? 1f : 0.5f);
            return;
        }
        TextView t = (TextView) v;
        if (!on) {
            if (t.getTag(R.id.cw_saved_bg) == null) {
                t.setTag(R.id.cw_saved_bg, t.getBackground());
                t.setTag(R.id.cw_saved_text, t.getTextColors());
            }
            t.setBackground(round(t.getContext(), NEUTRAL, NEUTRAL, 1, 12));
            t.setTextColor(MUTED);
        } else if (t.getTag(R.id.cw_saved_bg) != null) {
            t.setBackground((android.graphics.drawable.Drawable) t.getTag(R.id.cw_saved_bg));
            t.setTextColor((android.content.res.ColorStateList) t.getTag(R.id.cw_saved_text));
            t.setTag(R.id.cw_saved_bg, null);
            t.setTag(R.id.cw_saved_text, null);
        }
    }

    // 동작 유형은 상태가 아니라서 회색 바탕 + 기호 + 이름(파랑·초록은 "선택됨", "정상"과 뜻이 겹친다)
    static void typeBadge(TextView v, String type) {
        boolean pose = "pose".equals(type);
        v.setText((pose ? "↑ " : "◎ ") + AppState.typeName(type));
        v.setTextColor(INK);
        v.setBackground(round(v.getContext(), NEUTRAL, 0, 0, 999));
    }

    // 경고가 있는 행: 옅은 앰버 바탕 + 왼쪽 4dp 앰버 띠
    static android.graphics.drawable.Drawable warnRow(Context c, float radiusDp) {
        GradientDrawable strip = round(c, WARN, 0, 0, radiusDp);
        GradientDrawable body = round(c, WARN_ROW, 0, 0, radiusDp);
        android.graphics.drawable.LayerDrawable d = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{strip, body});
        d.setLayerInset(1, dp(c, 5), 0, 0, 0);
        return d;
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
