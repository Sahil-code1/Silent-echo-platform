/* SilentEcho progressive enhancement. Loaded after js/app.js and does not change routing or data.
 * It only handles presentation: the public header and menu, the dashboard drawer and app bar,
 * stacked-table labels for phones, the footer year, and keeping ARIA state in step with the view. */
(() => {
  'use strict';

  const body = document.body;
  const PUBLIC = new Set(['landing', 'how', 'about', 'resources']);
  const WORKSPACE = new Set(['dashboard', 'org']);

  /* ---------- Which chrome to show: public header and footer only on public pages ---------- */
  function currentScreenId() {
    const active = document.querySelector('.screen.active');
    return active ? active.id.replace(/^screen-/, '') : '';
  }

  function kindOf(name) {
    if (PUBLIC.has(name)) return 'public';
    if (WORKSPACE.has(name)) return 'workspace';
    if (name === 'report') return 'report';
    return 'auth';
  }

  const header = document.querySelector('.site-header');
  const menuToggle = header ? header.querySelector('.menu-toggle') : null;
  const navlinks = header ? header.querySelector('#site-nav') : null;

  function setHeaderOpen(open) {
    if (!header) return;
    header.classList.toggle('is-open', open);
    if (menuToggle) {
      menuToggle.setAttribute('aria-expanded', String(open));
      menuToggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    }
  }

  function updateChrome() {
    const name = currentScreenId();
    body.dataset.kind = kindOf(name);
    body.dataset.screen = name;
    setHeaderOpen(false);
    closeDrawers();
    if (navlinks) {
      navlinks.querySelectorAll('a[data-nav]').forEach((link) => {
        if (link.dataset.nav === name) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
      });
    }
  }

  document.querySelectorAll('.screen').forEach((screen) => {
    new MutationObserver(updateChrome).observe(screen, { attributes: true, attributeFilter: ['class'] });
  });

  /* ---------- Public header menu (phones and small tablets) ---------- */
  if (menuToggle) {
    menuToggle.addEventListener('click', () => setHeaderOpen(!header.classList.contains('is-open')));
  }
  if (navlinks) {
    navlinks.addEventListener('click', (event) => {
      if (event.target.closest('a')) setHeaderOpen(false);
    });
  }

  /* ---------- Dashboard drawer (below 1024px) and app bar title ---------- */
  const dashes = Array.from(document.querySelectorAll('.dash'));

  function setDrawer(dash, open) {
    dash.classList.toggle('nav-open', open);
    const toggle = dash.querySelector('.drawer-toggle');
    if (toggle) {
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    }
  }

  function closeDrawers() {
    dashes.forEach((dash) => setDrawer(dash, false));
  }

  dashes.forEach((dash) => {
    const toggle = dash.querySelector('.drawer-toggle');
    const scrim = dash.querySelector('.drawer-scrim');
    if (toggle) toggle.addEventListener('click', () => setDrawer(dash, !dash.classList.contains('nav-open')));
    if (scrim) scrim.addEventListener('click', () => setDrawer(dash, false));
    dash.querySelectorAll('.side-link').forEach((link) => link.addEventListener('click', () => setDrawer(dash, false)));
  });

  function syncAppbarTitles() {
    dashes.forEach((dash) => {
      const title = dash.querySelector('.appbar-title');
      const active = dash.querySelector('.side-link.active');
      if (title) title.textContent = active ? active.textContent.trim() : 'Menu';
    });
  }

  document.querySelectorAll('.side-link').forEach((link) => {
    new MutationObserver(syncAppbarTitles).observe(link, { attributes: true, attributeFilter: ['class'] });
  });

  /* Escape closes whichever menu is open. Changing to a desktop width closes the drawers. */
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') { setHeaderOpen(false); closeDrawers(); }
  });
  window.matchMedia('(min-width: 1024px)').addEventListener('change', (event) => {
    if (event.matches) closeDrawers();
  });
  window.matchMedia('(min-width: 881px)').addEventListener('change', (event) => {
    if (event.matches) setHeaderOpen(false);
  });

  /* ---------- Stacked tables on phones: each cell gets its column name as a label ---------- */
  function labelTables() {
    document.querySelectorAll('table.cases-table').forEach((table) => {
      const heads = Array.from(table.querySelectorAll('thead th')).map((th) => th.textContent.trim());
      table.querySelectorAll('tbody tr').forEach((row) => {
        Array.from(row.children).forEach((cell, index) => {
          if (cell.tagName === 'TD' && !cell.hasAttribute('colspan') && heads[index]) {
            if (cell.getAttribute('data-label') !== heads[index]) cell.setAttribute('data-label', heads[index]);
          }
        });
      });
    });
  }

  let labelQueued = false;
  new MutationObserver(() => {
    if (labelQueued) return;
    labelQueued = true;
    requestAnimationFrame(() => { labelQueued = false; labelTables(); });
  }).observe(body, { childList: true, subtree: true });

  /* ---------- Footer year ---------- */
  const year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());

  updateChrome();
  syncAppbarTitles();
  labelTables();
})();
