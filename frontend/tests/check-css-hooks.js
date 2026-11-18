/* Static check: every CSS class that index.html, js/app.js or js/ui.js uses must have a rule in css/style.css.
 * A class with no rule is a styling bug: the element still works, but it has no design.
 * Usage (from frontend/tests): node check-css-hooks.js     Exit code 1 if any class is undefined. */
'use strict';
const fs = require('fs');
const path = require('path');

const frontend = path.resolve(__dirname, '..');
const read = (rel) => fs.readFileSync(path.join(frontend, rel), 'utf8');
const html = read('index.html');
const scripts = read('js/app.js') + '\n' + read('js/ui.js');
const css = read('css/style.css').replace(/\/\*[\s\S]*?\*\//g, '');

// Classes written in markup: class="a b c"
const used = new Set();
for (const m of html.matchAll(/class="([^"]+)"/g)) for (const c of m[1].split(/\s+/)) if (c) used.add(c);

// Classes referenced by scripts: class: 'x', class: `x`, classList.x('y'), className = 'y', selectors '.y'
const dynamicPrefixes = new Set();
for (const m of scripts.matchAll(/class:\s*'([^']+)'/g)) for (const c of m[1].split(/\s+/)) if (c) used.add(c);
for (const m of scripts.matchAll(/class:\s*`([^`]+)`/g)) {
  for (const c of m[1].split(/\s+/)) {
    if (!c) continue;
    if (c.includes('${')) dynamicPrefixes.add(c.split('${')[0]);
    else used.add(c);
  }
}
for (const m of scripts.matchAll(/classList\.(?:add|remove|toggle)\(\s*'([^']+)'/g)) used.add(m[1]);
for (const m of scripts.matchAll(/\$\$?\(\s*'([^']+)'/g)) for (const c of m[1].matchAll(/\.([A-Za-z_][\w-]*)/g)) used.add(c[1]);
for (const m of scripts.matchAll(/querySelector(?:All)?\(\s*'([^']+)'/g)) for (const c of m[1].matchAll(/\.([A-Za-z_][\w-]*)/g)) used.add(c[1]);
for (const m of scripts.matchAll(/className\s*=\s*'([^']+)'/g)) for (const c of m[1].split(/\s+/)) if (c) used.add(c);
// Template literal class names such as `urgency-${value}`: the prefix must have at least one rule.
for (const m of scripts.matchAll(/`(\w[\w-]*)\$\{/g)) if (/^(status|urgency|msg|tl)-$/.test(m[1] + '-') ) dynamicPrefixes.add(m[1] + '-');

// Classes defined by CSS: every .name that appears in a selector (before a brace)
const defined = new Set();
for (const block of css.matchAll(/([^{}]+)\{/g)) for (const c of block[1].matchAll(/\.([A-Za-z_][\w-]*)/g)) defined.add(c[1]);

// Keep only real class tokens (lowercase kebab-case). Anything else is punctuation picked up by the scan.
const isClassToken = (c) => /^[a-z][a-z0-9-]*$/.test(c);
for (const c of [...used]) if (!isClassToken(c)) used.delete(c);
const undefinedClasses = [...used].filter((c) => !defined.has(c)).sort();
const missingPrefixes = [...dynamicPrefixes].filter((p) => ![...defined].some((d) => d.startsWith(p))).sort();

// Informational: classes defined but never used (may be used by a future script or left over).
const unusedDefined = [...defined].filter((c) => !used.has(c) && ![...dynamicPrefixes].some((p) => c.startsWith(p))).sort();

console.log(`Classes used by markup and scripts: ${used.size}. Classes defined in CSS: ${defined.size}.`);
if (dynamicPrefixes.size) console.log('Template-built prefixes checked: ' + [...dynamicPrefixes].join(', '));
if (undefinedClasses.length) console.log('USED BUT NOT STYLED:\n  ' + undefinedClasses.join('\n  '));
if (missingPrefixes.length) console.log('TEMPLATE PREFIX WITHOUT A RULE: ' + missingPrefixes.join(', '));
if (unusedDefined.length) console.log('Defined but not referenced (informational): ' + unusedDefined.join(', '));
const ok = undefinedClasses.length === 0 && missingPrefixes.length === 0;
console.log(ok ? 'CSS HOOK CHECK PASSED' : 'CSS HOOK CHECK FAILED');
process.exit(ok ? 0 : 1);
