package dev.nookclock;

import android.content.Context;
import android.content.SharedPreferences;

/** All settings, backed by SharedPreferences (edited in SettingsActivity). */
final class Prefs {
    static final String FILE = "nookclock";
    /** Screens in display order; "clock" is always on. */
    static final String[] PAGES = { "clock", "hourly", "air", "calendar", "bigclock" };

    String lang, place, lat, lon;
    boolean imperial, clock24, night;
    int autoReturnMin, nightFrom, nightTo, fullRefreshMin;
    boolean[] pageOn = new boolean[PAGES.length];

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Prefs load(Context c) {
        SharedPreferences sp = sp(c);
        Prefs p = new Prefs();
        p.lang = sp.getString("lang", "bg");
        // until a place is picked in settings, name the default in the UI language
        p.place = sp.getString("place", "bg".equals(p.lang) ? "София" : "Sofia");
        p.lat = sp.getString("lat", "42.6975");
        p.lon = sp.getString("lon", "23.3241");
        p.imperial = "imperial".equals(sp.getString("units", "metric"));
        p.clock24 = sp.getBoolean("clock24", true);
        p.autoReturnMin = Integer.parseInt(sp.getString("autoreturn", "10"));
        p.night = sp.getBoolean("night", true);
        p.nightFrom = Integer.parseInt(sp.getString("nightfrom", "23"));
        p.nightTo = Integer.parseInt(sp.getString("nightto", "7"));
        p.fullRefreshMin = Integer.parseInt(sp.getString("fullrefresh", "30"));
        for (int i = 0; i < PAGES.length; i++)
            p.pageOn[i] = i == 0 || sp.getBoolean("page_" + PAGES[i], true);
        S.lang = p.lang;
        return p;
    }

    boolean isNight(int hour) {
        if (!night || nightFrom == nightTo)
            return false;
        return nightFrom < nightTo ? hour >= nightFrom && hour < nightTo
                                   : hour >= nightFrom || hour < nightTo;
    }
}
