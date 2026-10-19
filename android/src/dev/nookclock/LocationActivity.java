package dev.nookclock;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Find a place by name with the Open-Meteo geocoding API and save it. */
public class LocationActivity extends Activity {
    private final Handler handler = new Handler();
    private EditText query;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final List<String[]> results = new ArrayList<String[]>();   // name, lat, lon

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Prefs.load(this);
        setTitle(S.get("location"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        LinearLayout row = new LinearLayout(this);
        query = new EditText(this);
        query.setHint(S.get("searchhint"));
        query.setSingleLine(true);
        query.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (code == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_UP) {
                    search();
                    return true;
                }
                return false;
            }
        });
        row.addView(query, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button go = new Button(this);
        go.setText(S.get("search"));
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search();
            }
        });
        row.addView(go);
        root.addView(row);

        status = new TextView(this);
        status.setPadding(4, 8, 4, 8);
        root.addView(status);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                String[] r = results.get(pos);
                Prefs.sp(LocationActivity.this).edit()
                        .putString("place", r[0]).putString("lat", r[1]).putString("lon", r[2]).commit();
                finish();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 0, 1));
        setContentView(root);
    }

    private void search() {
        final String q = query.getText().toString().trim();
        if (q.length() == 0)
            return;
        status.setText(S.get("searching"));
        final String lang = S.lang;
        new Thread("geocode") {
            @Override
            public void run() {
                final List<String[]> found = new ArrayList<String[]>();
                final List<String> labels = new ArrayList<String>();
                String err = null;
                try {
                    JSONObject root = new JSONObject(Weather.get(
                            "http://geocoding-api.open-meteo.com/v1/search?count=10&language=" + lang
                            + "&name=" + URLEncoder.encode(q, "UTF-8")));
                    JSONArray a = root.optJSONArray("results");
                    for (int i = 0; a != null && i < a.length(); i++) {
                        JSONObject o = a.getJSONObject(i);
                        String name = o.getString("name");
                        found.add(new String[] { name, String.valueOf(o.getDouble("latitude")),
                                String.valueOf(o.getDouble("longitude")) });
                        String where = o.optString("admin1", "");
                        String country = o.optString("country", "");
                        labels.add(name + (where.length() > 0 && !where.equals(name) ? ", " + where : "")
                                + (country.length() > 0 ? ", " + country : ""));
                    }
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final String error = err;
                handler.post(new Runnable() {
                    public void run() {
                        results.clear();
                        results.addAll(found);
                        adapter.clear();
                        for (String l : labels)
                            adapter.add(l);
                        status.setText(error != null ? error : found.isEmpty() ? S.get("noresults") : "");
                    }
                });
            }
        }.start();
    }
}
