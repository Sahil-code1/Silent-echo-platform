/* Checks that db/schema.sql matches the JPA entities: same tables, and every mapped column exists.
 * Usage (from the project root):  node db/check-schema-vs-entities.js
 * This is a static check. Hibernate's own validation (SILENTECHO_DDL_AUTO=validate) is the runtime check. */
'use strict';
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const entityDir = path.join(root, 'Backend/silentecho-backend/src/main/java/com/silentecho/silentecho_backend/entity');
const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');

const tables = new Map();
for (const m of schema.matchAll(/CREATE TABLE IF NOT EXISTS (\w+) \(([\s\S]*?)\) ENGINE/g)) {
  const columns = new Set();
  for (const raw of m[2].split('\n')) {
    const line = raw.trim();
    if (!line || /^(PRIMARY|UNIQUE|KEY|CONSTRAINT)\b/.test(line)) continue;
    columns.add(line.split(/\s+/)[0]);
  }
  tables.set(m[1], columns);
}

const snake = (field) => field.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase());
const problems = [];
const entityTables = new Set();
for (const file of fs.readdirSync(entityDir).filter((f) => f.endsWith('.java'))) {
  const src = fs.readFileSync(path.join(entityDir, file), 'utf8');
  if (!src.includes('@Entity')) continue;
  const table = (src.match(/@Table\(\s*name\s*=\s*"(\w+)"/) || [])[1];
  if (!table) { problems.push(`${file}: no @Table name`); continue; }
  entityTables.add(table);
  if (!tables.has(table)) { problems.push(`${file}: table ${table} is missing from schema.sql`); continue; }
  const columns = tables.get(table);
  // Every field that is a @Column, or an @Id/@Enumerated/@GeneratedValue field, must map to a schema column.
  for (const m of src.matchAll(/@Column\(([^)]*)\)\s*(?:\n\s*@[^\n]*)*\s*private\s+[\w<>]+\s+(\w+);/g)) {
    const named = (m[1].match(/name\s*=\s*"(\w+)"/) || [])[1];
    const column = named || snake(m[2]);
    if (!columns.has(column)) problems.push(`${file}: column ${column} (field ${m[2]}) is not in table ${table}`);
  }
  for (const m of src.matchAll(/@Id\s*\n(?:\s*@[^\n]*\n)*\s*private\s+[\w<>]+\s+(\w+);/g)) {
    if (!columns.has(snake(m[1]))) problems.push(`${file}: primary key ${m[1]} is not in table ${table}`);
  }
}
for (const table of tables.keys()) if (!entityTables.has(table)) problems.push(`schema table ${table} has no entity`);

console.log(`Tables in schema.sql: ${tables.size}. Entity tables: ${entityTables.size}.`);
if (problems.length) console.log('PROBLEMS:\n  ' + problems.join('\n  '));
const ok = problems.length === 0;
console.log(ok ? 'SCHEMA MATCHES ENTITIES' : 'SCHEMA CHECK FAILED');
process.exit(ok ? 0 : 1);
