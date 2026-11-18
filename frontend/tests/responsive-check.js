/* Real-browser layout test. Drives the UI through every major screen at phone, tablet and desktop widths,
 * fails on horizontal overflow or uncaught page errors, and writes full-page screenshots.
 *
 * Requirements: a Chrome/Chromium binary. Set CHROME_PATH, or install @sparticuz/chromium (Linux only).
 * Usage (from frontend/tests):  npm install && CHROME_PATH="C:\\...\\chrome.exe" node responsive-check.js
 * Optional: SHOTS_DIR=./screenshots to keep the images. Fonts and Bootstrap are served locally when offline. */
'use strict';
const fs = require('fs');
const path = require('path');
const { createServer } = require('./serve-mock');
const { PERSONAL_ID, ORG_ID, CASE_IDS, ORG } = require('./mock-api');

const PORT = 5500;
const WIDTHS = [320, 375, 430, 768, 1024, 1366, 1920];
const SHOTS = process.env.SHOTS_DIR ? path.resolve(process.env.SHOTS_DIR) : null;
const BOOTSTRAP = path.join(__dirname, 'node_modules', 'bootstrap', 'dist', 'css', 'bootstrap.min.css');

async function launch(puppeteer) {
  let executablePath = process.env.CHROME_PATH;
  let args = ['--no-sandbox', '--disable-dev-shm-usage'];
  if (!executablePath) {
    const chromium = (await import('@sparticuz/chromium')).default;
    executablePath = await chromium.executablePath();
    args = chromium.args;
  }
  return puppeteer.launch({ executablePath, args, headless: true, defaultViewport: null });
}

/* Primary controls below 1024px must be at least 40px tall so they are comfortable to tap. */
const TOUCH_PROBE = () => {
  if (window.innerWidth >= 1024) return [];
  const selector = '.btn, .side-link, .menu-toggle, .drawer-toggle, .send-btn, .auth-tab, .show-toggle';
  const small = [];
  document.querySelectorAll(selector).forEach((node) => {
    const r = node.getBoundingClientRect();
    const cs = getComputedStyle(node);
    if (r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && r.height < 40) {
      small.push(`${String(node.className).split(' ')[0]} ${node.textContent.trim().slice(0, 20)} ${Math.round(r.height)}px`);
    }
  });
  return small.slice(0, 6);
};

/* Finds visible elements that extend past the viewport and are not inside a clipping/scrolling box. */
const OVERFLOW_PROBE = () => {
  const vw = window.innerWidth;
  const clips = (n) => { const o = getComputedStyle(n).overflowX; return o === 'auto' || o === 'scroll' || o === 'hidden'; };
  const visible = (n) => { const r = n.getBoundingClientRect(); const cs = getComputedStyle(n); return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none'; };
  const insideClip = (n) => { for (let p = n.parentElement; p && p !== document.body; p = p.parentElement) { if (clips(p)) return true; } return false; };
  const offenders = [];
  for (const n of document.querySelectorAll('body *')) {
    if (!visible(n)) continue;
    const r = n.getBoundingClientRect();
    if (r.right > vw + 1 && !insideClip(n)) {
      offenders.push({ tag: n.tagName, id: n.id, cls: String(n.className).slice(0, 60), right: Math.round(r.right) });
    }
  }
  return { vw, scrollWidth: document.documentElement.scrollWidth, offenders: offenders.slice(0, 6) };
};

async function run() {
  const puppeteer = require('puppeteer-core');
  const server = createServer(PORT);
  await new Promise((resolve) => server.listen(PORT, resolve));
  const browser = await launch(puppeteer);
  const failures = [];
  const pageErrors = [];
  let checks = 0;
  if (SHOTS) fs.mkdirSync(SHOTS, { recursive: true });

  try {
    for (const width of WIDTHS) {
      const page = await browser.newPage();
      await page.setViewport({ width, height: 860, deviceScaleFactor: 1 });
      page.on('pageerror', (e) => pageErrors.push(`${width}px: ${e.message}`));
      await page.setRequestInterception(true);
      page.on('request', (req) => {
        const url = req.url();
        if (url.includes('cdn.jsdelivr.net/npm/bootstrap') && fs.existsSync(BOOTSTRAP)) {
          return req.respond({ status: 200, contentType: 'text/css', body: fs.readFileSync(BOOTSTRAP) });
        }
        if (url.startsWith('https://fonts.')) return req.respond({ status: 200, contentType: 'text/css', body: '' });
        if (!url.startsWith(`http://localhost:${PORT}`)) return req.abort();
        return req.continue();
      });
      await page.evaluateOnNewDocument((base) => { window.SILENTECHO_API_BASE = base; }, `http://localhost:${PORT}`);
      await page.goto(`http://localhost:${PORT}/#landing`, { waitUntil: 'domcontentloaded' });

      const snap = async (name) => {
        checks++;
        await new Promise((r) => setTimeout(r, 260));
        const result = await page.evaluate(OVERFLOW_PROBE);
        if (result.scrollWidth > result.vw || result.offenders.length) {
          failures.push({ width, state: name, ...result });
        }
        const small = await page.evaluate(TOUCH_PROBE);
        if (small.length) failures.push({ width, state: name, scrollWidth: 0, vw: width, offenders: small.map((t) => ({ touchTarget: t })) });
        if (SHOTS) await page.screenshot({ path: path.join(SHOTS, `${name}-${width}.png`), fullPage: true });
      };
      const goHash = async (hash, screenId) => {
        await page.evaluate((h) => { location.hash = h; }, hash);
        await page.waitForFunction((id) => document.getElementById(id) && document.getElementById(id).classList.contains('active'), { timeout: 5000 }, screenId);
      };
      const openDrawer = async (dash) => {
        await page.click(`${dash} .drawer-toggle`);
        await page.waitForFunction((d) => document.querySelector(`${d} .dash`).classList.contains('nav-open') && document.querySelector(`${d} .sidebar`).getBoundingClientRect().left >= -1, { timeout: 4000, polling: 50 }, dash);
      };
      const type = async (sel, text) => { await page.click(sel); await page.evaluate((s) => { document.querySelector(s).value = ''; }, sel); await page.type(sel, text); };
      const screenActive = (id) => page.waitForFunction((x) => document.getElementById(x).classList.contains('active'), { timeout: 5000 }, id);

      // Public pages
      await snap('landing');
      if (width <= 880) {
        await page.click('.site-header .menu-toggle');
        await page.waitForFunction(() => document.querySelector('.site-header').classList.contains('is-open'), { timeout: 3000 });
        await snap('landing-menu-open');
        await page.click('.site-header .menu-toggle');
        await page.waitForFunction(() => !document.querySelector('.site-header').classList.contains('is-open'), { timeout: 3000 });
      }
      await goHash('#how', 'screen-how'); await snap('how');
      await goHash('#about', 'screen-about'); await snap('about');
      await goHash('#resources', 'screen-resources'); await snap('resources');
      await goHash('#login', 'screen-login'); await snap('login-personal');
      await page.click('.auth-tab[data-workspace="ORGANISATION"]'); await snap('login-organisation');
      await goHash('#create', 'screen-create'); await snap('create');
      await goHash('#org-register', 'screen-org-register'); await snap('org-register');
      await goHash('#org-join', 'screen-org-join'); await snap('org-join');

      // Personal journey
      await goHash('#login', 'screen-login');
      await type('#login-id', PERSONAL_ID);
      await page.click('#btn-login');
      await page.waitForFunction(() => location.hash === '#dashboard/overview', { timeout: 5000 });
      await screenActive('screen-dashboard');
      await page.waitForFunction(() => document.querySelector('#recent-body tr.clickable'), { timeout: 5000 });
      await snap('dashboard-overview');
      if (width < 1024) {
        await openDrawer('#screen-dashboard');
        await snap('dashboard-drawer-open');
        // Tap the dimmed area to the right of the open drawer, as a user would.
        await page.mouse.click(width - 12, 420);
        await page.waitForFunction(() => !document.querySelector('#screen-dashboard .dash').classList.contains('nav-open'), { timeout: 3000 });
      }
      if (width <= 640) {
        const label = await page.evaluate(() => { const td = document.querySelector('#recent-body td'); return td ? td.getAttribute('data-label') : null; });
        checks++;
        if (!label) failures.push({ width, state: 'stacked table labels', scrollWidth: 0, vw: width, offenders: [{ table: 'recent cases has no data-label' }] });
      }
      await goHash('#dashboard/cases', 'screen-dashboard'); await snap('dashboard-cases');
      await goHash(`#dashboard/track/${CASE_IDS[0]}`, 'screen-dashboard');
      await page.waitForFunction(() => !document.getElementById('track-result').hidden, { timeout: 5000 });
      await snap('dashboard-track');
      await goHash(`#dashboard/messages/${CASE_IDS[0]}`, 'screen-dashboard');
      await page.waitForFunction(() => document.querySelectorAll('#chat-body .bubble').length >= 1, { timeout: 5000 });
      await snap('dashboard-messages');
      await goHash('#dashboard/resources', 'screen-dashboard'); await snap('dashboard-resources');
      await goHash('#dashboard/settings', 'screen-dashboard'); await snap('dashboard-settings');
      await goHash('#report', 'screen-report'); await page.waitForFunction(() => document.querySelectorAll('#f-org option').length >= 2, { timeout: 5000 });
      await snap('report-step1');
      await page.select('#f-org', ORG.id);
      await page.select('#f-category', 'Bullying');
      await page.click('[data-action="report-next-1"]');
      await type('#f-title', 'Layout check report');
      await type('#f-desc', 'Dummy text for the layout test only.');
      await page.click('[data-action="report-next-2"]');
      await page.waitForFunction(() => !document.getElementById('report-step-3').hidden, { timeout: 5000 });
      await snap('report-step3');
      await page.evaluate(() => { document.getElementById('f-consent').checked = true; });
      await page.click('#btn-submit-report');
      await page.waitForFunction(() => !document.getElementById('report-success').hidden, { timeout: 5000 });
      await snap('report-success');
      await goHash('#dashboard/overview', 'screen-dashboard');
      if (width < 1024) await openDrawer('#screen-dashboard');
      await page.click('#screen-dashboard .sidebar [data-action="logout"]');
      await page.waitForFunction(() => location.hash === '#landing', { timeout: 5000 });

      // Organisation journey
      await goHash('#login/org', 'screen-login');
      await type('#login-id', ORG_ID);
      await page.click('#btn-login');
      await page.waitForFunction(() => location.hash === '#org/overview', { timeout: 5000 });
      await screenActive('screen-org');
      await page.waitForFunction(() => document.querySelectorAll('#o-recent-body tr.clickable').length >= 1, { timeout: 5000 });
      await snap('org-overview');
      await goHash('#org/inbox', 'screen-org'); await snap('org-inbox');
      await goHash(`#org/case/${CASE_IDS[0]}`, 'screen-org');
      await page.waitForFunction(() => document.querySelector('#oc-title').textContent.length > 0 && document.querySelectorAll('#oc-chat .bubble').length >= 1, { timeout: 5000 });
      await snap('org-case');
      await goHash('#org/team', 'screen-org'); await snap('org-team');
      await goHash('#org/overview', 'screen-org');
      if (width < 1024) await openDrawer('#screen-org');
      await page.click('#screen-org .sidebar [data-action="logout"]');
      await page.waitForFunction(() => location.hash === '#landing', { timeout: 5000 });
      await page.close();
      console.log(`width ${width}px: done`);
    }
  } finally {
    await browser.close();
    server.close();
  }

  console.log(`\nChecks run: ${checks}`);
  if (pageErrors.length) { console.log('PAGE ERRORS:'); pageErrors.slice(0, 20).forEach((e) => console.log('  ' + e)); }
  if (failures.length) {
    console.log(`LAYOUT FAILURES: ${failures.length}`);
    failures.slice(0, 25).forEach((f) => console.log(`  ${f.width}px ${f.state}: scrollWidth=${f.scrollWidth} vw=${f.vw} offenders=${JSON.stringify(f.offenders)}`));
  }
  const ok = failures.length === 0 && pageErrors.length === 0;
  console.log(ok ? 'RESPONSIVE CHECK PASSED' : 'RESPONSIVE CHECK FAILED');
  process.exit(ok ? 0 : 1);
}

run().catch((error) => { console.error('HARNESS ERROR', error); process.exit(2); });
