package dev.nookclock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

/** Settings, built in code so it can be re-labelled when the language changes. */
public class SettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getPreferenceManager().setSharedPreferencesName(Prefs.FILE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        build();                        // new language / summaries
    }

    private void build() {
        Prefs p = Prefs.load(this);
        setTitle(S.get("settings"));
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(root);

        Preference help = new Preference(this);
        help.setTitle(S.get("app"));
        help.setSummary(S.get("controls"));
        help.setSelectable(false);
        root.addPreference(help);

        root.addPreference(list("lang", S.get("language"), "bg",
                new String[] { "Български", "English" }, new String[] { "bg", "en" }));

        Preference loc = new Preference(this);
        loc.setTitle(S.get("location"));
        loc.setSummary(p.place + "  (" + p.lat + ", " + p.lon + ")");
        loc.setIntent(new Intent(this, LocationActivity.class));
        root.addPreference(loc);

        root.addPreference(list("units", S.get("units"), "metric",
                new String[] { S.get("metric"), S.get("imperial") }, new String[] { "metric", "imperial" }));
        root.addPreference(check("clock24", S.get("clock24"), null, true));

        PreferenceCategory screens = new PreferenceCategory(this);
        screens.setTitle(S.get("screens"));
        root.addPreference(screens);
        for (int i = 1; i < Prefs.PAGES.length; i++)
            screens.addPreference(check("page_" + Prefs.PAGES[i], S.get("page_" + Prefs.PAGES[i]), null, true));
        screens.addPreference(list("autoreturn", S.get("autoreturn"), "10",
                new String[] { S.get("off"), "2 " + S.get("min"), "5 " + S.get("min"), "10 " + S.get("min"), "30 " + S.get("min") },
                new String[] { "0", "2", "5", "10", "30" }));
        screens.addPreference(list("fullrefresh", S.get("fullrefresh"), "30",
                new String[] { "10 " + S.get("min"), "30 " + S.get("min"), "60 " + S.get("min") },
                new String[] { "10", "30", "60" }));

        PreferenceCategory night = new PreferenceCategory(this);
        night.setTitle(S.get("nightmode"));
        root.addPreference(night);
        night.addPreference(check("night", S.get("nightmode"), S.get("nightsum"), true));
        String[] hours = new String[24];
        for (int h = 0; h < 24; h++)
            hours[h] = String.valueOf(h);
        String[] labels = new String[24];
        for (int h = 0; h < 24; h++)
            labels[h] = String.format("%02d:00", h);
        ListPreference from = list("nightfrom", S.get("nightfrom"), "23", labels, hours);
        ListPreference to = list("nightto", S.get("nightto"), "7", labels, hours);
        night.addPreference(from);
        night.addPreference(to);
        from.setDependency("night");
        to.setDependency("night");
    }

    private ListPreference list(String key, String title, String def, String[] labels, String[] values) {
        ListPreference l = new ListPreference(this);
        l.setKey(key);
        l.setTitle(title);
        l.setDialogTitle(title);
        l.setEntries(labels);
        l.setEntryValues(values);
        l.setDefaultValue(def);
        String v = Prefs.sp(this).getString(key, def);
        for (int i = 0; i < values.length; i++)
            if (values[i].equals(v))
                l.setSummary(labels[i]);
        return l;
    }

    private CheckBoxPreference check(String key, String title, String summary, boolean def) {
        CheckBoxPreference c = new CheckBoxPreference(this);
        c.setKey(key);
        c.setTitle(title);
        if (summary != null)
            c.setSummary(summary);
        c.setDefaultValue(def);
        return c;
    }
}
