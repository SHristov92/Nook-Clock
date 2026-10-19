package dev.nookclock;

import java.util.Calendar;
import java.util.GregorianCalendar;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/** Main screen: big clock, date, current weather, 4-day forecast. */
final class ClockPage extends Page {
    @Override
    void draw(Canvas c, Ctx x) {
        Weather w = x.weather;
        Prefs p = x.prefs;
        int W = x.W;
        boolean land = x.land;
        Calendar now = x.now;

        String left = p.place;
        if (w != null && x.status == null) {
            Calendar f = new GregorianCalendar(x.tz);
            f.setTimeInMillis(w.fetched);
            left += "  " + S.get("updated") + " " + hhmm(f, true);
        }
        statusLine(c, x, left);

        // clock + date
        int h = now.get(Calendar.HOUR_OF_DAY);
        float y = land ? 215 : 205;
        float cx = text(c, hhmm(now, p.clock24), W / 2, y, land ? 220 : 175, true, BLACK, C);
        if (!p.clock24)
            text(c, h < 12 ? "AM" : "PM", cx + 8, y, 30, true, BLACK, L);
        fit(c, S.date(now.get(Calendar.DAY_OF_WEEK), now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH)),
            W / 2, y + 52, 40, false, BLACK, C, W - 40);
        rect(c, 30, y + 72, W - 30, y + 74, GRAY);

        // current conditions
        y += 82;
        if (w == null || System.currentTimeMillis() - w.fetched > 3 * 3600 * 1000L) {
            text(c, S.get(w == null ? "waiting" : "stale"), W / 2, y + 110, 36, false, GRAY, C);
            return;
        }
        String wind = S.get("wind") + " " + Math.round(w.wind) + " " + S.get(p.imperial ? "mph" : "kmh");
        String hum = S.get("humidity") + " " + w.humidity + "%";
        String hilo = S.get("high") + " " + t(w.dayMax[0]) + "  " + S.get("low") + " " + t(w.dayMin[0]);
        icon(c, x.iconL[Weather.icon(w.code, w.isDay != 0)], 24, y + 4, 170);
        float tx = text(c, t(w.temp), 205, y + 122, 96, true, BLACK, L);
        if (land) {
            tx = Math.max(tx + 30, 440);
            float mw = W - 20 - tx;
            fit(c, S.weather(w.code), tx, y + 36, 30, true, BLACK, L, mw);
            fit(c, S.get("feels") + " " + t(w.feels), tx, y + 70, 30, false, BLACK, L, mw);
            fit(c, hilo, tx, y + 104, 30, false, BLACK, L, mw);
            fit(c, hum, tx, y + 138, 30, false, GRAY, L, mw);
            fit(c, wind, tx, y + 172, 30, false, GRAY, L, mw);
            y += 185;
        } else {
            fit(c, S.weather(w.code), 205, y + 162, 30, true, BLACK, L, W - 225);
            fit(c, S.get("feels") + " " + t(w.feels) + "   " + hilo, 30, y + 222, 30, false, BLACK, L, W - 60);
            fit(c, hum + "   " + wind, 30, y + 260, 30, false, GRAY, L, W - 60);
            y += 282;
        }
        rect(c, 30, y, W - 30, y + 2, GRAY);

        // forecast: the next four days
        int n = Weather.DAYS - 1, cw = W / n;
        for (int i = 1; i <= n; i++) {
            if (w.dayYear[i] == 0)
                continue;
            float dx = cw * (i - 1) + cw / 2;
            int dow = w.dayOfWeek(i);
            fit(c, land ? S.cap(S.weekday(dow)) : S.weekdayShort(dow), dx, y + 36, 30, true, BLACK, C, cw - 8);
            Bitmap ic = x.iconS[Weather.icon(w.dayCode[i], true)];
            String pop = w.dayPop[i] >= 0 ? S.get("rain") + " " + w.dayPop[i] + "%" : "";
            if (land) {
                icon(c, ic, dx - 92, y + 46, 76);
                float ex = text(c, t(w.dayMax[i]), dx - 10, y + 82, 30, true, BLACK, L);
                text(c, t(w.dayMin[i]), ex + 4, y + 82, 30, false, GRAY, L);
                text(c, pop, dx - 10, y + 112, 22, false, GRAY, L);
            } else {
                icon(c, ic, dx - 38, y + 46, 76);
                text(c, t(w.dayMax[i]) + " / " + t(w.dayMin[i]), dx, y + 156, 30, false, BLACK, C);
                text(c, pop, dx, y + 188, 22, false, GRAY, C);
            }
        }
    }
}
