'use strict';
/* Shared helpers for the browser UI: element builder, icons, theme (incl. the Auto colour cycle),
   menus, popovers, dialogs, toasts, URL helpers. Everything hangs off one global, A. */
const A = window.A = { api: window.aqua, isMac: window.aqua.platform === 'darwin' };
// In this UI, append(…, cond ? el : null) should skip the null instead of printing "null".
for (const proto of [Element.prototype, DocumentFragment.prototype]) {
  for (const m of ['append', 'prepend', 'replaceChildren']) {
    const orig = proto[m];
    proto[m] = function (...a) { return orig.apply(this, a.flat(Infinity).filter(x => x != null && x !== false)); };
  }
}

A.$ = (s, r = document) => r.querySelector(s);
A.$$ = (s, r = document) => [...r.querySelectorAll(s)];

// h('div.card.glass#id', { onclick, title, dataset: {}, style: {} }, child, 'text', [more])
A.h = (sel, props, ...kids) => {
  const m = String(sel).match(/^([a-z0-9-]+)?((?:[.#][\w-]+)*)$/i) || [];
  const el = document.createElement(m[1] || 'div');
  for (const p of (m[2] || '').match(/[.#][\w-]+/g) || []) { if (p[0] === '.') el.classList.add(p.slice(1)); else el.id = p.slice(1); }
  if (props && (typeof props !== 'object' || props instanceof Node || Array.isArray(props))) { kids.unshift(props); props = null; }
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'style' && typeof v === 'object') { for (const [sk, sv] of Object.entries(v)) { if (sv == null) continue; if (sk.startsWith('--')) el.style.setProperty(sk, String(sv)); else el.style[sk] = sv; } }
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else if (k === 'class') el.className = v;
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'text') el.textContent = v;
    else if (k in el && k !== 'list' && k !== 'form') { try { el[k] = v; } catch (e) { el.setAttribute(k, v); } }
    else el.setAttribute(k, v === true ? '' : v);
  }
  const add = c => { if (c == null || c === false) return; if (Array.isArray(c)) c.forEach(add); else el.append(c instanceof Node ? c : document.createTextNode(String(c))); };
  kids.forEach(add);
  return el;
};
const { h } = A;

/* ---------- icons (stroke icons, 24-unit grid) ---------- */
const P = {
  back: 'M15 5l-7 7 7 7', fwd: 'M9 5l7 7-7 7', reload: 'M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7', stop: 'M6 6l12 12M18 6 6 18',
  home: 'M3.5 11.5 12 4l8.5 7.5M6 10v9.5h4.5v-5h3v5H18V10', star: 'M12 3.6l2.6 5.3 5.8.85-4.2 4.1 1 5.8L12 16.9l-5.2 2.75 1-5.8-4.2-4.1 5.8-.85z',
  download: 'M12 4v11M7 10.5l5 5 5-5M5 20h14', key: 'M8.5 15.5m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0M11.3 12.7 20 4M16 8l3 3M14 10l2 2',
  menu: 'M4 7h16M4 12h16M4 17h16', dots: 'M12 5.5v.01M12 12v.01M12 18.5v.01', x: 'M6 6l12 12M18 6 6 18', plus: 'M12 5v14M5 12h14',
  min: 'M6 12h12', max: 'M6 6h12v12H6z', restore: 'M8.5 8.5h10v10h-10zM5.5 15.5v-10h10', lock: 'M5 10.5h14v10H5zM8 10.5V7.5a4 4 0 0 1 8 0v3',
  unlock: 'M5 10.5h14v10H5zM8 10.5V7.5a4 4 0 0 1 7.6-1.7', warn: 'M12 4 2.8 19.5h18.4zM12 10v4.5M12 17.2v.01', search: 'M10.5 10.5m-6 0a6 6 0 1 0 12 0a6 6 0 1 0-12 0M15 15l5 5',
  globe: 'M12 12m-8.5 0a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0M3.5 12h17M12 3.5c2.6 2.7 3.5 5.6 3.5 8.5s-.9 5.8-3.5 8.5M12 3.5C9.4 6.2 8.5 9.1 8.5 12s.9 5.8 3.5 8.5',
  folder: 'M3.5 6.5h6l2 2.5h9v9.5h-17z', history: 'M4 12a8 8 0 1 0 2.4-5.7M4 4.5v3.8h3.8M12 8v4.5l3 1.8', gear: 'M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0M12 2.8v2.6M12 18.6v2.6M21.2 12h-2.6M5.4 12H2.8M18.5 5.5l-1.8 1.8M7.3 16.7l-1.8 1.8M18.5 18.5l-1.8-1.8M7.3 7.3 5.5 5.5',
  user: 'M12 8.5m-3.8 0a3.8 3.8 0 1 0 7.6 0a3.8 3.8 0 1 0-7.6 0M4.5 20c.8-3.6 3.8-5.5 7.5-5.5s6.7 1.9 7.5 5.5', card: 'M2.5 5h19v14h-19zM2.5 10h19M6 15h4',
  eye: 'M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM12 12m-2.8 0a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0-5.6 0', eyeoff: 'M4 4l16 16M9.9 5.8A9.7 9.7 0 0 1 12 5.5C18 5.5 21.5 12 21.5 12a17 17 0 0 1-3 3.7M6.6 6.7C3.9 8.4 2.5 12 2.5 12S6 18.5 12 18.5a9 9 0 0 0 4.4-1.1M9.9 9.9a2.8 2.8 0 0 0 4 4',
  copy: 'M8.5 8.5h11v11h-11zM15.5 8.5v-4h-11v11h4', edit: 'M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4', trash: 'M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5',
  up: 'M12 19V5M6 11l6-6 6 6', down: 'M12 5v14M6 13l6 6 6-6', grip: 'M9 6v.01M15 6v.01M9 12v.01M15 12v.01M9 18v.01M15 18v.01', check: 'M5 12.5l4.5 4.5L19 7.5',
  sound: 'M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11', mute: 'M4 9.5h3.5L12 5.5v13l-4.5-4H4zM16 9.5l5 5M21 9.5l-5 5',
  pin: 'M14.5 4.5l5 5-3 1.5-3.5 3.5.5 4-2 2-8-8 2-2 4 .5 3.5-3.5z', shield: 'M12 3.5 19.5 6v5.5c0 4.6-3.2 7.8-7.5 9-4.3-1.2-7.5-4.4-7.5-9V6z',
  shieldoff: 'M12 3.5 19.5 6v5.5c0 1.6-.4 3-1.1 4.2M5.5 5.8 4.5 6v5.5c0 4.6 3.2 7.8 7.5 9 1.9-.5 3.5-1.5 4.8-2.7M3.5 3.5l17 17',
  chev: 'M6 9l6 6 6-6', chevr: 'M9 6l6 6-6 6', chevl: 'M15 6l-6 6 6 6', ext: 'M14 4.5h5.5V10M19.5 4.5 11 13M18 14v5.5H4.5V6H10',
  file: 'M6 3.5h8l4.5 4.5v12.5H6zM14 3.5V8h4.5', image: 'M3.5 5h17v14h-17zM3.5 16l5-5 4 4 3-3 4.5 4.5M15.5 9.2v.01',
  note: 'M5 3.5h14v17H5zM8.5 8.5h7M8.5 12h7M8.5 15.5h4', todo: 'M4.5 6.5l1.5 1.5 3-3M4.5 12.5l1.5 1.5 3-3M4.5 18.5l1.5 1.5 3-3M12 7h8M12 13h8M12 19h8',
  calendar: 'M4 6h16v14H4zM4 10h16M8.5 3.5V7M15.5 3.5V7', timer: 'M12 13.5m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0M12 13.5V10M9.5 3.5h5M18.5 6.5l1.5-1.5',
  rss: 'M5 11a8 8 0 0 1 8 8M5 5a14 14 0 0 1 14 14M5.5 18.5v.01', spark: 'M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6',
  layout: 'M3.5 4.5h17v15h-17zM3.5 10h17M10 10v9.5', palette: 'M12 3.5a8.5 8.5 0 0 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.2-1-1.6-1-2.6 0-1 .8-1.7 1.8-1.7h2.2c2.1 0 3.7-1.6 3.7-3.9C20.5 6.6 16.7 3.5 12 3.5zM7.5 11v.01M10 7.5v.01M14.5 7.5v.01',
  tabs: 'M3.5 7.5h17v12h-17zM3.5 7.5l2-3h5l2 3', sidebar: 'M3.5 4.5h17v15h-17zM9 4.5v15', mask: 'M3 10c0-2 1.5-3.5 4-3.5 2 0 3 1 5 1s3-1 5-1c2.5 0 4 1.5 4 3.5 0 4-2.5 6-5 6-2 0-2.5-2-4-2s-2 2-4 2c-2.5 0-5-2-5-6zM7 11h2M15 11h2',
  clock: 'M12 12m-8.5 0a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0M12 7v5l3.5 2', cloud: 'M7 18.5h10.5a4 4 0 0 0 .6-8 6 6 0 0 0-11.5 1A3.5 3.5 0 0 0 7 18.5z',
  hand: 'M12 3.5c-4.7 0-8.5 3.8-8.5 8.5s3.8 8.5 8.5 8.5 8.5-3.8 8.5-8.5S16.7 3.5 12 3.5zM9 10v.01M15 10v.01M8.5 14.5c1 1.2 2.2 1.8 3.5 1.8s2.5-.6 3.5-1.8',
  link: 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3A4 4 0 0 0 11 18.7l1-1', bookmark: 'M6.5 3.5h11v17l-5.5-4-5.5 4z',
  print: 'M7 9V3.5h10V9M7 17H4.5V9h15v8H17M7 14h10v6.5H7z', code: 'M8.5 7.5 4 12l4.5 4.5M15.5 7.5 20 12l-4.5 4.5M13.5 5l-3 14', find: 'M10.5 10.5m-6 0a6 6 0 1 0 12 0a6 6 0 1 0-12 0M15 15l5 5M8 10.5h5',
  zoomin: 'M10.5 10.5m-6 0a6 6 0 1 0 12 0a6 6 0 1 0-12 0M15 15l5 5M10.5 8v5M8 10.5h5', zoomout: 'M10.5 10.5m-6 0a6 6 0 1 0 12 0a6 6 0 1 0-12 0M15 15l5 5M8 10.5h5',
  window: 'M3.5 5h17v14h-17zM3.5 9h17', keyboard: 'M2.5 6.5h19v11h-19zM6 10h.01M9.5 10h.01M13 10h.01M16.5 10h.01M7 14h10',
  import: 'M12 3.5v11M7.5 10l4.5 4.5 4.5-4.5M4.5 15v4.5h15V15', export: 'M12 14.5v-11M7.5 8 12 3.5 16.5 8M4.5 15v4.5h15V15', heart: 'M12 20s-7.5-4.6-7.5-10A4.4 4.4 0 0 1 12 7a4.4 4.4 0 0 1 7.5 3c0 5.4-7.5 10-7.5 10z',
  wand: 'M5 19 15.5 8.5M14 4.5v2M18.5 9h2M17.5 5.5l1.5-1.5M10.5 4.5l.8 1.6M19.5 13.5l-1.6-.8', spacer: 'M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4'
};
A.icon = (name, cls = '') => {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24'); s.setAttribute('class', 'i ' + cls); s.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', P[name] || P.globe); s.append(p);
  return s;
};
A.setIcon = (el, name) => { el.replaceChildren(A.icon(name)); return el; };

/* ---------- small helpers ---------- */
A.debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
A.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
A.uid = () => Math.random().toString(36).slice(2, 10);
A.hostOf = u => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch (e) { return ''; } };
A.originOf = u => { try { const x = new URL(u); return x.origin; } catch (e) { return ''; } };
A.hashHue = s => { let x = 0; for (const c of String(s)) x = (x * 31 + c.charCodeAt(0)) >>> 0; return x % 360; };
A.fmtBytes = n => { if (!(n > 0)) return '0 B'; const u = ['B', 'KB', 'MB', 'GB', 'TB']; const i = Math.min(4, Math.floor(Math.log(n) / Math.log(1024))); return (n / Math.pow(1024, i)).toFixed(i ? 1 : 0) + ' ' + u[i]; };
A.fmtTime = ts => new Date(ts).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
A.dayLabel = ts => {
  const d = new Date(ts), t = new Date(); t.setHours(0, 0, 0, 0);
  const diff = Math.round((t - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 86400e3);
  if (diff === 0) return 'Today'; if (diff === 1) return 'Yesterday';
  return d.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric', year: d.getFullYear() === t.getFullYear() ? undefined : 'numeric' });
};
A.isHome = u => !u || /^aqua:\/\/(home|newtab)\/?$/i.test(u);
A.prettyURL = u => { try { return decodeURI(u); } catch (e) { return u; } };

// Favicon: the page's own icon, a lookup by domain, or a coloured letter.
A.fav = (iconUrl, pageUrl, opts = {}) => {
  const host = A.hostOf(pageUrl) || '';
  const box = h('span.fav');
  const letter = () => { box.replaceChildren(h('span.letter', { style: { '--fh': A.hashHue(host) } }, (host[0] || '•').toUpperCase())); };
  const src = iconUrl || (opts.lookup && host && /^https?:/.test(pageUrl) ? `https://icons.duckduckgo.com/ip3/${host}.ico` : '');
  if (!src) { if (A.isHome(pageUrl)) box.replaceChildren(A.icon('home')), box.classList.add('home'); else if (/^file:/.test(pageUrl || '')) box.replaceChildren(A.icon('file')); else letter(); return box; }
  const img = h('img', { src, alt: '', draggable: false, referrerPolicy: 'no-referrer' });
  img.onerror = () => { if (opts.lookup && img.src !== `https://icons.duckduckgo.com/ip3/${host}.ico` && host) img.src = `https://icons.duckduckgo.com/ip3/${host}.ico`; else letter(); };
  box.append(img);
  return box;
};

/* ---------- address bar text -> URL ---------- */
A.searchURL = (q, engine) => {
  const st = A.st;
  const key = engine || st.search;
  const tpl = key === 'custom' && /%s/.test(st.customSearch) ? st.customSearch : (A.engines[key] || A.engines.google).url;
  return tpl.replace('%s', encodeURIComponent(q));
};
A.toURL = (text, opts = {}) => {
  const t = String(text || '').trim();
  if (!t) return null;
  if (/^aqua:\/\//i.test(t)) return t.toLowerCase();
  if (/^(https?|file|view-source|about):/i.test(t)) return t;
  if (/^(localhost|\[[0-9a-f:]+\]|(\d{1,3}\.){3}\d{1,3})(:\d+)?([\/?#]\S*)?$/i.test(t)) return 'http://' + t;
  if (!/\s/.test(t) && /^[\w-]+(\.[\w-]+)*\.(xn--[\w-]+|[a-z]{2,24})(:\d+)?([\/?#]\S*)?$/i.test(t)) return 'https://' + t;
  if (/^[\w-]+:\d+(\/\S*)?$/.test(t)) return 'http://' + t;
  if (/^(\/|~\/)\S/.test(t) && !opts.noFile) return 'file://' + t.replace(/^~\//, (A.homeDir || '') + '/');
  return A.searchURL(t);
};

/* ---------- theme: colours, glass, the Auto cycle ---------- */
A.COLORS = { blue: { h: 214, lh: 192, name: 'Blue' }, teal: { h: 178, lh: 168, name: 'Teal' }, green: { h: 140, lh: 128, name: 'Green' }, orange: { h: 26, lh: 36, name: 'Orange' }, red: { h: 354, lh: 2, name: 'Red' }, pink: { h: 322, lh: 316, name: 'Pink' }, purple: { h: 266, lh: 258, name: 'Purple' } };
A.ORDER = Object.keys(A.COLORS);
// The display (LCD) hue for any shell hue, blended from the named colours' own shifts (as Aqua MD does).
A.lcdFor = hue => {
  const offs = A.ORDER.map(k => ({ h: A.COLORS[k].h, o: ((A.COLORS[k].lh - A.COLORS[k].h + 540) % 360) - 180 })).sort((a, b) => a.h - b.h);
  const x = ((hue % 360) + 360) % 360;
  let lo = offs[offs.length - 1], hi = offs[0];
  for (let i = 0; i < offs.length; i++) { if (offs[i].h >= x) { hi = offs[i]; lo = offs[(i - 1 + offs.length) % offs.length]; break; } }
  const span = ((hi.h - lo.h) + 360) % 360 || 360;
  const t = (((x - lo.h) + 360) % 360) / span;
  return Math.round(x + lo.o + (hi.o - lo.o) * t);
};
// Unwrapped hue tracks so blue -> teal -> ... -> purple -> blue always turns the short way round the wheel.
const unwrap = arr => { const out = [arr[0]]; for (let i = 1; i <= arr.length; i++) { const v = arr[i % arr.length]; const d = (((v - out[i - 1]) % 360) + 540) % 360 - 180; out.push(out[i - 1] + d); } return out; };
const TRACK_H = unwrap(A.ORDER.map(k => A.COLORS[k].h));
const TRACK_L = unwrap(A.ORDER.map(k => A.COLORS[k].lh));
const LAP_H = TRACK_H[7] - TRACK_H[0], LAP_L = TRACK_L[7] - TRACK_L[0];
let autoTimer = null, autoLap0 = null;
A.autoHue = (now = Date.now()) => {
  const st = A.st;
  const period = Math.max(5, +st.autoCycle || 30) * 1000;
  const k = Math.floor(now / period), f = (now % period) / period;
  const lap = Math.floor(k / 7), i = k % 7;
  if (autoLap0 == null) autoLap0 = lap;
  const L = lap - autoLap0;
  const at = j => ({ h: TRACK_H[j] + LAP_H * L, lh: TRACK_L[j] + LAP_L * L });
  const a = at(i), b = at(i + 1);
  if (st.autoMode !== 'smooth') return { h: a.h, lh: a.lh, key: A.ORDER[i], next: period - (now % period) };
  return { h: a.h + (b.h - a.h) * f, lh: a.lh + (b.lh - a.lh) * f, key: A.ORDER[i], next: 1000 };
};
A.currentHue = () => {
  const st = A.st;
  if (A.private) return { h: 266, lh: 258 };
  if (st.color === 'auto') return A.autoHue();
  const base = st.color === 'custom' ? { h: +st.hue || 214, lh: A.lcdFor(+st.hue || 214) } : (A.COLORS[st.color] || A.COLORS.blue);
  return { h: base.h, lh: st.lcdMatch === false ? +st.lcdHue : base.lh };
};
function setHue(c, fade) {
  const r = document.documentElement.style;
  r.setProperty('--hue-fade', fade);
  r.setProperty('--h', c.h.toFixed(2));
  r.setProperty('--lh', (A.st.lcdMatch === false && !A.private ? +A.st.lcdHue : c.lh).toFixed(2));
}
A.applyColour = () => {
  clearTimeout(autoTimer);
  const st = A.st;
  if (st.color !== 'auto' || A.private) { autoLap0 = null; setHue(A.currentHue(), '1.4s'); return; }
  const tick = () => {
    const c = A.autoHue();
    setHue(c, st.autoMode === 'smooth' ? '1s' : '2.6s');
    autoTimer = setTimeout(tick, Math.max(250, c.next + 30));
  };
  tick();
};
A.applyTheme = () => {
  const st = A.st, r = document.documentElement.style, b = document.body.classList;
  r.setProperty('--ga', (st.glass / 60).toFixed(3));
  r.setProperty('--blur', `${st.blur}px`);
  r.setProperty('--rs', (st.roundness / 100).toFixed(3));
  r.setProperty('--ui', (st.uiScale / 100).toFixed(3));
  r.setProperty('--sidew', `${st.sideWidth}px`);
  b.toggle('compact', st.density === 'compact');
  b.toggle('no-anim', !st.animations);
  b.toggle('addr-plain', st.addressFont === 'plain');
  b.toggle('close-always', st.tabClose === 'always');
  b.toggle('font-system', st.font === 'system');
  b.toggle('no-bookmarks', !st.showBookmarksBar);
  b.toggle('tabs-side', st.tabLayout === 'side');
  b.toggle('tabs-top', st.tabLayout !== 'side');
  b.toggle('private', !!A.private);
  A.$('#user-css').textContent = st.customCSS || '';
  A.applyColour();
};

/* ---------- floating UI ---------- */
const overlays = () => A.$('#overlays');
let openMenu = null;
A.closeMenus = () => { if (openMenu) { const m = openMenu; openMenu = null; m.close(); } };
function placeNear(el, anchor, opts = {}) {
  const r = anchor instanceof Element ? anchor.getBoundingClientRect() : { left: anchor.x, right: anchor.x, top: anchor.y, bottom: anchor.y, width: 0, height: 0 };
  const w = el.offsetWidth, hgt = el.offsetHeight, vw = innerWidth, vh = innerHeight;
  let x = opts.align === 'right' ? r.right - w : (opts.align === 'center' ? r.left + r.width / 2 - w / 2 : r.left);
  let y = opts.side === 'right' ? r.top : r.bottom + (opts.gap ?? 6);
  if (opts.side === 'right') x = r.right + 4;
  if (x + w > vw - 8) x = opts.side === 'right' ? r.left - w - 4 : vw - w - 8;
  if (x < 8) x = 8;
  if (y + hgt > vh - 8) y = Math.max(8, (opts.side === 'right' ? vh - hgt - 8 : r.top - hgt - 6));
  el.style.left = x + 'px'; el.style.top = y + 'px';
}
// items: [{ label, icon, kbd, click, disabled, checked, sub: [...] } | { sep: true } | { header: 'TEXT' } | { node }]
A.menu = (anchor, items, opts = {}) => {
  A.closeMenus();
  const layer = h('div.layer');
  const m = h('div.menu.glass.thick', { role: 'menu' });
  const close = () => { layer.remove(); m.remove(); if (opts.onClose) opts.onClose(); if (openMenu && openMenu.el === m) openMenu = null; };
  const build = list => {
    for (const it of list) {
      if (!it) continue;
      if (it.sep) { m.append(h('div.sep')); continue; }
      if (it.header) { m.append(h('div.mh.label', it.header)); continue; }
      if (it.node) { m.append(it.node); continue; }
      const b = h('button.mi', { role: 'menuitem', disabled: !!it.disabled, title: it.title || '' },
        it.mini || (it.icon ? A.icon(it.icon) : (it.checked != null ? (it.checked ? A.icon('check') : h('span', { style: { width: '1.125rem' } })) : null)),
        h('span', { style: { flex: '1', minWidth: '0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, it.label),
        it.kbd ? h('span.k', it.kbd) : null, it.sub ? A.icon('chevr') : null);
      b.addEventListener('click', e => {
        if (it.sub) { A.menu(b, it.sub, { side: 'right', keepParent: true }); return; }
        close(); if (it.click) it.click(e);
      });
      m.append(b);
    }
  };
  build(items);
  layer.addEventListener('mousedown', e => { if (e.target === layer) close(); });
  layer.addEventListener('contextmenu', e => { e.preventDefault(); close(); });
  overlays().append(layer, m);
  placeNear(m, anchor, opts);
  const btns = () => A.$$('.mi:not(:disabled)', m);
  m.tabIndex = -1; m.focus();
  m.addEventListener('keydown', e => {
    const list = btns(); const i = list.indexOf(document.activeElement);
    if (e.key === 'ArrowDown') { (list[i + 1] || list[0]).focus(); e.preventDefault(); }
    else if (e.key === 'ArrowUp') { (list[i - 1] || list[list.length - 1]).focus(); e.preventDefault(); }
    else if (e.key === 'Escape') { close(); e.preventDefault(); }
  });
  openMenu = { el: m, close };
  return { el: m, close };
};

// A popover card next to something. Not modal: the page stays usable unless opts.modal.
A.pop = (anchor, build, opts = {}) => {
  const el = h('div.pop.glass.thick');
  let layer = null;
  const close = () => { el.remove(); if (layer) layer.remove(); if (opts.onClose) opts.onClose(); };
  if (opts.modal !== false && opts.catcher !== false) {
    layer = h('div.layer');
    layer.addEventListener('mousedown', e => { if (e.target === layer) close(); });
    overlays().append(layer);
  }
  build(el, close);
  el.addEventListener('keydown', e => { if (e.key === 'Escape') { close(); e.stopPropagation(); } });
  overlays().append(el);
  placeNear(el, anchor, opts);
  const f = el.querySelector('[autofocus]'); if (f) setTimeout(() => f.focus(), 30);
  return { el, close, place: () => placeNear(el, anchor, opts) };
};

A.dialog = (build, opts = {}) => {
  const layer = h('div.layer.dim');
  const el = h('div.dialog.glass.thick', { role: 'dialog', 'aria-modal': 'true' });
  if (opts.wide) el.classList.add('wide');
  const close = v => { layer.remove(); el.remove(); if (opts.onClose) opts.onClose(v); };
  if (!opts.sticky) layer.addEventListener('mousedown', e => { if (e.target === layer) close(); });
  el.addEventListener('keydown', e => { if (e.key === 'Escape' && !opts.sticky) { close(); e.stopPropagation(); } });
  build(el, close);
  overlays().append(layer, el);
  const f = el.querySelector('[autofocus]') || el.querySelector('input, button.primary');
  if (f) setTimeout(() => f.focus(), 40);
  return { el, close };
};
A.confirm = (title, detail, ok = 'OK', danger = false) => new Promise(res => {
  A.dialog((el, close) => {
    el.append(h('h2', title), detail ? h('p', detail) : null,
      h('div.row', h('button.sbtn', { onclick: () => close(false) }, 'Cancel'), h(`button.sbtn.${danger ? 'danger' : 'primary'}`, { onclick: () => close(true) }, ok)));
  }, { onClose: v => res(!!v) });
});
A.prompt = (title, value = '', opts = {}) => new Promise(res => {
  A.dialog((el, close) => {
    const inp = h('input.input', { value, placeholder: opts.placeholder || '', autofocus: true, type: opts.type || 'text' });
    inp.addEventListener('keydown', e => { if (e.key === 'Enter') close(inp.value); });
    el.append(h('h2', title), opts.detail ? h('p', opts.detail) : null, inp,
      h('div.row', h('button.sbtn', { onclick: () => close(null) }, 'Cancel'), h('button.sbtn.primary', { onclick: () => close(inp.value) }, opts.ok || 'OK')));
  }, { onClose: v => res(v == null ? null : v) });
});
A.toast = text => {
  A.$$('.toast').forEach(t => t.remove());
  const t = h('div.toast.lcd', h('span', text));
  overlays().append(t);
  setTimeout(() => t.remove(), 2700);
};

// Form bits used by settings and widget editors.
A.toggle = (label, checked, onchange, small) => {
  const inp = h('input', { type: 'checkbox', checked: !!checked });
  inp.addEventListener('change', () => onchange(inp.checked));
  return h('label.toggle', inp, h('span.track'), h('span.tl', label, small ? h('small', small) : null));
};
A.chips = (options, value, onpick) => {
  const box = h('div.chips');
  const paint = v => A.$$('.chip-opt', box).forEach(b => b.setAttribute('aria-pressed', String(b.dataset.v === String(v))));
  for (const [v, label] of options) box.append(h('button.chip-opt', { dataset: { v: String(v) }, onclick: () => { paint(v); onpick(v); } }, label));
  paint(value);
  return box;
};
A.slider = (min, max, value, oninput, cls = '') => {
  const s = h('input.slider' + (cls ? '.' + cls : ''), { type: 'range', min, max, value });
  const paint = () => s.style.setProperty('--p', ((s.value - min) / (max - min) * 100) + '%');
  s.addEventListener('input', () => { paint(); oninput(+s.value); });
  paint();
  return s;
};
A.field = (label, control) => h('label.field-l', h('span', label), control);
