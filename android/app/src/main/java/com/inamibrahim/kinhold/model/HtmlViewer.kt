package com.inamibrahim.kinhold.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A family tree as one web page that opens in any browser with no app and no internet: a search box, a list of people, and a page
 * for each person with their parents, partners and children as links. Names, dates, places and notes only: no photos, contact details,
 * events or sources. Pure Kotlin over a [Family] (usually a [Share.subFamily] of the people chosen on the Share screen).
 */
object HtmlViewer {
    private const val NOTE_CHARS = 1500

    /** The people as JSON for the page: short keys keep a big tree small. */
    fun data(family: Family): String {
        val arr = JSONArray()
        for (p in family.people.sortedBy { it.name.lowercase() }) {
            val o = JSONObject()
                .put("i", p.id)
                .put("n", p.name)
                .put("x", p.sex.ordinal)
            p.birthDate.text().takeIf { it.isNotEmpty() }?.let { o.put("b", it) }
            p.deathDate.text().takeIf { it.isNotEmpty() }?.let { o.put("d", it) }
            p.birthPlace.takeIf { it.isNotBlank() }?.let { o.put("bp", it.trim()) }
            p.deathPlace.takeIf { it.isNotBlank() }?.let { o.put("dp", it.trim()) }
            p.notes.takeIf { it.isNotBlank() }?.let { o.put("t", it.trim().take(NOTE_CHARS)) }
            family.parentsOf(p.id).map { it.id }.takeIf { it.isNotEmpty() }?.let { o.put("p", JSONArray(it)) }
            family.spousesInOrder(p.id).map { it.id }.takeIf { it.isNotEmpty() }?.let { o.put("s", JSONArray(it)) }
            family.childrenOf(p.id).map { it.id }.takeIf { it.isNotEmpty() }?.let { o.put("c", JSONArray(it)) }
            arr.put(o)
        }
        return arr.toString()
    }

    /** The whole page as text. [title] is shown at the top and as the page title. */
    fun page(family: Family, title: String, madeOn: String): String =
        TEMPLATE
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{DATE}}", escapeHtml(madeOn))
            // The data sits in a script block, so nothing in it may close that block.
            .replace("{{DATA}}", data(family).replace("</", "<\\/").replace("<!--", "<\\!--"))

    internal fun escapeHtml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{{TITLE}}</title>
<style>
:root{--bg:#f6f7fb;--card:#fff;--text:#161a2b;--muted:#5d6479;--accent:#1f9d57;--line:#e3e6ee}
@media(prefers-color-scheme:dark){:root{--bg:#0d1220;--card:#18213a;--text:#eef1fa;--muted:#9aa3bd;--accent:#4ade80;--line:#27324f}}
*{box-sizing:border-box}body{margin:0;font:16px/1.5 system-ui,sans-serif;background:var(--bg);color:var(--text)}
header{padding:22px 18px 8px;max-width:760px;margin:0 auto}h1{margin:0;font-size:22px}.sub{color:var(--muted);font-size:13px}
main{max-width:760px;margin:0 auto;padding:8px 18px 40px}
input{width:100%;padding:13px 16px;border-radius:14px;border:1px solid var(--line);background:var(--card);color:var(--text);font-size:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:16px;margin:12px 0}
.row{display:block;padding:11px 4px;border-bottom:1px solid var(--line);color:inherit;text-decoration:none;cursor:pointer}.row:last-child{border:0}
.row b{display:block}.row span,.muted{color:var(--muted);font-size:14px}
h2{font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin:18px 0 4px}
a.back{color:var(--accent);text-decoration:none;font-weight:600;display:inline-block;margin:6px 0}
.big{font-size:24px;font-weight:700;margin:2px 0}.note{white-space:pre-wrap}
footer{max-width:760px;margin:0 auto;padding:0 18px 30px;color:var(--muted);font-size:12px}
</style>
</head>
<body>
<header><h1>{{TITLE}}</h1><div class="sub" id="count"></div></header>
<main id="app"></main>
<footer>Made with Kinhold on {{DATE}}. Names, dates, places and notes only.</footer>
<script id="data" type="application/json">{{DATA}}</script>
<script>
var people=JSON.parse(document.getElementById('data').textContent),by={};
people.forEach(function(p){by[p.i]=p});
document.getElementById('count').textContent=people.length+(people.length==1?' person':' people');
function esc(s){return String(s==null?'':s).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function years(p){var b=p.b||'',d=p.d||'';return b&&d?b+' – '+d:b?'b. '+b:d?'d. '+d:''}
function row(id){var p=by[id];if(!p)return'';return '<a class="row" href="#p'+p.i+'"><b>'+esc(p.n)+'</b><span>'+esc(years(p))+'</span></a>'}
function group(title,ids){if(!ids||!ids.length)return'';return '<h2>'+title+'</h2><div class="card">'+ids.map(row).join('')+'</div>'}
function home(q){
  q=(q||'').toLowerCase().trim();
  var list=people.filter(function(p){return !q||p.n.toLowerCase().indexOf(q)>=0||(p.bp||'').toLowerCase().indexOf(q)>=0||(p.dp||'').toLowerCase().indexOf(q)>=0}).slice(0,200);
  document.getElementById('app').innerHTML='<input id="q" placeholder="Search by name or place" value="'+esc(q)+'" autocomplete="off"><div class="card">'+(list.map(function(p){return row(p.i)}).join('')||'<span class="muted">Nobody matches.</span>')+'</div>'+(list.length==200?'<div class="muted">Showing the first 200. Type to narrow it down.</div>':'');
  var el=document.getElementById('q');el.focus();el.setSelectionRange(el.value.length,el.value.length);
  el.oninput=function(){home(el.value)};
}
function person(id){
  var p=by[id];if(!p){home('');return}
  var h='<a class="back" href="#">← Everyone</a><div class="card"><div class="big">'+esc(p.n)+'</div><div class="muted">'+esc(years(p))+'</div>';
  if(p.bp)h+='<div>Born in '+esc(p.bp)+'</div>';if(p.dp)h+='<div>Died in '+esc(p.dp)+'</div>';
  if(p.t)h+='<h2>Notes</h2><div class="note">'+esc(p.t)+'</div>';
  h+='</div>'+group('Parents',p.p)+group('Partners',p.s)+group('Children',p.c);
  document.getElementById('app').innerHTML=h;window.scrollTo(0,0);
}
function route(){var m=/^#p(\d+)$/.exec(location.hash);if(m)person(parseInt(m[1],10));else home('')}
window.onhashchange=route;route();
</script>
</body>
</html>
"""
}
