package dev.nookclock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * Home screen hosting the pages. Page buttons / swipes change the screen,
 * tap refreshes, long press opens settings. Pressing Home while visible
 * opens a picker of the other installed home apps.
 */
public class ClockActivity extends Activity implements PageView.Listener {
    private static final String TAG = "NookClock";
    /** What the NST's "n" button sends instead of MAIN + CATEGORY_HOME. */
    private static final String BN_HOME = "com.bn.intent.action.home";
    private static final long WEATHER_EVERY = 15 * 60 * 1000L, AIR_EVERY = 30 * 60 * 1000L;
    private static final long RETRY = 2 * 60 * 1000L;
    private static final String[] ICONS = { "clear_day", "clear_night", "partly_day",
            "partly_night", "cloudy", "fog", "drizzle", "rain", "snow", "thunder" };

    private final Handler handler = new Handler();
    private PageView view;
    private Prefs prefs;
    private final List<Page> pages = new ArrayList<Page>();
    private final BigClockPage nightPage = new BigClockPage();
    private int pageIndex;
    private boolean visible, started, fetching, nightShown;
    private long lastFullRefresh, lastInteraction;
    private String loadedLocation;
    private static boolean suWorks = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new PageView(this);
        view.listener = this;
        view.ctx.iconL = loadIcons("");
        view.ctx.iconS = loadIcons("_s");
        nightPage.night = true;
        setContentView(view);
    }

    private Bitmap[] loadIcons(String suffix) {
        Bitmap[] b = new Bitmap[ICONS.length];
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        for (int i = 0; i < ICONS.length; i++)
            b[i] = BitmapFactory.decodeResource(getResources(),
                    getResources().getIdentifier("wx_" + ICONS[i] + suffix, "drawable", getPackageName()), o);
        return b;
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        applyPrefs();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        lastInteraction = System.currentTimeMillis();
        fetch();
        update(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
    }

    @Override
    protected void onStop() {
        visible = false;
        started = false;
        unregisterReceiver(tick);
        unregisterReceiver(battery);
        handler.removeCallbacks(fetchTask);
        super.onStop();
    }

    /** (Re)load settings; drop cached data when the location or units changed. */
    private void applyPrefs() {
        prefs = Prefs.load(this);
        view.ctx.prefs = prefs;
        String loc = prefs.lat + "," + prefs.lon + "," + prefs.imperial;
        if (!loc.equals(loadedLocation)) {
            loadedLocation = loc;
            view.ctx.weather = null;
            view.ctx.air = null;
        }
        pages.clear();
        for (int i = 0; i < Prefs.PAGES.length; i++)
            if (prefs.pageOn[i])
                pages.add(newPage(Prefs.PAGES[i]));
        if (pageIndex >= pages.size())
            pageIndex = 0;
    }

    private static Page newPage(String id) {
        if (id.equals("hourly")) return new HourlyPage();
        if (id.equals("air")) return new AirPage();
        if (id.equals("calendar")) return new CalendarPage();
        if (id.equals("bigclock")) return new BigClockPage();
        return new ClockPage();
    }

    // ------------------------------------------------------------ input --

    /** Home pressed: delivered here because we are a singleTask home activity. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean home = BN_HOME.equals(intent.getAction()) || intent.hasCategory(Intent.CATEGORY_HOME);
        if (visible && home)
            showHomePicker();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, "key " + keyCode);
        switch (keyCode) {
        // NST page-turn buttons (B&N framework) and d-pad fallbacks
        case 93: case 95: case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_DOWN:
            onSwipe(1);
            return true;
        case 92: case 94: case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_UP:
            onSwipe(-1);
            return true;
        case KeyEvent.KEYCODE_MENU:
            onLongPress();
            return true;
        case KeyEvent.KEYCODE_BACK:
            // a home screen must not finish on Back: go to the main clock instead
            if (pageIndex != 0 || nightShown) {
                pageIndex = 0;
                lastInteraction = System.currentTimeMillis();
                update(true);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onSwipe(int dir) {
        lastInteraction = System.currentTimeMillis();
        int n = pages.size();
        if (nightShown)
            pageIndex = dir > 0 ? 0 : n - 1;     // leave the night screen
        else
            pageIndex = (pageIndex + dir + n) % n;
        update(true);
    }

    public void onTap() {
        lastInteraction = System.currentTimeMillis();
        update(true);
        Weather w = view.ctx.weather;
        if (w == null || System.currentTimeMillis() - w.fetched > 60000)
            fetch();
    }

    public void onLongPress() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void showHomePicker() {
        PackageManager pm = getPackageManager();
        // Home apps answer either the B&N action (B&N Home, ReLaunch) or the
        // standard one (Android launcher); remember which intent reaches each.
        Intent[] queries = { new Intent(BN_HOME),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME) };
        final List<Intent> targets = new ArrayList<Intent>();
        List<String> labels = new ArrayList<String>();
        List<String> seen = new ArrayList<String>();
        labels.add(S.get("app"));
        for (Intent q : queries) {
            for (ResolveInfo ri : pm.queryIntentActivities(q, PackageManager.MATCH_DEFAULT_ONLY)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || seen.contains(pkg))
                    continue;
                seen.add(pkg);
                Intent i = new Intent(q).setClassName(pkg, ri.activityInfo.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                targets.add(i);
                labels.add(ri.loadLabel(pm).toString());
            }
        }
        labels.add(S.get("settings"));
        final int settingsItem = labels.size() - 1;
        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0)
                            return;
                        if (which == settingsItem) {
                            onLongPress();
                            return;
                        }
                        Intent i = targets.get(which - 1);
                        try {
                            startActivity(i);
                        } catch (Exception e) {
                            Log.w(TAG, "cannot start " + i.getComponent(), e);
                        }
                    }
                })
                .show();
    }

    // ---------------------------------------------------------- display --

    private TimeZone zone() {
        return view.ctx.weather != null ? view.ctx.weather.zone() : TimeZone.getDefault();
    }

    private boolean isNight() {
        return prefs.isNight(new GregorianCalendar(zone()).get(Calendar.HOUR_OF_DAY));
    }

    /**
     * Pick the page (night screen / auto-return to the clock), redraw, and
     * optionally flash-refresh the e-ink. Never flashes at night.
     */
    private void update(boolean full) {
        long now = System.currentTimeMillis();
        Page.Ctx x = view.ctx;
        x.tz = zone();
        x.now = new GregorianCalendar(x.tz);
        boolean night = isNight();
        long quiet = now - lastInteraction;
        boolean idle = prefs.autoReturnMin > 0 && quiet > prefs.autoReturnMin * 60000L;

        if (night && quiet > 2 * 60000L) {     // night screen after 2 idle minutes
            if (!nightShown)
                full = true;
            nightShown = true;
            view.page = nightPage;
        } else {
            if (nightShown)
                full = true;
            nightShown = false;
            if (idle && pageIndex != 0) {
                pageIndex = 0;
                full = true;
            }
            view.page = pages.get(pageIndex);
        }
        view.invalidate();
        if (full && !night)
            fullRefresh();
    }

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            boolean night = isNight();
            // night screen: redraw only every 10 minutes
            if (night && nightShown && new GregorianCalendar(zone()).get(Calendar.MINUTE) % 10 != 0)
                return;
            update(!night && System.currentTimeMillis() - lastFullRefresh >= prefs.fullRefreshMin * 60000L);
        }
    };

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            view.ctx.battery = level < 0 ? -1 : level * 100 / Math.max(scale, 1);
            view.ctx.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
    };

    /** Ask the EPD driver (via root) for a flashing full refresh to clear ghosting. */
    private void fullRefresh() {
        lastFullRefresh = System.currentTimeMillis();
        if (!suWorks)
            return;
        handler.postDelayed(new Runnable() {
            public void run() {
                new Thread("epd") {
                    @Override
                    public void run() {
                        try {
                            Process p = Runtime.getRuntime().exec(new String[] { "su", "-c",
                                    "echo 1 > /sys/class/graphics/fb0/epd_refresh" });
                            if (p.waitFor() != 0)
                                suWorks = false;
                        } catch (Exception e) {
                            Log.w(TAG, "full refresh via su failed", e);
                            suWorks = false;
                        }
                    }
                }.start();
            }
        }, 700);
    }

    // ------------------------------------------------------------- data --

    private final Runnable fetchTask = new Runnable() {
        public void run() {
            fetch();
        }
    };

    /**
     * Fetches whatever is due: weather every 15 min, air quality every 30 min
     * (only if that screen is on). Nothing is downloaded at night once there
     * is data. Always re-arms itself while the activity is started.
     */
    private void fetch() {
        handler.removeCallbacks(fetchTask);
        long now = System.currentTimeMillis();
        Weather w = view.ctx.weather;
        Air a = view.ctx.air;
        final boolean needW = w == null || now - w.fetched >= WEATHER_EVERY - 60000;
        final boolean needA = prefs.pageOn[2] && (a == null || now - a.fetched >= AIR_EVERY - 60000);
        if (fetching)
            return;
        if ((!needW && !needA) || (isNight() && w != null)) {
            handler.postDelayed(fetchTask, w == null ? RETRY : Math.max(60000, WEATHER_EVERY - (now - w.fetched)));
            return;
        }
        fetching = true;
        final Prefs p = prefs;
        new Thread("fetch") {
            @Override
            public void run() {
                Weather nw = null;
                Air na = null;
                boolean failed = false;
                try {
                    if (needW) nw = Weather.fetch(p);
                } catch (Exception e) {
                    Log.w(TAG, "weather fetch failed", e);
                    failed = true;
                }
                try {
                    if (needA) na = Air.fetch(p);
                } catch (Exception e) {
                    Log.w(TAG, "air fetch failed", e);
                }
                final Weather rw = nw;
                final Air ra = na;
                final boolean rf = failed;
                handler.post(new Runnable() {
                    public void run() {
                        fetching = false;
                        if (rw != null) view.ctx.weather = rw;
                        if (ra != null) view.ctx.air = ra;
                        view.ctx.status = rf ? (S.bg() ? "няма връзка" : "offline") : null;
                        if (started) {
                            update(false);
                            handler.postDelayed(fetchTask, rf ? RETRY : WEATHER_EVERY);
                        }
                    }
                });
            }
        }.start();
    }
}
