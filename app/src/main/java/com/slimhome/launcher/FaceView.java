package com.slimhome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Drawn emoticon: several moods, no dependency on emoji fonts (TV ROMs often lack them).
 */
public class FaceView extends View {

    public static final int MOOD_COUNT = 8;

    public static final int SMILE = 0;
    public static final int WINK = 1;
    public static final int LAUGH = 2;
    public static final int SURPRISED = 3;
    public static final int SLEEPY = 4;
    public static final int LOVE = 5;
    public static final int TONGUE = 6;
    public static final int ROBOT = 7;

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int mood = SMILE;
    private boolean winking;

    public FaceView(Context context) {
        super(context);
        facePaint.setColor(Color.parseColor("#FFFFD866"));
        inkPaint.setColor(Color.parseColor("#FF2A1F0E"));
        inkPaint.setStyle(Paint.Style.STROKE);
        inkPaint.setStrokeWidth(3f);
        inkPaint.setStrokeCap(Paint.Cap.ROUND);
        blushPaint.setColor(0x33FF8A65);
    }

    public void setMood(int mood) {
        this.mood = ((mood % MOOD_COUNT) + MOOD_COUNT) % MOOD_COUNT;
        this.winking = this.mood == WINK;
        invalidate();
    }

    public int getMood() { return mood; }

    public void setWinking(boolean winking) {
        this.winking = winking;
        if (winking) this.mood = WINK;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f - 4f;
        float cx = w / 2f, cy = h / 2f;

        c.save();
        c.rotate(mood == ROBOT ? 0f : (mood % 2 == 0 ? -6f : 6f), cx, cy);

        // face
        c.drawCircle(cx, cy, r, facePaint);

        float eyeDx = r * 0.38f;
        float eyeY = cy - r * 0.18f;
        float eyeR = r * 0.10f;
        float stroke = Math.max(3f, r * 0.09f);
        inkPaint.setStrokeWidth(stroke);

        // eyes
        switch (mood) {

            case SLEEPY: {
                c.drawLine(cx - eyeDx - eyeR, eyeY, cx - eyeDx + eyeR, eyeY, inkPaint);
                c.drawLine(cx + eyeDx - eyeR, eyeY, cx + eyeDx + eyeR, eyeY, inkPaint);
                break;
            }
            case LOVE: {
                drawHeart(c, cx - eyeDx, eyeY, eyeR * 1.5f, 0xFFFF4D6D);
                drawHeart(c, cx + eyeDx, eyeY, eyeR * 1.5f, 0xFFFF4D6D);
                break;
            }
            case WINK: {
                c.drawCircle(cx - eyeDx, eyeY, eyeR, inkPaint);
                c.drawLine(cx + eyeDx - eyeR * 1.2f, eyeY, cx + eyeDx + eyeR * 1.2f, eyeY, inkPaint);
                break;
            }
            case SURPRISED: {
                c.drawCircle(cx - eyeDx, eyeY - eyeR * 0.2f, eyeR * 1.25f, inkPaint);
                c.drawCircle(cx + eyeDx, eyeY - eyeR * 0.2f, eyeR * 1.25f, inkPaint);
                break;
            }
            case ROBOT: {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.parseColor("#FF20303F"));
                c.drawCircle(cx - eyeDx, eyeY, eyeR * 1.1f, p);
                c.drawCircle(cx + eyeDx, eyeY, eyeR * 1.1f, p);
                p.setColor(0xFF66E0FF);
                c.drawCircle(cx - eyeDx + eyeR * 0.35f, eyeY - eyeR * 0.35f, eyeR * 0.35f, p);
                c.drawCircle(cx + eyeDx + eyeR * 0.35f, eyeY - eyeR * 0.35f, eyeR * 0.35f, p);
                break;
            }
            default: {
                c.drawCircle(cx - eyeDx, eyeY, eyeR, inkPaint);
                c.drawCircle(cx + eyeDx, eyeY, eyeR, inkPaint);
            }
        }

        // mouth
        float mY = cy + r * 0.28f;
        switch (mood) {
            case LAUGH: {
                rect.set(cx - r * 0.34f, mY - r * 0.18f, cx + r * 0.34f, mY + r * 0.34f);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.parseColor("#FF2A1F0E"));
                c.drawArc(rect, 0, 180, true, p);
                break;
            }
            case SURPRISED: {
                c.drawCircle(cx, mY, r * 0.13f, inkPaint);
                break;
            }

            case SLEEPY: {
                c.drawLine(cx - r * 0.18f, mY, cx + r * 0.18f, mY, inkPaint);
                break;
            }
            case TONGUE: {
                path.reset();
                path.moveTo(cx - r * 0.28f, mY - r * 0.05f);
                path.quadTo(cx, mY + r * 0.34f, cx + r * 0.28f, mY - r * 0.05f);
                c.drawPath(path, inkPaint);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.parseColor("#FFFF6B6B"));
                rect.set(cx - r * 0.12f, mY + r * 0.10f, cx + r * 0.12f, mY + r * 0.40f);
                c.drawOval(rect, p);
                break;
            }
            case ROBOT: {
                rect.set(cx - r * 0.26f, mY - r * 0.08f, cx + r * 0.26f, mY + r * 0.14f);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.parseColor("#FF20303F"));
                c.drawRoundRect(rect, r * 0.06f, r * 0.06f, p);
                break;
            }
            case LOVE: {
                path.reset();
                path.moveTo(cx - r * 0.26f, mY - r * 0.04f);
                path.quadTo(cx, mY + r * 0.34f, cx + r * 0.26f, mY - r * 0.04f);
                c.drawPath(path, inkPaint);
                break;
            }

            default: {
                path.reset();
                path.moveTo(cx - r * 0.28f, mY - r * 0.06f);
                path.quadTo(cx, mY + r * 0.30f, cx + r * 0.28f, mY - r * 0.06f);
                c.drawPath(path, inkPaint);
            }
        }

        // blush
        if (mood == SMILE || mood == LAUGH || mood == LOVE || mood == TONGUE) {
            c.drawCircle(cx - r * 0.55f, cy + r * 0.18f, r * 0.14f, blushPaint);
            c.drawCircle(cx + r * 0.55f, cy + r * 0.18f, r * 0.14f, blushPaint);
        }

        // antenna for robot
        if (mood == ROBOT) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.parseColor("#FF20303F"));
            p.setStrokeWidth(stroke);
            c.drawLine(cx, cy - r, cx, cy - r * 1.28f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF66E0FF);
            c.drawCircle(cx, cy - r * 1.34f, r * 0.09f, p);
        }

        c.restore();
    }

    private void drawHeart(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        Path heart = new Path();
        float s = size;
        heart.moveTo(cx, cy + s * 0.6f);
        heart.cubicTo(cx - s * 1.4f, cy - s * 0.4f, cx - s * 0.5f, cy - s * 1.1f, cx, cy - s * 0.3f);
        heart.cubicTo(cx + s * 0.5f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.4f, cx, cy + s * 0.6f);
        c.drawPath(heart, p);
    }
}