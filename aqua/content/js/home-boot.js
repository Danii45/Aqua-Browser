'use strict';
/* Starts the home page: loads this profile's layout and draws it. */
(async () => {
  try {
    await AquaPage.init();
    const data = await AquaPage.AquaBoot.Home.load();
    A.home.load(data);
    A.home.render();
    if (location.hash === '#edit') A.home.edit(true);
    // Coming back to this tab: pick up layout changes made in another tab, new bookmarks, new top sites.
    let stamp = JSON.stringify(A.home.data);
    document.addEventListener('visibilitychange', async () => {
      if (document.hidden) { stamp = JSON.stringify(A.home.data); return; }
      const fresh = await AquaPage.AquaBoot.Home.load();
      if (fresh && JSON.stringify(fresh) !== stamp && !A.home.editing) A.home.set(fresh);
      await AquaPage.loadBookmarks();
      A.home.refreshKind('bookmarks');
      A.home.refreshKind('topsites');
    });
  } catch (e) {
    console.error('Aqua home page failed', e);
    document.body.append(Object.assign(document.createElement('pre'), { textContent: 'The Aqua home page hit a problem:\n' + (e && e.stack || e), style: 'color:#fff;padding:24px;white-space:pre-wrap' }));
  }
})();
