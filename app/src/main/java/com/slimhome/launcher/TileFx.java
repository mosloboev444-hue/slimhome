package com.slimhome.launcher;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Разные анимации фокуса для плиток: у каждой свой характер.
 */
public final class TileFx {

    public static final int COUNT = 8;

    private TileFx() { }

    /** Стабильный стиль для пакета: одна и та же плитка всегда ведёт себя одинаково. */
    public static int styleFor(String pkg) {
        return Math.abs(pkg.hashCode()) % COUNT;
    }

    public static void focus(View v, int style) {
        reset(v, 0);
        v.setTranslationZ(dp(v, 12));
        switch (style) {
            case 0: // пружинистое увеличение
                v.animate().scaleX(1.12f).scaleY(1.12f)
                        .setDuration(300).setInterpolator(new OvershootInterpolator(2.6f)).start();
                break;

            case 1: // наклон
                v.animate().scaleX(1.10f).scaleY(1.10f).rotation(-6f)
                        .setDuration(260).setInterpolator(new OvershootInterpolator(2.0f)).start();
                break;

            case 2: // подъём вверх
                v.animate().translationY(-dp(v, 14)).scaleX(1.09f).scaleY(1.09f)
                        .setDuration(230).setInterpolator(new DecelerateInterpolator()).start();
                break;

            case 3: // двойной пульс
                v.animate().scaleX(1.14f).scaleY(1.14f).setDuration(170)
                        .withEndAction(() -> v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
                                .withEndAction(() -> v.animate().scaleX(1.10f).scaleY(1.10f)
                                        .setDuration(150).start()).start())
                        .start();
                break;

            case 4: // сдвиг вправо
                v.animate().translationX(dp(v, 12)).scaleX(1.08f).scaleY(1.08f)
                        .setDuration(230).setInterpolator(new OvershootInterpolator(1.8f)).start();
                break;

            case 5: // «переворот» по вертикали
                v.animate().scaleY(0.86f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleY(1.12f).scaleX(1.10f)
                                .setDuration(260).setInterpolator(new OvershootInterpolator(1.6f)).start())
                        .start();
                break;

            case 6: // дрожание
                ObjectAnimator shake = ObjectAnimator.ofFloat(v, "translationX", 0f, -dp(v, 9), dp(v, 9), -dp(v, 6), 0f);
                shake.setDuration(300);
                shake.start();
                v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(220).start();
                break;

            default: // 7 — поворот с пружинкой
                v.animate().scaleX(1.12f).scaleY(1.12f).rotation(5f)
                        .setDuration(280).setInterpolator(new OvershootInterpolator(3.0f)).start();
                break;
        }
    }

    public static void blur(View v) {
        v.setTranslationZ(0f);
        reset(v, 180);
    }

    private static void reset(View v, long duration) {
        v.animate().scaleX(1f).scaleY(1f)
                .translationX(0f).translationY(0f).rotation(0f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(duration)
                .start();
    }

    private static float dp(View v, float value) {
        return value * v.getResources().getDisplayMetrics().density;
    }
}