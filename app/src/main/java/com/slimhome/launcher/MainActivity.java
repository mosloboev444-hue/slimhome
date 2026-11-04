package com.slimhome.launcher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int COLUMNS = 5;
    private static final int ROWS = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat weekday = new SimpleDateFormat("EEEE", new Locale("ru"));
    private final SimpleDateFormat dayMonth = new SimpleDateFormat("d MMMM", new Locale("ru"));

    private TextView tvClock, tvDate, tvDateNum, tvNetwork, tvTemp, tvCond, tvCity, tvWeatherIcon;
    private LinearLayout appsGrid, weekRow;
    private final List<View> tiles = new ArrayList<>();
    private final List<View> icons = new ArrayList<>();
    private View tintA, tintB;
    private TextView tvGreeting, tvAllApps, tvVpn;
    private LinearLayout allAppsPanel, allAppsGrid;
    private boolean panelOpen;
    private ViewGroup rootView;
    private FaceView winkFace;

    private boolean useA = true;
    private int lastAmbient = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Calendar now = Calendar.getInstance();
            tvClock.setText(hhmm.format(now.getTime()));
            String wd = weekday.format(now.getTime()).toUpperCase(new Locale("ru"));
            tvDate.setText(wd);
            tvDateNum.setText(dayMonth.format(now.getTime()).toUpperCase(new Locale("ru")));
            tvGreeting.setText(greeting(now.get(Calendar.HOUR_OF_DAY)));
            updateVpn();
            ui.postDelayed(this, 10000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        applyDisplayScale();
        setContentView(R.layout.activity_main);

        tvClock = findViewById(R.id.tvClock);
        tvDate = findViewById(R.id.tvDate);
        tvDateNum = findViewById(R.id.tvDateNum);
        tvNetwork = findViewById(R.id.tvNetwork);
        tvTemp = findViewById(R.id.tvWeatherTemp);
        tvCond = findViewById(R.id.tvWeatherCond);
        tvCity = findViewById(R.id.tvWeatherCity);
        tvWeatherIcon = findViewById(R.id.tvWeatherIcon);
        appsGrid = findViewById(R.id.appsGrid);
        weekRow = findViewById(R.id.weekRow);
        tintA = findViewById(R.id.tintA);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvAllApps = findViewById(R.id.tvAllApps);
        allAppsPanel = findViewById(R.id.allAppsPanel);
        allAppsGrid = findViewById(R.id.allAppsGrid);
        tintB = findViewById(R.id.tintB);

        tvVpn = findViewById(R.id.tvVpn);
        stylePill(tvVpn, this::openVpn);
        TextView tvSettings = findViewById(R.id.tvSettings);
        stylePill(tvSettings, this::openSettings);
        stylePill(tvNetwork, this::openNetwork);
        stylePill(tvAllApps, this::toggleAllApps);

        rootView = findViewById(R.id.root);
        View signature = findViewById(R.id.signature);
        signature.setOnFocusChangeListener((v, has) -> {
            tvAuthor(has);
            if (has) playWink(); else hideFace();
        });
        signature.setOnLongClickListener(v -> {
            openAppearanceSettings();
            return true;
        });

        // подпись автора: переливающийся градиент
        startShimmer(findViewById(R.id.tvAuthor));
        startShimmer(findViewById(R.id.tvDesignBy));


        if (b == null) playIntro();

        applyResponsiveSizes();
        tick.run();
        updateNetwork();
        buildApps();
        loadWeather();
        applyWeatherAlpha();
        maybeAutostartIptv();
        registerAppsWatcher();
    }

    private android.content.BroadcastReceiver appsWatcher;

    private void registerAppsWatcher() {
        if (appsWatcher != null) return;
        appsWatcher = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context c, Intent i) {
                if (i == null) return;
                ui.removeCallbacks(rebuildGrid);
                ui.postDelayed(rebuildGrid, 1200);
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(appsWatcher, f);
    }

    private final Runnable rebuildGrid = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) buildApps();
        }
    };
    @Override protected void onDestroy() {
        try { if (appsWatcher != null) unregisterReceiver(appsWatcher); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        updateNetwork();
        if (tiles.isEmpty()) buildApps();
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) { loadWeather(); return true; }   // долгое MENU — обновить погоду
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (appearancePanel != null) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                closeAppearanceSettings();
                return true;
            }
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            openAppearanceSettings();       // короткое MENU — настройки вида
            return true;
        }
        // цифры 1–5: запуск закреплённых приложений
        if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_5) {
            int idx = code - KeyEvent.KEYCODE_1;
            String[] quick = {"com.slimiptv.player", "com.google.android.youtube.tv", "ru.rt.video.app.tv",
                    "tv.limehd.stb", "ru.vk.store.tv"};
            if (idx < quick.length) {
                android.content.Intent qi = getPackageManager().getLaunchIntentForPackage(quick[idx]);
                if (qi != null) { qi.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(qi); return true; }
            }
        }
        return super.onKeyDown(code, e);
    }

    // ---------- полный список приложений ----------
    private static String greeting(int h) {
        if (h >= 5 && h < 12) return "Доброе утро";
        if (h >= 12 && h < 17) return "Добрый день";
        if (h >= 17 && h < 23) return "Добрый вечер";
        return "Доброй ночи";
    }

    // ---------- настройки внешнего вида ----------
    private android.widget.FrameLayout appearancePanel;

    private int prefInt(String key, int def) {
        return prefs().getInt(key, def);
    }

    private boolean prefBool(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    private void openAppearanceSettings() {
        if (appearancePanel != null) return;

        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(0xE6070B12);
        overlay.setFocusable(false);
        overlay.setClickable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(40), dp(32), dp(40), dp(32));

        TextView title = new TextView(this);
        title.setText("Вид главного экрана");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(6));
        card.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Стрелки — выбор, OK — изменить, BACK — закрыть");
        hint.setTextColor(0xFF8FB3D9);
        hint.setTextSize(13);
        hint.setPadding(0, 0, 0, dp(18));
        card.addView(hint);

        final int[] colOptions = {0, 3, 4, 5, 6, 7};
        final String[] colLabels = {"Авто", "3 колонки", "4 колонки", "5 колонок", "6 колонок", "7 колонок"};
        card.addView(settingsRow("Колонок приложений", new Runnable() {
            @Override public void run() {
                int cur = prefInt("grid_cols", 0);
                int idx = 0;
                for (int i = 0; i < colOptions.length; i++) if (colOptions[i] == cur) idx = i;
                idx = (idx + 1) % colOptions.length;
                prefs().edit().putInt("grid_cols", colOptions[idx]).apply();
                refreshSettingsLabels();
                buildApps();
            }
        }, "cols"));

        final int[] sizeOptions = {0, 1, 2};
        card.addView(settingsRow("Размер плиток", new Runnable() {
            @Override public void run() {
                int cur = prefInt("tile_size", 0);
                prefs().edit().putInt("tile_size", (cur + 1) % sizeOptions.length).apply();
                refreshSettingsLabels();
                buildApps();
            }
        }, "size"));

        card.addView(settingsRow("Фон погоды", new Runnable() {
            @Override public void run() {
                prefs().edit().putBoolean("weather_bg", !prefBool("weather_bg", false)).apply();
                applyWeatherAlpha();
                refreshSettingsLabels();
            }
        }, "wbg"));

        card.addView(settingsRow("Прозрачность погоды", new Runnable() {
            @Override public void run() {
                int cur = prefInt("weather_alpha", 50);
                int next = cur == 50 ? 25 : (cur == 25 ? 75 : (cur == 75 ? 90 : 50));
                prefs().edit().putInt("weather_alpha", next).apply();
                applyWeatherAlpha();
                refreshSettingsLabels();
            }
        }, "alpha"));

        card.addView(settingsRow("Магазин Play", new Runnable() {
            @Override public void run() {
                prefs().edit().putBoolean("show_play_store", !prefBool("show_play_store", false)).apply();
                refreshSettingsLabels();
                buildApps();
            }
        }, "play"));

        card.addView(settingsRow("Автозапуск IPTV", new Runnable() {
            @Override public void run() {
                prefs().edit().putBoolean("autostart_iptv", !prefBool("autostart_iptv", true)).apply();
                refreshSettingsLabels();
            }
        }, "iptv"));

        card.addView(settingsRow("Сбросить настройки", new Runnable() {
            @Override public void run() {
                prefs().edit().remove("grid_cols").remove("tile_size")
                        .remove("show_play_store").remove("autostart_iptv")
                        .remove("weather_alpha").remove("weather_bg").apply();
                applyWeatherAlpha();
                refreshSettingsLabels();
                buildApps();
            }
        }, "reset"));

        android.graphics.drawable.GradientDrawable glassCard =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{0x40FFFFFF, 0x1AFFFFFF});
        glassCard.setCornerRadius(dp(26));
        glassCard.setStroke(dp(1), 0x4DFFFFFF);
        card.setBackground(glassCard);
        card.setElevation(dp(18));
        android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
                dp(560), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        overlay.addView(card, clp);

        overlay.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                closeAppearanceSettings();
                return true;
            }
            return false;
        });

        appearancePanel = overlay;
        if (rootView instanceof ViewGroup) ((ViewGroup) rootView).addView(overlay,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        refreshSettingsLabels();
        View firstRow = card.getChildAt(2);     // первый пункт после заголовка и подсказки
        if (firstRow != null) firstRow.requestFocus();
    }

    private TextView settingsRow(String label, final Runnable action, String tag) {
        final TextView row = new TextView(this);
        row.setTag(tag);
        row.setText(label);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(17);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setBackgroundResource(R.drawable.tile_min);
        row.setFocusable(true);
        row.setClickable(true);
        row.setTag(tag);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> action.run());
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackgroundResource(R.drawable.tile_min);
            row.setTextColor(has ? 0xFF0B1220 : 0xFFFFFFFF);   // на белом стекле — тёмный текст
        });
        return row;
    }

    private void refreshSettingsLabels() {
        if (appearancePanel == null) return;
        String[] cols = {"Авто", "3 колонки", "4 колонки", "5 колонок", "6 колонок", "7 колонок"};
        int[] colValues = {0, 3, 4, 5, 6, 7};
        int cv = prefInt("grid_cols", 0);
        String colsText = cols[0];
        for (int i = 0; i < colValues.length; i++) if (colValues[i] == cv) colsText = cols[i];

        String[] sizes = {"Авто", "Компактные", "Крупные"};
        String sizeText = sizes[Math.min(2, Math.max(0, prefInt("tile_size", 0)))];

        updateRowText("cols", "Колонок приложений: " + colsText);
        updateRowText("size", "Размер плиток: " + sizeText);
        updateRowText("wbg", "Фон погоды: " + (prefBool("weather_bg", false) ? "стекло" : "без фона"));
        updateRowText("alpha", "Прозрачность погоды: " + prefInt("weather_alpha", 50) + "%");
        updateRowText("play", "Магазин Play: " + (prefBool("show_play_store", false) ? "показан" : "скрыт"));
        updateRowText("iptv", "Автозапуск IPTV: " + (prefBool("autostart_iptv", true) ? "включён" : "выключен"));
    }

    private void updateRowText(String tag, String text) {
        if (!(appearancePanel instanceof android.widget.FrameLayout)) return;
        android.widget.FrameLayout f = (android.widget.FrameLayout) appearancePanel;
        View card = f.getChildAt(0);
        if (!(card instanceof LinearLayout)) return;
        LinearLayout ll = (LinearLayout) card;
        for (int i = 0; i < ll.getChildCount(); i++) {
            View v = ll.getChildAt(i);
            if (v instanceof TextView && tag.equals(String.valueOf(v.getTag()))) {
                ((TextView) v).setText(text);
            }
        }
    }

    private void closeAppearanceSettings() {
        if (appearancePanel == null) return;
        if (rootView instanceof ViewGroup) ((ViewGroup) rootView).removeView(appearancePanel);
        appearancePanel = null;
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }
    private void toggleAllApps() {
        if (panelOpen) { closeAllApps(); return; }
        if (allAppsGrid.getChildCount() == 0) buildAllApps();
        allAppsPanel.setAlpha(0f);
        allAppsPanel.setVisibility(View.VISIBLE);
        allAppsPanel.animate().alpha(1f).setDuration(160).start();
        panelOpen = true;
        allAppsPanel.post(() -> {
            if (allAppsGrid.getChildCount() > 0) {
                ViewGroup row = (ViewGroup) allAppsGrid.getChildAt(0);
                if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
            }
        });
    }

    private void closeAllApps() {
        panelOpen = false;
        allAppsPanel.animate().alpha(0f).setDuration(140)
                .withEndAction(() -> allAppsPanel.setVisibility(View.GONE)).start();
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override public void onBackPressed() {
        if (panelOpen) { closeAllApps(); return; }
        super.onBackPressed();
    }

    private void buildAllApps() {
        allAppsGrid.removeAllViews();

        // первая строка панели — вход в настройки вида (пульт: OK на «Все приложения» → OK на строке)
        TextView settingsEntry = new TextView(this);
        settingsEntry.setText("Вид главного экрана");
        settingsEntry.setTextColor(0xE6FFFFFF);
        settingsEntry.setTextSize(17);
        settingsEntry.setPadding(dp(20), dp(14), dp(20), dp(14));
        settingsEntry.setBackgroundResource(R.drawable.pill_bg);
        settingsEntry.setFocusable(true);
        settingsEntry.setClickable(true);
        settingsEntry.setOnClickListener(v -> {
            closeAllApps();
            openAppearanceSettings();
        });
        LinearLayout.LayoutParams sep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sep.bottomMargin = dp(18);
        allAppsGrid.addView(settingsEntry, sep);
        PackageManager pm = getPackageManager();
        final List<String> hidden = new ArrayList<>();
        if (!prefBool("show_play_store", false)) hidden.add("com.android.vending");   // Play Store — по настройке
        hidden.addAll(java.util.Arrays.asList(
                "com.gathin.tvhome", "com.oranth.tvlauncher", "com.spocky.projengmenu",
                "com.droidlogic.miracast", "com.droidlogic.appinstall",
                "io.github.sumyabazar1.weather"));

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(main, 0));
        all.addAll(pm.queryIntentActivities(lean, 0));

        List<ResolveInfo> list = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : all) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg) || hidden.contains(pkg)) continue;
            if (!seen.add(pkg + "/" + ri.activityInfo.name)) continue;
            list.add(ri);
        }
        Collections.sort(list, (a, b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        final int cols = 6;
        for (int i = 0; i < list.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = dp(12);
            row.setLayoutParams(rlp);
            row.setClipChildren(false);
            row.setClipToPadding(false);

            int count = Math.min(cols, list.size() - i);
            for (int j = 0; j < count; j++) {
                View tile = makeTile(list.get(i + j));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(136), 1f);
                if (j > 0) tlp.leftMargin = dp(12);
                row.addView(tile, tlp);
            }
            for (int k = count; k < cols; k++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (k > 0) slp.leftMargin = dp(12);
                row.addView(spacer, slp);
            }
            allAppsGrid.addView(row);
        }
    }
    private final Handler shimHandler = new Handler(Looper.getMainLooper());

    /** Переливание цветами на Handler: работает даже когда в системе выключены анимации. */
    private void startShimmer(final TextView v) {
        final int[] palette = {
                0xFFFFFFFF, 0xFF7FD8FF, 0xFFB48CFF, 0xFFFF9ED2,
                0xFF8CFFD2, 0xFFFFE08A, 0xFFFFFFFF
        };
        final long cycleMs = 9000L;
        final Runnable[] loop = new Runnable[1];
        loop[0] = new Runnable() {
            @Override public void run() {
                long phase = SystemClock.uptimeMillis() % cycleMs;
                float pos = phase / (float) cycleMs * (palette.length - 1);
                int i = (int) pos;
                float f = pos - i;
                int from = palette[i];
                int to = palette[Math.min(i + 1, palette.length - 1)];
                v.setTextColor(blend(from, to, f));
                shimHandler.postDelayed(this, 60);
            }
        };
        shimHandler.post(loop[0]);
    }

    private static int blend(int from, int to, float f) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.argb(a, r, g, b);
    }
    // ---------- смайлик при наведении на подпись ----------
    private void tvAuthor(boolean focused) {
        TextView a = findViewById(R.id.tvAuthor);
        TextView d = findViewById(R.id.tvDesignBy);
        a.animate().alpha(focused ? 0f : 1f).setDuration(180).start();
        d.animate().alpha(focused ? 0f : 1f).setDuration(180).start();
    }

    private void playWink() {
        if (rootView == null) return;
        if (winkFace != null && winkFace.getParent() != null) {
            ((ViewGroup) winkFace.getParent()).removeView(winkFace);
        }

        final FaceView face = new FaceView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(96), dp(96));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dp(48), dp(30));
        face.setAlpha(0f);
        face.setScaleX(0.5f);
        face.setScaleY(0.5f);
        rootView.addView(face, lp);
        winkFace = face;

        face.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f).setDuration(180).start();

        face.postDelayed(() -> {
            if (face.getParent() == null) return;
            face.setWinking(true);
            face.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        }, 650);


    }
    private void hideFace() {
        if (winkFace == null) return;
        FaceView f = winkFace;
        winkFace = null;
        f.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(160)
                .withEndAction(() -> {
                    if (f.getParent() != null) ((ViewGroup) f.getParent()).removeView(f);
                }).start();
    }
    // ---------- оформление кнопок-пилюль ----------
    private void stylePill(TextView v, Runnable action) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setClickable(true);
        v.setTextColor(getColorCompat(false));
        v.setOnFocusChangeListener((view, has) -> {
            v.setTextColor(getColorCompat(has));
            v.setElevation(has ? dp(6) : 0);
        });
        v.setOnClickListener(view -> action.run());
    }

    private int getColorCompat(boolean focused) {
        return focused ? Color.parseColor("#FF101725") : Color.parseColor("#E6FFFFFF");
    }

    private void openNetwork() {
        Intent[] variants = new Intent[] {
                new Intent().setClassName("com.android.tv.settings",
                        "com.android.tv.settings.connectivity.NetworkActivity"),
                new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),
                new Intent(android.provider.Settings.ACTION_SETTINGS)
        };
        for (Intent i : variants) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); return; } catch (Exception ignored) { }
        }
    }
    // ---------- настройки ----------
    /** Состояние VPN: зелёная кнопка — туннель поднят, красная — нет. */
    private void updateVpn() {
        if (tvVpn == null) return;
        boolean on = false;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (android.net.Network n : cm.getAllNetworks()) {
                    android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                    if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                        on = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) { }
        tvVpn.setText(on ? "VPN · вкл" : "VPN · выкл");
        tvVpn.setTextColor(on ? Color.parseColor("#FF39D98A") : Color.parseColor("#FFFF6B6B"));
    }

    /** Клик по кнопке VPN: включает установленный VPN-клиент или открывает настройки VPN. */
    private void openVpn() {
        String[] vpnApps = {"com.wireguard.android", "org.amnezia.vpn", "com.v2ray.ang",
                "free.vpn.unblock.proxy.vpn.master.pro", "com.psiphon3"};
        for (String pkg : vpnApps) {
            android.content.Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                try { startActivity(i); return; } catch (Throwable ignored) { }
            }
        }
        try {
            startActivity(new android.content.Intent("android.settings.VPN_SETTINGS")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) { }
    }

    private void openSettings() {
        Intent[] variants = new Intent[] {
                new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"),
                new Intent(android.provider.Settings.ACTION_SETTINGS)
        };
        for (Intent i : variants) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); return; } catch (Exception ignored) { }
        }
    }

    // ---------- сеть ----------
    private void updateNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        String label = "Нет сети";
        if (info != null && info.isConnected()) {
            label = info.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Ethernet";
        }
        tvNetwork.setText(label);
    }

    // ---------- приложения ----------
    private void buildApps() {
        appsGrid.setClipChildren(false);
        appsGrid.setClipToPadding(false);
        appsGrid.removeAllViews();
        tiles.clear();
        icons.clear();

        PackageManager pm = getPackageManager();

        // старые лаунчеры не показываем, но «Настройки» и системные утилиты оставляем
        final List<String> hidden = new ArrayList<>();
        if (!prefBool("show_play_store", false)) hidden.add("com.android.vending");   // Play Store — по настройке
        hidden.addAll(java.util.Arrays.asList(
                "com.gathin.tvhome",
                "com.oranth.tvlauncher",
                "com.spocky.projengmenu",
                "com.droidlogic.miracast",   // Miracast
                "com.droidlogic.appinstall",  // AppInstaller
                "io.github.sumyabazar1.weather", // погода уже есть карточкой
                "com.android.gallery3d"     // галерея
        ));

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(main, 0));
        found.addAll(pm.queryIntentActivities(lean, 0));

        // закреплённые приложения идут первыми, дальше — по алфавиту
        final List<String> pinned = java.util.Arrays.asList(
                "com.slimiptv.player",     // SlimIPTV — первым на главном экране
                "com.google.android.youtube.tv", // официальный YouTube для ТВ
                "ru.rt.video.app.tv",      // Wink
                "tv.limehd.stb",           // Лайм HD
                "ru.vk.store.tv",          // RuStore
                "io.github.sumyabazar1.weather" // погода
        );

        List<ResolveInfo> list = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            if (hidden.contains(pkg)) continue;
            String key = pkg + "/" + ri.activityInfo.name;
            if (!seen.add(key)) continue;
            list.add(ri);
        }
        Collections.sort(list, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                int ia = pinned.indexOf(a.activityInfo.packageName);
                int ib = pinned.indexOf(b.activityInfo.packageName);
                if (ia < 0) ia = 100;
                if (ib < 0) ib = 100;
                if (ia != ib) return Integer.compare(ia, ib);
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });

        // ---- адаптивная сетка: подбираем строки/колонки так, чтобы все плитки влезли ----
        final int gapX = dp(16);
        final int gapY = dp(14);
        final int minTileW = dp(132);
        final int minTileH = dp(96);
        final int n = list.size();

        gridWasEstimated = appsGrid.getWidth() <= 0 || appsGrid.getHeight() <= 0;
        int gridW = appsGrid.getWidth();
        int gridH = appsGrid.getHeight();
        if (gridW <= 0 || gridH <= 0) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            if (gridW <= 0) gridW = dm.widthPixels - dp(112);
            if (gridH <= 0) gridH = dm.heightPixels - dp(452);
        }

        int prefCols = prefInt("grid_cols", 0);
        float sizeK = prefInt("tile_size", 0) == 2 ? 1.12f : (prefInt("tile_size", 0) == 1 ? 0.88f : 1f);
        int perRow = prefCols > 0 ? Math.min(prefCols, Math.max(1, n)) : 0;
        int tileH = dp((int) (160 * sizeK));
        if (perRow > 0) {
            int rowsNeeded = (int) Math.ceil(n / (double) perRow);
            int rowsUsed = Math.max(1, Math.min(3, rowsNeeded));
            tileH = Math.max(dp(72), (gridH - gapY * (rowsUsed - 1)) / rowsUsed);
        }
        for (int r = 1; r <= 3 && perRow == 0; r++) {
            int cols = (int) Math.ceil(n / (double) r);
            if (cols < 1) cols = 1;
            if (cols > 5) continue;
            int w = (gridW - gapX * (cols - 1)) / cols;
            int h = (gridH - gapY * (r - 1)) / r;
            if (w >= minTileW && h >= minTileH) {
                perRow = cols;
                tileH = h;
                break;
            }
        }
        if (perRow == 0) {
            // не влезает «красиво» — ужимаем: максимум 8 колонок и 3 ряда
            perRow = Math.min(8, Math.max(1, n));
            int rowsNeeded = (int) Math.ceil(n / (double) perRow);
            int rowsUsed = Math.min(3, rowsNeeded);
            tileH = Math.max(dp(72), (gridH - gapY * (rowsUsed - 1)) / rowsUsed);
        }
        if (perRow < 1) perRow = 1;
        if (tileH < dp(72)) tileH = dp(72);
        int visible = Math.min(n, perRow * 3);   // остальные — в панели «Все приложения»

        int cellW = (gridW - gapX * (perRow - 1)) / perRow;
        frameW = Math.max(dp(110), Math.min(dp(200), cellW - dp(8)));

        for (int i = 0; i < visible; i += perRow) {
            int inRow = Math.min(perRow, visible - i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, tileH);
            if (i > 0) rlp.topMargin = gapY;
            row.setLayoutParams(rlp);
            row.setClipChildren(false);
            row.setClipToPadding(false);

            for (int j = i; j < i + inRow; j++) {
                ResolveInfo ri = list.get(j);
                View tile = makeTile(ri);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, tileH, 1f);
                if (j > i) tlp.leftMargin = gapX;
                row.addView(tile, tlp);
                tiles.add(tile);
            }
            for (int k = inRow; k < perRow; k++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (k > 0) slp.leftMargin = gapX;
                row.addView(spacer, slp);
            }
            appsGrid.addView(row);
        }

        // появление плиток с задержкой
        for (int idx = 0; idx < tiles.size(); idx++) {
            View tv = tiles.get(idx);
            tv.setAlpha(0f);
            tv.setScaleX(0.86f);
            tv.setScaleY(0.86f);
            tv.setTranslationY(dp(18));
            tv.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setStartDelay(45L * idx)
                    .setDuration(300L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        // сетка могла строиться до разметки — пересобираем с реальными размерами
        appsGrid.postDelayed(new Runnable() {
            @Override public void run() {
                if (!tiles.isEmpty()) tiles.get(0).requestFocus();
                if (appsGrid.getWidth() > 0 && gridWasEstimated) {
                    gridWasEstimated = false;
                    buildApps();
                }
            }
        }, 380);
    }

    // ---------- круглые иконки ----------
    private final java.util.HashMap<String, android.graphics.Bitmap> iconCache = new java.util.HashMap<>();

    /** Лёгкое «дыхание» иконки: 0 → −3dp → 0, у каждой свой сдвиг фазы. */
    private void startIconFloat(View icon, int seed) {
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, -dp(3), 0f);
        va.setDuration(3600);
        va.setStartDelay(900L + Math.abs(seed % 1400));
        va.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        va.addUpdateListener(a -> icon.setTranslationY((Float) a.getAnimatedValue()));
        va.start();
    }
    private android.graphics.Bitmap roundIcon(android.graphics.drawable.Drawable src, int size, String key) {
        String cacheKey = key + "@" + size;
        android.graphics.Bitmap cached = iconCache.get(cacheKey);
        if (cached != null) return cached;

        android.graphics.Bitmap square = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas sc = new android.graphics.Canvas(square);
        src.setBounds(0, 0, size, size);
        src.draw(sc);

        android.graphics.Bitmap round = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas rc = new android.graphics.Canvas(round);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.BitmapShader(square,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        float r = size / 2f;
        rc.drawCircle(r, r, r, p);

        // тонкое стеклянное кольцо, чтобы иконка вписывалась в интерфейс
        android.graphics.Paint ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(android.graphics.Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(1f, size / 52f));
        ring.setColor(0x59FFFFFF);
        rc.drawCircle(r, r, r - ring.getStrokeWidth() / 2f, ring);

        iconCache.put(cacheKey, round);
        return round;
    }
    private View makeTile(ResolveInfo ri) {
        PackageManager pm = getPackageManager();

        FrameLayout box = new FrameLayout(this);
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setClickable(true);

        // рамка ровно по логотипу и названию, внутри своей ячейки
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setBackgroundResource(R.drawable.tile_min);
        inner.setPadding(dp(14), dp(10), dp(18), dp(10));

        // одинаковая ширина плашек в ряду: ровные отступы, содержимое внутри
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                frameW > 0 ? frameW : dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER;
        box.addView(inner, flp);

        ImageView icon = new ImageView(this);
        Drawable d = ri.loadIcon(pm);
        int iconPx = dp(52);
        icon.setImageBitmap(roundIcon(d, iconPx, ri.activityInfo.packageName));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        inner.addView(icon, new LinearLayout.LayoutParams(iconPx, iconPx));
        icons.add(icon);
        startIconFloat(icon, ri.activityInfo.packageName.hashCode());

        TextView label = new TextView(this);
        label.setText(ri.loadLabel(pm));
        label.setTextColor(Color.WHITE);
        setTextPx(label, 17f);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setMaxWidth(Math.max(dp(70), (frameW > 0 ? frameW : dp(200)) - dp(104)));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        llp.leftMargin = dp(16);
        inner.addView(label, llp);

        final CharSequence pkg = ri.activityInfo.packageName;
        final CharSequence cls = ri.activityInfo.name;

        box.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(pkg.toString(), cls.toString());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try { startActivity(i); } catch (Exception ignored) { }
        });

        final int ambient = dominantColor(d);
        box.setOnFocusChangeListener((v, has) -> {
            float to = has ? 1.04f : 1f;
            v.animate().scaleX(to).scaleY(to)
                    .setDuration(has ? 220L : 150L)
                    .setInterpolator(new OvershootInterpolator(has ? 1.6f : 1f))
                    .start();
            if (has) setAmbientColor(ambient);
        });

        return box;
    }

    // ---------- подсветка фона цветом приложения ----------
    private void setAmbientColor(int color) {
        if (color == lastAmbient) return;
        lastAmbient = color;

        View next = useA ? tintB : tintA;
        View prev = useA ? tintA : tintB;
        useA = !useA;

        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();
        if (w <= 0 || h <= 0) { w = getResources().getDisplayMetrics().widthPixels; h = getResources().getDisplayMetrics().heightPixels; }

        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(Math.max(w, h) * 0.95f);
        g.setGradientCenter(0.72f, 0.18f);
        g.setColors(new int[]{withAlpha(color, 0xA6), withAlpha(color, 0x00)});
        next.setBackground(g);
        next.animate().alpha(0.30f).setDuration(320).start();
        prev.animate().alpha(0f).setDuration(260).start();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Средний цвет иконки, подтянутый по насыщенности. */
    private int dominantColor(Drawable drawable) {
        try {
            int size = 40;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Drawable d = drawable.getConstantState() != null
                    ? drawable.getConstantState().newDrawable().mutate()
                    : drawable;
            d.setBounds(0, 0, size, size);
            d.draw(canvas);

            double r = 0, g = 0, b = 0, total = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int p = bmp.getPixel(x, y);
                    int a = Color.alpha(p);
                    if (a < 48) continue;
                    int lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000;
                    if (lum < 36 || lum > 246) continue; // фон иконки не учитываем
                    double k = a / 255.0;
                    r += Color.red(p) * k;
                    g += Color.green(p) * k;
                    b += Color.blue(p) * k;
                    total += k;
                }
            }
            bmp.recycle();
            if (total <= 0) return Color.parseColor("#2A3B55");

            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / total), (int) (g / total), (int) (b / total), hsv);
            hsv[1] = Math.min(1f, Math.max(0.45f, hsv[1] * 1.35f));
            hsv[2] = Math.min(0.66f, Math.max(0.34f, hsv[2]));
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return Color.parseColor("#2A3B55");
        }
    }

    // ---------- погода ----------
    private int lastWeatherTemp = Integer.MIN_VALUE;
    private String lastWeatherCond = "";

    /** Градиент карточки погоды: холод — синеватый, жара — тёплый, плюс общая прозрачность. */
    private void applyWeatherAlpha() {
        try {
            View card = findViewById(R.id.weatherCard);
            if (card == null) return;

            if (!prefBool("weather_bg", false)) {   // фон выключен — только текст и чипы
                card.setBackground(null);
                card.setElevation(0);
                return;
            }
            card.setElevation(dp(6));

            int topColor;
            int bottomColor;
            int temp = lastWeatherTemp;

            if (temp == Integer.MIN_VALUE) {              // данные ещё не пришли
                topColor = 0xFF101826;
                bottomColor = 0xFF0B1220;
            } else if (temp <= -10) {                     // сильный мороз — тёмно-синее стекло
                topColor = 0xFF1B2A44;
                bottomColor = 0xFF0E1726;
            } else if (temp <= 0) {                       // мороз
                topColor = 0xFF1D2C46;
                bottomColor = 0xFF101A29;
            } else if (temp <= 10) {                      // прохладно
                topColor = 0xFF1B2C34;
                bottomColor = 0xFF0F191F;
            } else if (temp <= 20) {                      // тепло
                topColor = 0xFF1A2C26;
                bottomColor = 0xFF0E1A16;
            } else if (temp <= 28) {                      // жарко
                topColor = 0xFF2E2616;
                bottomColor = 0xFF1A150B;
            } else {                                      // очень жарко
                topColor = 0xFF33221A;
                bottomColor = 0xFF1D130E;
            }

            android.graphics.drawable.GradientDrawable base =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                            new int[]{topColor, bottomColor});
            base.setCornerRadius(dp(26));
            base.setStroke(dp(1), 0x33FFFFFF);

            // тонкий блик сверху — стекло, а не заливка
            android.graphics.drawable.GradientDrawable shine =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{0x14FFFFFF, 0x00FFFFFF});
            shine.setCornerRadius(dp(26));

            android.graphics.drawable.LayerDrawable layered =
                    new android.graphics.drawable.LayerDrawable(
                            new android.graphics.drawable.Drawable[]{base, shine});
            layered.setLayerInset(1, dp(1), dp(1), dp(1), dp(1));

            int transp = Math.max(0, Math.min(90, prefInt("weather_alpha", 50)));
            layered.setAlpha(255 - (255 * transp / 100));
            card.setBackground(layered);
        } catch (Throwable ignored) { }
    }

    private void loadWeather() {
        tvCond.setText("загрузка…");
        new Weather().load(new Weather.Listener() {
            @Override public void onWeather(String city, int temp, String condition,
                                            String icon, List<Weather.Day> week) {
                lastWeatherTemp = temp;
                lastWeatherCond = condition;
                applyWeatherAlpha();
                tvTemp.setText(temp + "°");
                tvCond.setText(condition);
                if (tvWeatherIcon != null) tvWeatherIcon.setText(icon);
                tvCity.setText(city);
                weekRow.removeAllViews();
                int count = Math.min(week.size(), 4);
                for (int i = 0; i < count; i++) {
                    Weather.Day d = week.get(i);
                    LinearLayout chip = new LinearLayout(MainActivity.this);
                    chip.setOrientation(LinearLayout.VERTICAL);
                    chip.setGravity(Gravity.CENTER_HORIZONTAL);
                    if (prefBool("weather_bg", false)) {
                        chip.setBackgroundResource(R.drawable.chip_bg);
                    } else {
                        chip.setBackgroundColor(0x0DFFFFFF);   // без карточки чипы почти прозрачные
                    }
                    chip.setPadding(dp(8), dp(6), dp(8), dp(6));
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (i > 0) clp.leftMargin = dp(6);
                    chip.setLayoutParams(clp);

                    TextView name = smallText(d.name, "#A6FFFFFF", 11);
                    TextView hi = smallText(d.max + "°", "#FFFFFFFF", 13);
                    TextView lo = smallText(d.min + "°", "#80FFFFFF", 11);
                    chip.addView(name); chip.addView(hi); chip.addView(lo);
                    weekRow.addView(chip);
                }
            }

            @Override public void onError(String message) {
                tvCond.setText("нет данных");
                tvCity.setText("проверьте сеть");
                lastWeatherTemp = Integer.MIN_VALUE;
                applyWeatherAlpha();
            }
        });
    }

    private TextView smallText(String text, String color, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor(color));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private float scale = 1f;
    private int columns = 5;
    private boolean gridWasEstimated = false;
    private int frameW = 0;

    /** Один раз после включения приставки открывает IPTV-плеер. */
    private void maybeAutostartIptv() {
        try {
            final android.content.SharedPreferences p = prefs();
            long bootId = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
            long prev = p.getLong("boot_id", 0L);
            if (prev != 0L && Math.abs(bootId - prev) < 60000L) return;   // уже запускали в этой загрузке
            p.edit().putLong("boot_id", bootId).apply();
            if (!p.getBoolean("autostart_iptv", true)) return;

            final Intent iptv = getPackageManager().getLaunchIntentForPackage("com.slimiptv.player");
            if (iptv == null) return;

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    try { startActivity(iptv); } catch (Throwable ignored) { }
                }
            }, 8000L);
        } catch (Throwable ignored) { }
    }
    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("slimhome", MODE_PRIVATE);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v * scale);
    }

    /** Короткое интро при холодном старте: частицы, логотип, название. */
    private void playIntro() {
        final android.view.ViewGroup root = findViewById(R.id.root);
        if (root == null) return;

        // не чаще раза в 30 минут: при возврате домой интро не мешает
        long now = System.currentTimeMillis();
        long last = prefs().getLong("intro_at", 0L);
        if (now - last < 30L * 60L * 1000L) return;
        prefs().edit().putLong("intro_at", now).apply();
        final IntroView intro = new IntroView(this);
        intro.setAlpha(1f);
        root.addView(intro, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!intro.finished()) {
                    intro.invalidate();
                    h.postDelayed(this, 16);
                    return;
                }
                intro.animate().alpha(0f).setDuration(320).withEndAction(() -> {
                    root.removeView(intro);
                }).start();
            }
        }, 16);
    }
    /** Подгоняет отступы, ширину карточки погоды, колонки и размеры текста. */
    private void applyResponsiveSizes() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        float wDp = m.widthPixels / m.density;

        // сколько плиток в ряд влезает
        columns = Math.round(wDp / 256f);
        columns = Math.max(3, Math.min(5, columns));

        // ширина рамки плитки: не шире ячейки
        float cellDp = (wDp - 112f - 16f * (columns - 1)) / columns;
        frameW = dp((int) Math.min(200f, cellDp - 6f));

        final View card = findViewById(R.id.weatherCard);
        if (card != null) {
            card.getLayoutParams().width = Math.min(dp(330), Math.round(m.widthPixels * 0.42f));
            card.requestLayout();
        }

        final View wrap = findViewById(R.id.appsWrap);
        if (wrap != null && card != null) {
            // подстраиваем отступ сетки каждый раз, когда меняются размеры карточки погоды
            card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int top = Math.max(card.getBottom() + dp(18), dp(120));
                wrap.setPadding(dp(56), top, dp(56), dp(132));
            });
        }

        int[] ids = {R.id.tvClock, R.id.tvDate, R.id.tvDateNum, R.id.tvNetwork, R.id.tvSettings,
                R.id.tvAllApps, R.id.tvGreeting, R.id.tvWeatherTemp, R.id.tvWeatherCond,
                R.id.tvWeatherCity, R.id.tvDesignBy, R.id.tvAuthor};
        for (int id : ids) {
            TextView tv = findViewById(id);
            if (tv != null) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * scale);
        }
    }

    private void setTextPx(TextView tv, float spValue) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                spValue * getResources().getDisplayMetrics().scaledDensity * scale);
    }

    private int sp(float v) {
        return Math.round(v * scale);
    }

    /** Подгонка под разрешение экрана: тюльпаны, HD, Full HD, 4K. */
    private void applyDisplayScale() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        float wDp = m.widthPixels / m.density;
        float hDp = m.heightPixels / m.density;
        scale = Math.min(wDp / 1280f, hDp / 720f);
        scale = Math.max(0.62f, Math.min(1.15f, scale));
    }

}
