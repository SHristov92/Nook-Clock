package dev.nookclock;

import android.graphics.Canvas;

/** Air quality: European AQI, particulates vs. WHO 24-h guidelines, pollen, 24-h AQI bars. */
final class AirPage extends Page {
    @Override
    void draw(Canvas c, Ctx x) {
        float top = header(c, x, S.get("air"));
        Air a = x.air;
        if (a == null || a.aqi < 0) {
            text(c, S.get("nodata"), x.W / 2, x.H / 2, 34, false, GRAY, C);
            return;
        }
        boolean land = x.land;
        int W = x.W;

        // big index + band
        float bx = land ? 150 : W / 2f, by = top + 130;
        text(c, String.valueOf(a.aqi), bx, by, 120, true, BLACK, C);
        fit(c, S.aqi(a.aqi), bx, by + 46, 34, true, BLACK, C, land ? 260 : W - 60);
        text(c, S.get("aqi"), bx, by + 78, 20, false, GRAY, C);

        // pollutants: value + bar against the WHO 24-hour guideline (marked)
        float px = land ? 330 : 40, py = land ? top + 40 : by + 130, pw = W - 40 - px;
        py = pollutant(c, "PM2.5", a.pm25, 15, px, py, pw);
        py = pollutant(c, "PM10", a.pm10, 45, px, py, pw);
        py = pollutant(c, "NO₂", a.no2, 25, px, py, pw);
        py = pollutant(c, "O₃", a.o3, 100, px, py, pw);

        // pollen
        float ly = land ? Math.max(py, by + 100) + 10 : py + 10;
        text(c, S.get("pollen"), 40, ly + 24, 26, true, BLACK, L);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < S.POLLEN.length; i++) {
            double v = a.pollen[i];
            if (Double.isNaN(v) || v < 1)
                continue;
            if (sb.length() > 0) sb.append("   ");
            sb.append(S.POLLEN[i][S.bg() ? 2 : 1]).append(": ").append(S.pollenLevel(v));
        }
        fit(c, sb.length() == 0 ? S.get("nopollen") : sb.toString(), 40 + width(S.get("pollen"), 26, true) + 16,
            ly + 24, 24, false, GRAY, L, W - 100 - width(S.get("pollen"), 26, true));

        // next 24 h AQI
        float cTop = ly + 50, cBot = x.H - 40;
        if (a.hours < 2 || cBot - cTop < 60)
            return;
        text(c, S.get("aqi24"), 40, cTop + 4, 20, false, GRAY, L);
        cTop += 14;
        int max = 60;
        for (int i = 0; i < a.hours; i++) max = Math.max(max, a.hourAqi[i]);
        float left = 50, right = W - 40, step = (right - left) / a.hours;
        rect(c, left, cBot, right, cBot + 1, GRAY);
        for (int i = 0; i < a.hours; i++) {
            int v = a.hourAqi[i];
            float cx = left + i * step + step / 2;
            if (v > 0) {
                float bh = (cBot - cTop) * v / max;
                rect(c, cx - step * 0.35f, cBot - bh, cx + step * 0.35f, cBot, v >= 60 ? BLACK : v >= 40 ? GRAY : LIGHT);
            }
            if (i % 3 == 0)
                text(c, String.format("%02d", a.hourOfDay[i]), cx, cBot + 24, 18, false, GRAY, C);
        }
    }

    /** Draws "PM2.5  28 µg/m³" with a bar; the tick marks the guideline. Returns next y. */
    private float pollutant(Canvas c, String name, double v, double guideline, float x, float y, float w) {
        if (Double.isNaN(v))
            return y;
        text(c, name, x, y + 26, 26, true, BLACK, L);
        text(c, Math.round(v) + " µg/m³", x + w, y + 26, 24, false, BLACK, R);
        float bx = x, bw = w, scale = (float) (guideline * 3);
        float fillW = (float) Math.min(1, v / scale) * bw;
        frame(c, bx, y + 36, bx + bw, y + 52, GRAY, 1.5f);
        rect(c, bx, y + 36, bx + fillW, y + 52, v > guideline ? BLACK : GRAY);
        float gx = bx + bw / 3;
        rect(c, gx - 1, y + 32, gx + 1, y + 56, BLACK);
        return y + 70;
    }
}
