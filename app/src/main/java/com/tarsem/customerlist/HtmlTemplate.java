package com.tarsem.customerlist;

// HTML export page (kept in code, no separate asset file)
final class HtmlTemplate {
    static final String HTML =
        "<!DOCTYPE html>\n" +
        "<html lang=\"en\">\n" +
        "<head>\n" +
        "<meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
        "<title>Customer List</title>\n" +
        "<style>\n" +
        ":root{--bg:#111115;--card:#1e1e24;--tx:#fff;--mu:#a0a0aa;--bd:#2c2c36;--pr:#6b5ce7;--ok:#34c37a;--no:#ff6b60}\n" +
        "*{box-sizing:border-box}\n" +
        "body{margin:0;background:var(--bg);color:var(--tx);font-family:system-ui,sans-serif}\n" +
        ".wrap{max-width:900px;margin:0 auto;padding:12px}\n" +
        ".brand{padding:6px 2px 10px}\n" +
        ".bn{color:var(--pr);font-size:22px;font-weight:700}\n" +
        ".bs{color:#b8b8c0;font-size:13px;margin-top:2px}\n" +
        ".bar{position:sticky;top:0;background:var(--bg);padding:8px 0;z-index:5}\n" +
        "input{width:100%;padding:12px;font-size:16px;border:1px solid #3a3a46;border-radius:14px;background:var(--card);color:var(--tx)}\n" +
        ".row{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;align-items:center;justify-content:center}\n" +
        "button{font:inherit;font-size:14px;color:#fff;padding:11px 16px;border-radius:14px;cursor:pointer;border:1px solid rgba(255,255,255,.35);background:linear-gradient(180deg,rgba(255,255,255,.26),rgba(255,255,255,.07))}\n" +
        "#cnt{color:var(--mu);font-size:13px;margin:6px 2px}\n" +
        ".item{display:flex;gap:10px;align-items:center;background:var(--card);border:1px solid var(--bd);border-radius:16px;padding:12px;margin-top:8px;cursor:pointer}\n" +
        ".info{flex:1;min-width:0}\n" +
        ".nm{font-weight:600;font-size:16px}\n" +
        ".sub{color:var(--mu);font-size:13px;margin-top:2px;word-break:break-all}\n" +
        ".tag{display:inline-block;font-size:12px;padding:1px 7px;border-radius:99px;border:1px solid currentColor;margin-left:6px}\n" +
        ".call{background:linear-gradient(180deg,#34d27b,#128a47);color:#fff;padding:13px 18px;font-weight:600;border-radius:16px;text-decoration:none;white-space:nowrap;border:1px solid rgba(255,255,255,.3)}\n" +
        ".empty{text-align:center;color:var(--mu);padding:30px}\n" +
        ".modal{display:none;position:fixed;inset:0;background:rgba(0,0,0,.7);align-items:center;justify-content:center;padding:12px;z-index:20}\n" +
        ".box{background:var(--card);border-radius:16px;padding:16px;width:100%;max-width:480px;max-height:90vh;overflow:auto}\n" +
        "label{display:block;font-size:12px;color:var(--mu);margin:8px 0 3px}\n" +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        "<div class=\"wrap\">\n" +
        "<div class=\"brand\"><div class=\"bn\">Mannat Communication</div><div class=\"bs\">Jio AirFiber Customer Records</div><div class=\"bs\">Tarsem ji - M: 9988863580</div></div>\n" +
        "<div class=\"bar\">\n" +
        "  <input id=\"q\" type=\"search\" placeholder=\"🔍\" autocomplete=\"off\">\n" +
        "  <div class=\"row\">\n" +
        "    <button id=\"nw\">➕ New Customer</button>\n" +
        "    <button id=\"dl\">💾 Save HTML</button>\n" +
        "  </div>\n" +
        "</div>\n" +
        "<div id=\"cnt\"></div>\n" +
        "<div id=\"list\"></div>\n" +
        "</div>\n" +
        "\n" +
        "<datalist id=\"pre\">\n" +
        "<option value=\"Done\">\n" +
        "<option value=\"Yes\">\n" +
        "<option value=\"Call Not Picked\">\n" +
        "<option value=\"Not Returning\">\n" +
        "</datalist>\n" +
        "\n" +
        "<div id=\"m\" class=\"modal\"><div class=\"box\">\n" +
        "  <h3 id=\"mt\"></h3>\n" +
        "  <div id=\"fs\"></div>\n" +
        "  <div class=\"row\"><button id=\"sv\">Save</button><button id=\"del\">Delete</button><button id=\"cx\">Cancel</button></div>\n" +
        "</div></div>\n" +
        "\n" +
        "<script>\n" +
        "const SEED=__DATA__;/*END*/\n" +
        "const KEY=\"cl___STAMP__\";\n" +
        "let D=SEED, cur=-1;\n" +
        "try{const s=localStorage.getItem(KEY);if(s)D=JSON.parse(s)}catch(e){}\n" +
        "const $=id=>document.getElementById(id);\n" +
        "const F=[[\"name\",\"Customer Name\"],[\"father\",\"Father Name\"],[\"village\",\"Village / Location\"],[\"near\",\"Near By\"],[\"mobile\",\"Mobile Number\"],[\"id\",\"Work Order No.\"],[\"rsn\",\"RSN\"],[\"type\",\"Type (ODU / IDU / STB)\"],[\"status\",\"Status\"],[\"stb\",\"STB Serial\"]];\n" +
        "const esc=s=>String(s==null?\"\":s).replace(/&/g,\"&amp;\").replace(/</g,\"&lt;\").replace(/>/g,\"&gt;\").replace(/\"/g,\"&quot;\");\n" +
        "function col(o){\n" +
        "  const l=(o.status||\"\").toLowerCase().trim();\n" +
        "  if(!l)return \"\";\n" +
        "  if(l.includes(\"not pick\"))return \"#ffd600\";                       // yellow\n" +
        "  if(l.startsWith(\"done\"))return \"#2ecc71\";                         // green\n" +
        "  if(l===\"yes\"||l===\"y\"||l===\"ok\")return \"#ff9800\";                 // orange\n" +
        "  if(l.startsWith(\"not\")||l===\"no\"||l.includes(\"switch\"))return \"#ff4d4d\"; // red\n" +
        "  return \"\";\n" +
        "}\n" +
        "function save(){try{localStorage.setItem(KEY,JSON.stringify(D))}catch(e){}}\n" +
        "function render(){\n" +
        "  const words=$(\"q\").value.trim().toLowerCase().split(/\\s+/).filter(Boolean), rows=[];\n" +
        "  D.forEach((o,i)=>{\n" +
        "    const hay=Object.values(o).join(\" \").toLowerCase();\n" +
        "    if(words.every(w=>hay.includes(w)))rows.push([o,i]);\n" +
        "  });\n" +
        "  $(\"cnt\").textContent=rows.length+\" / \"+D.length;\n" +
        "  $(\"list\").innerHTML=rows.length?rows.map(([o,i])=>{\n" +
        "    const n=String(o.mobile||\"\").replace(/\\D/g,\"\").slice(-10);\n" +
        "    const c=col(o), st=c?` style=\"color:${c}\"`:\"\";\n" +
        "    return `<div class=\"item\" data-i=\"${i}\"><div class=\"info\">\n" +
        "      <div class=\"nm\"${st}>${esc(o.name)}${o.status?`<span class=\"tag\">${esc(o.status)}</span>`:\"\"}</div>\n" +
        "      ${o.father?`<div class=\"sub\"${st}>👤 Father: ${esc(o.father)}</div>`:\"\"}\n" +
        "      <div class=\"sub\"${st}>📍 ${esc(o.village)} · 📱 ${esc(o.mobile)}</div>\n" +
        "      ${o.near?`<div class=\"sub\"${st}>🏠 Near by: ${esc(o.near)}</div>`:\"\"}\n" +
        "      <div class=\"sub\"${st}>${esc(o.type)} · RSN: ${esc(o.rsn)} · WO: ${esc(o.id)}${o.stb?\" · STB: \"+esc(o.stb):\"\"}</div></div>\n" +
        "      ${n.length===10?`<a class=\"call\" href=\"tel:+91${n}\">📞 Call</a>`:\"\"}</div>`;\n" +
        "  }).join(\"\"):'<div class=\"empty\">—</div>';\n" +
        "}\n" +
        "function openM(i){\n" +
        "  cur=i;const o=i>=0?D[i]:{};\n" +
        "  $(\"mt\").textContent=i>=0?\"View / Edit\":\"New Customer\";\n" +
        "  $(\"fs\").innerHTML=F.map(([k,l])=>`<label>${l}</label><input data-k=\"${k}\"${k===\"status\"?' list=\"pre\"':\"\"} value=\"${esc(o[k]||\"\")}\">`).join(\"\");\n" +
        "  $(\"del\").style.display=i>=0?\"\":\"none\";\n" +
        "  $(\"m\").style.display=\"flex\";\n" +
        "}\n" +
        "$(\"q\").oninput=render;\n" +
        "$(\"list\").onclick=e=>{if(e.target.closest(\"a\"))return;const it=e.target.closest(\".item\");if(it)openM(+it.dataset.i)};\n" +
        "$(\"nw\").onclick=()=>openM(-1);\n" +
        "$(\"cx\").onclick=()=>{$(\"m\").style.display=\"none\"};\n" +
        "$(\"sv\").onclick=()=>{\n" +
        "  const o={};document.querySelectorAll(\"#fs input\").forEach(x=>o[x.dataset.k]=x.value.trim());\n" +
        "  if(cur>=0)D[cur]=Object.assign(D[cur],o);else D.unshift(o);\n" +
        "  save();$(\"m\").style.display=\"none\";render();\n" +
        "};\n" +
        "$(\"del\").onclick=()=>{if(confirm(\"Delete this customer?\")){D.splice(cur,1);save();$(\"m\").style.display=\"none\";render();}};\n" +
        "$(\"dl\").onclick=()=>{\n" +
        "  const json=JSON.stringify(D).replace(/</g,\"\\\\u003c\");\n" +
        "  let h=\"<!DOCTYPE html>\\n\"+document.documentElement.outerHTML;\n" +
        "  h=h.replace(/const SEED=[\\s\\S]*?;\\/\\*END\\*\\//,()=>\"const SEED=\"+json+\";/*END*/\");\n" +
        "  h=h.replace(/const KEY=\"[^\"]*\";/,()=>'const KEY=\"cl_'+Date.now()+'\";');\n" +
        "  const a=document.createElement(\"a\");\n" +
        "  a.href=URL.createObjectURL(new Blob([h],{type:\"text/html\"}));\n" +
        "  a.download=\"customer_list.html\";a.click();\n" +
        "};\n" +
        "render();\n" +
        "</script>\n" +
        "</body>\n" +
        "</html>\n" +
        "\n" +
        "";
}
