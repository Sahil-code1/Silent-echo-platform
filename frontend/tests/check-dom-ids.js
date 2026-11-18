/* Static cross-check: every element ID that js/app.js looks up must exist in index.html.
 * A missing ID would otherwise fail only when that screen is opened.
 * Usage (from frontend/tests): node check-dom-ids.js   Exit code 1 if any ID is missing. */
'use strict';
const fs = require('fs');
const path = require('path');

const frontend = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(frontend, 'index.html'), 'utf8');
const js = fs.readFileSync(path.join(frontend, 'js', 'app.js'), 'utf8');

const htmlIds = new Set([...html.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]));

// Lookups in app.js: $('#x'), $$('#x ...'), getElementById('x'), and the quoted screen/view/step maps.
const looked = new Map();
const add = (id, where) => { if (!looked.has(id)) looked.set(id, where); };
for (const m of js.matchAll(/\$\$?\(\s*'#([A-Za-z0-9_-]+)/g)) add(m[1], 'selector');
for (const m of js.matchAll(/getElementById\(\s*'([A-Za-z0-9_-]+)'\s*\)/g)) add(m[1], 'getElementById');
// Maps of screen, dashboard and organisation views, plus the report step ids.
// Map entries are "routeName: 'elementId'". Only the element ID (the value) is looked up in the DOM.
for (const block of js.matchAll(/const (SCREENS|DASH_VIEWS|ORG_VIEWS) = \{([\s\S]*?)\};/g)) {
  for (const m of block[2].matchAll(/'?[A-Za-z0-9_-]+'?\s*:\s*'([A-Za-z0-9_-]+)'/g)) add(m[1], block[1]);
}
for (const m of js.matchAll(/\[\s*'(report-step-\d)'[^\]]*\]/g)) add(m[1], 'report steps');
for (const m of js.matchAll(/'(report-step-\d)'/g)) add(m[1], 'report steps');
for (const m of js.matchAll(/getElementById\(\s*id\s*\)/g)) void m; // dynamic lookup already covered by the maps

let missing = [];
const dynamic = [];
for (const [id, where] of looked) {
  if (htmlIds.has(id)) continue;
  // "report-step-" is built at runtime as '#report-step-' + n; accept it when IDs with that prefix exist.
  if (id.endsWith('-') && [...htmlIds].some((h) => h.startsWith(id))) { dynamic.push(`${id}* (${where})`); continue; }
  missing.push(`${id} (${where})`);
}
if (dynamic.length) console.log('Built at runtime, prefix matched in HTML: ' + dynamic.join(', '));

// Duplicate IDs are invalid HTML and make getElementById return only the first match.
const seen = new Map();
for (const m of html.matchAll(/\sid="([^"]+)"/g)) seen.set(m[1], (seen.get(m[1]) || 0) + 1);
const duplicates = [...seen].filter(([, n]) => n > 1).map(([id]) => id);

console.log(`Element IDs in index.html: ${htmlIds.size}. IDs looked up by app.js: ${looked.size}.`);
if (duplicates.length) console.log('Duplicate IDs: ' + duplicates.join(', '));
if (missing.length) console.log('MISSING in index.html:\n  ' + missing.join('\n  '));
const ok = missing.length === 0 && duplicates.length === 0;
console.log(ok ? 'DOM ID CHECK PASSED' : 'DOM ID CHECK FAILED');
process.exit(ok ? 0 : 1);
