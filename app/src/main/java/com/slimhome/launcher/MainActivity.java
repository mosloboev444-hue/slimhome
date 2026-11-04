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

    private TextView tvClock, tvDate, tvDateNum, tvNetwork, tvTemp, tvCond, tvCity;
    private LinearLayout appsGrid, weekRow;
    private final List<View> tiles = new ArrayList<>();
    private View tintA, tintB;
    private TextView tvGreeting, tvAllApps;
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
        applyDisplayScale();
        setContentView(R.layout.activity_main);

        tvClock = findViewById(R.id.tvClock);
        tvDate = findViewById(R.id.tvDate);
        tvDateNum = findViewById(R.id.tvDateNum);
        tvNetwork = findViewById(R.id.tvNetwork);
        tvTemp = findViewById(R.id.tvWeatherTemp);
        tvCond = findViewById(R.id.tvWeatherCond);
        tvCity = findViewById(R.id.tvWeatherCity);
        appsGrid = findViewById(R.id.appsGrid);
        weekRow = findViewById(R.id.weekRow);
        tintA = findViewById(R.id.tintA);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvAllApps = findViewById(R.id.tvAllApps);
        allAppsPanel = findViewById(R.id.allAppsPanel);
        allAppsGrid = findViewById(R.id.allAppsGrid);
        tintB = findViewById(R.id.tintB);

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

        // РїРѕРґРїРёСЃСЊ Р°РІС‚РѕСЂР°: РїРµСЂРµР»РёРІР°СЋС‰РёР№СЃСЏ РіСЂР°РґРёРµРЅС‚
        startShimmer(findViewById(R.id.tvAuthor));
        startShimmer(findViewById(R.id.tvDesignBy));


        if (b == null) playIntro();

        applyResponsiveSizes();
        tick.run();
        updateNetwork();
        buildApps();
        loadWeather();

        maybeAutostartIptv();
    }

    @Override protected void onResume() {
        super.onResume();
        updateNetwork();
        if (tiles.isEmpty()) buildApps();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) { loadWeather(); return true; }
        return super.onKeyDown(code, e);
    }

    // ---------- РїРѕР»РЅС‹Р№ СЃРїРёСЃРѕРє РїСЂРёР»РѕР¶РµРЅРёР№ ----------
    private static String greeting(int h) {
        if (h >= 5 && h < 12) return "Р”РѕР±СЂРѕРµ СѓС‚СЂРѕ";
        if (h >= 12 && h < 17) return "Р”РѕР±СЂС‹Р№ РґРµРЅСЊ";
        if (h >= 17 && h < 23) return "Р”РѕР±СЂС‹Р№ РІРµС‡РµСЂ";
        return "Р”РѕР±СЂРѕР№ РЅРѕС‡Рё";
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

    @Override protected void onDestroy() {
        shimHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (panelOpen) { closeAllApps(); return; }
        super.onBackPressed();
    }

    private void buildAllApps() {
        allAppsGrid.removeAllViews();
        PackageManager pm = getPackageManager();
        final List<String> hidden = java.util.Arrays.asList(
                "com.gathin.tvhome", "com.oranth.tvlauncher", "com.spocky.projengmenu",
                "com.droidlogic.miracast", "com.droidlogic.appinstall",
                "io.github.sumyabazar1.weather");

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

    /** РџРµСЂРµР»РёРІР°РЅРёРµ С†РІРµС‚Р°РјРё РЅР° Handler: СЂР°Р±РѕС‚Р°РµС‚ РґР°Р¶Рµ РєРѕРіРґР° РІ СЃРёСЃС‚РµРјРµ РІС‹РєР»СЋС‡РµРЅС‹ Р°РЅРёРјР°С†РёРё. */
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
    // ---------- СЃРјР°Р№Р»РёРє РїСЂРё РЅР°РІРµРґРµРЅРёРё РЅР° РїРѕРґРїРёСЃСЊ ----------
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
    // ---------- РѕС„РѕСЂРјР»РµРЅРёРµ РєРЅРѕРїРѕРє-РїРёР»СЋР»СЊ ----------
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
    // ---------- РЅР°СЃС‚СЂРѕР№РєРё ----------
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

    // ---------- СЃРµС‚СЊ ----------
    private void updateNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        String label = "РќРµС‚ СЃРµС‚Рё";
        if (info != null && info.isConnected()) {
            label = info.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Ethernet";
        }
        tvNetwork.setText(label);
    }

    // ---------- РїСЂРёР»РѕР¶РµРЅРёСЏ ----------
    private void buildApps() {
        appsGrid.setClipChildren(false);
        appsGrid.setClipToPadding(false);
        appsGrid.removeAllViews();
        tiles.clear();

        PackageManager pm = getPackageManager();

        // СЃС‚Р°СЂС‹Рµ Р»Р°СѓРЅС‡РµСЂС‹ РЅРµ РїРѕРєР°Р·С‹РІР°РµРј, РЅРѕ В«РќР°СЃС‚СЂРѕР№РєРёВ» Рё СЃРёСЃС‚РµРјРЅС‹Рµ СѓС‚РёР»РёС‚С‹ РѕСЃС‚Р°РІР»СЏРµРј
        final List<String> hidden = java.util.Arrays.asList(
                "com.gathin.tvhome",
                "com.oranth.tvlauncher",
                "com.spocky.projengmenu",
                "com.droidlogic.miracast",   // Miracast
                "com.droidlogic.appinstall",  // AppInstaller
                "io.github.sumyabazar1.weather", // РїРѕРіРѕРґР° СѓР¶Рµ РµСЃС‚СЊ РєР°СЂС‚РѕС‡РєРѕР№
                "com.android.gallery3d"     // РіР°Р»РµСЂРµСЏ
        );

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(main, 0));
        found.addAll(pm.queryIntentActivities(lean, 0));

        // Р·Р°РєСЂРµРїР»С‘РЅРЅС‹Рµ РїСЂРёР»РѕР¶РµРЅРёСЏ РёРґСѓС‚ РїРµСЂРІС‹РјРё, РґР°Р»СЊС€Рµ вЂ” РїРѕ Р°Р»С„Р°РІРёС‚Сѓ
        final List<String> pinned = java.util.Arrays.asList(
                "com.slimiptv.player",    // SlimIPTV вЂ” РїРµСЂРІС‹Рј РЅР° РіР»Р°РІРЅРѕРј СЌРєСЂР°РЅРµ
                "ru.rt.video.app.tv",      // Wink
                "tv.limehd.stb",           // Р›Р°Р№Рј HD
                "ru.vk.store.tv",          // RuStore
                "io.github.sumyabazar1.weather" // РїРѕРіРѕРґР°
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

        int perRow = columns;
        int max = perRow * ROWS;
        for (int i = 0; i < Math.min(list.size(), max); i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = dp(14);
            row.setLayoutParams(rlp);
            row.setClipChildren(false);
            row.setClipToPadding(false);

            for (int j = i; j < Math.min(i + perRow, Math.min(list.size(), max)); j++) {
                ResolveInfo ri = list.get(j);
                View tile = makeTile(ri);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(160), 1f);
                if (j > i) tlp.leftMargin = dp(16);
                row.addView(tile, tlp);
                tiles.add(tile);
            }
            // РїСѓСЃС‚С‹Рµ СЏС‡РµР№РєРё, С‡С‚РѕР±С‹ СЂСЏРґ РЅРµ СЂР°СЃС‚СЏРіРёРІР°Р» РїР»РёС‚РєРё
            for (int k = row.getChildCount(); k < perRow; k++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 1, 1f);
                if (k > 0) slp.leftMargin = dp(14);
                row.addView(spacer, slp);
            }
            appsGrid.addView(row);
        }

        // РїРѕСЏРІР»РµРЅРёРµ РїР»РёС‚РѕРє СЃ Р·Р°РґРµСЂР¶РєРѕР№
        for (int n = 0; n < tiles.size(); n++) {
            View tv = tiles.get(n);
            tv.setAlpha(0f);
            tv.setScaleX(0.86f);
            tv.setScaleY(0.86f);
            tv.setTranslationY(dp(18));
            tv.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setStartDelay(45L * n)
                    .setDuration(300L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (!tiles.isEmpty()) tiles.get(0).postDelayed(tiles.get(0)::requestFocus, 380);
    }

    private View makeTile(ResolveInfo ri) {
        PackageManager pm = getPackageManager();

        FrameLayout box = new FrameLayout(this);
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setClickable(true);

        // СЂР°РјРєР° СЂРѕРІРЅРѕ РїРѕ Р»РѕРіРѕС‚РёРїСѓ Рё РЅР°Р·РІР°РЅРёСЋ, РІРЅСѓС‚СЂРё СЃРІРѕРµР№ СЏС‡РµР№РєРё
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setBackgroundResource(R.drawable.tile_min);
        inner.setPadding(dp(12), dp(10), dp(12), dp(10));

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                frameW > 0 ? frameW : dp(200), ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER;
        box.addView(inner, flp);

        ImageView icon = new ImageView(this);
        Drawable d = ri.loadIcon(pm);
        icon.setImageDrawable(d);
        inner.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView label = new TextView(this);
        label.setText(ri.loadLabel(pm));
        label.setTextColor(Color.WHITE);
        setTextPx(label, 18f);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setMaxWidth(dp(108));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        llp.leftMargin = dp(10);
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

    // ---------- РїРѕРґСЃРІРµС‚РєР° С„РѕРЅР° С†РІРµС‚РѕРј РїСЂРёР»РѕР¶РµРЅРёСЏ ----------
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
        g.setColors(new int[]{withAlpha(color, 0xD9), withAlpha(color, 0x00)});
        next.setBackground(g);
        next.animate().alpha(0.62f).setDuration(260).start();
        prev.animate().alpha(0f).setDuration(260).start();
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** РЎСЂРµРґРЅРёР№ С†РІРµС‚ РёРєРѕРЅРєРё, РїРѕРґС‚СЏРЅСѓС‚С‹Р№ РїРѕ РЅР°СЃС‹С‰РµРЅРЅРѕСЃС‚Рё. */
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
                    if (lum < 36 || lum > 246) continue; // С„РѕРЅ РёРєРѕРЅРєРё РЅРµ СѓС‡РёС‚С‹РІР°РµРј
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

    // ---------- РїРѕРіРѕРґР° ----------
    private void loadWeather() {
        tvCond.setText("Р·Р°РіСЂСѓР·РєР°вЂ¦");
        new Weather().load(new Weather.Listener() {
            @Override public void onWeather(String city, int temp, String condition,
                                            String icon, List<Weather.Day> week) {
                tvTemp.setText(temp + "В°");
                tvCond.setText(icon + "  " + condition);
                tvCity.setText(city);
                weekRow.removeAllViews();
                int count = Math.min(week.size(), 7);
                for (int i = 0; i < count; i++) {
                    Weather.Day d = week.get(i);
                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    col.setLayoutParams(clp);

                    TextView name = smallText(d.name, "#B3FFFFFF", 16);
                    TextView ic = smallText(d.icon, "#FFFFFFFF", 19);
                    TextView hi = smallText(d.max + "В°", "#FFFFFFFF", 16);
                    TextView lo = smallText(d.min + "В°", "#80FFFFFF", 15);
                    col.addView(name); col.addView(ic); col.addView(hi); col.addView(lo);
                    weekRow.addView(col);
                }
            }

            @Override public void onError(String message) {
                tvCond.setText("РЅРµС‚ РґР°РЅРЅС‹С…");
                tvCity.setText("РїСЂРѕРІРµСЂСЊС‚Рµ СЃРµС‚СЊ");
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
    private int frameW = 0;

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("slimhome", MODE_PRIVATE);
    }

    /** Один раз после включения приставки открывает IPTV-плеер. */
    private void maybeAutostartIptv() {
        try {
            final android.content.SharedPreferences p = prefs();
            long bootId = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
            if (p.getLong("boot_id", 0L) == bootId) return;
            p.edit().putLong("boot_id", bootId).apply();
            if (!p.getBoolean("autostart_iptv", true)) return;

            final android.content.Intent iptv = getPackageManager()
                    .getLaunchIntentForPackage("com.slimiptv.player");
            if (iptv == null) return;

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    try { startActivity(iptv); } catch (Throwable ignored) { }
                }
            }, 8000L);
        } catch (Throwable ignored) { }
    }
    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v * scale);
    }

    /** РљРѕСЂРѕС‚РєРѕРµ РёРЅС‚СЂРѕ РїСЂРё С…РѕР»РѕРґРЅРѕРј СЃС‚Р°СЂС‚Рµ: С‡Р°СЃС‚РёС†С‹, Р»РѕРіРѕС‚РёРї, РЅР°Р·РІР°РЅРёРµ. */
    private void playIntro() {
        final android.view.ViewGroup root = findViewById(R.id.root);
        if (root == null) return;

        // РЅРµ С‡Р°С‰Рµ СЂР°Р·Р° РІ 30 РјРёРЅСѓС‚: РїСЂРё РІРѕР·РІСЂР°С‚Рµ РґРѕРјРѕР№ РёРЅС‚СЂРѕ РЅРµ РјРµС€Р°РµС‚
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
    /** РџРѕРґРіРѕРЅСЏРµС‚ РѕС‚СЃС‚СѓРїС‹, С€РёСЂРёРЅСѓ РєР°СЂС‚РѕС‡РєРё РїРѕРіРѕРґС‹, РєРѕР»РѕРЅРєРё Рё СЂР°Р·РјРµСЂС‹ С‚РµРєСЃС‚Р°. */
    private void applyResponsiveSizes() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        float wDp = m.widthPixels / m.density;

        // СЃРєРѕР»СЊРєРѕ РїР»РёС‚РѕРє РІ СЂСЏРґ РІР»РµР·Р°РµС‚
        columns = Math.round(wDp / 256f);
        columns = Math.max(3, Math.min(5, columns));

        // С€РёСЂРёРЅР° СЂР°РјРєРё РїР»РёС‚РєРё: РЅРµ С€РёСЂРµ СЏС‡РµР№РєРё
        float cellDp = (wDp - 112f - 16f * (columns - 1)) / columns;
        frameW = dp((int) Math.min(200f, cellDp - 6f));

        final View card = findViewById(R.id.weatherCard);
        if (card != null) {
            card.getLayoutParams().width = Math.min(dp(330), Math.round(m.widthPixels * 0.42f));
            card.requestLayout();
        }

        final View wrap = findViewById(R.id.appsWrap);
        if (wrap != null && card != null) {
            // РїРѕРґСЃС‚СЂР°РёРІР°РµРј РѕС‚СЃС‚СѓРї СЃРµС‚РєРё РєР°Р¶РґС‹Р№ СЂР°Р·, РєРѕРіРґР° РјРµРЅСЏСЋС‚СЃСЏ СЂР°Р·РјРµСЂС‹ РєР°СЂС‚РѕС‡РєРё РїРѕРіРѕРґС‹
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

    /** РџРѕРґРіРѕРЅРєР° РїРѕРґ СЂР°Р·СЂРµС€РµРЅРёРµ СЌРєСЂР°РЅР°: С‚СЋР»СЊРїР°РЅС‹, HD, Full HD, 4K. */
    private void applyDisplayScale() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        float wDp = m.widthPixels / m.density;
        float hDp = m.heightPixels / m.density;
        scale = Math.min(wDp / 1280f, hDp / 720f);
        scale = Math.max(0.62f, Math.min(1.15f, scale));
    }

}
