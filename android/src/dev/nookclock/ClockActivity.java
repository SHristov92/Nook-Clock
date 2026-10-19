package dev.nookclock;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * Home screen showing the clock. Pressing Home while the clock is visible
 * opens a picker of the other installed home apps; pressing Home anywhere
 * else comes back here.
 */
public class ClockActivity extends Activity {
    private static final String TAG = "NookClock";
    /** What the NST's "n" button sends instead of MAIN + CATEGORY_HOME. */
    private static final String BN_HOME = "com.bn.intent.action.home";

    // Settings (same defaults as device/nookclock.conf)
    private static final String LAT = "42.70", LON = "23.32", PLACE = "Sofia";
    private static final boolean IMPERIAL = false;
    private static final long WEATHER_EVERY = 15 * 60 * 1000L, WEATHER_RETRY = 2 * 60 * 1000L;
    private static final long FULL_REFRESH_EVERY = 30 * 60 * 1000L;

    private final Handler handler = new Handler();
    private ClockView view;
    private boolean visible;         // resumed and not yet stopped
    private boolean started;
    private boolean fetching;
    private long lastFullRefresh;
    private static boolean suWorks = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new ClockView(this);
        view.place = PLACE;
        view.imperial = IMPERIAL;
        view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fullRefresh();
                if (view.weather == null || System.currentTimeMillis() - view.weather.fetched > 60000)
                    fetchWeather();
            }
        });
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        Weather w = view.weather;
        if (w == null || System.currentTimeMillis() - w.fetched > WEATHER_EVERY)
            fetchWeather();
        else
            handler.postDelayed(weatherTask, WEATHER_EVERY - (System.currentTimeMillis() - w.fetched));
        view.invalidate();
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
        handler.removeCallbacks(weatherTask);
        super.onStop();
    }

    /** Home pressed: delivered here because we are a singleTask HOME activity. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean home = BN_HOME.equals(intent.getAction()) || intent.hasCategory(Intent.CATEGORY_HOME);
        if (visible && home)
            showHomePicker();
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
        labels.add(getString(R.string.app_name));
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
        new AlertDialog.Builder(this)
                .setTitle("Show")
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0)
                            return;
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

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (System.currentTimeMillis() - lastFullRefresh >= FULL_REFRESH_EVERY)
                fullRefresh();
            else
                view.invalidate();
        }
    };

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            view.battery = level < 0 ? -1 : level * 100 / Math.max(scale, 1);
            view.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }
    };

    private final Runnable weatherTask = new Runnable() {
        public void run() {
            fetchWeather();
        }
    };

    private void fetchWeather() {
        if (fetching)
            return;
        fetching = true;
        handler.removeCallbacks(weatherTask);
        new Thread("weather") {
            @Override
            public void run() {
                Weather w = null;
                try {
                    w = Weather.fetch(LAT, LON, IMPERIAL);
                } catch (Exception e) {
                    Log.w(TAG, "weather fetch failed", e);
                }
                final Weather result = w;
                handler.post(new Runnable() {
                    public void run() {
                        fetching = false;
                        if (result != null)
                            view.weather = result;
                        view.invalidate();
                        if (started)
                            handler.postDelayed(weatherTask, result != null ? WEATHER_EVERY : WEATHER_RETRY);
                    }
                });
            }
        }.start();
    }

    /** Redraw, then ask the EPD driver (via root) for a flashing full refresh to clear ghosting. */
    private void fullRefresh() {
        lastFullRefresh = System.currentTimeMillis();
        view.invalidate();
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
}
