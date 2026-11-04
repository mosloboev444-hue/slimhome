package com.slimhome.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Огненное кольцо вокруг кнопки: вращающийся градиент, свечение и пульсация.
 */
public class FireRingView extends View {

    private static final int[] FIRE = {
            Color.parseColor("#00FF3C00"),
            Color.parseColor("#FFFF2D00"),
            Color.parseColor("#FFFF9A00"),
            Color.parseColor("#FFFFE066"),
            Color.parseColor("#FFFF9A00"),
            Color.parseColor("#FFFF2D00"),
            Color.parseColor("#00FF3C00")
    };

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();
    private SweepGradient gradient;
    private android.graphics.Bitmap ringBitmap;
    private ValueAnimator spinner;
    private float rotate = 0f;
    private float pulse = 1f;
    private float radiusDp = 26f;

    public FireRingView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // нужен BlurMaskFilter
        setWillNotDraw(false);

        glow.setStyle(Paint.Style.STROKE);
        glow.setMaskFilter(new BlurMaskFilter(dp(10), BlurMaskFilter.Blur.NORMAL));

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);

        core.setStyle(Paint.Style.STROKE);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** Радиус скругления, чтобы кольцо повторяло форму пилюли. */
    public void setCornerRadius(float r) {
        radiusDp = r;
        buildGradient();
        invalidate();
    }

    private void buildGradient() {
        if (getWidth() == 0 || getHeight() == 0) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        gradient = new SweepGradient(cx, cy, FIRE, null);
        buildBitmap();
    }

    /** Кольцо рисуется один раз в bitmap — дальше только вращаем картинку (дешево для GPU). */
    private void buildBitmap() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h,
                android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = Math.min(oval.width(), oval.height()) * 0.5f;
        float rr = Math.max(0f, Math.min(radiusDp * getResources().getDisplayMetrics().density, r));
        c.drawRoundRect(oval, rr, rr, glow);
        c.drawRoundRect(oval, rr, rr, ring);
        c.drawRoundRect(oval, rr, rr, core);
        ringBitmap = bmp;
        glow.setShader(gradient);
        ring.setShader(gradient);
        core.setShader(gradient);
        glow.setStrokeWidth(dp(9));
        ring.setStrokeWidth(dp(4));
        core.setStrokeWidth(dp(1.4f));
        core.setAlpha(150);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float inset = dp(5);
        oval.set(inset, inset, w - inset, h - inset);
        buildGradient();
    }

    public void startFire() {
        if (spinner != null && spinner.isRunning()) return;
        spinner = ValueAnimator.ofFloat(0f, 360f);
        spinner.setDuration(1500);
        spinner.setInterpolator(new LinearInterpolator());
        spinner.setRepeatCount(ValueAnimator.INFINITE);
        spinner.addUpdateListener(a -> {
            rotate = (float) a.getAnimatedValue();
            // «дыхание» пламени
            long t = android.os.SystemClock.uptimeMillis();
            pulse = 1f + 0.035f * (float) Math.sin(t / 130.0);
            invalidate();
        });
        spinner.start();
        setVisibility(VISIBLE);
    }

    public void stopFire() {
        if (spinner != null) {
            spinner.cancel();
            spinner = null;
        }
        setVisibility(INVISIBLE);
    }

    @Override protected void onDraw(Canvas c) {
        if (gradient == null) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        c.save();
        c.scale(pulse, pulse, cx, cy);
        c.rotate(rotate, cx, cy);
        if (ringBitmap != null) c.drawBitmap(ringBitmap, 0, 0, null);
        c.restore();
    }
}