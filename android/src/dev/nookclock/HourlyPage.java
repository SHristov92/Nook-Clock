package dev.nookclock;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;

/** Next 24 hours: temperature curve with icons, rain-chance bars, wind. */
final class HourlyPage extends Page {
    private final Paint curve = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    void draw(Canvas c, Ctx x) {
        float top = header(c, x, S.get("next24"));
        Weather w = x.weather;
        if (w == null || w.hours < 2) {
            text(c, S.get("waiting"), x.W / 2, x.H / 2, 34, false, GRAY, C);
            return;
        }
        int n = Math.min(w.hours, 25);
        float left = 70, right = x.W - 30, step = (right - left) / (n - 1);

        // vertical layout (portrait gets taller bands)
        float iconY = top + 10, iconS = 52;
        float tTop = iconY + iconS + 40, tBot = tTop + (x.land ? 170 : 300);
        float pTop = tBot + 50, pBot = pTop + (x.land ? 90 : 150);
        float labelY = pBot + 30;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(w.hourTemp[i])) continue;
            min = Math.min(min, w.hourTemp[i]);
            max = Math.max(max, w.hourTemp[i]);
        }
        if (max - min < 4) { double mid = (max + min) / 2; min = mid - 2; max = mid + 2; }

        // temperature curve
        Path path = new Path();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(w.hourTemp[i])) continue;
            float px = left + i * step;
            float py = (float) (tBot - (w.hourTemp[i] - min) / (max - min) * (tBot - tTop));
            if (!started) { path.moveTo(px, py); started = true; } else path.lineTo(px, py);
        }
        curve.setStyle(Paint.Style.STROKE);
        curve.setStrokeWidth(4);
        curve.setColor(BLACK);
        curve.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(path, curve);
        rect(c, left, tBot + 6, right, tBot + 7, LIGHT);

        // every 3 hours: icon, temperature label, dot, hour label
        for (int i = 0; i < n; i += 3) {
            float px = left + i * step;
            icon(c, x.iconS[Weather.icon(w.hourCode[i], w.hourIsDay[i] != 0)], px - iconS / 2, iconY, iconS);
            if (!Double.isNaN(w.hourTemp[i])) {
                float py = (float) (tBot - (w.hourTemp[i] - min) / (max - min) * (tBot - tTop));
                dot(c, px, py, 6, BLACK);
                text(c, t(w.hourTemp[i]), px, py - 14, 24, true, BLACK, C);
            }
            text(c, String.format("%02d", w.hourOfDay[i]), px, labelY, 22, i == 0, i == 0 ? BLACK : GRAY, C);
        }

        // rain chance bars (+ amount when it rains)
        text(c, S.get("rainchance"), left, pTop - 12, 20, false, GRAY, L);
        rect(c, left - step / 2, pBot, right + step / 2, pBot + 1, GRAY);
        for (int i = 0; i < n; i++) {
            int pop = w.hourPop[i];
            if (pop <= 0) continue;
            float px = left + i * step, bh = (pBot - pTop) * pop / 100f;
            rect(c, px - step * 0.35f, pBot - bh, px + step * 0.35f, pBot, pop >= 50 ? GRAY : LIGHT);
        }
        for (int i = 0; i < n; i += 3)
            if (w.hourPop[i] > 0)
                text(c, w.hourPop[i] + "%", left + i * step, pBot - 6 - (pBot - pTop) * w.hourPop[i] / 100f, 18, false, BLACK, C);

        // summary line: max rain / wind
        double rain = 0, wind = 0;
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(w.hourPrecip[i])) rain += w.hourPrecip[i];
            if (!Double.isNaN(w.hourWind[i])) wind = Math.max(wind, w.hourWind[i]);
        }
        String unit = x.prefs.imperial ? "in" : S.get("mm");
        String sum = S.get("rain") + " " + (x.prefs.imperial ? String.format("%.2f", rain) : String.format("%.1f", rain))
                + " " + unit + "    " + S.get("wind") + " " + (S.bg() ? "до " : "up to ") + Math.round(wind) + " "
                + S.get(x.prefs.imperial ? "mph" : "kmh");
        text(c, sum, x.W / 2, Math.min(labelY + 40, x.H - 10), 24, false, GRAY, C);
    }
}
