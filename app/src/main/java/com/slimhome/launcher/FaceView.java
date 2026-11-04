package com.slimhome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Нарисованный смайлик: не зависит от шрифтов эмодзи, которых нет на всех ТВ. */
public class FaceView extends View {

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mouth = new Path();
    private final RectF mouthRect = new RectF();
    private boolean winking;

    public FaceView(Context c) {
        super(c);
        facePaint.setColor(Color.parseColor("#FFE07A"));
        inkPaint.setColor(Color.parseColor("#3A2A10"));
        inkPaint.setStyle(Paint.Style.FILL);
        inkPaint.setStrokeCap(Paint.Cap.ROUND);
        blushPaint.setColor(Color.parseColor("#33E86A5A"));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setWinking(boolean value) {
        if (winking == value) return;
        winking = value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - Math.max(w, h) * 0.04f;

        facePaint.setShadowLayer(r * 0.10f, 0, r * 0.05f, 0x66000000);
        canvas.drawCircle(cx, cy, r, facePaint);
        facePaint.clearShadowLayer();

        float eyeDy = -r * 0.16f;
        float eyeDx = r * 0.34f;
        float eyeR = r * 0.105f;

        canvas.drawCircle(cx - eyeDx, cy + eyeDy, eyeR, inkPaint);
        if (winking) {
            inkPaint.setStyle(Paint.Style.STROKE);
            inkPaint.setStrokeWidth(r * 0.075f);
            RectF eyeArc = new RectF(cx + eyeDx - eyeR * 1.4f, cy + eyeDy - eyeR * 1.1f,
                    cx + eyeDx + eyeR * 1.4f, cy + eyeDy + eyeR * 1.1f);
            canvas.drawArc(eyeArc, 200, 140, false, inkPaint);
            inkPaint.setStyle(Paint.Style.FILL);
        } else {
            canvas.drawCircle(cx + eyeDx, cy + eyeDy, eyeR, inkPaint);
        }

        canvas.drawCircle(cx - r * 0.55f, cy + r * 0.18f, r * 0.13f, blushPaint);
        canvas.drawCircle(cx + r * 0.55f, cy + r * 0.18f, r * 0.13f, blushPaint);

        mouth.reset();
        mouthRect.set(cx - r * 0.45f, cy + r * 0.02f, cx + r * 0.45f, cy + r * 0.55f);
        if (winking) {
            mouthRect.set(cx - r * 0.52f, cy + r * 0.0f, cx + r * 0.52f, cy + r * 0.62f);
        }
        mouth.addArc(mouthRect, 15, 150);
        inkPaint.setStyle(Paint.Style.STROKE);
        inkPaint.setStrokeWidth(r * 0.085f);
        canvas.drawPath(mouth, inkPaint);
        inkPaint.setStyle(Paint.Style.FILL);
    }
}