/* Serves the frontend and the mock API on one origin for local design work and browser tests.
 * Usage: node serve-mock.js [port]   then open http://localhost:5500/#landing
 * Sign in with the printed mock IDs. This is not the production server. */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const { createMockBackend } = require('./mock-api');

const root = path.resolve(__dirname, '..');
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.txt': 'text/plain' };
function createServer(port) {
const backend = createMockBackend();

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  let raw = '';
  req.on('data', (chunk) => { raw += chunk; });
  req.on('end', () => {
    if (url.pathname.startsWith('/api/')) {
      const result = backend.handle({ method: req.method, path: url.pathname, headers: req.headers, body: raw });
      res.writeHead(result.status, { 'Access-Control-Allow-Origin': '*', ...result.headers });
      return res.end(result.body);
    }
    let file = path.join(root, decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname));
    if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
});

return server;
}

module.exports = { createServer };

if (require.main === module) {
  const port = Number(process.argv[2] || 5500);
  createServer(port).listen(port, () => {
    const { PERSONAL_ID, ORG_ID } = require('./mock-api');
    console.log(`SilentEcho frontend + mock API on http://localhost:${port}`);
    console.log(`Personal sign-in ID: ${PERSONAL_ID}`);
    console.log(`Organisation sign-in ID: ${ORG_ID}`);
  });
}
