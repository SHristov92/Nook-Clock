package dev.nookclock;

import java.util.Calendar;

import android.graphics.Canvas;

/** Just the time, readable across the room. Also the night-mode screen. */
final class BigClockPage extends Page {
    boolean night;

    @Override
    void draw(Canvas c, Ctx x) {
        Calendar now = x.now;
        float size = x.land ? 300 : 215;
        float y = x.H / 2 + size * 0.36f - 30;
        text(c, hhmm(now, x.prefs.clock24), x.W / 2, y, size, true, BLACK, C);
        fit(c, S.date(now.get(Calendar.DAY_OF_WEEK), now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH)),
            x.W / 2, y + 70, 44, false, BLACK, C, x.W - 40);
        Weather w = x.weather;
        String bottom = w != null ? t(w.temp) + "  " + S.weather(w.code) : "";
        if (night)
            bottom += (bottom.length() > 0 ? "   ·   " : "") + S.get("night");
        text(c, bottom, x.W / 2, x.H - 30, 28, false, GRAY, C);
        battery(c, x, x.W - 16, 26);
    }
}
