package dev.nookclock;

import org.json.JSONObject;

/** Air quality + pollen from the Open-Meteo air-quality API (CAMS Europe). */
final class Air {
    static final int HOURS = 25;

    long fetched;
    int aqi = -1;
    double pm25 = Double.NaN, pm10 = Double.NaN, no2 = Double.NaN, o3 = Double.NaN;
    final double[] pollen = new double[S.POLLEN.length];
    int hours;
    final int[] hourOfDay = new int[HOURS], hourAqi = new int[HOURS];

    static Air fetch(Prefs p) throws Exception {
        StringBuilder pol = new StringBuilder();
        for (String[] k : S.POLLEN)
            pol.append(',').append(k[0]);
        JSONObject root = new JSONObject(Weather.get(
                "http://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + p.lat
                + "&longitude=" + p.lon + "&current=european_aqi,pm2_5,pm10,nitrogen_dioxide,ozone" + pol
                + "&hourly=european_aqi&forecast_hours=" + HOURS + "&timezone=auto"));
        JSONObject cur = root.getJSONObject("current");
        Air a = new Air();
        a.aqi = cur.optInt("european_aqi", -1);
        a.pm25 = cur.optDouble("pm2_5", Double.NaN);
        a.pm10 = cur.optDouble("pm10", Double.NaN);
        a.no2 = cur.optDouble("nitrogen_dioxide", Double.NaN);
        a.o3 = cur.optDouble("ozone", Double.NaN);
        for (int i = 0; i < S.POLLEN.length; i++)
            a.pollen[i] = cur.isNull(S.POLLEN[i][0]) ? Double.NaN : cur.optDouble(S.POLLEN[i][0], Double.NaN);
        JSONObject h = root.optJSONObject("hourly");
        if (h != null) {
            org.json.JSONArray t = h.getJSONArray("time");
            for (int i = 0; i < HOURS && i < t.length(); i++) {
                a.hourOfDay[i] = Integer.parseInt(t.getString(i).substring(11, 13));
                a.hourAqi[i] = Weather.optInt(h, "european_aqi", i, -1);
                a.hours = i + 1;
            }
        }
        a.fetched = System.currentTimeMillis();
        return a;
    }
}
