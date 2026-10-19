package dev.nookclock;

import java.util.Calendar;
import java.util.TimeZone;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * One full screen. Pages draw on a logical 800x600 (landscape) or 600x800
 * canvas that PageView scales to the display.
 */
abstract class Page {
    static final int BLACK = Color.BLACK, GRAY = Color.rgb(90, 90, 90), LIGHT = Color.rgb(200, 200, 200);
    static final String DEG = "°";
    static final Paint.Align L = Paint.Align.LEFT, C = Paint.Align.CENTER, R = Paint.Align.RIGHT;

    /** Everything a page may show; filled in by ClockActivity. */
    static final class Ctx {
        int W, H;
        boolean land;
        Calendar now;           // in the location's timezone
        TimeZone tz;
        Prefs prefs;
        Weather weather;
        Air air;
        int battery = -1;
        boolean charging;
        String status;          // transient message, e.g. a fetch error
        Bitmap[] iconL, iconS;
    }

    abstract void draw(Canvas c, Ctx x);

    // ---------------------------------------------------------- helpers --
    private static final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);

    /** Draws s with baseline y; returns the x right after the text. */
    static float text(Canvas c, String s, float x, float y, float size, boolean bold, int color, Paint.Align a) {
        text.setTextSize(size);
        text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setColor(color);
        text.setTextAlign(a);
        c.drawText(s, x, y, text);
        float w = text.measureText(s);
        return a == L ? x + w : a == C ? x + w / 2 : x;
    }

    static float width(String s, float size, boolean bold) {
        text.setTextSize(size);
        text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return text.measureText(s);
    }

    /** Draws s shrunk if needed so it fits maxW. */
    static float fit(Canvas c, String s, float x, float y, float size, boolean bold, int color, Paint.Align a, float maxW) {
        float w = width(s, size, bold);
        return text(c, s, x, y, w > maxW ? size * maxW / w : size, bold, color, a);
    }

    static void rect(Canvas c, float l, float t, float r, float b, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawRect(l, t, r, b, fill);
    }

    static void frame(Canvas c, float l, float t, float r, float b, int color, float stroke) {
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(stroke);
        fill.setColor(color);
        c.drawRect(l, t, r, b, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    static void line(Canvas c, float x0, float y0, float x1, float y1, int color, float stroke) {
        fill.setStrokeWidth(stroke);
        fill.setColor(color);
        fill.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x0, y0, x1, y1, fill);
    }

    static void dot(Canvas c, float x, float y, float r, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        c.drawCircle(x, y, r, fill);
    }

    static void icon(Canvas c, Bitmap b, float x, float y, float size) {
        if (b != null)
            c.drawBitmap(b, null, new RectF(x, y, x + size, y + size), bmp);
    }

    static String t(double v) {
        return Double.isNaN(v) ? "-" : Math.round(v) + DEG;
    }

    static String hhmm(Calendar c, boolean h24) {
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        return h24 ? String.format("%02d:%02d", h, m) : String.format("%d:%02d", h % 12 == 0 ? 12 : h % 12, m);
    }

    /** "Sofia · updated 14:05" top-left, battery top-right. */
    static void statusLine(Canvas c, Ctx x, String title) {
        String left = title;
        if (x.status != null)
            left += "  " + x.status;
        text(c, left, 16, 26, 22, false, GRAY, L);
        if (x.battery >= 0)
            text(c, (x.charging ? S.get("charging") + " " : "") + x.battery + "%", x.W - 16, 26, 22, false, GRAY, R);
    }

    /** Page title with a rule below; returns the y where content starts. */
    static float header(Canvas c, Ctx x, String title) {
        statusLine(c, x, x.prefs.place + "  " + hhmm(x.now, x.prefs.clock24));
        text(c, title, 30, 78, 38, true, BLACK, L);
        rect(c, 30, 92, x.W - 30, 94, GRAY);
        return 100;
    }
}
