/* SilentEcho frontend. Vanilla JavaScript; no framework, no build step.
 * All user-supplied text is written with textContent, never innerHTML.
 * The backend is reached through API (js/config.js). In production this is the same origin,
 * and Vercel rewrites /api/* to the Railway backend, so session cookies stay first-party. */
(() => {
  'use strict';

  const API = String(window.SILENTECHO_API_BASE || 'http://localhost:8080').replace(/\/$/, '');
  const CATEGORIES = ['Bullying', 'Harassment', 'Cyberbullying', 'Discrimination', 'Emotional Distress',
    'Violence/Threat', 'Theft', 'Facilities', 'Other'];
  const STATUSES = ['SUBMITTED', 'UNDER_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
  const OPEN = ['SUBMITTED', 'UNDER_REVIEW', 'IN_PROGRESS'];
  const DONE = ['RESOLVED', 'CLOSED'];
  const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  const STATUS_LABEL = { SUBMITTED: 'Submitted', UNDER_REVIEW: 'Under review', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', CLOSED: 'Closed' };
  const STATUS_CLASS = { SUBMITTED: 'status-submitted', UNDER_REVIEW: 'status-review', IN_PROGRESS: 'status-progress', RESOLVED: 'status-resolved', CLOSED: 'status-closed' };
  const PRIORITY_LABEL = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' };
  const ORG_TYPE_LABEL = { EDUCATIONAL_INSTITUTION: 'Educational institution', COMPANY: 'Company', NON_PROFIT: 'Non-profit', COMMUNITY: 'Community', OTHER: 'Other' };
  const EVENT_LABEL = { SUBMITTED: 'Report submitted', STATUS_CHANGED: 'Status updated', PRIORITY_CHANGED: 'Urgency updated',
    CATEGORY_CHANGED: 'Category updated', ACTION_NOTE: 'Update from the team', EVIDENCE_ADDED: 'Evidence added' };
  const SCREENS = { landing: 'screen-landing', how: 'screen-how', about: 'screen-about', resources: 'screen-resources',
    login: 'screen-login', create: 'screen-create', 'org-register': 'screen-org-register', 'org-join': 'screen-org-join', 'org-recover': 'screen-org-recover',
    reveal: 'screen-reveal', report: 'screen-report', dashboard: 'screen-dashboard', org: 'screen-org' };
  const DASH_VIEWS = { overview: 'dash-overview', cases: 'dash-cases', track: 'dash-track', messages: 'dash-messages', resources: 'dash-resources', settings: 'dash-settings' };
  const ORG_VIEWS = { overview: 'org-overview', inbox: 'org-inbox', case: 'org-case', team: 'org-team' };
  const SIGN_IN_ID = /^SE-[A-Za-z0-9_-]{43}$/;
  const MAX_EVIDENCE_BYTES = 2 * 1024 * 1024;
  const EVIDENCE_TYPES = /\.(pdf|jpe?g|png|txt)$/i;

  const state = {
    csrf: null, csrfHeader: 'X-CSRF-TOKEN', session: null, pending: null, flash: null,
    loginWorkspace: 'PERSONAL', cases: [], inbox: [], reportFile: null, reportStep: 1,
    current: null, chatCaseId: null, orgCaseId: null, trackCaseId: null
  };

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  /* ---------- DOM and formatting helpers ---------- */
  function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
      if (value === undefined || value === null || value === false) continue;
      if (key === 'class') node.className = value;
      else node.setAttribute(key, value === true ? '' : String(value));
    }
    for (const child of children) {
      if (child === undefined || child === null || child === false) continue;
      node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
  }

  /* Static icon markup from the original design (no user data is interpolated here). */
  function docIcon() {
    const span = document.createElement('span');
    span.innerHTML = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none"><path d="M4 19V5a2 2 0 0 1 2-2h9l5 5v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" stroke="currentColor" stroke-width="1.5"/></svg>';
    return span.firstChild;
  }

  function statusPill(status) {
    return el('span', { class: `status-pill ${STATUS_CLASS[status] || ''}` }, STATUS_LABEL[status] || status);
  }

  function shortRef(caseId) {
    return caseId ? `SE-${caseId.slice(0, 8).toUpperCase()}` : '';
  }

  function fmtDate(value) {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime()) ? date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
  }

  function fmtDateTime(value) {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime()) ? date.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';
  }

  function fmtSize(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  let toastTimer = null;
  function toast(message) {
    const node = $('#toast');
    node.textContent = message;
    node.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { node.hidden = true; }, 4200);
  }

  function showError(node, message) {
    if (!node) return toast(message);
    node.textContent = message;
    node.hidden = false;
  }

  function hideError(node) {
    if (node) { node.textContent = ''; node.hidden = true; }
  }

  function setBusy(button, busy, label) {
    if (!button) return;
    button.disabled = busy;
    if (label) button.textContent = busy ? 'Please wait…' : label;
  }

  async function copyText(text, doneMessage) {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const area = el('textarea', { readonly: true });
        area.value = text;
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.append(area);
        area.select();
        document.execCommand('copy');
        area.remove();
      }
      toast(doneMessage);
    } catch {
      toast('Copy did not work. Select the text and copy it manually.');
    }
  }

  function saveBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = el('a', { href: url, download: filename });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function filenameFromDisposition(header) {
    const match = header && header.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
    if (!match) return null;
    try { return decodeURIComponent(match[1]); } catch { return match[1]; }
  }

  /* ---------- API client ---------- */
  async function api(path, { method = 'GET', body, raw = false } = {}) {
    const headers = { Accept: 'application/json' };
    let payload;
    if (body instanceof FormData) {
      payload = body;
    } else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    if (method !== 'GET' && state.csrf) headers[state.csrfHeader] = state.csrf;

    let response;
    try {
      response = await fetch(API + path, { method, headers, body: payload, credentials: 'include' });
    } catch {
      throw new Error('Could not reach SilentEcho. Check your connection and try again.');
    }
    if (raw) return response;
    if (response.status === 204) return null;

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      let message = data.message || `Request failed (${response.status}).`;
      if (response.status === 403 && !data.message) message = 'Your request was blocked. Refresh the page and try again.';
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  async function loadCsrf() {
    try {
      const data = await api('/api/auth/csrf');
      state.csrf = data.token;
      state.csrfHeader = data.headerName || 'X-CSRF-TOKEN';
    } catch {
      state.csrf = null;
    }
  }

  async function refreshSession() {
    try {
      const me = await api('/api/auth/me');
      state.session = me.authenticated ? me : null;
    } catch {
      state.session = null;
    }
  }

  const isOrg = () => Boolean(state.session && state.session.accountType === 'ORGANISATION');
  const isPersonal = () => Boolean(state.session && state.session.accountType === 'PERSONAL');

  function sessionEnded(message) {
    state.session = null;
    state.current = null;
    state.chatCaseId = null;
    state.orgCaseId = null;
    toast(message || 'Your session has ended. Sign in again with your private sign-in ID.');
    go('login');
  }

  function handleError(error, fallbackNode, columnCount) {
    if (error.status === 401) return sessionEnded();
    const message = error.message || 'Something went wrong. Please try again.';
    if (fallbackNode) {
      fallbackNode.replaceChildren(el('tr', {}, el('td', { colspan: columnCount, class: 'muted-cell' }, message)));
    }
    toast(message);
  }

  /* ---------- Routing ---------- */
  function go(path) {
    const target = `#${path}`;
    if (location.hash === target) route();
    else location.hash = target;
  }

  function parseHash() {
    return location.hash.replace(/^#\/?/, '').split('/').filter(Boolean).map((part) => {
      try { return decodeURIComponent(part); } catch { return part; }
    });
  }

  function showScreen(name) {
    const target = SCREENS[name];
    $$('.screen').forEach((section) => section.classList.toggle('active', section.id === target));
    window.scrollTo(0, 0);
  }

  function setSideActive(screenId, view) {
    $$(`#${screenId} .side-link[data-view]`).forEach((link) => {
      link.classList.toggle('active', link.dataset.view === view);
    });
  }

  function showDashView(map, view, screenId) {
    Object.values(map).forEach((id) => { const node = document.getElementById(id); if (node) node.hidden = true; });
    const node = document.getElementById(map[view]);
    if (node) node.hidden = false;
    setSideActive(screenId, view);
  }

  async function route() {
    const [area = '', sub = '', id = ''] = parseHash();

    if (area === '' || area === 'landing') return showScreen('landing');
    if (area === 'how' || area === 'about' || area === 'resources') return showScreen(area);
    if (area === 'login') {
      if (state.session) return go(isOrg() ? 'org/overview' : 'dashboard/overview');
      state.loginWorkspace = sub === 'org' ? 'ORGANISATION' : 'PERSONAL';
      renderLoginTabs();
      if (state.flash) { showError($('#login-error'), state.flash); state.flash = null; }
      return showScreen('login');
    }
    if (area === 'create') return showScreen('create');
    if (area === 'org-register') return showScreen('org-register');
    if (area === 'org-join') return showScreen('org-join');
    if (area === 'org-recover') return showScreen('org-recover');
    if (area === 'reveal') {
      if (!state.pending) return go('landing');
      $('#reveal-id').value = state.pending.signInId;
      const orgCodeWrap = $('#reveal-org-code-wrap');
      const orgRef = (state.pending.organisations || [])[0];
      orgCodeWrap.hidden = !orgRef || !orgRef.organisationCode;
      if (orgRef && orgRef.organisationCode) {
        $('#reveal-org-name').textContent = orgRef.name;
        $('#reveal-org-code').value = orgRef.organisationCode;
      } else {
        $('#reveal-org-name').textContent = 'Organisation reference';
        $('#reveal-org-code').value = '';
      }
      $('#reveal-recovery-wrap').hidden = !state.pending.organisationRecoveryCode;
      $('#reveal-recovery-code').value = state.pending.organisationRecoveryCode || '';
      $('#reveal-ack').checked = false;
      $('#btn-reveal-continue').disabled = true;
      return showScreen('reveal');
    }
    if (area === 'report') {
      if (!isPersonal()) return requireSignIn(isOrg() ? 'org/overview' : 'login');
      showScreen('report');
      return startReport();
    }
    if (area === 'dashboard') {
      if (!isPersonal()) return requireSignIn(isOrg() ? 'org/overview' : 'login');
      showScreen('dashboard');
      const view = DASH_VIEWS[sub] ? sub : 'overview';
      showDashView(DASH_VIEWS, view, 'screen-dashboard');
      if (view === 'overview') return loadPersonalCases('overview');
      if (view === 'cases') return loadPersonalCases('cases');
      if (view === 'track') return sub === 'track' && id ? openTrack(id) : null;
      if (view === 'messages') return loadPersonalCases('messages', id);
      return undefined;
    }
    if (area === 'org') {
      if (!isOrg()) return requireSignIn(isPersonal() ? 'dashboard/overview' : 'login');
      showScreen('org');
      const view = sub === 'case' ? 'case' : (ORG_VIEWS[sub] ? sub : 'overview');
      showDashView(ORG_VIEWS, view, 'screen-org');
      renderOrgSidebarLabels();
      if (view === 'overview') return loadOrgOverview();
      if (view === 'inbox') return loadOrgInbox();
      if (view === 'team') return renderTeam();
      if (view === 'case' && id) return openOrgCase(id);
      return undefined;
    }
    return go('landing');
  }

  function requireSignIn(fallback) {
    if (!state.session) {
      state.flash = 'Sign in to continue.';
      return go('login');
    }
    return go(fallback);
  }

  /* ---------- Auth screens ---------- */
  function renderLoginTabs() {
    const org = state.loginWorkspace === 'ORGANISATION';
    $$('.auth-tab').forEach((tab) => {
      const active = tab.dataset.workspace === state.loginWorkspace;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', String(active));
    });
    $('#login-title').textContent = org ? 'Welcome back, team' : 'Welcome Back';
    $('#login-sub').textContent = org
      ? 'Enter your private sign-in ID to access your team\u2019s workspace. Your organisation membership controls which cases you can review.'
      : 'Enter the private sign-in ID you saved when you created your account. Your complaints and their progress will be waiting for you.';
    $('#login-switch-personal').hidden = org;
    $('#login-switch-org').hidden = !org;
    $('#login-id').placeholder = 'SE-…';
    hideError($('#login-error'));
  }

  async function signIn(event) {
    event.preventDefault();
    const input = $('#login-id');
    const errorNode = $('#login-error');
    hideError(errorNode);
    const signInId = input.value.trim();
    if (!SIGN_IN_ID.test(signInId)) {
      showError(errorNode, 'Enter the full private sign-in ID you saved. It starts with SE- and is 46 characters long.');
      return;
    }
    const button = $('#btn-login');
    setBusy(button, true, 'Sign in securely');
    try {
      await api('/api/auth/login', { method: 'POST', body: { signInId, workspace: state.loginWorkspace } });
      input.value = '';
      input.type = 'password';
      $('[data-action="toggle-id"][data-target="login-id"]').textContent = 'Show';
      await loadCsrf();
      await refreshSession();
      toast('Signed in.');
      go(isOrg() ? 'org/overview' : 'dashboard/overview');
    } catch (error) {
      input.value = '';
      showError(errorNode, error.message);
    } finally {
      setBusy(button, false, 'Sign in securely');
    }
  }

  async function afterAccountCreated(data, destination) {
    state.pending = { signInId: data.signInId, destination, organisations: data.organisations || [],
      organisationRecoveryCode: data.organisationRecoveryCode || null };
    await loadCsrf();
    await refreshSession();
    go('reveal');
  }

  function downloadSignInId(signInId) {
    const orgRef = state.pending && (state.pending.organisations || [])[0];
    const lines = ['SilentEcho private sign-in ID', '', signInId, '',
      'Keep this secret. Anyone who has it can access your account.',
      'SilentEcho cannot retrieve this ID for you if it is lost.'];
    if (orgRef && orgRef.organisationCode) {
      lines.push('', `Organisation: ${orgRef.name}`, `Organisation code: ${orgRef.organisationCode}`,
        'The organisation code is a stable public reference, not a password or login credential.');
    }
    if (state.pending && state.pending.organisationRecoveryCode) {
      lines.push('', 'One-time organisation administrator recovery key:', state.pending.organisationRecoveryCode,
        'Keep this separate and secure. It can create a replacement administrator account if the Sign-in ID is lost.');
    }
    lines.push('');
    saveBlob(new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' }), 'silentecho-sign-in-details.txt');
  }

  /* ---------- Personal dashboard ---------- */
  function filterCases(cases, query, status) {
    const needle = query.trim().toLowerCase();
    return cases.filter((c) => {
      if (status && c.status !== status) return false;
      if (!needle) return true;
      return (c.title || '').toLowerCase().includes(needle) || (c.caseId || '').toLowerCase().includes(needle);
    });
  }

  function fillTable(tbody, rows, columns, emptyText, onRowClick) {
    tbody.replaceChildren();
    if (!rows.length) {
      tbody.append(el('tr', {}, el('td', { colspan: columns.length, class: 'muted-cell' }, emptyText)));
      return;
    }
    for (const item of rows) {
      const row = el('tr', onRowClick ? { class: 'clickable', tabindex: 0 } : {});
      if (onRowClick) {
        row.addEventListener('click', () => onRowClick(item));
        row.addEventListener('keydown', (event) => { if (event.key === 'Enter') onRowClick(item); });
      }
      for (const column of columns) row.append(el('td', {}, column(item)));
      tbody.append(row);
    }
  }

  const COL = {
    ref: (c) => el('span', { class: 'mono' }, shortRef(c.caseId)),
    title: (c) => c.title,
    category: (c) => c.category,
    urgency: (c) => el('span', { class: `urgency urgency-${c.urgency}` }, PRIORITY_LABEL[c.urgency] || c.urgency),
    status: (c) => statusPill(c.status),
    updated: (c) => fmtDate(c.updatedAt)
  };

  async function loadPersonalCases(view, openCaseId) {
    try {
      state.cases = await api('/api/personal/cases');
      const cases = state.cases;
      $('#stat-total').textContent = String(cases.length);
      $('#stat-review').textContent = String(cases.filter((c) => c.status === 'SUBMITTED' || c.status === 'UNDER_REVIEW').length);
      $('#stat-progress').textContent = String(cases.filter((c) => c.status === 'IN_PROGRESS').length);
      $('#stat-resolved').textContent = String(cases.filter((c) => DONE.includes(c.status)).length);
      fillTable($('#recent-body'), cases.slice(0, 5),
        [COL.ref, COL.category, COL.status, COL.updated],
        'You have not submitted any concerns yet. When you do, it will appear here.',
        (c) => go(`dashboard/track/${c.caseId}`));
      renderCasesTable();
      renderMessageList();
      if (view === 'messages' && openCaseId) await openChat(openCaseId);
      if (view === 'messages' && !openCaseId && !state.chatCaseId && cases.length) await openChat(cases[0].caseId);
    } catch (error) {
      handleError(error, $('#recent-body'), 4);
    }
  }

  function renderCasesTable() {
    const filtered = filterCases(state.cases, $('#cases-search').value, $('#cases-filter').value);
    fillTable($('#cases-body'), filtered,
      [COL.ref, COL.title, COL.category, COL.status, COL.updated],
      state.cases.length ? 'No cases match your search.' : 'You have not submitted any concerns yet.',
      (c) => go(`dashboard/track/${c.caseId}`));
  }

  function renderMessageList() {
    const list = $('#msg-list-items');
    const query = $('#msg-search').value.trim().toLowerCase();
    const items = state.cases.filter((c) => !query || (c.title || '').toLowerCase().includes(query) || shortRef(c.caseId).toLowerCase().includes(query));
    list.replaceChildren();
    if (!items.length) {
      list.append(el('p', { class: 'muted-copy small-pad' }, state.cases.length ? 'No cases match your search.' : 'No cases yet. Messages appear here once you have submitted a concern.'));
      return;
    }
    for (const c of items) {
      const active = c.caseId === state.chatCaseId;
      list.append(el('div', { class: `msg-item${active ? ' active' : ''}`, tabindex: 0, 'data-case-id': c.caseId },
        el('div', { class: 'ic', 'aria-hidden': 'true' }, docIcon()),
        el('div', { class: 'body' },
          el('div', { class: 'top' }, el('span', {}, c.title || 'Case'), el('span', { class: 'time' }, fmtDate(c.updatedAt))),
          el('div', { class: 'prev' }, `${STATUS_LABEL[c.status] || c.status} · ${c.organisationName || ''}`))));
    }
  }

  async function openChat(caseId) {
    state.chatCaseId = caseId;
    renderMessageList();
    try {
      state.current = await api(`/api/personal/cases/${encodeURIComponent(caseId)}`);
      const detail = state.current;
      $('#chat-title').textContent = detail.title;
      $('#chat-status').textContent = `${STATUS_LABEL[detail.status] || detail.status} · ${detail.organisationName}`;
      $('#chat-track').href = `#dashboard/track/${detail.caseId}`;
      renderChat('#chat-body', detail.messages, 'personal');
    } catch (error) {
      handleError(error);
    }
  }

  /* Renders into the conversation panel named by selector, keeping that panel's own secure banner. */
  function renderChat(selector, messages, viewer) {
    const body = $(selector);
    const banner = body.querySelector('.secure-banner');
    body.replaceChildren();
    if (banner) body.append(banner);
    if (!messages.length) {
      body.append(el('p', { class: 'muted-copy center' }, 'No messages yet. Messages you send here are encrypted on the server.'));
      return;
    }
    for (const message of messages) {
      const fromReporter = message.sender === 'REPORTER';
      const mine = viewer === 'personal' ? fromReporter : !fromReporter;
      const time = fmtDateTime(message.sentAt);
      const bubble = el('div', { class: 'bubble' }, message.body);
      if (mine) {
        body.append(el('div', { class: 'bubble-row mine' }, el('div', {}, bubble, el('div', { class: 'bubble-time' }, time))));
      } else {
        body.append(el('div', { class: 'bubble-row' },
          el('div', { class: 'ic', 'aria-hidden': 'true' }, 'SE'),
          el('div', {}, bubble, el('div', { class: 'bubble-time' }, time))));
      }
    }
    body.scrollTop = body.scrollHeight;
  }

  function renderLifecycle(container, status) {
    const index = STATUSES.indexOf(status);
    container.replaceChildren(...STATUSES.map((stage, i) => {
      const cls = i < index ? 'tl-item done' : i === index ? 'tl-item current' : 'tl-item faded';
      const note = i < index ? 'Completed' : i === index ? 'Current stage' : 'Upcoming';
      return el('div', { class: cls }, el('div', { class: 'tl-dot' }), el('div', { class: 'txt' }, el('strong', {}, STATUS_LABEL[stage]), el('span', {}, note)));
    }));
  }

  function timelineText(entry) {
    switch (entry.type) {
      case 'STATUS_CHANGED': return `Status is now ${STATUS_LABEL[entry.status] || entry.status}.`;
      case 'PRIORITY_CHANGED': return `Urgency is now ${PRIORITY_LABEL[entry.urgency] || entry.urgency}.`;
      case 'CATEGORY_CHANGED': return `Category is now ${entry.category}.`;
      case 'ACTION_NOTE': return entry.note || '';
      default: return '';
    }
  }

  function renderActivity(container, entries) {
    container.replaceChildren();
    if (!entries.length) { container.append(el('p', { class: 'muted-copy' }, 'No activity yet.')); return; }
    for (const entry of entries) {
      container.append(el('div', { class: 'tl-item' }, el('div', { class: 'tl-dot' }),
        el('div', { class: 'txt' },
          el('strong', {}, EVENT_LABEL[entry.type] || entry.type),
          el('span', {}, fmtDateTime(entry.at)),
          timelineText(entry) ? el('p', { class: 'prewrap activity-note' }, timelineText(entry)) : null)));
    }
  }

  function renderEvidence(list, files, emptyText) {
    list.replaceChildren();
    if (!files.length) { list.append(el('li', { class: 'empty' }, emptyText)); return; }
    for (const file of files) {
      list.append(el('li', {},
        el('span', { class: 'file-name' }, `${file.filename} · ${fmtSize(file.sizeBytes)}`),
        el('button', { class: 'btn btn-secondary btn-sm', type: 'button', 'data-download': file.fileId }, 'Download')));
    }
  }

  async function openTrack(caseId) {
    state.trackCaseId = caseId;
    $('#track-input').value = caseId;
    hideError($('#track-error'));
    try {
      const detail = await api(`/api/personal/cases/${encodeURIComponent(caseId)}`);
      state.current = detail;
      $('#track-result').hidden = false;
      $('#tr-title').textContent = detail.title;
      $('#tr-id').textContent = `Case ID ${detail.caseId}`;
      $('#tr-meta').textContent = `${detail.category} · ${detail.organisationName} · Urgency ${PRIORITY_LABEL[detail.urgency] || detail.urgency}`;
      const pill = $('#tr-status');
      pill.className = `status-pill ${STATUS_CLASS[detail.status] || ''}`;
      pill.textContent = STATUS_LABEL[detail.status] || detail.status;
      renderLifecycle($('#tr-lifecycle'), detail.status);
      renderActivity($('#tr-activity'), detail.timeline);
      renderEvidence($('#tr-evidence'), detail.evidence, 'No evidence added yet.');
    } catch (error) {
      $('#track-result').hidden = true;
      if (error.status === 404) return showError($('#track-error'), 'This case could not be found, or it is not available to your account.');
      handleError(error);
    }
  }

  function trackFromInput() {
    const caseId = $('#track-input').value.trim();
    if (!/^[0-9a-f-]{36}$/i.test(caseId)) {
      showError($('#track-error'), 'Enter the full case ID shown when you submitted the report (it looks like 1f2e3d4c-…).');
      return;
    }
    go(`dashboard/track/${caseId}`);
  }

  /* Checks the file on the client for a fast message; the server repeats every check. Throws on failure. */
  async function sendEvidence(caseId, file) {
    if (!file) throw new Error('Choose a file first.');
    if (!EVIDENCE_TYPES.test(file.name)) throw new Error('Only PDF, JPG, PNG and TXT files are allowed.');
    if (file.size > MAX_EVIDENCE_BYTES) throw new Error('Evidence files must be 2 MB or smaller.');
    const data = new FormData();
    data.append('file', file);
    await api(`/api/personal/cases/${encodeURIComponent(caseId)}/evidence`, { method: 'POST', body: data });
  }

  async function sendPersonalMessage(event) {
    event.preventDefault();
    const input = $('#chat-in');
    const text = input.value.trim();
    if (!text || !state.chatCaseId) return;
    try {
      state.current = await api(`/api/personal/cases/${encodeURIComponent(state.chatCaseId)}/messages`, { method: 'POST', body: { body: text } });
      input.value = '';
      renderChat('#chat-body', state.current.messages, 'personal');
    } catch (error) {
      handleError(error);
    }
  }

  /* ---------- Report flow ---------- */
  function fillSelect(select, values, labelFor, selected) {
    select.replaceChildren(...values.map((value) => el('option', { value, selected: value === selected }, labelFor(value))));
  }

  async function startReport() {
    setReportStep(1);
    $('#report-success').hidden = true;
    ['report-step-1', 'report-step-2', 'report-step-3'].forEach((id) => { $('#' + id).hidden = id !== 'report-step-1'; });
    state.reportFile = null;
    $('#fileLabel').textContent = 'Click to choose a file';
    $('#f-title').value = '';
    $('#f-desc').value = '';
    $('#cc').textContent = '0';
    $('#f-date').value = '';
    $('#f-loc').value = '';
    $('#f-response').value = 'yes';
    $('#f-consent').checked = false;
    $('#f-ai').checked = false;
    $('#f-urgency').value = 'MEDIUM';
    fillSelect($('#f-category'), ['', ...CATEGORIES], (v) => v || 'Select a category', '');
    ['report-error-1', 'report-error-2', 'report-error-3'].forEach((id) => hideError($('#' + id)));
    try {
      const organisations = await api('/api/organisations');
      $('#f-org').replaceChildren(el('option', { value: '' }, organisations.length ? 'Select an organisation' : 'No organisations are registered yet'),
        ...organisations.map((o) => el('option', { value: o.id }, `${o.name} (${ORG_TYPE_LABEL[o.type] || o.type}) · ${o.organisationCode || 'registered'}`)));
    } catch (error) {
      handleError(error);
    }
  }

  function setReportStep(step) {
    state.reportStep = step;
    $$('#stepper .step').forEach((node) => {
      const n = Number(node.dataset.step);
      node.classList.toggle('done', n < step);
      node.classList.toggle('current', n === step);
    });
    [1, 2, 3].forEach((n) => { $('#report-step-' + n).hidden = n !== step; });
    window.scrollTo(0, 0);
  }

  function validateStep1() {
    const errorNode = $('#report-error-1');
    hideError(errorNode);
    if (!$('#f-org').value) return showError(errorNode, 'Choose the organisation that should receive your report.'), false;
    if (!$('#f-category').value) return showError(errorNode, 'Choose a category.'), false;
    return true;
  }

  function validateStep2() {
    const errorNode = $('#report-error-2');
    hideError(errorNode);
    if (!$('#f-title').value.trim()) return showError(errorNode, 'Add a short title for the report.'), false;
    if (!$('#f-desc').value.trim()) return showError(errorNode, 'Describe what happened, as much or as little as you feel comfortable sharing.'), false;
    return true;
  }

  function composeDescription() {
    const lines = [];
    if ($('#f-date').value) lines.push(`When: ${$('#f-date').value}`);
    if ($('#f-loc').value.trim()) lines.push(`Where: ${$('#f-loc').value.trim()}`);
    lines.push(`Response requested: ${$('#f-response').value === 'yes' ? 'yes' : 'no'}`);
    lines.push('');
    lines.push($('#f-desc').value.trim());
    return lines.join('\n');
  }

  function fillReview() {
    const orgSelect = $('#f-org');
    $('#r-org').textContent = orgSelect.options[orgSelect.selectedIndex]?.textContent || '—';
    $('#r-category').textContent = $('#f-category').value || '—';
    $('#r-urgency').textContent = PRIORITY_LABEL[$('#f-urgency').value] || '—';
    $('#r-title').textContent = $('#f-title').value.trim() || '—';
    $('#r-desc').textContent = $('#f-desc').value.trim() || '—';
    const when = [$('#f-date').value, $('#f-loc').value.trim()].filter(Boolean).join(' · ');
    $('#r-when').textContent = when || 'Not given';
    $('#r-file').textContent = state.reportFile ? `${state.reportFile.name} (${fmtSize(state.reportFile.size)})` : 'None';
  }

  async function submitReport() {
    const errorNode = $('#report-error-3');
    hideError(errorNode);
    if (!$('#f-consent').checked) return showError(errorNode, 'Please confirm the report can be shared with the chosen organisation.');
    const title = $('#f-title').value.trim();
    const category = $('#f-category').value;
    const wantsAi = $('#f-ai').checked;
    const button = $('#btn-submit-report');
    setBusy(button, true, 'Submit Report');
    try {
      const detail = await api('/api/personal/cases', {
        method: 'POST',
        body: {
          title, category, urgency: $('#f-urgency').value, description: composeDescription(),
          organisationId: $('#f-org').value, consentShared: true
        }
      });
      const notes = [];
      if (state.reportFile) {
        try {
          await sendEvidence(detail.caseId, state.reportFile);
        } catch (uploadError) {
          if (uploadError.status === 401) return sessionEnded();
          notes.push(`Your report was saved, but the evidence file was not uploaded: ${uploadError.message} You can try again from Track Case.`);
        }
      }
      showSubmitted(detail, wantsAi ? await requestAi(title, category) : null, notes);
    } catch (error) {
      if (error.status === 401) return sessionEnded();
      showError(errorNode, error.message);
    } finally {
      setBusy(button, false, 'Submit Report');
    }
  }

  async function requestAi(title, category) {
    try {
      const data = await api('/api/ai/analyze', { method: 'POST', body: { title, category, description: composeDescription() } });
      const raw = String(data.suggestion || '').replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '');
      try { return JSON.parse(raw); } catch { return { rationale: raw }; }
    } catch {
      return { unavailable: true };
    }
  }

  function showSubmitted(detail, suggestion, notes) {
    $('#report-step-3').hidden = true;
    $('#report-success').hidden = false;
    $('#new-case-id').textContent = detail.caseId;
    $('#report-success').dataset.caseId = detail.caseId;
    const box = $('#report-ai-box');
    box.replaceChildren();
    box.hidden = !suggestion && !notes.length;
    notes.forEach((note) => box.append(el('p', {}, note)));
    if (suggestion) {
      box.append(el('p', {}, el('strong', {}, 'Advisory triage suggestion')));
      if (suggestion.unavailable) {
        box.append(el('p', {}, 'AI analysis is unavailable right now. Your report was saved.'));
      } else {
        [['Suggested category', suggestion.suggestedCategory], ['Priority', suggestion.priority],
          ['Risk level', suggestion.riskLevel], ['Rationale', suggestion.rationale],
          ['Recommended next step', suggestion.recommendedNextStep]]
          .forEach(([label, value]) => { if (value) box.append(el('p', {}, el('strong', {}, `${label}: `), String(value))); });
        box.append(el('p', { class: 'muted-copy' }, 'A human responder makes the final decision.'));
      }
      box.hidden = false;
    }
    $('#report-success').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /* ---------- Organisation workspace ---------- */
  function renderOrgSidebarLabels() {
    const orgs = (state.session && state.session.organisations) || [];
    $('#org-subtitle').textContent = orgs.length
      ? `${orgs.map((o) => o.name).join(', ')}. Reporter identities stay hidden.`
      : 'You are not a member of an organisation yet. Reporter identities stay hidden.';
    $('#o-orgs').replaceChildren(...(orgs.length
      ? orgs.map((o) => el('li', {}, el('strong', {}, o.name), ` · ${o.role === 'ADMIN' ? 'Administrator' : 'Responder'} · ${o.organisationCode || ''}`))
      : [el('li', { class: 'muted-copy' }, 'No organisation yet.')]));
    const admin = orgs.filter((o) => o.role === 'ADMIN');
    $('#invite-card').hidden = admin.length === 0;
    $('#invite-org').replaceChildren(...admin.map((o) => el('option', { value: o.id }, o.name)));
    $('#invite-result').hidden = true;
  }

  function renderTeam() {
    const orgs = (state.session && state.session.organisations) || [];
    $('#team-list').replaceChildren(...(orgs.length
      ? orgs.map((o) => el('li', { class: 'team-item' }, el('strong', {}, o.name), el('span', { class: 'role-tag' }, o.role === 'ADMIN' ? 'Administrator' : 'Responder'), el('small', { class: 'muted-copy' }, o.organisationCode || '')))
      : [el('li', { class: 'muted-copy' }, 'No organisation yet.')]));
  }

  async function loadOrgInbox() {
    try {
      state.inbox = await api('/api/org/cases');
      renderInbox();
    } catch (error) {
      handleError(error, $('#inbox-body'), 6);
    }
  }

  function renderInbox() {
    const filtered = filterCases(state.inbox, $('#inbox-search').value, $('#inbox-filter').value);
    fillTable($('#inbox-body'), filtered,
      [COL.ref, COL.title, COL.category, COL.urgency, COL.status, COL.updated],
      state.inbox.length ? 'No cases match your search.' : 'No cases have been submitted to your organisations yet.',
      (c) => go(`org/case/${c.caseId}`));
  }

  async function loadOrgOverview() {
    try {
      const cases = await api('/api/org/cases');
      state.inbox = cases;
      $('#o-stat-total').textContent = String(cases.length);
      $('#o-stat-review').textContent = String(cases.filter((c) => c.status === 'SUBMITTED' || c.status === 'UNDER_REVIEW').length);
      $('#o-stat-progress').textContent = String(cases.filter((c) => c.status === 'IN_PROGRESS').length);
      $('#o-stat-resolved').textContent = String(cases.filter((c) => DONE.includes(c.status)).length);
      fillTable($('#o-recent-body'), cases.slice(0, 6),
        [COL.ref, COL.title, COL.status, COL.updated],
        'No cases have been submitted to your organisations yet.',
        (c) => go(`org/case/${c.caseId}`));
    } catch (error) {
      handleError(error, $('#o-recent-body'), 4);
    }
  }

  async function openOrgCase(caseId) {
    state.orgCaseId = caseId;
    try {
      const detail = await api(`/api/org/cases/${encodeURIComponent(caseId)}`);
      state.current = detail;
      renderOrgCase(detail);
    } catch (error) {
      if (error.status === 404) {
        toast('This case could not be found, or it is not available to your account.');
        return go('org/inbox');
      }
      handleError(error);
    }
  }

  function renderOrgCase(detail) {
    $('#oc-title').textContent = detail.title;
    $('#oc-id').textContent = `Case ID ${detail.caseId}`;
    const pill = $('#oc-status-pill');
    pill.className = `status-pill ${STATUS_CLASS[detail.status] || ''}`;
    pill.textContent = STATUS_LABEL[detail.status] || detail.status;
    $('#oc-meta').textContent = `${detail.category} · Urgency ${PRIORITY_LABEL[detail.urgency] || detail.urgency} · Filed ${fmtDate(detail.createdAt)}`;
    $('#oc-desc').textContent = detail.description;
    fillSelect($('#oc-status'), STATUSES, (s) => STATUS_LABEL[s], detail.status);
    fillSelect($('#oc-urgency'), PRIORITIES, (p) => PRIORITY_LABEL[p], detail.urgency);
    fillSelect($('#oc-category'), CATEGORIES.includes(detail.category) ? CATEGORIES : [detail.category, ...CATEGORIES], (c) => c, detail.category);
    $('#oc-note').value = '';
    hideError($('#oc-error'));
    renderChat('#oc-chat', detail.messages, 'organisation');
    renderActivity($('#oc-timeline'), detail.timeline);
    renderEvidence($('#oc-evidence'), detail.evidence, 'No evidence added yet.');
  }

  async function saveOrgCase(event) {
    event.preventDefault();
    if (!state.current) return;
    const errorNode = $('#oc-error');
    hideError(errorNode);
    const body = {};
    if ($('#oc-status').value !== state.current.status) body.status = $('#oc-status').value;
    if ($('#oc-urgency').value !== state.current.urgency) body.urgency = $('#oc-urgency').value;
    if ($('#oc-category').value !== state.current.category) body.category = $('#oc-category').value;
    const note = $('#oc-note').value.trim();
    if (note) body.actionNote = note;
    if (!Object.keys(body).length) return showError(errorNode, 'Change a field or add an update for the reporter first.');
    const button = $('#btn-oc-save');
    setBusy(button, true, 'Save update');
    try {
      state.current = await api(`/api/org/cases/${encodeURIComponent(state.current.caseId)}`, { method: 'PATCH', body });
      renderOrgCase(state.current);
      toast('Case updated. The reporter will see the change.');
    } catch (error) {
      if (error.status === 401) return sessionEnded();
      showError(errorNode, error.message);
    } finally {
      setBusy(button, false, 'Save update');
    }
  }

  async function sendOrgReply(event) {
    event.preventDefault();
    const input = $('#oc-reply');
    const text = input.value.trim();
    if (!text || !state.current) return;
    try {
      state.current = await api(`/api/org/cases/${encodeURIComponent(state.current.caseId)}/messages`, { method: 'POST', body: { body: text } });
      input.value = '';
      renderChat('#oc-chat', state.current.messages, 'organisation');
    } catch (error) {
      handleError(error);
    }
  }

  async function createInvitation() {
    const organisationId = $('#invite-org').value;
    if (!organisationId) return;
    const button = $('#btn-invite');
    setBusy(button, true, 'Generate code');
    try {
      const data = await api('/api/organisations/invitations', { method: 'POST', body: { organisationId } });
      $('#invite-code').textContent = data.code;
      $('#invite-expiry').textContent = `Expires ${fmtDateTime(data.expiresAt)}. Single use.`;
      $('#invite-result').hidden = false;
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(button, false, 'Generate code');
    }
  }

  async function createOrganisationRecoveryCode() {
    const organisationId = $('#invite-org').value;
    if (!organisationId) return;
    const button = $('#btn-generate-recovery');
    setBusy(button, true, 'Generate recovery key');
    try {
      const data = await api('/api/organisations/recovery-codes', { method: 'POST', body: { organisationId } });
      $('#org-recovery-code-value').textContent = data.recoveryCode;
      $('#org-recovery-result').hidden = false;
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(button, false, 'Generate recovery key');
    }
  }

  /* ---------- Session end and downloads ---------- */
  async function downloadEvidence(fileId) {
    if (!state.current) return;
    const base = isOrg() ? '/api/org/cases' : '/api/personal/cases';
    const path = `${base}/${encodeURIComponent(state.current.caseId)}/evidence/${encodeURIComponent(fileId)}`;
    try {
      const response = await api(path, { raw: true });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        if (response.status === 401) return sessionEnded();
        throw new Error(data.message || 'Could not download this file.');
      }
      const filename = filenameFromDisposition(response.headers.get('Content-Disposition')) || 'evidence';
      saveBlob(await response.blob(), filename);
    } catch (error) {
      toast(error.message);
    }
  }

  async function logout() {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* session may already be gone */ }
    state.session = null;
    state.current = null;
    state.pending = null;
    state.chatCaseId = null;
    state.orgCaseId = null;
    state.cases = [];
    await loadCsrf();
    toast('You have signed out.');
    go('landing');
  }

  /* ---------- Event wiring ---------- */
  function bindEvents() {
    $('#btn-nav-login').addEventListener('click', () => go('login'));
    $('#btn-report-now').addEventListener('click', reportNow);
    $$('.auth-tab').forEach((tab) => tab.addEventListener('click', () => {
      state.loginWorkspace = tab.dataset.workspace;
      renderLoginTabs();
    }));
    $('#form-login').addEventListener('submit', signIn);
    $('#go-create').addEventListener('click', () => go('create'));
    $('#go-org-register').addEventListener('click', () => go('org-register'));
    $('#go-org-join').addEventListener('click', () => go('org-join'));
    $('#go-org-recover').addEventListener('click', () => go('org-recover'));

    $('#btn-generate').addEventListener('click', async () => {
      const errorNode = $('#create-error');
      hideError(errorNode);
      const button = $('#btn-generate');
      setBusy(button, true, 'Generate my sign-in ID');
      try {
        const data = await api('/api/auth/personal/register', { method: 'POST' });
        await afterAccountCreated(data, 'dashboard/overview');
      } catch (error) {
        showError(errorNode, error.message);
      } finally {
        setBusy(button, false, 'Generate my sign-in ID');
      }
    });

    $('#form-org-register').addEventListener('submit', async (event) => {
      event.preventDefault();
      const errorNode = $('#org-register-error');
      hideError(errorNode);
      const name = $('#org-name').value.trim();
      if (!name) return showError(errorNode, 'Enter the organisation name.');
      try {
        const data = await api('/api/organisations/register', { method: 'POST', body: { name, type: $('#org-type').value } });
        await afterAccountCreated(data, 'org/overview');
      } catch (error) {
        showError(errorNode, error.message);
      }
    });

    $('#form-org-join').addEventListener('submit', async (event) => {
      event.preventDefault();
      const errorNode = $('#org-join-error');
      hideError(errorNode);
      const invitationCode = $('#org-code').value.trim();
      if (!invitationCode) return showError(errorNode, 'Enter the invitation code you were given.');
      try {
        const data = await api('/api/organisations/join', { method: 'POST', body: { invitationCode } });
        $('#org-code').value = '';
        await afterAccountCreated(data, 'org/overview');
      } catch (error) {
        showError(errorNode, error.message);
      }
    });

  $('#form-org-recover').addEventListener('submit', async (event) => {
  event.preventDefault();

  const errorNode = $('#org-recover-error');
  hideError(errorNode);

  const organisationCode = $('#org-recovery-org-code').value.trim();
  const recoveryCode = $('#org-recovery-key').value.trim();

  if (!organisationCode || !recoveryCode) {
    return showError(errorNode, 'Enter both the organisation code and recovery key.');
  }

  const button = event.submitter || $('#form-org-recover button[type="submit"]');
  setBusy(button, true, 'Recover administrator access');

  try {
    await loadCsrf();

    if (!state.csrf) {
      throw new Error('Security token unavailable. Refresh the page and try again.');
    }

    const data = await api('/api/organisations/recover', {
      method: 'POST',
      body: { organisationCode, recoveryCode }
    });

    $('#org-recovery-key').value = '';
    await afterAccountCreated(data, 'org/overview');

  } catch (error) {
    showError(errorNode, error.message);
  } finally {
    setBusy(button, false, 'Recover administrator access');
  }
});
    $('#reveal-ack').addEventListener('change', (event) => { $('#btn-reveal-continue').disabled = !event.target.checked; });
    $('#btn-copy-id').addEventListener('click', () => copyText($('#reveal-id').value, 'Sign-in ID copied. Store it somewhere safe.'));
    $('#btn-copy-org-code').addEventListener('click', () => copyText($('#reveal-org-code').value, 'Organisation code copied. This is a public reference, not a login credential.'));
    $('#btn-copy-recovery-code').addEventListener('click', () => copyText($('#reveal-recovery-code').value, 'Recovery key copied. Store it safely; it can be used only once.'));
    $('#btn-download-id').addEventListener('click', () => downloadSignInId($('#reveal-id').value));
    $('#btn-reveal-continue').addEventListener('click', () => {
      if (!state.pending) return go('landing');
      const destination = state.pending.destination;
      state.pending = null;
      $('#reveal-id').value = '';
      $('#reveal-org-code').value = '';
      $('#reveal-recovery-code').value = '';
      $('#reveal-org-code-wrap').hidden = true;
      $('#reveal-recovery-wrap').hidden = true;
      go(destination);
    });

    $('#f-desc').addEventListener('input', (event) => { $('#cc').textContent = String(event.target.value.length); });
    $('#f-file').addEventListener('change', (event) => {
      const file = event.target.files && event.target.files[0];
      state.reportFile = null;
      if (file && (!EVIDENCE_TYPES.test(file.name) || file.size > MAX_EVIDENCE_BYTES)) {
        showError($('#report-error-2'), 'Choose a PDF, JPG, PNG or TXT file of 2 MB or less.');
        event.target.value = '';
        $('#fileLabel').textContent = 'Click to choose a file';
        return;
      }
      if (file) state.reportFile = file;
      $('#fileLabel').textContent = file ? file.name : 'Click to choose a file';
      hideError($('#report-error-2'));
    });
    $('#btn-submit-report').addEventListener('click', submitReport);
    $('#btn-go-dashboard').addEventListener('click', () => go('dashboard/overview'));
    $('#btn-copy-case').addEventListener('click', () => copyText($('#new-case-id').textContent, 'Case ID copied.'));

    $('#cases-search').addEventListener('input', renderCasesTable);
    $('#cases-filter').addEventListener('change', renderCasesTable);
    $('#msg-search').addEventListener('input', renderMessageList);
    $('#btn-track').addEventListener('click', trackFromInput);
    $('#track-input').addEventListener('keydown', (event) => { if (event.key === 'Enter') trackFromInput(); });
    $('#tr-file').addEventListener('change', (event) => {
      const file = event.target.files && event.target.files[0];
      $('#tr-file-label').textContent = file ? file.name : 'Add evidence to this case';
    });
    $('#btn-upload-evidence').addEventListener('click', async () => {
      if (!state.current) return;
      const errorNode = $('#tr-evidence-error');
      hideError(errorNode);
      const input = $('#tr-file');
      try {
        await sendEvidence(state.current.caseId, input.files && input.files[0]);
        input.value = '';
        $('#tr-file-label').textContent = 'Add evidence to this case';
        toast('Evidence uploaded and encrypted.');
        await openTrack(state.current.caseId);
      } catch (error) {
        if (error.status === 401) return sessionEnded();
        showError(errorNode, error.message);
      }
    });
    $('#msg-list-items').addEventListener('click', (event) => {
      const item = event.target.closest('.msg-item');
      if (item) go(`dashboard/messages/${item.dataset.caseId}`);
    });
    $('#chat-form').addEventListener('submit', sendPersonalMessage);

    $('#inbox-search').addEventListener('input', renderInbox);
    $('#inbox-filter').addEventListener('change', renderInbox);
    $('#btn-invite').addEventListener('click', createInvitation);
    // Controls that previously had no listener (dead buttons):
    $('#btn-copy-invite').addEventListener('click', () => copyText($('#invite-code').textContent, 'Invitation code copied. Share it only with the person you are inviting.'));
    $('#btn-generate-recovery').addEventListener('click', createOrganisationRecoveryCode);
    $('#btn-copy-dashboard-recovery').addEventListener('click', () => copyText($('#org-recovery-code-value').textContent, 'Recovery key copied. Store it safely; it can be used only once.'));
    $('#oc-chat-form').addEventListener('submit', sendOrgReply);
    $('#btn-oc-save').addEventListener('click', saveOrgCase);

    document.addEventListener('click', (event) => {
      const download = event.target.closest('[data-download]');
      if (download) return downloadEvidence(download.dataset.download);
      const action = event.target.closest('[data-action]');
      if (!action) return;
      handleAction(action);
    });

    window.addEventListener('beforeunload', (event) => {
      if (state.pending) { event.preventDefault(); event.returnValue = ''; }
    });
  }

  function handleAction(action) {
    switch (action.dataset.action) {
      case 'go-landing': return go('landing');
      case 'go-login': state.loginWorkspace = 'PERSONAL'; return go('login');
      case 'go-login-org': state.loginWorkspace = 'ORGANISATION'; return go('login/org');
      case 'go-org-join': return go('org-join');
      case 'logout': return logout();
      case 'report-now': return reportNow();
      case 'toggle-id': {
        const input = document.getElementById(action.dataset.target);
        const reveal = input.type === 'password';
        input.type = reveal ? 'text' : 'password';
        action.textContent = reveal ? 'Hide' : 'Show';
        return undefined;
      }
      case 'report-next-1': if (validateStep1()) setReportStep(2); return undefined;
      case 'report-back-1': return setReportStep(1);
      case 'report-next-2': if (validateStep2()) { fillReview(); setReportStep(3); } return undefined;
      case 'report-back-2': return setReportStep(2);
      default: return undefined;
    }
  }

  function reportNow() {
    if (isPersonal()) return go('report');
    if (isOrg()) return toast('Organisation accounts cannot file reports. Sign in with a personal account to report.');
    return go('create');
  }

  async function init() {
    bindEvents();
    window.addEventListener('hashchange', route);
    await loadCsrf();
    await refreshSession();
    if (!location.hash) location.hash = '#landing';
    else route();
  }

  init();
})();
