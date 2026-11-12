package com.example.myandroidapp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

// 신체 모형. 목업과 같은 360×640 좌표로 부위를 계산한 뒤 뷰 크기에 맞춰 그린다.
// A 화면: 부위·노드 누르기, 노드 길게 눌러 끌기. B·D 화면: 움직임만 보여준다.
public class BodyView extends View {

    public interface Callbacks {
        void onSegmentTap(String seg);

        void onNodeTap(AppState.Node node);
    }

    private static final float W = 360, H = 640;
    // 팔을 머리 위로 들면 손이 모형 좌표 위쪽(음수)까지 올라가서, 그만큼 여백을 둔다
    private static final float TOP = 90;

    private static class Seg {
        String k;
        float x, y, w, h, t, r;
        boolean top;     // true 면 위쪽 가운데를 축으로 회전(팔·다리)
        boolean oval;
    }

    private AppState s;
    private String mode = "A";
    private Callbacks callbacks;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Matrix inverse = new Matrix();
    private List<Seg> segs = new ArrayList<>();
    private float scale = 1, offX, offY;

    private float downX, downY;
    private AppState.Node downNode;
    private boolean longPressed;
    private final int touchSlop;
    private final Runnable longPress = this::startNodeDrag;

    public BodyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        stroke.setStyle(Paint.Style.STROKE);
        seat.setStyle(Paint.Style.STROKE);
        seat.setColor(0xFFB8C0CC);
        seat.setPathEffect(new DashPathEffect(new float[]{6, 4}, 0));
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setOnDragListener(this::onDrag);
    }

    public void setup(AppState state, String mode, Callbacks callbacks) {
        this.s = state;
        this.mode = mode;
        this.callbacks = callbacks;
        invalidate();
    }

    // ---------- 부위 계산 (목업 geom() 과 같은 값) ----------

    private void add(String k, float x, float y, float w, float h, float t, boolean top, float r) {
        Seg g = new Seg();
        g.k = k;
        g.x = x;
        g.y = y;
        g.w = w;
        g.h = h;
        g.t = t;
        g.top = top;
        g.r = r;
        g.oval = r < 0;
        segs.add(g);
    }

    private float roll(String k) {
        return s.corrOf(k)[1];
    }

    // 위쪽 가운데(sx, sy)에서 t도 기울어진 길이 L 의 팔다리를 놓고, 끝점을 돌려준다
    private float[] limb(String k, float sx, float sy, float t, float len, float w, float r) {
        add(k, sx - w / 2, sy, w, len, t, true, r);
        double a = Math.toRadians(t);
        return new float[]{(float) (sx - len * Math.sin(a)), (float) (sy + len * Math.cos(a))};
    }

    private void geom() {
        segs = new ArrayList<>();
        float dy = s.lean * 16;
        float thigh = "sit".equals(s.base) ? 54 : 130;
        float t = roll("rth");
        float[] p = limb("rth", 156, 300, t, thigh, 46, 16);
        t += roll("rsh");
        p = limb("rsh", p[0], p[1], t, 122, 38, 14);
        limb("rf", p[0], p[1], t + roll("rf"), 24, 54, 10);
        t = roll("lth");
        p = limb("lth", 204, 300, t, thigh, 46, 16);
        t += roll("lsh");
        p = limb("lsh", p[0], p[1], t, 122, 38, 14);
        limb("lf", p[0], p[1], t + roll("lf"), 24, 54, 10);
        add("pelvis", 128, 244, 104, 64, roll("pelvis"), false, 16);
        add("chest", 124, 112 + dy * 0.8f, 112, 124 - dy * 0.8f, roll("chest"), false, 18);
        add("head", 142, 22 + dy, 76, 84, roll("head"), false, -1);
        float sy = 124 + dy * 0.8f;
        float tu = s.armR + roll("rua");
        p = limb("rua", 108, sy, tu, 104, 34, 14);
        float tl = tu + s.elbowR + roll("rla");
        p = limb("rla", p[0], p[1], tl, 96, 30, 13);
        limb("rh", p[0], p[1], tl + roll("rh"), 40, 34, 12);
        tu = -s.armL + roll("lua");
        p = limb("lua", 252, sy, tu, 104, 34, 14);
        tl = tu - s.elbowL + roll("lla");
        p = limb("lla", p[0], p[1], tl, 96, 30, 13);
        limb("lh", p[0], p[1], tl + roll("lh"), 40, 34, 12);
    }

    private float[] center(Seg g) {
        if (g.top) {
            double a = Math.toRadians(g.t);
            return new float[]{(float) (g.x + g.w / 2 - (g.h / 2) * Math.sin(a)), (float) (g.y + (g.h / 2) * Math.cos(a))};
        }
        return new float[]{g.x + g.w / 2, g.y + g.h / 2};
    }

    private Seg segByKey(String k) {
        for (Seg g : segs) if (g.k.equals(k)) return g;
        return null;
    }

    // 모형 좌표 → 화면 좌표. 누운 자세는 -90도 돌려 0.55배, 뒤에서 보기는 좌우 반전.
    private void updateMatrix() {
        scale = Math.min(getWidth() / W, getHeight() / (H + TOP));
        offX = (getWidth() - W * scale) / 2;
        offY = (getHeight() - (H + TOP) * scale) / 2 + TOP * scale;
        matrix.reset();
        matrix.postTranslate(-W / 2, -H / 2);
        if (s.back) matrix.postScale(-1, 1);
        if ("lie".equals(s.base)) {
            matrix.postScale(0.55f, 0.55f);
            matrix.postRotate(-90);
        }
        matrix.postTranslate(W / 2, H / 2);
        matrix.postScale(scale, scale);
        matrix.postTranslate(offX, offY);
        matrix.invert(inverse);
    }

    // ---------- 그리기 ----------

    @Override
    protected void onDraw(Canvas canvas) {
        if (s == null) return;
        geom();
        updateMatrix();
        boolean isA = "A".equals(mode);
        float dpx = getResources().getDisplayMetrics().density;

        if (!"lie".equals(s.base)) {
            text.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
            text.setColor(Ui.MUTED);
            text.setTextAlign(Paint.Align.LEFT);
            float labelY = offY - TOP * scale + 14 * dpx;
            canvas.drawText(s.back ? "L 왼쪽" : "R 오른쪽", offX, labelY, text);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(s.back ? "R 오른쪽" : "L 왼쪽", offX + W * scale, labelY, text);
            text.setTextAlign(Paint.Align.CENTER);
        }

        canvas.save();
        canvas.concat(matrix);
        if ("sit".equals(s.base)) {
            RectF r = new RectF(96, 290, 264, 366);
            fill.setColor(0xFFE4E8EE);
            canvas.drawRoundRect(r, 12, 12, fill);
            seat.setStrokeWidth(1);
            canvas.drawRoundRect(r, 12, 12, seat);
        }
        for (Seg g : segs) {
            boolean corr = s.hasCorr(g.k);
            boolean sel = isA && g.k.equals(s.selSeg);
            fill.setColor(g.k.equals(s.dragOver) ? 0xFFD6E1FA : (corr ? 0xFFE4E9F2 : 0xFFE9EDF3));
            stroke.setColor(sel ? Ui.PRIMARY : (corr ? 0xFF7C93D9 : 0xFFC3CAD6));
            stroke.setStrokeWidth(sel ? 3 : 2);
            canvas.save();
            RectF r;
            if (g.top) {
                canvas.translate(g.x + g.w / 2, g.y);
                canvas.rotate(g.t);
                r = new RectF(-g.w / 2, 0, g.w / 2, g.h);
            } else {
                canvas.translate(g.x + g.w / 2, g.y + g.h / 2);
                canvas.rotate(g.t);
                r = new RectF(-g.w / 2, -g.h / 2, g.w / 2, g.h / 2);
            }
            if (g.oval) {
                canvas.drawOval(r, fill);
                canvas.drawOval(r, stroke);
            } else {
                canvas.drawRoundRect(r, g.r, g.r, fill);
                canvas.drawRoundRect(r, g.r, g.r, stroke);
            }
            canvas.restore();
        }
        canvas.restore();

        // 노드는 회전하지 않은 화면 좌표에 그려 번호가 늘 바로 보이게 한다
        float radius = Math.max(16 * scale, 12 * dpx);
        long now = System.currentTimeMillis();
        boolean animating = false;
        for (AppState.Node n : s.nodes) {
            if (n.seg == null) continue;
            float[] c = mapNode(n);
            if (c == null) continue;
            boolean dim = "B".equals(mode) && !s.rec.nodes.contains(n.id);
            boolean sel = isA && n.seg.equals(s.selSeg);
            int alpha = dim ? 77 : 255;

            if (n.id.equals(s.identify)) {
                float ph = (now % 700) / 700f;
                dot.setStyle(Paint.Style.FILL);
                dot.setColor(Ui.PRIMARY);
                dot.setAlpha((int) (180 * (1 - ph)));
                canvas.drawCircle(c[0], c[1], radius + 14 * dpx * ph, dot);
                animating = true;
            }
            if (sel) {
                dot.setStyle(Paint.Style.FILL);
                dot.setColor(Ui.INK);
                dot.setAlpha(255);
                canvas.drawCircle(c[0], c[1], radius + 3 * dpx, dot);
            }
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(n.hub ? Ui.INK : (n.warn() ? Ui.WARN : Ui.PRIMARY));
            dot.setAlpha(alpha);
            canvas.drawCircle(c[0], c[1], radius, dot);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(2 * dpx);
            dot.setColor(Ui.WHITE);
            dot.setAlpha(alpha);
            canvas.drawCircle(c[0], c[1], radius - dpx, dot);

            text.setColor(Ui.WHITE);
            text.setAlpha(alpha);
            text.setTextSize(Math.max(12 * scale, 10 * dpx));
            canvas.drawText(n.id, c[0], c[1] - (text.descent() + text.ascent()) / 2, text);
            text.setAlpha(255);

            if (n.warn()) drawWarnBadge(canvas, c[0] + radius * 0.95f, c[1] - radius * 0.95f, dpx, n.lowBat(), alpha);
        }

        if ("B".equals(mode) && "count".equals(s.recPhase)) drawCountdown(canvas, dpx);
        if ("D".equals(mode) && s.flash != null && now <= s.flash.until) drawFlash(canvas, dpx);
        if (animating) postInvalidateOnAnimation();
    }

    private float[] mapNode(AppState.Node n) {
        Seg g = segByKey(n.seg);
        if (g == null) return null;
        float[] c = center(g);
        matrix.mapPoints(c);
        return c;
    }

    // 배터리 부족(배터리 모양) 또는 연결 불량(막대 모양) 표시
    private void drawWarnBadge(Canvas canvas, float x, float y, float dpx, boolean lowBat, int alpha) {
        float r = 9 * dpx;
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Ui.WHITE);
        dot.setAlpha(alpha);
        canvas.drawCircle(x, y, r, dot);
        dot.setStyle(Paint.Style.STROKE);
        dot.setStrokeWidth(1.5f * dpx);
        dot.setColor(Ui.WARN);
        dot.setAlpha(alpha);
        canvas.drawCircle(x, y, r, dot);
        if (lowBat) {
            canvas.drawRoundRect(new RectF(x - 4.5f * dpx, y - 2.5f * dpx, x + 3.5f * dpx, y + 2.5f * dpx), dpx, dpx, dot);
            canvas.drawLine(x + 5 * dpx, y - dpx, x + 5 * dpx, y + dpx, dot);
        } else {
            canvas.drawLine(x - 3.5f * dpx, y + 3.5f * dpx, x - 3.5f * dpx, y + 2.5f * dpx, dot);
            canvas.drawLine(x, y + 3.5f * dpx, x, y, dot);
            canvas.drawLine(x + 3.5f * dpx, y + 3.5f * dpx, x + 3.5f * dpx, y - 3.5f * dpx, dot);
        }
    }

    private void drawCountdown(Canvas canvas, float dpx) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(0xD114171F);
        canvas.drawCircle(cx, cy, 64 * dpx, dot);
        text.setColor(Ui.WHITE);
        text.setTextSize(52 * dpx);
        canvas.drawText(String.valueOf(s.countN), cx, cy + 8 * dpx, text);
        text.setTextSize(13 * dpx);
        text.setFakeBoldText(false);
        canvas.drawText("자세를 준비하세요", cx, cy + 32 * dpx, text);
        text.setFakeBoldText(true);
    }

    private void drawFlash(Canvas canvas, float dpx) {
        float left = 8 * dpx, top = 28 * dpx, width = getWidth() - 16 * dpx;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Ui.WHITE);
        tp.setFakeBoldText(true);
        tp.setTextSize(15 * getResources().getDisplayMetrics().scaledDensity);
        StaticLayout layout = new StaticLayout(s.flash.text, tp, (int) (width - 32 * dpx),
                Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        float h = layout.getHeight() + 28 * dpx;
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(s.flash.warn ? Ui.WARN : Ui.PRIMARY);
        canvas.drawRoundRect(new RectF(left, top, left + width, top + h), 12 * dpx, 12 * dpx, dot);
        canvas.save();
        canvas.translate(left + 16 * dpx, top + 14 * dpx);
        layout.draw(canvas);
        canvas.restore();
    }

    // ---------- 누르기와 끌기 (A 화면) ----------

    private AppState.Node nodeAtPoint(float x, float y) {
        float radius = Math.max(16 * scale, 12 * getResources().getDisplayMetrics().density) + Ui.dp(getContext(), 6);
        for (AppState.Node n : s.nodes) {
            if (n.seg == null) continue;
            float[] c = mapNode(n);
            if (c != null && Math.hypot(c[0] - x, c[1] - y) <= radius) return n;
        }
        return null;
    }

    // 손가락 아래 부위. 없으면 nearest 가 true 일 때 가장 가까운 부위.
    private String segAtPoint(float x, float y, boolean nearest) {
        float[] p = {x, y};
        inverse.mapPoints(p);
        for (int i = segs.size() - 1; i >= 0; i--) {
            Seg g = segs.get(i);
            float px = g.x + g.w / 2, py = g.top ? g.y : g.y + g.h / 2;
            double a = Math.toRadians(-g.t);
            float dx = p[0] - px, dy = p[1] - py;
            float lx = (float) (dx * Math.cos(a) - dy * Math.sin(a));
            float ly = (float) (dx * Math.sin(a) + dy * Math.cos(a));
            float y0 = g.top ? 0 : -g.h / 2;
            if (lx >= -g.w / 2 && lx <= g.w / 2 && ly >= y0 && ly <= y0 + g.h) return g.k;
        }
        if (!nearest) return null;
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (Seg g : segs) {
            float[] c = center(g);
            double d = Math.hypot(c[0] - p[0], c[1] - p[1]);
            if (d < bestD) {
                bestD = d;
                best = g.k;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (s == null || !"A".equals(mode)) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longPressed = false;
                downNode = nodeAtPoint(downX, downY);
                if (downNode != null && !downNode.hub) {
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > touchSlop) removeCallbacks(longPress);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (!longPressed && Math.hypot(e.getX() - downX, e.getY() - downY) <= touchSlop) {
                    performClick();
                    if (downNode != null) {
                        callbacks.onNodeTap(downNode);
                    } else {
                        String seg = segAtPoint(downX, downY, false);
                        if (seg != null) callbacks.onSegmentTap(seg);
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void startNodeDrag() {
        if (downNode == null) return;
        longPressed = true;
        startNodeDrag(this, downNode);
    }

    // 미배치 칸의 노드에서도 같은 모양으로 끌기를 시작한다
    static void startNodeDrag(View from, AppState.Node node) {
        ClipData data = ClipData.newPlainText("node", node.id);
        from.startDragAndDrop(data, new NodeShadow(from.getContext(), node), null, 0);
    }

    private boolean onDrag(View v, DragEvent e) {
        if (s == null || !"A".equals(mode)) return false;
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return e.getClipDescription() != null && e.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
            case DragEvent.ACTION_DRAG_LOCATION:
                String over = segAtPoint(e.getX(), e.getY(), true);
                if (over != null && !over.equals(s.dragOver)) {
                    s.setDragOver(over);
                    invalidate();
                }
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                s.setDragOver(null);
                invalidate();
                return true;
            case DragEvent.ACTION_DROP:
                String id = e.getClipData().getItemAt(0).getText().toString();
                s.placeNode(id, segAtPoint(e.getX(), e.getY(), true));
                return true;
        }
        return true;
    }

    // 끌 때 손가락을 따라오는 동그란 노드 그림
    private static class NodeShadow extends View.DragShadowBuilder {
        private final AppState.Node node;
        private final float r;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NodeShadow(Context c, AppState.Node node) {
            this.node = node;
            this.r = Ui.dp(c, 18);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(Ui.dp(c, 12));
        }

        @Override
        public void onProvideShadowMetrics(Point size, Point touch) {
            size.set((int) (r * 2), (int) (r * 2));
            touch.set((int) r, (int) r);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            paint.setColor(node.warn() ? Ui.WARN : Ui.PRIMARY);
            canvas.drawCircle(r, r, r, paint);
            paint.setColor(Ui.WHITE);
            canvas.drawText(node.id, r, r - (paint.descent() + paint.ascent()) / 2, paint);
        }
    }
}
