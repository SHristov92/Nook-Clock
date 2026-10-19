package dev.nookclock;

import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Draws the clock/weather page. Layout is the same as the native framebuffer
 * version: designed on an 800x600 (or 600x800) grid and scaled to the view.
 */
final class ClockView extends View {
    private static final int GRAY = Color.rgb(90, 90, 90);
    private static final String DEG = "°";
    private static final String[] WDAY = { "Sunday", "Monday", "Tuesday", "Wednesday",
            "Thursday", "Friday", "Saturday" };
    private static final String[] MONTH = { "January", "February", "March", "April", "May",
            "June", "July", "August", "September", "October", "November", "December" };
    private static final String[] ICONS = { "clear_day", "clear_night", "partly_day",
            "partly_night", "cloudy", "fog", "drizzle", "rain", "snow", "thunder" };

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] iconL = new Bitmap[ICONS.length], iconS = new Bitmap[ICONS.length];

    Weather weather;
    int battery = -1;
    boolean charging;
    String place = "";
    boolean imperial, clock24 = true;
    String status;          // transient message, e.g. "updating weather..."

    ClockView(Context ctx) {
        super(ctx);
        String pkg = ctx.getPackageName();
        for (int i = 0; i < ICONS.length; i++) {
            iconL[i] = load(ctx, ctx.getResources().getIdentifier("wx_" + ICONS[i], "drawable", pkg));
            iconS[i] = load(ctx, ctx.getResources().getIdentifier("wx_" + ICONS[i] + "_s", "drawable", pkg));
        }
    }

    private static Bitmap load(Context ctx, int id) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(ctx.getResources(), id, o);
    }

    private float text(Canvas c, String s, float x, float y, float size, boolean bold,
                       int color, Paint.Align align) {
        text.setTextSize(size);
        text.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setColor(color);
        text.setTextAlign(align);
        c.drawText(s, x, y, text);
        float w = text.measureText(s);
        return align == Paint.Align.LEFT ? x + w : align == Paint.Align.CENTER ? x + w / 2 : x;
    }

    private void icon(Canvas c, Bitmap b, float x, float y, float size) {
        if (b != null)
            c.drawBitmap(b, null, new RectF(x, y, x + size, y + size), bmp);
    }

    private static String t(double v) {
        return Double.isNaN(v) ? "-" : Math.round(v) + DEG;
    }

    @Override
    protected void onDraw(Canvas c) {
        int vw = getWidth(), vh = getHeight();
        boolean land = vw > vh;
        int W = land ? 800 : 600, H = land ? 600 : 800;
        float k = Math.min(vw / (float) W, vh / (float) H);

        c.drawColor(Color.WHITE);
        c.save();
        c.translate((vw - W * k) / 2, (vh - H * k) / 2);
        c.scale(k, k);
        draw(c, W, land);
        c.restore();
    }

    private void draw(Canvas c, int W, boolean land) {
        Weather w = weather;
        java.util.TimeZone tz = w != null ? w.zone() : java.util.TimeZone.getDefault();
        Calendar now = new GregorianCalendar(tz);
        Paint.Align L = Paint.Align.LEFT, C = Paint.Align.CENTER, R = Paint.Align.RIGHT;

        // status line
        String left = place;
        if (status != null)
            left += "  " + status;
        else if (w != null) {
            Calendar f = new GregorianCalendar(tz);
            f.setTimeInMillis(w.fetched);
            left += String.format("  updated %02d:%02d", f.get(Calendar.HOUR_OF_DAY), f.get(Calendar.MINUTE));
        }
        text(c, left, 16, 26, 22, false, GRAY, L);
        if (battery >= 0)
            text(c, (charging ? "charging " : "") + battery + "%", W - 16, 26, 22, false, GRAY, R);

        // clock + date
        int h = now.get(Calendar.HOUR_OF_DAY), m = now.get(Calendar.MINUTE);
        String clk = clock24 ? String.format("%02d:%02d", h, m)
                             : String.format("%d:%02d", h % 12 == 0 ? 12 : h % 12, m);
        float y = land ? 215 : 205;
        float x = text(c, clk, W / 2, y, land ? 220 : 175, true, Color.BLACK, C);
        if (!clock24)
            text(c, h < 12 ? "AM" : "PM", x + 8, y, 30, true, Color.BLACK, L);
        text(c, WDAY[now.get(Calendar.DAY_OF_WEEK) - 1] + ", " + now.get(Calendar.DAY_OF_MONTH)
                + " " + MONTH[now.get(Calendar.MONTH)], W / 2, y + 52, 40, false, Color.BLACK, C);
        c.drawRect(30, y + 72, W - 30, y + 74, fill(GRAY));

        // current conditions
        y += 82;
        if (w == null || System.currentTimeMillis() - w.fetched > 3 * 3600 * 1000L) {
            text(c, w == null ? "Waiting for weather..." : "Weather data is stale",
                 W / 2, y + 110, 40, false, GRAY, C);
            return;
        }
        String wind = "Wind " + Math.round(w.wind) + (imperial ? " mph" : " km/h");
        String hum = "Humidity " + w.humidity + "%";
        icon(c, iconL[Weather.icon(w.code, w.isDay != 0)], 24, y + 4, 170);
        x = text(c, t(w.temp), 205, y + 122, 96, true, Color.BLACK, L);
        if (land) {
            float tx = Math.max(x + 30, 440);
            text(c, Weather.describe(w.code), tx, y + 36, 30, true, Color.BLACK, L);
            text(c, "Feels like " + t(w.feels), tx, y + 70, 30, false, Color.BLACK, L);
            text(c, "High " + t(w.dayMax[0]) + "  Low " + t(w.dayMin[0]), tx, y + 104, 30, false, Color.BLACK, L);
            text(c, hum, tx, y + 138, 30, false, GRAY, L);
            text(c, wind, tx, y + 172, 30, false, GRAY, L);
            y += 185;
        } else {
            text(c, Weather.describe(w.code), 205, y + 162, 30, true, Color.BLACK, L);
            text(c, "Feels like " + t(w.feels) + "   High " + t(w.dayMax[0]) + "  Low " + t(w.dayMin[0]),
                 30, y + 222, 30, false, Color.BLACK, L);
            text(c, hum + "   " + wind, 30, y + 260, 30, false, GRAY, L);
            y += 282;
        }
        c.drawRect(30, y, W - 30, y + 2, fill(GRAY));

        // forecast: the next four days
        int n = Weather.DAYS - 1, cw = W / n;
        for (int i = 1; i <= n; i++) {
            if (w.dayYear[i] == 0)
                continue;
            float cx = cw * (i - 1) + cw / 2;
            Calendar d = new GregorianCalendar(w.dayYear[i], w.dayMonth[i] - 1, w.dayOfMonth[i]);
            String name = WDAY[d.get(Calendar.DAY_OF_WEEK) - 1];
            text(c, land ? name : name.substring(0, 3), cx, y + 36, 30, true, Color.BLACK, C);
            Bitmap ic = iconS[Weather.icon(w.dayCode[i], true)];
            String pop = w.dayPop[i] >= 0 ? "rain " + w.dayPop[i] + "%" : "";
            if (land) {
                icon(c, ic, cx - 92, y + 46, 76);
                x = text(c, t(w.dayMax[i]), cx - 10, y + 82, 30, true, Color.BLACK, L);
                text(c, t(w.dayMin[i]), x + 4, y + 82, 30, false, GRAY, L);
                text(c, pop, cx - 10, y + 112, 22, false, GRAY, L);
            } else {
                icon(c, ic, cx - 38, y + 46, 76);
                text(c, t(w.dayMax[i]) + " / " + t(w.dayMin[i]), cx, y + 156, 30, false, Color.BLACK, C);
                text(c, pop, cx, y + 188, 22, false, GRAY, C);
            }
        }
    }

    private final Paint fillPaint = new Paint();

    private Paint fill(int color) {
        fillPaint.setColor(color);
        return fillPaint;
    }
}
