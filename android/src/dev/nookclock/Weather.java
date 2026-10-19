package dev.nookclock;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

/** Current conditions + daily forecast from Open-Meteo (plain HTTP, no key). */
final class Weather {
    static final int DAYS = 5;

    long fetched;
    String timezone;        // e.g. "Europe/Sofia"
    int utcOffset;          // seconds, used if the tz name is unknown to the device
    double temp, feels, wind;
    int humidity, code, isDay;
    final int[] dayYear = new int[DAYS], dayMonth = new int[DAYS], dayOfMonth = new int[DAYS];
    final int[] dayCode = new int[DAYS], dayPop = new int[DAYS];
    final double[] dayMax = new double[DAYS], dayMin = new double[DAYS];

    /** Blocking; call off the UI thread. The NST's TLS can't reach modern servers, hence http. */
    static Weather fetch(String lat, String lon, boolean imperial) throws Exception {
        String url = "http://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                + "is_day,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                + "precipitation_probability_max&timezone=auto&forecast_days=" + DAYS
                + (imperial ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        try {
            if (c.getResponseCode() != 200)
                throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; )
                out.write(buf, 0, n);
            return parse(out.toString("UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    static Weather parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject cur = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        Weather w = new Weather();
        w.timezone = root.optString("timezone", null);
        w.utcOffset = root.optInt("utc_offset_seconds", 0);
        w.temp = cur.getDouble("temperature_2m");
        w.feels = cur.optDouble("apparent_temperature", w.temp);
        w.wind = cur.optDouble("wind_speed_10m", 0);
        w.humidity = cur.optInt("relative_humidity_2m", -1);
        w.code = cur.optInt("weather_code", -1);
        w.isDay = cur.optInt("is_day", 1);

        JSONArray time = daily.getJSONArray("time");
        for (int i = 0; i < DAYS && i < time.length(); i++) {
            String[] ymd = time.getString(i).split("-");
            w.dayYear[i] = Integer.parseInt(ymd[0]);
            w.dayMonth[i] = Integer.parseInt(ymd[1]);
            w.dayOfMonth[i] = Integer.parseInt(ymd[2]);
            w.dayCode[i] = optInt(daily, "weather_code", i, -1);
            w.dayPop[i] = optInt(daily, "precipitation_probability_max", i, -1);
            w.dayMax[i] = daily.getJSONArray("temperature_2m_max").optDouble(i, Double.NaN);
            w.dayMin[i] = daily.getJSONArray("temperature_2m_min").optDouble(i, Double.NaN);
        }
        w.fetched = System.currentTimeMillis();
        return w;
    }

    /**
     * The location's timezone. The Nook's own setting is often wrong (stock
     * firmware defaults to Europe/London), so trust the weather service.
     */
    java.util.TimeZone zone() {
        java.util.TimeZone tz = timezone == null ? null : java.util.TimeZone.getTimeZone(timezone);
        if (tz == null || !tz.getID().equals(timezone))
            tz = new java.util.SimpleTimeZone(utcOffset * 1000, "UTC" + utcOffset / 3600);
        return tz;
    }

    private static int optInt(JSONObject o, String key, int i, int def) {
        JSONArray a = o.optJSONArray(key);
        return a == null || a.isNull(i) ? def : a.optInt(i, def);
    }

    static String describe(int c) {
        switch (c) {
        case 0: return "Clear";
        case 1: return "Mostly clear";
        case 2: return "Partly cloudy";
        case 3: return "Overcast";
        case 45: case 48: return "Fog";
        case 51: case 53: case 55: return "Drizzle";
        case 56: case 57: return "Freezing drizzle";
        case 61: return "Light rain";
        case 63: return "Rain";
        case 65: return "Heavy rain";
        case 66: case 67: return "Freezing rain";
        case 71: return "Light snow";
        case 73: return "Snow";
        case 75: return "Heavy snow";
        case 77: return "Snow grains";
        case 80: case 81: return "Showers";
        case 82: return "Heavy showers";
        case 85: case 86: return "Snow showers";
        case 95: return "Thunderstorm";
        case 96: case 99: return "Thunder & hail";
        default: return "Unknown";
        }
    }

    /** Index into ClockView's icon table (same order as tools/genassets.py ICON_NAMES). */
    static int icon(int c, boolean day) {
        if (c <= 1) return day ? 0 : 1;
        if (c == 2) return day ? 2 : 3;
        if (c == 45 || c == 48) return 5;
        if (c >= 51 && c <= 57) return 6;
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return 7;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return 8;
        if (c >= 95) return 9;
        return 4;
    }
}
