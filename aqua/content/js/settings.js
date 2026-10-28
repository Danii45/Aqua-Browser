'use strict';
/* Aqua settings (chrome://aqua/content/settings.html). Every control writes a pref straight away; the browser
   windows and home pages are listening and update live. The #welcome section is the first-run page. */
(async () => {
  const { h, icon } = A;
  const { AquaBoot, chromeWin } = AquaPage;
  const { pref, setPref, Profiles } = AquaBoot;
  const lazy = new Proxy({}, { get: (_, name) => AquaBoot.mod(name) });
  await AquaPage.init();
  const main = document.getElementById('main');
  const set = (k, v) => setPref(k, v);
  const openTab = url => { const w = chromeWin(); w.gBrowser.selectedTab = w.gBrowser.addTrustedTab(url); };
  const section = (id, title, ...kids) => h('section.card.glass.thick', { id }, h('h2', title), ...kids);
  const row = (...kids) => h('div.p-row', ...kids);
  const note = t => h('p.p-note', t);
  const AQUA_MD = 'https://danii45.github.io/Aqua-MD/';
  const SITE = 'https://danii45.github.io/Aqua-Browser/';
  const SOURCE = 'https://github.com/Danii45/Aqua-Browser';

  function swatches(value, onpick, withCustom = true) {
    const box = h('div.swatches');
    const paint = v => [...box.children].forEach(b => b.setAttribute('aria-pressed', String(b.dataset.c === v)));
    for (const k of A.ORDER) box.append(h('button.sw', { title: A.COLORS[k].name, dataset: { c: k }, style: { '--sh': A.COLORS[k].h }, onclick: () => { paint(k); onpick(k); } }));
    box.append(h('button.sw.auto', { title: 'Auto: cycles through every Aqua MD colour', dataset: { c: 'auto' }, onclick: () => { paint('auto'); onpick('auto'); } }));
    if (withCustom) box.append(h('button.sw.custom', { title: 'Any colour', dataset: { c: 'custom' }, onclick: () => { paint('custom'); onpick('custom'); } }, '+'));
    paint(value);
    return box;
  }

  function safe(make) {
    try { return make(); }
    catch (e) { console.error('Aqua settings section failed', e); return h('section.card.glass.thick', h('h2', make.name.replace(/Card$/, '')), h('p', 'This section hit a problem: ' + (e && e.message || e))); }
  }
  function build() {
    main.replaceChildren();
    const welcome = location.hash === '#welcome';
    if (welcome) main.append(welcomeCard());
    main.append(
      h('nav.set-nav', ...[['look', 'Look'], ['tabs', 'Tabs & toolbar'], ['start', 'Home & start-up'], ['profiles', 'Profiles'], ['passwords', 'Passwords & cards'], ['privacy', 'Privacy'], ['search', 'Search'], ['advanced', 'Advanced']]
        .map(([id, l]) => h('button.chip-opt', { onclick: () => document.getElementById(id).scrollIntoView({ behavior: 'smooth', block: 'start' }) }, l))),
      ...[lookCard, tabsCard, homeCard, profilesCard, passwordsCard, privacyCard, searchCard, advancedCard].map(safe));
    const target = location.hash.slice(1);
    if (target && target !== 'welcome') setTimeout(() => { const el = document.getElementById(target); if (el) el.scrollIntoView({ block: 'start' }); }, 50);
  }

  /* ---------- welcome ---------- */
  function welcomeCard() {
    return h('section.card.glass.thick.welcome', { id: 'welcome' },
      h('div.lcd-hero.lcd', h('div.inds', h('span.ind.on', 'AQUA'), h('span.ind.on', 'BR-02'), h('span.ind', 'GLASS WEB BROWSER')), h('div.big', 'Welcome'), h('div.sub', `${A.profile.name} profile`)),
      h('p', 'Aqua Browser has separate Home and Work profiles (switch with the chip next to the address bar), a home page you can rearrange, a built-in password and card manager that stays on this computer, and ad blocking.'),
      A.field('Pick a colour', swatches(pref('aqua.color', 'blue'), v => set('aqua.color', v), false)),
      A.field('Tabs', A.chips([['top', 'Along the top'], ['side', 'Down the side']], pref('sidebar.verticalTabs', false) ? 'side' : 'top', v => setTabs(v))),
      h('div.label', 'Bring things over'),
      row(h('button.sbtn', { onclick: importData }, icon('import'), 'Import from Chrome or another browser'),
        h('button.sbtn', { onclick: () => document.getElementById('passwords').scrollIntoView({ behavior: 'smooth' }) }, icon('key'), 'Set a master password')),
      row(h('button.sbtn.primary', { onclick: () => { history.replaceState(null, '', location.pathname); build(); window.scrollTo(0, 0); } }, 'Done — show all settings'),
        h('button.sbtn.ghost', { onclick: () => openTab(AquaBoot.HOME) }, 'Start browsing')));
  }
  function importData() {
    try { lazy.MigrationUtils.showMigrationWizard(chromeWin(), { entrypoint: lazy.MigrationUtils.MIGRATION_ENTRYPOINTS ? lazy.MigrationUtils.MIGRATION_ENTRYPOINTS.PREFERENCES : undefined }); }
    catch (e) { console.error(e); openTab('about:preferences#general'); }
  }

  /* ---------- look ---------- */
  function lookCard() {
    const extra = h('div.stack');
    const paintExtra = () => {
      const color = pref('aqua.color', 'blue');
      extra.replaceChildren();
      if (color === 'auto') {
        extra.append(note('Auto fades through blue, teal, green, orange, red, pink and purple, like Aqua MD.'),
          A.field('Change colour every', A.chips([[10, '10 s'], [30, '30 s'], [60, '1 min'], [300, '5 min'], [900, '15 min'], [3600, '1 hour']], pref('aqua.autoCycle', 30), v => set('aqua.autoCycle', +v))),
          A.field('How it changes', A.chips([['step', 'Hold each colour, then fade'], ['smooth', 'Drift smoothly']], pref('aqua.autoMode', 'step'), v => set('aqua.autoMode', v))));
      }
      if (color === 'custom') extra.append(A.field('Hue', A.slider(0, 359, pref('aqua.hue', 214), A.debounce(v => set('aqua.hue', v), 60), 'hue')));
      extra.append(A.toggle('Display matches the colour', pref('aqua.lcdMatch', true), v => { set('aqua.lcdMatch', v); paintExtra(); }, 'The LCD glow in the address bar, clock and meters'));
      if (!pref('aqua.lcdMatch', true)) extra.append(A.field('Display hue', A.slider(0, 359, pref('aqua.lcdHue', 192), A.debounce(v => set('aqua.lcdHue', v), 60), 'hue')));
    };
    paintExtra();
    return section('look', 'Look',
      A.field('Colour', swatches(pref('aqua.color', 'blue'), v => { set('aqua.color', v); paintExtra(); })), extra,
      A.field('Glass strength', A.slider(10, 120, pref('aqua.glass', 60), A.debounce(v => set('aqua.glass', v), 40))),
      A.field('Glass blur', A.slider(0, 40, pref('aqua.blur', 16), A.debounce(v => set('aqua.blur', v), 40))),
      A.field('Corner roundness', A.slider(20, 160, pref('aqua.roundness', 100), A.debounce(v => set('aqua.roundness', v), 40))),
      A.field('Density', A.chips([[0, 'Comfortable'], [1, 'Compact']], pref('browser.uidensity', 0), v => set('browser.uidensity', +v))),
      A.field('Address bar text', A.chips([['dot', 'Dot matrix'], ['plain', 'Plain']], pref('aqua.addressFont', 'dot'), v => set('aqua.addressFont', v))),
      A.field('Interface font', A.chips([['barlow', 'Aqua (Barlow)'], ['system', 'System font']], pref('aqua.font', 'barlow'), v => set('aqua.font', v))),
      A.toggle('Show pages in a rounded glass frame', pref('aqua.frame', true), v => set('aqua.frame', v)),
      A.field('Websites’ colour scheme', A.chips([[2, 'Follow my computer'], [0, 'Dark'], [1, 'Light']], pref('layout.css.prefers-color-scheme.content-override', 2), v => set('layout.css.prefers-color-scheme.content-override', +v))));
  }

  /* ---------- tabs & toolbar ---------- */
  function setTabs(v) { set('sidebar.revamp', true); set('sidebar.verticalTabs', v === 'side'); }
  function tabsCard() {
    return section('tabs', 'Tabs & toolbar',
      A.field('Where tabs go', A.chips([['top', 'Along the top'], ['side', 'Down the side']], pref('sidebar.verticalTabs', false) ? 'side' : 'top', setTabs)),
      A.field('Bookmarks bar', A.chips([['always', 'Always'], ['newtab', 'Only on new tabs'], ['never', 'Never']], pref('browser.toolbars.bookmarks.visibility', 'always'), v => set('browser.toolbars.bookmarks.visibility', v))),
      A.toggle('Unload tabs I haven’t used, when memory runs low', pref('browser.tabs.unloadOnLowMemory', true), v => set('browser.tabs.unloadOnLowMemory', v)),
      row(h('button.sbtn', { onclick: () => chromeWin().gCustomizeMode.enter() }, icon('layout'), 'Rearrange toolbar buttons…')),
      note('Tip: drag buttons on and off the toolbar while rearranging. The profile chip can move too.'));
  }

  /* ---------- home ---------- */
  function homeCard() {
    return section('start', 'Home & start-up',
      A.field('New tabs open', A.chips([['home', 'My Aqua home page'], ['blank', 'A blank page'], ['engine', 'The engine’s own new tab page']], pref('aqua.newTab', 'home'), v => set('aqua.newTab', v))),
      A.field('When the browser starts', A.chips([[3, 'Continue where I left off'], [1, 'Open the home page']], pref('browser.startup.page', 3), v => set('browser.startup.page', +v))),
      row(h('button.sbtn', { onclick: () => openTab(AquaBoot.HOME + '#edit') }, icon('edit'), 'Customize the home page')));
  }

  /* ---------- profiles ---------- */
  function profilesCard() {
    const list = h('div.list');
    for (const p of Profiles.list()) {
      list.append(h('div.li', h('span.fav', icon('user')), h('div.t', h('b', p.name + (p.current ? ' (this window)' : '')), h('small', p.current ? `Colour: ${(A.COLORS[pref('aqua.color', 'blue')] || { name: pref('aqua.color', 'blue') === 'auto' ? 'Auto' : 'Custom' }).name}` : 'Opens in its own window')),
        h('div.acts.show',
          !p.current ? h('button.sbtn.small', { onclick: () => Profiles.open(p.name) }, 'Open') : null,
          h('button.gbtn.flat', { title: 'Rename', onclick: async () => { const n = await A.prompt('Rename profile', p.name, { ok: 'Rename' }); if (n && n.trim()) { await Profiles.rename(p.name, n.trim()); build(); if (p.current) chromeWin().AquaUI.applyLook(); } } }, icon('edit')))));
    }
    let color = 'purple';
    const name = h('input.input', { placeholder: 'e.g. School, Gaming, Shopping', maxLength: 24 });
    const err = h('p.err');
    const add = async () => {
      try { const n = await Profiles.create(name.value, color); A.toast(`Made “${n}”`); Profiles.open(n); build(); }
      catch (e) { err.textContent = e.message; }
    };
    name.addEventListener('keydown', e => { if (e.key === 'Enter') add(); });
    return section('profiles', 'Profiles',
      note('Each profile keeps its own sign-ins, history, bookmarks, home page, saved passwords and colour — like Home and Work. Switch with the chip next to the address bar.'),
      list,
      h('div.label', 'Add a profile'), name, A.field('Its colour', swatches(color, v => { color = v; }, false)), err,
      row(h('button.sbtn.primary', { onclick: add }, icon('plus'), 'Create and open'), h('button.sbtn.ghost', { onclick: () => openTab('about:profiles') }, 'Advanced profile manager')));
  }

  /* ---------- passwords & cards ---------- */
  // The master password lives in the engine's key store. Reached directly where the engine allows it;
  // otherwise Aqua shows whether one is set and hands over to the engine's own dialog.
  const token = () => { try { const c = Cc['@mozilla.org/security/pk11tokendb;1']; return c ? c.getService(Ci.nsIPK11TokenDB).getInternalKeyToken() : null; } catch (e) { return null; } };
  const primarySet = () => {
    try { return ChromeUtils.importESModule('resource://gre/modules/LoginHelper.sys.mjs').LoginHelper.isPrimaryPasswordSet(); } catch (e) { return false; }
  };
  function passwordsCard() {
    const t = token();
    const has = t ? t.hasPassword : primarySet();
    const box = h('div.stack');
    const a = h('input.input', { type: 'password', placeholder: 'Current master password', autocomplete: 'off' });
    const b = h('input.input', { type: 'password', placeholder: has ? 'New master password' : 'Master password (at least 8 characters)', autocomplete: 'off' });
    const c = h('input.input', { type: 'password', placeholder: 'Type it again', autocomplete: 'off' });
    const err = h('p.err');
    const save = () => {
      err.textContent = '';
      if (b.value.length < 8) { err.textContent = 'Use at least 8 characters.'; return; }
      if (b.value !== c.value) { err.textContent = 'The two passwords don’t match.'; return; }
      try {
        if (t.needsUserInit) t.initPassword(b.value);
        else if (!t.checkPassword(has ? a.value : '')) { err.textContent = 'The current master password is not right.'; return; }
        else t.changePassword(has ? a.value : '', b.value);
        A.toast(has ? 'Master password changed' : 'Master password set');
        build();
      } catch (e) { err.textContent = String(e.message || e); }
    };
    if (t) box.append(has ? a : null, b, c, err, row(h('button.sbtn.primary', { onclick: save }, icon('lock'), has ? 'Change master password' : 'Set master password')));
    else box.append(row(h('button.sbtn.primary', { onclick: () => openTab('about:preferences#privacy-logins') }, icon('lock'), has ? 'Change master password' : 'Set master password')),
      note('This opens the engine’s password settings: tick “Use a Primary Password” there.'));
    return section('passwords', 'Passwords & cards',
      h('div.lcd-hero.lcd', h('div.inds', h('span.ind.on', 'LOCAL'), h('span.ind' + (has ? '.on' : ''), 'MASTER PW'), h('span.ind.on', 'NO GOOGLE')), h('div.big', has ? 'PROTECTED' : 'NOT LOCKED'), h('div.sub', has ? 'Encrypted with your master password' : 'Set a master password to encrypt them')),
      note('Passwords and cards are saved in this profile, on this computer. Nothing goes to a Google account. With a master password, they are encrypted and the browser asks for it once each time it starts.'),
      row(h('button.sbtn', { onclick: () => openTab('about:logins') }, icon('key'), 'Saved passwords'),
        h('button.sbtn', { onclick: () => openTab('about:preferences#privacy-credit-card-autofill') }, icon('card'), 'Saved cards'),
        h('button.sbtn.ghost', { onclick: importData }, icon('import'), 'Import from Chrome…')),
      box,
      A.toggle('Offer to save passwords', pref('signon.rememberSignons', true), v => set('signon.rememberSignons', v)),
      A.toggle('Fill in saved passwords', pref('signon.autofillForms', true), v => set('signon.autofillForms', v)),
      A.toggle('Suggest strong passwords on sign-up forms', pref('signon.generation.enabled', true), v => set('signon.generation.enabled', v)),
      A.toggle('Save and fill payment cards', pref('extensions.formautofill.creditCards.enabled', true), v => set('extensions.formautofill.creditCards.enabled', v)),
      A.toggle('Save and fill addresses', pref('extensions.formautofill.addresses.enabled', true), v => set('extensions.formautofill.addresses.enabled', v)));
  }

  /* ---------- privacy ---------- */
  function privacyCard() {
    return section('privacy', 'Privacy',
      note('Ads are blocked by uBlock Origin, which comes built in. Trackers are also blocked by the engine itself.'),
      A.field('Tracker blocking', A.chips([['standard', 'Standard'], ['strict', 'Strict (may break some sites)']], pref('browser.contentblocking.category', 'standard'), v => set('browser.contentblocking.category', v))),
      A.toggle('Only use secure (https) connections', pref('dom.security.https_only_mode', false), v => set('dom.security.https_only_mode', v), 'Asks before opening sites that don’t support https'),
      A.toggle('Ask sites not to sell or share my data (Global Privacy Control)', pref('privacy.globalprivacycontrol.enabled', true), v => set('privacy.globalprivacycontrol.enabled', v)),
      A.toggle('Clear cookies and history when I quit', pref('privacy.sanitize.sanitizeOnShutdown', false), v => set('privacy.sanitize.sanitizeOnShutdown', v), 'You will be signed out of sites every time'),
      row(h('button.sbtn', { onclick: () => { const w = chromeWin(); w.Sanitizer.showUI(w); } }, icon('trash'), 'Clear browsing data…'),
        h('button.sbtn.ghost', { onclick: () => openTab('about:addons') }, icon('shield'), 'Ad blocker settings')));
  }

  /* ---------- search ---------- */
  function searchCard() {
    const sel = h('select.input', ...Object.keys(A.engines).map(n => h('option', { value: n, selected: n === A.st.search }, n)));
    sel.addEventListener('change', async () => {
      if (await AquaBoot.Home.setEngine(sel.value)) { A.st.search = sel.value; A.toast(`Searching with ${sel.value}`); }
    });
    return section('search', 'Search',
      A.field('Search engine', sel),
      A.toggle('Show search suggestions while I type', pref('browser.search.suggest.enabled', true), v => set('browser.search.suggest.enabled', v), 'What you type is sent to your search engine'),
      row(h('button.sbtn.ghost', { onclick: () => openTab('about:preferences#search') }, 'Add or remove search engines')));
  }

  /* ---------- advanced ---------- */
  function advancedCard() {
    const css = h('textarea.input.code', { rows: 6, spellcheck: false, placeholder: '/* CSS for the browser itself, e.g.\n   .tab-background { border-radius: 4px !important } */' });
    css.value = pref('aqua.customCSS', '');
    css.addEventListener('input', A.debounce(() => set('aqua.customCSS', css.value), 500));
    const def = h('div.p-row');
    if (Services.appinfo.OS === 'WINNT') {
      // Windows decides this in its own Settings app; the installer has listed Aqua Browser there.
      def.append(h('span.p-note.grow', 'Windows picks the default browser in Settings → Apps → Default apps. Aqua Browser is listed there.'),
        h('button.sbtn.small', { onclick: () => {
          try {
            Cc['@mozilla.org/uriloader/external-protocol-service;1'].getService(Ci.nsIExternalProtocolService)
              .loadURI(Services.io.newURI('ms-settings:defaultapps?registeredAppUser=Aqua%20Browser'), Services.scriptSecurityManager.getSystemPrincipal());
          } catch (e) { console.error(e); A.toast('Open Settings → Apps → Default apps'); }
        } }, 'Open Default apps'));
    } else try {
      const isDef = lazy.ShellService.isDefaultBrowser(false, false);
      def.append(h('span.p-note.grow', isDef ? 'Aqua Browser is your default browser.' : 'Open links from other apps in Aqua Browser.'),
        isDef ? null : h('button.sbtn.small', { onclick: async () => { try { await lazy.ShellService.setDefaultBrowser(false); } catch (e) { console.error(e); } A.toast('Asked your computer to make Aqua Browser the default'); build(); } }, 'Make default'));
    } catch (e) { def.append(h('span.p-note', 'Set Aqua Browser as default in your computer’s settings.')); }
    let ver = '';
    try { ver = Services.prefs.getStringPref('aqua.version', ''); } catch (e) {}
    return section('advanced', 'Advanced',
      h('div.label', 'Default browser'), def,
      A.field('Browser CSS (changes how Aqua Browser itself looks)', css),
      row(h('button.sbtn.ghost', { onclick: () => openTab('about:preferences') }, icon('gear'), 'All engine settings'), h('button.sbtn.ghost', { onclick: () => openTab('about:support') }, 'Troubleshooting')),
      h('div.label', 'About'),
      h('div.lcd-hero.lcd',
        h('div.inds', h('span.ind.on', 'AQUA'), h('span.ind.on', `V${ver || '2'}`), h('span.ind', 'GLASS WEB BROWSER')),
        h('div.big', 'Aqua Browser'),
        h('div.sub', `Engine ${Services.appinfo.version} · Mozilla’s open-source Gecko`)),
      row(h('button.sbtn.primary', { onclick: () => openTab(AQUA_MD) }, 'Try Aqua MD ↗'),
        h('button.sbtn.ghost', { onclick: () => openTab(SITE) }, 'Aqua Browser website'),
        h('button.sbtn.ghost', { onclick: () => openTab(SOURCE) }, 'Source code')),
      note('Aqua Browser is made by the maker of Aqua MD, the see-through MiniDisc deck for your desktop, and borrows its look. Firefox is a trademark of the Mozilla Foundation; Aqua Browser is not made or endorsed by Mozilla.'));
  }

  window.addEventListener('hashchange', build);
  build();
})().catch(e => { console.error('Aqua settings failed', e); document.body.append(Object.assign(document.createElement('pre'), { textContent: 'Aqua settings hit a problem:\n' + (e && e.stack || e), style: 'color:#fff;padding:24px;white-space:pre-wrap' })); });
