/* Aqua Browser — runs in every browser window (loaded by boot.sys.mjs).
   Applies the colour (and the Auto cycle), glass and shape settings, the profile chip menu,
   the LCD profile label in the address bar, and your own browser CSS. */
(function () {
  "use strict";
  if (window.AquaUI) return;
  const { AquaBoot, pref, currentHue, Profiles } = ChromeUtils.importESModule("chrome://aqua/content/boot.sys.mjs");
  const root = document.documentElement;
  const isPrivate = AquaBoot.isPrivate(window);
  const hueState = {};
  let timer = null, userSheet = null;

  function applyColour() {
    clearTimeout(timer);
    const c = currentHue(isPrivate, hueState);
    root.style.setProperty("--aqua-fade", c.fade);
    root.style.setProperty("--aqua-h", c.h.toFixed(2));
    root.style.setProperty("--aqua-lh", c.lh.toFixed(2));
    if (c.next) timer = setTimeout(applyColour, Math.max(250, c.next + 30));
  }
  function applyLook() {
    root.style.setProperty("--aqua-ga", (pref("aqua.glass", 60) / 60).toFixed(3));
    root.style.setProperty("--aqua-blur", pref("aqua.blur", 16) + "px");
    root.style.setProperty("--aqua-rs", (pref("aqua.roundness", 100) / 100).toFixed(3));
    root.classList.toggle("aqua-addr-plain", pref("aqua.addressFont", "dot") === "plain");
    root.classList.toggle("aqua-font-system", pref("aqua.font", "barlow") === "system");
    root.classList.toggle("aqua-no-frame", !pref("aqua.frame", true));
    root.classList.toggle("aqua-private", isPrivate);
    const css = pref("aqua.customCSS", "");
    const wu = window.windowUtils;
    if (userSheet) { try { wu.removeSheetUsingURIString(userSheet, wu.AUTHOR_SHEET); } catch (e) {} userSheet = null; }
    if (css.trim()) { userSheet = "data:text/css;charset=utf-8," + encodeURIComponent(css); try { wu.loadSheetUsingURIString(userSheet, wu.AUTHOR_SHEET); } catch (e) { console.error("Aqua: your browser CSS has a problem", e); } }
    paintLabels();
  }

  // "HOME" / "WORK" / "PRIVATE" glowing in the address bar, like the indicators on Aqua MD's display.
  function paintLabels() {
    const name = isPrivate ? "Private" : Profiles.current();
    let ind = document.getElementById("aqua-profile-ind");
    const host = document.getElementById("page-action-buttons");
    if (!ind && host) {
      ind = document.createXULElement("label");
      ind.id = "aqua-profile-ind";
      ind.className = "aqua-ind";
      host.prepend(ind);
    }
    if (ind) ind.setAttribute("value", name.toUpperCase());
    const chip = document.getElementById("aqua-profile-button");
    if (chip) { chip.setAttribute("label", name); chip.setAttribute("tooltiptext", isPrivate ? "Private window" : `Profile: ${name}`); }
  }

  // With aqua.debug on, logs which elements paint a few spots of the toolbar (for fixing the look when
  // Mozilla changes the window's structure).
  function debugDump() {
    const desc = el => `${el.localName}${el.id ? "#" + el.id : ""}${el.classList && el.classList.length ? "." + [...el.classList].slice(0, 4).join(".") : ""}`;
    const bg = el => { const cs = getComputedStyle(el); return `${cs.backgroundColor} ${cs.backgroundImage === "none" ? "" : cs.backgroundImage.slice(0, 50)}`.trim(); };
    for (const [x, y, what] of [[innerWidth * 0.62, 16, "tab strip"], [innerWidth * 0.62, 66, "nav bar"], [innerWidth * 0.35, 66, "address bar"], [innerWidth * 0.5, 104, "bookmarks bar"]]) {
      const stack = document.elementsFromPoint(x, y).slice(0, 14).map(e => `${desc(e)} {${bg(e)}}`);
      console.log(`Aqua debug ${what} @${Math.round(x)},${y}: ` + stack.join(" || "));
    }
    const ub = document.getElementById("urlbar");
    if (ub) console.log("Aqua debug urlbar tree: " + [ub, ...ub.querySelectorAll("*")].slice(0, 45).map(desc).join(" | "));
  }
  if (pref("aqua.debug", false)) setTimeout(debugDump, 7000);

  const P = Services.prefs;
  const obs = { observe() { applyLook(); applyColour(); } };
  P.addObserver("aqua.", obs);
  window.addEventListener("unload", () => { P.removeObserver("aqua.", obs); clearTimeout(timer); }, { once: true });

  /* ---------- the profile chip's menu ---------- */
  function item(label, cmd, opts = {}) {
    const m = document.createXULElement("menuitem");
    m.setAttribute("label", label);
    if (opts.disabled) m.setAttribute("disabled", "true");
    if (opts.checked) { m.setAttribute("type", "checkbox"); m.setAttribute("checked", "true"); }
    if (opts.cls) m.className = opts.cls;
    if (cmd) m.addEventListener("command", cmd);
    return m;
  }
  function profileMenu(anchor) {
    let popup = document.getElementById("aqua-profile-popup");
    if (!popup) {
      popup = document.createXULElement("menupopup");
      popup.id = "aqua-profile-popup";
      document.getElementById("mainPopupSet").appendChild(popup);
    }
    popup.replaceChildren();
    popup.append(item("PROFILES", null, { disabled: true, cls: "aqua-menu-caption" }));
    for (const p of Profiles.list()) {
      popup.append(item(p.name + (p.current && !isPrivate ? "  ·  this window" : ""), () => { if (!p.current || isPrivate) Profiles.open(p.name); else window.OpenBrowserWindow(); }, { checked: p.current && !isPrivate }));
    }
    popup.append(document.createXULElement("menuseparator"));
    popup.append(item("New private window", () => window.OpenBrowserWindow({ private: true })));
    popup.append(item("Add a profile…", () => AquaBoot.openSettings(window, "profiles")));
    popup.append(item("Aqua settings", () => AquaBoot.openSettings(window)));
    popup.openPopup(anchor, "after_end", 0, 4, false, false);
  }

  window.AquaUI = { profileMenu, applyLook, applyColour };
  applyLook();
  applyColour();

  // First window of a fresh install: show the welcome page once.
  if (!isPrivate && !pref("aqua.welcomed", false)) {
    Services.prefs.setBoolPref("aqua.welcomed", true);
    setTimeout(() => { try { window.gBrowser.selectedTab = window.gBrowser.addTrustedTab(AquaBoot.SETTINGS + "#welcome"); } catch (e) { console.error(e); } }, 600);
  }
})();
