/* In-memory mock of the HTTP contract the frontend expects, used by the frontend tests and by
 * `node serve-mock.js` for local design work. It mirrors the controllers' JSON shapes and enforces the
 * X-CSRF-TOKEN header on writes. It is NOT the backend and does not check any real security.
 * It also models organisation codes, recovery keys and the /api/organisations/recover and
 * /recovery-codes endpoints, which the frontend calls but the delivered Spring backend does NOT yet implement.
 * Those routes are reported as MISSING by tests/check-api-routes.js until the backend provides them. */
'use strict';

const CSRF = 'mock-csrf-token';
const PERSONAL_ID = 'SE-' + 'A'.repeat(43);
const ORG_ID = 'SE-' + 'B'.repeat(43);
const ORG = { id: '0a0a0a0a-0000-4000-8000-00000000000a', name: 'Riverside Community Centre', type: 'COMMUNITY' };
const CATEGORIES = ['Bullying', 'Harassment', 'Cyberbullying', 'Discrimination', 'Emotional Distress', 'Violence/Threat', 'Theft', 'Facilities', 'Other'];
const ORG_CODE = 'SE-ORG-TEST';
const RECOVERY_CODE = 'SER-' + 'r'.repeat(40);
const CASE_IDS = ['11111111-2222-4333-8444-555555555555', '22222222-2222-4333-8444-555555555555'];

const iso = (daysAgo) => new Date(Date.now() - daysAgo * 864e5).toISOString();

function seedCases() {
  const cases = {};
  cases[CASE_IDS[0]] = {
    caseId: CASE_IDS[0], title: 'Corridor incident', category: 'Bullying', urgency: 'HIGH', status: 'UNDER_REVIEW',
    organisationName: ORG.name, createdAt: iso(6), updatedAt: iso(2), description: 'Dummy description for testing.',
    timeline: [{ type: 'SUBMITTED', note: null, status: 'SUBMITTED', urgency: 'HIGH', category: 'Bullying', at: iso(6) },
      { type: 'STATUS_CHANGED', note: null, status: 'UNDER_REVIEW', urgency: null, category: null, at: iso(2) }],
    messages: [{ sender: 'ORGANISATION', body: 'We have received your report and a responder will contact you here.', sentAt: iso(2) }],
    evidence: [{ fileId: 'f1f1f1f1-0000-4000-8000-000000000001', filename: 'notes.txt', contentType: 'text/plain', sizeBytes: 120, uploadedAt: iso(5) }]
  };
  cases[CASE_IDS[1]] = {
    caseId: CASE_IDS[1], title: 'Broken heating', category: 'Facilities', urgency: 'LOW', status: 'RESOLVED',
    organisationName: ORG.name, createdAt: iso(40), updatedAt: iso(30), description: 'The heating in the study room is broken.',
    timeline: [{ type: 'SUBMITTED', note: null, status: 'SUBMITTED', urgency: 'LOW', category: 'Facilities', at: iso(40) },
      { type: 'ACTION_NOTE', note: 'Repair completed and checked.', status: 'RESOLVED', urgency: null, category: null, at: iso(30) }],
    messages: [], evidence: []
  };
  return cases;
}

function summary(c) {
  return { caseId: c.caseId, title: c.title, category: c.category, urgency: c.urgency, status: c.status,
    organisationName: c.organisationName, createdAt: c.createdAt, updatedAt: c.updatedAt };
}

function createMockBackend() {
  let session = null;
  let cases = seedCases();
  const invitations = [];

  const json = (status, body) => ({ status, headers: { 'Content-Type': 'application/json' }, body: body === null ? '' : JSON.stringify(body) });
  const empty = (status) => ({ status, headers: {}, body: '' });
  const detail = (c) => ({ ...c, messages: c.messages, timeline: c.timeline, evidence: c.evidence });
  const orgRefs = () => (session && session.accountType === 'ORGANISATION' ? session.organisations : []);

  function filenameOf(body) {
    if (!body) return null;
    if (typeof body === 'string') { const m = body.match(/filename="([^"]+)"/); return m ? m[1] : null; }
    if (typeof body.get === 'function') { const f = body.get('file'); return f ? f.name : null; }
    return null;
  }

  function ownCase(caseId, personal) {
    const c = cases[caseId];
    if (!c) return null;
    return c;
  }

  function handle({ method, path, headers = {}, body }) {
    const m = method.toUpperCase();
    // HTTP header names are case-insensitive; Node lowercases them, browsers and tests may not.
    const h = {};
    for (const [key, value] of Object.entries(headers)) h[key.toLowerCase()] = value;
    if (!path.startsWith('/api/')) return json(404, { message: 'Not found.' });
    if (m !== 'GET' && path !== '/api/auth/csrf') {
      if (h['x-csrf-token'] !== CSRF) return empty(403);
    }
    let payload = {};
    if (typeof body === 'string' && body.length && body.trim().startsWith('{')) { try { payload = JSON.parse(body); } catch { return json(400, { message: 'The request could not be read.' }); } }

    if (m === 'GET' && path === '/api/auth/csrf') return json(200, { headerName: 'X-CSRF-TOKEN', token: CSRF });
    if (m === 'GET' && path === '/api/auth/me') return json(200, session ? { authenticated: true, accountType: session.accountType, organisations: orgRefs() } : { authenticated: false, accountType: null, organisations: [] });
    if (m === 'GET' && path === '/api/test') return json(200, 'SilentEcho Backend is Working!');
    if (m === 'GET' && path === '/api/health') return json(200, { status: 'UP', service: 'silentecho-backend' });

    if (m === 'POST' && path === '/api/auth/login') {
      if (payload.signInId === PERSONAL_ID && payload.workspace === 'PERSONAL') { session = { accountType: 'PERSONAL', organisations: [] }; return json(200, { authenticated: true, accountType: 'PERSONAL', organisations: [] }); }
      if (payload.signInId === ORG_ID && payload.workspace === 'ORGANISATION') { session = { accountType: 'ORGANISATION', organisations: [{ id: ORG.id, name: ORG.name, role: 'ADMIN' }] }; return json(200, { authenticated: true, accountType: 'ORGANISATION', organisations: session.organisations }); }
      if (payload.signInId && (payload.signInId === PERSONAL_ID || payload.signInId === ORG_ID)) return json(403, { message: 'This sign-in ID belongs to the other workspace. Use the other sign-in page.' });
      return json(401, { message: 'Sign-in failed. Check your private sign-in ID and try again.' });
    }
    if (m === 'POST' && path === '/api/auth/personal/register') { session = { accountType: 'PERSONAL', organisations: [] }; return json(201, { signInId: PERSONAL_ID, accountType: 'PERSONAL', organisations: [] }); }
    if (m === 'POST' && path === '/api/auth/logout') { session = null; return empty(204); }

    if (m === 'GET' && path === '/api/organisations') return json(200, [{ id: ORG.id, name: ORG.name, type: ORG.type }]);
    if (m === 'POST' && path === '/api/organisations/register') {
      if (!payload.name || !payload.type) return json(400, { message: 'name: must not be blank' });
      session = { accountType: 'ORGANISATION', organisations: [{ id: ORG.id, name: payload.name, role: 'ADMIN', organisationCode: ORG_CODE }] };
      return json(201, { signInId: ORG_ID, accountType: 'ORGANISATION', organisations: session.organisations, organisationRecoveryCode: RECOVERY_CODE });
    }
    if (m === 'POST' && path === '/api/organisations/join') {
      if (payload.invitationCode !== 'SEJ-valid-test-code') return json(400, { message: 'This invitation code is invalid, expired or already used.' });
      session = { accountType: 'ORGANISATION', organisations: [{ id: ORG.id, name: ORG.name, role: 'RESPONDER' }] };
      return json(201, { signInId: ORG_ID, accountType: 'ORGANISATION', organisations: session.organisations });
    }
    if (m === 'POST' && path === '/api/organisations/recovery-codes') {
      if (!session || session.accountType !== 'ORGANISATION') return json(401, { message: 'Sign in to continue.' });
      return json(201, { recoveryCode: RECOVERY_CODE });
    }
    if (m === 'POST' && path === '/api/organisations/recover') {
      if (payload.organisationCode !== ORG_CODE || payload.recoveryCode !== RECOVERY_CODE) return json(401, { message: 'Recovery failed. Check the organisation code and recovery key.' });
      session = { accountType: 'ORGANISATION', organisations: [{ id: ORG.id, name: ORG.name, role: 'ADMIN', organisationCode: ORG_CODE }] };
      return json(201, { signInId: ORG_ID, accountType: 'ORGANISATION', organisations: session.organisations });
    }
    if (m === 'POST' && path === '/api/organisations/invitations') {
      if (!session || session.accountType !== 'ORGANISATION') return json(401, { message: 'Sign in to continue.' });
      const code = 'SEJ-' + 'x'.repeat(22);
      invitations.push(code);
      return json(201, { code, expiresAt: new Date(Date.now() + 7 * 864e5).toISOString(), organisationId: payload.organisationId });
    }

    // Personal workspace
    if (path.startsWith('/api/personal/') || path.startsWith('/api/org/')) {
      if (!session) return json(401, { message: 'Sign in to continue.' });
    }
    if (m === 'GET' && path === '/api/personal/cases') {
      if (session.accountType !== 'PERSONAL') return json(403, { message: 'Only personal accounts can view reports.' });
      return json(200, Object.values(cases).map(summary));
    }
    if (m === 'POST' && path === '/api/personal/cases') {
      if (session.accountType !== 'PERSONAL') return json(403, { message: 'Only personal accounts can submit a report.' });
      if (!payload.title || !payload.description) return json(400, { message: 'title: must not be blank' });
      if (!CATEGORIES.includes(payload.category)) return json(400, { message: 'Choose a category from the list.' });
      if (payload.consentShared !== true) return json(400, { message: 'Please confirm the report can be shared with the chosen organisation.' });
      const caseId = 'aaaaaaaa-' + Math.random().toString(16).slice(2, 6).padEnd(4, '0') + '-4000-8000-' + Date.now().toString(16).padStart(12, '0').slice(-12);
      const c = { caseId, title: payload.title, category: payload.category, urgency: payload.urgency, status: 'SUBMITTED', organisationName: ORG.name,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), description: payload.description,
        timeline: [{ type: 'SUBMITTED', note: null, status: 'SUBMITTED', urgency: payload.urgency, category: payload.category, at: new Date().toISOString() }],
        messages: [], evidence: [] };
      cases[caseId] = c;
      return json(201, detail(c));
    }
    let match = path.match(/^\/api\/personal\/cases\/([^/]+)$/);
    if (match && m === 'GET') { const c = ownCase(match[1], true); return c ? json(200, detail(c)) : json(404, { message: 'Case not found.' }); }
    match = path.match(/^\/api\/personal\/cases\/([^/]+)\/messages$/);
    if (match && m === 'POST') {
      const c = ownCase(match[1], true);
      if (!c) return json(404, { message: 'Case not found.' });
      c.messages.push({ sender: 'REPORTER', body: payload.body, sentAt: new Date().toISOString() });
      c.updatedAt = new Date().toISOString();
      return json(200, detail(c));
    }
    match = path.match(/^\/api\/personal\/cases\/([^/]+)\/evidence$/);
    if (match && m === 'POST') {
      const c = ownCase(match[1], true);
      if (!c) return json(404, { message: 'Case not found.' });
      const name = filenameOf(body);
      if (!name || !/\.(pdf|jpe?g|png|txt)$/i.test(name)) return json(400, { message: 'Only PDF, JPG, PNG and TXT files are allowed.' });
      const fileId = 'f' + Math.random().toString(16).slice(2, 10).padEnd(8, '0') + '-0000-4000-8000-000000000002';
      c.evidence.push({ fileId, filename: name, contentType: 'text/plain', sizeBytes: 1024, uploadedAt: new Date().toISOString() });
      return json(201, { fileId, filename: name, contentType: 'text/plain', sizeBytes: 1024, uploadedAt: new Date().toISOString() });
    }
    match = path.match(/^\/api\/(personal|org)\/cases\/([^/]+)\/evidence\/([^/]+)$/);
    if (match && m === 'GET') {
      const c = ownCase(match[2], true);
      const f = c && c.evidence.find((e) => e.fileId === match[3]);
      if (!f) return json(404, { message: 'File not found.' });
      return { status: 200, headers: { 'Content-Type': 'text/plain', 'Content-Disposition': `attachment; filename="${f.filename}"`, 'X-Content-Type-Options': 'nosniff' }, body: 'mock evidence contents' };
    }

    // Organisation workspace
    if (m === 'GET' && path === '/api/org/cases') {
      if (session.accountType !== 'ORGANISATION') return json(403, { message: 'Only organisation accounts can view the inbox.' });
      return json(200, Object.values(cases).map(summary));
    }
    match = path.match(/^\/api\/org\/cases\/([^/]+)$/);
    if (match && m === 'GET') { const c = ownCase(match[1], false); return c ? json(200, detail(c)) : json(404, { message: 'Case not found.' }); }
    if (match && m === 'PATCH') {
      const c = ownCase(match[1], false);
      if (!c) return json(404, { message: 'Case not found.' });
      if (!payload.status && !payload.urgency && !payload.category && !payload.actionNote) return json(400, { message: 'Nothing to update.' });
      const now = new Date().toISOString();
      if (payload.status && payload.status !== c.status) { c.status = payload.status; c.timeline.push({ type: 'STATUS_CHANGED', note: null, status: c.status, urgency: null, category: null, at: now }); }
      if (payload.urgency && payload.urgency !== c.urgency) { c.urgency = payload.urgency; c.timeline.push({ type: 'PRIORITY_CHANGED', note: null, status: null, urgency: c.urgency, category: null, at: now }); }
      if (payload.category && payload.category !== c.category) { c.category = payload.category; c.timeline.push({ type: 'CATEGORY_CHANGED', note: null, status: null, urgency: null, category: c.category, at: now }); }
      if (payload.actionNote) c.timeline.push({ type: 'ACTION_NOTE', note: payload.actionNote, status: c.status, urgency: c.urgency, category: c.category, at: now });
      c.updatedAt = now;
      return json(200, detail(c));
    }
    match = path.match(/^\/api\/org\/cases\/([^/]+)\/messages$/);
    if (match && m === 'POST') {
      const c = ownCase(match[1], false);
      if (!c) return json(404, { message: 'Case not found.' });
      c.messages.push({ sender: 'ORGANISATION', body: payload.body, sentAt: new Date().toISOString() });
      return json(200, detail(c));
    }

    if (m === 'POST' && path === '/api/ai/analyze') {
      return json(200, { suggestion: JSON.stringify({ suggestedCategory: 'Bullying', priority: 'HIGH', riskLevel: 'Medium', rationale: 'Mock suggestion for testing.', recommendedNextStep: 'Contact the reporter through the case.' }), humanReviewRequired: true });
    }
    return json(404, { message: 'Not found.' });
  }

  return { handle, CSRF, PERSONAL_ID, ORG_ID, CASE_IDS, ORG };
}

module.exports = { createMockBackend, CSRF, PERSONAL_ID, ORG_ID, CASE_IDS, ORG, ORG_CODE, RECOVERY_CODE };
