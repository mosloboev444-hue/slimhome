package com.slimhome.launcher;

import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Погода: город по IP + прогноз (wttr.in, при недоступности — open-meteo). */
public class Weather {

    public static class Day {
        public String name;   // Пн
        public String icon;   // ☀
        public int max;
        public int min;
    }

    public interface Listener {
        void onWeather(String city, int temp, String condition, String icon, List<Day> week);
        void onError(String message);
    }

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    public void load(final Listener listener) {
        new Thread(() -> {
            try {
                double lat = 55.7558, lon = 37.6173;
                String city = "Москва";
                try {
                    JSONObject o = new JSONObject(http("http://ip-api.com/json/?fields=status,city,lat,lon&lang=ru"));
                    if ("success".equals(o.optString("status"))) {
                        city = o.optString("city", city);
                        lat = o.optDouble("lat", lat);
                        lon = o.optDouble("lon", lon);
                    }
                } catch (Exception ignored) { }

                String cityOut = city;
                int temp;
                String cond, icon;
                List<Day> week;

                try {
                    JSONObject root = new JSONObject(http("https://wttr.in/"
                            + URLEncoder.encode(city, "UTF-8") + "?format=j1&lang=ru"));
                    JSONObject cur = root.getJSONArray("current_condition").getJSONObject(0);
                    temp = num(cur.optString("temp_C", "0"));
                    int code = num(cur.optString("weatherCode", "113"));
                    cond = conditionWwo(code);
                    icon = iconWwo(code);

                    JSONArray days = root.getJSONArray("weather");
                    week = new ArrayList<>();
                    for (int i = 0; i < days.length() && i < 7; i++) {
                        JSONObject d = days.getJSONObject(i);
                        Day day = new Day();
                        day.name = weekday(d.optString("date", ""));
                        day.max = num(d.optString("maxtempC", "0"));
                        day.min = num(d.optString("mintempC", "0"));
                        int dc = code;
                        try {
                            JSONArray h = d.getJSONArray("hourly");
                            dc = num(h.getJSONObject(h.length() / 2)
                                    .optString("weatherCode", "113"));
                        } catch (Exception ignored) { }
                        day.icon = iconWwo(dc);
                        week.add(day);
                    }
                } catch (Exception e) {
                    // запасной источник
                    String url = String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                    + "&current=temperature_2m,weather_code"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                    + "&timezone=auto&forecast_days=7", lat, lon);
                    JSONObject root = new JSONObject(http(url));
                    JSONObject cur = root.getJSONObject("current");
                    temp = (int) Math.round(cur.getDouble("temperature_2m"));
                    int code = cur.optInt("weather_code", 0);
                    cond = conditionFor(code);
                    icon = iconFor(code);

                    JSONObject daily = root.getJSONObject("daily");
                    JSONArray codes = daily.getJSONArray("weather_code");
                    JSONArray maxs = daily.getJSONArray("temperature_2m_max");
                    JSONArray mins = daily.getJSONArray("temperature_2m_min");
                    String[] names = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
                    Calendar cal = Calendar.getInstance();
                    week = new ArrayList<>();
                    for (int i = 0; i < codes.length(); i++) {
                        Day d = new Day();
                        d.name = names[(cal.get(Calendar.DAY_OF_WEEK) - 2 + i + 7) % 7];
                        d.icon = iconFor(codes.optInt(i));
                        d.max = (int) Math.round(maxs.getDouble(i));
                        d.min = (int) Math.round(mins.getDouble(i));
                        week.add(d);
                    }
                }

                final int t = temp;
                final String c = cond, ic = icon;
                final List<Day> w = week;
                ui.post(() -> listener.onWeather(cityOut, t, c, ic, w));
            } catch (Exception e) {
                android.util.Log.e("WEATHER", "fail", e);
                ui.post(() -> listener.onError(e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }).start();
    }

    private static int num(String s) {
        try { return (int) Math.round(Double.parseDouble(s.trim())); }
        catch (Exception e) { return 0; }
    }

    private static String http(String spec) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(spec).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "SlimHome/0.2 (Android TV)");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String weekday(String isoDate) {
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            java.util.Date d = in.parse(isoDate);
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("EE", new Locale("ru"));
            String s = out.format(d);
            return s.substring(0, 1).toUpperCase(new Locale("ru")) + s.substring(1);
        } catch (Exception e) {
            return "";
        }
    }

    // --- коды WWO (wttr.in) ---
    static String conditionWwo(int code) {
        if (code == 113) return "Ясно";
        if (code == 116) return "Малооблачно";
        if (code == 119 || code == 122) return "Облачно";
        if (code == 143 || code == 248 || code == 260) return "Туман";
        if (code >= 200 && code <= 232) return "Гроза";
        if (code == 227 || code == 230) return "Метель";
        if (code >= 317 && code <= 338) return "Снег";
        if (code >= 368 && code <= 377) return "Снег";
        if (code == 179 || code == 182 || code == 185) return "Мокрый снег";
        if (code == 281 || code == 284 || code == 311 || code == 314) return "Ледяной дождь";
        if (code >= 293 && code <= 308) return "Дождь";
        if (code >= 353 && code <= 359) return "Ливень";
        if (code >= 386 && code <= 395) return "Гроза со снегом";
        return "Облачно";
    }

    static String iconWwo(int code) {
        if (code == 113) return "☀";
        if (code == 116) return "🌤";
        if (code == 119 || code == 122) return "☁";
        if (code == 143 || code == 248 || code == 260) return "🌫";
        if (code >= 200 && code <= 232) return "⛈";
        if (code == 227 || code == 230) return "🌨";
        if (code >= 317 && code <= 338) return "❄";
        if (code >= 368 && code <= 377) return "❄";
        if (code == 179 || code == 182 || code == 185) return "🌨";
        if (code == 281 || code == 284 || code == 311 || code == 314) return "🌧";
        if (code >= 293 && code <= 308) return "🌧";
        if (code >= 353 && code <= 359) return "🌦";
        if (code >= 386 && code <= 395) return "⛈";
        return "☁";
    }

    // --- коды WMO (open-meteo) ---
    static String conditionFor(int code) {
        if (code == 0) return "Ясно";
        if (code <= 2) return "Малооблачно";
        if (code == 3) return "Облачно";
        if (code <= 48) return "Туман";
        if (code <= 57) return "Морось";
        if (code <= 67) return "Дождь";
        if (code <= 77) return "Снег";
        if (code <= 82) return "Ливень";
        if (code <= 86) return "Снегопад";
        return "Гроза";
    }

    static String iconFor(int code) {
        if (code == 0) return "☀";
        if (code <= 2) return "🌤";
        if (code == 3) return "☁";
        if (code <= 48) return "🌫";
        if (code <= 57) return "🌦";
        if (code <= 67) return "🌧";
        if (code <= 77) return "🌨";
        if (code <= 82) return "🌧";
        if (code <= 86) return "❄";
        return "⛈";
    }

    static Typeface iconTypeface() {
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }
}