/* Aqua Browser — runs once in the main process at start-up (loaded by aqua.cfg).
   It sets Aqua's defaults, loads the Aqua look into every browser window, makes the Aqua home page the
   new-tab and home page, adds the profile chip, and sets up Home and Work profiles on first run. */
const lazy = {};
ChromeUtils.defineESModuleGetters(lazy, {
  AboutNewTab: "resource:///modules/AboutNewTab.sys.mjs",
  CustomizableUI: "resource:///modules/CustomizableUI.sys.mjs",
  PlacesUtils: "resource://gre/modules/PlacesUtils.sys.mjs",
  PrivateBrowsingUtils: "resource://gre/modules/PrivateBrowsingUtils.sys.mjs",
});

const HOME = "chrome://aqua/content/home.html";
const SETTINGS = "chrome://aqua/content/settings.html";
const BROWSER = "chrome://browser/content/browser.xhtml";

// Aqua's own settings (per profile, like every pref).
const AQUA_DEFAULTS = {
  "aqua.color": "blue", "aqua.hue": 214, "aqua.lcdHue": 192, "aqua.lcdMatch": true,
  "aqua.autoCycle": 30, "aqua.autoMode": "step",
  "aqua.glass": 60, "aqua.blur": 16, "aqua.roundness": 100,
  "aqua.addressFont": "dot", "aqua.font": "barlow", "aqua.frame": true,
  "aqua.newTab": "home", "aqua.customCSS": "", "aqua.firstRun": true, "aqua.welcomed": false,
};
// Engine defaults that suit Aqua Browser (a person's own changes still win).
const ENGINE_DEFAULTS = {
  "browser.startup.homepage": HOME,
  "browser.startup.page": 3,
  "browser.shell.checkDefaultBrowser": false,
  "extensions.activeThemeID": "firefox-compact-dark@mozilla.org",
  "sidebar.revamp": true,
  "sidebar.verticalTabs": false,
  "browser.toolbars.bookmarks.visibility": "always",
  "browser.aboutwelcome.enabled": false,
  "browser.startup.homepage_override.mstone": "ignore",
  "startup.homepage_welcome_url": "",
  "startup.homepage_welcome_url.additional": "",
  "browser.newtabpage.activity-stream.showSponsored": false,
  "browser.newtabpage.activity-stream.showSponsoredTopSites": false,
  "browser.urlbar.suggest.quicksuggest.sponsored": false,
  "browser.preferences.moreFromMozilla": false,
  "identity.fxaccounts.toolbar.enabled": false,
  "signon.rememberSignons": true,
  "signon.autofillForms": true,
  "signon.generation.enabled": true,
  "extensions.formautofill.creditCards.supported": "on",
  "extensions.formautofill.creditCards.enabled": true,
  "extensions.formautofill.addresses.supported": "on",
  "media.eme.enabled": true,
  "media.gmp-widevinecdm.visible": true,
  "media.gmp-widevinecdm.enabled": true,
  "datareporting.healthreport.uploadEnabled": false,
  "datareporting.policy.dataSubmissionEnabled": false,
  "toolkit.telemetry.enabled": false,
  "app.update.auto": false,
  "browser.compactmode.show": true,
  "layout.css.backdrop-filter.enabled": true,
  "browser.tabs.tabmanager.enabled": true,
};

function setDefaults(map) {
  const d = Services.prefs.getDefaultBranch("");
  for (const [k, v] of Object.entries(map)) {
    try {
      if (typeof v === "boolean") d.setBoolPref(k, v);
      else if (typeof v === "number") d.setIntPref(k, v);
      else d.setStringPref(k, v);
    } catch (e) { /* locked or wrong type: leave it */ }
  }
}

export function pref(k, fallback) {
  const P = Services.prefs;
  try {
    switch (P.getPrefType(k)) {
      case P.PREF_STRING: return P.getStringPref(k);
      case P.PREF_INT: return P.getIntPref(k);
      case P.PREF_BOOL: return P.getBoolPref(k);
    }
  } catch (e) {}
  return fallback;
}
export function setPref(k, v) {
  const P = Services.prefs;
  if (typeof v === "boolean") P.setBoolPref(k, v);
  else if (typeof v === "number") P.setIntPref(k, Math.round(v));
  else P.setStringPref(k, String(v));
}

/* ---------- colours (the same maths as Aqua MD and Aqua Browser 1) ---------- */
export const COLORS = { blue: [214, 192], teal: [178, 168], green: [140, 128], orange: [26, 36], red: [354, 2], pink: [322, 316], purple: [266, 258] };
export const ORDER = Object.keys(COLORS);
const unwrap = arr => { const out = [arr[0]]; for (let i = 1; i <= arr.length; i++) { const v = arr[i % arr.length]; const d = (((v - out[i - 1]) % 360) + 540) % 360 - 180; out.push(out[i - 1] + d); } return out; };
const TRACK_H = unwrap(ORDER.map(k => COLORS[k][0])), TRACK_L = unwrap(ORDER.map(k => COLORS[k][1]));
export function lcdFor(hue) {
  const offs = ORDER.map(k => ({ h: COLORS[k][0], o: ((COLORS[k][1] - COLORS[k][0] + 540) % 360) - 180 })).sort((a, b) => a.h - b.h);
  const x = ((hue % 360) + 360) % 360;
  let lo = offs[offs.length - 1], hi = offs[0];
  for (let i = 0; i < offs.length; i++) { if (offs[i].h >= x) { hi = offs[i]; lo = offs[(i - 1 + offs.length) % offs.length]; break; } }
  const span = ((hi.h - lo.h) + 360) % 360 || 360;
  const t = (((x - lo.h) + 360) % 360) / span;
  return Math.round(x + lo.o + (hi.o - lo.o) * t);
}
// The colour right now. "Auto" walks the seven colours on the clock, so every window agrees.
// lap0 keeps the unwrapped hue small for this window (so it turns the short way round the wheel).
export function currentHue(isPrivate, state = {}) {
  if (isPrivate) return { h: 266, lh: 258, next: 0, fade: "1.4s" };
  const color = pref("aqua.color", "blue");
  const lcdMatch = pref("aqua.lcdMatch", true);
  let c;
  if (color === "auto") {
    const period = Math.max(5, pref("aqua.autoCycle", 30)) * 1000, now = Date.now();
    const k = Math.floor(now / period), f = (now % period) / period, lap = Math.floor(k / 7), i = k % 7;
    if (state.lap0 == null) state.lap0 = lap;
    const L = lap - state.lap0;
    const at = j => ({ h: TRACK_H[j] - 360 * L, lh: TRACK_L[j] - 360 * L });
    const a = at(i), b = at(i + 1);
    const smooth = pref("aqua.autoMode", "step") === "smooth";
    c = smooth ? { h: a.h + (b.h - a.h) * f, lh: a.lh + (b.lh - a.lh) * f, next: 1000, fade: "1s" } : { h: a.h, lh: a.lh, next: period - (now % period), fade: "2.6s" };
  } else if (color === "custom") {
    const h = pref("aqua.hue", 214);
    c = { h, lh: lcdFor(h), next: 0, fade: "1.4s" };
  } else {
    const [h, lh] = COLORS[color] || COLORS.blue;
    c = { h, lh, next: 0, fade: "1.4s" };
  }
  if (lcdMatch === false) c.lh = pref("aqua.lcdHue", 192);
  return c;
}

/* ---------- profiles (separate sign-ins, history, passwords, home page, colour) ---------- */
const profileService = () => Cc["@mozilla.org/toolkit/profile-service;1"].getService(Ci.nsIToolkitProfileService);
const flush = async ps => { if (ps.asyncFlush) await ps.asyncFlush(); else ps.flush(); };
export const Profiles = {
  current() {
    try {
      const ps = profileService();
      if (ps.currentProfile) return ps.currentProfile.name;
      const dir = Services.dirsvc.get("ProfD", Ci.nsIFile).path;
      for (const p of ps.profiles) if (p.rootDir.path === dir) return p.name;
    } catch (e) {}
    return "Profile";
  },
  list() {
    const ps = profileService(), cur = this.current(), out = [];
    for (const p of ps.profiles) out.push({ name: p.name, current: p.name === cur });
    return out;
  },
  open(name) {
    const exe = Services.dirsvc.get("XREExeF", Ci.nsIFile);
    const proc = Cc["@mozilla.org/process/util;1"].createInstance(Ci.nsIProcess);
    proc.init(exe);
    proc.runwAsync(["-P", name], 2);
  },
  async create(name, color) {
    const ps = profileService();
    name = String(name || "").trim().slice(0, 24);
    if (!name) throw new Error("Give the profile a name.");
    for (const p of ps.profiles) if (p.name.toLowerCase() === name.toLowerCase()) throw new Error("There is already a profile with that name.");
    const p = ps.createProfile(null, name);
    await flush(ps);
    await IOUtils.writeUTF8(PathUtils.join(p.rootDir.path, "user.js"),
      `user_pref("aqua.color", ${JSON.stringify(color || "purple")});\nuser_pref("aqua.firstRun", false);\n`);
    return name;
  },
  async rename(oldName, newName) {
    const ps = profileService();
    newName = String(newName || "").trim().slice(0, 24);
    if (!newName) return;
    for (const p of ps.profiles) if (p.name === oldName) p.name = newName;
    await flush(ps);
  },
  // First run: call the starting profile "Home" and add an orange "Work" profile next to it.
  async firstRun() {
    if (!pref("aqua.firstRun", true)) return;
    setPref("aqua.firstRun", false);
    try {
      const ps = profileService();
      const cur = ps.currentProfile;
      if (!cur) return; // started with -profile <dir> (tests)
      const names = [...ps.profiles].map(p => p.name);
      if (!names.includes("Home") && /^default/i.test(cur.name)) cur.name = "Home";
      if (!names.includes("Work")) {
        const w = ps.createProfile(null, "Work");
        await IOUtils.writeUTF8(PathUtils.join(w.rootDir.path, "user.js"), 'user_pref("aqua.color", "orange");\nuser_pref("aqua.firstRun", false);\n');
      }
      await flush(ps);
    } catch (e) { console.error("Aqua: could not set up profiles", e); }
  }
};

/* ---------- the home page's data ---------- */
const homeFile = () => PathUtils.join(PathUtils.profileDir, "aqua-home.json");
export const Home = {
  async load() { try { return await IOUtils.readJSON(homeFile()); } catch (e) { return null; } },
  async save(data) { await IOUtils.writeJSON(homeFile(), data, { tmpPath: homeFile() + ".tmp" }); },
  async topSites(n = 8) {
    const db = await lazy.PlacesUtils.promiseDBConnection();
    const rows = await db.execute(`SELECT url, title FROM moz_places WHERE hidden = 0 AND frecency > 0 AND (url LIKE 'https://%' OR url LIKE 'http://%') ORDER BY frecency DESC LIMIT 80`);
    const seen = new Set(), out = [];
    for (const r of rows) {
      const url = r.getResultByName("url");
      let host; try { host = new URL(url).hostname.replace(/^www\./, ""); } catch (e) { continue; }
      if (seen.has(host)) continue;
      seen.add(host);
      out.push({ url, title: r.getResultByName("title") || host, favicon: "page-icon:" + url });
      if (out.length >= n) break;
    }
    return out;
  },
  async bookmarks() {
    const conv = node => {
      if (!node) return [];
      return (node.children || []).map(c => c.type === "text/x-moz-place-container"
        ? { id: c.guid, title: c.title || "Folder", children: conv(c) }
        : (c.uri && /^(https?|file):/.test(c.uri) ? { id: c.guid, title: c.title || "", url: c.uri, favicon: "page-icon:" + c.uri } : null)).filter(Boolean);
    };
    const B = lazy.PlacesUtils.bookmarks;
    const [bar, other] = await Promise.all([lazy.PlacesUtils.promiseBookmarksTree(B.toolbarGuid), lazy.PlacesUtils.promiseBookmarksTree(B.unfiledGuid)]);
    return { bar: conv(bar), other: conv(other) };
  },
  async engines() {
    await Services.search.init();
    const list = await Services.search.getVisibleEngines();
    const def = await Services.search.getDefault();
    return { list: list.map(e => e.name), def: def ? def.name : (list[0] && list[0].name) };
  },
  searchURL(q, name) {
    const e = (name && Services.search.getEngineByName(name)) || Services.search.defaultEngine;
    return e.getSubmission(q).uri.spec;
  },
  // The address to ask the search engine for suggestions (the page itself does the request).
  suggestURL(q, name) {
    if (!q || !pref("browser.search.suggest.enabled", true)) return "";
    const e = (name && Services.search.getEngineByName(name)) || Services.search.defaultEngine;
    const sub = e && e.getSubmission(q, "application/x-suggestions+json");
    return sub ? sub.uri.spec : "";
  }
};

/* ---------- windows ---------- */
function onWindowOpened(win) {
  win.addEventListener("DOMContentLoaded", () => {
    if (win.location.href !== BROWSER) return;
    // The look goes in before the window paints, so there's no flash of the stock look.
    try { win.windowUtils.loadSheetUsingURIString("chrome://aqua/content/css/aqua-browser.css", win.windowUtils.AUTHOR_SHEET); }
    catch (e) { console.error("Aqua: could not load the look", e); }
  }, { once: true });
}
function onWindowReady(win) {
  try {
    if (win.gInitialPages && !win.gInitialPages.includes(HOME)) win.gInitialPages.push(HOME);
    Services.scriptloader.loadSubScript("chrome://aqua/content/js/aqua-window.js", win);
  } catch (e) { console.error("Aqua: window script failed", e); }
  applyNewTab();
}
function applyNewTab() {
  try {
    const mode = pref("aqua.newTab", "home");
    if (mode === "home") lazy.AboutNewTab.newTabURL = HOME;
    else if (mode === "blank") lazy.AboutNewTab.newTabURL = "about:blank";
    else lazy.AboutNewTab.resetNewTabURL();
  } catch (e) { console.error("Aqua: new tab page", e); }
}

let widgetMade = false;
function makeWidgets() {
  if (widgetMade) return;
  widgetMade = true;
  try {
    lazy.CustomizableUI.createWidget({
      id: "aqua-profile-button",
      type: "button",
      label: Profiles.current(),
      tooltiptext: "Profiles",
      defaultArea: lazy.CustomizableUI.AREA_NAVBAR,
      onCreated(node) { node.setAttribute("label", Profiles.current()); },
      onCommand(e) { const w = e.target.ownerGlobal; if (w.AquaUI) w.AquaUI.profileMenu(e.target); }
    });
  } catch (e) { console.error("Aqua: profile button", e); }
}

let started = false;
export const AquaBoot = {
  HOME, SETTINGS,
  start() {
    if (started) return;
    started = true;
    setDefaults(AQUA_DEFAULTS);
    setDefaults(ENGINE_DEFAULTS);
    Services.obs.addObserver(subject => onWindowOpened(subject), "domwindowopened");
    Services.obs.addObserver(subject => { makeWidgets(); onWindowReady(subject); }, "browser-delayed-startup-finished");
    Services.prefs.addObserver("aqua.newTab", applyNewTab);
    Services.obs.addObserver(() => { Profiles.firstRun(); }, "final-ui-startup");
    // A global style for the engine's own pages (passwords, settings, add-ons): Aqua colours.
    try {
      const sss = Cc["@mozilla.org/content/style-sheet-service;1"].getService(Ci.nsIStyleSheetService);
      const uri = Services.io.newURI("chrome://aqua/content/css/aqua-pages.css");
      if (!sss.sheetRegistered(uri, sss.USER_SHEET)) sss.loadAndRegisterSheet(uri, sss.USER_SHEET);
    } catch (e) { console.error("Aqua: page styles", e); }
  },
  openSettings(win, section) {
    const url = SETTINGS + (section ? "#" + section : "");
    win.switchToTabHavingURI(url, true, { replaceQueryString: true, ignoreFragment: "whenComparingAndReplace" });
  },
  isPrivate(win) { try { return lazy.PrivateBrowsingUtils.isWindowPrivate(win); } catch (e) { return false; } },
  pref, setPref, currentHue, Profiles, Home
};
