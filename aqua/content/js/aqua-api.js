'use strict';
/* The bridge between Aqua's own pages (home, settings) and the browser engine. These pages are part of the
   browser (chrome://aqua/), so they can read bookmarks and history and save files in the profile. Anything
   from the web (weather, feeds, favicons) is only ever shown as text or images, never run as code. */
(() => {
  const { AquaBoot, pref } = ChromeUtils.importESModule('chrome://aqua/content/boot.sys.mjs');
  const { Home, Profiles } = AquaBoot;
  const chromeWin = () => window.browsingContext.topChromeWindow;
  const isPrivate = () => { try { return AquaBoot.isPrivate(chromeWin()); } catch (e) { return false; } };

  async function pickImage() {
    const fp = Cc['@mozilla.org/filepicker;1'].createInstance(Ci.nsIFilePicker);
    try { fp.init(window.browsingContext, 'Choose a picture', Ci.nsIFilePicker.modeOpen); }
    catch (e) { fp.init(window, 'Choose a picture', Ci.nsIFilePicker.modeOpen); }
    fp.appendFilters(Ci.nsIFilePicker.filterImages);
    const rv = await new Promise(res => fp.open(res));
    if (rv !== Ci.nsIFilePicker.returnOK || !fp.file) return null;
    if (fp.file.fileSize > 40 * 1024 * 1024) throw new Error('That image is too big (over 40 MB).');
    const dir = PathUtils.join(PathUtils.profileDir, 'aqua-media');
    await IOUtils.makeDirectory(dir, { ignoreExisting: true });
    const ext = (fp.file.leafName.match(/\.[a-z0-9]{2,5}$/i) || ['.img'])[0].toLowerCase();
    const dest = PathUtils.join(dir, Math.random().toString(36).slice(2, 10) + ext);
    await IOUtils.copy(fp.file.path, dest);
    return PathUtils.toFileURI(dest);
  }
  async function getText(url, ms = 12000) {
    const ac = new AbortController(); const t = setTimeout(() => ac.abort(), ms);
    try {
      const r = await fetch(url, { signal: ac.signal, credentials: 'omit' });
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return await r.text();
    } finally { clearTimeout(t); }
  }

  const handlers = {
    'home:save': data => Home.save(data),
    'pick-image': pickImage,
    'feed:fetch': async url => {
      if (!/^https?:\/\//i.test(String(url))) throw new Error('Feeds need an http:// or https:// address.');
      return (await getText(url)).slice(0, 3 * 1024 * 1024);
    },
    'suggest:remote': async q => {
      if (isPrivate()) return [];
      const u = Home.suggestURL(q, window.A && A.st && A.st.search);
      if (!u) return [];
      try { const j = JSON.parse(await getText(u, 2500)); return (Array.isArray(j) && Array.isArray(j[1]) ? j[1] : []).filter(x => typeof x === 'string').slice(0, 5); } catch (e) { return []; }
    },
    'history:top': n => (isPrivate() ? [] : Home.topSites(n)),
    'win:new': (o = {}) => {
      const w = chromeWin();
      if (o.urls && o.urls[0]) w.openTrustedLinkIn(o.urls[0], 'window', { private: !!o.private });
      else w.OpenBrowserWindow({ private: !!o.private });
    }
  };
  const platform = Services.appinfo.OS === 'Darwin' ? 'darwin' : (Services.appinfo.OS === 'WINNT' ? 'win32' : 'linux');
  window.aqua = {
    platform,
    invoke: (ch, ...a) => Promise.resolve().then(() => { const f = handlers[ch]; if (!f) throw new Error('Not available here: ' + ch); return f(...a); }),
    on: () => () => {},
    pathForFile: () => ''
  };

  // Bookmarks in the shape the home page widgets expect.
  function makeBM(data) {
    const BM = {
      data,
      folders(nodes = null, depth = 0, out = []) {
        if (!nodes) {
          out.push({ id: 'bar', title: 'Bookmarks toolbar', depth: 0, node: { children: data.bar } }); BM.folders(data.bar, 1, out);
          out.push({ id: 'other', title: 'Other bookmarks', depth: 0, node: { children: data.other } }); BM.folders(data.other, 1, out);
          return out;
        }
        for (const n of nodes) if (n.children) { out.push({ id: n.id, title: n.title, depth, node: n }); BM.folders(n.children, depth + 1, out); }
        return out;
      },
      folderById(id) { if (id === 'bar') return { children: data.bar }; if (id === 'other') return { children: data.other }; const f = BM.folders().find(x => x.id === id); return f ? f.node : null; }
    };
    return BM;
  }

  // Aqua's settings as the shared page code expects them (A.st).
  function settings() {
    return {
      color: pref('aqua.color', 'blue'), hue: pref('aqua.hue', 214), lcdHue: pref('aqua.lcdHue', 192), lcdMatch: pref('aqua.lcdMatch', true),
      autoCycle: pref('aqua.autoCycle', 30), autoMode: pref('aqua.autoMode', 'step'),
      glass: pref('aqua.glass', 60), blur: pref('aqua.blur', 16), roundness: pref('aqua.roundness', 100),
      density: 'comfy', uiScale: 100, animations: true, addressFont: pref('aqua.addressFont', 'dot'), font: pref('aqua.font', 'barlow'),
      tabLayout: 'top', sideWidth: 250, tabClose: 'hover', showBookmarksBar: true, customCSS: '',
      suggestions: pref('browser.search.suggest.enabled', true), toolbar: {}, privacy: { blockAds: true, adblockOff: [] }
    };
  }

  window.AquaPage = {
    AquaBoot, chromeWin, isPrivate, settings,
    async init() {
      const A = window.A;
      const eng = await Home.engines();
      A.engines = Object.fromEntries(eng.list.map(n => [n, { name: n }]));
      A.st = settings();
      A.st.search = eng.def;
      A.private = isPrivate();
      A.profile = { name: A.private ? 'Private' : Profiles.current() };
      A.homeDir = Services.dirsvc.get('Home', Ci.nsIFile).path;
      A.searchURL = (q, name) => Home.searchURL(q, name || A.st.search);
      const plainFav = A.fav;
      A.fav = (icon, url, o = {}) => plainFav(icon || (/^https?:/.test(url || '') ? 'page-icon:' + url : ''), url, Object.assign({ lookup: true }, o));
      A.tabs = {
        open(url, o = {}) { const w = chromeWin(); const tab = w.gBrowser.addTrustedTab(url, { inBackground: !!o.background }); if (!o.background) w.gBrowser.selectedTab = tab; },
        navigate(url) { chromeWin().openTrustedLinkIn(url, 'current'); }
      };
      await this.loadBookmarks();
      const obs = { observe: () => { const s = A.st.search; A.st = Object.assign(settings(), { search: s }); A.applyTheme(); } };
      Services.prefs.addObserver('aqua.', obs);
      addEventListener('unload', () => Services.prefs.removeObserver('aqua.', obs), { once: true });
      A.applyTheme();
    },
    async loadBookmarks() { window.A.bm = makeBM(await Home.bookmarks()); }
  };
})();
