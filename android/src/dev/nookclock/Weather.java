package dev.nookclock;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

/** Current conditions, 24 h hourly and 5-day forecast from Open-Meteo (plain HTTP, no key). */
final class Weather {
    static final int DAYS = 5, HOURS = 25;

    long fetched;
    String timezone;        // e.g. "Europe/Sofia"
    int utcOffset;          // seconds, used if the tz name is unknown to the device
    double temp, feels, wind;
    int humidity, code, isDay;
    final int[] dayYear = new int[DAYS], dayMonth = new int[DAYS], dayOfMonth = new int[DAYS];
    final int[] dayCode = new int[DAYS], dayPop = new int[DAYS];
    final double[] dayMax = new double[DAYS], dayMin = new double[DAYS];
    int hours;              // valid hourly entries, starting with the current hour
    final int[] hourOfDay = new int[HOURS], hourCode = new int[HOURS], hourPop = new int[HOURS];
    final int[] hourIsDay = new int[HOURS];
    final double[] hourTemp = new double[HOURS], hourPrecip = new double[HOURS], hourWind = new double[HOURS];

    /** Blocking GET; the NST's TLS can't reach modern servers, hence http. */
    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        try {
            if (c.getResponseCode() != 200)
                throw new Exception("HTTP " + c.getResponseCode() + " for " + url);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; )
                out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static Weather fetch(Prefs p) throws Exception {
        return parse(get("http://api.open-meteo.com/v1/forecast?latitude=" + p.lat + "&longitude=" + p.lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                + "is_day,weather_code,wind_speed_10m"
                + "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,"
                + "wind_speed_10m,is_day&forecast_hours=" + HOURS
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                + "precipitation_probability_max&timezone=auto&forecast_days=" + DAYS
                + (p.imperial ? "&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch" : "")));
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
            w.dayMax[i] = optDouble(daily, "temperature_2m_max", i);
            w.dayMin[i] = optDouble(daily, "temperature_2m_min", i);
        }

        JSONObject hourly = root.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray ht = hourly.getJSONArray("time");
            for (int i = 0; i < HOURS && i < ht.length(); i++) {
                String s = ht.getString(i);           // "2026-10-20T01:00"
                w.hourOfDay[i] = Integer.parseInt(s.substring(11, 13));
                w.hourTemp[i] = optDouble(hourly, "temperature_2m", i);
                w.hourPrecip[i] = optDouble(hourly, "precipitation", i);
                w.hourWind[i] = optDouble(hourly, "wind_speed_10m", i);
                w.hourPop[i] = optInt(hourly, "precipitation_probability", i, -1);
                w.hourCode[i] = optInt(hourly, "weather_code", i, -1);
                w.hourIsDay[i] = optInt(hourly, "is_day", i, 1);
                w.hours = i + 1;
            }
        }
        w.fetched = System.currentTimeMillis();
        return w;
    }

    static int optInt(JSONObject o, String key, int i, int def) {
        JSONArray a = o.optJSONArray(key);
        return a == null || i >= a.length() || a.isNull(i) ? def : a.optInt(i, def);
    }

    static double optDouble(JSONObject o, String key, int i) {
        JSONArray a = o.optJSONArray(key);
        return a == null || i >= a.length() || a.isNull(i) ? Double.NaN : a.optDouble(i, Double.NaN);
    }

    /**
     * The location's timezone. The Nook's own setting is often wrong (stock
     * firmware defaults to Europe/London), so trust the weather service.
     */
    TimeZone zone() {
        TimeZone tz = timezone == null ? null : TimeZone.getTimeZone(timezone);
        if (tz == null || !tz.getID().equals(timezone))
            tz = new SimpleTimeZone(utcOffset * 1000, "UTC" + utcOffset / 3600);
        return tz;
    }

    /** Day of week (Calendar.SUNDAY..) of forecast day i. */
    int dayOfWeek(int i) {
        return new GregorianCalendar(dayYear[i], dayMonth[i] - 1, dayOfMonth[i]).get(Calendar.DAY_OF_WEEK);
    }

    /** Index into the icon table (same order as tools/genassets.py ICON_NAMES). */
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
