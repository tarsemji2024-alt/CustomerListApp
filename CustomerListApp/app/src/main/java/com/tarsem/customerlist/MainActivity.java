package com.tarsem.customerlist;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static class Customer {
        String id, name, village, mobile, rsn, type, status;
        boolean isYes() { return status != null && status.toLowerCase(Locale.ROOT).startsWith("y"); }
        String all() {
            return (id + " " + name + " " + village + " " + mobile + " " + rsn + " " + type + " " + status)
                    .toLowerCase(Locale.ROOT);
        }
    }

    // पहली बार खुलने पर यही डेटा दिखेगा (| से अलग)
    static final String SEED =
        "7101699225|Aman Sharma|Moonak|9518286292|RNOODGKQ0026476|ODU|not\n" +
        "7117226851|Gurjit Singh/joga Singh|Chural kalan|7888432167|RTHODGLK0013911|ODU|Yes\n" +
        "7104015773|Harvinder Kumar|Moonak|9915208007|RTHODJKK0090400|ODU|Yes\n" +
        "7112870241|Karam Chand/Balkar singh|Mackod|9855997492|RTHODHKK0127147|ODU|Yes\n" +
        "7105549179|Karav singh|Moonak|6283766292|RARODEK00070403|ODU|yes\n" +
        "7124888343|Rajpal Singh|moonak|9569097512|RHFODKL00050558|ODU|yes\n" +
        "7099014543|Rajpal Singh|kaliya|7087131932|RHFODAL00068125|ODU|Yes\n" +
        "7105541822|Sahil Singh|lahara|8699721397|RNOODGKQ0061065|ODU|yes\n" +
        "7107477342|Sanju Singh|Balran|9646103506|RSVODJKBK048860|ODU|Yes\n" +
        "7105217105|Sarbjeet Singh/baljinder|Navagavo|9463000633|RNOODGKQ0015576|ODU|Yes\n" +
        "7104121994|Shashi Goyal|Moonak|8872691421|RSVODALBK463135|ODU|Yes\n" +
        "7103706824|Sukhwinder Singh/sukhdev singh|Handa|9050905310|RTHODEK00150901|ODU|Yes\n" +
        "7107383413|Surjit Singh/joginder singh|Moonak|8146033350|RTHODIKK0058735|ODU|yes";

    static final String PREFS = "customer_list", KEY = "data";
    static final int REQ_CALL = 101;

    ArrayList<Customer> all = new ArrayList<>();
    ArrayList<Customer> shown = new ArrayList<>();
    String filter = "all";
    String pendingNumber = null;
    Adapter adapter;
    EditText search;
    TextView count;
    Button fAll, fYes, fNot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        search = findViewById(R.id.search);
        count = findViewById(R.id.count);
        fAll = findViewById(R.id.fAll);
        fYes = findViewById(R.id.fYes);
        fNot = findViewById(R.id.fNot);
        ListView list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);

        loadData();
        applyFilter();

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) { applyFilter(); }
            public void afterTextChanged(Editable s) {}
        });
        fAll.setOnClickListener(v -> { filter = "all"; applyFilter(); });
        fYes.setOnClickListener(v -> { filter = "yes"; applyFilter(); });
        fNot.setOnClickListener(v -> { filter = "not"; applyFilter(); });
        findViewById(R.id.btnImport).setOnClickListener(v -> showImportDialog());
    }

    // ---------- डेटा सेव / लोड ----------
    void loadData() {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, null);
        all.clear();
        try {
            if (json == null) {
                for (Customer c : parseSeed()) all.add(c);
                saveData();
            } else {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Customer c = new Customer();
                    c.id = o.optString("id"); c.name = o.optString("name");
                    c.village = o.optString("village"); c.mobile = o.optString("mobile");
                    c.rsn = o.optString("rsn"); c.type = o.optString("type");
                    c.status = o.optString("status");
                    all.add(c);
                }
            }
        } catch (Exception e) { /* खराब डेटा हो तो खाली लिस्ट */ }
    }

    void saveData() {
        try {
            JSONArray arr = new JSONArray();
            for (Customer c : all) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("name", c.name); o.put("village", c.village);
                o.put("mobile", c.mobile); o.put("rsn", c.rsn); o.put("type", c.type);
                o.put("status", c.status);
                arr.put(o);
            }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) { }
    }

    // ---------- पार्सिंग ----------
    ArrayList<Customer> parseSeed() {
        ArrayList<Customer> out = new ArrayList<>();
        for (String line : SEED.split("\n")) {
            Customer c = fromFields(line.split("\\|"));
            if (c != null) out.add(c);
        }
        return out;
    }

    Customer fromFields(String[] f) {
        if (f.length < 4) return null;
        Customer c = new Customer();
        c.id = f[0].trim(); c.name = f[1].trim(); c.village = f[2].trim(); c.mobile = f[3].trim();
        c.rsn = f.length > 4 ? f[4].trim() : "";
        c.type = f.length > 5 ? f[5].trim() : "";
        c.status = f.length > 6 ? f[6].trim() : "";
        return c;
    }

    ArrayList<Customer> parsePasted(String text) {
        ArrayList<Customer> out = new ArrayList<>();
        Pattern p = Pattern.compile("^\\s*(\\d{10})\\s+(.+?)\\s+(\\d{10})\\s+(R[A-Z0-9]+)\\s+(\\w+)\\s*(\\w+)?\\s*$",
                Pattern.CASE_INSENSITIVE);
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] f = line.split("\t");
            if (f.length < 5) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    f = new String[]{m.group(1), m.group(2), "", m.group(3), m.group(4), m.group(5),
                            m.group(6) == null ? "" : m.group(6)};
                } else {
                    f = line.trim().split("\\s{2,}");
                }
            }
            Customer c = fromFields(f);
            if (c != null) out.add(c);
        }
        return out;
    }

    // ---------- इम्पोर्ट ----------
    void showImportDialog() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(6);
        et.setGravity(android.view.Gravity.TOP);
        et.setHint("यहाँ डेटा पेस्ट करें\nक्रम: ID, नाम, गाँव, मोबाइल, RSN, ODU, Yes/not");
        new AlertDialog.Builder(this)
                .setTitle("डेटा इम्पोर्ट")
                .setView(et)
                .setPositiveButton("जोड़ें", (d, w) -> doImport(et.getText().toString(), false))
                .setNeutralButton("बदलें", (d, w) -> doImport(et.getText().toString(), true))
                .setNegativeButton("रद्द", null)
                .show();
    }

    void doImport(String text, boolean replace) {
        ArrayList<Customer> rows = parsePasted(text);
        if (rows.isEmpty()) {
            Toast.makeText(this, "कोई सही लाइन नहीं मिली", Toast.LENGTH_LONG).show();
            return;
        }
        if (replace) all.clear();
        all.addAll(rows);
        saveData();
        applyFilter();
        Toast.makeText(this, rows.size() + " रिकॉर्ड जुड़े", Toast.LENGTH_SHORT).show();
    }

    // ---------- सर्च / फ़िल्टर ----------
    void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Customer c : all) {
            if (filter.equals("yes") && !c.isYes()) continue;
            if (filter.equals("not") && c.isYes()) continue;
            if (!q.isEmpty() && !c.all().contains(q)) continue;
            shown.add(c);
        }
        count.setText(shown.size() + " / " + all.size());
        fAll.setAlpha(filter.equals("all") ? 1f : 0.5f);
        fYes.setAlpha(filter.equals("yes") ? 1f : 0.5f);
        fNot.setAlpha(filter.equals("not") ? 1f : 0.5f);
        adapter.notifyDataSetChanged();
    }

    // ---------- कॉल ----------
    void callNumber(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        if (digits.length() != 10) {
            Toast.makeText(this, "मोबाइल नंबर सही नहीं है", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingNumber = digits;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:+91" + digits)));
        } else {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_CALL && pendingNumber != null) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            // अनुमति न मिले तो डायलर खुलेगा
            startActivity(new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                    Uri.parse("tel:+91" + pendingNumber)));
            pendingNumber = null;
        }
    }

    // ---------- लिस्ट एडैप्टर ----------
    class Adapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_customer, parent, false);
            final Customer c = shown.get(i);
            ((TextView) v.findViewById(R.id.tvName)).setText(c.name + "  [" + c.status + "]");
            ((TextView) v.findViewById(R.id.tvName)).setTextColor(c.isYes() ? Color.parseColor("#1B2330") : Color.parseColor("#D93025"));
            ((TextView) v.findViewById(R.id.tvSub1)).setText("📍 " + c.village + "  ·  📱 " + c.mobile);
            ((TextView) v.findViewById(R.id.tvSub2)).setText(c.type + " · RSN: " + c.rsn + " · ID: " + c.id);
            v.findViewById(R.id.btnCall).setOnClickListener(x -> callNumber(c.mobile));
            return v;
        }
    }
}
