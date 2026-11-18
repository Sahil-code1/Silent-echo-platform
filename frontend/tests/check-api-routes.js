/* Static cross-check: every API path the frontend calls must exist as a mapping in the real backend
 * controllers with the same HTTP method. It reads the Java source; it does not start the server.
 * Usage (from frontend/tests): node check-api-routes.js [path-to-backend-src]
 * Exit code 1 if the frontend calls a route the backend does not define. */
'use strict';
const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..', '..');
const backendPackage = path.resolve(process.argv[2] || path.join(repoRoot, 'Backend/silentecho-backend/src/main/java/com/silentecho/silentecho_backend'));
const appJs = fs.readFileSync(path.join(__dirname, '..', 'js', 'app.js'), 'utf8');

/* ---- backend: collect @RequestMapping base paths and method mappings from every controller ---- */
function javaFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? javaFiles(full) : entry.name.endsWith('.java') ? [full] : [];
  });
}

const backendRoutes = [];
for (const file of javaFiles(path.join(backendPackage, 'controller'))) {
  const src = fs.readFileSync(file, 'utf8');
  const classBase = (src.match(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"/) || [, ''])[1];
  const mappingRe = /@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*\)|\(\s*\))?/g;
  let m;
  while ((m = mappingRe.exec(src))) {
    const method = m[1].toUpperCase();
    const sub = m[2] || '';
    backendRoutes.push({ method, pattern: classBase + sub, file: path.relative(repoRoot, file) });
  }
  // A method with no argument, such as @GetMapping, maps to the class base path.
  const bareRe = /@(Get|Post|Patch)Mapping\s*\n/g;
  while ((m = bareRe.exec(src))) backendRoutes.push({ method: m[1].toUpperCase(), pattern: classBase, file: path.relative(repoRoot, file) });
}

function toRegex(pattern) {
  const escaped = pattern.replace(/[.*+?^${}()|[\]\\]/g, (c) => (c === '{' || c === '}' ? c : '\\' + c));
  return new RegExp('^' + escaped.replace(/\{[^/]+\}/g, '[^/]+') + '$');
}

/* ---- frontend: find every /api/ literal and infer its method from the nearest method option ---- */
const callSites = [];
// Literals that start with /api/, plus templates that start with ${base} (a variable holding a list path).
const literalRe = /(['"`])((?:\/api\/|\$\{base\})[^'"`]*)\1/g;
let lit;
while ((lit = literalRe.exec(appJs))) {
  let raw = lit[2];
  if (raw.startsWith('${base}')) raw = '/api/personal/cases' + raw.slice('${base}'.length); // replaced again below for both bases
  const windowText = appJs.slice(lit.index, lit.index + 320);
  const nextCall = windowText.indexOf('api(', 1);
  const scope = nextCall > 0 ? windowText.slice(0, nextCall) : windowText;
  const methodMatch = scope.match(/method:\s*'([A-Z]+)'/);
  const method = methodMatch ? methodMatch[1] : 'GET';
  // ${base} is a frontend variable that holds one of two list paths; check both possibilities.
  const expansions = raw.includes('/api/personal/cases') && /\$\{base\}/.test(appJs.slice(lit.index, lit.index + 200)) && lit[2].startsWith('${base}')
    ? ['/api/personal/cases', '/api/org/cases'].map((b) => raw.replace('/api/personal/cases', b))
    : [raw];
  for (const candidate of expansions) {
    callSites.push({ method, path: candidate.replace(/\$\{[^}]*\}/g, '{id}').replace(/\?.*$/, '') });
  }
}

/* ---- match ---- */
const seen = new Map();
for (const call of callSites) seen.set(`${call.method} ${call.path}`, call);
let missing = 0;
console.log('Frontend calls matched against backend controller mappings:\n');
for (const call of [...seen.values()].sort((a, b) => a.path.localeCompare(b.path))) {
  const hit = backendRoutes.find((r) => r.method === call.method && toRegex(r.pattern).test(call.path));
  if (hit) console.log(`  OK      ${call.method.padEnd(6)} ${call.path.padEnd(52)} -> ${hit.file}`);
  else { missing++; console.log(`  MISSING ${call.method.padEnd(6)} ${call.path}`); }
}

const usedKeys = new Set([...seen.keys()]);
const unused = backendRoutes.filter((r) => ![...usedKeys].some((k) => {
  const [method, p] = [k.split(' ')[0], k.split(' ').slice(1).join(' ')];
  return method === r.method && toRegex(r.pattern).test(p);
}));
if (unused.length) {
  console.log('\nBackend routes the frontend does not call (informational):');
  unused.forEach((r) => console.log(`  ${r.method.padEnd(6)} ${r.pattern}`));
}
console.log(`\nBackend mappings found: ${backendRoutes.length}. Frontend calls checked: ${seen.size}. Missing: ${missing}.`);
console.log(missing === 0 ? 'API ROUTE CHECK PASSED' : 'API ROUTE CHECK FAILED');
process.exit(missing === 0 ? 0 : 1);
