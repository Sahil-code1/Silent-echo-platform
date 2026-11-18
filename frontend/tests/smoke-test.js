/* Headless functional tests (jsdom). Loads the real index.html and js/app.js against mock-api.js
 * and checks behaviour: validation, sign-in errors, the one-time ID reveal, the report flow, tracking,
 * messaging, evidence checks, organisation actions, logout, and that user text is never run as HTML.
 * Usage: npm install && npm run smoke            (from frontend/tests)
 * This does not replace running the real backend; see DEPLOYMENT_GUIDE.md for the manual smoke test. */
'use strict';
const fs = require('fs');
const path = require('path');
const { JSDOM, VirtualConsole } = require('jsdom');
const { createMockBackend, PERSONAL_ID, ORG_ID, ORG, CASE_IDS, RECOVERY_CODE } = require('./mock-api');

const FRONTEND = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(FRONTEND, 'index.html'), 'utf8')
  .replace(/<link[^>]*(fonts\.googleapis|fonts\.gstatic|cdn\.jsdelivr)[^>]*>/g, '')
  .replace(/<link rel="stylesheet" href="css\/style.css">/, '')
  .replace(/<script src="js\/config.js"><\/script>/, '')
  .replace(/<script src="js\/app.js"><\/script>/, '');
const configJs = fs.readFileSync(path.join(FRONTEND, 'js/config.js'), 'utf8');
const appJs = fs.readFileSync(path.join(FRONTEND, 'js/app.js'), 'utf8');

const runtimeErrors = [];
const vc = new VirtualConsole();
vc.on('jsdomError', (e) => runtimeErrors.push(String(e && (e.message || e))));
vc.on('error', (...a) => runtimeErrors.push(a.map(String).join(' ')));

const backend = createMockBackend();
backend.RECOVERY_CODE_FOR_TEST = RECOVERY_CODE;
const requests = [];

function toResponse(result) {
  const headers = { get: (name) => (result.headers && result.headers[name]) || null };
  return {
    ok: result.status >= 200 && result.status < 300,
    status: result.status,
    headers,
    json: async () => (result.body ? JSON.parse(result.body) : {}),
    blob: async () => new Blob([result.body || '']),
    text: async () => result.body || ''
  };
}

async function mockFetch(url, opts = {}) {
  const u = new URL(url);
  const method = (opts.method || 'GET').toUpperCase();
  let body = opts.body;
  requests.push(`${method} ${u.pathname}`);
  return toResponse(backend.handle({ method, path: u.pathname, headers: opts.headers || {}, body }));
}

const dom = new JSDOM(html, {
  url: 'http://localhost:5500/index.html',
  runScripts: 'outside-only',
  pretendToBeVisual: true,
  virtualConsole: vc,
  beforeParse(window) {
    window.fetch = mockFetch;
    window.SILENTECHO_API_BASE = 'http://localhost:5500';
    window.scrollTo = () => {};
    window.HTMLElement.prototype.scrollIntoView = function () {};
    window.URL.createObjectURL = () => 'blob:mock';
    window.URL.revokeObjectURL = () => {};
  }
});
const w = dom.window;
const $ = (sel) => w.document.querySelector(sel);
const $$ = (sel) => Array.from(w.document.querySelectorAll(sel));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitFor(predicate, label, timeout = 3000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (predicate()) return;
    await sleep(15);
  }
  throw new Error(`Timed out waiting for: ${label}`);
}
const activeScreen = () => { const s = $$('.screen.active'); return s.length === 1 ? s[0].id : `${s.length} active`; };
const setVal = (sel, value) => { const n = $(sel); n.value = value; n.dispatchEvent(new w.Event('input', { bubbles: true })); n.dispatchEvent(new w.Event('change', { bubbles: true })); };
const submit = (sel) => $(sel).dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
const click = (sel) => $(sel).dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true }));
const attachFile = (sel, name, type, content) => {
  const input = $(sel);
  const file = new w.File([content], name, { type });
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new w.Event('change', { bubbles: true }));
  return file;
};

let passed = 0;
const failures = [];
function check(name, condition, detail = '') {
  if (condition) { passed++; console.log(`PASS ${name}`); }
  else { failures.push(name); console.log(`FAIL ${name}${detail ? '  [' + detail + ']' : ''}`); }
}

async function main() {
  w.eval(configJs);
  w.eval(appJs);
  await waitFor(() => activeScreen() === 'screen-landing', 'landing screen');

  // ---- Public pages and navigation ----
  check('landing is the first screen', activeScreen() === 'screen-landing');
  click('#btn-nav-login');
  await waitFor(() => activeScreen() === 'screen-login', 'login screen');
  check('Login button opens the login screen', activeScreen() === 'screen-login');
  check('login defaults to the Personal tab', $('.auth-tab.active').dataset.workspace === 'PERSONAL');

  // ---- Sign-in validation and errors ----
  setVal('#login-id', 'not-a-valid-id');
  submit('#form-login');
  await sleep(20);
  check('malformed ID shows a local error and sends nothing', !$('#login-error').hidden && /full private sign-in ID/.test($('#login-error').textContent) && !requests.some((r) => r.includes('/api/auth/login')));

  setVal('#login-id', ORG_ID);
  submit('#form-login');
  await waitFor(() => !$('#login-error').hidden, 'workspace error');
  check('organisation ID on the personal tab is refused with a clear message', /other workspace/.test($('#login-error').textContent), $('#login-error').textContent);
  check('the ID field is cleared after a failed attempt', $('#login-id').value === '');

  click('.auth-tab[data-workspace="ORGANISATION"]');
  check('organisation tab updates the heading', /team/.test($('#login-title').textContent));
  check('organisation tab shows the join and register links', !$('#login-switch-org').hidden && $('#login-switch-personal').hidden);
  click('.auth-tab[data-workspace="PERSONAL"]');

  // ---- Personal sign-in ----
  setVal('#login-id', PERSONAL_ID);
  submit('#form-login');
  await waitFor(() => w.location.hash === '#dashboard/overview', 'dashboard after sign-in');
  await waitFor(() => $$('#recent-body tr.clickable').length === 2, 'recent cases rendered');
  check('personal sign-in opens the dashboard overview', activeScreen() === 'screen-dashboard' && !$('#dash-overview').hidden);
  check('stats count the cases from the API', $('#stat-total').textContent === '2' && $('#stat-resolved').textContent === '1' && $('#stat-review').textContent === '1');
  check('the sign-in ID field is cleared after success', $('#login-id').value === '');
  check('dashboard navigation lists the seven personal views', $$('#screen-dashboard .side-link[data-view]').length === 7);

  // ---- Protected routes and the organisation guard ----
  w.location.hash = '#org/overview';
  await waitFor(() => w.location.hash === '#dashboard/overview', 'personal redirected away from organisation area');
  check('a personal account cannot open the organisation area', activeScreen() === 'screen-dashboard');

  // ---- Report flow validation ----
  w.location.hash = '#report';
  await waitFor(() => $$('#f-org option').length === 2, 'organisations in the report form');
  check('report step 1 is shown first', !$('#report-step-1').hidden && $('#report-step-2').hidden);
  click('[data-action="report-next-1"]');
  check('step 1 requires an organisation', !$('#report-error-1').hidden && /organisation/.test($('#report-error-1').textContent));
  setVal('#f-org', ORG.id);
  setVal('#f-category', 'Bullying');
  click('[data-action="report-next-1"]');
  check('step 2 opens once step 1 is valid', !$('#report-step-2').hidden);
  click('[data-action="report-next-2"]');
  check('step 2 requires a title', !$('#report-error-2').hidden && /title/.test($('#report-error-2').textContent));
  setVal('#f-title', 'Test report from smoke test');
  setVal('#f-desc', 'Dummy description for the smoke test. Nothing real.');
  setVal('#f-loc', 'Library');
  // Evidence is chosen on step 2. An executable is refused on the client and is never sent.
  attachFile('#f-file', 'setup.exe', 'application/octet-stream', 'MZ');
  check('report refuses an executable on the client with a clear message', /PDF, JPG, PNG or TXT/.test($('#report-error-2').textContent), $('#report-error-2').textContent);
  check('a refused file is not kept for upload', w.document.getElementById('fileLabel').textContent === 'Click to choose a file');
  attachFile('#f-file', 'evidence-notes.txt', 'text/plain', 'Dated notes for the smoke test.');
  check('an accepted file is named on the step', w.document.getElementById('fileLabel').textContent === 'evidence-notes.txt');
  click('[data-action="report-next-2"]');
  check('review step shows the entered title', !$('#report-step-3').hidden && $('#r-title').textContent === 'Test report from smoke test');
  check('review step shows the optional location', $('#r-when').textContent.includes('Library'));
  check('review step lists the accepted evidence', $('#r-file').textContent.includes('evidence-notes.txt'));
  click('#btn-submit-report');
  await sleep(30);
  check('submitting without consent is refused and nothing is created', !$('#report-error-3').hidden && !requests.some((r) => r === 'POST /api/personal/cases'));
  $('#f-consent').checked = true;

  click('#btn-submit-report');
  await waitFor(() => !$('#report-success').hidden, 'report success');
  const newCaseId = $('#new-case-id').textContent;
  check('report success shows a case ID in UUID form', /^[0-9a-f-]{36}$/.test(newCaseId), newCaseId);
  check('the evidence upload was sent with the case', requests.some((r) => r.endsWith(`/api/personal/cases/${newCaseId}/evidence`)));

  // ---- Track case ----
  setVal('#track-input', 'not-a-case');
  click('#btn-track');
  check('track refuses something that is not a case ID', !$('#track-error').hidden);
  w.location.hash = `#dashboard/track/${CASE_IDS[0]}`;
  await waitFor(() => !$('#track-result').hidden, 'track result');
  check('track shows the case title', $('#tr-title').textContent === 'Corridor incident');
  check('track shows exactly five lifecycle stages', $$('#tr-lifecycle .tl-item').length === 5);
  check('track marks exactly one current stage', $$('#tr-lifecycle .tl-item.current').length === 1 && $('#tr-lifecycle .tl-item.current strong').textContent === 'Under review');
  check('track lists the evidence file with a download control', !!$('#tr-evidence [data-download]'));

  // ---- Evidence upload on the Track Case page ----
  w.location.hash = `#dashboard/track/${CASE_IDS[0]}`;
  await waitFor(() => !$('#track-result').hidden, 'track page for evidence');
  const before = $$('#tr-evidence li').length;
  attachFile('#tr-file', 'track-note.pdf', 'application/pdf', '%PDF-1.4 smoke');
  check('a PDF chosen on the track page is named on the upload control', w.document.getElementById('tr-file-label').textContent === 'track-note.pdf');
  click('#btn-upload-evidence');
  await waitFor(() => $$('#tr-evidence li').length === before + 1, 'evidence list updated');
  check('uploading evidence adds it to the case evidence list', $$('#tr-evidence li').length === before + 1);
  attachFile('#tr-file', 'malware.exe', 'application/octet-stream', 'MZ');
  click('#btn-upload-evidence');
  await waitFor(() => !$('#tr-evidence-error').hidden, 'refused evidence on track page');
  check('an executable on the track page is refused with a message and not uploaded', /Only PDF, JPG, PNG and TXT/.test($('#tr-evidence-error').textContent) && $$('#tr-evidence li').length === before + 1);

  // ---- Messaging ----
  w.location.hash = `#dashboard/messages/${CASE_IDS[0]}`;
  await waitFor(() => $$('#chat-body .bubble').length >= 1, 'conversation loaded');
  setVal('#chat-in', 'Thank you for the update.');
  submit('#chat-form');
  await waitFor(() => $('#chat-body').textContent.includes('Thank you for the update.'), 'own message shown');
  check('the reporter message appears on the reporter side', $$('#chat-body .bubble-row.mine .bubble').some((b) => b.textContent === 'Thank you for the update.'));
  check('the message box is cleared after sending', $('#chat-in').value === '');

  // ---- User text is rendered as text, never as HTML ----
  // The mock stores exactly what the user typed. If the UI ever used innerHTML, an <img> would appear below.
  const hostile = '<img src="x" alt="attack-title"><b>bold</b>';
  const created = backend.handle({ method: 'POST', path: '/api/personal/cases', headers: { 'x-csrf-token': backend.CSRF },
    body: JSON.stringify({ title: hostile, category: 'Other', urgency: 'LOW', description: 'Dummy text.', organisationId: ORG.id, consentShared: true }) });
  const hostileId = JSON.parse(created.body).caseId;
  w.location.hash = `#dashboard/track/${hostileId}`;
  await waitFor(() => $('#tr-title').textContent === hostile, 'hostile title rendered as text');
  check('a hostile case title is shown as literal text', $('#tr-title').textContent === hostile);
  check('a hostile case title creates no elements', $('#tr-title').querySelector('img, b') === null);
  w.location.hash = `#dashboard/messages/${hostileId}`;
  await waitFor(() => $('#chat-title').textContent === hostile, 'hostile title in chat header');
  check('a hostile title in the conversation header creates no elements', $('#chat-title').querySelector('img, b') === null && w.document.querySelectorAll('img[alt="attack-title"]').length === 0);
  backend.handle({ method: 'POST', path: `/api/org/cases/${hostileId}/messages`, headers: { 'x-csrf-token': backend.CSRF }, body: JSON.stringify({ body: hostile }) });
  w.location.hash = `#dashboard/messages/${hostileId}`;
  await sleep(20);
  setVal('#chat-in', 'ok');
  submit('#chat-form');
  await waitFor(() => $('#chat-body').textContent.includes('ok'), 'chat re-rendered');
  check('a hostile chat message creates no elements', w.document.querySelectorAll('#chat-body img[alt="attack-title"]').length === 0);

  // ---- Logout and the sign-in ID never persists in the page ----
  click('#screen-dashboard .sidebar [data-action="logout"]');
  await waitFor(() => w.location.hash === '#landing', 'landing after logout');
  w.location.hash = '#dashboard/overview';
  await waitFor(() => w.location.hash === '#login', 'protected route after logout');
  check('after logout a protected route sends the user to sign in', activeScreen() === 'screen-login');

  // ---- Account creation with the save-your-ID gate ----
  w.location.hash = '#create';
  await waitFor(() => activeScreen() === 'screen-create', 'create screen');
  click('#btn-generate');
  await waitFor(() => activeScreen() === 'screen-reveal', 'reveal screen');
  check('the reveal screen shows the sign-in ID once', $('#reveal-id').value === PERSONAL_ID);
  check('continue is disabled until the ID is acknowledged', $('#btn-reveal-continue').disabled === true);
  $('#reveal-ack').checked = true;
  $('#reveal-ack').dispatchEvent(new w.Event('change', { bubbles: true }));
  check('continue is enabled after acknowledgement', $('#btn-reveal-continue').disabled === false);
  click('#btn-reveal-continue');
  await waitFor(() => w.location.hash === '#dashboard/overview', 'workspace after reveal');
  check('the ID is removed from the page after continuing', $('#reveal-id').value === '');

  click('#screen-dashboard .sidebar [data-action="logout"]');
  await waitFor(() => w.location.hash === '#landing', 'landing after second logout');
  w.location.hash = '#reveal';
  await waitFor(() => w.location.hash === '#landing', 'reveal without pending ID redirects');
  check('the reveal screen is not reachable without a pending ID', activeScreen() === 'screen-landing');

  // ---- Organisation: register, invite, manage ----
  w.location.hash = '#org-register';
  await waitFor(() => activeScreen() === 'screen-org-register', 'org register screen');
  setVal('#org-name', '  ');
  submit('#form-org-register');
  check('organisation name is required', !$('#org-register-error').hidden);
  setVal('#org-name', 'Riverside Community Centre');
  submit('#form-org-register');
  await waitFor(() => activeScreen() === 'screen-reveal', 'organisation ID reveal');
  $('#reveal-ack').checked = true;
  $('#reveal-ack').dispatchEvent(new w.Event('change', { bubbles: true }));
  click('#btn-reveal-continue');
  await waitFor(() => w.location.hash === '#org/overview', 'organisation overview');
  await waitFor(() => !$('#invite-card').hidden, 'invitation card for an administrator');
  check('admin sees the invitation card', !$('#invite-card').hidden);
  click('#btn-invite');
  await waitFor(() => !$('#invite-result').hidden, 'invitation code');
  check('invitation code is shown with an expiry', /^SEJ-/.test($('#invite-code').textContent) && /Expires/.test($('#invite-expiry').textContent));

  w.location.hash = '#org/inbox';
  await waitFor(() => $$('#inbox-body tr.clickable').length >= 3, 'inbox rows');
  check('the inbox lists the two seeded cases and the report filed earlier in this run', $$('#inbox-body tr.clickable').length >= 3);
  check('the inbox shows the report the reporter just filed', $('#inbox-body').textContent.includes('Test report from smoke test'));
  setVal('#inbox-search', 'corridor');
  check('inbox search narrows the list', $$('#inbox-body tr.clickable').length === 1);
  setVal('#inbox-search', '');

  w.location.hash = `#org/case/${CASE_IDS[0]}`;
  await waitFor(() => $('#oc-title').textContent === 'Corridor incident', 'organisation case');
  check('the case shows the reported description', $('#oc-desc').textContent.includes('Dummy description'));
  check('the reporter message appears in the organisation conversation', $$('#oc-chat .bubble').length >= 1);
  check('the status selector starts on the current stage', $('#oc-status').value === 'UNDER_REVIEW');
  click('#btn-oc-save');
  await waitFor(() => !$('#oc-error').hidden, 'empty update refused');
  check('an empty update is refused without a request', /Change a field/.test($('#oc-error').textContent));
  $('#oc-status').value = 'IN_PROGRESS';
  setVal('#oc-note', 'We have arranged a meeting with the reporter.');
  click('#btn-oc-save');
  await waitFor(() => /In progress/.test($('#oc-status-pill').textContent), 'status updated');
  check('the status pill reflects the saved stage', $('#oc-status-pill').textContent === 'In progress');
  check('the action note appears in the timeline', $('#oc-timeline').textContent.includes('arranged a meeting'));
  check('the note box is cleared after saving', $('#oc-note').value === '');

  setVal('#oc-reply', 'We will follow up shortly.');
  submit('#oc-chat-form');
  await waitFor(() => $('#oc-chat').textContent.includes('We will follow up shortly.'), 'organisation reply shown');
  check('an organisation reply appears on the responder side', $$('#oc-chat .bubble-row.mine .bubble').some((b) => b.textContent === 'We will follow up shortly.'));

  // ---- User text is rendered as text, never as HTML ----
  click('#screen-org .sidebar [data-action="logout"]');
  await waitFor(() => w.location.hash === '#landing', 'landing after organisation logout');
  check('organisation logout clears the session', w.document.body.innerHTML.length > 0);

  // ---- Organisation admin recovery and the controls that were dead ----
  w.location.hash = '#org-recover';
  await waitFor(() => activeScreen() === 'screen-org-recover', 'recovery screen');
  submit('#form-org-recover');
  check('recovery needs both the organisation code and the recovery key', !$('#org-recover-error').hidden && /Enter both/.test($('#org-recover-error').textContent));
  setVal('#org-recovery-org-code', 'SE-ORG-TEST');
  setVal('#org-recovery-key', 'SER-wrong');
  submit('#form-org-recover');
  await waitFor(() => !$('#org-recover-error').hidden && /Recovery failed/.test($('#org-recover-error').textContent), 'recovery refused');
  check('a wrong recovery key is refused with a clear message', /Recovery failed/.test($('#org-recover-error').textContent));
  check('a refused recovery leaves the key field empty or as typed, and no session is created', w.location.hash === '#org-recover');
  setVal('#org-recovery-key', backend.RECOVERY_CODE_FOR_TEST);
  submit('#form-org-recover');
  // A successful recovery creates a new administrator sign-in ID, so the one-time reveal comes first.
  await waitFor(() => activeScreen() === 'screen-reveal', 'new administrator ID revealed after recovery');
  check('a correct organisation code and recovery key create a new administrator ID to save', $('#reveal-id').value === ORG_ID);
  $('#reveal-ack').checked = true;
  $('#reveal-ack').dispatchEvent(new w.Event('change', { bubbles: true }));
  click('#btn-reveal-continue');
  await waitFor(() => w.location.hash === '#org/overview', 'recovered into the organisation overview');
  check('after saving the new ID, the administrator reaches the organisation overview', w.location.hash === '#org/overview');
  await waitFor(() => !$('#invite-card').hidden, 'administrator controls after recovery');
  click('#btn-generate-recovery');
  await waitFor(() => !$('#org-recovery-result').hidden, 'recovery key generated');
  check('Generate recovery key now works and shows the one-time key', /^SER-/.test($('#org-recovery-code-value').textContent));
  click('#btn-copy-dashboard-recovery');
  await waitFor(() => !w.document.getElementById('toast').hidden, 'copy recovery key feedback');
  check('Copy recovery key now gives feedback', !w.document.getElementById('toast').hidden);
  click('#btn-invite');
  await waitFor(() => !$('#invite-result').hidden, 'invitation code for copy test');
  w.document.getElementById('toast').hidden = true;
  click('#btn-copy-invite');
  await waitFor(() => !w.document.getElementById('toast').hidden, 'copy invitation feedback');
  check('Copy code on an invitation now gives feedback', !w.document.getElementById('toast').hidden);
  click('#screen-org .sidebar [data-action="logout"]');
  await waitFor(() => w.location.hash === '#landing', 'landing after recovery session');

  console.log(`\n${passed} passed, ${failures.length} failed`);
  if (runtimeErrors.length) console.log('RUNTIME ERRORS:\n  ' + runtimeErrors.join('\n  '));
  const ok = failures.length === 0 && runtimeErrors.length === 0;
  console.log(ok ? 'SMOKE TESTS PASSED' : 'SMOKE TESTS FAILED');
  process.exit(ok ? 0 : 1);
}

main().catch((error) => {
  console.error('HARNESS ERROR:', error.message);
  console.error('Active screen:', activeScreen(), 'hash:', w.location.hash);
  if (runtimeErrors.length) console.error('Runtime errors:\n  ' + runtimeErrors.join('\n  '));
  process.exit(2);
});
