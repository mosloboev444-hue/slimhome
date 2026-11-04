package com.slimhome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Интро по мотивам авторской подписи: разноцветные буквы
 * "DESIGN BY / ANDREY / MASLOBOEV" проявляются по одной.
 */
public class IntroView extends View {

    private static final long DURATION = 2600L;
    private static final String[] LINES = {"DESIGN BY", "ANDREY", "MASLOBOEV"};

    // палитра как на наклейке
    private static final int[] PALETTE = {
            0xFF17A398, 0xFF2E86DE, 0xFF6C5CE7, 0xFFC0392B,
            0xFFE67E22, 0xFFC4297B, 0xFF27AE60, 0xFF1B4F72,
            0xFF8E44AD, 0xFFD35400
    };

    private long startedAt = -1L;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lettersTotal = 0;

    public IntroView(Context c) {
        super(c);
        text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        for (String line : LINES) lettersTotal += line.length();
        setFocusable(false);
        setClickable(false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (startedAt < 0) startedAt = SystemClock.uptimeMillis();
    }

    public float progress() {
        long from = startedAt < 0 ? SystemClock.uptimeMillis() : startedAt;
        return Math.min(1f, (SystemClock.uptimeMillis() - from) / (float) DURATION);
    }

    public boolean finished() {
        return progress() >= 1f;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float p = progress();

        // фон
        bg.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0A0E16, 0xFF16233A, 0xFF0A0E16}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, bg);

        // размеры
        float size = Math.min(w, h) * 0.115f;
        text.setTextSize(size);
        float lineH = size * 1.22f;
        float blockH = lineH * LINES.length;
        float top = h / 2f - blockH / 2f + size;

        // появление букв
        float stagger = 0.055f;
        int index = 0;
        for (int li = 0; li < LINES.length; li++) {
            String line = LINES[li];
            float lineW = text.measureText(line);
            float x = w / 2f - lineW / 2f;
            float y = top + li * lineH;

            for (int ci = 0; ci < line.length(); ci++) {
                char ch = line.charAt(ci);
                float appear = (p - index * stagger) / 0.22f;
                appear = Math.max(0f, Math.min(1f, appear));
                if (ch != ' ' && appear > 0f) {
                    float scale = 0.72f + 0.28f * easeOut(appear);
                    text.setColor(PALETTE[index % PALETTE.length]);
                    text.setAlpha((int) (255 * appear));
                    canvas.save();
                    canvas.translate(x + text.measureText(String.valueOf(ch)) / 2f, y - size * 0.36f);
                    canvas.scale(scale, scale);
                    canvas.drawText(String.valueOf(ch), -text.measureText(String.valueOf(ch)) / 2f, size * 0.36f, text);
                    canvas.restore();
                }
                x += text.measureText(String.valueOf(ch));
                index++;
            }
        }

        // блик, проходящий по надписи
        float shine = Math.max(0f, (p - 0.72f) / 0.28f);
        if (shine > 0f && shine < 1f) {
            float sx = -w * 0.3f + shine * w * 1.6f;
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setShader(new LinearGradient(sx, 0, sx + w * 0.25f, 0,
                    new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h / 2f - blockH / 2f, w, h / 2f + blockH / 2f, glow);
        }
    }

    private static float easeOut(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}