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
        String id = "", name = "", village = "", mobile = "", rsn = "", type = "", status = "";
        boolean isYes() { return status.toLowerCase(Locale.ROOT).startsWith("y"); }
        String all() {
            return (id + " " + name + " " + village + " " + mobile + " " + rsn + " " + type + " " + status)
                    .toLowerCase(Locale.ROOT);
        }
    }

    static final String SEED =
        "7101699225|Aman Sharma|Moonak|9518286292|RNOODGKQ0026476|ODU|not\n" +
      
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
        list.setOnItemClickListener((p, v, pos, id) -> showEdit(shown.get(pos), false));

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
        findViewById(R.id.btnNew).setOnClickListener(v -> showEdit(new Customer(), true));
        findViewById(R.id.btnExport).setOnClickListener(v -> showExportDialog());
    }

    // ---------- सेव / लोड ----------
    void loadData() {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, null);
        all.clear();
        try {
            if (json == null) {
                for (String line : SEED.split("\n")) {
                    String[] f = line.split("\\|");
                    Customer c = new Customer();
                    c.id = f[0]; c.name = f[1]; c.village = f[2]; c.mobile = f[3];
                    c.rsn = f[4]; c.type = f[5]; c.status = f[6];
                    all.add(c);
                }
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
        } catch (Exception e) { }
    }

    JSONArray toJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (Customer c : all) {
            JSONObject o = new JSONObject();
            o.put("id", c.id); o.put("name", c.name); o.put("village", c.village);
            o.put("mobile", c.mobile); o.put("rsn", c.rsn); o.put("type", c.type);
            o.put("status", c.status);
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
        if (l.length() < 5) return null;
        int lim = l.length() >= 7 ? 2 : 1;
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

    // ---------- इम्पोर्ट ----------
    void showImportDialog() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(6);
        et.setGravity(Gravity.TOP);
        et.setHint("कोई भी लिस्ट पेस्ट करें। ऐप खुद नाम, गाँव, मोबाइल, वर्क ऑर्डर, RSN पहचान लेगा।");
        new AlertDialog.Builder(this)
                .setTitle("डेटा इम्पोर्ट")
                .setView(et)
                .setPositiveButton("जोड़ें", (d, w) -> doImport(et.getText().toString(), false))
                .setNeutralButton("बदलें", (d, w) -> doImport(et.getText().toString(), true))
                .setNegativeButton("रद्द", null)
                .show();
    }

    void doImport(String text, boolean replace) {
        ArrayList<Customer> known = new ArrayList<>(all);
        ArrayList<Customer> rows = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            Customer c = smartParse(line, known);
            if (c == null) continue;
            rows.add(c);
            known.add(c);
        }
        if (rows.isEmpty()) {
            Toast.makeText(this, "कोई सही लाइन नहीं मिली", Toast.LENGTH_LONG).show();
            return;
        }
        if (replace) all.clear();
        int added = 0, skipped = 0;
        for (Customer c : rows) {
            if (isDup(c)) { skipped++; continue; }
            all.add(c);
            added++;
        }
        saveData();
        applyFilter();
        Toast.makeText(this, added + " नए जुड़े, " + skipped + " पहले से थे", Toast.LENGTH_LONG).show();
    }

    // ---------- देखें / बदलें ----------
    EditText addField(LinearLayout box, String label, String value, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#667085"));
        tv.setTextSize(12);
        box.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        et.setInputType(inputType);
        box.addView(et);
        return et;
    }

    void showEdit(final Customer c, final boolean isNew) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 20, 40, 0);
        final EditText eName = addField(box, "ग्राहक का नाम", c.name, InputType.TYPE_CLASS_TEXT);
        final EditText eLoc = addField(box, "लोकेशन / गाँव", c.village, InputType.TYPE_CLASS_TEXT);
        final EditText eMob = addField(box, "मोबाइल नंबर", c.mobile, InputType.TYPE_CLASS_PHONE);
        final EditText eWo = addField(box, "वर्क ऑर्डर नंबर", c.id, InputType.TYPE_CLASS_NUMBER);
        final EditText eRsn = addField(box, "RSN", c.rsn, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText eType = addField(box, "प्रकार (ODU / IDU / STB)", c.type, InputType.TYPE_CLASS_TEXT);
        final EditText eSt = addField(box, "स्थिति (Yes / not)", c.status, InputType.TYPE_CLASS_TEXT);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "नया ग्राहक" : "देखें / बदलें")
                .setView(sv)
                .setPositiveButton("सेव", (d, w) -> {
                    c.name = eName.getText().toString().trim();
                    c.village = eLoc.getText().toString().trim();
                    c.mobile = eMob.getText().toString().trim();
                    c.id = eWo.getText().toString().trim();
                    c.rsn = eRsn.getText().toString().trim().toUpperCase(Locale.ROOT);
                    c.type = eType.getText().toString().trim().toUpperCase(Locale.ROOT);
                    c.status = eSt.getText().toString().trim();
                    if (isNew) all.add(0, c);
                    saveData();
                    applyFilter();
                })
                .setNegativeButton("रद्द", null);
        if (!isNew) {
            b.setNeutralButton("हटाएँ", (d, w) -> new AlertDialog.Builder(this)
                    .setMessage(c.name + " को हटाएँ?")
                    .setPositiveButton("हाँ, हटाएँ", (d2, w2) -> { all.remove(c); saveData(); applyFilter(); })
                    .setNegativeButton("नहीं", null)
                    .show());
        }
        b.show();
    }

    // ---------- एक्सपोर्ट ----------
    void showExportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("कौन सी फ़ाइल बनानी है?")
                .setItems(new String[]{"📄 HTML फ़ाइल (सर्च + कॉल बटन)", "📊 Excel फ़ाइल (.xlsx)"},
                        (d, w) -> doExport(w == 0))
                .show();
    }

    void doExport(boolean html) {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
            String path;
            if (html) {
                path = saveFile("customer_list_" + stamp + ".html", "text/html", buildHtml().getBytes("UTF-8"));
            } else {
                path = saveFile("customer_list_" + stamp + ".xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", buildXlsx());
            }
            new AlertDialog.Builder(this).setTitle("फ़ाइल बन गई ✅").setMessage(path)
                    .setPositiveButton("ठीक है", null).show();
        } catch (Exception e) {
            Toast.makeText(this, "एक्सपोर्ट नहीं हो पाया: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    String saveFile(String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            OutputStream o = getContentResolver().openOutputStream(u);
            o.write(data);
            o.close();
            return "Downloads फ़ोल्डर में: " + name;
        } else {
            File f = new File(getExternalFilesDir(null), name);
            FileOutputStream o = new FileOutputStream(f);
            o.write(data);
            o.close();
            return f.getAbsolutePath();
        }
    }

    String buildHtml() throws Exception {
        String json = toJson().toString().replace("<", "\\u003c");
        return "<!DOCTYPE html><html lang='hi'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>ग्राहक लिस्ट</title>"
            + "<style>body{margin:0;background:#f4f6fa;font-family:sans-serif;padding:10px}"
            + "#q{width:100%;box-sizing:border-box;padding:12px;font-size:16px;border:1px solid #ccc;border-radius:8px;position:sticky;top:0}"
            + ".i{display:flex;justify-content:space-between;align-items:center;background:#fff;border-radius:10px;padding:10px;margin-top:8px}"
            + ".i a{background:#0f9d58;color:#fff;padding:12px 14px;border-radius:8px;text-decoration:none;font-weight:bold}"
            + "small{color:#667085}</style></head><body><h3>📋 ग्राहक लिस्ट</h3>"
            + "<input id='q' type='search' placeholder='खोजें…'><div id='l'></div><script>"
            + "const D=" + json + ";"
            + "const q=document.getElementById('q'),l=document.getElementById('l');"
            + "function e(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;')}"
            + "function r(){const k=q.value.toLowerCase();"
            + "const x=D.filter(o=>!k||Object.values(o).join(' ').toLowerCase().includes(k));"
            + "l.innerHTML=x.map(o=>`<div class='i'><div><b>${e(o.name)}</b> <small>[${e(o.status)}]</small><br>"
            + "<small>📍 ${e(o.village)} · 📱 ${e(o.mobile)}<br>${e(o.type)} · ${e(o.rsn)} · ${e(o.id)}</small></div>"
            + "<a href='tel:+91${String(o.mobile).replace(/\\D/g,'').slice(-10)}'>📞 कॉल</a></div>`).join('')"
            + "||'<p>कोई रिकॉर्ड नहीं</p>';}"
            + "q.oninput=r;r();</script></body></html>";
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
        String[] head = {"क्रम", "वर्क ऑर्डर", "नाम", "लोकेशन", "मोबाइल", "RSN", "प्रकार", "स्थिति"};
        addRow(sb, 1, head);
        int r = 2;
        for (Customer c : all) {
            addRow(sb, r, new String[]{String.valueOf(r - 1), c.id, c.name, c.village, c.mobile, c.rsn, c.type, c.status});
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
            nm.setText(c.name + "  [" + c.status + "]");
            nm.setTextColor(c.isYes() ? Color.parseColor("#1B2330") : Color.parseColor("#D93025"));
            ((TextView) v.findViewById(R.id.tvSub1)).setText("📍 " + c.village + "  ·  📱 " + c.mobile);
            ((TextView) v.findViewById(R.id.tvSub2)).setText(c.type + " · RSN: " + c.rsn + " · WO: " + c.id);
            v.findViewById(R.id.btnCall).setOnClickListener(x -> callNumber(c.mobile));
            return v;
        }
    }
}
