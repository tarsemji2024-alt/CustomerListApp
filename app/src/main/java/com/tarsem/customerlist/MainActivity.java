package com.tarsem.customerlist;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Xml;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import org.xmlpull.v1.XmlPullParser;
import java.util.zip.ZipFile;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    static class Customer {
        String id = "", name = "", village = "", mobile = "", rsn = "", type = "", status = "", stb = "", father = "", near = "";
        boolean isYes() { String l = status.toLowerCase(Locale.ROOT); return l.startsWith("y") || l.equals("ok") || l.equals("returning") || l.equals("returned")
                    || l.contains("दे रहा है") || l.contains("दे दिया"); }
        String all() {
            return (id + " " + name + " " + village + " " + mobile + " " + rsn + " " + type + " " + status + " " + stb + " " + father + " " + near)
                    .toLowerCase(Locale.ROOT);
        }
    }

    static final String PREFS = "customer_list", KEY = "data";
    static final int REQ_CALL = 101;

    ArrayList<Customer> all = new ArrayList<>();
    ArrayList<Customer> shown = new ArrayList<>();
    String pendingNumber = null;
    Adapter adapter;
    EditText search;
    TextView count;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        search = findViewById(R.id.search);
        count = findViewById(R.id.count);
        ListView list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.emptyView));
        list.setOnItemClickListener((p, v, pos, id) -> showEdit(shown.get(pos), false));

        loadData();
        applyFilter();

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) { applyFilter(); }
            public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.btnImport).setOnClickListener(v -> pickExcel());
        findViewById(R.id.btnNew).setOnClickListener(v -> showEdit(new Customer(), true));
        findViewById(R.id.btnExport).setOnClickListener(v -> showExportDialog());
    }

    // ---------- सेव / लोड ----------
    void loadData() {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, null);
        all.clear();
        try {
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Customer c = new Customer();
                    c.id = o.optString("id"); c.name = o.optString("name");
                    c.village = o.optString("village"); c.mobile = o.optString("mobile");
                    c.rsn = o.optString("rsn"); c.type = o.optString("type");
                    c.status = o.optString("status"); c.stb = o.optString("stb"); c.father = o.optString("father"); c.near = o.optString("near");
                    all.add(c);
                }
            }
        } catch (Exception e) { }
    }

    JSONArray toJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (Customer c : all) {
            JSONObject o = new JSONObject();
            o.put("id", c.id); o.put("name", c.name); o.put("village", c.village);
            o.put("mobile", c.mobile); o.put("rsn", c.rsn); o.put("type", c.type);
            o.put("status", c.status); o.put("stb", c.stb); o.put("father", c.father); o.put("near", c.near);
            arr.put(o);
        }
        return arr;
    }

    void saveData() {
        try {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson().toString()).apply();
        } catch (Exception e) { }
    }

    // ---------- स्मार्ट पहचान ----------
    static String title(String s) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char ch : s.trim().toCharArray()) {
            sb.append(up ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            up = (ch == ' ' || ch == '/' || ch == '.' || ch == '-');
        }
        return sb.toString();
    }

    static int lev(String a, String b) {
        int[] p = new int[b.length() + 1], q = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) p[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            q[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                q[j] = Math.min(Math.min(q[j - 1] + 1, p[j] + 1), p[j - 1] + cost);
            }
            int[] t = p; p = q; q = t;
        }
        return p[b.length()];
    }

    // पुराने गाँव के नामों से मिलता-जुलता नाम ढूँढे (जैसे moonak / Monak / MOONAK)
    String canonVillage(String t, List<Customer> known) {
        String l = t.trim().toLowerCase(Locale.ROOT);
        if (l.length() < 3) return null;
        for (Customer c : known) {
            if (!c.village.isEmpty() && c.village.toLowerCase(Locale.ROOT).equals(l)) return title(c.village);
        }
        if (l.length() < 6) return null;
        int lim = l.length() >= 9 ? 2 : 1;
        for (Customer c : known) {
            String v = c.village.toLowerCase(Locale.ROOT);
            if (!v.isEmpty() && Math.abs(v.length() - l.length()) <= lim && lev(v, l) <= lim) return title(c.village);
        }
        return null;
    }

    static String[] splitLine(String line) {
        String[] f = line.split("\t");
        if (f.length < 3) f = line.trim().split("\\s*[,;|]\\s*");
        if (f.length < 3) f = line.trim().split("\\s{2,}");
        if (f.length < 3) f = line.trim().split("\\s+");
        return f;
    }

    static String join(String[] w, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) { if (sb.length() > 0) sb.append(' '); sb.append(w[i]); }
        return sb.toString();
    }

    Customer smartParse(String line, List<Customer> known) {
        String[] toks = splitLine(line);
        boolean delim = line.contains("\t") || line.contains(",") || line.contains("|") || line.trim().split("\\s{2,}").length >= 3;
        Customer c = new Customer();
        ArrayList<String> tens = new ArrayList<>();
        ArrayList<String> texts = new ArrayList<>();
        for (String t : toks) {
            String s = t.trim();
            if (s.isEmpty()) continue;
            String d = s.replaceAll("[\\s\\-]", "");
            if (d.startsWith("+91")) d = d.substring(3);
            else if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
            else if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
            if (d.matches("\\d{10}")) tens.add(d);
            else if (d.matches("7\\d{8}")) c.id = d;                       // 9 अंक का वर्क ऑर्डर
            else if (d.matches("(?i)R[A-Z0-9]{9,}") && d.matches(".*\\d.*")) c.rsn = d.toUpperCase(Locale.ROOT);
            else if (d.matches("(?i)ODU|IDU|STB")) c.type = d.toUpperCase(Locale.ROOT);
            else if (d.matches("(?i)yes|not|no|y|n")) c.status = d.toLowerCase(Locale.ROOT).startsWith("y") ? "Yes" : "not";
            else if (d.matches("\\d+")) { /* बेकार नंबर छोड़ दो */ }
            else texts.add(s);
        }
        // वर्क ऑर्डर और मोबाइल अलग करना
        if (tens.size() >= 2) {
            String a = tens.get(0), b = tens.get(1);
            if (!a.startsWith("7") && b.startsWith("7")) { c.mobile = a; c.id = b; }
            else { c.id = a; c.mobile = b; }
        } else if (tens.size() == 1) {
            String a = tens.get(0);
            if (c.id.isEmpty() && a.matches("(71|709)\\d{7,8}")) c.id = a;
            else c.mobile = a;
        }
        // नाम और गाँव अलग करना
        ArrayList<String> nameParts = new ArrayList<>();
        String village = "";
        for (String t : texts) {
            String cv = canonVillage(t, known);
            if (cv != null && village.isEmpty()) { village = cv; continue; }
            String[] w = t.split("\\s+");
            if (w.length > 1 && village.isEmpty()) {
                String last = canonVillage(w[w.length - 1], known);
                if (last != null) { village = last; t = join(w, 0, w.length - 1); }
                else {
                    String first = canonVillage(w[0], known);
                    if (first != null) { village = first; t = join(w, 1, w.length); }
                }
            }
            nameParts.add(t);
        }
        if (village.isEmpty()) {
            if (delim && nameParts.size() >= 2) village = title(nameParts.remove(nameParts.size() - 1));
            else if (!delim && nameParts.size() >= 3) village = title(nameParts.remove(nameParts.size() - 1));
        }
        StringBuilder nm = new StringBuilder();
        for (String p : nameParts) { if (nm.length() > 0) nm.append(' '); nm.append(p); }
        c.name = title(nm.toString());
        c.village = village;
        if (c.name.isEmpty() && c.mobile.isEmpty() && c.id.isEmpty() && c.rsn.isEmpty()) return null;
        return c;
    }

    boolean isDup(Customer n) {
        for (Customer c : all) {
            if (!n.id.isEmpty() && n.id.equals(c.id)) return true;
            if (!n.rsn.isEmpty() && n.rsn.equals(c.rsn)) return true;
        }
        return false;
    }

    // ---------- Excel इम्पोर्ट ----------
    static final int REQ_FILE = 202;
    ArrayList<Customer> pendingRows = new ArrayList<>();

    interface RowSink { boolean row(ArrayList<String> cells) throws Exception; }

    void pickExcel() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"});
        startActivityForResult(i, REQ_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SAVE) {
            if (res == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
                try {
                    OutputStream o = getContentResolver().openOutputStream(data.getData());
                    o.write(pendingExport);
                    o.close();
                    Toast.makeText(this, "Saved ✅", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingExport = null;
            return;
        }
        if (req == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null) readExcel(data.getData());
    }

    void readExcel(final Uri uri) {
        Toast.makeText(this, "Excel फ़ाइल पढ़ रहे हैं…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File tmp = new File(getCacheDir(), "import.xlsx");
                InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                final ArrayList<Customer> rows = parseExcel(tmp);
                tmp.delete();
                runOnUiThread(() -> askImport(rows));
            } catch (final Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("फ़ाइल नहीं पढ़ी जा सकी")
                        .setMessage("सिर्फ़ .xlsx फ़ाइल चलेगी। पुरानी .xls हो तो Excel में Save As करके .xlsx बना लें।\n\n" + e)
                        .setPositiveButton("OK", null).show());
            }
        }).start();
    }

    void askImport(ArrayList<Customer> rows) {
        if (rows.isEmpty()) {
            Toast.makeText(this, "फ़ाइल में कोई ग्राहक नहीं मिला", Toast.LENGTH_LONG).show();
            return;
        }
        pendingRows = rows;
        new AlertDialog.Builder(this)
                .setTitle("Excel से " + rows.size() + " ग्राहक मिले")
                .setMessage("नाम, गाँव, मोबाइल, वर्क ऑर्डर और RSN अपने आप पहचान लिए गए हैं।")
                .setPositiveButton("Add to list", (d, w) -> applyImport(false))
                .setNeutralButton("Replace all", (d, w) -> applyImport(true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    Customer findDup(Customer n) {
        for (Customer c : all) {
            if (!n.id.isEmpty() && n.id.equals(c.id)) return c;
            if (!n.rsn.isEmpty() && n.rsn.equals(c.rsn)) return c;
        }
        return null;
    }

    void applyImport(boolean replace) {
        if (replace) all.clear();
        int added = 0, updated = 0;
        for (Customer n : pendingRows) {
            Customer ex = findDup(n);
            if (ex == null) { all.add(n); added++; continue; }
            boolean ch = false;
            if (ex.status.isEmpty() && !n.status.isEmpty()) { ex.status = n.status; ch = true; }
            if (ex.village.isEmpty() && !n.village.isEmpty()) { ex.village = n.village; ch = true; }
            if (ex.mobile.isEmpty() && !n.mobile.isEmpty()) { ex.mobile = n.mobile; ch = true; }
            if (ex.type.isEmpty() && !n.type.isEmpty()) { ex.type = n.type; ch = true; }
            if (ex.stb.isEmpty() && !n.stb.isEmpty()) { ex.stb = n.stb; ch = true; }
            if (ch) updated++;
        }
        pendingRows = new ArrayList<>();
        saveData();
        applyFilter();
        Toast.makeText(this, added + " नए जुड़े, " + updated + " अपडेट हुए", Toast.LENGTH_LONG).show();
    }

    ArrayList<Customer> parseExcel(File f) throws Exception {
        final ArrayList<Customer> out = new ArrayList<>();
        final ArrayList<Customer> known = new ArrayList<>(all);
        final int[][] map = {null};
        final boolean[] first = {true};
        ZipFile zf = new ZipFile(f);
        try {
            ArrayList<String> shared = readShared(zf);
            for (String[] sh : readSheetList(zf)) {
                final String sheetName = sh[0];
                map[0] = null;
                first[0] = true;
                readSheet(zf, sh[1], shared, cells -> {
                    if (first[0]) {
                        first[0] = false;
                        int[] m = headerMap(cells);
                        if (m != null) { map[0] = m; return true; }
                        map[0] = defaultMap(cells);
                    }
                    Customer c = rowToCustomer(sheetName, cells, map[0], known);
                    if (c != null) { out.add(c); known.add(c); }
                    return out.size() < 50000;
                });
            }
        } finally {
            zf.close();
        }
        return out;
    }

    static String clean(String s) {
        if (s == null) return "";
        s = s.replace('\u00a0', ' ').trim();
        return s.equals("#N/A") ? "" : s;
    }

    static String get(ArrayList<String> cells, int i) {
        return (i < 0 || i >= cells.size()) ? "" : clean(cells.get(i));
    }

    // पहली लाइन हेडर हो तो कॉलम पहचानो (Order ID, Name, Address, Mobile no, Serial no ...)
    int[] headerMap(ArrayList<String> cells) {
        int[] m = {-1, -1, -1, -1, -1, -1, -1, -1};
        for (int i = 0; i < cells.size(); i++) {
            String h = clean(cells.get(i)).toLowerCase(Locale.ROOT);
            if (h.isEmpty()) continue;
            int k = -1;
            if (h.contains("order id") || h.contains("work order") || h.equals("wo") || h.equals("order")) k = 0;
            else if (h.equals("name") || h.contains("customer")) k = 1;
            else if (h.contains("address") || h.contains("village") || h.contains("location") || h.contains("गाँव")) k = 2;
            else if (h.contains("mobile") || h.contains("phone") || h.contains("contact")) k = 3;
            else if (h.contains("stb")) k = 7;
            else if (h.contains("serial") || h.equals("rsn")) k = 4;
            else if (h.equals("type")) k = 5;
            else if (h.contains("remark") || h.contains("status")) k = 6;
            if (k >= 0 && m[k] < 0) m[k] = i;
        }
        return (m[1] >= 0 && m[3] >= 0) ? m : null;
    }

    // हेडर न हो तो: वर्क ऑर्डर, नाम, गाँव, मोबाइल, RSN, प्रकार, रिमार्क
    int[] defaultMap(ArrayList<String> cells) {
        if (cells.size() >= 5 && get(cells, 0).replaceAll("\\D", "").matches("\\d{9,10}"))
            return new int[]{0, 1, 2, 3, 4, 5, 6, -1};
        return null;
    }

    Customer rowToCustomer(String sheet, ArrayList<String> cells, int[] m, List<Customer> known) {
        Customer c;
        if (m == null) {
            StringBuilder sb = new StringBuilder();
            for (String s : cells) sb.append(clean(s)).append('\t');
            c = smartParse(sb.toString(), known);
            if (c == null) return null;
        } else {
            c = new Customer();
            c.id = get(cells, m[0]).replaceAll("\\D", "");
            c.name = title(get(cells, m[1]));
            String v = get(cells, m[2]);
            if (!v.isEmpty()) {
                String cv = canonVillage(v, known);
                c.village = cv != null ? cv : title(v);
            }
            String mob = get(cells, m[3]).replaceAll("\\D", "");
            c.mobile = mob.length() > 10 ? mob.substring(mob.length() - 10) : mob;
            c.rsn = get(cells, m[4]).replace(" ", "").toUpperCase(Locale.ROOT);
            c.type = get(cells, m[5]).toUpperCase(Locale.ROOT);
            c.stb = get(cells, m[7]).replace(" ", "").toUpperCase(Locale.ROOT);
            String st = get(cells, m[6]);
            String sl = st.toLowerCase(Locale.ROOT);
            c.status = (sl.equals("yes") || sl.equals("y")) ? "Yes" : sl.equals("not") ? "not" : st;
        }
        if (c.type.isEmpty()) {
            String sn = sheet.toLowerCase(Locale.ROOT);
            if (sn.contains("idu")) c.type = "IDU";
            else if (sn.contains("odu")) c.type = "ODU";
        }
        if (c.name.equalsIgnoreCase("name")) return null;
        if (c.name.isEmpty() && c.mobile.isEmpty() && c.id.isEmpty() && c.rsn.isEmpty()) return null;
        return c;
    }

    // ---------- xlsx पढ़ने के औज़ार (बिना किसी लाइब्रेरी के) ----------
    static int colIndex(String ref) {
        int idx = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (!Character.isLetter(ch)) break;
            idx = idx * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return idx - 1;
    }

    static String normNum(String v) {
        v = v.trim();
        if (v.matches("-?\\d+(\\.\\d+)?[eE][+-]?\\d+")) {
            try { return new BigDecimal(v).toPlainString(); } catch (Exception e) { }
        }
        if (v.matches("\\d+\\.0+")) return v.substring(0, v.indexOf('.'));
        return v;
    }

    ArrayList<String> readShared(ZipFile zf) throws Exception {
        ArrayList<String> out = new ArrayList<>();
        ZipEntry ze = zf.getEntry("xl/sharedStrings.xml");
        if (ze == null) return out;
        XmlPullParser p = Xml.newPullParser();
        p.setInput(zf.getInputStream(ze), null);
        StringBuilder sb = null;
        boolean inRph = false;
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.equals("si")) sb = new StringBuilder();
                else if (n.equals("rPh")) inRph = true;
                else if (n.equals("t") && sb != null && !inRph) sb.append(p.nextText());
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                if (n.equals("rPh")) inRph = false;
                else if (n.equals("si")) { out.add(sb == null ? "" : sb.toString()); sb = null; }
            }
            ev = p.next();
        }
        return out;
    }

    ArrayList<String[]> readSheetList(ZipFile zf) throws Exception {
        java.util.HashMap<String, String> rel = new java.util.HashMap<>();
        XmlPullParser p = Xml.newPullParser();
        p.setInput(zf.getInputStream(zf.getEntry("xl/_rels/workbook.xml.rels")), null);
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG && p.getName().equals("Relationship"))
                rel.put(p.getAttributeValue(null, "Id"), p.getAttributeValue(null, "Target"));
        }
        ArrayList<String[]> out = new ArrayList<>();
        XmlPullParser q = Xml.newPullParser();
        q.setInput(zf.getInputStream(zf.getEntry("xl/workbook.xml")), null);
        for (int ev = q.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = q.next()) {
            if (ev == XmlPullParser.START_TAG && q.getName().equals("sheet")) {
                String name = q.getAttributeValue(null, "name");
                String rid = null;
                for (int i = 0; i < q.getAttributeCount(); i++) {
                    String an = q.getAttributeName(i);
                    if (an.equals("r:id") || an.equals("id")) rid = q.getAttributeValue(i);
                }
                String target = rid == null ? null : rel.get(rid);
                if (target == null) continue;
                out.add(new String[]{name == null ? "" : name, target.startsWith("/") ? target.substring(1) : "xl/" + target});
            }
        }
        return out;
    }

    static String readInline(XmlPullParser p) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ev = p.next();
        while (!(ev == XmlPullParser.END_TAG && p.getName().equals("is"))) {
            if (ev == XmlPullParser.START_TAG && p.getName().equals("t")) sb.append(p.nextText());
            ev = p.next();
        }
        return sb.toString();
    }

    // शीट को लाइन-दर-लाइन पढ़ता है। लगातार 200 खाली लाइनें आएँ तो रुक जाता है
    // (बड़ी फ़ाइलों में लाखों खाली लाइनें होती हैं)
    void readSheet(ZipFile zf, String path, ArrayList<String> shared, RowSink sink) throws Exception {
        ZipEntry ze = zf.getEntry(path);
        if (ze == null) return;
        XmlPullParser p = Xml.newPullParser();
        p.setInput(zf.getInputStream(ze), null);
        ArrayList<String> row = new ArrayList<>();
        int col = -1, blankStreak = 0;
        String t = null, val = "";
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.equals("row")) { row = new ArrayList<>(); col = -1; }
                else if (n.equals("c")) {
                    String ref = p.getAttributeValue(null, "r");
                    col = ref != null ? colIndex(ref) : col + 1;
                    t = p.getAttributeValue(null, "t");
                    val = "";
                } else if (n.equals("v")) val = p.nextText();
                else if (n.equals("is")) val = readInline(p);
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                if (n.equals("c")) {
                    String v = val;
                    if ("s".equals(t)) {
                        try {
                            int i = Integer.parseInt(v.trim());
                            v = i < shared.size() ? shared.get(i) : "";
                        } catch (Exception e) { v = ""; }
                    } else if (t == null || "n".equals(t)) v = normNum(v);
                    while (row.size() <= col) row.add("");
                    if (col >= 0) row.set(col, v);
                } else if (n.equals("row")) {
                    boolean blank = true;
                    for (String s : row) if (!s.trim().isEmpty()) { blank = false; break; }
                    if (blank) { if (++blankStreak >= 200) return; }
                    else { blankStreak = 0; if (!sink.row(row)) return; }
                }
            }
            ev = p.next();
        }
    }

    // ---------- देखें / बदलें ----------
    void showEdit(final Customer c, final boolean isNew) {
        View box = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null);
        final EditText eName = box.findViewById(R.id.eName);
        final EditText eLoc = box.findViewById(R.id.eLoc);
        final EditText eMob = box.findViewById(R.id.eMob);
        final EditText eWo = box.findViewById(R.id.eWo);
        final EditText eRsn = box.findViewById(R.id.eRsn);
        final EditText eType = box.findViewById(R.id.eType);
        final EditText eSt = box.findViewById(R.id.eSt);
        final EditText eStb = box.findViewById(R.id.eStb);
        final EditText eFather = box.findViewById(R.id.eFather);
        final EditText eNear = box.findViewById(R.id.eNear);
        box.findViewById(R.id.btnPreset).setOnClickListener(x -> showPresets(eSt));
        eName.setText(c.name);
        eLoc.setText(c.village);
        eMob.setText(c.mobile);
        eWo.setText(c.id);
        eRsn.setText(c.rsn);
        eType.setText(c.type);
        eSt.setText(c.status);
        eStb.setText(c.stb);
        eFather.setText(c.father);
        eNear.setText(c.near);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "New Customer" : "View / Edit")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    c.name = eName.getText().toString().trim();
                    c.village = eLoc.getText().toString().trim();
                    c.mobile = eMob.getText().toString().trim();
                    c.id = eWo.getText().toString().trim();
                    c.rsn = eRsn.getText().toString().trim().toUpperCase(Locale.ROOT);
                    c.type = eType.getText().toString().trim().toUpperCase(Locale.ROOT);
                    c.status = eSt.getText().toString().trim();
                    c.stb = eStb.getText().toString().trim().toUpperCase(Locale.ROOT);
                    c.father = eFather.getText().toString().trim();
                    c.near = eNear.getText().toString().trim();
                    if (isNew) all.add(0, c);
                    saveData();
                    applyFilter();
                })
                .setNegativeButton("Cancel", null);
        if (!isNew) {
            b.setNeutralButton("Delete", (d, w) -> new AlertDialog.Builder(this)
                    .setMessage("Delete " + c.name + "?")
                    .setPositiveButton("Yes, delete", (d2, w2) -> { all.remove(c); saveData(); applyFilter(); })
                    .setNegativeButton("No", null)
                    .show());
        }
        b.show();
    }

    static final String[] PRESETS = {
        "दे रहा है (Returning)",
        "नहीं दे रहा (Not returning)",
        "दे दिया (Returned)",
        "कॉल नहीं उठाई (Call not picked)",
        "डिवाइस नहीं दे रहा – पेंडिंग (Device not returning pending)",
        "डिवाइस नहीं दे रहा (Device not returning)"
    };

    void showPresets(final EditText target) {
        new AlertDialog.Builder(this).setTitle("Select remark")
                .setItems(PRESETS, (d, w) -> target.setText(PRESETS[w])).show();
    }

    // ---------- एक्सपोर्ट ----------
    static final int REQ_SAVE = 303;
    byte[] pendingExport = null;

    void showExportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Export as")
                .setItems(new String[]{"📊 Excel file (.xlsx)", "🌐 HTML page (search + call + edit)"},
                        (d, w) -> startExport(w == 0))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // फ़ाइल बनाकर Android का "कहाँ सेव करें" वाला पेज खोलता है
    void startExport(boolean excel) {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
            String mime, name;
            if (excel) {
                pendingExport = buildXlsx();
                mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                name = "customer_list_" + stamp + ".xlsx";
            } else {
                pendingExport = buildHtml().getBytes("UTF-8");
                mime = "text/html";
                name = "customer_list_" + stamp + ".html";
            }
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    String buildHtml() throws Exception {
        String json = toJson().toString().replace("<", "\\u003c");
        return HtmlTemplate.HTML.replace("__DATA__", json)
                .replace("__STAMP__", String.valueOf(System.currentTimeMillis()));
    }

    static String x(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void put(ZipOutputStream z, String name, String content) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes("UTF-8"));
        z.closeEntry();
    }

    byte[] buildXlsx() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bos);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
        put(z, "[Content_Types].xml", xml
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "</Types>");
        put(z, "_rels/.rels", xml
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>");
        put(z, "xl/workbook.xml", xml
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Customers\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        put(z, "xl/_rels/workbook.xml.rels", xml
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "</Relationships>");

        StringBuilder sb = new StringBuilder(xml);
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        String[] head = {"क्रम", "वर्क ऑर्डर", "नाम", "लोकेशन", "मोबाइल", "RSN", "प्रकार", "रिमार्क", "STB", "पिता का नाम", "नियर बाई"};
        addRow(sb, 1, head);
        int r = 2;
        for (Customer c : all) {
            addRow(sb, r, new String[]{String.valueOf(r - 1), c.id, c.name, c.village, c.mobile, c.rsn, c.type, c.status, c.stb, c.father, c.near});
            r++;
        }
        sb.append("</sheetData></worksheet>");
        put(z, "xl/worksheets/sheet1.xml", sb.toString());
        z.close();
        return bos.toByteArray();
    }

    void addRow(StringBuilder sb, int row, String[] cells) {
        sb.append("<row r=\"").append(row).append("\">");
        for (int i = 0; i < cells.length; i++) {
            sb.append("<c r=\"").append((char) ('A' + i)).append(row).append("\" t=\"inlineStr\"><is><t>")
              .append(x(cells[i])).append("</t></is></c>");
        }
        sb.append("</row>");
    }

    // ---------- सर्च / फ़िल्टर ----------
    // सर्च: लिखे हुए सभी शब्द मिलने चाहिए (जैसे "moonak not returning")
    void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        shown.clear();
        for (Customer c : all) {
            String hay = c.all();
            boolean ok = true;
            for (String w : words) { if (!hay.contains(w)) { ok = false; break; } }
            if (ok) shown.add(c);
        }
        count.setText(shown.size() + " / " + all.size());
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
            TextView nm = v.findViewById(R.id.tvName);
            nm.setText(c.name + (c.status.isEmpty() ? "" : "  [" + c.status + "]"));
            nm.setTextColor(c.status.isEmpty() || c.isYes() ? Color.parseColor("#FFFFFF") : Color.parseColor("#FF6B60"));
            ((TextView) v.findViewById(R.id.tvSub1)).setText("📍 " + c.village + "  ·  📱 " + c.mobile);
            TextView tf = v.findViewById(R.id.tvFather);
            tf.setVisibility(c.father.isEmpty() ? View.GONE : View.VISIBLE);
            tf.setText("👤 पिता: " + c.father);
            TextView tn = v.findViewById(R.id.tvNear);
            tn.setVisibility(c.near.isEmpty() ? View.GONE : View.VISIBLE);
            tn.setText("🏠 नियर बाई: " + c.near);
            ((TextView) v.findViewById(R.id.tvSub2)).setText(c.type + " · RSN: " + c.rsn + " · WO: " + c.id + (c.stb.isEmpty() ? "" : " · STB: " + c.stb));
            v.findViewById(R.id.btnCall).setOnClickListener(x -> callNumber(c.mobile));
            return v;
        }
    }
}
