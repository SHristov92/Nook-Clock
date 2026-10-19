package dev.nookclock;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Color;

/** Month grid with Bulgarian holidays, today's name days and upcoming days off. */
final class CalendarPage extends Page {
    @Override
    void draw(Canvas c, Ctx x) {
        Calendar now = x.now;
        int year = now.get(Calendar.YEAR), month = now.get(Calendar.MONTH), today = now.get(Calendar.DAY_OF_MONTH);
        float top = header(c, x, S.cap(S.month(month)) + " " + year);
        List<BgCalendar.Day> hol = BgCalendar.holidays(year);

        // grid: Monday-first weeks
        float gl = 30, gr = x.land ? 500 : x.W - 30, gt = top + 14;
        float cw = (gr - gl) / 7, ch = x.land ? 66 : 64;
        for (int d = 0; d < 7; d++) {
            int dow = (d + 1) % 7 + 1;          // Mon..Sun as Calendar constants
            text(c, S.weekdayShort(dow), gl + d * cw + cw / 2, gt + 24, 22, true, d >= 5 ? GRAY : BLACK, C);
        }
        gt += 36;
        Calendar day = new GregorianCalendar(year, month, 1);
        int offset = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;     // Monday = 0
        int days = day.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int d = 1; d <= days; d++) {
            int cell = offset + d - 1, col = cell % 7, row = cell / 7;
            float l = gl + col * cw, t = gt + row * ch;
            day.set(Calendar.DAY_OF_MONTH, d);
            boolean off = BgCalendar.find(hol, day) != null, we = col >= 5;
            if (d == today) {
                rect(c, l + 3, t + 3, l + cw - 3, t + ch - 3, BLACK);
                text(c, String.valueOf(d), l + cw / 2, t + ch / 2 + 11, 32, true, Color.WHITE, C);
            } else {
                if (off)
                    frame(c, l + 4, t + 4, l + cw - 4, t + ch - 4, BLACK, 2);
                text(c, String.valueOf(d), l + cw / 2, t + ch / 2 + 11, 30, off, we && !off ? GRAY : BLACK, C);
            }
        }

        // side panel (landscape) or bottom panel (portrait)
        float px = x.land ? 530 : 30, py = x.land ? top + 30 : gt + 6 * ch + 20, pw = x.W - 30 - px;
        text(c, S.get("today"), px, py, 26, true, BLACK, L);
        BgCalendar.Day h = BgCalendar.find(hol, now);
        py += 34;
        if (h != null) {
            py = wrap(c, h.name(), px, py, pw, 24, true, BLACK);
        }
        String names = BgCalendar.nameDays(now);
        if (names != null) {
            text(c, S.get("namedays") + ":", px, py, 22, false, GRAY, L);
            py = wrap(c, names, px, py + 28, pw, 24, false, BLACK);
        } else if (h == null) {
            // nothing today: show the next name days instead
            text(c, S.get("upcoming") + ":", px, py, 22, false, GRAY, L);
            py += 30;
            for (Calendar d : BgCalendar.upcomingNameDays(now, 2)) {
                text(c, d.get(Calendar.DAY_OF_MONTH) + " " + shortMonth(d.get(Calendar.MONTH)), px, py, 22, true, BLACK, L);
                py = wrap(c, BgCalendar.nameDays(d), px + 80, py, pw - 80, 22, false, BLACK);
            }
        }

        py += 16;
        text(c, S.get("holidays"), px, py, 26, true, BLACK, L);
        py += 32;
        int shown = 0, nowKey = (month + 1) * 32 + today;
        List<BgCalendar.Day> next = hol;
        for (int pass = 0; pass < 2 && shown < 4; pass++) {
            for (BgCalendar.Day d : next) {
                if (shown >= 4 || (pass == 0 && d.month * 32 + d.day <= nowKey))
                    continue;
                String when = d.day + " " + shortMonth(d.month - 1);
                text(c, when, px, py, 22, true, BLACK, L);
                fit(c, d.name(), px + 80, py, 22, false, BLACK, L, pw - 80);
                py += 30;
                shown++;
            }
            next = BgCalendar.holidays(year + 1);       // wrap into next year
        }
    }

    private static String shortMonth(int m0) {
        String m = S.month(m0);
        return m.substring(0, Math.min(3, m.length()));
    }

    /** Word-wraps comma-separated text; returns the next baseline. */
    private static float wrap(Canvas c, String s, float x, float y, float w, float size, boolean bold, int color) {
        String line = "";
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (width(test, size, bold) > w && line.length() > 0) {
                text(c, line, x, y, size, bold, color, L);
                y += size + 6;
                line = word;
            } else
                line = test;
        }
        if (line.length() > 0) {
            text(c, line, x, y, size, bold, color, L);
            y += size + 6;
        }
        return y;
    }
}
