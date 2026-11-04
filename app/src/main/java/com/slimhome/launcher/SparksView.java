package com.slimhome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Искры вокруг выбранной плитки: частицы цвета иконки разлетаются и гаснут.
 */
public class SparksView extends View {

    private static class Spark {
        float x, y, vx, vy, life, maxLife, size;
        int color;
    }

    private final List<Spark> sparks = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final DecelerateInterpolator ease = new DecelerateInterpolator();
    private long lastFrame = 0;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            step();
            if (!sparks.isEmpty()) {
                postInvalidateOnAnimation();
                postDelayed(this, 16);
            }
        }
    };

    public SparksView(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Запускает вспышку искр в точке (x, y) цветом color. */
    public void burst(float x, float y, int color) {
        int count = 26;
        for (int i = 0; i < count; i++) {
            Spark s = new Spark();
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = dp(3.4f) + random.nextFloat() * dp(6.0f);
            s.x = x + (random.nextFloat() - 0.5f) * dp(60);
            s.y = y + (random.nextFloat() - 0.5f) * dp(26);
            s.vx = (float) Math.cos(angle) * speed;
            s.vy = (float) Math.sin(angle) * speed - dp(1.4f);
            s.maxLife = 620 + random.nextInt(420);
            s.life = s.maxLife;
            s.size = dp(2.6f) + random.nextFloat() * dp(3.4f);
            s.color = color;
            sparks.add(s);
        }
        if (!isRunning) {
            isRunning = true;
            lastFrame = SystemClock.uptimeMillis();
            removeCallbacks(tick);
            post(tick);
        }
    }

    private boolean isRunning = false;

    private void step() {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(32f, now - lastFrame) / 16f;
        lastFrame = now;

        Iterator<Spark> it = sparks.iterator();
        while (it.hasNext()) {
            Spark s = it.next();
            s.life -= dt * 16f;
            if (s.life <= 0f) { it.remove(); continue; }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += dp(0.22f) * dt;      // чуть «падают»
            s.vx *= 0.985f;
        }
        invalidate();
        if (sparks.isEmpty()) isRunning = false;
    }

    @Override protected void onDraw(Canvas c) {
        for (Spark s : sparks) {
            float k = ease.getInterpolation(Math.max(0f, Math.min(1f, s.life / s.maxLife)));
            paint.setColor(s.color);
            paint.setAlpha((int) (255 * Math.min(1f, k * 1.8f)));
            c.drawCircle(s.x, s.y, s.size * (0.6f + k * 0.8f), paint);
            // мягкий ореол
            paint.setAlpha((int) (110 * k));
            c.drawCircle(s.x, s.y, s.size * 2.2f, paint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}