// Shared script for every page: header search over the website's pages and sections, the storage
// notice, and keeping --header-h in sync with the sticky header. No network requests, no tracking.
(function () {
  "use strict";

  // Pages and sections the header search can jump to. Keep in sync with the pages' headings / ids.
  var SITE = [
    { t: "Main App", u: "./", p: "App", d: "Launch the D&D Engine character builder in your browser.",
      k: "app launch play start open run character builder sheet browser" },

    { t: "About / Guide", u: "about.html", p: "Guide", d: "What D&D Engine is and how to use it.", k: "about guide help manual how to" },
    { t: "What is D&D Engine?", u: "about.html#what", p: "Guide", d: "A free, unofficial D&D 2024 (5.5e) character builder and homebrew toolkit.", k: "5.5e 2024 rules engine overview" },
    { t: "Features", u: "about.html#features", p: "Guide", d: "Character wizard, sheets, level up, Asset Library, tags, maps.", k: "features wizard level up asset library maps combat" },
    { t: "How to use the app", u: "about.html#using", p: "Guide", d: "Create a character, level up, and browse the Asset Library.", k: "getting started tutorial create character level up" },
    { t: "Asset Library & homebrew", u: "about.html#homebrew", p: "Guide", d: "Browse every built-in asset and create your own homebrew.", k: "homebrew custom items spells classes species backgrounds feats npc pack" },
    { t: "Tags & content filters", u: "about.html#tags", p: "Guide", d: "Hide sourcebooks or homebrew from character creation and level up.", k: "tags filters sourcebook xanathar tasha official homebrew campaign ruleset" },
    { t: "Browser saving", u: "about.html#saving", p: "Guide", d: "Where the web version keeps your characters (this browser only).", k: "save saving storage indexeddb local data lost" },
    { t: "JSON backups (Export / Import)", u: "about.html#backups", p: "Guide", d: "Back up or restore everything from the app's main menu.", k: "backup restore export import json file transfer move device" },
    { t: "FAQ", u: "about.html#faq", p: "Guide", d: "Common questions.", k: "faq questions phone mobile offline free" },

    { t: "Download", u: "download.html", p: "Download", d: "Run the desktop version on Windows, macOS or Linux.", k: "download desktop offline jar zip install java" },
    { t: "Requirements (Java 11+)", u: "download.html#requirements", p: "Download", d: "Install Java 11 or newer to run the desktop version.", k: "java jre jdk temurin requirements" },
    { t: "Windows", u: "download.html#windows", p: "Download", d: "Run D&D Engine on Windows.", k: "windows pc install run" },
    { t: "macOS", u: "download.html#macos", p: "Download", d: "Run D&D Engine on a Mac.", k: "mac macos apple gatekeeper" },
    { t: "Linux", u: "download.html#linux", p: "Download", d: "Run D&D Engine on Linux.", k: "linux ubuntu terminal" },
    { t: "Moving data between browser and desktop", u: "download.html#data", p: "Download", d: "Export from one, import into the other.", k: "move transfer data backup desktop browser" },
    { t: "Build from source", u: "download.html#source", p: "Download", d: "Compile the app yourself from GitHub.", k: "source github build compile javac" },

    { t: "Privacy Policy", u: "privacy.html", p: "Legal", d: "No accounts, no cookies, no tracking; data stays on your device.", k: "privacy policy data cookies tracking gdpr" },
    { t: "What is stored, and where", u: "privacy.html#stored", p: "Legal", d: "Browser storage used by the app.", k: "indexeddb localstorage stored" },
    { t: "Third-party services", u: "privacy.html#third-party", p: "Legal", d: "GitHub Pages hosting and the CheerpJ runtime.", k: "github cheerpj leaning technologies third party" },
    { t: "Terms of Service", u: "terms.html", p: "Legal", d: "The rules for using D&D Engine.", k: "terms service conditions warranty liability" },
    { t: "Fan Content Disclaimer", u: "disclaimer.html", p: "Legal", d: "Unofficial Fan Content; not approved or endorsed by Wizards of the Coast.", k: "disclaimer fan content wizards of the coast wotc copyright trademark srd" }
  ];

  var $ = function (s) { return document.querySelector(s); };
  function esc(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;"); }
  function sget(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
  function sset(k, v) { try { window.localStorage.setItem(k, v); } catch (e) { /* storage blocked */ } }

  // ---------------- header height ----------------
  var header = $("#siteHeader");
  if (header) {
    var sync = function () { document.documentElement.style.setProperty("--header-h", header.offsetHeight + "px"); };
    sync();
    if (window.ResizeObserver) new ResizeObserver(sync).observe(header); else window.addEventListener("resize", sync);
  }

  // ---------------- storage notice ----------------
  var KEY_CONSENT = "dndengine.storageNotice.v1";
  var consent = $("#consent");
  if (consent) {
    if (!sget(KEY_CONSENT)) consent.hidden = false;
    var ok = $("#consentOk");
    if (ok) ok.addEventListener("click", function () { sset(KEY_CONSENT, "1"); consent.hidden = true; });
  }

  // ---------------- site search ----------------
  var q = $("#q"), results = $("#results"), list = $("#resultList"), clearBtn = $("#qClear");
  if (!q) return;
  var current = [], active = -1;
  SITE.forEach(function (e) { e._t = e.t.toLowerCase(); e._k = (e.k + " " + e.d + " " + e.p).toLowerCase(); });

  function search(text) {
    var terms = text.toLowerCase().split(/\s+/).filter(Boolean);
    var out = [];
    SITE.forEach(function (e) {
      var score = 0;
      for (var i = 0; i < terms.length; i++) {
        var t = terms[i];
        if (e._t.indexOf(t) === 0) score += 60;
        else if (e._t.indexOf(t) > 0) score += 35;
        else if (e._k.indexOf(t) >= 0) score += 12;
        else return;
      }
      if (e.u.indexOf("#") < 0) score += 5; // whole pages slightly first
      out.push([score, e]);
    });
    out.sort(function (a, b) { return b[0] - a[0]; });
    return out.slice(0, 12).map(function (x) { return x[1]; });
  }

  function highlight(text, terms) {
    var html = esc(text);
    terms.forEach(function (t) {
      var et = esc(t).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      if (et) html = html.replace(new RegExp("(" + et + ")(?![^<]*>)", "gi"), "<mark>$1</mark>");
    });
    return html;
  }

  function show(on) { results.hidden = !on; q.setAttribute("aria-expanded", on ? "true" : "false"); if (!on) q.removeAttribute("aria-activedescendant"); }

  function render() {
    var text = q.value.trim();
    clearBtn.hidden = !q.value;
    if (!text) { show(false); return; }
    var terms = text.toLowerCase().split(/\s+/).filter(Boolean);
    current = search(text);
    active = -1;
    show(true);
    if (!current.length) {
      list.innerHTML = '<li class="empty">No pages match "' + esc(text) + '". Try "backup", "download" or "privacy".</li>';
      return;
    }
    list.innerHTML = current.map(function (e, i) {
      return '<li role="presentation"><a class="result" role="option" id="opt' + i + '" href="' + esc(e.u) + '">'
        + '<span class="top"><span class="title">' + highlight(e.t, terms) + '</span><span class="badge">' + esc(e.p) + "</span></span>"
        + '<span class="sub">' + esc(e.d) + "</span></a></li>";
    }).join("");
  }

  function setActive(i) {
    var opts = list.querySelectorAll(".result");
    if (!opts.length) return;
    active = (i + opts.length) % opts.length;
    opts.forEach(function (o, k) { o.classList.toggle("active", k === active); o.setAttribute("aria-selected", k === active ? "true" : "false"); });
    opts[active].scrollIntoView({ block: "nearest" });
    q.setAttribute("aria-activedescendant", opts[active].id);
  }

  function go(e) {
    show(false);
    q.blur();
    location.href = e.u;
  }

  q.addEventListener("input", render);
  q.addEventListener("focus", function () { if (q.value.trim()) render(); });
  q.addEventListener("keydown", function (ev) {
    if (ev.key === "ArrowDown") { ev.preventDefault(); if (results.hidden) render(); setActive(active + 1); }
    else if (ev.key === "ArrowUp") { ev.preventDefault(); setActive(active - 1); }
    else if (ev.key === "Enter") { var e = current[active >= 0 ? active : 0]; if (e) { ev.preventDefault(); go(e); } }
    else if (ev.key === "Escape") { if (!results.hidden) show(false); else { q.value = ""; render(); } }
  });
  list.addEventListener("click", function () { show(false); });
  clearBtn.addEventListener("click", function () { q.value = ""; render(); q.focus(); });
  document.addEventListener("pointerdown", function (ev) { if (!results.hidden && !$("#search").contains(ev.target)) show(false); });
})();
