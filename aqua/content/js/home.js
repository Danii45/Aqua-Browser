'use strict';
/* The home page: a grid of widgets the person arranges, resizes and configures, on a wallpaper they choose.
   Each profile keeps its own layout. */
(() => {
  const { $, $$, h, icon, api } = A;
  const root = $('#home');
  const HM = A.home = { data: null, editing: false, rendered: false };
  const cleanups = new Map();
  const SIZES = ['s', 'm', 'l', 'xl', 'full'], SIZE_LABEL = { s: 'S', m: 'M', l: 'L', xl: 'XL', full: 'FULL' };
  const WIDTHS = { narrow: '58rem', normal: '76rem', wide: '96rem', full: 'none' };
  const nid = () => A.uid();
  const save = A.debounce(() => api.invoke('home:save', HM.data), 400);
  HM.save = save;

  /* ---------- defaults & presets ---------- */
  const TILES_HOME = [['Google', 'https://www.google.com'], ['YouTube', 'https://www.youtube.com'], ['Gmail', 'https://mail.google.com'], ['Spotify', 'https://open.spotify.com'], ['GitHub', 'https://github.com'], ['Reddit', 'https://www.reddit.com'], ['Wikipedia', 'https://en.wikipedia.org'], ['Netflix', 'https://www.netflix.com']];
  const TILES_WORK = [['Gmail', 'https://mail.google.com'], ['Calendar', 'https://calendar.google.com'], ['Drive', 'https://drive.google.com'], ['Docs', 'https://docs.google.com'], ['Sheets', 'https://sheets.google.com'], ['GitHub', 'https://github.com'], ['Wikipedia', 'https://en.wikipedia.org'], ['Translate', 'https://translate.google.com']];
  const tiles = list => list.map(([title, url]) => ({ id: nid(), title, url }));
  const W = (type, size, extra = {}) => Object.assign({ id: nid(), type, size, card: 'auto', cfg: JSON.parse(JSON.stringify(TYPES[type].cfg || {})) }, extra);
  function presetWidgets(name, keep) {
    const old = t => (keep || []).find(w => w.type === t);
    const reuse = (t, size, extra) => { const o = old(t); return o ? Object.assign({}, o, { size, id: o.id }, extra || {}) : W(t, size, extra); };
    if (name === 'minimal') return [reuse('greeting', 'full', { align: 'center', card: 'clear' }), reuse('search', 'full'), reuse('shortcuts', 'full', { align: 'center', card: 'clear' })];
    if (name === 'dashboard') return [reuse('clock', 'm', { card: 'glass' }), reuse('weather', 'm', { card: 'glass' }), reuse('calendar', 'm'), reuse('search', 'full'), reuse('shortcuts', 'xl'), reuse('todo', 'm'), reuse('notes', 'l'), reuse('topsites', 'l')];
    if (name === 'focus') return [reuse('clock', 'full', { align: 'center', card: 'clear' }), reuse('search', 'full'), reuse('timer', 'm'), reuse('todo', 'm'), reuse('notes', 'm')];
    return [reuse('clock', 'l', { card: 'clear' }), reuse('weather', 'l', { card: 'clear' }), reuse('search', 'full'), reuse('shortcuts', 'full'), reuse('todo', 'm'), reuse('notes', 'm'), reuse('calendar', 'm')];
  }
  const PRESETS = [['aqua', 'Aqua', [6, 6, 12, 12, 4, 4, 4]], ['minimal', 'Minimal', [12, 12, 12]], ['dashboard', 'Dashboard', [4, 4, 4, 12, 8, 4, 6, 6]], ['focus', 'Focus', [12, 12, 4, 4, 4]]];
  function defaults() {
    const work = /work|school|office/i.test(A.profile.name || '');
    const ws = presetWidgets('aqua');
    const sc = ws.find(w => w.type === 'shortcuts'); sc.cfg.tiles = tiles(work ? TILES_WORK : TILES_HOME);
    return { version: 1, layout: { width: 'normal', align: 'top', gap: 20, card: 'glass' }, wallpaper: { type: 'desk', color: '#0a1428', image: '', dim: 30, blur: 0 }, widgets: ws };
  }
  HM.load = data => {
    HM.data = data && data.version ? data : defaults();
    HM.data.layout = Object.assign({ width: 'normal', align: 'top', gap: 20, card: 'glass' }, HM.data.layout);
    HM.data.wallpaper = Object.assign({ type: 'desk', color: '#0a1428', image: '', dim: 30, blur: 0 }, HM.data.wallpaper);
    HM.data.widgets = (HM.data.widgets || []).filter(w => TYPES[w.type]).map(w => Object.assign({ card: 'auto', cfg: {} }, w, { cfg: Object.assign({}, TYPES[w.type].cfg, w.cfg) }));
    if (!data || !data.version) save();
  };

  /* ---------- widget helpers ---------- */
  const head = (label, tag) => h('div.w-head', h('span.w-label', label), tag ? h('span.w-tag', tag) : null);
  const go = (url, e) => { if (e && (e.ctrlKey || e.metaKey || e.button === 1)) A.tabs.open(url, { background: true, related: true }); else if (e && e.shiftKey) api.invoke('win:new', { urls: [url] }); else A.tabs.navigate(url); };

  function wmo(code, day) {
    day = day !== 0;
    if (code === 0) return { text: day ? 'Clear' : 'Clear night', kind: day ? 'sun' : 'moon' };
    if (code === 1) return { text: 'Mainly clear', kind: day ? 'sun' : 'moon' };
    if (code === 2) return { text: 'Partly cloudy', kind: day ? 'partly' : 'partly-night' };
    if (code === 3) return { text: 'Overcast', kind: 'cloud' };
    if (code === 45 || code === 48) return { text: 'Fog', kind: 'fog' };
    if (code >= 51 && code <= 57) return { text: 'Drizzle', kind: 'rain' };
    if (code >= 61 && code <= 67) return { text: code >= 66 ? 'Freezing rain' : 'Rain', kind: 'rain' };
    if (code >= 71 && code <= 77) return { text: 'Snow', kind: 'snow' };
    if (code >= 80 && code <= 82) return { text: 'Rain showers', kind: 'rain' };
    if (code === 85 || code === 86) return { text: 'Snow showers', kind: 'snow' };
    if (code >= 95) return { text: 'Thunderstorm', kind: 'storm' };
    return { text: 'Cloudy', kind: 'cloud' };
  }
  const MINI = { sun: 'sn', moon: 'mn', partly: 'pc', 'partly-night': 'mn', cloud: 'cl', rain: 'rn', snow: 'sw', storm: 'st', fog: 'fg' };
  const wxIcon = kind => h('div.wx-icon', { dataset: { kind } }, h('span.sun'), h('span.moon'), h('span.cloud.back'), h('span.cloud'),
    h('span.drops', h('i'), h('i'), h('i'), h('i')), h('span.flakes', h('i'), h('i'), h('i'), h('i')), h('span.bolt'), h('span.fogl', h('i'), h('i'), h('i')));
  const wxCache = new Map();
  async function fetchWeather(place, unit) {
    const key = `${place.lat},${place.lon},${unit}`;
    const c = wxCache.get(key) || (() => { try { return JSON.parse(localStorage.getItem('wx:' + key) || 'null'); } catch (e) { return null; } })();
    if (c && Date.now() - c.at < 15 * 60e3) return c.j;
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2${unit === 'f' ? '&temperature_unit=fahrenheit' : ''}`;
    try {
      const r = await fetch(url, { cache: 'no-store' });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      const j = await r.json();
      const v = { at: Date.now(), j };
      wxCache.set(key, v); try { localStorage.setItem('wx:' + key, JSON.stringify(v)); } catch (e) {}
      return j;
    } catch (e) { if (c) return c.j; throw e; }
  }
  async function searchCity(q) {
    const r = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=6&language=en&format=json`);
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const j = await r.json();
    return (j.results || []).map(x => ({ name: [x.name, x.admin1, x.country].filter(Boolean).join(', '), lat: x.latitude, lon: x.longitude }));
  }
  function cityPicker(onpick, initial = '') {
    const box = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '.45rem' } });
    const inp = h('input.input', { placeholder: 'Type a city, then press Enter', value: initial });
    const res = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '.3rem' } });
    const run = async () => {
      const q = inp.value.trim(); if (!q) return;
      res.replaceChildren(h('span.p-note', 'Searching…'));
      try {
        const list = await searchCity(q);
        res.replaceChildren(...(list.length ? list.map(p => h('button.sbtn.small', { style: { justifyContent: 'flex-start' }, onclick: () => onpick(p) }, p.name)) : [h('span.p-note', 'No places found with that name.')]));
      } catch (e) { res.replaceChildren(h('span.p-note', "Couldn't reach the weather service.")); }
    };
    inp.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); run(); } });
    box.append(h('div', { style: { display: 'flex', gap: '.4rem' } }, inp, h('button.sbtn', { onclick: run }, 'Find')), res);
    return box;
  }
  function tileFor(t, opts = {}) {
    const host = A.hostOf(t.url);
    const b = h('button.sc', { title: `${t.title}\n${A.prettyURL(t.url)}`, style: { '--sh': A.hashHue(host) } },
      h('span.tile', A.fav(t.favicon || '', t.url, { lookup: true })),
      h('span.tx', h('b', t.title || host), h('small', host)));
    const tileImg = b.querySelector('.tile .fav'); if (tileImg) tileImg.style.cssText = 'width:100%;height:100%;display:grid;place-items:center;background:none';
    b.addEventListener('click', e => go(t.url, e));
    b.addEventListener('auxclick', e => { if (e.button === 1) go(t.url, e); });
    return b;
  }

  /* ---------- the widget types ---------- */
  const TYPES = {
    clock: {
      name: 'Clock', icon: 'clock', desc: 'Big dot-matrix time and date', size: 'l', card: 'clear',
      cfg: { h24: false, seconds: true, date: true, style: 'dot' },
      fields: [['h24', 'toggle', '24-hour time'], ['seconds', 'toggle', 'Show seconds'], ['date', 'toggle', 'Show the date'], ['style', 'chips', 'Style', [['dot', 'Dot matrix'], ['slim', 'Slim']]]],
      render(w, b, el) {
        const c = w.cfg;
        el.classList.toggle('slim', c.style === 'slim');
        const date = h('div.wc-date'), hm = h('div.wc-hm'), ampm = h('div.wc-ampm'), secs = h('div.wc-secs');
        b.append(c.date ? date : null, h('div.wc-row', hm, (c.h24 && !c.seconds) ? null : h('div.wc-side', c.h24 ? null : ampm, c.seconds ? secs : null)));
        const tick = () => {
          const d = new Date(), H = d.getHours(), M = d.getMinutes(), S = d.getSeconds();
          const hh = c.h24 ? String(H).padStart(2, '0') : String(H % 12 || 12);
          hm.innerHTML = `${hh}<span class="colon ${S % 2 ? 'off' : ''}">:</span>${String(M).padStart(2, '0')}`;
          ampm.textContent = H >= 12 ? 'PM' : 'AM';
          secs.textContent = String(S).padStart(2, '0');
          date.textContent = `${d.toLocaleDateString([], { weekday: 'short' })} · ${d.toLocaleDateString([], { month: 'long', day: 'numeric' })}`.toUpperCase();
        };
        tick();
        const iv = setInterval(tick, 1000);
        return () => clearInterval(iv);
      }
    },
    weather: {
      name: 'Weather', icon: 'cloud', desc: 'Now and the next few hours', size: 'l', card: 'clear',
      cfg: { place: null, unit: 'c', hourly: true },
      fields: [['place', 'city', 'Place'], ['unit', 'chips', 'Units', [['c', '°C'], ['f', '°F']]], ['hourly', 'toggle', 'Show the next hours']],
      render(w, b, el) {
        const c = w.cfg;
        if (!c.place) {
          b.append(head('Weather'), h('p.w-msg', 'Pick your city to see the weather here.'), cityPicker(p => { c.place = p; save(); rerender(w); }));
          return;
        }
        const z = { s: .45, m: .52, l: .68, xl: .74, full: .8 }[w.size] || .6;
        b.style.setProperty('--wxz', z);
        b.append(h('p.w-msg', 'Loading the weather…'));
        let alive = true, timer;
        const load = async () => {
          try {
            const j = await fetchWeather(c.place, c.unit);
            if (!alive) return;
            const cur = j.current, info = wmo(cur.weather_code, cur.is_day);
            const deg = Math.round(cur.temperature_2m);
            const hi = Math.round(j.daily.temperature_2m_max[0]), lo = Math.round(j.daily.temperature_2m_min[0]);
            const nowIso = cur.time.slice(0, 13);
            let i0 = j.hourly.time.findIndex(t => t.slice(0, 13) === nowIso); if (i0 < 0) i0 = 0;
            const n = w.size === 's' ? 3 : (w.size === 'm' ? 4 : 5);
            const hours = [];
            for (let k = 1; k <= n; k++) {
              const i = i0 + k * 2; if (i >= j.hourly.time.length) break;
              const hr = +j.hourly.time[i].slice(11, 13);
              const hw = wmo(j.hourly.weather_code[i], j.hourly.is_day[i]);
              hours.push(h('div.wx-hour', h('span.t', `${hr % 12 || 12} ${hr >= 12 ? 'PM' : 'AM'}`), h('span', { class: 'mi ' + MINI[hw.kind] }), h('span.d', Math.round(j.hourly.temperature_2m[i]) + '°')));
            }
            b.replaceChildren(
              h('div.wx-now', wxIcon(info.kind), h('div.wx-temp', deg + '°'), h('div.wx-meta', h('div.wx-cond', info.text), h('div.wx-hl', `High ${hi}° · Low ${lo}°`), h('div.wx-loc', String(c.place.name).split(',')[0]))),
              c.hourly && hours.length ? h('div.wx-hours', { style: { '--n': hours.length } }, hours) : null);
          } catch (e) {
            if (alive) b.replaceChildren(head('Weather', String(c.place.name).split(',')[0]), h('p.w-msg', "Couldn't reach the weather service. It will try again soon."));
          }
          timer = setTimeout(load, 15 * 60e3);
        };
        load();
        return () => { alive = false; clearTimeout(timer); };
      }
    },
    search: {
      name: 'Search', icon: 'search', desc: 'A big LCD search box', size: 'full',
      cfg: { engine: '', placeholder: '' },
      fields: [['engine', 'select', 'Search with', () => [['', 'Same as the browser']].concat(Object.entries(A.engines).map(([k, e]) => [k, e.name]))], ['placeholder', 'text', 'Placeholder text', 'Search the web']],
      render(w, b, el) {
        el.classList.remove('card', 'glass', 'solid'); el.classList.add('clear');
        const eng = w.cfg.engine || A.st.search;
        const name = eng === 'custom' ? 'WEB' : ((A.engines[eng] || { name: 'Web' }).name).toUpperCase();
        const inp = h('input', { type: 'text', placeholder: w.cfg.placeholder || 'Search the web', spellcheck: false, 'aria-label': 'Search' });
        const list = h('div.ws-sugg');
        let items = [], sel = -1, seqn = 0;
        const submit = q => {
          q = (q || inp.value).trim(); if (!q) return;
          const url = A.toURL(q, { noFile: true });
          const looksLikeUrl = url && !url.startsWith(A.searchURL('').split('?')[0]);
          go(looksLikeUrl ? url : A.searchURL(q, eng));
        };
        const paint = () => list.replaceChildren(...items.map((s, i) => h('button' + (i === sel ? '.sel' : ''), { onmousedown: e => { e.preventDefault(); submit(s); } }, icon('search'), s)));
        inp.addEventListener('input', async () => {
          const q = inp.value.trim(), my = ++seqn;
          if (!q || A.private || !A.st.suggestions) { items = []; paint(); return; }
          const r = await api.invoke('suggest:remote', q);
          if (my !== seqn) return;
          items = r.slice(0, 5); sel = -1; paint();
        });
        inp.addEventListener('keydown', e => {
          if (e.key === 'Enter') { e.preventDefault(); submit(sel >= 0 ? items[sel] : null); }
          else if (e.key === 'ArrowDown' && items.length) { sel = (sel + 1) % items.length; paint(); e.preventDefault(); }
          else if (e.key === 'ArrowUp' && items.length) { sel = (sel - 1 + items.length) % items.length; paint(); e.preventDefault(); }
          else if (e.key === 'Escape') { items = []; paint(); }
        });
        inp.addEventListener('blur', () => setTimeout(() => { items = []; paint(); }, 150));
        b.append(h('div.ws-box.lcd', h('span.ind.on', name), inp, h('button.gbtn', { title: 'Search', onclick: () => submit() }, icon('search'))), list);
        el._focus = () => { inp.focus(); return true; };
      }
    },
    shortcuts: {
      name: 'Shortcuts', icon: 'link', desc: 'Your favourite sites as glass tiles', size: 'full',
      cfg: { tiles: [], style: 'chips', label: '' },
      fields: [['style', 'chips', 'Look', [['chips', 'Chips'], ['tiles', 'Tiles'], ['icons', 'Icons only']]], ['label', 'text', 'Heading (optional)', 'Shortcuts']],
      render(w, b, el) {
        const c = w.cfg;
        if (c.label) b.append(head(c.label));
        const grid = h('div.sc-grid' + (c.style !== 'chips' ? '.' + c.style : ''));
        let dragId = null;
        c.tiles.forEach(t => {
          const tile = tileFor(t);
          tile.draggable = true;
          tile.append(h('span.ed', { title: 'Edit', onclick: e => { e.stopPropagation(); editTile(w, t); } }, icon('edit')));
          tile.addEventListener('contextmenu', e => { e.preventDefault(); e.stopPropagation(); A.menu({ x: e.clientX, y: e.clientY }, [
            { label: 'Open in new tab', icon: 'plus', click: () => A.tabs.open(t.url, { background: true }) },
            { label: 'Open in private window', icon: 'mask', click: () => api.invoke('win:new', { urls: [t.url], private: true }) },
            { sep: true }, { label: 'Edit…', icon: 'edit', click: () => editTile(w, t) },
            { label: 'Remove', icon: 'trash', click: () => { c.tiles = c.tiles.filter(x => x !== t); save(); rerender(w); } }]); });
          tile.addEventListener('dragstart', e => { dragId = t.id; e.dataTransfer.setData('text/x-aqua-tile', t.id); e.dataTransfer.setData('text/uri-list', t.url); tile.classList.add('dragging'); e.stopPropagation(); });
          tile.addEventListener('dragend', () => tile.classList.remove('dragging'));
          tile.addEventListener('dragover', e => { if (e.dataTransfer.types.includes('text/x-aqua-tile')) { e.preventDefault(); e.stopPropagation(); tile.classList.add('drop-before'); } });
          tile.addEventListener('dragleave', () => tile.classList.remove('drop-before'));
          tile.addEventListener('drop', e => {
            tile.classList.remove('drop-before');
            const id = e.dataTransfer.getData('text/x-aqua-tile'); if (!id || id === t.id) return;
            e.preventDefault(); e.stopPropagation();
            const src = c.tiles.find(x => x.id === id); if (!src) return;
            c.tiles.splice(c.tiles.indexOf(src), 1); c.tiles.splice(c.tiles.indexOf(t), 0, src);
            save(); rerender(w);
          });
          grid.append(tile);
        });
        grid.append(h('button.sc.add', { title: 'Add a shortcut', onclick: () => editTile(w, null) }, h('span.tile', icon('plus')), h('span.tx', h('b', 'Add'), h('small', 'a shortcut'))));
        // Drop a link from a page or the bookmarks bar to add it.
        grid.addEventListener('dragover', e => { if (e.dataTransfer.types.includes('text/uri-list') && !e.dataTransfer.types.includes('text/x-aqua-tile')) e.preventDefault(); });
        grid.addEventListener('drop', e => {
          if (e.dataTransfer.types.includes('text/x-aqua-tile')) return;
          const u = (e.dataTransfer.getData('text/uri-list') || '').split('\n')[0].trim();
          if (!/^https?:/.test(u)) return;
          e.preventDefault();
          c.tiles.push({ id: nid(), title: A.hostOf(u).split('.')[0].replace(/^./, s => s.toUpperCase()), url: u }); save(); rerender(w);
        });
        b.append(grid);
      }
    },
    greeting: {
      name: 'Greeting', icon: 'hand', desc: 'Good morning, with your name', size: 'full', card: 'clear',
      cfg: { name: '', text: '' },
      fields: [['name', 'text', 'Your name', 'Optional'], ['text', 'text', 'Message under it', 'e.g. Ready for today?']],
      render(w, b) {
        const hi = () => { const hr = new Date().getHours(); return hr < 5 ? 'Good night' : hr < 12 ? 'Good morning' : hr < 18 ? 'Good afternoon' : 'Good evening'; };
        const line = h('div.wg-hi'), sub = h('div.wg-sub');
        const paint = () => {
          line.replaceChildren(hi(), w.cfg.name ? ', ' : '', w.cfg.name ? h('em', w.cfg.name) : '');
          sub.textContent = w.cfg.text || `${new Date().toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })} · ${A.private ? 'Private window' : A.profile.name + ' profile'}`;
        };
        paint(); b.append(line, sub);
        const iv = setInterval(paint, 60e3); return () => clearInterval(iv);
      }
    },
    notes: {
      name: 'Notes', icon: 'note', desc: 'A note that saves as you type', size: 'm',
      cfg: { title: 'Notes', text: '' },
      fields: [['title', 'text', 'Title', 'Notes']],
      render(w, b) {
        const ta = h('textarea.wn-text', { placeholder: 'Write anything…', spellcheck: true }, w.cfg.text);
        ta.value = w.cfg.text || '';
        ta.addEventListener('input', () => { w.cfg.text = ta.value; save(); });
        b.append(head(w.cfg.title || 'Notes'), ta);
      }
    },
    todo: {
      name: 'To-do', icon: 'todo', desc: 'A checklist', size: 'm',
      cfg: { title: 'To-do', items: [] },
      fields: [['title', 'text', 'Title', 'To-do']],
      render(w, b) {
        const c = w.cfg;
        const listEl = h('div.wt-list');
        const inp = h('input.input', { placeholder: 'Add a task and press Enter' });
        const paint = () => {
          listEl.replaceChildren(...(c.items.length ? c.items.map(it => {
            const row = h('div.wt-item' + (it.done ? '.done' : ''),
              h('button.ck', { title: it.done ? 'Mark not done' : 'Mark done', onclick: () => { it.done = !it.done; save(); paint(); } }, icon('check')),
              h('span.tx', it.text),
              h('button.del', { title: 'Delete', onclick: () => { c.items = c.items.filter(x => x !== it); save(); paint(); } }, icon('x')));
            return row;
          }) : [h('p.w-msg', { style: { margin: '.2rem 0', fontSize: '.9375rem' } }, 'Nothing to do. Nice.')]));
          tag.textContent = c.items.length ? `${c.items.filter(x => !x.done).length} LEFT` : '';
          tag.hidden = !c.items.length;
        };
        const tag = h('span.w-tag');
        inp.addEventListener('keydown', e => { if (e.key === 'Enter' && inp.value.trim()) { c.items.push({ id: nid(), text: inp.value.trim(), done: false }); inp.value = ''; save(); paint(); } });
        b.append(h('div.w-head', h('span.w-label', c.title || 'To-do'), tag), h('div.wt-add', inp), listEl);
        paint();
      }
    },
    calendar: {
      name: 'Calendar', icon: 'calendar', desc: 'This month at a glance', size: 'm',
      cfg: { monday: false },
      fields: [['monday', 'toggle', 'Weeks start on Monday']],
      render(w, b) {
        let off = 0;
        const paint = () => {
          const now = new Date(), d = new Date(now.getFullYear(), now.getMonth() + off, 1);
          const start = (d.getDay() - (w.cfg.monday ? 1 : 0) + 7) % 7;
          const days = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
          const names = w.cfg.monday ? ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'] : ['SU', 'MO', 'TU', 'WE', 'TH', 'FR', 'SA'];
          const cells = names.map(n => h('span.dn', n));
          const prevDays = new Date(d.getFullYear(), d.getMonth(), 0).getDate();
          for (let i = start - 1; i >= 0; i--) cells.push(h('span.d.out', prevDays - i));
          for (let i = 1; i <= days; i++) {
            const wd = new Date(d.getFullYear(), d.getMonth(), i).getDay();
            const today = off === 0 && i === now.getDate();
            cells.push(h('span.d' + (today ? '.today' : '') + (wd === 0 || wd === 6 ? '.we' : ''), i));
          }
          while ((cells.length - 7) % 7) cells.push(h('span.d.out', (cells.length - 7 - start - days) + 1));
          b.replaceChildren(h('div.wcal-h', h('button.gbtn', { title: 'Previous month', onclick: () => { off--; paint(); } }, icon('chevl')),
            h('b', d.toLocaleDateString([], { month: 'long', year: 'numeric' })), h('button.gbtn', { title: 'Next month', onclick: () => { off++; paint(); } }, icon('chevr'))),
          h('div.wcal-g', cells));
        };
        paint();
        const iv = setInterval(() => { if (off === 0) paint(); }, 10 * 60e3); return () => clearInterval(iv);
      }
    },
    topsites: {
      name: 'Most visited', icon: 'history', desc: 'Sites you go to the most', size: 'full',
      cfg: { count: 8, style: 'chips' },
      fields: [['count', 'chips', 'How many', [[4, '4'], [6, '6'], [8, '8'], [12, '12']]], ['style', 'chips', 'Look', [['chips', 'Chips'], ['tiles', 'Tiles'], ['icons', 'Icons only']]]],
      render(w, b) {
        b.append(head('Most visited'));
        const grid = h('div.sc-grid' + (w.cfg.style !== 'chips' ? '.' + w.cfg.style : ''));
        b.append(grid);
        if (A.private) { grid.append(h('p.w-msg', 'Private windows keep no history.')); return; }
        api.invoke('history:top', +w.cfg.count || 8).then(listv => {
          if (!listv.length) { grid.append(h('p.w-msg', 'Sites you visit often show up here.')); return; }
          for (const x of listv) grid.append(tileFor({ title: x.title || A.hostOf(x.url), url: x.url, favicon: x.favicon }));
        });
      }
    },
    bookmarks: {
      name: 'Bookmarks', icon: 'bookmark', desc: 'A bookmark folder as chips', size: 'full',
      cfg: { folder: 'bar', style: 'chips' },
      fields: [['folder', 'select', 'Folder', () => A.bm.folders().map(f => [f.id, '  '.repeat(f.depth) + f.title])], ['style', 'chips', 'Look', [['chips', 'Chips'], ['tiles', 'Tiles'], ['icons', 'Icons only']]]],
      render(w, b) {
        const f = A.bm.folderById(w.cfg.folder) || A.bm.folderById('bar');
        const name = (A.bm.folders().find(x => x.id === w.cfg.folder) || { title: 'Bookmarks' }).title;
        b.append(head(name));
        const links = (f.children || []).filter(n => n.url);
        const grid = h('div.sc-grid' + (w.cfg.style !== 'chips' ? '.' + w.cfg.style : ''));
        if (!links.length) grid.append(h('p.w-msg', 'No bookmarks in this folder yet.'));
        for (const n of links) grid.append(tileFor({ title: n.title || A.hostOf(n.url), url: n.url, favicon: n.favicon }));
        b.append(grid);
      }
    },
    countdown: {
      name: 'Countdown', icon: 'calendar', desc: 'Days until something', size: 's',
      cfg: { title: 'Summer break', date: '' },
      fields: [['title', 'text', 'What is it?', 'Summer break'], ['date', 'date', 'Date']],
      render(w, b) {
        const c = w.cfg;
        if (!c.date) { b.append(head('Countdown'), h('p.w-msg', 'Open this widget’s settings (the gear while customizing) to pick a date.')); return; }
        const paint = () => {
          const t0 = new Date(); t0.setHours(0, 0, 0, 0);
          const [y, m, d] = c.date.split('-').map(Number);
          const days = Math.round((new Date(y, m - 1, d) - t0) / 86400e3);
          b.replaceChildren(head('Countdown', new Date(y, m - 1, d).toLocaleDateString([], { month: 'short', day: 'numeric' }).toUpperCase()),
            h('div.wcd-n', days === 0 ? 'TODAY' : Math.abs(days)), days === 0 ? null : h('div.wcd-u', days > 0 ? (days === 1 ? 'Day to go' : 'Days to go') : (days === -1 ? 'Day ago' : 'Days ago')), h('div.wcd-t', c.title));
        };
        paint(); const iv = setInterval(paint, 30 * 60e3); return () => clearInterval(iv);
      }
    },
    timer: {
      name: 'Focus timer', icon: 'timer', desc: 'Work in focused blocks', size: 'm',
      cfg: { minutes: 25, rest: 5 },
      fields: [['minutes', 'chips', 'Focus for', [[15, '15 min'], [25, '25 min'], [45, '45 min'], [60, '60 min']]], ['rest', 'chips', 'Break', [[5, '5 min'], [10, '10 min'], [15, '15 min']]]],
      render(w, b) {
        const S = HM.timers[w.id] || (HM.timers[w.id] = { phase: 'focus', left: w.cfg.minutes * 60, running: false, end: 0 });
        const t = h('div.wft-t'), segs = h('div.wft-segs'), btns = h('div.wft-btns'), tag = h('span.w-tag');
        for (let i = 0; i < 20; i++) segs.append(h('i'));
        const total = () => (S.phase === 'focus' ? w.cfg.minutes : w.cfg.rest) * 60;
        const left = () => S.running ? Math.max(0, Math.round((S.end - Date.now()) / 1000)) : S.left;
        const paint = () => {
          const l = left();
          t.textContent = `${String(Math.floor(l / 60)).padStart(2, '0')}:${String(l % 60).padStart(2, '0')}`;
          const on = Math.round((1 - l / total()) * 20);
          [...segs.children].forEach((s, i) => s.classList.toggle('on', i < on));
          tag.textContent = S.phase === 'focus' ? 'FOCUS' : 'BREAK';
          btns.replaceChildren(
            h('button.sbtn.primary', { onclick: () => { if (S.running) { S.left = left(); S.running = false; } else { S.end = Date.now() + left() * 1000; S.running = true; } paint(); } }, S.running ? 'Pause' : 'Start'),
            h('button.sbtn', { onclick: () => { S.running = false; S.left = total(); paint(); } }, 'Reset'),
            h('button.sbtn.ghost', { onclick: () => { S.phase = S.phase === 'focus' ? 'rest' : 'focus'; S.running = false; S.left = total(); paint(); } }, S.phase === 'focus' ? 'Skip to break' : 'Back to focus'));
          if (S.running && l <= 0) {
            S.running = false; beep();
            A.toast(S.phase === 'focus' ? 'Focus block done — take a break' : 'Break over');
            S.phase = S.phase === 'focus' ? 'rest' : 'focus'; S.left = total(); paint();
          }
        };
        b.append(h('div.w-head', h('span.w-label', 'Focus timer'), tag), t, segs, btns);
        paint();
        const iv = setInterval(() => { if (S.running) paint(); }, 500); return () => clearInterval(iv);
      }
    },
    rss: {
      name: 'News feed', icon: 'rss', desc: 'Headlines from any RSS or Atom feed', size: 'l',
      cfg: { url: '', count: 6, title: '' },
      fields: [['url', 'text', 'Feed address', 'https://example.com/feed.xml'], ['title', 'text', 'Title (optional)', 'News'], ['count', 'chips', 'Headlines', [[4, '4'], [6, '6'], [10, '10']]]],
      render(w, b) {
        const c = w.cfg;
        if (!c.url) { b.append(head('News feed'), h('p.w-msg', 'Add a feed address in this widget’s settings. Most news sites and blogs have one (look for RSS).')); return; }
        b.append(head(c.title || 'News feed', A.hostOf(c.url).toUpperCase()), h('p.w-msg', 'Loading…'));
        let alive = true;
        const load = async () => {
          try {
            const cached = HM.feeds[c.url];
            const text = cached && Date.now() - cached.at < 30 * 60e3 ? cached.text : await api.invoke('feed:fetch', c.url);
            HM.feeds[c.url] = { at: Date.now(), text };
            const doc = new DOMParser().parseFromString(text, 'text/xml');
            const items = [...doc.querySelectorAll('item, entry')].slice(0, +c.count || 6).map(it => {
              const title = (it.querySelector('title') || {}).textContent || '';
              const linkEl = it.querySelector('link');
              const link = linkEl ? (linkEl.getAttribute('href') || linkEl.textContent) : '';
              const date = (it.querySelector('pubDate, updated, published') || {}).textContent || '';
              return { title: title.trim(), link: link.trim(), date };
            }).filter(x => x.title && /^https?:/.test(x.link));
            if (!alive) return;
            const feedTitle = (doc.querySelector('channel > title, feed > title') || {}).textContent;
            b.replaceChildren(head(c.title || feedTitle || 'News feed', A.hostOf(c.url).toUpperCase()), h('div.wf-list', items.length ? items.map(x => {
              const a = h('div.wf-item', { role: 'link', tabIndex: 0 }, h('b', x.title), x.date ? h('small', new Date(x.date).toString() !== 'Invalid Date' ? A.dayLabel(new Date(x.date).getTime()) + ' · ' + A.fmtTime(new Date(x.date).getTime()) : '') : null);
              a.addEventListener('click', e => go(x.link, e));
              a.addEventListener('auxclick', e => { if (e.button === 1) go(x.link, e); });
              return a;
            }) : [h('p.w-msg', 'That feed has no headlines.')]));
          } catch (e) { if (alive) b.replaceChildren(head(c.title || 'News feed'), h('p.w-msg', `Couldn't load that feed (${e.message.replace(/^Error invoking remote method '[^']+': (Error: )?/, '')}).`)); }
        };
        load();
        const iv = setInterval(load, 30 * 60e3);
        return () => { alive = false; clearInterval(iv); };
      }
    },
    photo: {
      name: 'Picture', icon: 'image', desc: 'A photo you like', size: 'm',
      cfg: { image: '', caption: '', ratio: '16 / 10' },
      fields: [['image', 'image', 'Picture'], ['caption', 'text', 'Caption', 'Optional'], ['ratio', 'chips', 'Shape', [['16 / 10', 'Wide'], ['1 / 1', 'Square'], ['3 / 4', 'Tall']]]],
      render(w, b) {
        const c = w.cfg;
        if (!c.image) { b.append(head('Picture'), h('button.sbtn', { onclick: async () => { const u = await api.invoke('pick-image'); if (u) { c.image = u; save(); rerender(w); } } }, icon('image'), 'Choose a picture')); return; }
        b.append(h('div.wp-img', { style: { backgroundImage: `url("${c.image}")`, '--ar': c.ratio } }), c.caption ? h('div.wp-cap', c.caption) : null);
      }
    },
    spacer: { name: 'Empty space', icon: 'spacer', desc: 'A gap to shape your layout', size: 's', card: 'clear', cfg: {}, render(w, b) { b.style.minHeight = '4rem'; } }
  };
  HM.TYPES = TYPES;
  HM.timers = {}; HM.feeds = {};
  function beep() {
    try {
      const ac = new AudioContext(), o = ac.createOscillator(), g = ac.createGain();
      o.type = 'triangle'; o.frequency.value = 880; o.connect(g); g.connect(ac.destination);
      g.gain.setValueAtTime(.0001, ac.currentTime); g.gain.exponentialRampToValueAtTime(.25, ac.currentTime + .02); g.gain.exponentialRampToValueAtTime(.0001, ac.currentTime + .9);
      o.start(); o.stop(ac.currentTime + 1);
    } catch (e) {}
  }

  /* ---------- rendering ---------- */
  function cardOf(w) { const T = TYPES[w.type]; return w.card && w.card !== 'auto' ? w.card : (T.card || HM.data.layout.card); }
  function widgetEl(w) {
    const T = TYPES[w.type];
    const el = h('div.w', { dataset: { size: w.size || T.size, id: w.id, type: w.type } });
    const card = cardOf(w);
    if (card === 'glass') el.classList.add('card', 'glass'); else if (card === 'solid') el.classList.add('solid'); else el.classList.add('clear');
    if (w.align === 'center') el.classList.add('centered');
    const body = h('div.w-body');
    el.append(body);
    try { const c = T.render(w, body, el); if (typeof c === 'function') cleanups.set(w.id, c); }
    catch (e) { console.error(e); body.replaceChildren(h('p.w-msg', 'This widget hit a problem.')); }
    // edit controls
    const sz = h('button.gbtn.sz', { title: 'Size', onclick: e => { e.stopPropagation(); A.menu(e.currentTarget, SIZES.map(s => ({ label: { s: 'Small', m: 'Medium', l: 'Large', xl: 'Extra large', full: 'Full width' }[s], checked: (w.size || T.size) === s, click: () => { w.size = s; save(); rerender(w); } }))); } }, SIZE_LABEL[w.size || T.size]);
    const tools = h('div.w-edit.glass.thick',
      h('button.gbtn.grip', { title: 'Drag to move' }, icon('grip')), sz,
      h('button.gbtn', { title: 'Settings', onclick: () => widgetSettings(w) }, icon('gear')),
      h('button.gbtn', { title: 'Remove', onclick: () => { HM.data.widgets = HM.data.widgets.filter(x => x !== w); save(); HM.render(); } }, icon('trash')));
    el.append(tools);
    el.draggable = HM.editing;
    el.addEventListener('dragstart', e => { if (!HM.editing || e.target !== el) return; e.dataTransfer.setData('text/x-aqua-widget', w.id); e.dataTransfer.effectAllowed = 'move'; el.classList.add('dragging'); });
    el.addEventListener('dragend', () => { el.classList.remove('dragging'); $$('.w.drop-target', root).forEach(x => x.classList.remove('drop-target')); });
    el.addEventListener('dragover', e => { if (HM.editing && e.dataTransfer.types.includes('text/x-aqua-widget')) { e.preventDefault(); el.classList.add('drop-target'); } });
    el.addEventListener('dragleave', () => el.classList.remove('drop-target'));
    el.addEventListener('drop', e => {
      el.classList.remove('drop-target');
      const id = e.dataTransfer.getData('text/x-aqua-widget'); if (!id || id === w.id) return;
      e.preventDefault();
      const L = HM.data.widgets, src = L.find(x => x.id === id); if (!src) return;
      const from = L.indexOf(src), to = L.indexOf(w);
      L.splice(from, 1); L.splice(L.indexOf(w) + (from < to ? 1 : 0), 0, src);
      save(); HM.render();
    });
    el.addEventListener('dblclick', e => { if (HM.editing && !e.target.closest('input, textarea, button')) widgetSettings(w); });
    return el;
  }
  function rerender(w) {
    const old = root.querySelector(`.w[data-id="${w.id}"]`);
    if (!old) return HM.render();
    if (cleanups.has(w.id)) { cleanups.get(w.id)(); cleanups.delete(w.id); }
    old.replaceWith(widgetEl(w));
  }
  HM.rerender = rerender;
  HM.refreshKind = type => { if (!HM.rendered) return; for (const w of HM.data.widgets) if (w.type === type) rerender(w); };

  HM.render = () => {
    for (const c of cleanups.values()) c();
    cleanups.clear();
    const d = HM.data;
    root.className = `align-${d.layout.align}` + (HM.editing ? ' editing' : '');
    const wp = d.wallpaper;
    const wall = h('div.wall.' + (wp.type || 'desk'), h('div.shade'));
    if (wp.type === 'image' && wp.image) wall.style.setProperty('--wall-image', `url("${wp.image}")`);
    wall.style.setProperty('--wall-color', wp.color || '#0a1428');
    wall.style.setProperty('--wall-dim', (wp.type === 'image' ? (wp.dim || 0) : 0) / 100);
    wall.style.setProperty('--wall-blur', `${wp.type === 'image' ? (wp.blur || 0) : 0}px`);
    const grid = h('div.grid');
    for (const w of d.widgets) grid.append(widgetEl(w));
    if (!d.widgets.length) grid.append(h('div.w.card.glass', { dataset: { size: 'full' } }, h('div.w-body', h('p.w-msg', 'Your home page is empty. Press Customize to add widgets.'))));
    const inner = h('div.inner', { style: { '--home-max': WIDTHS[d.layout.width] || WIDTHS.normal, '--home-gap': `${d.layout.gap}px` } }, grid);
    const bar = h('div.hbar', HM.editing
      ? h('button.gbtn.lit', { onclick: () => HM.edit(false) }, icon('check'), 'Done')
      : h('button.gbtn', { title: 'Customize your home page', onclick: () => HM.edit(true) }, icon('edit'), 'Customize'));
    root.replaceChildren(wall, inner, bar);
    if (HM.editing) root.append(editSheet());
    HM.rendered = true;
  };
  HM.show = () => { if (!HM.rendered) HM.render(); };
  HM.hide = () => { if (HM.editing) HM.edit(false); };
  HM.focusSearch = () => {
    if (!HM.rendered) HM.render();
    const s = root.querySelector('.w[data-type="search"]');
    return s && s._focus ? s._focus() : false;
  };
  HM.edit = on => { HM.editing = !!on; HM.render(); };
  HM.set = data => { HM.load(data); if (HM.rendered && !HM.editing) HM.render(); };

  /* ---------- edit sheet ---------- */
  let sheetTab = 'add';
  function editSheet() {
    const d = HM.data;
    const sheet = h('div.edit-sheet.glass.thick');
    const body = h('div');
    const tabsEl = A.chips([['add', 'Add widgets'], ['layout', 'Layout'], ['wall', 'Wallpaper']], sheetTab, v => { sheetTab = v; paint(); });
    sheet.append(h('div.es-head', h('h2', 'Customize home'), tabsEl, h('button.sbtn.primary.small', { onclick: () => HM.edit(false) }, 'Done')), body);
    const paint = () => {
      body.replaceChildren();
      if (sheetTab === 'add') {
        body.append(h('div.gallery', ...Object.entries(TYPES).map(([k, T]) => h('button', { title: `Add ${T.name}`, onclick: () => { d.widgets.push(W(k, T.size)); save(); HM.render(); setTimeout(() => { const el = root.querySelector('.w:last-of-type'); if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, 50); } },
          icon(T.icon), h('span', h('b', T.name), h('small', T.desc))))));
      } else if (sheetTab === 'layout') {
        body.append(h('div.opts',
          A.field('Width', A.chips([['narrow', 'Narrow'], ['normal', 'Normal'], ['wide', 'Wide'], ['full', 'Full']], d.layout.width, v => { d.layout.width = v; save(); HM.render(); })),
          A.field('Line up', A.chips([['top', 'At the top'], ['center', 'In the middle']], d.layout.align, v => { d.layout.align = v; save(); HM.render(); })),
          A.field('Widget style', A.chips([['glass', 'Glass'], ['solid', 'Solid'], ['clear', 'No card']], d.layout.card, v => { d.layout.card = v; save(); HM.render(); })),
          A.field('Spacing', A.slider(6, 44, d.layout.gap, A.debounce(v => { d.layout.gap = v; save(); HM.render(); }, 120)))),
        h('div.label', { style: { marginTop: '1rem', marginBottom: '.5rem' } }, 'Start from a layout (keeps your shortcuts, notes and to-dos)'),
        h('div.presets', ...PRESETS.map(([k, name, spans]) => h('button.preset', { onclick: async () => {
          if (!await A.confirm(`Use the ${name} layout?`, 'Your widgets get rearranged. Your shortcuts, notes and to-dos stay.', 'Use layout')) return;
          d.widgets = presetWidgets(k, d.widgets); save(); HM.render();
        } }, h('span.pv', ...spans.map(s => h('i', { style: { gridColumn: `span ${s}` } }))), h('b', name)))),
        h('div', { style: { marginTop: '.9rem' } }, h('button.sbtn.ghost.small', { onclick: async () => { if (await A.confirm('Reset the home page?', 'This puts back the default widgets and wallpaper. Your notes and shortcuts are cleared.', 'Reset', true)) { HM.data = defaults(); save(); HM.render(); } } }, 'Reset to default')));
      } else {
        const wp = d.wallpaper;
        const types = [['desk', 'Aqua desk'], ['aurora', 'Aurora'], ['grid', 'Grid'], ['dots', 'Dot matrix'], ['solid', 'Solid colour'], ['image', 'Your picture']];
        body.append(A.field('Wallpaper', A.chips(types, wp.type, v => { wp.type = v; save(); HM.render(); })));
        if (wp.type === 'solid') body.append(h('div', { style: { marginTop: '.8rem' } }, A.field('Colour', h('input', { type: 'color', value: wp.color || '#0a1428', style: { width: '4rem', height: '2.2rem', border: '0', background: 'none' }, oninput: A.debounce(e => { wp.color = e.target.value; save(); HM.render(); }, 100) }))));
        if (wp.type === 'image') body.append(h('div.opts', { style: { marginTop: '.8rem' } },
          h('div', h('button.sbtn', { onclick: async () => { const u = await api.invoke('pick-image'); if (u) { wp.image = u; save(); HM.render(); } } }, icon('image'), wp.image ? 'Choose another picture' : 'Choose a picture')),
          A.field('Darken', A.slider(0, 80, wp.dim || 0, A.debounce(v => { wp.dim = v; save(); HM.render(); }, 120))),
          A.field('Blur', A.slider(0, 30, wp.blur || 0, A.debounce(v => { wp.blur = v; save(); HM.render(); }, 120)))));
        body.append(h('p.p-note', { style: { marginTop: '.8rem' } }, 'The Aqua wallpapers follow your browser colour (and the Auto cycle).'));
      }
    };
    paint();
    return sheet;
  }

  /* ---------- per-widget settings ---------- */
  function widgetSettings(w) {
    const T = TYPES[w.type];
    A.dialog((el, close) => {
      const cfg = JSON.parse(JSON.stringify(w.cfg));
      let card = w.card || 'auto', align = w.align || 'left', size = w.size || T.size;
      const form = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '.8rem' } });
      for (const [key, type, label, extra] of T.fields || []) {
        if (type === 'toggle') form.append(A.toggle(label, cfg[key], v => { cfg[key] = v; }));
        else if (type === 'text') form.append(A.field(label, h('input.input', { value: cfg[key] || '', placeholder: extra || '', oninput: e => { cfg[key] = e.target.value; } })));
        else if (type === 'date') form.append(A.field(label, h('input.input', { type: 'date', value: cfg[key] || '', oninput: e => { cfg[key] = e.target.value; } })));
        else if (type === 'chips') form.append(A.field(label, A.chips(extra, cfg[key], v => { cfg[key] = v; })));
        else if (type === 'select') { const opts = typeof extra === 'function' ? extra() : extra; form.append(A.field(label, h('select.input', { onchange: e => { cfg[key] = e.target.value; } }, ...opts.map(([v, l]) => h('option', { value: v, selected: String(cfg[key]) === String(v) }, l))))); }
        else if (type === 'city') {
          const cur = h('div.p-note', cfg.place ? `Now: ${cfg.place.name}` : 'No place picked yet.');
          form.append(A.field(label, h('div', { style: { display: 'flex', flexDirection: 'column', gap: '.45rem' } }, cur, cityPicker(p => { cfg.place = p; cur.textContent = `Now: ${p.name}`; }, ''))));
        } else if (type === 'image') {
          const btn = h('button.sbtn', { onclick: async () => { const u = await api.invoke('pick-image'); if (u) { cfg[key] = u; btn.textContent = 'Picture chosen ✓'; } } }, cfg[key] ? 'Choose another picture' : 'Choose a picture');
          form.append(A.field(label, h('div', btn)));
        }
      }
      form.append(
        A.field('Size', A.chips(SIZES.map(s => [s, { s: 'Small', m: 'Medium', l: 'Large', xl: 'X-large', full: 'Full' }[s]]), size, v => { size = v; })),
        A.field('Card', A.chips([['auto', 'Default'], ['glass', 'Glass'], ['solid', 'Solid'], ['clear', 'None']], card, v => { card = v; })),
        A.field('Line up', A.chips([['left', 'Left'], ['center', 'Centre']], align, v => { align = v; })));
      el.append(h('h2', T.name), form,
        h('div.row', h('button.sbtn.danger', { style: { marginRight: 'auto' }, onclick: () => { HM.data.widgets = HM.data.widgets.filter(x => x !== w); save(); HM.render(); close(); } }, 'Remove'),
          h('button.sbtn', { onclick: () => close() }, 'Cancel'),
          h('button.sbtn.primary', { onclick: () => { w.cfg = cfg; w.card = card; w.align = align; w.size = size; save(); rerender(w); close(); } }, 'Save')));
    });
  }

  function editTile(w, t) {
    A.dialog((el, close) => {
      const name = h('input.input', { value: t ? t.title : '', placeholder: 'Name', autofocus: !t ? false : true });
      const url = h('input.input', { value: t ? t.url : '', placeholder: 'example.com', autofocus: !t });
      const ok = () => {
        const u = A.toURL(url.value, { noFile: true });
        if (!u || !/^https?:/.test(u)) { url.focus(); return; }
        const title = name.value.trim() || A.hostOf(u).split('.')[0].replace(/^./, s => s.toUpperCase());
        if (t) Object.assign(t, { title, url: u }); else w.cfg.tiles.push({ id: nid(), title, url: u });
        save(); rerender(w); close();
      };
      [name, url].forEach(i => i.addEventListener('keydown', e => { if (e.key === 'Enter') ok(); }));
      el.append(h('h2', t ? 'Edit shortcut' : 'Add a shortcut'), A.field('Address', url), A.field('Name', name),
        h('div.row', t ? h('button.sbtn.danger', { style: { marginRight: 'auto' }, onclick: () => { w.cfg.tiles = w.cfg.tiles.filter(x => x !== t); save(); rerender(w); close(); } }, 'Remove') : null,
          h('button.sbtn', { onclick: () => close() }, 'Cancel'), h('button.sbtn.primary', { onclick: ok }, t ? 'Save' : 'Add')));
    });
  }
})();
